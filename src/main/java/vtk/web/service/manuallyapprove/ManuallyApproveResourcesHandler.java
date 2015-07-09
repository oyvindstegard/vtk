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
package vtk.web.service.manuallyapprove;

import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.MultiHostSearcher;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.security.SecurityContext;
import vtk.util.text.Json;
import vtk.util.text.JsonStreamer;
import vtk.web.RequestContext;
import vtk.web.service.URL;

/**
 * Retrieves list of resources to manually approve from. Used in editing of
 * collections that have manually-approve-from property.
 * 
 * Caches result for 10min at a time, with resource path, last modified time and
 * list of resources to approve from as cache key. Caching is used to avoid
 * unnecessary search for resources on subsequent reloads of editing tab without
 * having done any changes to property manually-approve-from (e.g. edited
 * anything else other than manually approval).
 * 
 */
public class ManuallyApproveResourcesHandler implements Controller {

    private static final String LOCATIONS_PARAM = "locations";
    private static final String AGGREGATE_PARAM = "aggregate";
    private static final String APPROVED_ONLY_PARAM = "approved-only";
    
    private static final String URI = "uri"; 
    private static final String TITLE = "title";
    private static final String SOURCE = "source";
    private static final String PUBLISHED = "published";
    private static final String APPROVED = "approved";

    private PropertyTypeDefinition manuallyApproveFromPropDef;
    private PropertyTypeDefinition manuallyApprovedResourcesPropDef;
    private PropertyTypeDefinition aggregationPropDef;
    
    private ManuallyApproveResourcesSearcher searcher;
    private MultiHostSearcher multiHostSearcher;
    private Ehcache cache;

    @Override
    @SuppressWarnings("unchecked")
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        Repository repository = RequestContext.getRequestContext().getRepository();
        Path currentCollectionPath = RequestContext.getRequestContext().getCurrentCollection();
        String token = SecurityContext.getSecurityContext().getToken();
        Resource currentCollection = repository.retrieve(token, currentCollectionPath, false);
        Property manuallyApproveFromProp = currentCollection.getProperty(manuallyApproveFromPropDef);
        Property manuallyApprovedResourcesProp = currentCollection.getProperty(manuallyApprovedResourcesPropDef);
        Property aggregationProp = currentCollection.getProperty(aggregationPropDef);
        String[] manuallyApproveFromParam = request.getParameterValues(LOCATIONS_PARAM);
        String[] aggregateParam = request.getParameterValues(AGGREGATE_PARAM);

        // Nothing to work with, need at least one of these
        if (manuallyApproveFromProp == null && manuallyApprovedResourcesProp == null
                && manuallyApproveFromParam == null) {
            return null;
        }

        Set<String> locations = new HashSet<String>();
        // Parameter "locations" overrides property, because user might change
        // content and update service before storing resource
        if (manuallyApproveFromParam != null) {
            for (String location : manuallyApproveFromParam) {
                if (isValid(location, currentCollectionPath, repository, token)) {
                    locations.add(location);
                }
            }
        } else if (manuallyApproveFromProp != null) {
            Value[] manuallyApproveFromValues = manuallyApproveFromProp.getValues();
            for (Value manuallyApproveFromValue : manuallyApproveFromValues) {
                String location = manuallyApproveFromValue.getStringValue();
                if (isValid(location, currentCollectionPath, repository, token)) {
                    locations.add(location);
                }
            }
        }

        // Make sure current collection path is not among set of locations to
        // manually approve from
        locations.remove(currentCollectionPath.toString());

        // Aggregation must also be considered. Any location to aggregate from
        // will be discarded from location set to manually approve from, because
        // aggregation overrides manual approval. Parameter "aggregate"
        // overrides property, again because user might change content and
        // update service before storing resource
        if (aggregateParam != null) {
            for (String agg : aggregateParam) {
                locations.remove(agg);
            }
        } else if (aggregationProp != null) {
            Value[] aggregationValues = aggregationProp.getValues();
            for (Value aggregationValue : aggregationValues) {
                locations.remove(aggregationValue.toString());
            }
        }

        Set<String> alreadyApproved = new HashSet<String>();
        if (manuallyApprovedResourcesProp != null) {
            Value[] manuallyApprovedResourcesValues = manuallyApprovedResourcesProp.getValues();
            for (Value manuallyApprovedResourcesValue : manuallyApprovedResourcesValues) {
                alreadyApproved.add(manuallyApprovedResourcesValue.toString());
            }
        }

        // Nothing to do here...
        if (locations.isEmpty() && alreadyApproved.isEmpty()) {
            return null;
        }

        String cacheKey = getCacheKey(currentCollectionPath, currentCollection.getLastModified().toString(),
                manuallyApproveFromParam);
        Element cached = cache.get(cacheKey);
        Object cachedObj = cached != null ? cached.getObjectValue() : null;

        List<ManuallyApproveResource> result;
        if (cachedObj != null) {
            result = (List<ManuallyApproveResource>) cachedObj;
        } else {
            result = searcher.getManuallyApproveResources(currentCollection, locations, alreadyApproved);
            cache.put(new Element(cacheKey, result));
        }

        if (result == null || result.isEmpty()) {
            return null;
        }

        boolean approvedOnly = request.getParameter(APPROVED_ONLY_PARAM) != null;
        Json.ListContainer arr = new Json.ListContainer();

        for (ManuallyApproveResource m : result) {
            boolean approved = m.isApproved();
            if (approvedOnly && !approved) {
                continue;
            }
            Json.MapContainer obj = new Json.MapContainer();
            obj.put(TITLE, m.getTitle());
            obj.put(URI, m.getUrl().toString());
            obj.put(SOURCE, m.getSource());
            obj.put(PUBLISHED, m.getPublishDateAsString());
            obj.put(APPROVED, approved);
            arr.add(obj);
        }
        
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/plain;charset=utf-8");
        PrintWriter writer = response.getWriter();
        try {
            JsonStreamer streamer = new JsonStreamer(writer, 1);
            streamer.array(arr);
        } finally {
            writer.flush();
            writer.close();
        }

        return null;
    }

    private String getCacheKey(Path currentCollectionPath, String lastModifiedString, String[] manuallyApproveFromParam) {
        StringBuilder cacheKey = new StringBuilder(currentCollectionPath.toString());
        cacheKey.append("-").append(lastModifiedString);
        if (manuallyApproveFromParam.length != 0) {
            cacheKey.append("-");
            for (String manuallyApproveFrom : manuallyApproveFromParam) {
                cacheKey.append(manuallyApproveFrom);
            }
        }
        return cacheKey.toString();
    }

    private boolean isValid(String location, Path currentCollectionPath, Repository repository, String token) {

        try {
            URL url = URL.parse(location);
            if (multiHostSearcher.isMultiHostSearchEnabled()) {
                PropertySet ps = multiHostSearcher.retrieve(token, url);
                if (ps == null) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            // Not a url, but might be a local path. Ignore and continue.
        }

        try {

            Path locationPath = Path.fromStringWithTrailingSlash(location);

            // Do not allow manual approval from self
            if (currentCollectionPath.equals(locationPath)) {
                return false;
            }

            // Make sure resource exists
            if (!repository.exists(token, locationPath)) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Required
    public void setSearcher(ManuallyApproveResourcesSearcher searcher) {
        this.searcher = searcher;
    }

    @Required
    public void setManuallyApproveFromPropDef(PropertyTypeDefinition manuallyApproveFromPropDef) {
        this.manuallyApproveFromPropDef = manuallyApproveFromPropDef;
    }

    @Required
    public void setManuallyApprovedResourcesPropDef(PropertyTypeDefinition manuallyApprovedResourcesPropDef) {
        this.manuallyApprovedResourcesPropDef = manuallyApprovedResourcesPropDef;
    }

    @Required
    public void setAggregationPropDef(PropertyTypeDefinition aggregationPropDef) {
        this.aggregationPropDef = aggregationPropDef;
    }

    @Required
    public void setMultiHostSearcher(MultiHostSearcher multiHostSearcher) {
        this.multiHostSearcher = multiHostSearcher;
    }

    @Required
    public void setCache(Ehcache cache) {
        this.cache = cache;
    }

}
