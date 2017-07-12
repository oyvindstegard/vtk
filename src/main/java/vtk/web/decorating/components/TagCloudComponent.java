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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.search.QueryException;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.tags.RepositoryTagElementsDataProvider;
import vtk.web.tags.TagElement;
import vtk.web.tags.TagsHelper;

/**
 * Decorator component for tag cloud.
 * 
 */
public class TagCloudComponent extends ViewRenderingDecoratorComponent implements InitializingBean {

    private static final String DESCRIPTION = "Renders a tag cloud as an alphabetically sorted list. Classes are put on "
            + "the elements for representing the magnitude of the individual tags in the cloud. "
            + "List elements will be assigned classes 'tag-magnitude-N', where "
            + "N represents the magnitude as a bounded positive integer number.";

    private static final String PARAMETER_TAG_LIMIT = "limit";
    private static final int PARAMETER_TAG_LIMIT_DEFAULT_VALUE = 20;
    private static final String PARAMETER_TAG_LIMIT_DESC = "Set limit on how many tags to include. Setting this to a low value will "
            + "show only the most popular tags. Default is: " + PARAMETER_TAG_LIMIT_DEFAULT_VALUE;

    private static final String PARAMETER_TAG_OCCURENCE_MIN = "tag-occurence-min";
    private static final int PARAMETER_TAG_OCCURENCE_MIN_DEFAULT_VALUE = 1;
    private static final String PARAMETER_TAG_OCCURENCE_MIN_DESC = "Limit tag cloud to include only tags with an occurence count higher than "
            + "or equal to this minimal value. This can be used to weed out tags "
            + "with for instance only one or two occurences within the scope. "
            + "The default value is 1. Increase this as needed, if your tag cloud "
            + "contains many undesirable small tags with only few occurences.";

    private static final String PARAMETER_SCOPE = TagsHelper.SCOPE_PARAMETER;
    private static final String PARAMETER_SCOPE_DESC = "Set the URI scope for the tag cloud. Relative URIs are allowed. "
            + "Only tags existing in the folder tree given by the URI will be "
            + "taken into consideration when generating the tag cloud. "
            + "The default value is the current directory and below.";

    private static final String PARAMETER_MAGNITUDE_MAX = "magnitude-max";
    private static final int PARAMETER_MAGNITUDE_MAX_DEFAULT_VALUE = 5;
    private static final String PARAMETER_MAGNITUDE_MAX_DESC = "Sets the maximum magnitude for a tags in the cloud (an integer number bigger than 1). "
            + "The tags with the highest occurence will be assigned the maximum magnitude value. "
            + "You can use this to adjust the granularity of the magnitude-scale. "
            + "Note that this number must be bigger than or equal to the minimum value (see next parameter). "
            + "Default value is: " + PARAMETER_MAGNITUDE_MAX_DEFAULT_VALUE;

    private static final String PARAMETER_MAGNITUDE_MIN = "magnitude-min";
    private static final int PARAMETER_MAGNITUDE_MIN_DEFAULT_VALUE = 1;
    private static final String PARAMETER_MAGNITUDE_MIN_DESC = "Sets the minimum magnitude for a tag in the tag cloud (an integer number bigger than 1). "
            + "The tags with the lowest occurence within the result set will be assigned the minimum magnitude value. "
            + "The result set can be restricted using the parameters '"
            + PARAMETER_SCOPE
            + "' and '"
            + PARAMETER_TAG_LIMIT + "'. " + "Default value is: " + PARAMETER_MAGNITUDE_MIN_DEFAULT_VALUE;

    private static final String PARAMETER_SERVICE_URL = "service-url";
    private static final String PARAMETER_SERVICE_URL_DESC = "Deprecated: NO LONGER USED. Kept to avoid breaking existing component references.";

    private RepositoryTagElementsDataProvider tagElementsProvider;
    
    @Override
    protected String getDescriptionInternal() {
        return DESCRIPTION;
    }


    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new LinkedHashMap<>();

        map.put(PARAMETER_SCOPE, PARAMETER_SCOPE_DESC);
        map.put(PARAMETER_TAG_LIMIT, PARAMETER_TAG_LIMIT_DESC);
        map.put(PARAMETER_TAG_OCCURENCE_MIN, PARAMETER_TAG_OCCURENCE_MIN_DESC);
        map.put(PARAMETER_MAGNITUDE_MAX, PARAMETER_MAGNITUDE_MAX_DESC);
        map.put(PARAMETER_MAGNITUDE_MIN, PARAMETER_MAGNITUDE_MIN_DESC);
        map.put(PARAMETER_SERVICE_URL, PARAMETER_SERVICE_URL_DESC);

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

        int magnitudeMin = PARAMETER_MAGNITUDE_MIN_DEFAULT_VALUE;
        int magnitudeMax = PARAMETER_MAGNITUDE_MAX_DEFAULT_VALUE;
        int limit = PARAMETER_TAG_LIMIT_DEFAULT_VALUE;
        int tagOccurenceMin = PARAMETER_TAG_OCCURENCE_MIN_DEFAULT_VALUE;

        try {
            if (request.getStringParameter(PARAMETER_MAGNITUDE_MIN) != null) {
                magnitudeMin = Integer.parseInt(request.getStringParameter(PARAMETER_MAGNITUDE_MIN));
            }

            if (request.getStringParameter(PARAMETER_MAGNITUDE_MAX) != null) {
                magnitudeMax = Integer.parseInt(request.getStringParameter(PARAMETER_MAGNITUDE_MAX));
            }

            if (request.getStringParameter(PARAMETER_TAG_LIMIT) != null) {
                limit = Integer.parseInt(request.getStringParameter(PARAMETER_TAG_LIMIT));
            }

            if (request.getStringParameter(PARAMETER_TAG_OCCURENCE_MIN) != null) {
                tagOccurenceMin = Integer.parseInt(request.getStringParameter(PARAMETER_TAG_OCCURENCE_MIN));
            }

            if (tagOccurenceMin < 1) {
                throw new DecoratorComponentException("Parameter '" + PARAMETER_TAG_OCCURENCE_MIN
                        + "' must be a number larger than or equal to 1.");
            }

            if (limit <= 0) {
                throw new DecoratorComponentException("Parameter '" + PARAMETER_TAG_LIMIT
                        + "' cannot be zero or negative");
            }

            if (magnitudeMin < 1 || magnitudeMax < magnitudeMin) {
                throw new DecoratorComponentException("Value of parameter '" + PARAMETER_MAGNITUDE_MAX
                        + "' must be greater or equal to value of parameter '" + PARAMETER_MAGNITUDE_MIN
                        + "' and both parameters must be greater than zero.");
            }

        } catch (NumberFormatException nfe) {
            throw new DecoratorComponentException("An invalid numeric parameter value was supplied: "
                    + nfe.getMessage());
        }


        // Legacy exception handling, should be refactored.
        try {
            List<TagElement> tagElements = 
                tagElementsProvider.getTagElements(request.getServletRequest(), 
                        scopeUri, token, magnitudeMin,
                        magnitudeMax, limit, tagOccurenceMin);

            // Populate model
            model.put("tagElements", tagElements);
        } catch (QueryException qe) {
            throw new DecoratorComponentException("There was a problem with the data report query: " + qe.getMessage());
        } catch (IllegalArgumentException e) {
            throw new DecoratorComponentException("Illegal value for parameter '" + PARAMETER_SCOPE
                    + "', must be a valid URI.");
        }
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
    public void setTagElementsProvider(
            RepositoryTagElementsDataProvider tagElementsProvider) {
        this.tagElementsProvider = tagElementsProvider;
    }


}
