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

import org.apache.lucene.index.Term;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import vtk.repository.index.mapping.PropertyFields;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.query.PropertyWildcardQuery;
import vtk.repository.search.query.QueryBuilder;
import vtk.repository.search.query.QueryBuilderException;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.filter.FilterFactory;

/**
 * 
 * @author oyviste
 *
 */
public class PropertyWildcardQueryBuilder implements QueryBuilder {

    private PropertyWildcardQuery query;
    private PropertyTypeDefinition def;
    
    public PropertyWildcardQueryBuilder(PropertyWildcardQuery query, PropertyTypeDefinition def) {
        this.query = query;
        this.def = def;
    }

    @Override
    public Query buildQuery() throws QueryBuilderException {
        
        String wildcard = this.query.getTerm();

        if (! (def.getType() == Type.PRINCIPAL ||
                def.getType() == Type.STRING ||
                def.getType() == Type.HTML ||
                def.getType() == Type.JSON)) {
             throw new QueryBuilderException("Wildcard queries are only supported for "
                 + "property types PRINCIPAL, STRING, HTML and JSON w/attribute specifier. "
                 + "Use range queries for dates and numbers.");
         }

        TermOperator op = query.getOperator();

        boolean ignorecase = (op == TermOperator.EQ_IGNORECASE || op == TermOperator.NE_IGNORECASE);
        boolean invert = (op == TermOperator.NE || op == TermOperator.NE_IGNORECASE);
        
        String fieldName = PropertyFields.propertyFieldName(def, ignorecase);
        if (def.getType() == Type.JSON && query.complexValueAttributeSpecifier().isPresent()) {
            fieldName = PropertyFields.jsonFieldName(def,
                    query.complexValueAttributeSpecifier().get(), ignorecase);
        }

        Term wTerm = new Term(fieldName, (ignorecase ? wildcard.toLowerCase() : wildcard));

        Filter filter = FilterFactory.wildcardFilter(wTerm);
        if (invert) {
            filter = FilterFactory.inversionFilter(filter);
        }
        
        return new ConstantScoreQuery(filter);
    }

}
