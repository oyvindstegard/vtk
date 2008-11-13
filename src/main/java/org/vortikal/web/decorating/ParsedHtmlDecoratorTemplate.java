/* Copyright (c) 2008, University of Oslo, Norway
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
package org.vortikal.web.decorating;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.vortikal.text.html.HtmlAttribute;
import org.vortikal.text.html.HtmlComment;
import org.vortikal.text.html.HtmlContent;
import org.vortikal.text.html.HtmlElement;
import org.vortikal.text.html.HtmlFragment;
import org.vortikal.text.html.HtmlPage;
import org.vortikal.text.html.HtmlPageParser;
import org.vortikal.text.html.HtmlText;


/**
 * Template that uses a parsed HTML document as 
 * its internal template representation.
 */
public class ParsedHtmlDecoratorTemplate implements Template {

    private HtmlPageParser htmlParser;
    private TextualComponentParser componentParser;
    private ComponentResolver componentResolver;
    private TemplateSource templateSource;
    
    private CompiledTemplate compiledTemplate;
    private long lastModified = -1;
    
    public ParsedHtmlDecoratorTemplate(HtmlPageParser htmlParser, 
            TextualComponentParser componentParser,
            ComponentResolver componentResolver,
            TemplateSource templateSource) throws Exception {

        if (htmlParser == null) {
            throw new IllegalArgumentException("Argument 'htmlParser' is NULL");
        }
        if (componentParser == null) {
            throw new IllegalArgumentException("Argument 'componentParser' is NULL");
        }
        if (componentResolver == null) {
            throw new IllegalArgumentException("Argument 'componentResolver' is NULL");
        }
        if (templateSource == null) {
            throw new IllegalArgumentException("Argument 'templateSource' is NULL");
        }
        this.htmlParser = htmlParser;
        this.componentParser = componentParser;
        this.componentResolver = componentResolver;
        this.templateSource = templateSource;

        compile();
    }


    public HtmlPageContent render(HtmlPageContent html, HttpServletRequest request,
            Map<Object, Object> model) throws Exception {

        if (this.templateSource.getLastModified() > this.lastModified) {
            compile();
        }
        HtmlPage resultPage = 
            this.compiledTemplate.generate(html.getHtmlContent(), request, model);
        return new HtmlPageContentImpl(resultPage.getCharacterEncoding(), resultPage);
    }

    private synchronized void compile() throws Exception {
        if (this.compiledTemplate != null && 
                this.lastModified == this.templateSource.getLastModified()) {
            return;
        }
        this.compiledTemplate = new CompiledTemplate(
                this.htmlParser, this.componentParser, 
                this.componentResolver, this.templateSource);
        this.lastModified = this.templateSource.getLastModified();
    }
    
    private class CompiledTemplate {
        private Node root;

        public CompiledTemplate(HtmlPageParser htmlParser, 
                TextualComponentParser componentParser,
                ComponentResolver componentResolver,
                TemplateSource templateSource) throws InvalidTemplateException {

            HtmlPage page = null;
            try {
                page = htmlParser.parse(
                        templateSource.getInputStream(), 
                        templateSource.getCharacterEncoding());
            } catch (Exception e) {
                throw new InvalidTemplateException(
                        "Error parsing template " + templateSource, e);
            }
            this.root = createNode(page.getRootElement(), componentParser, componentResolver);
        }

        public HtmlPage generate(HtmlPage userPage, 
                HttpServletRequest request, Map<Object, Object> model) throws Exception {
            List<HtmlContent> transformedContent = 
                this.root.generate(userPage, request, model);

            if (transformedContent.size() != 1) {
                throw new IllegalStateException("Invalid HTML result");
            }

            if (!(transformedContent.get(0) instanceof HtmlElement)) {
                throw new IllegalStateException("Invalid HTML result");
            }

            HtmlElement newRoot = ((HtmlElement) transformedContent.get(0));
            userPage.getRootElement().setChildNodes(newRoot.getChildNodes());
            return userPage;
        }
    }


    private Node createNode(HtmlContent c, 
            TextualComponentParser componentParser, 
            ComponentResolver componentResolver) {

        if (c instanceof HtmlElement) {
            HtmlElement e = (HtmlElement) c;
            if ("v:element-content".equals(e.getName())) {
                return new VrtxElementContentNode(e);
            } 
            if ("v:component".equals(e.getName())) {
                return new VrtxComponentNode(e);
            } 
            List<Node> children = new ArrayList<Node>();
            for (HtmlContent child: e.getChildNodes()) {
                children.add(createNode(child, componentParser, componentResolver));
            }
            return new ElementNode(e, children);
        } 
        
        if (c instanceof HtmlText) {
            return new TextNode((HtmlText) c);
        } 
        if (c instanceof HtmlComment) {
            return new CommentNode((HtmlComment) c);
        } 
        return new DefaultContentNode(c);
    }
    
    private abstract class Node {

        public abstract List<HtmlContent> 
            generate(HtmlPage userPage, HttpServletRequest req, 
                Map<Object, Object> model) throws Exception;

        protected List<HtmlContent> renderComponentAsHtml(DecoratorComponent c, DecoratorRequest request) {
            if (c instanceof HtmlDecoratorComponent) {
                try {
                   return ((HtmlDecoratorComponent) c).render(request);
                } catch (Throwable t) {
                    final String msg = c.getNamespace() + ":" 
                        + c.getName() + ": " + t.getMessage();
                    HtmlContent err = new HtmlText() {
                        public String getContent() {
                            return msg;
                        }
                    };
                    return Collections.singletonList(err);
                }
            }
            final String rendered = renderComponentAsString(c, request);
            try {
                HtmlFragment fragment = htmlParser.parseFragment(rendered);
                return fragment.getContent();
            } catch (Exception e) {
                HtmlContent err = new HtmlText() {
                    public String getContent() {
                        return rendered;
                    }
                };
                return Collections.singletonList(err);
            }
        }
        
        protected String renderComponentAsString(DecoratorComponent c, 
                DecoratorRequest request) {

            String defaultResponseDoctype = request.getDoctype();
            String defaultResponseEncoding = "utf-8";
            Locale defaultResponseLocale = Locale.getDefault();

            DecoratorResponseImpl response = new DecoratorResponseImpl(
                    defaultResponseDoctype, defaultResponseLocale, defaultResponseEncoding);
            String result = null;
            try {
                c.render(request, response);
                result = response.getContentAsString();
            } catch (Throwable t) {
                result = c.getNamespace() + ":" + c.getName() + ": " + t.getMessage();
            }
            return result;
        }
        
    }
    
    /**
     * v:element-content 
     */
    private class VrtxElementContentNode extends Node {
        private Throwable error;
        private String copyElementExpression;
        
        public VrtxElementContentNode(HtmlElement elem) {
            HtmlAttribute from = elem.getAttribute("from");
            if (from == null) {
                this.error = 
                    new InvalidTemplateException(
                            "Required attribute 'from' missing in element: " 
                            + elem.getEnclosedContent());
            } else {
                this.copyElementExpression = from.getValue();
            }
        }
        
        public List<HtmlContent> generate(HtmlPage userPage, HttpServletRequest req, 
                Map<Object, Object> model) throws Exception {
            List<HtmlContent> result = new ArrayList<HtmlContent>();
            if (this.error != null) {
                result.add(userPage.createTextNode(this.error.getMessage()));
            } else {
                HtmlElement copyElement = userPage.selectSingleElement(this.copyElementExpression);
                if (copyElement != null) {
                    result.addAll(java.util.Arrays.asList(copyElement.getChildNodes()));
                }
            }
            return result;
        }
    }
    
    /**
     * v:component 
     */
    private class VrtxComponentNode extends Node {
        private Throwable error;
        private ComponentInvocation elementComponent;
        
        public VrtxComponentNode(HtmlElement elem) {
            if (elem.getAttribute("name") == null) {
                this.error = new InvalidTemplateException(
                        "Missing 'name' attribute on element: " 
                        + elem.getEnclosedContent());
                return;
            } 

            String componentRef = elem.getAttribute("name").getValue();
            int separatorIdx = componentRef.indexOf(":");
            if (separatorIdx == -1 || separatorIdx == 0 
                    || separatorIdx == componentRef.length() - 1) {
                this.error = new InvalidTemplateException(
                        "Invalid component reference: " + componentRef);
                return;
            }

            String namespace = componentRef.substring(0, separatorIdx);
            String name = componentRef.substring(separatorIdx + 1);
            Map<String, Object> parameters = new HashMap<String, Object>();
            HtmlElement[] paramElems = elem.getChildElements("v:parameter");

            for (HtmlElement paramElem: paramElems) {
                HtmlAttribute paramName = paramElem.getAttribute("name");
                HtmlAttribute paramValue = paramElem.getAttribute("value");
                if (paramName == null || paramValue == null) {
                    this.error = new InvalidTemplateException(
                            "Component parameters must have 'name' and 'value' attributes: " 
                            + elem.getEnclosedContent());
                    return;
                }
                parameters.put(paramName.getValue(), paramValue.getValue());
            }
            DecoratorComponent component = 
                componentResolver.resolveComponent(namespace, name);
            
            if (component == null) {
                this.error = new Throwable("Unknown component: " + namespace + ":" + name);
                return;
            }
            this.elementComponent = new ComponentInvocationImpl(component, parameters);
        }
        
        public List<HtmlContent> generate(HtmlPage userPage, HttpServletRequest req, 
                Map<Object, Object> model) throws Exception {
            List<HtmlContent> result = new ArrayList<HtmlContent>();
            if (this.error != null) {
                result.add(userPage.createTextNode(this.error.getMessage()));
            } else {
                Locale locale = 
                    new org.springframework.web.servlet.support.RequestContext(req).getLocale();
                DecoratorRequest decoratorRequest = new DecoratorRequestImpl(
                        userPage, req, model, this.elementComponent.getParameters(), userPage.getDoctype(), locale);
                List<HtmlContent> nodes = 
                    renderComponentAsHtml(this.elementComponent.getComponent(), 
                            decoratorRequest);
                result.addAll(nodes);
            }
            return result;
        }
    }
    
    /**
     * regular element
     */
    private class ElementNode extends Node {
        private Throwable error;
        private HtmlElement element;
        private String copyAttributesExpression;
        private Map<String, ComponentInvocation[]> attributesMap;
        private List<Node> children;

        public ElementNode(HtmlElement elem, List<Node> children) {
            if (!elem.getName().matches("[a-zA-Z0-9]+")) {
                this.error = new InvalidTemplateException(
                        "Invalid element name: " + elem.getName());
                return;
            }
            this.children = children;
            this.element = elem;
            if (elem.getAttribute("v:attrs-from") != null) {
                this.copyAttributesExpression = 
                    elem.getAttribute("v:attrs-from").getValue();
            } else {
                this.attributesMap = new LinkedHashMap<String, ComponentInvocation[]>();
                for (HtmlAttribute attr: elem.getAttributes()) {
                    String name = attr.getName();
                    if (name == null || !name.matches("[a-zA-Z0-9]+")) {
                        this.error = new InvalidTemplateException("Invalid attribute name: " + name);
                        return;
                    }
                    String value = attr.getValue();
                    if (value == null) {
                        value = "";
                    }
                    ComponentInvocation[] parsedValue;
                    try {
                        parsedValue = 
                            componentParser.parse(new StringReader(value));
                    } catch (Exception e) {
                        this.error = e;
                        return;
                    }
                    this.attributesMap.put(attr.getName(), parsedValue);
                }
            }
        }
        
        public List<HtmlContent> generate(HtmlPage userPage, HttpServletRequest req, 
                Map<Object, Object> model) throws Exception {
            List<HtmlContent> result = new ArrayList<HtmlContent>();
            if (this.error != null) {
                result.add(userPage.createTextNode(this.error.getMessage()));
            } else {
                HtmlElement newElem = userPage.createElement(this.element.getName());

                if (this.copyAttributesExpression != null) {
                    HtmlAttribute[] attrs = this.element.getAttributes();
                    if (attrs != null && attrs.length > 0) {
                        List<HtmlAttribute> newAttrs = new ArrayList<HtmlAttribute>();
                        for (HtmlAttribute attr: attrs) {
                            newAttrs.add(attr);
                        }
                        newElem.setAttributes(newAttrs.toArray(new HtmlAttribute[newAttrs.size()]));
                    }
                } else {
                    List<HtmlAttribute> newAttrs = new ArrayList<HtmlAttribute>();
                    for (String name: this.attributesMap.keySet()) {
                        StringBuilder value = new StringBuilder();
                        ComponentInvocation[] invocations = this.attributesMap.get(name);
                        Locale locale = 
                            new org.springframework.web.servlet.support.RequestContext(req).getLocale();
                        for (ComponentInvocation inv: invocations) {
                            DecoratorRequest decoratorRequest = new DecoratorRequestImpl(
                                    userPage, req, model, inv.getParameters(), userPage.getDoctype(), locale);
                            value.append(renderComponentAsString(inv.getComponent(), decoratorRequest));
                        }
                        HtmlAttribute attr = userPage.createAttribute(name, value.toString());
                        newAttrs.add(attr);
                    }
                    newElem.setAttributes(newAttrs.toArray(
                            new HtmlAttribute[newAttrs.size()]));
                }

                List<HtmlContent> newElementContent = new ArrayList<HtmlContent>();
                for (Node childNode: this.children) {
                    newElementContent.addAll(childNode.generate(userPage, req, model));
                }
                newElem.setChildNodes(newElementContent.toArray(new HtmlContent[newElementContent.size()]));
                result.add(newElem);
            }
            return result;
        }
    }
    
    /**
     * text node
     */
    private class TextNode extends Node {
        private Throwable error;
        private ComponentInvocation[] parsedContent;
        
        public TextNode(HtmlText text) {
            try {
                this.parsedContent = componentParser.parse(
                    new java.io.StringReader(text.getContent()));
            } catch (Throwable t) {
                this.error = t;
            }
        }
        
        public List<HtmlContent> generate(HtmlPage userPage, HttpServletRequest req, 
                Map<Object, Object> model) throws Exception {
            List<HtmlContent> result = new ArrayList<HtmlContent>();
            if (this.error != null) {
                result.add(userPage.createTextNode(this.error.getMessage()));
            } else {
              StringBuilder sb = new StringBuilder();
              for (ComponentInvocation inv: this.parsedContent) {
                  Locale locale = 
                      new org.springframework.web.servlet.support.RequestContext(req).getLocale();
                  DecoratorRequest decoratorRequest = new DecoratorRequestImpl(
                          userPage, req, model, inv.getParameters(), userPage.getDoctype(), locale);
                  String renderedContent = renderComponentAsString(inv.getComponent(), decoratorRequest);
                  sb.append(renderedContent);
              }
              HtmlText newText = userPage.createTextNode(sb.toString());
              result.add(newText);
            }
            return result;
        }
    }

    /**
     * comment node
     */
    private class CommentNode extends Node {
        private String comment;
        
        public CommentNode(HtmlComment comment) {
            this.comment = comment.getContent();
        }

        public List<HtmlContent> generate(HtmlPage userPage, HttpServletRequest req, 
                Map<Object, Object> model) throws Exception {
            List<HtmlContent> result = new ArrayList<HtmlContent>();
            result.add(userPage.createComment(this.comment));
            return result;
        }
    }

    /**
     * "unspecified content"
     */
    private class DefaultContentNode extends Node {
        private String content;

        public DefaultContentNode(HtmlContent c) {
            this.content = c.getContent();
        }
        
        public List<HtmlContent> generate(HtmlPage userPage, HttpServletRequest req, 
                Map<Object, Object> model) throws Exception {
            List<HtmlContent> result = new ArrayList<HtmlContent>();
            result.add(userPage.createTextNode(this.content));
            return result;
        }
    }

}
