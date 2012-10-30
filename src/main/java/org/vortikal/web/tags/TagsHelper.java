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
package org.vortikal.web.tags;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.support.RequestContext;
import org.vortikal.repository.Path;
import org.vortikal.repository.Resource;
import org.vortikal.repository.ResourceNotFoundException;
import org.vortikal.repository.ResourceTypeTree;
import org.vortikal.repository.resourcetype.ResourceTypeDefinition;
import org.vortikal.web.referencedata.Link;
import org.vortikal.web.search.Listing;
import org.vortikal.web.service.Service;
import org.vortikal.web.service.URL;

public final class TagsHelper {

    public static final String TAG_PARAMETER = "tag";
    public static final String SCOPE_PARAMETER = "scope";
    public static final String RESOURCE_TYPE_PARAMETER = "resource-type";
    public static final String RESOURCE_TYPE_MODEL_KEY = "resourceType";
    public static final String SCOPE_UP_MODEL_KEY = "scopeUp";
    public static final String DISPLAY_SCOPE_PARAMETER = "display-scope";
    public static final String OVERRIDE_RESOURCE_TYPE_TITLE_PARAMETER = "override-resource-type-title";

    private ResourceTypeTree resourceTypeTree;
    private boolean servesWebRoot;

    public Resource getScopedResource(String token, HttpServletRequest request) throws Exception {
        Path requestedScope = this.getScopePath(request);
        Resource scopedResource = null;
        try {
            scopedResource = org.vortikal.web.RequestContext.getRequestContext().getRepository()
                    .retrieve(token, requestedScope, true);
        } catch (ResourceNotFoundException e) {
            throw new IllegalArgumentException("Scope resource doesn't exist: " + requestedScope);
        }

        if (!scopedResource.isCollection()) {
            throw new IllegalArgumentException("Scope resource isn't a collection");
        }
        return scopedResource;
    }

    private final Path getScopePath(HttpServletRequest request) {

        String scopeFromRequest = request.getParameter(SCOPE_PARAMETER);

        if (StringUtils.isBlank(scopeFromRequest) || ".".equals(scopeFromRequest)) {
            return org.vortikal.web.RequestContext.getRequestContext().getCurrentCollection();
        } else if (scopeFromRequest.startsWith("/")) {
            return Path.fromString(scopeFromRequest);
        }

        throw new IllegalArgumentException("Scope parameter must be empty, '.' or a valid path");
    }

    public String getTitle(HttpServletRequest request, Resource resource, String tag, boolean scopeUp) {

        String repositoryID = org.vortikal.web.RequestContext.getRequestContext().getRepository().getId();
        String scopeTitle = (scopeUp && !resource.getURI().isRoot()) ? repositoryID : resource.getTitle();
        String overrideResourceTypeTitle = request.getParameter(OVERRIDE_RESOURCE_TYPE_TITLE_PARAMETER);
        String[] resourceParams = request.getParameterValues(RESOURCE_TYPE_PARAMETER);
        boolean displayScope = this.getDisplayScope(request);

        StringBuilder keyBuilder = new StringBuilder("tags.title");
        if (StringUtils.isBlank(tag)) {
            keyBuilder.append(".noTag");
        }
        if (scopeUp || displayScope) {
            keyBuilder.append(".scoped");
        }
        String titleKey = keyBuilder.toString();

        RequestContext rc = new RequestContext(request);
        if (!StringUtils.isBlank(overrideResourceTypeTitle)) {
            titleKey = titleKey.concat(".overridenTitle");
        } else if (resourceParams != null && resourceParams.length == 1) {
            String tmpKey = titleKey.concat(".").concat(resourceParams[0]);
            try {
                rc.getMessage(tmpKey);
                titleKey = tmpKey;
            } catch (Exception e) {
                // key doesn't exist, ignore it
            }
        }

        Object[] localizationParams = this.getLocalizationParams(tag, scopeUp, displayScope, scopeTitle,
                overrideResourceTypeTitle);

        return rc.getMessage(titleKey, localizationParams);

    }

    private Object[] getLocalizationParams(String tag, boolean scopeUp, boolean displayScope, String scopeTitle,
            String overrideResourceTypeTitle) {

        List<Object> params = new ArrayList<Object>();
        if (!StringUtils.isBlank(overrideResourceTypeTitle)) {
            params.add(overrideResourceTypeTitle);
        }
        if (scopeUp || displayScope) {
            params.add(scopeTitle);
        }
        if (!StringUtils.isBlank(tag)) {
            params.add(tag);
        }

        return params.toArray();
    }

    public final boolean getDisplayScope(HttpServletRequest request) {
        boolean displayScope = false;
        String displayScopeInTitle = request.getParameter(TagsHelper.DISPLAY_SCOPE_PARAMETER);
        if (Boolean.TRUE.toString().equals(displayScopeInTitle)) {
            displayScope = true;
        }
        return displayScope;
    }

    public List<ResourceTypeDefinition> getResourceTypes(HttpServletRequest request) {
        String[] resourcePrams = request.getParameterValues(TagsHelper.RESOURCE_TYPE_PARAMETER);
        if (resourcePrams != null) {
            List<ResourceTypeDefinition> resourceTypes = new ArrayList<ResourceTypeDefinition>();
            for (String resourceType : resourcePrams) {
                try {
                    ResourceTypeDefinition resourceTypeDef = this.resourceTypeTree
                            .getResourceTypeDefinitionByName(resourceType);
                    resourceTypes.add(resourceTypeDef);
                } catch (IllegalArgumentException iae) {
                    // invalid resource type name, ignore it
                }
            }
            return resourceTypes;
        }
        return null;
    }

    public Link getScopeUpUrl(HttpServletRequest request, Resource resource, Map<String, Object> model, String tag,
            List<ResourceTypeDefinition> resourceTypes, boolean displayScope, String overrideResourceTypeTitle, boolean sort) {

        if (this.servesWebRoot && !resource.getURI().equals(Path.ROOT)) {
            Link scopeUpLink = new Link();
            Service service = org.vortikal.web.RequestContext.getRequestContext().getService();
            URL url = service.constructURL(resource.getURI());
            url.setPath(Path.ROOT);
            List<String> sortFieldParams = null;
            if (!StringUtils.isBlank(tag) && sort) {
                Object searchResult = model.get("listing");
                if (searchResult != null && searchResult instanceof Listing) {
                    Listing listing = (Listing) searchResult;
                    sortFieldParams = listing.getSortFieldParams();
                }
            }
            this.processUrl(url, tag, resourceTypes, sortFieldParams, displayScope, overrideResourceTypeTitle);
            String scopeUpTitle = this.getTitle(request, resource, tag, true);
            scopeUpLink.setUrl(url);
            scopeUpLink.setTitle(scopeUpTitle);
            return scopeUpLink;
        }

        return null;
    }

    public void processUrl(URL url, String tag, List<ResourceTypeDefinition> resourceTypes,
            List<String> sortFieldParams, boolean displayScope, String overrideResourceTypeTitle) {

        if (!StringUtils.isBlank(tag)) {
            url.removeParameter(TagsHelper.TAG_PARAMETER);
            url.addParameter(TagsHelper.TAG_PARAMETER, tag);
        }
        if (sortFieldParams != null && sortFieldParams.size() > 0) {
            for (String param : sortFieldParams) {
                url.addParameter(Listing.SORTING_PARAM, param);
            }
        }
        if (resourceTypes != null && !url.getParameterNames().contains(TagsHelper.RESOURCE_TYPE_PARAMETER)) {
            for (ResourceTypeDefinition resourceTypeDefinition : resourceTypes) {
                url.addParameter(TagsHelper.RESOURCE_TYPE_PARAMETER, resourceTypeDefinition.getName());
            }
        }
        if (displayScope) {
            url.addParameter(TagsHelper.DISPLAY_SCOPE_PARAMETER, Boolean.TRUE.toString());
        }
        if (!StringUtils.isBlank(overrideResourceTypeTitle)) {
            url.addParameter(TagsHelper.OVERRIDE_RESOURCE_TYPE_TITLE_PARAMETER, overrideResourceTypeTitle);
        }

    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    public void setServesWebRoot(boolean servesWebRoot) {
        this.servesWebRoot = servesWebRoot;
    }

}
