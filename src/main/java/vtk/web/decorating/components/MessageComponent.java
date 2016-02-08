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
package vtk.web.decorating.components;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Required;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.search.Listing;
import vtk.web.search.SearchComponent;
import vtk.web.servlet.ResourceAwareLocaleResolver;

public class MessageComponent extends ViewRenderingDecoratorComponent {

    private static final String PARAMETER_URI = "uri";
    private static final String PARAMETER_URI_DESC = "URI of the message folder to include messages from.";

    private static final String PARAMETER_MAX_NUMBER_OF_MESSAGES = "max-number-of-messages";
    private static final String PARAMETER_MAX_NUMBER_OF_MESSAGES_DESC = "Maximum number of messages to display.";

    private static final String PARAMETER_TITLE = "title";
    private static final String PARAMETER_TITLE_DESC = "Title to set on messages list.";

    private static final String PARAMETER_COMPACT_VIEW = "compact-view";
    private final static String PARAMETER_COMPACT_VIEW_DESCRIPTION = "Set to 'true' to show compact view. Default is false";

    private static final String PARAMETER_INCLUDE_IF_EMPTY = "include-if-empty";
    private static final String PARAMETER_INCLUDE_IF_EMPTY_DESC = "Set to 'false' if you don't want to display empty message listings. Default is 'true'.";

    private SearchComponent searchComponent;
    private CollectionListingHelper helper;
    private PropertyTypeDefinition pageLimitPropDef;
    private ResourceAwareLocaleResolver localeResolver;

    @Override
    protected void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {

        String pathUriParameter = request.getStringParameter(PARAMETER_URI);
        if (pathUriParameter == null || "".equals(pathUriParameter.trim())) {
            return;
        }

        Path path;
        try {
            path = Path.fromStringWithTrailingSlash(pathUriParameter);
        } catch (IllegalArgumentException iae) {
            // Invalid path parameter
            return;
        }

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.isViewUnauthenticated() ? null : requestContext.getSecurityToken(); // VTK-2460
        Resource requestedMessageFolder = repository.retrieve(token, path, true);
        Principal principal = requestContext.getPrincipal();

        if (!requestedMessageFolder.isCollection()) {
            return;
        }

        int pageLimit = 20; // Limit to max 20 messages as default
        int requestedMaxLimit = 0;
        String maxLimitString = request.getStringParameter(PARAMETER_MAX_NUMBER_OF_MESSAGES);
        if (StringUtils.isNotBlank(maxLimitString)) {
            try {
                requestedMaxLimit = Integer.parseInt(maxLimitString);
            } catch (NumberFormatException nfe) {
            }
        }
        if (requestedMaxLimit > 0) {
            pageLimit = requestedMaxLimit;
        } else {
            Property pageLimitProp = requestedMessageFolder.getProperty(this.pageLimitPropDef);
            if (pageLimitProp != null) {
                pageLimit = pageLimitProp.getIntValue();
            }
        }

        String compactView = request.getStringParameter(PARAMETER_COMPACT_VIEW);
        boolean isCompactView = StringUtils.equalsIgnoreCase(compactView, "true");
        model.put("compactView", isCompactView);

        String title = request.getStringParameter(PARAMETER_TITLE);
        if (StringUtils.isNotBlank(title)) {
            model.put("title", title);
        }

        Listing result = searchComponent.execute(request.getServletRequest(), requestedMessageFolder, 1, pageLimit, 0);
        Locale preferredLocale = localeResolver.resolveResourceLocale(requestedMessageFolder);
        Map<String, Principal> principalDocuments = helper.getPrincipalDocumentLinks(new HashSet<>(
                result.getPropertySets()), preferredLocale, null);

        String includeIfEmpty = request.getStringParameter(PARAMETER_INCLUDE_IF_EMPTY);
        model.put("includeIfEmpty", includeIfEmpty == null || !includeIfEmpty.equalsIgnoreCase("false"));

        model.put("principalDocuments", principalDocuments);
        model.put("messageListingResult", result);
        model.put("editMessageFolder", helper.checkResourceForEditLink(repository, requestedMessageFolder, principal));
        model.put("messageFolder", requestedMessageFolder);
        model.put("locale", preferredLocale);
        model.put("requestURL", requestContext.getRequestURL());

        if (pageLimit < result.getTotalHits()) {
            model.put("moreMessages", true);
        }

    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(PARAMETER_URI, PARAMETER_URI_DESC);
        map.put(PARAMETER_MAX_NUMBER_OF_MESSAGES, PARAMETER_MAX_NUMBER_OF_MESSAGES_DESC);
        map.put(PARAMETER_TITLE, PARAMETER_TITLE_DESC);
        map.put(PARAMETER_COMPACT_VIEW, PARAMETER_COMPACT_VIEW_DESCRIPTION);
        map.put(PARAMETER_INCLUDE_IF_EMPTY, PARAMETER_INCLUDE_IF_EMPTY_DESC);
        return map;
    }

    @Required
    public void setSearchComponent(SearchComponent searchComponent) {
        this.searchComponent = searchComponent;
    }

    @Required
    public void setHelper(CollectionListingHelper helper) {
        this.helper = helper;
    }

    @Required
    public void setPageLimitPropDef(PropertyTypeDefinition pageLimitPropDef) {
        this.pageLimitPropDef = pageLimitPropDef;
    }

    @Required
    public void setLocaleResolver(ResourceAwareLocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

}
