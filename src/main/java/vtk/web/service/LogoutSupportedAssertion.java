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
package vtk.web.service;

import java.util.Optional;

import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.security.token.TokenManager;
import vtk.security.web.AuthenticationHandler;
import vtk.security.web.AuthenticationHandlerRegistry;
import vtk.web.RequestContext;

public class LogoutSupportedAssertion extends AbstractRepositoryAssertion {

    private TokenManager tokenManager;
    private AuthenticationHandlerRegistry authHandlerRegistry;
    
    public LogoutSupportedAssertion(TokenManager tokenManager, 
            AuthenticationHandlerRegistry authHandlerRegistry) {
        this.tokenManager = tokenManager;
        this.authHandlerRegistry = authHandlerRegistry;
    }

    @Override
    public boolean matches(Resource resource, Principal principal) {
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        if (token == null) {
            return false;
        }
        String handlerID = tokenManager.getAuthenticationHandlerID(token);
        if (handlerID == null) {
            return false;
        }
        Optional<AuthenticationHandler> handler = authHandlerRegistry.lookup(handlerID);
        if (!handler.isPresent()) return false;
        return handler.get().isLogoutSupported();
    }

    @Override
    public boolean conflicts(Assertion assertion) {
        return false;
    }
    
    @Override
    public String toString() {
        return "authHandler.logoutSupported";
    }
}
