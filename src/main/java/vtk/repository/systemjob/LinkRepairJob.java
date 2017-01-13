/* Copyright (c) 2016, University of Oslo, Norway
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
package vtk.repository.systemjob;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.repository.ContentInputSources;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.RepositoryAction;
import vtk.repository.Resource;
import vtk.repository.Revision.Type;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.PropertyTermQuery;
import vtk.repository.search.query.TermOperator;
import vtk.resourcemanagement.StructuredResource;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.StructuredResourceManager;
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
import vtk.util.repository.PropertyAspectDescription;
import vtk.util.repository.PropertyAspectResolver;
import vtk.util.text.Json.MapContainer;
import vtk.util.text.JsonStreamer;
import vtk.web.service.CanonicalUrlConstructor;
import vtk.web.service.URL;

public class LinkRepairJob extends AbstractResourceJob {
    private static final Logger logger = LoggerFactory.getLogger(LinkRepairJob.class);
    private StructuredResourceManager resourceManager;
    private PropertyTypeDefinition aspectsPropDef;
    private PropertyAspectDescription aspectFieldDesc;
    private String enabledAspect;
    private CanonicalUrlConstructor urlConstructor;

    public LinkRepairJob(StructuredResourceManager resourceManager, 
            PropertyTypeDefinition aspectsPropDef, 
            PropertyAspectDescription aspectFieldDesc,
            String enabledAspect,
            CanonicalUrlConstructor urlConstructor) {
        this.resourceManager = resourceManager;
        this.aspectsPropDef = aspectsPropDef;
        this.aspectFieldDesc = aspectFieldDesc;
        this.enabledAspect = enabledAspect;
        this.urlConstructor = urlConstructor;
    }
    
    
    @Override
    protected void executeForResource(Resource resource, ExecutionContext ctx)
            throws Exception {
        if (resource.getLock() != null) {
            logger.debug("Resource is locked, skipping: " + resource);
            return;
        }
        URL base = urlConstructor.canonicalUrl(resource).setImmutable();
        PropertyAspectResolver resolver = 
                new PropertyAspectResolver(ctx.getRepository(), 
                        aspectsPropDef, aspectFieldDesc, ctx.getToken());
        
        MapContainer aspect = resolver.resolve(resource.getURI(), enabledAspect);
        boolean enabled = aspect != null && "true".equals(aspect.get("auto-link-repair"));
        if (!enabled) {
            logger.debug("Link repair disabled for " + resource);
            ctx.getRepository().store(ctx.getToken(), resource);
            return;
        }
        
        logger.debug("Correcting links for resource " + resource + "; " + ctx);
        UrlMapper mapper = new UrlMapper(resource, base, ctx);
        
        if ("application/json".equals(resource.getContentType())) {
            StructuredResourceDescription desc = 
                    resourceManager.get(resource.getResourceType());
            if (desc == null) {
                return;
            }
            processStructuredResource(resource, ctx, desc, mapper);
        }
        else {
            processProperties(resource, ctx, mapper);
        }

        // Process content of well-known resource types:
        
        if ("text/html".equals(resource.getContentType())) {
            processHtmlResource(resource, ctx, mapper);
        }
    }
    
    private void processProperties(Resource resource, ExecutionContext ctx, UrlMapper mapper) {
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
                    Value mapped = processPropValue(value, p.getType(), mapper);
                    modified = modified || !mapped.equals(value);
                    result.add(mapped);
                }
                p.setValues(result.toArray(new Value[result.size()]));
            }
            else {
                Value value = p.getValue();
                Value mapped = processPropValue(value, p.getType(), mapper);
                p.setValue(mapped);
                modified = modified || !mapped.equals(value);
            }
        }
        
        if (modified) {
            try {
                // XXX: cannot use system change context when storing properties:
                //ctx.getRepository().store(ctx.getToken(), resource, 
                //        ctx.getSystemChangeContext());
                ctx.getRepository().store(ctx.getToken(), resource);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    private Value processPropValue(Value value, PropertyType.Type t, UrlMapper mapper) {
        switch (t) {
        case IMAGE_REF:
            return new Value(mapRef(value.getStringValue(), mapper), t);
        case HTML:
            return new Value(
                    filterHtml(value.getStringValue(), mapper), t);
        default:
            return value;
        }
    }
    
    private void processHtmlResource(Resource resource, ExecutionContext ctx, UrlMapper mapper) throws Exception {
        if (resource.getContentLength() > 2000000L) return;
        InputStream inputStream = ctx.getRepository()
                .getInputStream(ctx.getToken(), resource.getURI(), false);
        HtmlPageParser parser = new HtmlPageParser();
        HtmlPage page = parser.parse(inputStream, resource.getCharacterEncoding());
        UrlMapFilter filter = new UrlMapFilter(mapper);
        page.filter(filter);
        Resource stored = ctx.getRepository().storeContent(ctx.getToken(), 
                resource.getURI(), ContentInputSources.fromString(
                        page.getStringRepresentation(), page.getCharacterEncoding()));
        ctx.getRepository().store(ctx.getToken(), stored, ctx.getSystemChangeContext());
    }
    
    private void processStructuredResource(Resource resource, ExecutionContext ctx, 
            StructuredResourceDescription desc, UrlMapper mapper) throws Exception {
        
        InputStream is = ctx.getRepository()
                .getInputStream(ctx.getToken(), resource.getURI(), false);
        
        StructuredResource res = desc.buildResource(is);
        boolean modified = false;
        
        for (PropertyDescription pdesc : desc.getAllPropertyDescriptions()) {
            Object prop = res.getProperty(pdesc.getName());
            if (prop == null) continue;
            if (!(pdesc instanceof EditablePropertyDescription)) continue;
            
            switch (pdesc.getType()) {
            case "image_ref":
            case "media_ref":
            case "resource_ref":
                String mappedd = mapRef(prop, mapper);
                modified = modified || !mappedd.equals(prop.toString());
                if (modified) {
                    res.removeProperty(pdesc.getName());
                    res.addProperty(pdesc.getName(), mappedd);
                }
                break;
            case "html":
            case "simple_html":
                String filtered = filterHtml(prop.toString(), mapper);
                modified = modified || !filtered.equals(prop.toString());
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
                String before = prop.toString();

                if (pdesc.isMultiple()) {
                    List<Map<String, Object>> elements = (List<Map<String, Object>>) prop;

                    prop = elements.stream()
                            .map(jsonValue -> filterJsonProp(jsonValue, attributes, mapper))
                            .collect(Collectors.toList());
                    modified = modified || !prop.equals(elements);
                }
                else {
                    Map<String, Object> jsonValue = (Map<String, Object>) prop;
                    prop = filterJsonProp(jsonValue, attributes, mapper);
                    modified = modified || !jsonValue.equals(before);
                }

                if (modified) {
                    res.removeProperty(pdesc.getName());
                    res.addProperty(pdesc.getName(), prop);
                }
                break;
            }
        }
        if (modified) writeRevision(ctx, resource.getURI(), res);
    }

    
    private void writeRevision(ExecutionContext ctx, Path uri, StructuredResource res) {
        try {
            final byte[] buffer = JsonStreamer.toJson(res.toJSON(), 3, false).getBytes("utf-8");

            ctx.getRepository().createRevision(ctx.getToken(), uri, Type.REGULAR);
            Resource stored = ctx.getRepository()
                    .storeContent(ctx.getToken(), uri, ContentInputSources.fromBytes(buffer));
            ctx.getRepository().store(ctx.getToken(), stored, ctx.getSystemChangeContext());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private Map<String, Object> filterJsonProp(Map<String, Object> jsonValue, 
            List<JSONPropertyAttributeDescription> attributes, UrlMapper mapper) {
        for (JSONPropertyAttributeDescription attr: attributes) {
            switch (attr.getType()) {
            case "image_ref": 
            case "media_ref":
            case "resource_ref":
                Object ref = jsonValue.get(attr.getName());
                jsonValue.put(attr.getName(), mapRef(ref, mapper));
                break;
            case "simple_html":
            case "html":
                String html = jsonValue.get(attr.getName()).toString();
                jsonValue.put(attr.getName(), filterHtml(html, mapper));
                break;
            }
        }
        return jsonValue;
    }
    
    private String mapRef(Object ref, UrlMapper mapper) {
        String value = ref.toString();
        Optional<String> mapped = mapper.mapUrl(value);
        return mapped.orElse(value);
    }    
    
    private String filterHtml(String fragment, UrlMapper mapper) {
        try {
            HtmlPageParser parser = new HtmlPageParser();
            HtmlFragment f = parser.parseFragment(fragment.toString());
            UrlMapFilter filter = new UrlMapFilter(mapper);
            f.filter(filter);
            return f.getStringRepresentation();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    
    private static class UrlMapper {
        private final URL base;
        private Map<String, String> vrtxIdMap;
        private Map<String, String> relocMap;
        
        public UrlMapper(Resource resource, URL base, ExecutionContext ctx) throws Exception {
            this.base = base;
            this.vrtxIdMap = mapIds(resource);
            this.relocMap = mapLinks(vrtxIdMap, ctx);
        }
        
        public Optional<String> mapUrl(String input) {            
            if (vrtxIdMap.containsKey(input)) {
                String vrtxid = vrtxIdMap.get(input);
                return Optional.ofNullable(relocMap.get(vrtxid));
            }
            return Optional.empty();
        }

        private Map<String, String> mapIds(Resource resource) throws Exception {
            Map<String, String> vrtxIdMap = new HashMap<>();
            MapContainer jsonValue = resource
                    .getProperty(Namespace.DEFAULT_NAMESPACE, "link-check")
                    .getJSONValue();
            jsonValue.arrayValue("relocatedLinks").forEach(urlObj-> {
                Map<?,?> link = (Map<?,?>) urlObj;
                if (link.containsKey("link") && link.containsKey("vrtxid")) {
                    String key = link.get("link").toString();
                    String value = link.get("vrtxid").toString();
                    vrtxIdMap.put(key, value);
                    if (key.startsWith("http://") || key.startsWith("https://")) {
                        try {
                            URL url = URL.parse(key);
                            if (url.getHost().equals(base.getHost())) {
                                vrtxIdMap.put(url.getPathRepresentation(), value);
                            }
                        }
                        catch (Throwable t) { }
                    }
                    
                }
            });
            return vrtxIdMap;
        }
        
        private Map<String, String> mapLinks(Map<String, String> vrtxIdMap, ExecutionContext ctx) {
            Map<String, String> resultMap = new HashMap<>();
            if (vrtxIdMap.isEmpty()) return resultMap;
            
            PropertyTypeDefinition idPropDef = ctx.getRepository()
                    .getTypeInfo("resource")
                    .getPropertyTypeDefinition(Namespace.DEFAULT_NAMESPACE, 
                            PropertyType.EXTERNAL_ID_PROP_NAME);

            OrQuery query = new OrQuery();
            vrtxIdMap.entrySet().forEach(entry -> {
                query.add(new PropertyTermQuery(idPropDef, entry.getValue(), TermOperator.EQ));
            });
            
            Search search = new Search();
            search.setQuery(query);
            search.setLimit(vrtxIdMap.size());
            search.clearAllFilterFlags();
            search.setSorting(null);
            //search.setPropertySelect(PropertySelect.NONE);
            
            ResultSet hits = ctx.getRepository().search(ctx.getToken(), search);
            hits.getAllResults().forEach(resource -> {
                String vrtxid = resource.getProperty(idPropDef).getStringValue();
                resultMap.put(vrtxid, resource.getURI().toString());
            });
            return resultMap;
        }
    }

    
    private static class UrlMapFilter implements HtmlPageFilter {
        private UrlMapper mapper;

        public UrlMapFilter(UrlMapper mapper) {
            this.mapper = mapper;
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
                Optional<String> mapped = mapper.mapUrl(value);
                if (mapped.isPresent()) {
                    ref.setValue(mapped.get());
                }
            }
        }
    }    
}
