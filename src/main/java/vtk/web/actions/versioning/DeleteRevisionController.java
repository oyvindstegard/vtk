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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Path;
import vtk.repository.Resource;
import vtk.repository.Revision;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class DeleteRevisionController implements Controller {

    private static Logger logger = LoggerFactory.getLogger(DeleteRevisionController.class);
    private Service redirectService;

    @Required
    public void setRedirectService(Service redirectService) {
        this.redirectService = redirectService;
    }

    @Override
    public ModelAndView handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Principal principal = requestContext.getPrincipal();
        Path uri = requestContext.getResourceURI();
        
        Resource resource = requestContext.getRepository().retrieve(token, uri, false);
        URL redirectURL = redirectService.urlConstructor(requestContext.getRequestURL())
                .withResource(resource)
                .withPrincipal(principal)
                .constructURL();
        
        String revisionParam = request.getParameter("revision");
        if (revisionParam == null) {
            response.sendRedirect(redirectURL.toString());
            return null;
        }
        
        Revision revision = null;
        for (Revision rev: requestContext.getRepository().getRevisions(token, uri)) {
            if (rev.getName().equals(revisionParam)) {
                revision = rev;
                break;
            }
        }
        if (revision == null) {
            response.sendRedirect(redirectURL.toString());
            return null;
        }

        try {
            requestContext.getRepository().deleteRevision(token, uri, revision);
        }
        catch (Throwable t) {
            logger.warn("Failed to delete revision on " + uri + ": " + revision, t);
        }
        response.sendRedirect(redirectURL.toString());
        return null;
    }
    
    
}
