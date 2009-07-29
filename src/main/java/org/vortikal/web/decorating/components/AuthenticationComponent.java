/* Copyright (c) 2007, University of Oslo, Norway
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

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.Path;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.security.Principal;
import org.vortikal.security.SecurityContext;
import org.vortikal.web.RequestContext;
import org.vortikal.web.decorating.DecoratorRequest;
import org.vortikal.web.decorating.DecoratorResponse;
import org.vortikal.web.service.Service;
import org.vortikal.web.service.URL;


public class AuthenticationComponent extends ViewRenderingDecoratorComponent {

    private static final String DESCRIPTION = "Displays a login or logout URL";

    private Service loginService;
    private Service logoutService;
    private Repository repository;
    

    @Required public void setLoginService(Service loginService) {
        if (loginService == null) {
            throw new IllegalArgumentException("Argument cannot be null");
        }
        this.loginService = loginService;
    }

    @Required public void setLogoutService(Service logoutService) {
        if (logoutService == null) {
            throw new IllegalArgumentException("Argument cannot be null");
        }
        this.logoutService = logoutService;
    }
    
    @Required public void setRepository(Repository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("Argument cannot be null");
        }
        this.repository = repository;
    }
    
    protected String getDescriptionInternal() {
        return DESCRIPTION;
    }

    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new HashMap<String, String>();
        return map;
    }

    protected void processModel(Map<Object, Object> model, DecoratorRequest request,
                             DecoratorResponse response) throws Exception {

        super.processModel(model, request, response);
        Path uri = RequestContext.getRequestContext().getResourceURI();
        SecurityContext securityContext = SecurityContext.getSecurityContext();
        
        Principal principal = securityContext.getPrincipal();
        String token = securityContext.getToken();
        Resource resource = this.repository.retrieve(token, uri, true);
        
        model.put("principal", principal);

            if (principal == null) {
                try {
                    URL loginURL = this.loginService.constructURL(resource, principal);
                    model.put("loginURL", loginURL);
                } catch (Exception e) { }
            } else {
                try {
                    URL logoutURL = this.logoutService.constructURL(resource, principal);
                    model.put("logoutURL", logoutURL);
                } catch (Exception e) { }
            }
    }

}
