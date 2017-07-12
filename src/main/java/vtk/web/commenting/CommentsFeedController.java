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
package vtk.web.commenting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Comment;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class CommentsFeedController implements Controller {
    private String viewName;
    private Service viewService;

    @Required
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    @Required
    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }

    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        Principal principal = requestContext.getPrincipal();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        Resource resource = repository.retrieve(token, uri, true);

        List<Comment> comments = repository.getComments(token, resource, true, 50);

        Map<String, Resource> resourceMap = new HashMap<>();
        Map<String, URL> urlMap = new HashMap<>();
        resourceMap.put(resource.getURI().toString(), resource);
        
        URL url = viewService.urlConstructor(URL.create(request))
                .withURI(resource.getURI())
                .constructURL();
        
        urlMap.put(resource.getURI().toString(), url);
        for (Comment comment : comments) {
            Resource r = repository.retrieve(token, comment.getURI(), true);
            resourceMap.put(r.getURI().toString(), r);
            URL resourceURL = viewService.urlConstructor(URL.create(request))
                    .withURI(r.getURI())
                    .constructURL();
            urlMap.put(r.getURI().toString(), resourceURL);
        }
        URL selfURL = URL.create(request);

        Map<String, Object> model = new HashMap<>();
        model.put("resource", resource);
        model.put("principal", principal);
        model.put("comments", comments);
        model.put("resourceMap", resourceMap);
        model.put("urlMap", urlMap);
        model.put("selfURL", selfURL);

        return new ModelAndView(this.viewName, model);
    }

}
