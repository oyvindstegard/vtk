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
package vtk.web.decorating;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.text.html.HtmlAttribute;
import vtk.text.html.HtmlComment;
import vtk.text.html.HtmlContent;
import vtk.text.html.HtmlElement;
import vtk.text.html.HtmlFragment;
import vtk.text.html.HtmlPage;
import vtk.text.html.HtmlPageParser;
import vtk.text.html.HtmlText;
import vtk.text.html.HtmlUtil;
import vtk.util.io.InputSource;


/**
 * Template that uses a parsed HTML document as 
 * its internal template representation.
 */
public class ParsedHtmlDecoratorTemplate implements Template {

    private static Logger logger = LoggerFactory.getLogger(ParsedHtmlDecoratorTemplate.class);

    private HtmlPageParser htmlParser;
    private TextualComponentParser componentParser;
    private ComponentResolver componentResolver;
    private InputSource templateSource;

    private CompiledTemplate compiledTemplate;
    private long lastModified = -1;

    public ParsedHtmlDecoratorTemplate(HtmlPageParser htmlParser, 
            TextualComponentParser componentParser,
            ComponentResolver componentResolver,
            InputSource templateSource) throws Exception {

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


    @Override
    public TemplateExecution newTemplateExecution(HtmlPageContent html,
            HttpServletRequest request, Map<String, Object> model,
            Map<String, Object> templateParameters) throws Exception {
        if (this.templateSource.getLastModified() > this.lastModified) {
            compile();
        }
        return new Execution(this.compiledTemplate, this.componentResolver, html, request, model);
    }

    public static class Execution implements TemplateExecution {

        private CompiledTemplate compiledTemplate;
        private ComponentResolver componentResolver;
        private HtmlPageContent html;
        private HttpServletRequest request;
        private Map<String, Object> model;

        public Execution(CompiledTemplate compiledTemplate, 
                ComponentResolver componentResolver, HtmlPageContent html, 
                HttpServletRequest request, Map<String, Object> model) {
            this.compiledTemplate = compiledTemplate;
            this.componentResolver = componentResolver;
            this.html = html;
            this.request = request;
            this.model = model;
        }

        public ComponentResolver getComponentResolver() {
            return this.componentResolver;
        }

        public void setComponentResolver(ComponentResolver componentResolver) {
            this.componentResolver = componentResolver;
        }

        public HtmlPageContent render() throws Exception {
            HtmlPage resultPage = 
                this.compiledTemplate.generate(html.getHtmlContent(), 
                        this.componentResolver, request, model);
            return new HtmlPageContentImpl(resultPage.getCharacterEncoding(), 
                    resultPage);
        }
    }

    private synchronized void compile() throws Exception {
        try {
            if (this.compiledTemplate != null && 
                    this.lastModified == this.templateSource.getLastModified()) {
                return;
            }
            logger.info("Compiling template " + this.templateSource.getID());
            this.compiledTemplate = new CompiledTemplate(
                    this.htmlParser, this.componentParser, 
                    this.templateSource);
            this.lastModified = this.templateSource.getLastModified();
        } catch (Exception e) {
            logger.warn("Error compiling template " + this.templateSource.getID(), e);
            throw e;
        }
    }

    private class CompiledTemplate {

        private Node root;

        private final Pattern ELEMENT_NAME_REGEX_PATTERN = Pattern.compile("[a-z-]+:[a-z-]+");

        public CompiledTemplate(HtmlPageParser htmlParser, 
                TextualComponentParser componentParser,
                InputSource templateSource) throws InvalidTemplateException {

            HtmlPage page = null;
            InputStream is = null;
            try {
                is = templateSource.getInputStream();
                page = htmlParser.parse(
                        is,
                        templateSource.getCharacterEncoding());
            } catch (Exception e) {
                throw new InvalidTemplateException(
                        "Error parsing template " + templateSource, e);
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException io) {}
                }
            }
            this.root = createNode(page.getRootElement(), componentParser);
        }

        public HtmlPage generate(HtmlPage userPage, ComponentResolver componentResolver, 
                HttpServletRequest request, Map<String, Object> model) throws Exception {
            List<HtmlContent> transformedContent = 
                this.root.generate(userPage, componentResolver, request, model);
            if (transformedContent.size() != 1) {
                throw new IllegalStateException("Invalid HTML result: " + transformedContent);
            }

            Object firstElem = transformedContent.get(0);
            if (!(firstElem instanceof HtmlElement)) {
                throw new IllegalStateException("Invalid HTML result: expected element, found " 
                        + firstElem.getClass().getName());
            }

            HtmlElement newRoot = (HtmlElement) firstElem;
            userPage.getRootElement().setChildNodes(newRoot.getChildNodes());
            return userPage;
        }

        private Node createNode(HtmlContent c, 
                TextualComponentParser componentParser) {

            if (c instanceof HtmlElement) {
                HtmlElement e = (HtmlElement) c;
                if (ELEMENT_NAME_REGEX_PATTERN.matcher(e.getName()).matches()) {
                    return new VrtxComponentNode(e);
                }
                List<Node> children = new ArrayList<Node>();
                for (HtmlContent child: e.getChildNodes()) {
                    children.add(createNode(child, componentParser));
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
    }

    private abstract class Node {

        public abstract List<HtmlContent> generate(HtmlPage userPage, 
                ComponentResolver componentResolver,
                HttpServletRequest req, Map<String, Object> model) 
                throws Exception;

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
                result = c.getNamespace() + ":" + c.getName() + ": " + HtmlUtil.encodeBasicEntities(t.getMessage());
            }
            return result;
        }

    }


    /**
     * Component node
     */
    private class VrtxComponentNode extends Node {
        private Throwable error;
        private ComponentInvocation elementComponent;

        public VrtxComponentNode(HtmlElement elem) {
            String componentRef = elem.getName();
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
            for (HtmlAttribute attr: elem.getAttributes()) {
                parameters.put(attr.getName(), attr.getValue());    
            }
            this.elementComponent = new ComponentInvocationImpl(namespace, name, parameters);
        }

        @Override
        public List<HtmlContent> generate(HtmlPage userPage, ComponentResolver componentResolver, HttpServletRequest req, 
                Map<String, Object> model) throws Exception {
            List<HtmlContent> result = new ArrayList<HtmlContent>();
            if (this.error != null) {
                result.add(userPage.createTextNode(this.error.getMessage()));
            } else {
                Locale locale = 
                    new org.springframework.web.servlet.support.RequestContext(req).getLocale();
                DecoratorRequest decoratorRequest = new DecoratorRequestImpl(
                        userPage, req, model, this.elementComponent.getParameters(), userPage.getDoctype(), locale);

                String namespace = this.elementComponent.getNamespace();
                String name = this.elementComponent.getName();

                DecoratorComponent component = 
                    componentResolver.resolveComponent(namespace, name);
                if (component == null) {
                    result.add(userPage.createTextNode(
                            "Unknown component: " + namespace + ":" + name));
                    return result;
                }

                List<HtmlContent> nodes = 
                    renderComponentAsHtml(component, decoratorRequest);
                if (nodes != null) {
                    result.addAll(nodes);
                }
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
        private String copyAttributesList;
        private Map<String, ComponentInvocation[]> attributesMap;
        private List<Node> children;

        private final Pattern ELEMENT_NAME_PATTERN_REGEX = Pattern.compile("[a-zA-Z0-9]+");
        private final Pattern ATTRIBUTE_NAME_PATTERN_REGEX = Pattern.compile("[a-zA-Z0-9-_]+");

        public ElementNode(HtmlElement elem, List<Node> children) {
            if (!ELEMENT_NAME_PATTERN_REGEX.matcher(elem.getName()).matches()) {
                this.error = new InvalidTemplateException(
                        "Invalid element name: " + elem.getName());
                return;
            }
            this.children = children;
            this.element = elem;
            this.attributesMap = new LinkedHashMap<String, ComponentInvocation[]>();
            for (HtmlAttribute attr: elem.getAttributes()) {
                String name = attr.getName();

                if ("vrtx:attrs-from".equals(name)) {
                    this.copyAttributesExpression = attr.getValue();

                } else if ("vrtx:attrs".equals(name)) {
                    this.copyAttributesList = attr.getValue();

                } else {
                    if (name == null || !ATTRIBUTE_NAME_PATTERN_REGEX.matcher(name).matches()) {
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

        @Override
        public List<HtmlContent> generate(HtmlPage userPage, ComponentResolver componentResolver, HttpServletRequest req, 
                Map<String, Object> model) throws Exception {
            List<HtmlContent> result = new ArrayList<HtmlContent>();
            if (this.error != null) {
                result.add(userPage.createTextNode(this.error.getMessage()));
            } else {
                HtmlElement newElem = userPage.createElement(this.element.getName());

                if (this.copyAttributesExpression != null) {
                    HtmlElement userElem = userPage.selectSingleElement(this.copyAttributesExpression);
                    if (userElem != null) {

                        List<HtmlAttribute> newAttrs = new ArrayList<HtmlAttribute>();

                        if (this.copyAttributesList != null) {
                            String[] names = this.copyAttributesList.split(",");
                            for (String name: names) {
                                if (userElem.getAttribute(name) != null) {
                                    HtmlAttribute attr = userElem.getAttribute(name);
                                    newAttrs.add(userPage.createAttribute(attr.getName(), attr.getValue()));
                                }
                            }
                        } else {
                            for (HtmlAttribute attr: userElem.getAttributes()) {
                                newAttrs.add(userPage.createAttribute(attr.getName(), attr.getValue()));
                            }
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

                            if (inv instanceof StaticTextFragment) {
                                value.append(((StaticTextFragment) inv).buffer);
                            } else {
                                DecoratorRequest decoratorRequest = new DecoratorRequestImpl(
                                        userPage, req, model, inv.getParameters(), userPage.getDoctype(), locale);

                                String compNamespace = inv.getNamespace();
                                String compName = inv.getName();

                                DecoratorComponent component = 
                                    componentResolver.resolveComponent(compNamespace, compName);
                                if (component == null) {
                                    value.append("Unknown component: " + compNamespace + ":" + compName);
                                    return result;
                                } else {
                                    value.append(renderComponentAsString(component, decoratorRequest));
                                }
                            }
                        }
                        HtmlAttribute attr = userPage.createAttribute(name, value.toString());
                        newAttrs.add(attr);
                    }
                    newElem.setAttributes(newAttrs.toArray(
                            new HtmlAttribute[newAttrs.size()]));
                }

                List<HtmlContent> newElementContent = new ArrayList<HtmlContent>();
                for (Node childNode: this.children) {
                    newElementContent.addAll(childNode.generate(userPage, componentResolver, req, model));
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
        @Override
        public List<HtmlContent> generate(HtmlPage userPage, ComponentResolver componentResolver, HttpServletRequest req, 
                Map<String, Object> model) throws Exception {
            List<HtmlContent> result = new ArrayList<HtmlContent>();
            if (this.error != null) {
                result.add(userPage.createTextNode(this.error.getMessage()));
            } else {
                StringBuilder sb = new StringBuilder();
                for (ComponentInvocation inv: this.parsedContent) {
                    if (inv instanceof StaticTextFragment) {
                        sb.append(((StaticTextFragment) inv).buffer);
                    } else {

                        Locale locale = 
                            new org.springframework.web.servlet.support.RequestContext(req).getLocale();
                        DecoratorRequest decoratorRequest = new DecoratorRequestImpl(
                                userPage, req, model, inv.getParameters(), userPage.getDoctype(), locale);

                        String compNamespace = inv.getNamespace();
                        String compName = inv.getName();

                        DecoratorComponent component = 
                            componentResolver.resolveComponent(compNamespace, compName);
                        if (component == null) {
                            sb.append("Unknown component: " + compNamespace + ":" + compName);
                        } else {
                            sb.append(renderComponentAsString(component, decoratorRequest));
                        }
                    }
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

        @Override
        public List<HtmlContent> generate(HtmlPage userPage, ComponentResolver componentResolver, HttpServletRequest req, 
                Map<String, Object> model) throws Exception {
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

        public List<HtmlContent> generate(HtmlPage userPage, ComponentResolver componentResolver, HttpServletRequest req, 
                Map<String, Object> model) throws Exception {
            List<HtmlContent> result = new ArrayList<HtmlContent>();
            result.add(userPage.createTextNode(this.content));
            return result;
        }
    }


    public String toString() {
        return this.getClass().getName() + ": " + this.templateSource;
    }


}
