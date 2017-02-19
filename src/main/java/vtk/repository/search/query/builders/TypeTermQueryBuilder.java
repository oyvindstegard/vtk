/* Copyright (c) 2007-2017, University of Oslo, Norway
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

import static vtk.repository.search.query.TermOperator.EQ;
import static vtk.repository.search.query.TermOperator.NE;
import static vtk.repository.search.query.TermOperator.IN;
import static vtk.repository.search.query.TermOperator.NI;

import org.apache.lucene.index.Term;
import org.apache.lucene.queries.TermFilter;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import vtk.repository.index.mapping.ResourceFields;
import vtk.repository.search.query.QueryBuilder;
import vtk.repository.search.query.QueryBuilderException;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.filter.FilterFactory;

public class TypeTermQueryBuilder implements QueryBuilder {

    private final Object term;
    private final TermOperator op;
    
    public TypeTermQueryBuilder(Object term, TermOperator op) {
        switch (op) {
            case EQ:
            case NE:
            case IN:
            case NI:
                break;
            default:
                throw new QueryBuilderException("Unsupported type operator: " + op);
        }

        this.term = term;
        this.op = op;
    }

    @Override
    public Query buildQuery() throws QueryBuilderException {
        String fieldName;
        if (op == EQ || op == NE) {
            fieldName = ResourceFields.RESOURCETYPE_NAME_FIELD_NAME;
        } else {
            fieldName = ResourceFields.RESOURCETYPES_FIELD_NAME;
        }

        Term indexTerm = new Term(fieldName, this.term.toString());
        Filter filter = new TermFilter(indexTerm);
        
        if (op == NE || op == NI) {
            filter = FilterFactory.inversionFilter(filter);
        }

        return new ConstantScoreQuery(filter);
    }

}
