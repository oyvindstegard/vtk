/* Copyright (c) 2006-2017, University of Oslo, Norway
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
package vtk.repository.search.query.builders;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.util.BytesRef;
import vtk.repository.index.mapping.ResourceFields;
import vtk.repository.search.query.AbstractMultipleQuery;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.LuceneQueryBuilder;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.Query;
import vtk.repository.search.query.QueryBuilder;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.UriPrefixQuery;
import vtk.repository.search.query.UriSetQuery;
import vtk.repository.search.query.UriTermQuery;

/**
 * Handles recursive building of a query tree with some rewrites along the way.
 */
public class QueryTreeBuilder implements QueryBuilder {

    private final AbstractMultipleQuery query;
    private final LuceneQueryBuilder mainBuilder;
    private final IndexSearcher searcher;
    
    public QueryTreeBuilder(LuceneQueryBuilder mainBuilder,
            IndexSearcher searcher, AbstractMultipleQuery query) {
        this.query = query;
        this.mainBuilder = mainBuilder;
        this.searcher = searcher;
    }

    @Override
    public org.apache.lucene.search.Query buildQuery() {
        return buildInternal(this.query);
    }

    private org.apache.lucene.search.Query buildInternal(Query query) {
        final Occur occur;
        if (query instanceof AndQuery) {
            occur = BooleanClause.Occur.FILTER;
        } else if (query instanceof OrQuery) {
            org.apache.lucene.search.Query optimized = maybeOptimizeUriQueryClauses((OrQuery)query);
            if (optimized != null) {
                return optimized;
            }
            
            occur = BooleanClause.Occur.SHOULD;
        } else {
            return this.mainBuilder.buildQuery(query, searcher);
        }

        AbstractMultipleQuery multipleQuery = (AbstractMultipleQuery)query;
        List<Query> subQueries = multipleQuery.getQueries();
        
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (Query q: subQueries) {
            builder.add(buildInternal(q), occur);
        }

        return maybeRewriteInvertedClauses(builder.build(), occur);
    }

    // Possibly unwrap inverted queries following the pattern "#... #(-some:query #*:*)", into "#... -some:query"
    // This can weed out some unnecessary match-all-docs clauses in the query tree
    private org.apache.lucene.search.Query maybeRewriteInvertedClauses(BooleanQuery bq, BooleanClause.Occur occur) {
        if (occur == Occur.SHOULD) {
            return bq;
        }

        BooleanQuery.Builder rewriteBuilder = new BooleanQuery.Builder();
        for (BooleanClause bc : bq) {
            if (bc.getQuery() instanceof BooleanQuery) {
                BooleanQuery subQuery = (BooleanQuery)bc.getQuery();
                if (subQuery.clauses().size() == 2
                        && subQuery.clauses().get(0).isProhibited()
                        && subQuery.clauses().get(1).getQuery() instanceof MatchAllDocsQuery) {

                    rewriteBuilder.add(subQuery.clauses().get(0).getQuery(), BooleanClause.Occur.MUST_NOT);
                                                                          // ^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Yoda says so.
                    continue;
                }
            }
            rewriteBuilder.add(bc);
        }

        // Note that rewritten BooleanQuery at this stage may be purely negative (only prohibited clauses),
        // which will not match any documents, however LuceneQueryBuilder does a final fixup
        // of those cases at the top level.
        return rewriteBuilder.build();
    }
    
    // Check if we can optimize a set of URI queries by collapsing into TermInSetQuery instances
    // instead of one term query per boolean clause.
    private org.apache.lucene.search.Query maybeOptimizeUriQueryClauses(OrQuery orQuery) {
        List<String> uriTerms = null;
        List<String> uriPrefixTerms = null;
        for (Query subQuery : orQuery.getQueries()) {
            if (subQuery instanceof UriTermQuery) {
                UriTermQuery utq = (UriTermQuery)subQuery;
                if (utq.getOperator() == TermOperator.NE) {
                    return null;
                }
                if (uriTerms == null) uriTerms = new ArrayList<>();
                uriTerms.add(utq.getUri());
            } else if (subQuery instanceof UriPrefixQuery) {
                UriPrefixQuery upq = (UriPrefixQuery)subQuery;
                if (upq.isInverted()) {
                    return null;
                }
                if (uriPrefixTerms == null) uriPrefixTerms = new ArrayList<>();
                uriPrefixTerms.add(upq.getUri());
                if (upq.isIncludeSelf()) {
                    if (uriTerms == null) uriTerms = new ArrayList<>();
                    uriTerms.add(upq.getUri());
                }
            } else if (subQuery instanceof UriSetQuery) {
                UriSetQuery usq = (UriSetQuery)subQuery;
                if (usq.getOperator() == TermOperator.NI) {
                    return null;
                }
                if (uriTerms == null) uriTerms = new ArrayList<>();
                uriTerms.addAll(usq.getUris());
            } else {
                return null;
            }
        }
        
        TermInSetQuery uriSetQuery = null;
        TermInSetQuery uriPrefixSetQuery = null;
        
        if (uriTerms != null) {
            List<BytesRef> indexTerms = uriTerms.stream().map(s -> new BytesRef(s)).collect(Collectors.toList());
            uriSetQuery = new TermInSetQuery(ResourceFields.URI_FIELD_NAME, indexTerms);
        }
        if (uriPrefixTerms != null) {
            List<BytesRef> indexTerms = uriPrefixTerms.stream().map(s -> new BytesRef(s)).collect(Collectors.toList());
            uriPrefixSetQuery = new TermInSetQuery(ResourceFields.URI_ANCESTORS_FIELD_NAME, indexTerms);
        }

        if (uriSetQuery != null && uriPrefixSetQuery != null) {
            return new BooleanQuery.Builder().add(uriSetQuery, Occur.SHOULD)
                                             .add(uriPrefixSetQuery, Occur.SHOULD).build();
        } else if (uriSetQuery != null) {
            return uriSetQuery;
        } else if (uriPrefixSetQuery != null) {
            return uriPrefixSetQuery;
        }

        return null;
    }
    
    
}
