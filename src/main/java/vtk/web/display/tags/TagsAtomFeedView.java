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
package vtk.web.display.tags;

import java.util.Date;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.abdera.model.Feed;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.RequestContext;
import vtk.web.display.feed.ListingFeedView;
import vtk.web.service.Service;
import vtk.web.service.URL;
import vtk.web.tags.TagsHelper;

public class TagsAtomFeedView extends ListingFeedView {

    private PropertyTypeDefinition overridePublishDatePropDef;
    protected TagsHelper tagsHelper;

    @Override
    protected void addFeedLinks(HttpServletRequest request, Resource feedScope, Feed feed) {
        RequestContext requestContext = RequestContext.getRequestContext();
        URL feedAlternateURL = viewService.constructURL(feedScope);
        String tag = requestContext.getRequestURL().getParameter("tag");
        if (tag != null) feedAlternateURL.addParameter("tag", tag);
        feed.addLink(feedAlternateURL.toString(), "alternate");
        feed.addLink(requestContext.getRequestURL().toString(), "self");
    }


    @Override
    protected Resource getFeedScope(HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();

        return tagsHelper.getScopedResource(token, request);
    }

    @Override
    protected String getFeedTitle(HttpServletRequest request, Map<String, ?> model,
            Resource feedScope) {
        RequestContext requestContext = RequestContext.getRequestContext();
        Service service = requestContext.getService();
        return service.getLocalizedName(feedScope, request);
    }

    @Override
    protected boolean showFeedIntroduction(Resource feedScope) {
        return false;
    }

    @Override
    protected String getFeedPrefix() {
        return "tags:";
    }

    @Override
    protected Date getLastModified(PropertySet collection) {
        return new Date();
    }

    @Override
    protected Property getPublishDate(PropertySet resource) {
        Property overridePublishDateProp = resource.getProperty(overridePublishDatePropDef);
        if (overridePublishDateProp != null) {
            return overridePublishDateProp;
        }
        return getDefaultPublishDate(resource);
    }

    @Required
    public void setTagsHelper(TagsHelper tagsHelper) {
        this.tagsHelper = tagsHelper;
    }

    public void setOverridePublishDatePropDef(PropertyTypeDefinition overridePublishDatePropDef) {
        this.overridePublishDatePropDef = overridePublishDatePropDef;
    }

}
