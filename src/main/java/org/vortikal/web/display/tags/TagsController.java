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
package org.vortikal.web.display.tags;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.vortikal.repository.Path;
import org.vortikal.repository.Resource;
import org.vortikal.repository.resourcetype.ResourceTypeDefinition;
import org.vortikal.web.RequestContext;
import org.vortikal.web.display.listing.ListingPager;
import org.vortikal.web.display.listing.ListingPagingLink;
import org.vortikal.web.referencedata.Link;
import org.vortikal.web.search.Listing;
import org.vortikal.web.search.SearchComponent;
import org.vortikal.web.service.Service;
import org.vortikal.web.service.URL;
import org.vortikal.web.tags.RepositoryTagElementsDataProvider;
import org.vortikal.web.tags.TagElement;
import org.vortikal.web.tags.TagsHelper;

public class TagsController implements Controller {

    private int defaultPageLimit = 20;
    private String viewName;
    private SearchComponent searchComponent;
    private Map<String, Service> alternativeRepresentations;
    private RepositoryTagElementsDataProvider tagElementsProvider;
    private TagsHelper tagsHelper;

    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        Map<String, Object> model = new HashMap<String, Object>();

        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();

        Resource resource = tagsHelper.getScopedResource(token, request);
        String tag = request.getParameter(TagsHelper.TAG_PARAMETER);
        List<ResourceTypeDefinition> resourceTypes = tagsHelper.getResourceTypes(request);
        boolean displayScope = tagsHelper.getDisplayScope(request);
        String overrideResourceTypeTitle = request.getParameter(TagsHelper.OVERRIDE_RESOURCE_TYPE_TITLE_PARAMETER);

        /* List all known tags for the current collection */
        if (!StringUtils.isBlank(tag)) {
            model.put(TagsHelper.TAG_PARAMETER, tag);
            handleSingleTag(request, tag, resource, model, resourceTypes, displayScope, overrideResourceTypeTitle);
        } else {
            handleAllTags(token, request, resource, model, resourceTypes, overrideResourceTypeTitle, displayScope);
        }

        // Resolve Title
        String title = tagsHelper.getTitle(request, resource, tag, false);
        model.put("title", title);

        // Add scope up url
        Link scopeUpLink = tagsHelper.getScopeUpUrl(request, resource, model, tag, resourceTypes, displayScope, true);
        model.put(TagsHelper.SCOPE_UP_MODEL_KEY, scopeUpLink);

        return new ModelAndView(viewName, model);
    }

    private void handleSingleTag(HttpServletRequest request, String tag, Resource resource, Map<String, Object> model,
            List<ResourceTypeDefinition> resourceTypes, boolean displayScope, String overrideResourceTypeTitle)
            throws Exception {

        // Setting the default page limit
        int pageLimit = defaultPageLimit;

        int page = ListingPager.getPage(request, ListingPager.UPCOMING_PAGE_PARAM);
        int limit = pageLimit;
        int totalHits = 0;

        Listing listing = searchComponent.execute(request, resource, page, limit, 0);
        List<String> sortFieldParams = null;
        if (listing != null) {
            totalHits = listing.getTotalHits();
            sortFieldParams = listing.getSortFieldParams();
        }

        if (resourceTypes != null && resourceTypes.size() == 1) {
            model.put(TagsHelper.RESOURCE_TYPE_MODEL_KEY, resourceTypes.get(0).getName());
        }

        Service service = org.vortikal.web.RequestContext.getRequestContext().getService();
        URL baseURL = service.constructURL(resource.getURI());
        tagsHelper.processUrl(baseURL, tag, resourceTypes, sortFieldParams, displayScope, overrideResourceTypeTitle);

        List<ListingPagingLink> urls = ListingPager.generatePageThroughUrls(totalHits, pageLimit, baseURL, page);

        model.put("listing", listing);
        model.put("page", page);
        model.put("pageThroughUrls", urls);

        if (alternativeRepresentations != null) {
            Set<Object> alt = new HashSet<Object>();
            for (String contentType : alternativeRepresentations.keySet()) {
                try {
                    Service altService = alternativeRepresentations.get(contentType);
                    URL url = altService.constructURL(resource.getURI());
                    tagsHelper.processUrl(url, tag, resourceTypes, sortFieldParams, displayScope,
                            overrideResourceTypeTitle);
                    String title = altService.getName();
                    org.springframework.web.servlet.support.RequestContext rc = new org.springframework.web.servlet.support.RequestContext(
                            request);
                    title = rc.getMessage(altService.getName(), new Object[] { resource.getTitle() },
                            altService.getName());

                    Map<String, Object> m = new HashMap<String, Object>();
                    m.put("title", title);
                    m.put("url", url);
                    m.put("contentType", contentType);

                    alt.add(m);
                    model.put("alternativeRepresentations", alt);
                } catch (Throwable t) {
                }
            }
        }

    }

    private void handleAllTags(String token, HttpServletRequest request, Resource resource, Map<String, Object> model,
            List<ResourceTypeDefinition> resourceTypes, String overrideResourceTypeTitle, boolean displayScope) {

        Path scopeUri = resource.getURI();

        List<TagElement> tagElements = tagElementsProvider.getTagElements(scopeUri, token, 1, 1, Integer.MAX_VALUE, 1,
                resourceTypes, null, overrideResourceTypeTitle, displayScope);

        model.put("tagElements", tagElements);
    }

    public void setDefaultPageLimit(int defaultPageLimit) {
        if (defaultPageLimit <= 0)
            throw new IllegalArgumentException("Argument must be a positive integer");
        this.defaultPageLimit = defaultPageLimit;
    }

    @Required
    public void setSearchComponent(SearchComponent searchComponent) {
        this.searchComponent = searchComponent;
    }

    @Required
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public void setAlternativeRepresentations(Map<String, Service> alternativeRepresentations) {
        this.alternativeRepresentations = alternativeRepresentations;
    }

    public void setTagElementsProvider(RepositoryTagElementsDataProvider tagElementsProvider) {
        this.tagElementsProvider = tagElementsProvider;
    }

    @Required
    public void setTagsHelper(TagsHelper tagsHelper) {
        this.tagsHelper = tagsHelper;
    }

}
