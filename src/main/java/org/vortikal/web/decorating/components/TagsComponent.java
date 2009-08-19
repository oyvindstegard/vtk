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
package org.vortikal.web.decorating.components;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.Path;
import org.vortikal.repository.reporting.DataReportException;
import org.vortikal.security.SecurityContext;
import org.vortikal.web.RequestContext;
import org.vortikal.web.decorating.DecoratorRequest;
import org.vortikal.web.decorating.DecoratorResponse;
import org.vortikal.web.tags.RepositoryTagElementsDataProvider;
import org.vortikal.web.tags.TagElement;

/**
 * Decorator component for tags component.
 * 
 */
public class TagsComponent extends ViewRenderingDecoratorComponent implements InitializingBean {

    private static final String DESCRIPTION = "Renders a tags as an alphabetically sorted list.";
    
    private static final String PARAMETER_SCOPE = "scope";
    private static final String PARAMETER_SCOPE_DESC = "Set the URI scope for the tag cloud. Relative URIs are allowed. "
            + "Only tags existing in the folder tree given by the URI will be "
            + "taken into consideration when generating the tag cloud. "
            + "The default value is the current directory and below.";
    
    private static final String PARAMETER_RESULT_SETS = "result-sets";
    private static final int PARAMETER_RESULT_DEFAULT_VALUE = 1;
    private static final String PARAMETER_PARAMETER_RESULT_DESC  = "The number of result sets to split the result into. The default value is: " + PARAMETER_RESULT_DEFAULT_VALUE;

    private static final String PARAMETER_TAG_LIMIT = "limit";
    private static final int PARAMETER_TAG_LIMIT_DEFAULT_VALUE = Integer.MAX_VALUE;
    private static final String PARAMETER_TAG_LIMIT_DESC = "Set limit on how many tags to include. Setting this to a low value will "
            + "show only the most popular tags. Default is: " + PARAMETER_TAG_LIMIT_DEFAULT_VALUE;

 
    private static final String PARAMETER_SHOW_OCCURENCE = "show-occurence";
    private static final boolean PARAMETER_SHOW_OCCURENCE_VALUE = false;
    private static final String PARAMETER_SHOW_OCCURENCE_DESC = "Display a number indicating the number of documents associated with the tag"
            + "Default value is: " + PARAMETER_SHOW_OCCURENCE_VALUE;

    private static final String PARAMETER_SERVICE_URL = "service-url";
    private static final String PARAMETER_SERVICE_URL_DESC = "Deprecated: NO LONGER USED. Kept to avoid breaking existing component references.";

    private RepositoryTagElementsDataProvider tagElementsProvider;
    
    protected String getDescriptionInternal() {
        return DESCRIPTION;
    }

    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new LinkedHashMap<String, String>();

        map.put(PARAMETER_SCOPE, PARAMETER_SCOPE_DESC);
        map.put(PARAMETER_TAG_LIMIT, PARAMETER_TAG_LIMIT_DESC);
        map.put(PARAMETER_RESULT_SETS, PARAMETER_PARAMETER_RESULT_DESC);
        map.put(PARAMETER_SHOW_OCCURENCE, PARAMETER_SHOW_OCCURENCE_DESC);
        map.put(PARAMETER_SERVICE_URL, PARAMETER_SERVICE_URL_DESC);

        return map;
    }

    protected void processModel(Map<Object, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {

        super.processModel(model, request, response);

        Path scopeUri = RequestContext.getRequestContext().getCurrentCollection();
        String token = SecurityContext.getSecurityContext().getToken();

        if (request.getStringParameter(PARAMETER_SCOPE) != null) {
            scopeUri = buildScopePath(request.getStringParameter(PARAMETER_SCOPE));
        }

        int limit = PARAMETER_TAG_LIMIT_DEFAULT_VALUE;
        int resultSet = PARAMETER_RESULT_DEFAULT_VALUE;
        boolean showOccurence = PARAMETER_SHOW_OCCURENCE_VALUE;
        
        try {
        	
        	if(request.getStringParameter(PARAMETER_RESULT_SETS) != null)
        		resultSet = Integer.parseInt(request.getStringParameter(PARAMETER_RESULT_SETS));
        	
        	if(request.getStringParameter(PARAMETER_SHOW_OCCURENCE) !=null && 
        			request.getStringParameter(PARAMETER_SHOW_OCCURENCE).equals("true"))
        		showOccurence = true;

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

       
        // Legacy exception handling, should be refactored.
        try {
            List<TagElement> tagElements = 
                tagElementsProvider.getTagElements(scopeUri, token, 1, 1, limit, 1); //TODO: not so neat hack??

            // Populate model
            int numberOfTagsInEachColumn;
            int remainder;
            if(limit < PARAMETER_TAG_LIMIT_DEFAULT_VALUE && limit < tagElements.size()){
            	numberOfTagsInEachColumn = limit/resultSet;
            	remainder = limit % resultSet;
            }else{
            	numberOfTagsInEachColumn =  tagElements.size()/resultSet;
            	remainder = tagElements.size() % resultSet;
            }
            
            // If we have an reminder then we need to round up
            if(remainder != 0)
            	numberOfTagsInEachColumn++;

            // Ensure that we get the number of columns that we ask for in
            // the parameter 'result-sets', given that we have enough elements. 
            if((remainder != 0 && resultSet < numberOfTagsInEachColumn && resultSet > remainder) || (remainder == 0))
            	model.put("completeColumn",resultSet);
            else 
            	model.put("completeColumn",remainder-1);
            
            model.put("showOccurence", showOccurence);
            model.put("numberOfTagsInEachColumn",numberOfTagsInEachColumn);
            model.put("tagElements", tagElements);
        } catch (DataReportException d) {
            throw new DecoratorComponentException("There was a problem with the data report query: " + d.getMessage());
        } catch (IllegalArgumentException e) {
            throw new DecoratorComponentException("Illegal value for parameter '" + PARAMETER_SCOPE
                    + "', must be a valid URI.");
        }

    }

    Path buildScopePath(String href) {
        if (href.startsWith("/")) {
            return Path.fromString(href);
        }
        Path requestURI = RequestContext.getRequestContext().getResourceURI();
        return requestURI.expand(href);
    }

    @Required
    public void setTagElementsProvider(
            RepositoryTagElementsDataProvider tagElementsProvider) {
        this.tagElementsProvider = tagElementsProvider;
    }
}
