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
package org.vortikal.repositoryimpl.search.query.builders;

import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryFilter;
import org.apache.lucene.search.TermQuery;
import org.vortikal.repository.HierarchicalVocabulary;
import org.vortikal.repository.search.query.TermOperator;
import org.vortikal.repositoryimpl.search.query.InversionFilter;
import org.vortikal.repositoryimpl.search.query.QueryBuilder;
import org.vortikal.repositoryimpl.search.query.QueryBuilderException;

public class HierarchicalTermQueryBuilder implements QueryBuilder {

    private HierarchicalVocabulary hierarchicalVocabulary;
    private final TermOperator operator;
    private final String fieldName;
    private final String term;
    
    public HierarchicalTermQueryBuilder(HierarchicalVocabulary hierarchicalVocabulary, TermOperator operator, String fieldName, String term) {
        this.hierarchicalVocabulary = hierarchicalVocabulary;
        this.operator = operator;
        this.fieldName = fieldName;
        this.term = term; 
    }

    public Query buildQuery() {
        if (this.operator == TermOperator.IN) {
            return getInQuery();
        } else if (this.operator == TermOperator.NI) {
            Filter filter = new InversionFilter(new QueryFilter(getInQuery()));
            return new ConstantScoreQuery(filter);
        } else {
            throw new QueryBuilderException("Unsupported type operator: " + this.operator);
        }

    }

    private Query getInQuery() {
        BooleanQuery bq = new BooleanQuery(true);
        bq.add(new TermQuery(new Term(fieldName, term)),
                            BooleanClause.Occur.SHOULD);

        List<String> descendantNames = this.hierarchicalVocabulary.getDescendantsAndSelf(term);
        
        if (descendantNames != null) {
            for (String descendantName: descendantNames) {
                Term t = new Term(fieldName, descendantName);
                bq.add(new TermQuery(t),  BooleanClause.Occur.SHOULD);
            }
        }
        
        return bq;
    }

}
