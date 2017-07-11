/* Copyright (c) 2013, University of Oslo, Norway
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
package vtk.web.display.feed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Resource;
import vtk.repository.resourcetype.HtmlValueFormatter;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.RequestContext;
import vtk.web.service.Service;

/**
 * 
 * Creates an ITunes extended RSS 2.0 feed, adhering to:
 * http://cyber.law.harvard.edu/rss/rss.html
 * http://www.apple.com/itunes/podcasts/specs.html
 * 
 * 
 * Subclasses provide results for and add entries to feed, as well as override
 * title and certain other properties (date, author ++).
 * 
 */
public abstract class RSSFeedGenerator implements Controller {

    private String viewName;
    private String feedLogoPath;

    protected Service viewService;
    protected PropertyTypeDefinition titlePropDef;
    protected PropertyTypeDefinition publishDatePropDef;
    protected PropertyTypeDefinition lastModifiedPropDef;
    protected PropertyTypeDefinition introductionPropDef;

    // Must be overriden by subclasses to provide content for feed entries and
    // add these to feed
    protected abstract List<Map<String, Object>> getFeedEntries(
            Resource feedScope, HttpServletRequest request) throws Exception;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext(request);
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        Resource feedScope = requestContext.getRepository().retrieve(token, uri, true);

        Map<String, Object> feedContent = new HashMap<>();

        // Title, link and description are required by spec
        feedContent.put("title", getTitle(feedScope, requestContext));
        feedContent.put("link", request.getRequestURL().toString());
        Property introductionProp = feedScope.getProperty(introductionPropDef);
        String description = introductionProp != null ? introductionProp.getFormattedValue(
                HtmlValueFormatter.FLATTENED_FORMAT, null) : "";
        feedContent.put("description", description);

        // Optional elements
        feedContent.put("atomLink", requestContext.getRequestURL().toString());

        if (!StringUtils.isBlank(feedLogoPath)) {
            feedContent.put("feedLogoPath", feedLogoPath);
        }

        feedContent.put("feedItems", getFeedEntries(feedScope, request));

        Map<String, Object> model = new HashMap<>();
        model.put("feedContent", feedContent);

        return new ModelAndView(viewName, model);

    }

    protected String getTitle(Resource feedScope, RequestContext requestContext) {
        String feedTitle = feedScope.getTitle();
        if (Path.ROOT.equals(feedScope.getURI())) {
            feedTitle = requestContext.getRepository().getId();
        }
        return feedTitle;
    }

    @Required
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public void setFeedLogoPath(String feedLogoPath) {
        this.feedLogoPath = feedLogoPath;
    }

    @Required
    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }

    @Required
    public void setTitlePropDef(PropertyTypeDefinition titlePropDef) {
        this.titlePropDef = titlePropDef;
    }

    @Required
    public void setPublishDatePropDef(PropertyTypeDefinition publishDatePropDef) {
        this.publishDatePropDef = publishDatePropDef;
    }

    @Required
    public void setLastModifiedPropDef(PropertyTypeDefinition lastModifiedPropDef) {
        this.lastModifiedPropDef = lastModifiedPropDef;
    }

    @Required
    public void setIntroductionPropDef(PropertyTypeDefinition introductionPropDef) {
        this.introductionPropDef = introductionPropDef;
    }

}
