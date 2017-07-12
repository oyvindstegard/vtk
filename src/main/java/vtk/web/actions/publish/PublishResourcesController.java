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
package vtk.web.actions.publish;

import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.RequestContext;
import vtk.web.actions.ActionsHelper;

public class PublishResourcesController implements Controller {

    private String viewName;
    private PropertyTypeDefinition publishDatePropDef; 
    private PropertyTypeDefinition unpublishedCollectionPropDef;
    
    private static final String ACTION_PARAM = "action";
    private static final String PUBLISH_PARAM = "publish-resources";
    private static final String UNPUBLISH_PARAM = "unpublish-resources";
    
    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();

        // Map of files that for some reason failed on publish. Separated by a
        // key (String) that specifies type of failure and identifies list of
        // paths to resources that failed.
        Map<String, List<Path>> failures = new HashMap<String, List<Path>>();
                
        String action = request.getParameter(ACTION_PARAM);

        if (action != null && !action.isEmpty()) {
            boolean isPublishAction = PUBLISH_PARAM.equals(action);
            boolean isUnpublishAction = UNPUBLISH_PARAM.equals(action);

            Date publishedDate = null;
            if (isPublishAction) {
                publishedDate = Calendar.getInstance().getTime(); // Publish all resources at same instance of time
            }

            @SuppressWarnings("rawtypes")
            Enumeration e = request.getParameterNames();
            while (e.hasMoreElements()) {
                String name = (String) e.nextElement();
                try {
                    if (isPublishAction) {
                        ActionsHelper.publishResource(publishDatePropDef,unpublishedCollectionPropDef, publishedDate, repository, token,
                                Path.fromString(name), failures);
                    } else if (isUnpublishAction) {
                        ActionsHelper.unpublishResource(publishDatePropDef,unpublishedCollectionPropDef, repository, token, Path.fromString(name),
                                failures);
                    }
                } catch (IllegalArgumentException iae) { // Not a path, ignore it
                    continue;
                }
            }
        }

        return new ModelAndView(this.viewName);
    }

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public String getViewName() {
        return viewName;
    }
    
    @Required
    public void setPublishDatePropDef(PropertyTypeDefinition publishDatePropDef) {
        this.publishDatePropDef = publishDatePropDef;
    }

    @Required   
    public void setUnpublishedCollectionPropDef(PropertyTypeDefinition unpublishedCollectionPropDef) {
        this.unpublishedCollectionPropDef = unpublishedCollectionPropDef;
    }

}
