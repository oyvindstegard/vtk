/* Copyright (c) 2014 University of Oslo, Norway
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
package vtk.web.actions.create;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.util.Assert;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.servlet.ModelAndView;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.TypeInfo;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.RequestContext;
import vtk.web.SimpleFormController;
import vtk.web.actions.create.CreateCollectionWithProperties.CreateOperation;
import vtk.web.service.URL;


public class CreateCollectionWithProperties extends SimpleFormController<CreateOperation> {
    
    @Override
    protected CreateOperation formBackingObject(HttpServletRequest request)
            throws Exception {
        return new CreateOperation();
    }

    @Override
    protected Map<String, Object> referenceData(HttpServletRequest request,
            CreateOperation command, Errors errors) throws Exception {
        Map<String, Object> model = new HashMap<String, Object>();
        URL submitURL = RequestContext.getRequestContext(request).getRequestURL();
        model.put("submitURL", submitURL);
        return model;
    }

    @Override
    protected ModelAndView onSubmit(HttpServletRequest request,
            HttpServletResponse response, CreateOperation operation,
            BindException errors) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        
        Resource collection = repository.createCollection(token, operation.getUri());
        if (operation.getTypeProperty() != null) {
            TypeInfo typeInfo = repository.getTypeInfo(collection);
            Namespace ns = Namespace.DEFAULT_NAMESPACE;
            Property property = typeInfo.createProperty(ns, "collection-type", operation.getTypeProperty());
            collection.addProperty(property);
            collection = repository.store(token, collection);
        }

        TypeInfo typeInfo = repository.getTypeInfo(collection);

        if (operation.getPropertyOps() != null) {
            for (PropertyOperation op : operation.getPropertyOps()) {
                Namespace ns = null;

                if (op.namespace == null || op.namespace.trim().equals(""))
                    ns = Namespace.DEFAULT_NAMESPACE;
                else if (op.namespace.startsWith("http://"))
                    ns = Namespace.getNamespace(op.namespace);
                else
                    ns = Namespace.getNamespaceFromPrefix(op.namespace);
                
                PropertyTypeDefinition propDef = typeInfo.getPropertyTypeDefinition(ns, op.name);
                if (propDef.isMultiple()) {
                    String[] values = op.values.toArray(new String[op.values.size()]);
                    Property property = typeInfo.createProperty(ns, op.name, values);
                    collection.addProperty(property);
                } else {
                    Property property = typeInfo.createProperty(ns, op.name, op.values.get(0));
                    collection.addProperty(property);
                }
            }
            repository.store(token, collection);
        }
        return new ModelAndView(getSuccessView(), new HashMap<String, Object>());
    }

    @Override
    protected ServletRequestDataBinder createBinder(HttpServletRequest request,
            final CreateOperation command) throws Exception {
        return new ServletRequestDataBinder(command) {
            @Override
            public void bind(ServletRequest request) {
                String uri = request.getParameter("uri");
                String type = request.getParameter("type");
                
                String[] namespaces = request.getParameterValues("propertyNamespace");
                String[] propNames = request.getParameterValues("propertyName");
                String[] propValues = request.getParameterValues("propertyValue");
                Assert.hasText(uri, "Input 'uri' must be defined");

                List<PropertyOperation> propertyOps = new ArrayList<PropertyOperation>();
                
                if (namespaces != null && propNames != null && propValues != null) {
                    if (namespaces.length != propNames.length || 
                            namespaces.length != propValues.length) {
                        throw new IllegalArgumentException(
                                "Inputs 'propertyNamespaces', 'propertyNames' and 'propertyValues' "
                                + "must be of the same length");
                    }
                    for (int i = 0; i < namespaces.length; i++) {
                        if (!"".equals(propNames[i].trim()) && !"".equals(propValues[i].trim())) {

                            PropertyOperation existing = null;
                            for (PropertyOperation op : propertyOps) {
                                if (op.namespace.equals(namespaces[i]) && 
                                        op.name.equals(propNames[i])) {
                                    existing = op;
                                    break;
                                }
                            }
                            if (existing != null) {
                                existing.addValue(propValues[i]);
                            } 
                            else propertyOps.add(new PropertyOperation(namespaces[i], 
                                    propNames[i], propValues[i]));
                        }
                    }
                }
                command.setUri(Path.fromString(uri));
                command.setTypeProperty(type);
                command.setPropertyOps(propertyOps);
            }
        };
    }


    public static class CreateOperation {
        Path uri;
        String typeProperty = null;
        List<PropertyOperation> propertyOps;
        
        public Path getUri() {
            return uri;
        }

        public void setUri(Path uri) {
            this.uri = uri;
        }

        public String getTypeProperty() {
            return typeProperty;
        }

        public void setTypeProperty(String typeProperty) {
            this.typeProperty = typeProperty;
        }

        public List<PropertyOperation> getPropertyOps() {
            return propertyOps;
        }

        public void setPropertyOps(List<PropertyOperation> propertyOps) {
            this.propertyOps = propertyOps;
        }

        @Override
        public String toString() {
            return "CreateOperation(uri=" + uri + ", typeProperty=" 
                    + typeProperty + ", propertyOps=" + propertyOps
                    + ")";
        }
    }

    public static class PropertyOperation {
        String namespace, name;
        List<String> values;

        PropertyOperation(String namespace, String name, String value) {
            this.namespace = namespace;
            this.name = name;
            this.values = new ArrayList<String>();
            this.values.add(value);
        }

        public void addValue(String value) {
            values.add(value);
        }

        @Override
        public String toString() {
            return "PropertyOp(namespace=" + namespace + ", name=" + name 
                    + ", values=" + values + ")";
        }
    }
}
