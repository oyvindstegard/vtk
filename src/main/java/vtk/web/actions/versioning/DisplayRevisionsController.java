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
package vtk.web.actions.versioning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.Repository;
import vtk.repository.RepositoryAction;
import vtk.repository.Resource;
import vtk.repository.Revision;
import vtk.security.Principal;
import vtk.security.Principal.Type;
import vtk.security.PrincipalFactory;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class DisplayRevisionsController implements Controller {

    private Service viewService = null;
    private Service viewDiffService = null;
    private Service deleteService = null;
    private Service restoreService = null;
    private String viewName = null;
    private PrincipalFactory principalFactory;
    
    @Override
    public ModelAndView handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        Map<String, Object> model = new HashMap<>();
        
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Principal principal = requestContext.getPrincipal();
        Path uri = requestContext.getResourceURI();
        List<Revision> revisions = requestContext.getRepository().getRevisions(token, uri);
        
        Object workingCopy = null;
        Revision latest = null;
        List<Object> allRevisions = new ArrayList<>();
        List<Object> regularRevisions = new ArrayList<>();
        
        Resource resource = repository.retrieve(token, uri, false);
        URL displayURL = null;
        try {
            displayURL = viewService.urlConstructor(requestContext.getRequestURL())
                    .withResource(resource)
                    .withPrincipal(principal)
                    .constructURL();
        }
        catch (Throwable t) { }
        
        URL diffURL = null;
        try {
            diffURL = viewDiffService.urlConstructor(requestContext.getRequestURL())
                    .withResource(resource)
                    .withPrincipal(principal)
                    .constructURL();
        }
        catch (Throwable t) { }

        URL deleteURL = null;
        try {
            deleteURL = deleteService.urlConstructor(requestContext.getRequestURL())
                    .withResource(resource)
                    .withPrincipal(principal)
                    .constructURL();
        }
        catch (Throwable t) { }
        
        URL restoreURL = null;
        try {
            restoreURL = restoreService.urlConstructor(requestContext.getRequestURL())
                    .withResource(resource)
                    .withPrincipal(principal)
                    .constructURL();
        }
        catch (Throwable t) { }

        Map<String, Object> prevRevisionMap = null;
        Revision prevRevision = null;
        Revision firstRevision = null;
        
        for (Revision revision: revisions) {
            Map<String, Object> rev = new HashMap<>();
            rev.put("id", revision.getID());
            rev.put("name", revision.getName());
            rev.put("timestamp", revision.getTimestamp());
            rev.put("principal", this.principalFactory.getPrincipal(revision.getUid(), Type.USER));
            rev.put("acl", revision.getAcl());
            rev.put("checksum", revision.getChecksum());
            allRevisions.add(rev);

            boolean haveDisplayURL = displayURL != null
                    && (revision.getType() == Revision.Type.REGULAR
                        && repository.authorize(principal, revision.getAcl(), Privilege.READ)
                        || revision.getType() == Revision.Type.WORKING_COPY); 
            
            if (haveDisplayURL) {
                if (prevRevision != null) { 
                    prevRevisionMap.put("diffURL", new URL(diffURL)
                                   .setParameter("revision", revision.getName() + "," + prevRevision.getName()));
                }
                rev.put("displayURL", new URL(displayURL)
                   .setParameter("revision", revision.getName()));
                prevRevisionMap = rev;
                
                if (revision.getType() == Revision.Type.WORKING_COPY) {
                    rev.put("diffURL", new URL(diffURL)
                       .setParameter("revision", "HEAD," + revision.getName()));
                }
                else {
                    prevRevision = revision;
                    if(firstRevision == null) {
                        firstRevision = revision;
                    }
                }
            }

            if (revision.getType() == Revision.Type.WORKING_COPY) {
                workingCopy = rev;
            }
            else {
                if (latest == null) {
                    latest = revision;
                }
                regularRevisions.add(rev);
            }
            
            boolean haveDeleteURL = deleteURL != null;

            if (haveDeleteURL) {
                rev.put("deleteURL", new URL(deleteURL)
                   .setParameter("revision", revision.getName()));
            }
            
            else if (revision.getType() == Revision.Type.WORKING_COPY) {

                if (repository.isAuthorized(resource, RepositoryAction.READ_WRITE_UNPUBLISHED,
                        principal, true)) {
                    try {
                        URL u = deleteService.urlConstructor(requestContext.getRequestURL())
                                .withResource(resource)
                                .withPrincipal(principal)
                                .matchAssertions(false)
                                .constructURL();
                        rev.put("deleteURL", new URL(u)
                        .setParameter("revision", revision.getName()));
                        
                    }
                    catch (Throwable t) { }
                }
            }
            
            boolean haveRestoreURL = restoreURL != null 
                    && (revision.getType() == Revision.Type.REGULAR
                        && repository.authorize(principal, revision.getAcl(), Privilege.READ)
                        || revision.getType() == Revision.Type.WORKING_COPY);

            if (haveRestoreURL) {
                rev.put("restoreURL", new URL(restoreURL)
                   .setParameter("revision", revision.getName()));
            }
        }
       
        model.put("resource", resource);
        model.put("displayURL", displayURL);
        if(firstRevision != null) {
            diffURL.addParameter("revision", firstRevision.getName() + ",HEAD");
            model.put("diffURL", diffURL);
        }
        model.put("workingCopy", workingCopy);
        model.put("allRevisions", allRevisions);
        model.put("regularRevisions", regularRevisions);

        return new ModelAndView(this.viewName, model);
        
    }
    
    @Required
    public void setViewName(String viewName) {
        if (viewName == null || "".equals(viewName.trim())) {
            throw new IllegalArgumentException("viewName must be specified");
        }
        this.viewName = viewName;
    }

    @Required
    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }
    
    @Required
    public void setViewDiffService(Service viewDiffService) {
        this.viewDiffService = viewDiffService;
    }

    @Required
    public void setDeleteService(Service deleteService) {
        this.deleteService = deleteService;
    }

    @Required
    public void setRestoreService(Service restoreService) {
        this.restoreService = restoreService;
    }
    
    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }

}
