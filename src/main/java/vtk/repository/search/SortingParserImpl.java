/* Copyright (c) 2017 University of Oslo, Norway
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
package vtk.repository.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import vtk.repository.PropertySet;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.SortField.Direction;

public class SortingParserImpl implements SortingParser {
    
    private ResourceTypeTree resourceTypeTree;
    
    public SortingParserImpl(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    @Override
    public Sorting parse(String sortString) throws QueryException {
        if (sortString == null || "".equals(sortString.trim())) {
            return null;
        }

        String[] fields = sortString.split(",");
        List<SortField> result = new ArrayList<>();
        Set<String> referencedFields = new HashSet<>();
        Direction uriDirection = Direction.ASC;

        for (int i = 0; i < fields.length; i++) {
            String specifier = fields[i].trim();
            String field = null;
            Direction direction = Direction.ASC;
            String[] pair = specifier.split("\\s+");
            if (pair.length == 2) {
                field = pair[0];
                if ("descending".startsWith(pair[1]) || "DESCENDING".startsWith(pair[1])) {
                    direction = Direction.DESC;
                }
            }
            else if (pair.length == 1) {
                field = pair[0];
            }
            else {
                throw new QueryException("Invalid sort field: '" + specifier + "'");
            }
            SortField sortField = null;

            if (referencedFields.contains(field)) {
                throw new QueryException(
                        "Sort field '" + field + "' occurs more than once");
            }

            if (PropertySet.URI_IDENTIFIER.equals(field)) {
                uriDirection = direction;
                break;
            }

            if (PropertySet.TYPE_IDENTIFIER.equals(field) ||
                    PropertySet.NAME_IDENTIFIER.equals(field)) {
                sortField = new ResourceSortField(field, direction);
            }
            else {
                String prefix = null;
                String nameAndCvaSpec = null;

                String[] components = field.split(":");
                if (components.length == 2) {
                    prefix = components[0];
                    nameAndCvaSpec = components[1];
                }
                else if (components.length == 1) {
                    nameAndCvaSpec = components[0];
                }
                else {
                    throw new QueryException("Unknown sort field: '" + field + "'");
                }
                String name = nameAndCvaSpec;
                String cvaSpec = null;
                int cvaIdx = nameAndCvaSpec.indexOf('@');
                if (cvaIdx > 0 && cvaIdx < nameAndCvaSpec.length() - 1) {
                    name = nameAndCvaSpec.substring(0, cvaIdx);
                    cvaSpec = nameAndCvaSpec.substring(cvaIdx + 1);
                }

                PropertyTypeDefinition def =
                        resourceTypeTree.getPropertyDefinitionByPrefix(prefix, name);
                PropertySortField propSortField = new PropertySortField(def, direction);
                propSortField.setComplexValueAttributeSpecifier(cvaSpec);
                sortField = propSortField;
            }

            referencedFields.add(field);
            result.add(sortField);
        }

        result.add(new ResourceSortField(PropertySet.URI_IDENTIFIER, uriDirection));
        return new Sorting(result);
    }

}
