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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.repository.ContentInputSources;
import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.Resource;
import vtk.repository.Revision.Type;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.ConfigurablePropertySelect;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.PropertyTermQuery;
import vtk.repository.search.query.TermOperator;
import vtk.resourcemanagement.StructuredResource;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.StructuredResourceManager;
import vtk.util.repository.LinkReplacer;
import vtk.util.text.Json.ListContainer;
import vtk.util.text.Json.MapContainer;
import vtk.util.text.JsonStreamer;
import vtk.web.service.CanonicalUrlConstructor;
import vtk.web.service.URL;

public class LinkRepairJob extends AbstractResourceJob {
    private final Logger logger = 
            LoggerFactory.getLogger(LinkRepairJob.class);
    private final Logger changeLogger = 
        LoggerFactory.getLogger(LinkRepairJob.class.getName() + ".Changes");
    
    private final StructuredResourceManager resourceManager;
    private final PropertyTypeDefinition hrefsPropDef;
    private final CanonicalUrlConstructor urlConstructor;

    public LinkRepairJob(StructuredResourceManager resourceManager, 
            PropertyTypeDefinition hrefsPropDef, 
            CanonicalUrlConstructor urlConstructor) {
        this.resourceManager = resourceManager;
        this.hrefsPropDef = hrefsPropDef;
        this.urlConstructor = urlConstructor;
    }
    
    @Override
    protected void executeForResource(Resource resource, final ExecutionContext ctx)
            throws Exception {
        if (resource.getLock() != null) {
            logger.debug("Resource is locked, skipping: " + resource);
            return;
        }
        URL base = urlConstructor.canonicalUrl(resource).setImmutable();
        
        logger.debug("Correcting links for resource " + resource + "; " + ctx);
        final UrlMapper mapper = new UrlMapper(resource, base, ctx);
        
        LinkReplacer.Context replaceContext = new LinkReplacer.Context() {
            
            @Override
            public Resource resource() {
                return resource;
            }
            
            @Override
            public Optional<String> mapURL(String input) {
                return mapper.mapUrl(input);
            }

            @Override
            public Optional<StructuredResourceDescription> resourceDescription() {
                return Optional.ofNullable(resourceManager.get(resource.getResourceType()));
            }

            @Override
            public void storeProperties() {
                try {
                    ctx.getRepository().store(ctx.getToken(), null, resource,
                            ctx.getSystemChangeContext());
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public Optional<InputStream> content() {
                try {
                    return Optional.of(ctx.getRepository()
                            .getInputStream(ctx.getToken(), resource.getURI(), false));
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void writeContent(InputStream content) {
                try {
                    ctx.getRepository().storeContent(
                        ctx.getToken(), null, resource.getURI(),
                        ContentInputSources.fromStream(content));
                    
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void writeRevision(StructuredResource res) {
                // XXX: If the last revision was written by "our" principal, 
                // don't create a new revision here..
                try {
                    final byte[] buffer = JsonStreamer.toJson(
                            res.toJSON(), 3, false).getBytes("utf-8");

                    ctx.getRepository().createRevision(ctx.getToken(), null,
                            resource.getURI(), Type.REGULAR);
                    
                    ctx.getRepository()
                        .storeContent(ctx.getToken(), null, resource.getURI(),
                            ContentInputSources.fromBytes(buffer));                    
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            
            @Override
            public void log(String from, String to, String label) {
                changeLogger.info("Link repair: " + resource.getURI() 
                    + ": " + label + ": " + from + " -> " + to);
            }
        };

        // Update 'hrefs' JSON property with relocated 'uri' fields
        
        Property hrefsProp = resource.getProperty(hrefsPropDef);
        if (hrefsProp != null) {
            MapContainer jsonValue = hrefsProp.getJSONValue();
            if (jsonValue.containsKey("links")) {
                ListContainer links = jsonValue.arrayValue("links");
                List<Object> linksResult = new ArrayList<>();
                for (int i = 0; i < links.size(); i++) {
                    MapContainer hrefsObj = links.objectValue(i);
                    if (hrefsObj.containsKey("url")) {
                        String url = hrefsObj.stringValue("url");
                        Optional<String> mapped = mapper.mapUrl(url);
                        hrefsObj.put("url", mapped.orElse(url));
                    }
                    linksResult.add(hrefsObj);
                }
                jsonValue.put("links", linksResult);
            }
            hrefsProp.setJSONValue(jsonValue);
        }
        

        // Remove field 'relocatedLinks' from the 'link-check' property if it exists:
        Property linkCheck = resource.getProperty(Namespace.DEFAULT_NAMESPACE, "link-check");
        if (linkCheck != null) {
            MapContainer jsonValue = linkCheck.getJSONValue();
            if (jsonValue != null && jsonValue.containsKey("relocatedLinks")) {
                jsonValue.remove("relocatedLinks");
            }
            linkCheck.setJSONValue(jsonValue);
        }
        
        // Process properties and content. This will also trigger evaluation
        // of the 'hrefs' property, which will use the updated JSON value as a
        // starting point:
        LinkReplacer.process(replaceContext);
        
        // Update system job status:
        ctx.getRepository().store(ctx.getToken(), null, 
                ctx.getRepository().retrieve(ctx.getToken(), resource.getURI(), false),
                ctx.getSystemChangeContext());
    }
    
    
    private static class UrlMapper {
        private final URL base;
        private Map<String, URL> relocMap;
        
        public UrlMapper(Resource resource, URL base, ExecutionContext ctx) throws Exception {
            this.base = base;
            this.relocMap = mapLinks(resource, ctx);
        }
        
        public Optional<String> mapUrl(String input) {
            URL relocated = relocMap.get(input);
            if (relocated == null) {
                return Optional.empty();
            }
            return Optional.of(relocated.getPathRepresentation());
        }
        
        private Map<String, URL> mapLinks(Resource resource, ExecutionContext ctx) {
            Map<String, URL> resultMap = new HashMap<>();
            
            Map<String, String> vrtxIdMap = new HashMap<>();
            MapContainer jsonValue = resource
                    .getProperty(Namespace.DEFAULT_NAMESPACE, "link-check")
                    .getJSONValue();
            if (jsonValue == null || !jsonValue.containsKey("relocatedLinks")) {
                return resultMap;
            }
            
            jsonValue.arrayValue("relocatedLinks").forEach(urlObj-> {
                Map<?,?> link = (Map<?,?>) urlObj;
                if (link.containsKey("link") && link.containsKey("vrtxid")) {
                    String url = link.get("link").toString();
                    String vrtxid = link.get("vrtxid").toString();
                    vrtxIdMap.put(vrtxid, url);
                }
            });
            
            if (vrtxIdMap.isEmpty()) return resultMap;
            
            PropertyTypeDefinition idPropDef = ctx.getRepository()
                    .getTypeInfo("resource")
                    .getPropertyTypeDefinition(Namespace.DEFAULT_NAMESPACE, 
                            PropertyType.EXTERNAL_ID_PROP_NAME);

            OrQuery query = new OrQuery();
            vrtxIdMap.entrySet().forEach(entry -> {
                query.add(new PropertyTermQuery(idPropDef, entry.getKey(), TermOperator.EQ));
            });
            
            Search search = new Search();
            search.setQuery(query);
            search.setLimit(vrtxIdMap.size());
            search.clearAllFilterFlags();
            search.setSorting(null);
            search.setPropertySelect(new ConfigurablePropertySelect(idPropDef));
            
            ResultSet hits = ctx.getRepository().search(ctx.getToken(), search);
            hits.forEach(r -> {
                String vrtxid = r.getProperty(idPropDef).getStringValue();
                String prev = vrtxIdMap.get(vrtxid);
                if (prev != null) {
                    try {
                        URL prevURL = base.relativeURL(prev);
                        URL mappedURL = new URL(prevURL).setPath(r.getURI());
                        resultMap.put(prev, mappedURL);
                        if (!prev.startsWith("/")) {
                            resultMap.put(prevURL.getPathRepresentation(), mappedURL);
                            resultMap.put(prevURL.protocolRelativeURL(), mappedURL);
                        }
                    }
                    catch (Throwable t) {
                        // Nothing we can do with the (mangled) URL
                    }
                }
             });
            return resultMap;
        }
    }
}
