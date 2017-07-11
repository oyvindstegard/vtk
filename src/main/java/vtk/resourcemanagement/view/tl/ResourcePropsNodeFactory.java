/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.resourcemanagement.view.tl;

import java.io.Writer;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.TypeInfo;
import vtk.repository.resourcetype.PrimaryResourceTypeDefinition;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.resourcemanagement.view.StructuredResourceDisplayController;
import vtk.text.tl.Context;
import vtk.text.tl.DirectiveHandler;
import vtk.text.tl.Node;
import vtk.text.tl.Parser.Directive;
import vtk.text.tl.TemplateContext;
import vtk.text.tl.Token;
import vtk.web.RequestContext;
import vtk.web.decorating.DynamicDecoratorTemplate;

public class ResourcePropsNodeFactory implements DirectiveHandler {
    private String name;
    
    public ResourcePropsNodeFactory(String name) {
        this.name = name;
    }
    
    @Override
    public String[] tokens() {
        return new String[] { name };
    }

    @Override
    public void directive(Directive directive, TemplateContext context) {
        List<Token> tokens = directive.args();
        if (tokens.size() != 1) {
            context.error("Wrong number of arguments");
            return;
        }
        final Token arg1 = tokens.get(0);

        context.add(new Node() {
            @Override
            public boolean render(Context ctx, Writer out) throws Exception {
                Resource resource;
                
                HttpServletRequest request = (HttpServletRequest) 
                        ctx.getAttribute(DynamicDecoratorTemplate.SERVLET_REQUEST_CONTEXT_ATTR);
                if (request == null) {
                    throw new RuntimeException("Servlet request not found in context by attribute: "
                            + DynamicDecoratorTemplate.SERVLET_REQUEST_CONTEXT_ATTR);
                }
                RequestContext requestContext = RequestContext.getRequestContext(request);
                
                String token = requestContext.getSecurityToken();
                Repository repository = requestContext.getRepository();
                String ref = arg1.getValue(ctx).toString();
                
                if (ref.equals(".")) {
                    Object o = request.getAttribute(StructuredResourceDisplayController.MVC_MODEL_REQ_ATTR);
                    if (o == null) {
                        throw new Exception("Unable to locate resource: no model: " 
                                + StructuredResourceDisplayController.MVC_MODEL_REQ_ATTR);
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> model = (Map<String, Object>) o;
                    resource = (Resource) model.get("resource");
                } else {
                    Path uri = Path.fromString(ref);
                    resource = repository.retrieve(token, uri, true);
                }
                if (resource == null) {
                    throw new RuntimeException("Unable to resolve resource");
                }
                TypeInfo typeInfo = repository.getTypeInfo(resource);
                PrimaryResourceTypeDefinition resourceType = typeInfo.getResourceType();
                while (resourceType != null) {
                    PropertyTypeDefinition[] propDefs = resourceType.getPropertyTypeDefinitions();
                    for (PropertyTypeDefinition propDef : propDefs) {
                        Property prop = resource.getProperty(propDef);
                        if (prop == null) {
                            ctx.define(propDef.getName(), null, false);
                        } else {
                            if (propDef.isMultiple()) {
                                ctx.define(propDef.getName(), prop.getValues(), false);
                            } else {
                                ctx.define(propDef.getName(), prop.getValue(), false);
                            }
                        }
                    }
                    resourceType = resourceType.getParentTypeDefinition();
                }
                return true;
            }
        });
    }
}
