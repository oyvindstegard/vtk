/* Copyright (c) 2015 University of Oslo, Norway
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom.Document;
import org.jdom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.PropertyEvaluationContext.Type;
import vtk.repository.Resource;
import vtk.repository.resourcetype.LatePropertyEvaluator;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.Value;
import vtk.resourcemanagement.StructuredResource;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.StructuredResourceManager;
import vtk.resourcemanagement.property.JSONPropertyAttributeDescription;
import vtk.resourcemanagement.property.JSONPropertyDescription;
import vtk.resourcemanagement.property.PropertyDescription;
import vtk.text.html.TagsoupParserFactory;
import vtk.util.text.Json;
import vtk.web.service.URL;

public class HrefsEvaluator implements LatePropertyEvaluator {
    private StructuredResourceManager resourceManager;
    private static final int MAX_LINKS = 500;

    public static enum RelType {
        ABSOLUTE,
        RELATIVE,
        ROOT_RELATIVE,
        PROTOCOL_RELATIVE
    }

    public HrefsEvaluator(StructuredResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }
    
    
    @Override
    public boolean evaluate(Property property, PropertyEvaluationContext ctx)
            throws PropertyEvaluationException {
        final LinkCollector collector = new LinkCollector();
        boolean evaluateContent = true;
        try {
            if (ctx.getEvaluationType() != Type.ContentChange 
                && ctx.getEvaluationType() != Type.Create) {
                // Preserve existing content links, since this is not content change.
                evaluateContent = false;
            } 
            
            if (property.isValueInitialized()) {
                Json.MapContainer mmap = property.getJSONValue();
                Json.ListContainer aarr = mmap.arrayValue("links");
                for (Object o: aarr) {
                    if (! (o instanceof Json.MapContainer)) continue;
                    Json.MapContainer obj = (Json.MapContainer) o;
                    String url = obj.stringValue("url");
                    String type = obj.stringValue("type");
                    String vrtxid = obj.optStringValue("vrtxid", null);
                    // Preserve existing (URL -> vrtxid) mappings
                    if (vrtxid != null) collector.mapping(url, vrtxid);

                    if (!evaluateContent) {
                        LinkSource source = LinkSource.valueOf(obj.stringValue("source"));
                        if (source == LinkSource.CONTENT) {
                            Link link = new Link(url, LinkType.valueOf(type), source);
                            if (!collector.link(link)) {
                                break;
                            }
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            // Some error in old property value, ignore and start fresh
            collector.clear();
        }
        
        try {
            Resource resource = ctx.getNewResource();
            for (Property p: resource) {
                if (p.getType() == PropertyType.Type.IMAGE_REF) {
                    String[] values = propertyValues(p);
                    for (String value: values) {
                        Link link = new Link(value, LinkType.PROPERTY, LinkSource.PROPERTIES);
                        if (!collector.link(link)) {
                            break;
                        }
                        
                    }
                }
                else if (p.getType() == PropertyType.Type.HTML) {
                    String[] values = propertyValues(p);
                    for (String value: values) {
                        InputStream is = new ByteArrayInputStream(value.getBytes());
                        extractFromHtml(is, collector, LinkSource.PROPERTIES);
                    }
                }
            }

            if (evaluateContent && ctx.getContent() != null) {
                if ("application/json".equals(resource.getContentType())) {
                    // if structured resource, find links in JSON or HTML fields 
                    // that are not evaluated as properties by the regular mechanism:
                    StructuredResourceDescription desc = 
                            resourceManager.get(resource.getResourceType());
                    if (desc != null) {
                        StructuredResource res = 
                                desc.buildResource(ctx.getContent().getContentInputStream());

                        for (PropertyDescription pdesc : desc.getAllPropertyDescriptions()) {
                            Object p = res.getProperty(pdesc.getName());
                            if (p == null) {
                                continue;
                            }
                            if ("json".equals(pdesc.getType())) {
                                JSONPropertyDescription jsonDesc = (JSONPropertyDescription) pdesc;
                                extractFromJson(p, jsonDesc, collector);
                            }
                            else {
                                InputStream is = new ByteArrayInputStream(p.toString().getBytes());
                                extractFromHtml(is, collector, LinkSource.CONTENT);
                            }
                        }
                    }
                }
                else if ("text/html".equals(resource.getContentType())) {
                    extractFromHtml(ctx.getContent().getContentInputStream(), 
                            collector, LinkSource.CONTENT);
                }
                else if ("text/xml".equals(resource.getContentType())) {
                    Document doc = ctx.getContent().getContentRepresentation(Document.class);
                    extractFromXml(doc, collector, LinkSource.CONTENT);
                }
            }

            if (collector.isEmpty()) {
                return false;
            }

            Json.MapContainer value = new Json.MapContainer();
            Json.ListContainer array = collector.toJsonArray();
            value.put("links", array);
            value.put("size", array.size());
            property.setJSONValue(value);
            
            return true;
        }
        catch (Throwable t) {
            return false;
        }
    }

    

    private String[] propertyValues(Property p) {
        if (p.getDefinition().isMultiple()) {
            Value[] values = p.getValues();
            String[] result = new String[values.length];
            for (int i = 0; i < values.length; i++)
                result[i] = values[i].getStringValue();
            return result;
        }
        return new String[]{p.getValue().getStringValue()};
    }
    
    private enum LinkSource {
        PROPERTIES,
        CONTENT
    }
    
    private enum LinkType {
        ANCHOR,
//        CSS,
        LINK,
        IMG,
        SCRIPT,
        FRAME,
        IFRAME,
        PROPERTY,
        OBJ
    }
    
    private static class Link {

        private String url;
        private LinkType type;
        private LinkSource source;
        public Link(String url, LinkType type, LinkSource source) {
            this.url = url.trim();
            this.type = type;
            this.source = source;
        }
        public String getURL() { return url; }
        public LinkType getType() { return type; }
        public LinkSource getSource() { return source; }
        
        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Link other = (Link) obj;
            if ((this.url == null) ? (other.url != null) : !this.url.equals(other.url)) {
                return false;
            }
            if (this.type != other.type) {
                return false;
            }
            if (this.source != other.source) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 37 * hash + (this.url != null ? this.url.hashCode() : 0);
            hash = 37 * hash + (this.type != null ? this.type.hashCode() : 0);
            hash = 37 * hash + (this.source != null ? this.source.hashCode() : 0);
            return hash;
        }
    }
    
    private static class LinkCollector {
        private Map<String, String> idMap = new HashMap<>();
        private final List<Link> links = new ArrayList<>();
        
        public void mapping(String url, String id) {
            idMap.put(url, id);
        }

        /**
         * Add a link. Links with URL size greather than {@link Property#MAX_STRING_LENGTH}
         * will be silently discarded.
         * 
         * @param link link to add
         * @return <code>false</code> if maximum amount of links have been added
         * to collector. This can be used to stop extraction from the current
         * source.
         */
        public boolean link(Link link) {
            if (links.size() >= MAX_LINKS) {
                return false;
            }
            if (link.url.length() <= Property.MAX_STRING_LENGTH) {
                links.add(link);
            }
            return true;
        }
        public void clear() {
            links.clear();
        }
        public boolean isEmpty() {
            return links.isEmpty();
        }
        public Json.ListContainer toJsonArray() {
            Json.ListContainer list = new Json.ListContainer();
            for (Link l: links) {
                Json.MapContainer object = new Json.MapContainer();
                object.put("url", l.getURL());
                if (idMap.containsKey(l.getURL())) {
                    object.put("vrtxid", idMap.get(l.getURL()));
                }
                object.put("type", l.getType());
                object.put("reltype", relType(l.getURL()));
                object.put("source", l.getSource());
                list.add(object);
            }
            return list;
        }
        
        private String relType(String ref) {
            if (ref.startsWith("//")) return RelType.PROTOCOL_RELATIVE.name();
            if (URL.isRelativeURL(ref)) return RelType.RELATIVE.name();
            if (ref.startsWith("/") && !ref.contains("../") && !ref.contains("./")) 
                return RelType.ROOT_RELATIVE.name();
            return RelType.ABSOLUTE.name();
        }
    }

    
    // TODO Handlers/link parsing strategies should be pluggable, keyed on content type.
        
    private void extractFromHtml(InputStream is,
                                  LinkCollector collector,
                                  LinkSource source)
        throws Exception {
        
        org.ccil.cowan.tagsoup.Parser parser = TagsoupParserFactory.newParser(true);
        parser.setContentHandler(new HtmlHandler(collector, source));

        InputSource input = new InputSource(is);

        try {
            parser.parse(input);
        }
        catch (StopException t) { 
        }
        finally {
            is.close();
        }
    }
    
    private void extractFromXml(Document doc,
                                 LinkCollector collector,
                                 LinkSource source) throws Exception {
        
        Iterator<?> nodeIterator = doc.getDescendants();
        while (nodeIterator.hasNext()) {
            Object next = nodeIterator.next();
            if (! (next instanceof Element)) {
                continue;
            }

            Element element = (Element) next;
            Element parent = element.getParentElement();

            String href = null;

            if ("webadresse".equals(element.getName())
                    || "url".equals(element.getName())) {
                href = element.getTextTrim();
            }
            else {
                if (parent != null) {
                    if ("pensumpunkt".equals(parent.getName()) || "bilde-referanse".equals(parent.getName())) {
                        if ("src".equals(element.getName())
                            || "lenkeadresse".equals(element.getName())
                            || "bibsys".equals(element.getName())
                            || "fulltekst".equals(element.getName())) {
                            href = element.getTextTrim();
                        }
                    }
                }
            }
            
            if (href != null && !href.isEmpty()) {
                LinkType type = LinkType.ANCHOR;
                if (parent != null && ("bilde".equals(parent.getName()) 
                        || "bilde-referanse".equals(parent.getName()))) {
                    type = LinkType.IMG;
                }
                if (!collector.link(new Link(href, type, source))) {
                    break;
                }
            }
        }
    }
    
    @SuppressWarnings("serial")
    private static class StopException extends RuntimeException { }

    private static class HtmlHandler extends DefaultHandler {
        private final LinkCollector listener;
        private final LinkSource linkSource;

        public HtmlHandler(LinkCollector listener, LinkSource source) {
            this.listener = listener;
            this.linkSource = source;
        }
        
        private static final Set<String> ELEMS = new HashSet<>(Arrays.asList(new String[]{
                "a", "img", "script", "link", "frame", "iframe"
        }));
        
        @Override
        public void startElement(String namespaceUri, String localName, String qName,
                Attributes attrs) throws SAXException {
            if (localName == null) {
                return;
            }
            localName = localName.toLowerCase();
            if (ELEMS.contains(localName)) {
                for (int i = 0; i < attrs.getLength(); i++) {
                    String attrName = attrs.getQName(i);
                    String attrValue = attrs.getValue(i);
                    
                    LinkType type = null;
                    
                    if ("a".equals(localName) && "href".equals(attrName)) {
                        type = LinkType.ANCHOR;
                    }
                    else if ("img".equals(localName) && "src".equals(attrName)) {
                        type = LinkType.IMG;
                    }
                    else if ("script".equals(localName) && "src".equals(attrName)) {
                        type = LinkType.SCRIPT;
                    }
                    else if ("link".equals(localName) && "href".equals(attrName)) {
                        type = LinkType.LINK;
                    }
                    else if ("frame".equals(localName) && "src".equals(attrName)) {
                        type = LinkType.FRAME;
                    }
                    else if ("iframe".equals(localName) && "src".equals(attrName)) {
                        type = LinkType.IFRAME;
                    }
                    if (type != null && attrValue != null) {
                        if (!this.listener.link(new Link(attrValue, type, this.linkSource))) {
                            throw new StopException();
                        }
                    }
                }                
            }
        }
    }
    
    private void extractFromJson(Object object, JSONPropertyDescription jsonDesc, 
            LinkCollector collector) throws Exception {
        List<JSONPropertyAttributeDescription> attributes = jsonDesc.getAttributes();
        if (jsonDesc.isMultiple()) {
            if (!(object instanceof List)) {
                extractDefault(object, collector);
                return;
            }
            List<?> list = (List<?>) object;
            for (Object o: list) {
                if (!(o instanceof Map)) {
                    extractDefault(o, collector);
                }
                Map<?, ?> map = (Map<?, ?>) o;
                extractFields(map, attributes, collector);
            }
        }
        else {
            if (!(object instanceof Map)) {
                extractDefault(object, collector);
                return;
            }
            Map<?, ?> map = (Map<?, ?>) object;
            extractFields(map, attributes, collector);
        }
    }
    
    private void extractFields(Map<?,?> map, 
            List<JSONPropertyAttributeDescription> fields, 
            LinkCollector collector) throws Exception {
        
        for (JSONPropertyAttributeDescription field: fields) {
            Object o = map.get(field.getName());
            if (o == null) continue;
            
            if (field.isRefType()) {
                Link link = new Link(o.toString(), LinkType.PROPERTY, LinkSource.CONTENT);
                collector.link(link);
            }
            else {
                InputStream is = new ByteArrayInputStream(o.toString().getBytes());
                extractFromHtml(is, collector, LinkSource.CONTENT);
            }
        }
    }
    

    private void extractDefault(Object object, LinkCollector collector) throws Exception {
        if (object != null) {
            InputStream is = new ByteArrayInputStream(object.toString().getBytes());
            extractFromHtml(is, collector, LinkSource.CONTENT);
        }
    }
    
    
}
