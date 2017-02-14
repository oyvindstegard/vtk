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

import java.util.Iterator;
import vtk.repository.PropertySet;
import vtk.repository.index.mapping.PropertyFields;
import vtk.repository.index.mapping.ResourceFields;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.PropertySortField;
import vtk.repository.search.SortField;
import vtk.repository.search.SortFieldDirection;
import vtk.repository.search.Sorting;
import vtk.repository.search.TypedSortField;

/**
 * 
 */
public class SortBuilder {
 
    /**
     * Build a {@link org.apache.lucene.search.Sort Lucene sorting spec} from {@link Sorting}.
     * @param sort instance of {@link Sorting}
     * @return a corresponding instance of 
     * @throws SortBuilderException 
     */
    public org.apache.lucene.search.Sort buildSort(Sorting sort) throws SortBuilderException {

        org.apache.lucene.search.SortField[] luceneSortFields =
            new org.apache.lucene.search.SortField[sort.getSortFields().size()];

        int j = 0;
        for (Iterator<SortField> i = sort.getSortFields().iterator(); i.hasNext(); j++) {
            SortField sortField = i.next();
            String fieldName = null;
            boolean reverse = sortField.getDirection() != SortFieldDirection.ASC;

            if (sortField instanceof TypedSortField) {
                TypedSortField tsf =  (TypedSortField)sortField;
                if (PropertySet.TYPE_IDENTIFIER.equals(tsf.getType())) {
                    fieldName = ResourceFields.RESOURCETYPE_FIELD_NAME;
                } else if (PropertySet.URI_IDENTIFIER.equals(tsf.getType())) {
                    fieldName = ResourceFields.URI_SORT_FIELD_NAME;
                } else if (PropertySet.NAME_IDENTIFIER.equals(tsf.getType())) {
                    fieldName = ResourceFields.NAME_SORT_FIELD_NAME;
                } else {
                    throw new SortBuilderException("Unknown typed sort field type: " + tsf.getType());
                }
                
                luceneSortFields[j] = new org.apache.lucene.search.SortField(
                        fieldName, org.apache.lucene.search.SortField.Type.STRING, reverse);

            } else if (sortField instanceof PropertySortField) {
                PropertySortField psf = (PropertySortField)sortField;
                PropertyTypeDefinition def = psf.getDefinition();
                if (def.isMultiple()) {
                    throw new SortBuilderException("Cannot sort on multi-value property: " + def);
                }
                
                PropertyType.Type dataType = def.getType();
                fieldName = PropertyFields.sortFieldName(def);
                if (def.getType() == PropertyType.Type.JSON) {
                    String cva = psf.getComplexValueAttributeSpecifier();
                    if (cva != null) {
                        fieldName = PropertyFields.jsonSortFieldName(def, cva);
                        dataType = PropertyFields.jsonFieldDataType(def, fieldName);
                    }
                }
                
                switch (dataType) {
                case DATE:
                case TIMESTAMP:
                case LONG:
                    luceneSortFields[j] = new org.apache.lucene.search.SortField(fieldName, 
                        org.apache.lucene.search.SortField.Type.LONG, reverse);
                    break;
                    
                case INT:
                    luceneSortFields[j] = new org.apache.lucene.search.SortField(fieldName, 
                        org.apache.lucene.search.SortField.Type.INT, reverse);
                    break;
                    
                default:
                    // o.a.l.s.SortField.Type.STRING works for all dedicated sorting fields and other string types
                    luceneSortFields[j] = new org.apache.lucene.search.SortField(fieldName,
                        org.apache.lucene.search.SortField.Type.STRING, reverse);
                    
                }
            } else {
                throw new SortBuilderException("Unknown sorting type " + sortField.getClass().getName());
            }
        }

        return new org.apache.lucene.search.Sort(luceneSortFields);
    }
    
}
