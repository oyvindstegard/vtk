/* Copyright (c) 2006, University of Oslo, Norway
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.springframework.beans.factory.annotation.Required;
import vtk.repository.resourcetype.HierarchicalVocabulary;
import vtk.repository.Path;
import vtk.repository.PropertySetImpl;
import vtk.repository.ResourceTypeTree;
import vtk.repository.Vocabulary;
import vtk.repository.index.mapping.DocumentMapper;
import vtk.repository.index.mapping.PropertyFields;
import vtk.repository.index.mapping.ResourceFields;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.search.Search;
import vtk.repository.search.Sorting;
import vtk.repository.search.query.builders.ACLInheritedFromQueryBuilder;
import vtk.repository.search.query.builders.ACLReadForAllQueryBuilder;
import vtk.repository.search.query.builders.AclPrivilegeQueryBuilder;
import vtk.repository.search.query.builders.NamePrefixQueryBuilder;
import vtk.repository.search.query.builders.NameRangeQueryBuilder;
import vtk.repository.search.query.builders.NameTermQueryBuilder;
import vtk.repository.search.query.builders.NameWildcardQueryBuilder;
import vtk.repository.search.query.builders.PropertyExistsQueryBuilder;
import vtk.repository.search.query.builders.PropertyPrefixQueryBuilder;
import vtk.repository.search.query.builders.PropertyRangeQueryBuilder;
import vtk.repository.search.query.builders.PropertyTermQueryBuilder;
import vtk.repository.search.query.builders.PropertyWildcardQueryBuilder;
import vtk.repository.search.query.builders.QueryTreeBuilder;
import vtk.repository.search.query.builders.TermsQueryBuilder;
import vtk.repository.search.query.builders.TypeTermQueryBuilder;
import vtk.repository.search.query.builders.UriDepthQueryBuilder;
import vtk.repository.search.query.builders.UriPrefixQueryBuilder;
import vtk.repository.search.query.builders.UriSetQueryBuilder;
import vtk.repository.search.query.builders.UriTermQueryBuilder;
import vtk.repository.search.query.security.AuthorizationFilterQueryFactory;

/**
 * Build instances of {@link org.apache.lucene.search.Query} and
 * {@link org.apache.lucene.search.Filter} from our own query types.
 */
public class LuceneQueryBuilder {

    private DocumentMapper documentMapper;
    private AuthorizationFilterQueryFactory authorizationFilterQueryFactory;
    private PropertyTypeDefinition publishedPropDef;
    private PropertyTypeDefinition unpublishedCollectionPropDef;
    private ResourceTypeTree resourceTypeTree;

    /**
     * Build a Lucene {@link org.apache.lucene.search.Query} for a given
     * <code>{@link vtk.repository.search.query.Query}</code>.
     *
     * @param vtkQuery input query
     * @param searcher current index searcher instance
     * @return a Lucene query
     * @throws QueryBuilderException
     */
    public org.apache.lucene.search.Query buildQuery(Query vtkQuery, IndexSearcher searcher) throws QueryBuilderException {

        QueryBuilder builder = null;

        if (vtkQuery instanceof AbstractMultipleQuery) {
            builder = new QueryTreeBuilder(this, searcher, (AbstractMultipleQuery) vtkQuery);
        } else if (vtkQuery instanceof AbstractPropertyQuery) {
            builder = getAbstractPropertyQueryBuilder(vtkQuery);
        } else if (vtkQuery instanceof UriTermQuery) {
            builder = new UriTermQueryBuilder((UriTermQuery) vtkQuery);
        } else if (vtkQuery instanceof UriPrefixQuery) {
            UriPrefixQuery uriPrefixQuery = (UriPrefixQuery) vtkQuery;
            builder = new UriPrefixQueryBuilder(uriPrefixQuery);
        } else if (vtkQuery instanceof UriDepthQuery) {
            builder = new UriDepthQueryBuilder((UriDepthQuery)vtkQuery, documentMapper.getResourceFields());
        } else if (vtkQuery instanceof UriSetQuery) {
            builder = new UriSetQueryBuilder((UriSetQuery) vtkQuery);
        } else if (vtkQuery instanceof NameTermQuery) {
            builder = new NameTermQueryBuilder((NameTermQuery) vtkQuery, documentMapper.getResourceFields());
        } else if (vtkQuery instanceof NameRangeQuery) {
            builder = new NameRangeQueryBuilder((NameRangeQuery) vtkQuery, documentMapper.getResourceFields());
        } else if (vtkQuery instanceof NamePrefixQuery) {
            builder = new NamePrefixQueryBuilder((NamePrefixQuery) vtkQuery);
        } else if (vtkQuery instanceof NameWildcardQuery) {
            builder = new NameWildcardQueryBuilder((NameWildcardQuery) vtkQuery);
        } else if (vtkQuery instanceof TypeTermQuery) {
            TypeTermQuery ttq = (TypeTermQuery) vtkQuery;
            builder = new TypeTermQueryBuilder(ttq.getTerm(), ttq.getOperator());
        } else if (vtkQuery instanceof AbstractAclQuery) {
            builder = getACLQueryBuilder(vtkQuery, searcher);
        } else if (vtkQuery instanceof MatchAllQuery) {
            return new MatchAllDocsQuery();
        } else if (vtkQuery == null) {
            return new MatchNoDocsQuery("Input user query was null");
        }

        if (builder == null) {
            throw new QueryBuilderException("Unsupported query type: " + vtkQuery.getClass().getName());
        }

        return adjustPureNegativeQuery(builder.buildQuery());
    }

    /**
     * Build a {@link org.apache.lucene.search.Query} that should be used to
     * filter the search according to security token and search settings.
     *
     * <p>May return empty optional if there is no need to apply a filter.
     *
     * @param token security token used for search
     * @param search search object
     * @return a {@link Query} for filtering the search, or {@code null} if no
     * search filter query applies
     */
    public Optional<org.apache.lucene.search.Query> buildSearchFilterQuery(String token, Search search) throws QueryBuilderException {

        final BooleanQuery.Builder filterBuilder = new BooleanQuery.Builder();

        authorizationFilterQueryFactory.authorizationFilterQuery(token).ifPresent(
                authFilterQuery -> filterBuilder.add(authFilterQuery, BooleanClause.Occur.FILTER));

        if (search.hasFilterFlag(Search.FilterFlag.UNPUBLISHED)) {
            filterBuilder.add(buildFilterQueryMatchingPublishedResources(), BooleanClause.Occur.FILTER);
        }
        if (search.hasFilterFlag(Search.FilterFlag.UNPUBLISHED_COLLECTIONS)) {
            filterBuilder.add(buildFilterQueryMatchingUnpublishedCollections(), BooleanClause.Occur.MUST_NOT);
        }

        BooleanQuery bq = filterBuilder.build();
        if (bq.clauses().isEmpty()) {
            return Optional.empty();
        } else if (bq.clauses().size() == 1 && !bq.clauses().get(0).isProhibited()) {
            return Optional.of(bq.clauses().get(0).getQuery());
        } else {
            return Optional.of(bq);
        }
    }

    /**
     * Combine a "user query" with a filter query.
     *
     * <p>This method assumes that scoring is of no interest, which is typically
     * the case for VTK metadata queries.
     *
     * @param userQuery a user query, not {@code null}
     * @param filterQuery a filter query, not {@code null}
     * @return a query combining the user query with the filter query
     */
    public org.apache.lucene.search.Query combineQueryWithFilter(org.apache.lucene.search.Query userQuery,
            org.apache.lucene.search.Query filterQuery) {

        return new BooleanQuery.Builder()
                .add(userQuery, BooleanClause.Occur.FILTER) // FILTER instead of MUST, since we don't need scores
                .add(filterQuery, BooleanClause.Occur.FILTER)
                .build();
    }

    // Check for top level pure negative boolean query, which will need an adjustment
    // to be able to match the logically inverse set of documents
    private org.apache.lucene.search.Query adjustPureNegativeQuery(org.apache.lucene.search.Query query) {
        if (query instanceof BooleanQuery) {
            BooleanQuery topLevelBooleanQuery = (BooleanQuery)query;
            if (topLevelBooleanQuery.clauses().isEmpty()) {
                // Hmm, empty boolean query at top level "()" ? Default to how Lucene matches such strangeness.
                return query;
            }

            boolean haveProhibitedClauses = false;
            boolean haveRequiredClauses = false;
            for (BooleanClause c: topLevelBooleanQuery) {
                if (c.isProhibited()) {
                    haveProhibitedClauses = true;
                } else if (c.isRequired()) {
                    haveRequiredClauses = true;
                }
            }
            if (haveProhibitedClauses && !haveRequiredClauses) {
                BooleanQuery.Builder b = new BooleanQuery.Builder();
                for (BooleanClause c: topLevelBooleanQuery) {
                    b.add(c);
                }
                b.add(new MatchAllDocsQuery(), BooleanClause.Occur.FILTER);
                query = b.build();
            }
        }

        return query;
    }

    private QueryBuilder getACLQueryBuilder(Query query, IndexSearcher searcher) {
        if (query instanceof AclExistsQuery) {
            AclExistsQuery aclExistsQuery = (AclExistsQuery) query;

            return new ACLInheritedFromQueryBuilder(PropertySetImpl.NULL_RESOURCE_ID, aclExistsQuery.isInverted());
        }

        if (query instanceof AclPrivilegeQuery) {
            return new AclPrivilegeQueryBuilder((AclPrivilegeQuery) query);
        }

        if (query instanceof AclInheritedFromQuery) {
            AclInheritedFromQuery aclIHFQuery = (AclInheritedFromQuery) query;

            return new ACLInheritedFromQueryBuilder(getResourceIdFromIndex(aclIHFQuery.getUri(), searcher),
                    aclIHFQuery.isInverted());
        }

        if (query instanceof AclReadForAllQuery) {
            return new ACLReadForAllQueryBuilder(((AclReadForAllQuery) query).isInverted(),
                    this.authorizationFilterQueryFactory, searcher);
        }

        return null;
    }

    private QueryBuilder getAbstractPropertyQueryBuilder(Query query) throws QueryBuilderException {

        final AbstractPropertyQuery apq = (AbstractPropertyQuery) query;
        final String cva = apq.complexValueAttributeSpecifier().orElse(null);
        final PropertyType.Type type = apq.type();
        final PropertyTypeDefinition propDef = resourceTypeTree.getPropertyTypeDefinition(apq.namespace(), apq.name());
        if (!(cva == null ^ type == PropertyType.Type.JSON) && !(query instanceof PropertyExistsQuery)) {
            throw new QueryBuilderException(
                    "Attribute specifier (..@attr) is required for all JSON-property queries except EXISTS, and forbidden for other types.");
        }

        if (query instanceof PropertyTermQuery) {
            PropertyTermQuery ptq = (PropertyTermQuery) query;
            TermOperator op = ptq.getOperator();

            if (op == TermOperator.IN || op == TermOperator.NI) {
                if (type != PropertyType.Type.STRING) {
                    throw new QueryBuilderException("Operators IN or NI only supported for properties of type STRING");
                }

                // XXX not entirely sure this code path is in use.
                // (Only in theory do we have propdefs with a hierarchical value vocab)
                Vocabulary<Value> vocabulary = propDef.getVocabulary();
                if (!(vocabulary instanceof HierarchicalVocabulary<?>)) {
                    throw new QueryBuilderException("Property type doesn't have a hierachical vocabulary: " + propDef);
                }
                HierarchicalVocabulary<Value> hv = (HierarchicalVocabulary<Value>) vocabulary;
                String fieldName = PropertyFields.propertyFieldName(propDef, false);
                List<String> values = new ArrayList<>();
                values.add(ptq.getTerm());
                for (Value v : hv.flattenedDescendants(new Value(ptq.getTerm(), PropertyType.Type.STRING))) {
                    values.add(v.getStringValue());
                }

                return new TermsQueryBuilder(fieldName, values, op == TermOperator.NI);
            } else if (op == TermOperator.GE || op == TermOperator.GT) {
                // Convert to PropertyRangeQuery
                PropertyRangeQuery prq = new PropertyRangeQuery(ptq.name(), ptq.namespace(), ptq.type(), ptq.complexValueAttributeSpecifier().orElse(null),
                        ptq.getTerm(), null, op == TermOperator.GE);

                return new PropertyRangeQueryBuilder(prq, propDef, documentMapper.getPropertyFields());
            } else if (op == TermOperator.LE || op == TermOperator.LT) {
                // Convert to PropertyRangeQuery
                PropertyRangeQuery prq = new PropertyRangeQuery(ptq.name(), ptq.namespace(), ptq.type(), ptq.complexValueAttributeSpecifier().orElse(null),
                        null, ptq.getTerm(), op == TermOperator.LE);

                return new PropertyRangeQueryBuilder(prq, propDef, documentMapper.getPropertyFields());

            } else {
                return new PropertyTermQueryBuilder(ptq, propDef, documentMapper.getPropertyFields());
            }
        }

        if (query instanceof PropertyPrefixQuery) {
            return new PropertyPrefixQueryBuilder((PropertyPrefixQuery) query, propDef);
        }

        if (query instanceof PropertyRangeQuery) {
            return new PropertyRangeQueryBuilder((PropertyRangeQuery) query, propDef, documentMapper.getPropertyFields());
        }

        if (query instanceof PropertyWildcardQuery) {
            return new PropertyWildcardQueryBuilder((PropertyWildcardQuery) query, propDef);
        }

        if (query instanceof PropertyExistsQuery) {
            PropertyExistsQuery peq = (PropertyExistsQuery) query;
            return new PropertyExistsQueryBuilder(peq, propDef, documentMapper.getPropertyFields());
        }

        throw new QueryBuilderException("Unsupported property query type: " + query.getClass().getName());
    }

    private int getResourceIdFromIndex(Path uri, IndexSearcher searcher) throws QueryBuilderException {
        TermQuery tq = new TermQuery(new Term(ResourceFields.URI_FIELD_NAME, uri.toString()));
        try {
            TopDocs docs = searcher.search(tq, 1);
            if (docs.scoreDocs.length == 1) {
                Document doc = searcher.doc(docs.scoreDocs[0].doc, Collections.singleton(ResourceFields.ID_FIELD_NAME));
                String id = doc.get(ResourceFields.ID_FIELD_NAME);
                if (id != null) {
                    return Integer.parseInt(id);
                }
            }

            return PropertySetImpl.NULL_RESOURCE_ID;

        } catch (IOException io) {
            throw new QueryBuilderException("IOException while building query: " + io.getMessage());
        }
    }


    /**
     * Invert provided query by wrapping it in a {@code BooleanQuery} as a single
     * {@link BooleanClause.Occur#MUST_NOT} clause.
     *
     * <p>Note that if the {@code Query} is to be used at top level, another {@link MatchAllDocsQuery}
     * clause must be added to the returned {@code BooleanQuery}, otherwise no
     * docs will match. (Pure negative boolean queries will match zero docs in Lucene and has prohibition semantics,
     * rather than actual matching of the theoretical inverse set.)
     * @param q the query to invert
     * @return a {@code BooleanQuery} requiring that docs matched by the wrapped
     * query must not occur in the results.
     */
    public static org.apache.lucene.search.BooleanQuery invert(org.apache.lucene.search.Query q) {
        return new BooleanQuery.Builder().add(q, BooleanClause.Occur.MUST_NOT).build();
    }

    private org.apache.lucene.search.Query buildFilterQueryMatchingPublishedResources() {
        PropertyTermQuery ptq = new PropertyTermQuery(publishedPropDef, "true", TermOperator.EQ);
        return new PropertyTermQueryBuilder(ptq, publishedPropDef, documentMapper.getPropertyFields()).buildQuery();
    }

    private org.apache.lucene.search.Query buildFilterQueryMatchingUnpublishedCollections() {
        PropertyTermQuery ptq = new PropertyTermQuery(unpublishedCollectionPropDef, "true", TermOperator.EQ);
        return new PropertyTermQueryBuilder(ptq, unpublishedCollectionPropDef, documentMapper.getPropertyFields()).buildQuery();
    }

    /**
     * Build a {@link org.apache.lucene.search.Sort} from given
     * {@link vtk.repository.search.Sorting}.
     *
     * @param sort
     * @return an optional lucene Sort instance with at least one field, or
     * empty if no sort fields are present or sorting is {@code null}
     */
    public Optional<org.apache.lucene.search.Sort> buildSort(Sorting sort) {
        if (sort == null || sort.getSortFields().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new SortBuilder().buildSort(sort));
    }

    @Required
    public void setAuthorizationFilterQueryFactory(AuthorizationFilterQueryFactory factory) {
        this.authorizationFilterQueryFactory = factory;
    }

    @Required
    public void setPublishedPropDef(PropertyTypeDefinition publishedPropDef) {
        this.publishedPropDef = publishedPropDef;
    }

    @Required
    public void setUnpublishedCollectionPropDef(PropertyTypeDefinition unpublishedCollectionPropDef) {
        this.unpublishedCollectionPropDef = unpublishedCollectionPropDef;
    }

    @Required
    public void setDocumentMapper(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }
}
