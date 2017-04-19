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
package vtk.web.decorating;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.support.RequestContextUtils;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.resourcemanagement.view.tl.ComponentInvokerNodeFactory;
import vtk.text.tl.CaptureHandler;
import vtk.text.tl.Context;
import vtk.text.tl.DefineHandler;
import vtk.text.tl.DirectiveHandler;
import vtk.text.tl.IfHandler;
import vtk.text.tl.ListHandler;
import vtk.text.tl.StripHandler;
import vtk.text.tl.Symbol;
import vtk.text.tl.ValHandler;
import vtk.text.tl.expr.Expression.FunctionResolver;
import vtk.text.tl.expr.Function;
import vtk.util.io.InputSource;
import vtk.util.repository.PropertyAspectDescription;
import vtk.util.repository.PropertyAspectResolver;
import vtk.web.RequestContext;
import vtk.web.service.URL;

public class DynamicDecoratorTemplateFactory implements TemplateFactory, InitializingBean {

    private Repository repository;
    private PropertyTypeDefinition aspectsPropdef;
    PropertyAspectDescription fieldConfig;
    private String token;

    private List<DirectiveHandler> directiveHandlers;
    private FunctionResolver functionResolver = new FunctionResolver();
    private ComponentResolver componentResolver;

    public Template newTemplate(InputSource templateSource) throws InvalidTemplateException {
        return new DynamicDecoratorTemplate(templateSource, this.componentResolver, this.directiveHandlers, null);
    }

    @Required public void setRepository(Repository repository) {
        this.repository = repository;
    }

    @Required public void setAspectsPropdef(PropertyTypeDefinition aspectsPropdef) {
        this.aspectsPropdef = aspectsPropdef;
    }
    
    public void setFieldConfig(PropertyAspectDescription fieldConfig) {
        this.fieldConfig = fieldConfig;
    }
    
    public void setToken(String token) {
        this.token = token;
    }

    @Required public void setComponentResolver(ComponentResolver componentResolver) {
        this.componentResolver = componentResolver;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        //Set<Function> functions = new HashSet<>();
        //functions.add(new RequestURLFunction(new Symbol("request-url")));
        functionResolver.addFunction(new RepositoryIDFunction(new Symbol("repo-id"), this.repository));
        functionResolver.addFunction(new RequestParameterFunction(new Symbol("request-param")));
        functionResolver.addFunction(new ResourceLocaleFunction(new Symbol("resource-locale")));
        functionResolver.addFunction(new TemplateParameterFunction(new Symbol("template-param")));
        functionResolver.addFunction(new ResourceUriAspectFunction(new Symbol("resource-uri-aspect"), 
                this.repository, this.aspectsPropdef, this.fieldConfig, this.token));
        functionResolver.addFunction(new ResourceAspectFunction(new Symbol("resource-aspect"), 
                this.repository, this.aspectsPropdef, this.fieldConfig, this.token));
        functionResolver.addFunction(new ResourcePropHandler(new Symbol("resource-prop")));


        this.directiveHandlers = Arrays.asList(new DirectiveHandler[] {
                new IfHandler(functionResolver),
                new StripHandler(),
                new ValHandler(null, functionResolver),
                new ListHandler(functionResolver),
                new DefineHandler(functionResolver),
                new CaptureHandler(),
                new ComponentInvokerNodeFactory("call",
                        new DynamicDecoratorTemplate.ComponentSupport(), functionResolver)
        });
    }

    public void setFunctionResolver(FunctionResolver functionResolver) {
        this.functionResolver = functionResolver;
    }

    private static class RepositoryIDFunction extends Function {
        private Repository repository;
        public RepositoryIDFunction(Symbol symbol, Repository repository) {
            super(symbol, 0);
            this.repository = repository;
        }
        @Override
        public Object eval(Context ctx, Object... args) {
            return this.repository.getId();
        }        
    }

    @SuppressWarnings("unused")
    private static class RequestURLFunction extends Function {
        public RequestURLFunction(Symbol symbol) {
            super(symbol, 0);
        }

        @Override
        public Object eval(Context ctx, Object... args) {
//            RequestContext requestContext = RequestContext.getRequestContext();
//            HttpServletRequest request = requestContext.getServletRequest();
            HttpServletRequest request = (HttpServletRequest) ctx.getAttribute(DynamicDecoratorTemplate.SERVLET_REQUEST_CONTEXT_ATTR);

            URL url = URL.create(request);
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("url", url);
            map.put("scheme", url.getProtocol());
            map.put("host", url.getHost());
            map.put("port", url.getPort());
            map.put("path", url.getPath());
            map.put("query", url.getQueryString());
            return map;
        }
    }
    
    private static class TemplateParameterFunction extends Function {
        public TemplateParameterFunction(Symbol symbol) {
            super(symbol, 1);
        }

        @Override
        public Object eval(Context ctx, Object... args) {
//            RequestContext requestContext = RequestContext.getRequestContext();
//            HttpServletRequest request = requestContext.getServletRequest();
            HttpServletRequest request = (HttpServletRequest) ctx.getAttribute(DynamicDecoratorTemplate.SERVLET_REQUEST_CONTEXT_ATTR);
            
            Object o = args[0];
            if (o == null) {
                throw new IllegalArgumentException("Argument cannot be NULL");
            }
            String name = o.toString();
            return DynamicDecoratorTemplate.getTemplateParam(request, name);
        }
    }
    
    private static class RequestParameterFunction extends Function {
        
        public RequestParameterFunction(Symbol symbol) {
            super(symbol, 1);
        }

        @Override
        public Object eval(Context ctx, Object... args) {
//            RequestContext requestContext = RequestContext.getRequestContext();
//            HttpServletRequest request = requestContext.getServletRequest();
            HttpServletRequest request = (HttpServletRequest) ctx.getAttribute(DynamicDecoratorTemplate.SERVLET_REQUEST_CONTEXT_ATTR);

            Object o = args[0];
            if (o == null) {
                throw new IllegalArgumentException("Argument cannot be NULL");
            }
            String name = o.toString();
            return request.getParameter(name);
        }
        
    }
    
    private static class ResourceLocaleFunction extends Function {
        
        public ResourceLocaleFunction(Symbol symbol) {
            super(symbol, 0);
        }

        @Override
        public Object eval(Context ctx, Object... args) {
//            RequestContext requestContext = RequestContext.getRequestContext();
//            HttpServletRequest request = requestContext.getServletRequest();
            HttpServletRequest request = (HttpServletRequest) ctx.getAttribute(DynamicDecoratorTemplate.SERVLET_REQUEST_CONTEXT_ATTR);

            return RequestContextUtils.getLocale(request).toString();
        }
        
    }
    
    private static class ResourceAspectFunction extends Function {
        private PropertyAspectResolver resolver = null;

        public ResourceAspectFunction(Symbol symbol, Repository repository, 
                PropertyTypeDefinition aspectsPropdef, 
                PropertyAspectDescription fieldConfig, String token) {
            super(symbol, 1);
            this.resolver = new PropertyAspectResolver(repository, aspectsPropdef, 
                    fieldConfig, token);
        }
        
        @Override
        public Object eval(Context ctx, Object... args) {
            RequestContext requestContext = RequestContext.getRequestContext();
            Object o = args[0];
            if (o == null) {
                throw new IllegalArgumentException("Argument must be a valid name");
            }
            String aspect = o.toString();
            try {
                return this.resolver.resolve(requestContext.getResourceURI(), aspect);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
     }
    
    private static class ResourceUriAspectFunction extends Function {
        private PropertyAspectResolver resolver = null;

        public ResourceUriAspectFunction(Symbol symbol, Repository repository,
                PropertyTypeDefinition aspectsPropdef, 
                PropertyAspectDescription fieldConfig, String token) {
            super(symbol, 2);
            this.resolver = new PropertyAspectResolver(repository, aspectsPropdef, 
                    fieldConfig, token);
        }
        
        @Override
        public Object eval(Context ctx, Object... args) {
            Object o1 = args[0];
            Object o2 = args[1];
            if (o1 == null || o2 == null) {
                throw new IllegalArgumentException("Arguments must have valid names");
            }
            Path url = Path.fromString(o1.toString());
            String aspect = o2.toString();
            try {
                return this.resolver.resolve(url, aspect);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
     }
    
    // XXX: Should use same code as ResourcePropHandler.java(?)
    private static class ResourcePropHandler extends Function {

        public ResourcePropHandler(Symbol symbol) {
            super(symbol, 2);
        }

        @Override
        public Object eval(Context ctx, Object... args) {
            final Object arg1 = args[0];
            final Object arg2 = args[1];
            PropertySet resource = null;
            String ref = null;

            if (arg1 instanceof PropertySet) {
                resource = (PropertySet) arg1;
            } else {
                ref = arg1.toString();
            }

            if (resource == null) {
                RequestContext requestContext = RequestContext
                        .getRequestContext();
                Repository repository = requestContext.getRepository();
                Path uri = ".".equals(ref) 
                ? requestContext.getResourceURI()
                        : Path.fromString(ref);
                String token = requestContext.getSecurityToken();
                try {
                    resource = repository.retrieve(token, uri, true);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
            String propName = arg2.toString();
            if ("uri".equals(propName)) {
                return resource.getURI();
            }
            if ("resourceType".equals(propName)) {
                return resource.getResourceType();
            }

            Property property = resource.getProperty(Namespace.DEFAULT_NAMESPACE, propName);
            if (property == null) {
                for (Property prop : resource) {
                    if (propName.equals(prop.getDefinition().getName())) {
                        property = prop;
                    }
                }
            }
            if (property == null) {
                return null;
            }
            if (property.getDefinition().isMultiple()) {
                return getObjectValue(property.getValues());
            } else {
                return getObjectValue(property.getValue());
            }
        }

    }
    
    private static Object getObjectValue(Object obj) {        
        if (obj instanceof Value) 
            return ((Value)obj).getObjectValue();
        
        if (obj instanceof Value[]) {
            Value[] values = (Value[])obj;
            Object[] objValues = new Object[values.length];
            for (int i=0; i<values.length; i++) {
                objValues[i] = values[i].getObjectValue();
            }
            return objValues;
        }
        return obj;
    }

}
