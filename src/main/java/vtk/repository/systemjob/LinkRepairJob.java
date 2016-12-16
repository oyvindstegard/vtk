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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.repository.ContentInputSources;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Resource;
import vtk.repository.Revision.Type;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.PropertyTermQuery;
import vtk.repository.search.query.TermOperator;
import vtk.resourcemanagement.StructuredResource;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.StructuredResourceManager;
import vtk.resourcemanagement.property.EditablePropertyDescription;
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

public class LinkRepairJob extends AbstractResourceJob {
    private static final Logger logger = LoggerFactory.getLogger(LinkRepairJob.class);
    private StructuredResourceManager resourceManager;
    private PropertyTypeDefinition aspectsPropDef;
    private PropertyAspectDescription aspectFieldDesc;
    private String enabledAspect;
    
    public LinkRepairJob(StructuredResourceManager resourceManager, 
            PropertyTypeDefinition aspectsPropDef, 
            PropertyAspectDescription aspectFieldDesc,
            String enabledAspect) {
        this.resourceManager = resourceManager;
        this.aspectsPropDef = aspectsPropDef;
        this.aspectFieldDesc = aspectFieldDesc;
        this.enabledAspect = enabledAspect;
    }
    
    
    @Override
    protected void executeForResource(Resource resource, ExecutionContext ctx)
            throws Exception {
        if (resource.getLock() != null) {
            logger.debug("Resource is locked, skipping: " + resource);
            return;
        }
        
        PropertyAspectResolver resolver = 
                new PropertyAspectResolver(ctx.getRepository(), 
                        aspectsPropDef, aspectFieldDesc, ctx.getToken());
        
        MapContainer aspect = resolver.resolve(resource.getURI(), enabledAspect);
        boolean enabled = aspect != null && "true".equals(aspect.get("link-repair"));
        if (!enabled) {
            logger.debug("Link repair disabled for " + resource);
            ctx.getRepository().store(ctx.getToken(), resource);
            return;
        }
        logger.debug("Correcting links for resource " + resource + "; " + ctx);
        
        if ("application/json".equals(resource.getContentType())) {
            UrlMapper mapper = new UrlMapper(resource, ctx);
            StructuredResourceDescription desc = 
                    resourceManager.get(resource.getResourceType());
            if (desc == null) {
                return;
            }
            processStructuredResource(resource, ctx, desc, mapper);
            
        }
        else if ("text/html".equals(resource.getContentType())) {
            UrlMapper mapper = new UrlMapper(resource, ctx);
            processHtmlResource(resource, ctx, mapper);
        }
        // XXX: handle other types (collections, markdown, ..)
    }
    
    private void processStructuredResource(Resource resource, ExecutionContext ctx, 
            StructuredResourceDescription desc, UrlMapper mapper) throws Exception {
        
        InputStream is = ctx.getRepository()
                .getInputStream(ctx.getToken(), resource.getURI(), false);
        
        StructuredResource res = desc.buildResource(is);
        boolean modified = false;
        
        for (PropertyDescription pdesc : desc.getAllPropertyDescriptions()) {
            
            Object prop = res.getProperty(pdesc.getName());
            
            if (!(pdesc instanceof EditablePropertyDescription)) continue;
            
            switch (pdesc.getType()) {
            case "image_ref":
            case "media_ref":
            case "resource_ref":
                if (prop != null) {
                    String filtered = filterRefProp(prop, mapper);
                    res.removeProperty(pdesc.getName());
                    res.addProperty(pdesc.getName(), filtered);
                    if (!filtered.equals(prop.toString())) modified = true;
                }
                break;
            case "html":
            case "simple_html":
                if (prop != null) {
                    String filtered = filterHtmlProp(prop, mapper);
                    res.removeProperty(pdesc.getName());
                    res.addProperty(pdesc.getName(), filtered);
                    if (!filtered.equals(prop.toString())) modified = true;
                }
                break;
            case "json":
                if (prop != null) {

                }
                break;
            }
        }
        if (modified) writeRevision(ctx, resource.getURI(), res);
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
    
    private void writeRevision(ExecutionContext ctx, Path uri, StructuredResource res) throws Exception {
        final byte[] buffer = JsonStreamer.toJson(res.toJSON(), 3, false).getBytes("utf-8");
        
        ctx.getRepository().createRevision(ctx.getToken(), uri, Type.REGULAR);
        Resource stored = ctx.getRepository().storeContent(ctx.getToken(), uri, ContentInputSources.fromBytes(buffer));
        ctx.getRepository().store(ctx.getToken(), stored, ctx.getSystemChangeContext());
    }
    
    private String filterRefProp(Object prop, UrlMapper mapper) throws Exception {
        String value = prop.toString();
        Optional<String> mapped = mapper.mapUrl(value);
        return mapped.orElse(value);
    }    
    
    private String filterHtmlProp(Object prop, UrlMapper mapper) throws Exception {
        HtmlPageParser parser = new HtmlPageParser();
        HtmlFragment fragment;
        fragment = parser.parseFragment(prop.toString());
        UrlMapFilter filter = new UrlMapFilter(mapper);
        fragment.filter(filter);
        return fragment.getStringRepresentation();
    }
    
    
    private static class UrlMapper {
        private Map<String, String> vrtxIdMap;
        private Map<String, String> relocMap;
        
        public UrlMapper(Resource resource, ExecutionContext ctx) throws Exception {
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
                    vrtxIdMap.put(link.get("link").toString(), link.get("vrtxid").toString());
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
