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
import vtk.repository.index.mapping.PropertyFields;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.query.LuceneQueryBuilder;
import vtk.repository.search.query.PropertyTermQuery;
import vtk.repository.search.query.QueryBuilder;
import vtk.repository.search.query.QueryBuilderException;
import vtk.repository.search.query.TermOperator;

/**
 * Builds property value term queries.
 * 
 * <p>Supports the following term operators:
 * 
 * <ul>
 *   <li>{@link TermOperator#EQ}
 *   <li>{@link TermOperator#EQ_IGNORECASE}
 *   <li>{@link TermOperator#NE}
 *   <li>{@link TermOperator#NE_IGNORECASE}
 * </ul>
 * 
 */
public class PropertyTermQueryBuilder implements QueryBuilder {

    private final PropertyTermQuery ptq;
    private PropertyFields pf;
    private PropertyTypeDefinition def;
    
    public PropertyTermQueryBuilder(PropertyTermQuery ptq, PropertyTypeDefinition def, PropertyFields fvm) {
        this.ptq = ptq;
        this.pf = fvm;
        this.def = def;
    }

    @Override
    public Query buildQuery() throws QueryBuilderException {

        final TermOperator op = ptq.getOperator();
        final boolean lowercase = (op == TermOperator.EQ_IGNORECASE || op == TermOperator.NE_IGNORECASE);
        final String cva = ptq.complexValueAttributeSpecifier().orElse(null);
        final String fieldValue = ptq.getTerm();
        
        final PropertyType.Type valueType;
        final String fieldName;
        if (cva != null) {
            valueType = PropertyFields.jsonFieldDataType(def, cva);
            fieldName = PropertyFields.jsonFieldName(def, cva, lowercase);
        } else {
            valueType = def.getType();
            fieldName = PropertyFields.propertyFieldName(def, lowercase);
        }

        Query q = pf.propertyFieldQuery(fieldName, fieldValue, valueType, lowercase);

        if (op == TermOperator.NE || op == TermOperator.NE_IGNORECASE) {
            q = LuceneQueryBuilder.invert(q);
        } else if (op != TermOperator.EQ && op != TermOperator.EQ_IGNORECASE) {
            throw new QueryBuilderException("Term operator " + op + " not supported by this builder.");
        }
        
        return q;
    }

}
