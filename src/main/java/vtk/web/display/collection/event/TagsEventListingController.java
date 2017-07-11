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
package vtk.web.display.collection.event;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Required;
import vtk.repository.Resource;
import vtk.repository.resourcetype.ResourceTypeDefinition;
import vtk.web.RequestContext;
import vtk.web.referencedata.Link;
import vtk.web.tags.TagsHelper;

public class TagsEventListingController extends EventListingController {

    private TagsHelper tagsHelper;

    @Override
    public void runSearch(HttpServletRequest request, Resource collection, Map<String, Object> model, int pageLimit)
            throws Exception {

        // Run the search
        super.runSearch(request, collection, model, pageLimit);

        RequestContext requestContext = RequestContext.getRequestContext(request);
        String token = requestContext.getSecurityToken();
        Resource resource = tagsHelper.getScopedResource(token, request);

        // Parameter tag is required for service invocation
        String tag = request.getParameter(TagsHelper.TAG_PARAMETER);
        if (!StringUtils.isBlank(tag)) {
            model.put(TagsHelper.TAG_PARAMETER, tag);
        }

        // Parameter resource type is required for service invocation
        List<ResourceTypeDefinition> resourceTypeDefs = tagsHelper.getResourceTypes(request);
        String resourceType = resourceTypeDefs.get(0).getName();
        model.put(TagsHelper.RESOURCE_TYPE_MODEL_KEY, resourceType);

        boolean displayScope = tagsHelper.getDisplayScope(request);
        // Scope up link
        Link scopeUpLink = tagsHelper.getScopeUpUrl(request, resource, model, tag, resourceTypeDefs, displayScope,
                false);
        model.put(TagsHelper.SCOPE_UP_MODEL_KEY, scopeUpLink);

        // Resolve Title
        String title = tagsHelper.getTitle(request, resource, tag, false);
        model.put("title", title);

    }

    @Required
    public void setTagsHelper(TagsHelper tagsHelper) {
        this.tagsHelper = tagsHelper;
    }

}
