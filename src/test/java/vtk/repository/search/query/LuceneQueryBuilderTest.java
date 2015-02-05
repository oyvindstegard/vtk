/* Copyright (c) 2012–2015, University of Oslo, Norway
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 
 *  * Neither the name of the University of Oslo nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *      
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package vtk.repository.search.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import static org.junit.Assert.*;

import org.apache.lucene.index.Term;
import org.apache.lucene.queries.BooleanFilter;
import org.apache.lucene.queries.TermsFilter;
import org.apache.lucene.search.BooleanClause;

import org.apache.lucene.search.Filter;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.TermQuery;

import org.jmock.Expectations;
import static org.jmock.Expectations.returnValue;
import org.jmock.auto.Mock;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import vtk.repository.Namespace;
import vtk.repository.index.mapping.AclFields;
import vtk.repository.index.mapping.DocumentMapper;

import vtk.repository.resourcetype.PropertyTypeDefinitionImpl;
import vtk.repository.resourcetype.ValueFactoryImpl;
import vtk.repository.search.Search;
import vtk.repository.search.query.security.QueryAuthorizationFilterFactory;
import vtk.security.PrincipalFactory;
import vtk.testing.mocktypes.MockPrincipalFactory;
import vtk.testing.mocktypes.MockResourceTypeTree;

/**
 * Currently this test class only provides test for search filters.
 */
public class LuceneQueryBuilderTest {

    private final String dummyToken = "dummy_token";

    private LuceneQueryBuilder luceneQueryBuilder = new LuceneQueryBuilder();

    @Rule
    public JUnitRuleMockery context = new JUnitRuleMockery();

    @Mock
    private QueryAuthorizationFilterFactory mockQueryAuthorizationFilterFactory;

    private TermsFilter dummyAclFilter;
    private Search search;
    
    private Filter iterationQueryFilter;

    @Before
    public void setUp() {
        luceneQueryBuilder.setQueryAuthorizationFilterFactory(mockQueryAuthorizationFilterFactory);

        PropertyTypeDefinitionImpl publishedPropDef = new PropertyTypeDefinitionImpl();
        publishedPropDef.setNamespace(Namespace.DEFAULT_NAMESPACE);
        publishedPropDef.setName("published");
        luceneQueryBuilder.setPublishedPropDef(publishedPropDef);

        PropertyTypeDefinitionImpl unpublishedCollectionPropDef = new PropertyTypeDefinitionImpl();
        unpublishedCollectionPropDef.setNamespace(Namespace.DEFAULT_NAMESPACE);
        unpublishedCollectionPropDef.setName("unpublishedCollection");
        luceneQueryBuilder.setUnpublishedCollectionPropDef(unpublishedCollectionPropDef);

        PropertyTypeDefinitionImpl hiddenPropDef = new PropertyTypeDefinitionImpl();
        hiddenPropDef.setNamespace(Namespace.getNamespace("http://www.uio.no/navigation"));
        hiddenPropDef.setName("hidden");
        luceneQueryBuilder.setHiddenPropDef(hiddenPropDef);
        
        // Document mapper dependency
        PrincipalFactory pf = new MockPrincipalFactory();
        DocumentMapper dm = new DocumentMapper();
        dm.setLocale(Locale.getDefault());
        dm.setResourceTypeTree(new MockResourceTypeTree());
        dm.setPrincipalFactory(pf);
        ValueFactoryImpl vf = new ValueFactoryImpl();
        vf.setPrincipalFactory(pf);
        dm.setValueFactory(vf);
        dm.afterPropertiesSet();
        luceneQueryBuilder.setDocumentMapper(dm);
        
        List<Term> terms = new ArrayList<Term>();
        terms.add(new Term(AclFields.AGGREGATED_READ_FIELD_NAME, PrincipalFactory.ALL
                .getQualifiedName()));
        dummyAclFilter = new TermsFilter(terms);

        luceneQueryBuilder.afterPropertiesSet();
        
        search = new Search();
        search.setQuery(new UriTermQuery("/", TermOperator.EQ));
        
        iterationQueryFilter = new QueryWrapperFilter(new TermQuery(new Term("uri", "/")));
    }
    
    @Test
    public void noAclFilterNoSearchFlags() {
        search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED,
                Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
        assertSearchFilter(null, search, null);
        
        // Iteration filter
        assertIterationFilter(iterationQueryFilter, search, null);
    }
    
    @Test
    public void onlyAclFilter() {
        search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED,
                Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
        assertSearchFilter(dummyAclFilter, search, dummyAclFilter);
        
        // Iteration filter
        BooleanFilter bf = new BooleanFilter();
        bf.add(dummyAclFilter, BooleanClause.Occur.MUST);
        bf.add(iterationQueryFilter, BooleanClause.Occur.MUST);
        assertIterationFilter(bf, search, dummyAclFilter);
    }

    @Test
    public void aclFilterAndPublished() {
        search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
        
        BooleanFilter expected = new BooleanFilter();
        expected.add(dummyAclFilter, BooleanClause.Occur.MUST);
        expected.add(luceneQueryBuilder.getPublishedFilter(), BooleanClause.Occur.MUST);
        
        assertSearchFilter(expected, search, dummyAclFilter);
        
        // Iteration filter
        expected.add(iterationQueryFilter, BooleanClause.Occur.MUST);
        assertIterationFilter(expected, search, dummyAclFilter);
    }

    @Test
    public void aclFilterAndOnlyPublishedCollections() {
        search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED);
        
        BooleanFilter expected = new BooleanFilter();
        expected.add(dummyAclFilter, BooleanClause.Occur.MUST);
        expected.add(luceneQueryBuilder.getUnpublishedCollectionFilter(), BooleanClause.Occur.MUST);
        
        assertSearchFilter(expected, search, dummyAclFilter);

        // Iteration filter
        expected.add(iterationQueryFilter, BooleanClause.Occur.MUST);
        assertIterationFilter(expected, search, dummyAclFilter);
    }
    
    @Test
    public void aclFilterAndAllSearchFilters() {
        BooleanFilter expected = new BooleanFilter();
        expected.add(dummyAclFilter, BooleanClause.Occur.MUST);
        expected.add(luceneQueryBuilder.getPublishedFilter(), BooleanClause.Occur.MUST);
        expected.add(luceneQueryBuilder.getUnpublishedCollectionFilter(), BooleanClause.Occur.MUST);
        
        assertSearchFilter(expected, search, dummyAclFilter);

        // Iteration filter
        expected.add(iterationQueryFilter, BooleanClause.Occur.MUST);
        assertIterationFilter(expected, search, dummyAclFilter);
    }
    
    @Test
    public void noAclAndAllSearchFilters() {
        BooleanFilter expected = new BooleanFilter();
        expected.add(luceneQueryBuilder.getPublishedFilter(), BooleanClause.Occur.MUST);
        expected.add(luceneQueryBuilder.getUnpublishedCollectionFilter(), BooleanClause.Occur.MUST);
        
        assertSearchFilter(expected, search, null);

        // Iteration filter
        expected.add(iterationQueryFilter, BooleanClause.Occur.MUST);
        assertIterationFilter(expected, search, null);
    }

    @Test
    public void noAclAndPublishedFilter() {
        search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
        assertSearchFilter(luceneQueryBuilder.getPublishedFilter(), search, null);
        
        // Iteration filter
        BooleanFilter expected = new BooleanFilter();
        expected.add(luceneQueryBuilder.getPublishedFilter(), BooleanClause.Occur.MUST);
        expected.add(iterationQueryFilter, BooleanClause.Occur.MUST);
        
        assertIterationFilter(expected, search, null);
        
    }
    
    @Test
    public void noAclAndUnpublishedCollectionsFilter() {
        search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED);
        assertSearchFilter(luceneQueryBuilder.getUnpublishedCollectionFilter(), search, null);

    
        // Iteration filter
        BooleanFilter expected = new BooleanFilter();
        expected.add(luceneQueryBuilder.getUnpublishedCollectionFilter(), BooleanClause.Occur.MUST);
        expected.add(iterationQueryFilter, BooleanClause.Occur.MUST);
        assertIterationFilter(expected, search, null);
    }
    
    @Test
    public void iterationWithNoQuery() {
        search.setQuery(null);
        BooleanFilter expected = new BooleanFilter();
        expected.add(dummyAclFilter, BooleanClause.Occur.MUST);
        expected.add(luceneQueryBuilder.getPublishedFilter(), BooleanClause.Occur.MUST);
        expected.add(luceneQueryBuilder.getUnpublishedCollectionFilter(), BooleanClause.Occur.MUST);
        
        assertIterationFilter(expected, search, dummyAclFilter);
    }
    
    @Test
    public void iterationAll() {
        search.setQuery(null);
        search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED, Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
        assertIterationFilter(null, search, null);
    }
    
    private void assertSearchFilter(final Filter expected, final Search search, final Filter aclFilter) {
        context.checking(new Expectations() {
            {
                oneOf(mockQueryAuthorizationFilterFactory).authorizationQueryFilter(dummyToken, null);
                will(returnValue(aclFilter));
            }
        });

        Filter actual = luceneQueryBuilder.buildSearchFilter(dummyToken, search, null);

        if (expected == null) {
            assertNull("Filter was expected to be null", actual);
        } else {
            assertEquals("Filter is not as expected", expected, actual);
        }
    }
    
    private void assertIterationFilter(final Filter expected, final Search search, final Filter aclFilter) {
        context.checking(new Expectations() {
            {
                oneOf(mockQueryAuthorizationFilterFactory).authorizationQueryFilter(dummyToken, null);
                will(returnValue(aclFilter));
            }
        });

        Filter actual = luceneQueryBuilder.buildIterationFilter(dummyToken, search, null);
        
        if (expected == null) {
            assertNull("Filter was expected to be null", actual);
        } else {
            assertEquals("Filter is not as expected", expected, actual);
        }
    }
}
