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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;

import javax.servlet.http.HttpServletRequest;

import vtk.resourcemanagement.view.StructuredResourceDisplayController;
import vtk.text.html.HtmlPage;
import vtk.text.tl.Context;
import vtk.text.tl.DirectiveHandler;
import vtk.text.tl.Node;
import vtk.text.tl.Parser.Directive;
import vtk.text.tl.TemplateContext;
import vtk.text.tl.Token;
import vtk.text.tl.expr.Expression;
import vtk.text.tl.expr.Expression.FunctionResolver;
import vtk.web.decorating.ComponentResolver;
import vtk.web.decorating.DecoratorComponent;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorRequestImpl;
import vtk.web.decorating.DecoratorResponseImpl;
import vtk.web.decorating.DynamicDecoratorTemplate;

public class ComponentInvokerNodeFactory implements DirectiveHandler {

    private static final String COMPONENT_STACK_REQ_ATTR =
        ComponentInvokerNodeFactory.class.getName() + ".ComponentStack";

    private ComponentSupport componentSupport;
    private FunctionResolver functionResolver;
    private String name;

    public ComponentInvokerNodeFactory(String name, ComponentSupport componentSupport, FunctionResolver functionResolver) {
        if (componentSupport == null) {
            throw new IllegalArgumentException("Constructor argument is NULL");
        }
        this.componentSupport = componentSupport;
        this.functionResolver = functionResolver;
        this.name = name;
    }

    @Override
    public String[] tokens() {
        return new String[] { this.name };
    }

    public interface ComponentSupport {
        public ComponentResolver getComponentResolver(Context context);
        public HtmlPage getHtmlPage(Context context);
    }

    protected DecoratorComponent resolveComponent(Context context, String namespace, String name) {
        ComponentResolver componentResolver = this.componentSupport.getComponentResolver(context);
        if (componentResolver == null) return null;
        DecoratorComponent component = componentResolver.resolveComponent(namespace, name);
        return component;
    }

    protected HtmlPage getHtmlPage(Context context) {
        return this.componentSupport.getHtmlPage(context);
    }

    @Override
    public void directive(Directive directive, TemplateContext context) {
        final List<Token> args = directive.args();

        if (args.size() == 0) {
            context.error("Wrong number of arguments: expected <component-reference> <params>");
            return;
        }
        final Token arg1 = args.get(0);
        List<Token> rest = args.subList(1, args.size());
        final Expression expression = rest.size() > 0 ? new Expression(this.functionResolver, rest) : null;

        context.add(new Node() {
            @Override
            public String toString() {
                return "[" + name + args + "]";
            }
            @Override
            @SuppressWarnings("unchecked")
            public boolean render(Context ctx, Writer out) throws Exception {
                Object componentRef = arg1.getValue(ctx);

                if (!(componentRef instanceof String)) {
                    throw new RuntimeException("First argument must be a string");
                }

                Object parameterMap = Collections.<String, Object>emptyMap();
                if (expression != null) {
                    try {
                        parameterMap = expression.evaluate(ctx);
                    } catch (Throwable t) {
                        out.write(componentRef + ":" + t.getMessage());
                        return true;
                    }
                }
                if (!(parameterMap instanceof Map<?, ?>)) {
                    out.write(componentRef + ": second argument must be a map: " + parameterMap);
                    return true;
                }
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) parameterMap).entrySet()) {
                    if (!(entry.getKey() instanceof String)) {
                        out.write(componentRef + ": parameter name must be string: " + entry.getKey());
                    }
                }

                String namespace = null;
                String name = (String) componentRef;
                if (name.contains(":")) {
                    namespace = name.substring(0, name.indexOf(":"));
                    name = name.substring(namespace.length() + 1);
                }
                DecoratorComponent component = resolveComponent(ctx, namespace, name);
                if (component == null) {
                    out.write("Unable to resolve component '" + namespace + ":" + name + "'");
                    return true;
                }
                //RequestContext requestContext = RequestContext.getRequestContext();
                //HttpServletRequest servletRequest = requestContext.getServletRequest();

                HttpServletRequest servletRequest = (HttpServletRequest) ctx.getAttribute(DynamicDecoratorTemplate.SERVLET_REQUEST_CONTEXT_ATTR);

                Stack<DecoratorComponent> componentStack =
                    (Stack<DecoratorComponent>) servletRequest.getAttribute(COMPONENT_STACK_REQ_ATTR);
                if (componentStack == null) {
                    componentStack = new Stack<DecoratorComponent>();
                    servletRequest.setAttribute(COMPONENT_STACK_REQ_ATTR, componentStack);
                }

                /* VTK-4702: commented out because can't do a recursive call needed for parsing exam json data
                for (DecoratorComponent c : componentStack) {
                    if (c == component) {
                        out.write("Component invocation loop detected: '" + c.getNamespace() + ":" + c.getName()+ "'");
                        return true;
                    }
                }
                */
                componentStack.push(component);
                try {
                    Locale locale = ctx.getLocale();
                    final String doctype = "";

                    Map<String, Object> mvcModel = (Map<String, Object>) servletRequest.getAttribute(StructuredResourceDisplayController.MVC_MODEL_REQ_ATTR);
                    DecoratorRequest decoratorRequest = new DecoratorRequestImpl(
                            getHtmlPage(ctx), servletRequest, mvcModel,
                            (Map<String, Object>) parameterMap, doctype, locale);
                    DecoratorResponseImpl decoratorResponse = new DecoratorResponseImpl(
                            doctype, locale, "utf-8");
                    component.render(decoratorRequest, decoratorResponse);
                    out.write(decoratorResponse.getContentAsString());
                } catch (Throwable t) {
                    out.write(component.getNamespace() + ":" + component.getName()+ ": " + t.getMessage());

                } finally {
                    componentStack.pop();
                }
                return true;
            }
        });
    }

}
