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
package vtk.repository.search.query.builders;

import org.apache.lucene.search.Query;
import vtk.repository.index.mapping.ResourceFields;
import vtk.repository.search.query.LuceneQueryBuilder;

import vtk.repository.search.query.NameTermQuery;
import vtk.repository.search.query.QueryBuilder;
import vtk.repository.search.query.QueryBuilderException;
import vtk.repository.search.query.TermOperator;

/**
 * 
 */
public class NameTermQueryBuilder implements QueryBuilder {

    private final NameTermQuery ntq;
    private final ResourceFields rf;
    
    public NameTermQueryBuilder(NameTermQuery q, ResourceFields rf) {
        this.ntq = q;
        this.rf = rf;
    }
    
    @Override
    public org.apache.lucene.search.Query buildQuery() {
        String term = this.ntq.getTerm();
        TermOperator op = this.ntq.getOperator();

        if (op == TermOperator.EQ || op == TermOperator.NE) {
            Query q = rf.typedFieldQuery(ResourceFields.NAME_FIELD_NAME, term, String.class, false);
            return op == TermOperator.EQ ? q : LuceneQueryBuilder.invert(q);
        }

        if (op == TermOperator.EQ_IGNORECASE || op == TermOperator.NE_IGNORECASE) {
            Query q = rf.typedFieldQuery(ResourceFields.NAME_LC_FIELD_NAME, term, String.class, true);
            return op == TermOperator.EQ_IGNORECASE ? q : LuceneQueryBuilder.invert(q);
        }

        boolean includeLower = false;
        boolean includeUpper = false;
        String upperTerm = null;
        String lowerTerm = null;
        
        if (op == TermOperator.GE) {
            lowerTerm = term;
            includeLower = true;
            includeUpper = true;
        } else if (op == TermOperator.GT) {
            lowerTerm = term;
            includeUpper = true;
        } else if (op == TermOperator.LE) {
            upperTerm = term;
            includeUpper = true;
            includeLower = true;
        } else if (op == TermOperator.LT) {
            upperTerm = term;
            includeLower = true;
        } else {
            throw new QueryBuilderException("Unknown term operator: " + op);
        }

        return rf.typedFieldRangeQuery(ResourceFields.NAME_FIELD_NAME, lowerTerm, upperTerm,
                includeLower, includeUpper, String.class, false);
    }

}
