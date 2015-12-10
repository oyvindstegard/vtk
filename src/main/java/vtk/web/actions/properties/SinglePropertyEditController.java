/* Copyright (c) 2014, University of Oslo, Norway
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
package vtk.web.actions.properties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.TypeInfo;
import vtk.web.RequestContext;
import vtk.web.service.URL;

/**
 * Controller for editing a single property.
 * 
 * <ul>
 *  <li>Required inputs: <code>name</code>
 *  <li>Optional inputs: <code>namespace, default-value</code>. 
 *      The <code>default-value</code> determines the default value 
 *      of the form input field if the property does not exist. The
 *      <code>namespace</code> input is a {@link Namespace} prefix 
 *      used to qualify the property if multiple properties exist 
 *      with the same name. Default is {@link Namespace#DEFAULT_NAMESPACE}.
 *  <li>POST submission inputs: <code>value</code> - the 
 *      property value. If this parameter is absent, the property is 
 *      removed from the resource. 
 *      <code>redirect-after</code> - URL to redirect to after submission. 
 *      If not specified, a redirect to the form itself is sent.
 * </ul>
 */
public class SinglePropertyEditController implements Controller {
    private String viewName = null;
    
    public SinglePropertyEditController(String viewName) {
        this.viewName = viewName;
    }
    
    @Override
    public ModelAndView handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        try {
            EditRequest req = new EditRequest(request);
            if ("POST".equals(request.getMethod())) {
                return handlePost(req, response);
            }
            return displayForm(req);
        } 
        catch (IllegalArgumentException e) {
            return reject(response, HttpServletResponse.SC_BAD_REQUEST, 
                    e.getMessage());
        }
    }
    
    private ModelAndView displayForm(EditRequest request) throws Exception {
        Resource resource = request.resource;
        Property property = resource.getProperty(request.namespace, request.name);
        
        String value = request.defaultValue;
        if (property != null) {
            value = property.getValue().getObjectValue().toString();
        }
        
        Map<String, Object> form = new HashMap<>();
        List<Object> inputs = new ArrayList<>();
        inputs.add(formElement("value", value, "text"));
        inputs.add(formElement("submit", "Save", "submit"));
        form.put("action", request.requestContext.getRequestURL());
        form.put("inputs", inputs);
        
        Map<String, Object> model = new HashMap<>();
        model.put("property", request.name);
        model.put("form", form);
        return new ModelAndView(viewName, model);
    }

    private ModelAndView handlePost(EditRequest request, 
            HttpServletResponse response) throws Exception {
        Resource resource = request.resource;
        Property property = resource.getProperty(request.namespace, request.name);
        
        String value = request.value;
        if (value == null || value.trim().equals("")) {
            resource.removeProperty(request.namespace, request.name);
        }
        else {
            property = request.typeInfo
                    .createProperty(request.namespace, request.name, value);
            resource.addProperty(property);
        }

        RequestContext requestContext = request.requestContext;
        requestContext.getRepository()
            .store(requestContext.getSecurityToken(), resource);

        response.sendRedirect(request.redirectURL.toString());
        return null;
    }

    private ModelAndView reject(HttpServletResponse response, int sc, String msg) throws Exception {
        response.setStatus(sc);
        response.getWriter().write(msg);
        response.flushBuffer();
        return null;
    }
    
    private Object formElement(String name, String value, String type) {
        Map<String, Object> element = new HashMap<String, Object>();
        element.put("name", name);
        element.put("value", value);
        element.put("type", type);
        return element;
    }
    
    private static class EditRequest {
        private RequestContext requestContext;
        private Resource resource;
        private TypeInfo typeInfo;
        private Namespace namespace;
        private String name;
        private String value;
        private String defaultValue;
        private URL redirectURL;
        
        public EditRequest(HttpServletRequest request) throws Exception {
            String name = request.getParameter("name");
            if (name == null)
                throw new IllegalArgumentException(
                        "Parameter 'name' is required");
            this.name = name;
            this.defaultValue = request.getParameter("default-value");
            this.value = request.getParameter("value");
            this.requestContext = RequestContext.getRequestContext();
            String namespace = request.getParameter("namespace");

            String token = requestContext.getSecurityToken();
            Repository repository = requestContext.getRepository();
            Path uri = requestContext.getResourceURI();
            
            this.resource = repository.retrieve(token, uri, true);
            Namespace ns = Namespace.DEFAULT_NAMESPACE;
            this.typeInfo = repository.getTypeInfo(resource);

            if (namespace != null) {
                ns = typeInfo.getNamespaceByPrefix(namespace);
            }
            this.namespace = ns;
            String redir = request.getParameter("redirect-after");
            
            this.redirectURL = redir == null ? requestContext.getRequestURL()
                    : requestContext.getRequestURL().relativeURL(redir);
        }
    }
}
