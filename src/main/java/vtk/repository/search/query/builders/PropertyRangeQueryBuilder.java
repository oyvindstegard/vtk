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
import vtk.repository.search.query.PropertyRangeQuery;
import vtk.repository.search.query.QueryBuilder;
import vtk.repository.search.query.QueryBuilderException;

/**
 * 
 */
public class PropertyRangeQueryBuilder implements QueryBuilder {

    private final PropertyRangeQuery prq;
    private final PropertyFields pf;
    private final PropertyTypeDefinition def;
    
    public PropertyRangeQueryBuilder(PropertyRangeQuery prq, PropertyTypeDefinition def, PropertyFields fvm) {
        this.prq = prq;
        this.pf = fvm;
        this.def = def;
    }

    @Override
    public Query buildQuery() throws QueryBuilderException {

        final String fromValue = prq.getFromTerm();
        final String toValue = prq.getToTerm();
        if (fromValue == null && toValue == null) {
            throw new QueryBuilderException("At least one value has to be set for either upper or lower bound");
        }
        final String cva = prq.complexValueAttributeSpecifier().orElse(null);
        final boolean inclusive = prq.isInclusive();
        
        final String fieldName;
        final PropertyType.Type valueType;
        if (cva != null) {
            valueType = PropertyFields.jsonFieldDataType(def, cva);
            fieldName = PropertyFields.jsonFieldName(def, cva, false);
        } else {
            valueType = def.getType();
            fieldName = PropertyFields.propertyFieldName(def, false);
            if (valueType == PropertyType.Type.JSON) {
                throw new QueryBuilderException("Cannot range query on JSON type without complex attribute specifier");
            }
        }
    
        // TODO Consider using sort-field for string term ranges, for consistency with sorting order.

        return pf.propertyFieldRangeQuery(fieldName, fromValue, toValue, inclusive, inclusive, valueType, false);
    }
    
}
