/* Copyright (c) 2015, University of Oslo, Norway
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
package vtk.util.repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import vtk.repository.Property;
import vtk.repository.Resource;
import vtk.repository.resourcetype.BinaryValue;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.util.text.Json;

public class ResourceToMapConverter {

    
    public static final Map<String, Object> toMap(Resource resource) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("uri", resource.getURI());
        map.put("resourcetype", resource.getResourceType());
        Map<String, Object> propsMap = new LinkedHashMap<>();
        for (Property prop: resource) {
            property(prop, propsMap);
        }
        map.put("properties", propsMap);
        return map;
    }
    
    private static void property(Property property, Map<String, Object> map) {
        final PropertyTypeDefinition def = property.getDefinition();
        if (def.isMultiple()) {
            List<Object> values = new ArrayList<>();
            for (Value val: property.getValues()) {
                Object mapped = mapToBasicValue(val);
                if (mapped != null)
                    values.add(mapped);
            }
            map.put(def.getName(), values);
        } else {
            Object mapped = mapToBasicValue(property.getValue());
            if (mapped != null)
                map.put(def.getName(), mapped);
        }
    }
    
    private static Object mapToBasicValue(Value value) {
        switch (value.getType()) {
        case BOOLEAN:
            return value.getBooleanValue();
        case DATE:
        case TIMESTAMP:
            return value.getDateValue().getTime();
        case JSON:
            return value.getJSONValue();
        case INT:
            return value.getIntValue();
        case LONG:
            return value.getLongValue();
        case PRINCIPAL:
            return value.getPrincipalValue().getQualifiedName();
        case BINARY:
            try {
                return mapBinaryValue(value.getBinaryValue());
            } catch (IOException e) { return "Error; " + e.getMessage(); }
        default:
            return value.getNativeStringRepresentation();
        }
    }
    
    private static Object mapBinaryValue(BinaryValue value) throws IOException {
        if ("application/json".equals(value.getContentType())) {
            return Json.parse(value.getContentStream().getStream());
        }
       return null;
    }
    
}
