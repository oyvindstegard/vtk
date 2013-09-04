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
package org.vortikal.web.decorating.components;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.AuthorizationException;
import org.vortikal.repository.Path;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.ResourceNotFoundException;
import org.vortikal.security.AuthenticationException;
import org.vortikal.security.Principal;
import org.vortikal.security.SecurityContext;
import org.vortikal.web.RequestContext;
import org.vortikal.web.decorating.DecoratorRequest;
import org.vortikal.web.decorating.DecoratorResponse;
import org.vortikal.web.search.Listing;
import org.vortikal.web.search.SearchComponent;
import org.vortikal.web.servlet.ResourceAwareLocaleResolver;

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

    protected void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {

        Map<String, Object> conf = new HashMap<String, Object>();

        String uri = request.getStringParameter(PARAMETER_URI);
        if (uri == null) {
            throw new DecoratorComponentException("Component parameter 'uri' is required");
        }

        Path resourcePath = getResourcePath(uri);
        if (resourcePath == null) {
            throw new DecoratorComponentException("Provided uri is not a valid folder reference: " + uri);
        }

        Repository repository = RequestContext.getRequestContext().getRepository();
        String token = SecurityContext.getSecurityContext().getToken();

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

        Listing listing = search.execute(request.getServletRequest(), res, 1, maxItems, 0);

        Locale preferredLocale = localeResolver.resolveResourceLocale(res);
        Map<String, Principal> principalDocuments = helper.getPrincipalDocumentLinks(
                new HashSet<PropertySet>(listing.getPropertySets()), preferredLocale, null);
        model.put("principalDocuments", principalDocuments);

        model.put("entries", listing.getEntries());
        model.put("conf", conf);
    }

    private Path getResourcePath(String uri) {
        // Be lenient on trailing slash
        uri = uri.endsWith("/") && !uri.equals("/") ? uri.substring(0, uri.lastIndexOf("/")) : uri;

        RequestContext rc = RequestContext.getRequestContext();

        try {
            if (!uri.startsWith("/")) {
                return rc.getCurrentCollection().extend(uri);
            }
            return Path.fromString(uri);
        } catch (IllegalArgumentException iae) {
            return null;
        }
    }

    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(PARAMETER_URI, PARAMETER_URI_DESCRIPTION);
        map.put(PARAMETER_MAX_ITEMS, PARAMETER_MAX_ITEMS_DESCRIPTION);
        map.put(PARAMETER_GO_TO_FOLDER_LINK, PARAMETER_GO_TO_FOLDER_LINK_DESCRIPTION);
        map.put(PARAMETER_FOLDER_TITLE, PARAMETER_FOLDER_TITLE_DESCRIPTION);
        map.put(PARAMETER_COMPACT_VIEW, PARAMETER_COMPACT_VIEW_DESCRIPTION);
        return map;
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

    protected String getDescriptionInternal() {
        return "Inserts a folder item list component on the page";
    }
}
