/* Copyright (c) 2010, University of Oslo, Norway
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

import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.util.text.Json;
import vtk.web.RequestContext;
import vtk.web.RequestContext.RepositoryTraversal;
import vtk.web.RequestContext.TraversalCallback;

public class PropertyAspectResolver {

    private PropertyTypeDefinition aspectsPropdef;
    PropertyAspectDescription fieldConfig;
    private String token = null;
    
    public PropertyAspectResolver(PropertyTypeDefinition aspectsPropdef, PropertyAspectDescription fieldConfig, String token) {
        this.aspectsPropdef = aspectsPropdef;
        this.fieldConfig = fieldConfig;
        this.token = token;
    }

    public Json.MapContainer resolve(final Path uri, final String aspect) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        final Json.MapContainer result = new Json.MapContainer();
        String token = this.token != null ? this.token : requestContext.getSecurityToken();
        RepositoryTraversal traversal = requestContext.rootTraversal(token, uri);

        traversal.traverse(new TraversalCallback() {
            @Override
            public boolean callback(Resource resource) {
                Property property = resource.getProperty(aspectsPropdef);
                if (property != null) {
                    Json.MapContainer value = property.getJSONValue();
                    
                    if (value.get(aspect) != null) {
                        value = value.objectValue(aspect);

                        for (PropertyAspectField field : fieldConfig.getFields()) {
                            String key = field.getIdentifier();
                            Object newValue = value.get(key);

                            if (resource.getURI().equals(uri)) {
                                result.put(key, newValue);
                                
                            } else if (field.isInherited() && result.get(key) == null) {
                                result.put(key, newValue);
                            }
                        }
                    }
                }
                return true;
            }

            @Override
            public boolean error(Path uri, Throwable error) {
                return false;
            }});
        return result;
    }
 }
