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
package vtk.repository.resourcetype.property;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.CharBuffer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.springframework.beans.factory.annotation.Required;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.Resource;
import vtk.repository.resourcetype.LatePropertyEvaluator;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.resourcemanagement.StructuredResource;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.StructuredResourceManager;
import vtk.resourcemanagement.property.PropertyDescription;
import vtk.text.html.TagsoupParserFactory;
import vtk.util.web.LinkTypesPrefixes;

public class MixedContentEvaluator implements LatePropertyEvaluator {

    private StructuredResourceManager resourceManager;
    private PropertyTypeDefinition ssiDirectivesPropDef;
    
    @Override
    public boolean evaluate(Property property, PropertyEvaluationContext ctx)
            throws PropertyEvaluationException {
    
        try {

            Report report = new Report();
            Resource r = ctx.getNewResource();
            Property ssiProp = r.getProperty(this.ssiDirectivesPropDef);
            if (ssiProp != null) {
                for (Value v: ssiProp.getValues()) {
                    String s = v.getStringValue();
                    if (s.indexOf("include") != -1) {
                        report.unsafeElement("ssi:" + s);
                    }
                }
            }

            for (Property p: r) {
                if (p.getType() == PropertyType.Type.IMAGE_REF) {
                    String href = p.getStringValue();
                    if (checkLink(href)) {
                        report.includedContent("property", href);
                    }
                } else if (p.getType() == PropertyType.Type.HTML) {
                    InputStream is = new ByteArrayInputStream(p.getStringValue().getBytes());
                    checkHtml(is, report);
                }
            }
            
            checkContent(ctx, report);
            if (!report.hasWarnings()) {
                return false;
            }
            Value[] values = new Value[report.warnings.size()];
            int i = 0;
            for (String err: report.warnings) {
                values[i++] = new Value(err, PropertyType.Type.STRING);
            }
            property.setValues(values);
            return true;
        } catch (Exception e) {
            
            return false;
        }
    }

    private static class Report {
        private static final int MAX_WARNINGS = 10;
        private Set<String> warnings = new HashSet<String>();
        public boolean includedContent(String name, String href) {
            if (this.warnings.size() < MAX_WARNINGS) {
                this.warnings.add(name + ":" + href);
            }
            return this.warnings.size() < MAX_WARNINGS;
        }
        
        public boolean unsafeAttribute(String name) {
            if (this.warnings.size() < MAX_WARNINGS) {
                this.warnings.add("attr:" + name);
            }
            return this.warnings.size() < MAX_WARNINGS;
        }
        
        public boolean unsafeElement(String name) {
            if (this.warnings.size() < MAX_WARNINGS) {
                this.warnings.add("element:" + name);
            }
            return this.warnings.size() < MAX_WARNINGS;
        }
        
        public boolean hasWarnings() {
            return !this.warnings.isEmpty();
        }
    }
    
    private void checkContent(PropertyEvaluationContext ctx, Report report) {
        try {
            Resource resource = ctx.getNewResource();
            if (ctx.getContent() != null) {
                if ("application/json".equals(resource.getContentType())) {
                    StructuredResourceDescription desc = this.resourceManager.get(resource.getResourceType());
                    if (desc != null) {

                        StructuredResource res = desc.buildResource(ctx.getContent().getContentInputStream());

                        for (PropertyDescription pdesc : desc.getAllPropertyDescriptions()) {
                            if (pdesc.isNoExtract()) {
                                Object p = res.getProperty(pdesc.getName());
                                if (p == null) {
                                    continue;
                                }
                                if ("json".equals(pdesc.getType())) {
                                    checkJSON(p, report);
                                } else {
                                    InputStream is = new ByteArrayInputStream(p.toString().getBytes());
                                    checkHtml(is, report);
                                }
                            }
                        }
                    }
                } else if ("text/html".equals(resource.getContentType())) {
                    checkHtml(ctx.getContent().getContentInputStream(), report);
                    
                } else if ("text/xml".equals(resource.getContentType())) {
                    Document doc = ctx.getContent().getContentRepresentation(Document.class);
                    checkXml(doc, report);
                }
            }
        } catch (Throwable t) { }
    }
    
    private static boolean checkLink(String value) {
        if (value == null) {
            return false;
        }
        value = value.toLowerCase();
        return (value.startsWith("http://") 
                || value.startsWith(LinkTypesPrefixes.JAVASCRIPT) 
                || value.startsWith("data:"));
    }
    
    //private static final Pattern STYLE_PATTERN = Pattern.compile("(url\\(http:|@import)");
    private static final Pattern STYLE_PATTERN = Pattern.compile("url\\(http:");
    private static boolean checkStyle(CharSequence value) {
        if (value == null) {
            return false;
        }
        boolean match = STYLE_PATTERN.matcher(value).find();
        return match;
    }
    
    private void checkJSON(Object object, Report report) throws Exception {
        if (object instanceof List) {
            List<?> list = (List<?>) object;
            for (Object o: list) {
                checkJSON(o, report);
            }
            
        } else if (object instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) object;
            for (Object k: map.keySet()) {
                Object o = map.get(k);
                checkJSON(o, report);
            }
        } else if (object != null) {
            InputStream is = new ByteArrayInputStream(object.toString().getBytes());
            checkHtml(is, report);
        }
    }
    
    @SuppressWarnings("serial")
    private static class StopException extends RuntimeException {} 
    
    private void checkHtml(InputStream is, Report report) throws Exception {

        org.ccil.cowan.tagsoup.Parser parser = TagsoupParserFactory.newParser(true);
        parser.setFeature(org.ccil.cowan.tagsoup.Parser.namespacesFeature, true);
        parser.setFeature(org.ccil.cowan.tagsoup.Parser.ignoreBogonsFeature, false);
        parser.setContentHandler(new HtmlHandler(report));

        InputSource input = new InputSource(is);

        try {
            parser.parse(input);
        } catch (StopException t) { 
        } finally {
            is.close();
        }
    }
    
    private void checkXml(Document doc,
            Report report) throws Exception {

        Iterator<?> nodeIterator = doc.getDescendants();
        while (nodeIterator.hasNext()) {
            Object next = nodeIterator.next();
            if (! (next instanceof Element)) {
                continue;
            }
            Element element = (Element) next;
            Element parent = element.getParentElement();

            String href = null;

            if (parent != null) {
                if ("bilde-referanse".equals(parent.getName()) 
                        && ("src".equals(element.getName())
                            || "lenkeadresse".equals(element.getName()))) {
                        href = element.getTextTrim();
                } else if ("bilde".equals(parent.getName())
                        && "webadresse".equals(element.getName())) {
                    href = element.getTextTrim();
                }
            }
            if (checkLink(href)) {
                if (!report.includedContent("xml:img", href)) {
                    break;
                }
            }
        }
    }
    

    private static class HtmlHandler extends DefaultHandler {
        private final Report report;
        private Stack<String> elemStack = new Stack<String>();

        public HtmlHandler(Report report) {
            this.report = report;
        }

        
        @Override
        public void characters(char[] ch, int start, int length)
                throws SAXException {
            if ("style".equals(elemStack.peek())) {
                CharBuffer buf = CharBuffer.wrap(ch, start, length);
                if (checkStyle(buf)) {
                    this.report.unsafeElement("style");
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            if (localName == null) {
                return;
            }
            this.elemStack.pop();
        }

        @Override
        public void startElement(String namespaceUri, String localName, String qName,
                Attributes attrs) throws SAXException {
            if (localName == null) {
                return;
            }
            localName = localName.toLowerCase();
            this.elemStack.push(localName);
            
            if ("script".equals(localName)) {
                this.report.unsafeElement(localName);
                String attr = attrs.getValue("src");
                if (checkLink(attr)) {
                    this.report.includedContent(localName, attr);
                }
                return;
            }

            if ("img".equals(localName) || "iframe".equals(localName)
                    || "frame".equals(localName) || "embed".equals(localName)) {
                String attr = attrs.getValue("src");
                if (checkLink(attr)) {
                    this.report.includedContent(localName, attr);
                    return;
                }
            }
            
            if ("link".equals(localName) || "base".equals(localName)) {
                String attr = attrs.getValue("href");
                if (checkLink(attr)) {
                    this.report.includedContent(localName, attr);
                    return;
                }
            }
            
            if ("object".equals(localName)) {
                String attr = attrs.getValue("data");
                if (checkLink(attr)) {
                    this.report.includedContent(localName, attr);
                    return;
                }
            }
            
            if ("applet".equals(localName)) {
                String attr = attrs.getValue("code");
                if (checkLink(attr)) {
                    this.report.includedContent(localName, attr);
                    return;
                }
            }
            
            if ("esi:include".equals(qName)) {
                this.report.unsafeElement(qName);
                return;
            }
            
            for (int i = 0; i < attrs.getLength(); i++) {
                String attrName = attrs.getQName(i).toLowerCase();
                if (attrName.startsWith("on")) {
                    this.report.unsafeAttribute(attrName);
                    return;
                } else if (attrName.equals("style")) {
                    String attrValue = attrs.getValue(i);
                    if (checkStyle(attrValue)) {
                        this.report.unsafeAttribute("style:" + StringUtils.abbreviate(attrValue, 100));
                    }
                }
            }
        }
    }

    @Required
    public void setResourceManager(StructuredResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @Required
    public void setSsiDirectivesPropDef(PropertyTypeDefinition ssiDirectivesPropDef) {
        this.ssiDirectivesPropDef = ssiDirectivesPropDef;
    }
}
