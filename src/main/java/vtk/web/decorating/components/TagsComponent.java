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
package vtk.web.decorating.components;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.ResourceTypeDefinition;
import vtk.repository.search.QueryException;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.service.URL;
import vtk.web.tags.RepositoryTagElementsDataProvider;
import vtk.web.tags.TagElement;
import vtk.web.tags.TagsHelper;

/**
 * Decorator component for tags component.
 * 
 */
public class TagsComponent extends ViewRenderingDecoratorComponent implements InitializingBean {

    private static final String DESCRIPTION = "Renders tags as an alphabetically sorted list.";

    private static final String PARAMETER_SCOPE = TagsHelper.SCOPE_PARAMETER;
    private static final String PARAMETER_SCOPE_DESC = "Set the URI scope for the tag cloud. Relative URIs are allowed. "
            + "Only tags existing in the folder tree given by the URI will be "
            + "taken into consideration when generating the tag cloud. "
            + "The default value is the current directory and below.";

    private static final String PARAMETER_RESULT_SETS = "result-sets";
    private static final int PARAMETER_RESULT_DEFAULT_VALUE = 1;
    private static final String PARAMETER_PARAMETER_RESULT_DESC = "The number of result sets to split the result into. The default value is: "
            + PARAMETER_RESULT_DEFAULT_VALUE;

    private static final String PARAMETER_TAG_LIMIT = "limit";
    private static final int PARAMETER_TAG_LIMIT_DEFAULT_VALUE = Integer.MAX_VALUE;
    private static final String PARAMETER_TAG_LIMIT_DESC = "Set limit on how many tags to include. Setting this to a low value will "
            + "show only the most popular tags. Default is: " + PARAMETER_TAG_LIMIT_DEFAULT_VALUE;

    private static final String PARAMETER_SHOW_OCCURENCE = "show-occurence";
    private static final boolean PARAMETER_SHOW_OCCURENCE_VALUE = false;
    private static final String PARAMETER_SHOW_OCCURENCE_DESC = "Display a number indicating the number of documents associated with the tag"
            + "Default value is: " + PARAMETER_SHOW_OCCURENCE_VALUE;

    private static final String PARAMETER_RESOURCE_TYPE = TagsHelper.RESOURCE_TYPE_PARAMETER;
    private static final String PARAMETER_RESOURCE_TYPE_DESC = "Comma seperated list of resource types to search for tags in.";

    private static final String PARAMETER_SORT_SELECTED_TAG_BY = "sort-selected-by";
    private static final String PARAMETER_SORT_SELECTED_TAG_BY_DESC = "Comma seperated list of attributes to sort a selected tag by "
            + "from the result. Each attribute in the format [prefix]:[name]:[sortdirection]. Prefix is optional. "
            + "For example: resource:surname:asc";

    private static final String PARAMETER_DISPLAY_SCOPE = TagsHelper.DISPLAY_SCOPE_PARAMETER;
    private static final String PARAMETER_DISPLAY_SCOPE_DESC = "Whether or not to display the current scope in the page title when linking "
            + "to a single tag. Default is 'false'";

    private static final String PARAMETER_OVERRIDE_RESOURCE_TYPE_TITLE = TagsHelper.OVERRIDE_RESOURCE_TYPE_TITLE_PARAMETER;
    private static final String PARAMETER_OVERRIDE_RESOURCE_TYPE_TITLE_DESC = "User provided resource type title to use in page title when "
            + "linking to a single tag.";

    private static final String PARAMETER_WHITELIST = "whitelist";
    private static final String PARAMETER_WHITELIST_DESC = "Comma separated whitelist of tags. Whitelist is optional Example: whitelist=[tag1,tag2]";
    
    private static final String PARAMETER_URL_PATTERN = "url-pattern";
    private static final String PARAMETER_URL_PATTERN_DESC = 
            "Pattern for generating a tag URL. Occurrences of '%t' are replaced with the tag value. "
            + "Occurrences of %p are replaced with the current URI";

    private RepositoryTagElementsDataProvider tagElementsProvider;
    private ResourceTypeTree resourceTypeTree;

    @Override
    protected String getDescriptionInternal() {
        return DESCRIPTION;
    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new LinkedHashMap<>();

        map.put(PARAMETER_SCOPE, PARAMETER_SCOPE_DESC);
        map.put(PARAMETER_TAG_LIMIT, PARAMETER_TAG_LIMIT_DESC);
        map.put(PARAMETER_RESULT_SETS, PARAMETER_PARAMETER_RESULT_DESC);
        map.put(PARAMETER_SHOW_OCCURENCE, PARAMETER_SHOW_OCCURENCE_DESC);
        map.put(PARAMETER_RESOURCE_TYPE, PARAMETER_RESOURCE_TYPE_DESC);
        map.put(PARAMETER_SORT_SELECTED_TAG_BY, PARAMETER_SORT_SELECTED_TAG_BY_DESC);
        map.put(PARAMETER_DISPLAY_SCOPE, PARAMETER_DISPLAY_SCOPE_DESC);
        map.put(PARAMETER_OVERRIDE_RESOURCE_TYPE_TITLE, PARAMETER_OVERRIDE_RESOURCE_TYPE_TITLE_DESC);
        map.put(PARAMETER_WHITELIST, PARAMETER_WHITELIST_DESC);
        map.put(PARAMETER_URL_PATTERN, PARAMETER_URL_PATTERN_DESC);
        return map;
    }

    @Override
    protected void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {

        super.processModel(model, request, response);
        RequestContext requestContext = RequestContext.getRequestContext(request.getServletRequest());
        Path scopeUri = requestContext.getCurrentCollection();
        String token = requestContext.isViewUnauthenticated() ? null : requestContext.getSecurityToken(); // VTK-2460

        if (request.getStringParameter(PARAMETER_SCOPE) != null) {
            scopeUri = buildScopePath(request);
        }

        int limit = PARAMETER_TAG_LIMIT_DEFAULT_VALUE;
        int resultSet = PARAMETER_RESULT_DEFAULT_VALUE;
        boolean showOccurence = PARAMETER_SHOW_OCCURENCE_VALUE;

        try {

            if (request.getStringParameter(PARAMETER_RESULT_SETS) != null) {
                resultSet = Integer.parseInt(request.getStringParameter(PARAMETER_RESULT_SETS));
            }

            if (request.getStringParameter(PARAMETER_SHOW_OCCURENCE) != null
                    && request.getStringParameter(PARAMETER_SHOW_OCCURENCE).equals("true")) {
                showOccurence = true;
            }

            if (request.getStringParameter(PARAMETER_TAG_LIMIT) != null) {
                limit = Integer.parseInt(request.getStringParameter(PARAMETER_TAG_LIMIT));
            }

            if (limit <= 0) {
                throw new DecoratorComponentException("Parameter '" + PARAMETER_TAG_LIMIT
                        + "' cannot be zero or negative");
            }

        } catch (NumberFormatException nfe) {
            throw new DecoratorComponentException("An invalid numeric parameter value was supplied: "
                    + nfe.getMessage());
        }

        Object resourceTypeParam = request.getRawParameter(PARAMETER_RESOURCE_TYPE);
        List<ResourceTypeDefinition> resourceTypeDefs = new ArrayList<>();
        if (resourceTypeParam != null) {
            String[] resourceTypes = resourceTypeParam.toString().split(",");
            for (String resourceType : resourceTypes) {
                ResourceTypeDefinition resourceTypeDef = getResourceTypeDef(resourceType.trim());
                if (resourceTypeDef != null) {
                    resourceTypeDefs.add(resourceTypeDef);
                }
            }
        }

        List<String> urlSortingParmas = new ArrayList<>();
        Object sortingParam = request.getRawParameter(PARAMETER_SORT_SELECTED_TAG_BY);
        if (sortingParam != null) {
            String[] sortingParams = sortingParam.toString().split(",");
            for (String param : sortingParams) {
                urlSortingParmas.add(param);
            }
        }

        Object displayScopeParam = request.getRawParameter(PARAMETER_DISPLAY_SCOPE);
        boolean displayScope = displayScopeParam != null
                && Boolean.TRUE.toString().equals(displayScopeParam.toString());

        String overrideResTypeTitle = null;
        Object overrideResTypeTitleParam = request.getRawParameter(PARAMETER_OVERRIDE_RESOURCE_TYPE_TITLE);
        if (overrideResTypeTitleParam != null) {
            overrideResTypeTitle = overrideResTypeTitleParam.toString();
        }

        Set<String> whiteList = null;
        String whiteListParam = request.getStringParameter(PARAMETER_WHITELIST);
        if (whiteListParam != null) {
            whiteListParam = whiteListParam.toLowerCase();
            whiteList = new HashSet<>(Arrays.asList(whiteListParam.split(",")));
        }

        // Legacy exception handling, should be refactored.
        try {
            List<TagElement> tagElements = tagElementsProvider
                    .getTagElements(request.getServletRequest(), scopeUri, token, 1, 1, limit, 1,
                            resourceTypeDefs, urlSortingParmas, overrideResTypeTitle, 
                            displayScope, whiteList);
            
            String urlPattern = request.getStringParameter(PARAMETER_URL_PATTERN);
            if (urlPattern != null) {
                URL baseURL = requestContext.getRequestURL()
                    .setPath(requestContext.getCurrentCollection())
                    .clearParameters();
                tagElements = tagElements.stream().map(elem -> {
                    String newURL = urlPattern
                            .replaceAll("%t", elem.getText())
                            .replaceAll("%p", baseURL.getPath().toString());
                    URL url = baseURL.relativeURL(newURL);
                    return new TagElement(elem.getMagnitude(), url, elem.getText(), elem.getOccurences());
                }).collect(Collectors.toList());
            }

            // Populate model
            int numberOfTagsInEachColumn;
            int remainder;
            if (limit < PARAMETER_TAG_LIMIT_DEFAULT_VALUE && limit < tagElements.size()) {
                numberOfTagsInEachColumn = limit / resultSet;
                remainder = limit % resultSet;
            }
            else {
                numberOfTagsInEachColumn = tagElements.size() / resultSet;
                remainder = tagElements.size() % resultSet;
            }

            // If we have an reminder then we need to round up
            if (remainder != 0)
                numberOfTagsInEachColumn++;

            // Ensure that we get the number of columns that we ask for in
            // the parameter 'result-sets', given that we have enough elements.
            if ((remainder != 0 && resultSet < numberOfTagsInEachColumn && resultSet > remainder) || (remainder == 0))
                model.put("completeColumn", resultSet);
            else
                model.put("completeColumn", remainder - 1);

            model.put("showOccurence", showOccurence);
            model.put("numberOfTagsInEachColumn", numberOfTagsInEachColumn);
            model.put("numberOfColumns", resultSet);
            model.put("tagElements", tagElements);
            if (resourceTypeDefs.size() > 0) {
                model.put("resourceTypes", resourceTypeDefs);
            }

        }
        catch (QueryException qe) {
            throw new DecoratorComponentException("There was a problem with the data report query: " + qe.getMessage());
        }
        catch (IllegalArgumentException e) {
            throw new DecoratorComponentException("Illegal value for parameter '" + PARAMETER_SCOPE
                    + "', must be a valid URI.");
        }
    }

    private ResourceTypeDefinition getResourceTypeDef(String resourceType) {
        try {
            return this.resourceTypeTree.getResourceTypeDefinitionByName(resourceType);
        } catch (IllegalArgumentException e) {
            // Invalid resource type param, ignore it
        }
        return null;
    }

    Path buildScopePath(DecoratorRequest request) {
        String href = request.getStringParameter(PARAMETER_SCOPE);
        if (href.startsWith("/")) {
            return Path.fromString(href);
        }
        Path requestURI = RequestContext
                .getRequestContext(request.getServletRequest()).getResourceURI();
        return requestURI.expand(href);
    }

    @Required
    public void setTagElementsProvider(RepositoryTagElementsDataProvider tagElementsProvider) {
        this.tagElementsProvider = tagElementsProvider;
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }
}
