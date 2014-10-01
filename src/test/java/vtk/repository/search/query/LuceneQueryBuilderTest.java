/* Copyright (c) 2012, University of Oslo, Norway
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.lucene.index.Term;
import org.apache.lucene.queries.BooleanFilter;
import org.apache.lucene.queries.TermsFilter;
import org.apache.lucene.search.BooleanClause;

import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
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

/*
 *  TODO: Review this test. It is (at least to me) a bit unclear exactly what we are testing, so 
 *  I believe we need to document this more throughly, and make it perfectly clear what is suppose 
 *  to happen at each step in each test...
 * 
 */

public class LuceneQueryBuilderTest {

    final String dummyToken = "dummy_token";
    final IndexSearcher nullIndexSearcher = null;

    private LuceneQueryBuilder luceneQueryBuilder = new LuceneQueryBuilder();
    private Mockery context = new JUnit4Mockery();
    private QueryAuthorizationFilterFactory mockQueryAuthorizationFilterFactory;
    private TermsFilter dummyAclFilter;

    @Before
    public void setUp() {
        this.mockQueryAuthorizationFilterFactory = context.mock(QueryAuthorizationFilterFactory.class);
        luceneQueryBuilder.setQueryAuthorizationFilterFactory(mockQueryAuthorizationFilterFactory);

        PropertyTypeDefinitionImpl publishedPropDef = new PropertyTypeDefinitionImpl();
        publishedPropDef.setNamespace(Namespace.DEFAULT_NAMESPACE);
        publishedPropDef.setName("published");
        luceneQueryBuilder.setPublishedPropDef(publishedPropDef);

        PropertyTypeDefinitionImpl unpublishedCollectionPropDef = new PropertyTypeDefinitionImpl();
        unpublishedCollectionPropDef.setNamespace(Namespace.DEFAULT_NAMESPACE);
        unpublishedCollectionPropDef.setName("unpublishedCollection");
        luceneQueryBuilder.setUnpublishedCollectionPropDef(unpublishedCollectionPropDef);

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
    }

    @Test
    public void testGetSearchFilterDefaultExcludesFalseNoAcl() {

        Search search = new Search();
        search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED,
                Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
        this.assertGetSearchFilter(null, search, null);
    }

    @Test
    public void testGetSearchFilterDefaultExcludesFalseAndAcl() {

        Search search = new Search();
        search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED,
                Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
        this.assertGetSearchFilter(dummyAclFilter, search, dummyAclFilter);

    }

    @Test
    public void testGetSearchFilterDefaultExcludesNoAcl() {

        Search search = new Search();
        Filter expected = luceneQueryBuilder.buildUnpublishedFilter();
        expected = luceneQueryBuilder.addUnpublishedCollectionFilter(expected);
        this.assertGetSearchFilter(expected, search, null);

    }

    @Test
    public void testGetSearchFilterDefaultExcludesAndAcl() {

        Search search = new Search();
        BooleanFilter expected = luceneQueryBuilder.buildUnpublishedFilter();
        expected.add(dummyAclFilter, BooleanClause.Occur.MUST);
        expected = luceneQueryBuilder.addUnpublishedCollectionFilter(expected);
        this.assertGetSearchFilter(expected, search, dummyAclFilter);

    }

    private void assertGetSearchFilter(final Filter expected, final Search search, final Filter expectedReturnAclQuery) {

        context.checking(new Expectations() {
            {
                one(mockQueryAuthorizationFilterFactory).authorizationQueryFilter(dummyToken, nullIndexSearcher);
                will(returnValue(expectedReturnAclQuery));
            }
        });

        Filter actual = luceneQueryBuilder.buildSearchFilter(dummyToken, search, nullIndexSearcher);

        // Nothing more to do, requested configuration yields no filter
        if (!search.hasFilterFlag(Search.FilterFlag.UNPUBLISHED)
                && expectedReturnAclQuery == null) {
            assertNull("Filter 'actual' is supposed to be NULL", actual);
            return;
        }

        assertNotNull("Filter 'actual' is NOT supposed to be NULL", actual);
        assertNotNull("Filter 'expected' is NOT supposed to be NULL", expected);
        assertTrue("Filter is not as expected", expected.equals(actual));

    }

}
