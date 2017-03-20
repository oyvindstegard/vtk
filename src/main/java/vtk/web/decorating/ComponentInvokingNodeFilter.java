/* Copyright (c) 2007, University of Oslo, Norway
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

import vtk.text.html.HtmlAttribute;
import vtk.text.html.HtmlComment;
import vtk.text.html.HtmlContent;
import vtk.text.html.HtmlElement;
import vtk.text.html.HtmlNodeFilter;
import vtk.text.html.HtmlPage;
import vtk.text.html.HtmlPageFilter;
import vtk.text.html.HtmlText;
import vtk.web.RequestContext;


public class ComponentInvokingNodeFilter implements HtmlNodeFilter, HtmlPageFilter, InitializingBean {

    private static final Pattern SSI_DIRECTIVE_REGEXP = Pattern.compile(
            "#([a-zA-Z]+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern SSI_PARAMETERS_REGEXP = Pattern.compile(
            "\\s*([a-zA-Z]*)\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static Logger logger = LoggerFactory.getLogger(ComponentInvokingNodeFilter.class);

    private ComponentResolver componentResolver;

    private Map<String, DecoratorComponent> ssiDirectiveComponentMap;
    private Set<String> availableComponentNamespaces = new HashSet<String>();
    private TextualComponentParser contentComponentParser;
    private boolean parseAttributes = false;

    public void setComponentResolver(ComponentResolver componentResolver) {
        this.componentResolver = componentResolver;
    }

    public void setSsiDirectiveComponentMap(Map<String, DecoratorComponent> ssiDirectiveComponentMap) {
        this.ssiDirectiveComponentMap = ssiDirectiveComponentMap;
    }

    public void setAvailableComponentNamespaces(Set<String> availableComponentNamespaces) {
        this.availableComponentNamespaces = availableComponentNamespaces;
    }

    public void setContentComponentParser(TextualComponentParser contentComponentParser) {
        this.contentComponentParser = contentComponentParser;
    }

    public void setParseAttributes(boolean parseAttributes) {
        this.parseAttributes = parseAttributes;
    }

    public void afterPropertiesSet() {
        if (this.componentResolver == null) {
            throw new BeanInitializationException(
                    "JavaBean property 'componentResolver' not specified");
        }
        if (this.ssiDirectiveComponentMap == null) {
            throw new BeanInitializationException(
            "JavaBean property 'ssiDirectiveComponentMap' not specified");
        }
    }

    @Override
    public boolean match(HtmlPage page) {
        return true;
    }

    // HtmlPageFilter.filter() (invoked after parsing)
    public NodeResult filter(HtmlContent node) {
        if (node instanceof HtmlElement) {
            HtmlElement element = (HtmlElement) node;
            HtmlContent[] childNodes = element.getChildNodes();
            for (int i = 0; i < childNodes.length; i++) {
                childNodes[i] = filterNode(childNodes[i]);
            }
            element.setChildNodes(childNodes);
        }
        return NodeResult.keep;
    }


    // HtmlNodeFilter.filter() (invoked during parsing)
    public HtmlContent filterNode(HtmlContent node) {
        if (node instanceof HtmlComment) {
            ComponentInvocation ssiInvocation = buildSsiComponentInvocation(node);
            if (ssiInvocation == null) {
                return node;
            }
            HtmlContent filteredNode = invokeComponentsAsContent(new ComponentInvocation[] {ssiInvocation});
            return filteredNode;

        }
        else if (node instanceof HtmlElement && this.parseAttributes) {
            HtmlElement element = (HtmlElement) node;
            HtmlAttribute[] attributes = element.getAttributes();
            if (attributes.length > 0) {
                for (HtmlAttribute attribute: attributes) {
                    String value = attribute.getValue();
                    if (attribute.hasValue()) {
                        try {
                            ComponentInvocation[] parsedValue =
                                this.contentComponentParser.parse(
                                        new java.io.StringReader(value));
                            value = invokeComponentsAsString(parsedValue);
                        }
                        catch (Exception e) {
                            if (e.getMessage() == null) {
                                value = e.getClass().getName();
                            }
                            else {
                                value = e.getMessage();
                            }
                        }
                    }
                    attribute.setValue(value);
                }
            }
        }
        else if (node instanceof HtmlText) {

            if (this.contentComponentParser == null) {
                return node;
            }
            String content = ((HtmlText) node).getContent();
            try {
                ComponentInvocation[] parsedContent =
                    this.contentComponentParser.parse(new java.io.StringReader(content));
                HtmlContent filteredNode = invokeComponentsAsContent(parsedContent);
                return filteredNode;
            }
            catch (Exception e) {
                return node;
            }
        }
        return node;
    }


    private ComponentInvocation buildSsiComponentInvocation(HtmlContent node) {
        String content = node.getContent();
        if (content == null || "".equals(content.trim())) {
            return null;
        }

        String directive = null;
        Matcher directiveMatcher = SSI_DIRECTIVE_REGEXP.matcher(content);
        if (!directiveMatcher.find()) {
            return null;
        }
        directive = directiveMatcher.group(1);

        Matcher paramMatcher = SSI_PARAMETERS_REGEXP.matcher(
                content.substring(directiveMatcher.end()));

        Map<String, Object> parameters = new HashMap<String, Object>();

        while (paramMatcher.find()) {
            String name = paramMatcher.group(1);
            String value = paramMatcher.group(2);
            if (name != null && value != null) {
                parameters.put(name, value);
            }
        }

        final DecoratorComponent component = 
            this.ssiDirectiveComponentMap.get(directive);
        if (component == null) {
            return null;
        }
        final Map<String, Object> invocationParams = parameters;
        return new ComponentInvocation() {
            public Map<String, Object> getParameters() {
                return invocationParams;
            }
            public String getNamespace() {
                return component.getNamespace();
            }
            public String getName() {
                return component.getName();
            }
            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder(this.getClass().getName());
                sb.append("; component=").append(component);
                sb.append("; params=").append(invocationParams);
                return sb.toString();
            }
        };
    }


    private HtmlContent invokeComponentsAsContent(ComponentInvocation[] components) {
        final String text = invokeComponentsAsString(components);
        return new HtmlText() {
            public String getContent() {
                return text;
            }
        };
    }


    private String invokeComponentsAsString(ComponentInvocation[] componentInvocations) {
        HttpServletRequest servletRequest = 
                RequestContext.getRequestContext().getServletRequest();

        org.springframework.web.servlet.support.RequestContext ctx =
            new org.springframework.web.servlet.support.RequestContext(servletRequest);
        Locale locale = ctx.getLocale();

        final String doctype = "";

        StringBuilder sb = new StringBuilder();

        for (ComponentInvocation invocation: componentInvocations) {
            if (invocation instanceof StaticTextFragment) {
                sb.append(((StaticTextFragment) invocation).buffer);
                continue;
            }
            Map<String, Object> parameters = invocation.getParameters();
            DecoratorRequest decoratorRequest = new DecoratorRequestImpl(
                    null, servletRequest, new HashMap<String, Object>(), 
                    parameters, doctype, locale);
            DecoratorResponseImpl response = new DecoratorResponseImpl(
                    doctype, locale, "utf-8");
            String result = null;
            try {
                DecoratorComponent component = this.componentResolver.resolveComponent(
                        invocation.getNamespace(), invocation.getName());
                if (component == null) {
                    sb.append("Invalid component reference: " + invocation.getNamespace()
                            + ":" + invocation.getName());
                    continue;
                }
                component.render(decoratorRequest, response);
                result = response.getContentAsString();

            }
            catch (Throwable t) {
                String msg = t.getMessage();
                if (msg == null) {
                    msg = t.getClass().getName();
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Error invoking component on page " + 
                            servletRequest.getRequestURI() + ": " + invocation, t);
                }
                else if (logger.isInfoEnabled()) {
                    logger.info("Error invoking component on page " + 
                            servletRequest.getRequestURI() + ": " + invocation + ": "
                            + msg);
                }
                result = "Error: " + msg;
            }
            sb.append(result);
        }
        return sb.toString();
    }

}
