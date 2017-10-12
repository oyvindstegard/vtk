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
package vtk.web.decorating.components;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.RepositoryAction;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.display.listing.ListingPager;
import vtk.web.service.Service;
import vtk.web.service.URL;
import vtk.web.service.WebAssertion;

public class LoginManageComponent extends ViewRenderingDecoratorComponent {

    private static final String DESCRIPTION = "Displays an authentication and manage component";

    private Map<String, Service> alternativeLoginServices;
    private Service logoutService;
    private boolean displayOnlyIfAuth = false;
    private boolean displayAuthUser = false;
    private WebAssertion ajaxEditorExistsAssertion;

    @Override
    protected void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {

        super.processModel(model, request, response);
        RequestContext requestContext = RequestContext.getRequestContext(request.getServletRequest());
        Repository repository = requestContext.getRepository();
        Path uri = requestContext.getResourceURI();
        Principal principal = requestContext.getPrincipal();
        String token = requestContext.getSecurityToken();
        Resource resource = repository.retrieve(token, uri, true);

        // VTK-2460
        if (requestContext.isViewUnauthenticated()) {
            principal = null;
        }

        model.put("principal", principal);

        Map<String, URL> options = new LinkedHashMap<>();

        try {
            if (principal == null && !displayOnlyIfAuth) { // Not logged in - Unauthenticated
                this.putLoginURL(options, request);
                this.putAdminURL(options, resource, request);
            } else if (principal != null) { // Logged in - Authenticated)
                if (displayAuthUser) {
                    options.put("principal-desc", null);
                }
                this.putAdminURL(options, resource, request);
                this.putEditURL(options, resource, request, principal, repository);
                this.putLogoutURL(options, resource, principal, requestContext);
            }
        }
        catch (Exception e) { }

        model.put("options", options);
    }
    
    private void putLoginURL(Map<String, URL> options, DecoratorRequest request) throws Exception {
        URL loginURL = ListingPager.removePagerParms(URL.create(request.getServletRequest()));
        loginURL.removeParameter("authTarget");
        loginURL.addParameter("authTarget", request.getServletRequest().getScheme());
        options.put("login", loginURL);
    }
    
    private void putLogoutURL(Map<String, URL> options, Resource resource, Principal principal, RequestContext requestContext) throws Exception {
        URL logoutURL = logoutService.urlConstructor(requestContext.getRequestURL())
                                     .withResource(resource)
                                     .withPrincipal(principal)
                                     .constructURL();
        options.put("logout", logoutURL);
    }

    private void putAdminURL(Map<String, URL> options, Resource resource, DecoratorRequest request) throws Exception {
        Service adminService = this.alternativeLoginServices.get("admin");
        if (adminService != null) {
            URL adminURL = adminService.urlConstructor(URL.create(request.getServletRequest()))
                    .withURI(resource.getURI())
                    .constructURL();
            if (resource.isCollection()) {
                options.put("admin-collection", adminURL);
            }
            else {
                options.put("admin", adminURL);
            }
        }
    }

    private void putEditURL(Map<String, URL> options, Resource resource, DecoratorRequest request, Principal principal, Repository repository) throws Exception {
        URL editURL = null;
        Service editService = null;
        
        if(ajaxEditorExistsAssertion.matches(request.getServletRequest(), resource, principal)) { // Need to check this here also, as not checked in Service.constructURL for some reason
            editService = this.alternativeLoginServices.get("ajaxeditor.service");
            if (editService != null) editURL = editContructURL(editURL, editService, resource, request, principal);
        } else {
            editService = this.alternativeLoginServices.get("structuredResources.editService");
            if (editService != null) editURL = editContructURL(editURL, editService, resource, request, principal);
            
            editService = this.alternativeLoginServices.get("editor.service");
            if (editService != null) editURL = editContructURL(editURL, editService, resource, request, principal);
        }
        
        if(editURL != null) {
            boolean isWriteAuthorizedNotLockedByOther = repository.isAuthorized(resource, RepositoryAction.READ_WRITE, principal, true);
            if(isWriteAuthorizedNotLockedByOther) {
                if (resource.isCollection()) {
                    options.put("edit-collection", editURL);
                } else {
                    options.put("edit", editURL);
                }
            }
        }
    }
    
    private URL editContructURL(URL editURL, Service editService, Resource resource, DecoratorRequest request, Principal principal) {
        if(editURL == null) {
            try {
                editURL = editService.urlConstructor(URL.create(request.getServletRequest()))
                                     .withURI(resource.getURI())
                                     .withResource(resource)
                                     .withPrincipal(principal)
                                     .constructURL();
            } catch (Exception e) {
                // URL not created because of assertions
            }
        }
        return editURL;
    }

    public void setAlternativeLoginServices(Map<String, Service> alternativeLoginServices) {
        this.alternativeLoginServices = alternativeLoginServices;
    }

    public Map<String, Service> getAlternativeLoginServices() {
        return alternativeLoginServices;
    }

    @Required
    public void setLogoutService(Service logoutService) {
        if (logoutService == null) {
            throw new IllegalArgumentException("Argument cannot be null");
        }
        this.logoutService = logoutService;
    }

    @Override
    protected String getDescriptionInternal() {
        return DESCRIPTION;
    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new HashMap<>();
        return map;
    }

    public void setDisplayOnlyIfAuth(boolean displayOnlyIfAuth) {
        this.displayOnlyIfAuth = displayOnlyIfAuth;
    }

    public void setDisplayAuthUser(boolean displayAuthUser) {
        this.displayAuthUser = displayAuthUser;
    }

    public void setAjaxEditorExistsAssertion(WebAssertion ajaxEditorExistsAssertion) {
        this.ajaxEditorExistsAssertion = ajaxEditorExistsAssertion;
    }

}
