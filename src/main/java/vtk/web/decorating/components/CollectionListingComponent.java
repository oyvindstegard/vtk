/* Copyright (c) 2011, University of Oslo, Norway
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
package vtk.web.decorating.components;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.AuthorizationException;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceNotFoundException;
import vtk.security.AuthenticationException;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.search.Listing;
import vtk.web.search.SearchComponent;
import vtk.web.servlet.ResourceAwareLocaleResolver;

public class CollectionListingComponent extends ViewRenderingDecoratorComponent {

    private static final int MAX_ITEMS = 10;

    private SearchComponent search;
    private CollectionListingHelper helper;
    private ResourceAwareLocaleResolver localeResolver;

    private final static String PARAMETER_URI = "uri";
    private final static String PARAMETER_URI_DESCRIPTION = "Uri to the folder. This is a required parameter";
    private final static String PARAMETER_MAX_ITEMS = "max-items";
    private final static String PARAMETER_MAX_ITEMS_DESCRIPTION = "Defines how many items from the folder that will be visable in the list. Any defined value must be above 0 else the default value is "
            + MAX_ITEMS;
    private final static String PARAMETER_GO_TO_FOLDER_LINK = "go-to-folder-link";
    private final static String PARAMETER_GO_TO_FOLDER_LINK_DESCRIPTION = "Set to 'true' to show 'Go to folder' link. Default is false";
    private final static String PARAMETER_FOLDER_TITLE = "folder-title";
    private final static String PARAMETER_FOLDER_TITLE_DESCRIPTION = "Set to 'true' to show folder title. Default is false";
    private final static String PARAMETER_COMPACT_VIEW = "compact-view";
    private final static String PARAMETER_COMPACT_VIEW_DESCRIPTION = "Set to 'true' to show compact view. Default is false";
    private final static String PARAMETER_SORTING = "sorting";
    private final static String PARAMETER_SORTING_DESCRIPTION = "Sort the listing according to one or more properties, e.g. 'modifiedBy:asc,lastModified:desc'";

    @Override
    protected void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {

        Map<String, Object> conf = new HashMap<>();

        String uri = request.getStringParameter(PARAMETER_URI);
        if (uri == null) {
            throw new DecoratorComponentException("Component parameter 'uri' is required");
        }

        Path resourcePath = getResourcePath(uri, RequestContext.getRequestContext(request.getServletRequest()));
        if (resourcePath == null) {
            throw new DecoratorComponentException("Provided uri is not a valid folder reference: " + uri);
        }
        RequestContext requestContext = RequestContext.getRequestContext(
                request.getServletRequest());
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();

        HttpServletRequest servletRequest = request.getServletRequest();
        final String sorting = request.getStringParameter(PARAMETER_SORTING);
        if (sorting != null) {
            // Add 'sorting' parameter to request, so that SearchComponent can see it:
            servletRequest = addSortingParameter(servletRequest, sorting);
        }
        
        Resource res;
        try {
            res = repository.retrieve(token, resourcePath, false);
        } catch (AuthenticationException e) {
            res = null;
        } catch (AuthorizationException e) {
            res = null;
        } catch (ResourceNotFoundException e) {
            throw new DecoratorComponentException(uri + " does not exist");
        } catch (Exception e) {
            throw new DecoratorComponentException(e.getMessage());
        }

        conf.put("auth", res != null);
        if (res == null) {
            model.put("conf", conf);
            return;
        }

        if (!res.isCollection()) {
            throw new DecoratorComponentException(uri + " is not a folder");
        }

        int maxItems = MAX_ITEMS;
        try {
            int suppliedMaxItems = Integer.parseInt(request.getStringParameter(PARAMETER_MAX_ITEMS));
            maxItems = suppliedMaxItems <= 0 ? maxItems : suppliedMaxItems;
        } catch (Exception e) {
            // Ignore, defaults to predefined limit
        }

        // XXX ???
        boolean goToFolderLink, folderTitle = parameterHasValue(PARAMETER_FOLDER_TITLE, "true", request);
        if (goToFolderLink = parameterHasValue(PARAMETER_GO_TO_FOLDER_LINK, "true", request)) {
            model.put("goToFolderLink", uri);
            model.put("folderTitle", res.getTitle());
        } else if (folderTitle) {
            model.put("folderTitle", res.getTitle());
        }

        conf.put("goToFolderLink", goToFolderLink);
        conf.put("folderTitle", folderTitle);

        conf.put("compactView", parameterHasValue(PARAMETER_COMPACT_VIEW, "true", request));
        
        Listing listing = search.execute(servletRequest, res, 1, maxItems, 0);

        Locale preferredLocale = localeResolver.resolveResourceLocale(res);
        Map<String, Principal> principalDocuments = helper.getPrincipalDocumentLinks(
                new HashSet<>(listing.getPropertySets()), preferredLocale, null);
        model.put("principalDocuments", principalDocuments);

        model.put("entries", listing.getEntries());
        model.put("conf", conf);
    }

    private Path getResourcePath(String uri, RequestContext requestContext) {
        // Be lenient on trailing slash
        uri = uri.endsWith("/") && !uri.equals("/") ? uri.substring(0, uri.lastIndexOf("/")) : uri;

        try {
            if (!uri.startsWith("/")) {
                return requestContext.getCurrentCollection().extend(uri);
            }
            return Path.fromString(uri);
        } catch (IllegalArgumentException iae) {
            return null;
        }
    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(PARAMETER_URI, PARAMETER_URI_DESCRIPTION);
        map.put(PARAMETER_MAX_ITEMS, PARAMETER_MAX_ITEMS_DESCRIPTION);
        map.put(PARAMETER_GO_TO_FOLDER_LINK, PARAMETER_GO_TO_FOLDER_LINK_DESCRIPTION);
        map.put(PARAMETER_FOLDER_TITLE, PARAMETER_FOLDER_TITLE_DESCRIPTION);
        map.put(PARAMETER_COMPACT_VIEW, PARAMETER_COMPACT_VIEW_DESCRIPTION);
        map.put(PARAMETER_SORTING, PARAMETER_SORTING_DESCRIPTION);
        return map;
    }

    private HttpServletRequest addSortingParameter(HttpServletRequest servletRequest, final String sortInput) {
        
        Map<String, String[]> m = new HashMap<>(servletRequest.getParameterMap());
        m.put(Listing.SORTING_PARAM, sortInput.split(","));
        
        final Map<String, String[]> paramsMap = Collections.unmodifiableMap(new HashMap<>(m));
        
        return new HttpServletRequestWrapper(servletRequest) {
            @Override
            public String getParameter(String name) {
                String[] vals = paramsMap.get(name);
                if (vals == null || vals.length == 0) return null;
                return vals[0];
            }
            @Override
            public Map<String, String[]> getParameterMap() {
                return paramsMap;
            }

            @Override
            public Enumeration<String> getParameterNames() {
                return Collections.enumeration(paramsMap.keySet());
                
            }
            @Override
            public String[] getParameterValues(String name) {
                return paramsMap.get(name);
            }
        };
    }
    
    private boolean parameterHasValue(String param, String includeParamValue, DecoratorRequest request) {
        String itemDescriptionString = request.getStringParameter(param);
        if (itemDescriptionString != null && includeParamValue.equalsIgnoreCase(itemDescriptionString)) {
            return true;
        }
        return false;
    }

    @Required
    public void setSearch(SearchComponent search) {
        this.search = search;
    }

    @Required
    public void setHelper(CollectionListingHelper helper) {
        this.helper = helper;
    }

    @Required
    public void setLocaleResolver(ResourceAwareLocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @Override
    protected String getDescriptionInternal() {
        return "Inserts a folder item list component on the page";
    }
}
