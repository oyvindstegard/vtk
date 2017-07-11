/* Copyright (c) 2004, University of Oslo, Norway
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
package vtk.web.actions.logout;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.security.web.SecurityInitializer;
import vtk.web.RequestContext;
import vtk.web.SimpleFormController;
import vtk.web.actions.logout.LogoutController.LogoutCommand;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * Logs the current principal out from the security context. Does not
 * return a view, instead redirects to a given service or the referring URL, 
 * depending on the form input.
 *
 * <p>Configurable properties:
 * <ul>
 *   <li><code>securityInitializer</code> - the {@link SecurityInitializer}
 *   <li><code>redirectService</code> - the {@link Service} to redirect to if
 *   the security initializer did not handle the request itself and the form 
 *   input <code>useRedirectService</code> is not specified.
 * </ul>
 */
public class LogoutController extends SimpleFormController<LogoutCommand> implements InitializingBean {

    private Service redirectService = null;
    private SecurityInitializer securityInitializer = null;

    public void setRedirectService(Service redirectService) {
        this.redirectService = redirectService;
    }
    
    public void setSecurityInitializer(SecurityInitializer securityInitializer) {
        this.securityInitializer = securityInitializer;
    }

    @Override
    public void afterPropertiesSet() {
        if (this.redirectService == null) {
            throw new BeanInitializationException(
                "Bean property 'service' not set");
        }
        if (this.securityInitializer == null) {
            throw new BeanInitializationException(
                "Bean property 'securityInitializer' not set");
        }
    }
    
    public static class LogoutCommand {
        private URL submitURL;
        private String logoutAction;
        private String useRedirectService;
        
        public LogoutCommand(URL submitURL) {
            this.submitURL = submitURL;
        }
        public URL getSubmitURL() {
            return this.submitURL;
        }
        public void setLogoutAction(String logoutAction) {
            this.logoutAction = logoutAction;
        }
        public String getLogoutAction() {
            return this.logoutAction;
        }
        public void setUseRedirectService(String useRedirectService) {
            this.useRedirectService = useRedirectService;
        }
        public String getUseRedirectService() {
            return this.useRedirectService;
        }
    }
    
    @Override
    protected LogoutCommand formBackingObject(HttpServletRequest request)
            throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Principal principal = requestContext.getPrincipal();
        Repository repository = requestContext.getRepository();
        
        Resource resource = repository.retrieve(
                requestContext.getSecurityToken(), requestContext.getResourceURI(), true);
                    
        Service service = requestContext.getService();
        URL submitURL = service.urlConstructor(requestContext.getRequestURL())
                .withResource(resource)
                .withPrincipal(principal)
                .constructURL();
        LogoutCommand logoutCommand = new LogoutCommand(submitURL);
        return logoutCommand;
    }

    @Override
    protected ModelAndView onSubmit(HttpServletRequest request,
            HttpServletResponse response, LogoutCommand command, BindException errors)
            throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Principal principal = requestContext.getPrincipal();
        Repository repository = requestContext.getRepository();
        
        Resource resource = repository.retrieve(
                requestContext.getSecurityToken(), requestContext.getResourceURI(), true);


        boolean responseWritten = this.securityInitializer.logout(request, response);
        if (responseWritten) {
            return null;
        }

        if (command.getUseRedirectService() != null) {
            URL url = redirectService.urlConstructor(URL.create(request))
                    .withResource(resource)
                    .withPrincipal(principal)
                    .constructURL();
            sendRedirect(url.toString(), request, response);
            return null;
        }
        
        String referrer = request.getHeader("Referer");
        String url = referrer;
        if (url == null) {
            url = redirectService.urlConstructor(URL.create(request))
                    .withResource(resource)
                    .withPrincipal(principal)
                    .constructURL()
                    .toString();
        }
        sendRedirect(url, request, response);
        return null;
    }
    
    private void sendRedirect(String url, HttpServletRequest request, HttpServletResponse response) throws Exception {
        boolean http10 = "HTTP/1.0".equals(request.getProtocol());
        if (http10) {
            response.sendRedirect(url);
        } else {
            response.setStatus(303);
            response.setHeader("Location", url);
        }
    }
}
