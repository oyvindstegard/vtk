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

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import vtk.repository.Namespace;
import vtk.repository.ResourceTypeTree;
import vtk.repository.index.mapping.DocumentMapper;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.ValueFactoryImpl;
import vtk.repository.search.Search;
import vtk.repository.search.query.security.AuthorizationFilterQueryFactory;
import vtk.repository.search.query.security.SimpleAuthorizationFilterQueryFactory;
import vtk.security.PrincipalFactory;
import vtk.testing.mocktypes.MockPrincipalFactory;

/**
 * Test Lucene query building.
 */
public class LuceneQueryBuilderTest {

    private final DocumentMapper documentMapper;
    private final AuthorizationFilterQueryFactory authorizationFilterQueryFactory;
    private final PropertyTypeDefinition publishedPropDef;
    private final PropertyTypeDefinition unpublishedCollectionPropDef;
    private final ResourceTypeTree resourceTypeTree;
    private final IndexSearcher searcher;

    private final LuceneQueryBuilder instance;

    public LuceneQueryBuilderTest() {
        authorizationFilterQueryFactory = mock(AuthorizationFilterQueryFactory.class);
        
        publishedPropDef = mock(PropertyTypeDefinition.class);
        when(publishedPropDef.getName()).thenReturn("published");
        when(publishedPropDef.getNamespace()).thenReturn(Namespace.DEFAULT_NAMESPACE);
        when(publishedPropDef.getType()).thenReturn(PropertyType.Type.BOOLEAN);


        unpublishedCollectionPropDef = mock(PropertyTypeDefinition.class);
        when(unpublishedCollectionPropDef.getName()).thenReturn("unpublishedCollection");
        when(unpublishedCollectionPropDef.getNamespace()).thenReturn(Namespace.DEFAULT_NAMESPACE);
        when(unpublishedCollectionPropDef.getType()).thenReturn(PropertyType.Type.BOOLEAN);

        resourceTypeTree = mock(ResourceTypeTree.class);
        searcher = mock(IndexSearcher.class);
        
        PrincipalFactory pf = new MockPrincipalFactory();
        ValueFactoryImpl vf = new ValueFactoryImpl();
        vf.setPrincipalFactory(pf);
        documentMapper = new DocumentMapper();
        documentMapper.setLocale(Locale.getDefault());
        documentMapper.setPrincipalFactory(pf);
        documentMapper.setResourceTypeTree(resourceTypeTree);
        documentMapper.setValueFactory(vf);
        documentMapper.afterPropertiesSet();

        instance = new LuceneQueryBuilder();
        instance.setAuthorizationFilterQueryFactory(authorizationFilterQueryFactory);
        instance.setDocumentMapper(documentMapper);
        instance.setPublishedPropDef(publishedPropDef);
        instance.setUnpublishedCollectionPropDef(unpublishedCollectionPropDef);
    }

    @Test
    public void testBuildSearchFilterQuery_root() {

        when(authorizationFilterQueryFactory.authorizationFilterQuery("pretend-super-token")).thenReturn(Optional.empty());

        Search s = new Search().clearAllFilterFlags();
        Optional<org.apache.lucene.search.Query> filter = instance.buildSearchFilterQuery("pretend-super-token", s);
        assertFalse(filter.isPresent());
    }

    @Test
    public void testBuildSearchFilterQuery_root_published() {

        when(authorizationFilterQueryFactory.authorizationFilterQuery("pretend-super-token")).thenReturn(Optional.empty());

        Search s = new Search().clearAllFilterFlags().addFilterFlag(Search.FilterFlag.UNPUBLISHED);
        Optional<org.apache.lucene.search.Query> filter = instance.buildSearchFilterQuery("pretend-super-token", s);
        assertTrue(filter.isPresent());
        assertEquals("p_published:true", filter.get().toString());
        
    }

    @Test
    public void testBuildSearchFilterQuery_root_unpublishedCollection() {

        when(authorizationFilterQueryFactory.authorizationFilterQuery("pretend-super-token")).thenReturn(Optional.empty());

        Search s = new Search().clearAllFilterFlags().addFilterFlag(Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
        Optional<org.apache.lucene.search.Query> filter = instance.buildSearchFilterQuery("pretend-super-token", s);
        assertTrue(filter.isPresent());
        assertTrue(filter.get() instanceof BooleanQuery);
        assertEquals("-p_unpublishedCollection:true", filter.get().toString());

    }

    @Test
    public void testBuildSearchFilterQuery_root_defaultFilterFlags() {

        when(authorizationFilterQueryFactory.authorizationFilterQuery("pretend-super-token")).thenReturn(Optional.empty());

        Search s = new Search();
        Optional<org.apache.lucene.search.Query> filter = instance.buildSearchFilterQuery("pretend-super-token", s);
        assertTrue(filter.isPresent());
        assertTrue(filter.get() instanceof BooleanQuery);
        assertEquals("#p_published:true -p_unpublishedCollection:true", filter.get().toString());
    }

    @Test
    public void testBuildSearchFilterQuery_anyUser_defaultFilterFlags() {

        when(authorizationFilterQueryFactory.authorizationFilterQuery(null))
                .thenReturn(Optional.of(SimpleAuthorizationFilterQueryFactory.ACL_READ_FOR_ALL_FILTER_QUERY));

        Search s = new Search();
        Optional<org.apache.lucene.search.Query> filter = instance.buildSearchFilterQuery(null, s);
        assertTrue(filter.isPresent());
        assertTrue(filter.get() instanceof BooleanQuery);
        assertEquals("#acl_read_aggregate:pseudo:all #p_published:true -p_unpublishedCollection:true",
                filter.get().toString());
    }

    @Test
    public void testBuildSearchFilterQuery_anyUser() {

        when(authorizationFilterQueryFactory.authorizationFilterQuery(null))
                .thenReturn(Optional.of(SimpleAuthorizationFilterQueryFactory.ACL_READ_FOR_ALL_FILTER_QUERY));

        Search s = new Search().clearAllFilterFlags();
        Optional<org.apache.lucene.search.Query> filter = instance.buildSearchFilterQuery(null, s);
        assertTrue(filter.isPresent());
        assertEquals("acl_read_aggregate:pseudo:all", filter.get().toString());
    }

    @Test
    public void testCombineQueryWithFilter() {
        org.apache.lucene.search.Query userQuery = new TermQuery(new Term("field", "value"));

        org.apache.lucene.search.Query result = instance.combineQueryWithFilter(userQuery,
                SimpleAuthorizationFilterQueryFactory.ACL_READ_FOR_ALL_FILTER_QUERY);

        assertEquals("#field:value #acl_read_aggregate:pseudo:all", result.toString());
    }

    @Test
    public void testCombineQueryWithFilter_orUserQuery() {
        BooleanQuery userQuery = new BooleanQuery.Builder()
                .add(new TermQuery(new Term("field", "this")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("field", "that")), BooleanClause.Occur.SHOULD)
                .build();

        org.apache.lucene.search.Query result = instance.combineQueryWithFilter(userQuery,
                SimpleAuthorizationFilterQueryFactory.ACL_READ_FOR_ALL_FILTER_QUERY);
        assertEquals("#(field:this field:that) #acl_read_aggregate:pseudo:all", result.toString());
    }

    @Test
    public void pureNegativeTopLevelUserQuery() {
        UriTermQuery notThisUri = new UriTermQuery("/x", TermOperator.NE);
        org.apache.lucene.search.Query userQuery = instance.buildQuery(notThisUri, searcher);
        assertEquals("-uri:/x #*:*", userQuery.toString());
    }

    @Test
    public void pureNegativeTopLevelUserQuery_twoClauses() {
        AndQuery aq = new AndQuery();
        aq.add(new UriTermQuery("/x", TermOperator.NE));
        aq.add(new UriTermQuery("/y", TermOperator.NE));

        org.apache.lucene.search.Query userQuery = instance.buildQuery(aq, searcher);
        assertEquals("-uri:/x -uri:/y #*:*", userQuery.toString());
    }

    @Test
    public void queryWithRequiredAndProhibitedClause() {
        AndQuery aq = new AndQuery();
        aq.add(new UriTermQuery("/x", TermOperator.EQ));
        aq.add(new UriTermQuery("/y", TermOperator.NE));

        org.apache.lucene.search.Query userQuery = instance.buildQuery(aq, searcher);
        assertEquals("#uri:/x -uri:/y", userQuery.toString());
    }

    @Test
    public void queryWithSuggestedAndProhibitedClause() {
        OrQuery oq = new OrQuery();
        oq.add(new UriTermQuery("/x", TermOperator.EQ));
        oq.add(new UriTermQuery("/y", TermOperator.NE));

        org.apache.lucene.search.Query userQuery = instance.buildQuery(oq, searcher);
        assertEquals("uri:/x (-uri:/y #*:*)", userQuery.toString());
    }

    @Test
    public void topLevelAndWithSuggestedMatchesAndProhibition() {
        OrQuery oq = new OrQuery();
        oq.add(new UriTermQuery("/x", TermOperator.EQ));
        oq.add(new UriTermQuery("/y", TermOperator.EQ));
        AndQuery topLevel = new AndQuery();
        topLevel.add(oq);
        topLevel.add(new UriTermQuery("/z", TermOperator.NE));

        org.apache.lucene.search.Query userQuery = instance.buildQuery(topLevel, searcher);
        assertTrue(userQuery instanceof BooleanQuery);
        assertEquals(2, ((BooleanQuery)userQuery).clauses().size());
        assertTrue(((BooleanQuery)userQuery).clauses().get(0).getQuery() instanceof TermInSetQuery); // OR-ed URI term query optimization
        assertEquals("#uri:/x uri:/y -uri:/z", userQuery.toString());
                    // ^^^^^^^^^^^^^ actually a single clause, TermInSetQuery with unfortunate toString() impl (should have used parens if more than noe term)
    }

    @Test
    public void slightlyComplex() {
        OrQuery oq1 = new OrQuery();
        oq1.add(new UriPrefixQuery("/a", false));
        oq1.add(new UriTermQuery("/a/b", TermOperator.NE));
        OrQuery oq2 = new OrQuery();
        oq2.add(new UriTermQuery("/x", TermOperator.EQ));
        oq2.add(new UriTermQuery("/y", TermOperator.EQ));
        AndQuery topLevel = new AndQuery();
        topLevel.add(oq1);
        topLevel.add(oq2);
        topLevel.add(new UriTermQuery("/z", TermOperator.NE));

        org.apache.lucene.search.Query userQuery = instance.buildQuery(topLevel, searcher);

        assertEquals("#((uriAncestors:/a uri:/a) (-uri:/a/b #*:*)) #uri:/x uri:/y -uri:/z", userQuery.toString());
    }

    @Test
    public void testInvert() {
        org.apache.lucene.search.Query q = new TermQuery(new Term("field", "value"));
        BooleanQuery inverted = LuceneQueryBuilder.invert(q);
        assertTrue(inverted instanceof BooleanQuery);
        assertEquals(1, inverted.clauses().size());
        assertTrue(inverted.clauses().get(0).isProhibited());
    }

}
