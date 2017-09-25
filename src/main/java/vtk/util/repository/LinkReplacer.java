/* Copyright (c) 2017, University of Oslo, Norway
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
package vtk.util.repository;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import vtk.repository.Property;
import vtk.repository.RepositoryAction;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.resourcemanagement.StructuredResource;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.property.EditablePropertyDescription;
import vtk.resourcemanagement.property.JSONPropertyAttributeDescription;
import vtk.resourcemanagement.property.JSONPropertyDescription;
import vtk.resourcemanagement.property.PropertyDescription;
import vtk.text.html.HtmlAttribute;
import vtk.text.html.HtmlContent;
import vtk.text.html.HtmlElement;
import vtk.text.html.HtmlFragment;
import vtk.text.html.HtmlPage;
import vtk.text.html.HtmlPageFilter;
import vtk.text.html.HtmlPageParser;
import vtk.util.text.JsonStreamer;

public class LinkReplacer {
    
    public static interface Context {
        
        public Resource resource();
        
        public Optional<String> mapURL(String input);
        
        public Optional<StructuredResourceDescription> resourceDescription();
    
        public void storeProperties();
        
        public Optional<InputStream> content();
        
        public void writeContent(InputStream content);
       
        public void writeRevision(StructuredResource res);
        
        public void log(String from, String to, String label);
    }
    
    public static void process(Context ctx) {
        try {
            Resource resource = ctx.resource();
            
            if ("application/json".equals(resource.getContentType())) {
                Optional<StructuredResourceDescription> desc = ctx.resourceDescription();
                if (!desc.isPresent()) return;
                processStructuredResource(desc.get(), ctx);
            }
            else {
                processProperties(ctx);
            }

            // Process content of well-known resource types:
            if ("text/html".equals(resource.getContentType())) {
                processHtmlResource(ctx);
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private static void processProperties(Context ctx) {
        Resource resource = ctx.resource();
        boolean modified = false;

        for (Property p: resource) {
            PropertyTypeDefinition def = p.getDefinition();
            if (def == null) continue;
            
            if (def.getProtectionLevel() == RepositoryAction.UNEDITABLE_ACTION) continue;
            if (p.getType() != PropertyType.Type.IMAGE_REF && p.getType() != PropertyType.Type.HTML) {
                continue;
            }
            if (def.isMultiple()) {
                List<Value> result = new ArrayList<>();
                for (Value value: p.getValues()) {
                    Value mapped = processPropValue(value, p.getType(), 
                            ctx, "property:" + def.getName());
                    if (!mapped.equals(value)) {
                        modified = true;
                    }
                    result.add(mapped);
                }
                p.setValues(result.toArray(new Value[result.size()]));
            }
            else {
                Value value = p.getValue();
                Value mapped = processPropValue(value, p.getType(), 
                        ctx, "property:" + def.getName());
                if (!mapped.equals(value)) {
                    modified = true;
                }
                p.setValue(mapped);
            }
        }
        
        if (modified) {
            try {
                ctx.storeProperties();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    private static void processStructuredResource(StructuredResourceDescription desc, 
            Context ctx) throws Exception {
        
        Optional<InputStream> is = ctx.content();
        if (!is.isPresent()) return;
        
        StructuredResource res = desc.buildResource(is.get());
        boolean modified = false;
        
        for (PropertyDescription pdesc : desc.getAllPropertyDescriptions()) {
            Object prop = res.getProperty(pdesc.getName());
            if (prop == null) continue;
            if (!(pdesc instanceof EditablePropertyDescription)) continue;
            
            switch (pdesc.getType()) {
            case "image_ref":
            case "media_ref":
            case "resource_ref":
                String mapped = mapRef(prop, ctx, 
                        "json_resource_field:" + pdesc.getName());
                if (!mapped.equals(prop.toString())) {
                    modified = true;
                }
                if (modified) {
                    res.removeProperty(pdesc.getName());
                    res.addProperty(pdesc.getName(), mapped);
                }
                break;
            case "html":
            case "simple_html":
                
                String filtered = filterHtml(prop.toString(), ctx, 
                        "json_resource_field:" + pdesc.getName());
                if (!filtered.equals(prop.toString())) {
                    modified = true;
                }
                if (modified) {
                    res.removeProperty(pdesc.getName());
                    res.addProperty(pdesc.getName(), filtered);
                }
                break;
            case "json":
                if (!(pdesc instanceof JSONPropertyDescription)) break;
                
                JSONPropertyDescription jsonDesc = (JSONPropertyDescription) pdesc;
                if (jsonDesc.isWildcard()) continue;
                List<JSONPropertyAttributeDescription> attributes = jsonDesc.getAttributes();

                if (pdesc.isMultiple()) {
                    List<Map<String, Object>> elements = (List<Map<String, Object>>) prop;

                    prop = elements.stream()
                            .map(jsonValue -> filterJsonProp(jsonValue, attributes, ctx, 
                                    "json_resource_field:" + pdesc.getName()))
                            .collect(Collectors.toList());
                    if (!prop.equals(elements)) {
                        modified = true;
                    }
                }
                else {
                    Map<String, Object> jsonValue = (Map<String, Object>) prop;
                    String before = prop.toString();
                    
                    prop = filterJsonProp(jsonValue, attributes, ctx, 
                            "json_resource_field:" + pdesc.getName());
                    
                    if (!JsonStreamer.toJson(prop).equals(before)) {
                        modified = true;
                    }
                }

                if (modified) {
                    res.removeProperty(pdesc.getName());
                    res.addProperty(pdesc.getName(), prop);
                }
                break;
            }
        }
        if (modified) ctx.writeRevision(res);
    }
    
    
    private static void processHtmlResource(Context ctx) throws Exception {
        if (ctx.resource().getContentLength() > 2000000L) return;
        Optional<InputStream> inputStream = ctx.content();
        if (!inputStream.isPresent()) return;
        HtmlPageParser parser = new HtmlPageParser();
        HtmlPage page = parser.parse(inputStream.get(), ctx.resource().getCharacterEncoding());
        URLMapFilter filter = new URLMapFilter(ctx, "html_file_content");
        page.filter(filter);
        
        ctx.writeContent(new ByteArrayInputStream(
                page.getStringRepresentation().getBytes(page.getCharacterEncoding()))); 
    }
    
    
    private static Value processPropValue(Value value, PropertyType.Type t, 
            Context ctx, String logLabel) {
        switch (t) {
        case IMAGE_REF:
            return new Value(mapRef(value.getStringValue(), ctx, logLabel), t);
        case HTML:
            return new Value(filterHtml(value.getStringValue(), ctx, logLabel), t);
        default:
            return value;
        }
    }
    
    private static Map<String, Object> filterJsonProp(Map<String, Object> jsonValue, 
            List<JSONPropertyAttributeDescription> attributes, Context ctx,
            String logLabel) {
        
        Map<String, Object> returnValue = new LinkedHashMap<>();
        
        for (JSONPropertyAttributeDescription attr: attributes) {
            switch (attr.getType()) {
            case "image_ref": 
            case "media_ref":
            case "resource_ref":
                Object ref = jsonValue.get(attr.getName());
                if (ref != null) {
                    returnValue.put(attr.getName(),
                            mapRef(ref, ctx, logLabel +
                                    "@" + attr.getName()));
                }
                break;
            case "simple_html":
            case "html":
                Object html = jsonValue.get(attr.getName());
                if (html != null) {
                    returnValue.put(attr.getName(), 
                            filterHtml(html.toString(), ctx,
                                       logLabel + "@" + attr.getName()));
                }
                break;
            default:
                Object value = jsonValue.get(attr.getName());
                if (value != null) {
                    returnValue.put(attr.getName(), value);
                }
                break;
            }
        }
        return returnValue;
    }
    
    private static String mapRef(Object ref, Context ctx, String logLabel) {
        String value = ref.toString();
        Optional<String> mapped = ctx.mapURL(value);
        if (mapped.isPresent() && !mapped.get().equals(value)) {
            ctx.log(value, mapped.get(), logLabel);
        }
        return mapped.orElse(value);
    }    
    
    private static String filterHtml(String fragment, Context ctx, String logLabel) {
        try {
            HtmlPageParser parser = new HtmlPageParser();
            HtmlFragment f = parser.parseFragment(fragment.toString());
            URLMapFilter filter = new URLMapFilter(ctx, logLabel);
            f.filter(filter);
            return f.getStringRepresentation();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private static class URLMapFilter implements HtmlPageFilter {
        private Context ctx;
        private String logLabel;
        
        public URLMapFilter(Context ctx, String logLabel) {
            this.ctx = ctx;
            this.logLabel = logLabel;
        }

        @Override
        public boolean match(HtmlPage page) {
            return true;                
        }

        @Override
        public NodeResult filter(HtmlContent node) {
            if (node instanceof HtmlElement) {
                HtmlElement elem = (HtmlElement) node;
                if ("img".equalsIgnoreCase(elem.getName())) {
                    mapURL(elem, "src");
                }
                else if ("a".equalsIgnoreCase(elem.getName())) {
                    mapURL(elem, "href");
                }
                else if ("script".equalsIgnoreCase(elem.getName())) {
                    mapURL(elem, "src");
                }
                else if ("link".equalsIgnoreCase(elem.getName())) {
                    mapURL(elem, "href");
                }
                else if ("frame".equalsIgnoreCase(elem.getName())) {
                    mapURL(elem, "src");
                }
                else if ("iframe".equalsIgnoreCase(elem.getName())) {
                    mapURL(elem, "src");
                }
                
            }
            return NodeResult.keep;
        }
        
        private void mapURL(HtmlElement elem, String attr) {
            HtmlAttribute ref = elem.getAttribute(attr);
            if (ref == null) return;
            String value = ref.getValue();
            if (value != null) {
                Optional<String> mapped = ctx.mapURL(value);
                if (mapped.isPresent()) {
                    String newValue = mapped.get();
                    if (!value.equals(newValue)) {
                        ctx.log(value, newValue, logLabel + ":" + elem.getName() + ":" + attr);
                    }
                    ref.setValue(newValue);
                }
            }
        }
    }
}
