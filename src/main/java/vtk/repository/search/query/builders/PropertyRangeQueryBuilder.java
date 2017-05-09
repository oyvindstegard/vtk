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

import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;
import vtk.repository.index.mapping.PropertyFields;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.query.PropertyRangeQuery;
import vtk.repository.search.query.QueryBuilder;
import vtk.repository.search.query.QueryBuilderException;

/**
 * 
 * @author oyviste
 */
public class PropertyRangeQueryBuilder implements QueryBuilder {

    private final PropertyRangeQuery prq;
    private final PropertyFields fvm;
    private final PropertyTypeDefinition def;
    
    public PropertyRangeQueryBuilder(PropertyRangeQuery prq, PropertyTypeDefinition def, PropertyFields fvm) {
        this.prq = prq;
        this.fvm = fvm;
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
        }
        
        switch (valueType) {
        case STRING:
        case HTML:
        case JSON:
        case IMAGE_REF:
        case BOOLEAN:
        case PRINCIPAL:
            return stringRangeQuery(fieldName, fromValue, toValue, inclusive);
            
        case DATE:
        case TIMESTAMP:
            return dateRangeQuery(fieldName, fromValue, toValue, inclusive);
            
        case INT:
            return intRangeQuery(fieldName, fromValue, toValue, inclusive);
            
        case LONG:
            return longRangeQuery(fieldName, fromValue, toValue, inclusive);
            
        default:
            throw new QueryBuilderException("Unknown or unsupported value type " + valueType);
            
        }

    }
    
    // TODO Considering using sort-field for string term range, for consistency with sorting order.
    //      (Will then need to encode terms as collation keys.)
    private Query stringRangeQuery(String fieldName, String fromValue, String toValue, boolean inclusive) {
        return TermRangeQuery.newStringRange(fieldName, fromValue, toValue, inclusive, inclusive);
    }
    
    private Query dateRangeQuery(String fieldName, String fromValue, String toValue, boolean inclusive) {
        Long fromLong = fromValue != null ? fvm.parseDate(fromValue) : null;
        Long toLong = toValue != null ? fvm.parseDate(toValue) : null;
        return NumericRangeQuery.newLongRange(fieldName, fromLong, toLong, inclusive, inclusive);
    }
    
    private Query intRangeQuery(String fieldName, String fromValue, String toValue, boolean inclusive) {
        Integer fromInt = fromValue != null ? Integer.parseInt(fromValue) : null;
        Integer toInt = toValue != null ? Integer.parseInt(toValue) : null;
        return NumericRangeQuery.newIntRange(fieldName, fromInt, toInt, inclusive, inclusive);
    }

    private Query longRangeQuery(String fieldName, String fromValue, String toValue, boolean inclusive) {
        Long fromLong = fromValue != null ? Long.parseLong(fromValue) : null;
        Long toLong = toValue != null ? Long.parseLong(toValue) : null;
        return NumericRangeQuery.newLongRange(fieldName, fromLong, toLong, inclusive, inclusive);
    }
}
