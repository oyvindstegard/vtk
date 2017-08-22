/* Copyright (c) 2012, University of Oslo, Norway
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
package vtk.repository;

import java.io.IOException;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinitionImpl;
import vtk.repository.resourcetype.Value;
import vtk.security.Principal;
import vtk.security.Principal.Type;
import vtk.security.PrincipalFactory;
import vtk.security.PrincipalImpl;


/**
 * Simple factory for creating test {@link Resource resource} objects.
 * 
 * <p>Resources are defined using a JSON format:
 * <pre>
 *   {
 *      "&lt;uri-1&gt;" : {
 *          "type" : "&lt;collection|file&gt;"
 *          "acl" : {
 *             "&lt;permission-1&gt;" : [ "&lt;principal-1&gt;", ..., "&lt;principal-N&gt;" ]
 *             ...
 *             "&lt;permission-N&gt;" : [ "&lt;principal-1&gt;", ..., "&lt;principal-N&gt;" ]
 *          }
 *          "properties" : {
 *              "&lt;property-1&gt;" : &lt;value-1&gt;
 *              ...
 *              "&lt;property-N&gt;" : &lt;value-N&gt;
 *          }
 *      }
 *   }
 * </pre>
 * </p>
 * <p>
 * Notes/limitations:
 * <ul>
 *   <li>Parent resources must be defined before children
 *   <li>All fields are optional, except for the "acl" field of the root resource
 *   <li>All properties are defined in the default namespace
 *   <li>Some property types are not supported (e.g. BINARY)
 *   <li>..
 * </u> 
 * </p>
 */
public class TestResourceFactory {

    public static interface Consumer {
        public boolean resource(ResourceImpl resource);
        public void end();
    }

    private static enum State {
        TOPLEVEL,
        IN_TYPE,
        IN_RESOURCE,
        IN_ACL,
        IN_ACL_PRINCIPAL_LIST,
        IN_PROPERTIES
    }

    public void load(Reader in, final Consumer consumer) throws IOException, ParseException {
        JSONParser parser = new JSONParser();
        parser.parse(in, new ContentHandler() {

            private State state = null;

            private Path uri;
            private boolean collection = true;
            private Map<String, Object> properties;
            private Acl acl;

            private String curKey;
            

            @Override
            public boolean startObjectEntry(String key)
                    throws ParseException, IOException {

                curKey = key;

                switch (state) {
                case TOPLEVEL:
                    uri = Path.fromString(key);
                    state = State.IN_RESOURCE;
                    return true;
                case IN_RESOURCE:
                    if ("type".equals(key)) {
                        state = State.IN_TYPE;
                        return true;
                    }
                    if ("acl".equals(key)) {
                        state = State.IN_ACL;
                        return true;
                    }
                    if ("properties".equals(key)) {
                        state = State.IN_PROPERTIES;
                        return true;
                    }
                    throw new IllegalArgumentException("Unexpected key: " + key + ", state=" + state);
                case IN_ACL:
                    state = State.IN_ACL_PRINCIPAL_LIST;
                    return true;
                case IN_ACL_PRINCIPAL_LIST:
                    return true;
                case IN_PROPERTIES:
                    return true;
                default:
                    throw new IllegalArgumentException("Unexpected key: " + key + ", state=" + state);
                }
            }

            @Override
            public boolean endObjectEntry() throws ParseException,
            IOException {
                switch (state) {
                case IN_TYPE: 
                    state = State.IN_RESOURCE;
                    return true;
                default:
                    return true;
                }
            }

            @Override
            public boolean startObject() throws ParseException, IOException {
                return true;
            }

            @Override
            public boolean endObject() throws ParseException, IOException {
                switch (state) {
                case IN_RESOURCE:
                    if (emit()) {
                        this.state = State.TOPLEVEL;
                        return true;
                    }
                    return false;
                case IN_ACL:
                    state = State.IN_RESOURCE;
                    return true;
                default:
                    state = State.IN_RESOURCE;
                    return true;
                }
            }

            @Override
            public boolean startArray() throws ParseException, IOException {
                switch (state) {
                case IN_ACL_PRINCIPAL_LIST:
                    return true;
                default:
                    throw new IllegalArgumentException("Unexpected array, state=" + state);
                }
            }

            @Override
            public boolean endArray() throws ParseException, IOException {
                switch (state) {
                case IN_ACL_PRINCIPAL_LIST:
                    state = State.IN_ACL;
                    return true;
                default:
                    throw new IllegalArgumentException("Unexpected array");
                }
            }                

            @Override
            public boolean primitive(Object value) throws ParseException,
            IOException {
                switch (state) {
                case IN_ACL_PRINCIPAL_LIST:
                    if (acl == null) acl = Acl.EMPTY_ACL;
                    Privilege priv = Privilege.forName(curKey);
                    
                    String name = value.toString();
                    Principal.Type t = null;
                    if (name.startsWith("pseudo:")) {
                        t = Type.PSEUDO;
                        name = name.substring(7);
                    } else if (name.startsWith("u:")) {
                        t = Type.USER;
                        name = name.substring(2);
                    } else if (name.startsWith("g:")) {
                        t = Type.GROUP;
                        name = name.substring(2);
                    }
                    if (t == null) {
                        throw new IllegalArgumentException("Invalid principal: " + value);
                    }
                    
                    Principal p = null;
                    if (name.equals("all") && t == Type.PSEUDO) {
                        p = PrincipalFactory.ALL;
                    } else {
                        p = new PrincipalImpl(name, t);
                    }
                    
                    acl = acl.addEntry(priv, p);
                    return true;

                case IN_PROPERTIES:
                    if (properties == null) {
                        properties = new HashMap<String, Object>();
                    }
                    properties.put(curKey, value);
                    return true;
                case IN_TYPE:
                    collection = "collection".equals(value.toString());
                    return true;
                default:
                    throw new IllegalArgumentException("Unexpected primitive: " + value + ", state=" + state);
                }
            }

            @Override
            public void startJSON() throws ParseException, IOException {
                state = State.TOPLEVEL;
            }

            @Override
            public void endJSON() throws ParseException, IOException {
            }


            private boolean emit() {
                ResourceImpl resource = buildResource();
                this.uri = null;
                this.properties = null;
                this.acl = null;

                return consumer.resource(resource);
            }
            
            
            private ResourceImpl buildResource() {
                ResourceImpl resource = new ResourceImpl(uri);
                Map<String, Object> properties = this.properties;
                if (collection) {
                    if (properties == null) {
                        properties = new HashMap<String, Object>();
                    }
                    properties.put("collection", Boolean.TRUE);
                }
                
                resource.setInheritedAcl(true);
                if (acl != null) {
                    resource.setAcl(acl);
                    resource.setInheritedAcl(false);
                }
                if (properties != null) {
                    for (String key: properties.keySet()) {
                        Object val = properties.get(key);
                        PropertyTypeDefinitionImpl def = new PropertyTypeDefinitionImpl();
                        Value value;
                        if (val instanceof Boolean) {
                            def.setType(PropertyType.Type.BOOLEAN);
                            value = new Value(val.toString(), PropertyType.Type.BOOLEAN);
                        } else if (val instanceof Long) {
                            def.setType(PropertyType.Type.LONG);
                            value = new Value(val.toString(), PropertyType.Type.LONG);
                        } else if (val instanceof Integer) {
                            def.setType(PropertyType.Type.INT);
                            value = new Value(val.toString(), PropertyType.Type.INT);
                        } else {
                            try {
                                // Try parsing as date value first and assume it is a date if parsing works out.
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                                Date d = sdf.parse(val.toString());
                                value = new Value(d, true);
                                def.setType(PropertyType.Type.DATE);
                            } catch (Exception e) {
                                value = new Value(val.toString(), PropertyType.Type.STRING);
                                def.setType(PropertyType.Type.STRING);
                            }
                        }
                        def.setName(key);
                        def.setNamespace(Namespace.DEFAULT_NAMESPACE);
                        def.afterPropertiesSet();
                        PropertyImpl p = new PropertyImpl(def);
                        p.setValue(value);
                        resource.addProperty(p);
                    }
                }
                return resource;
            }
        });
    }

}
