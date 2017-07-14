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
package vtk.security;

import javax.servlet.http.HttpServletRequest;

import vtk.security.web.SecurityInitializer;

public class SecurityContext {
    private static final String REQUEST_ATTRIBUTE = 
            SecurityContext.class.getName() + ".requestAttribute";
    
    public static final SecurityContext ANONYMOUS_CONTEXT = new SecurityContext(null, null);

    private String token;
    private Principal principal;
    private SecurityInitializer securityInitializer;
    
    public SecurityContext(String token, Principal principal) {
        this(token, principal, null);
    }
    
    public SecurityContext(String token, Principal principal, SecurityInitializer securityInitializer) {
        this.token = token;
        this.principal = principal;
        this.securityInitializer = securityInitializer;
    }

    public static void setSecurityContext(SecurityContext securityContext, HttpServletRequest request) {
        request.setAttribute(REQUEST_ATTRIBUTE, securityContext);
    }

    public static SecurityContext getSecurityContext(HttpServletRequest request) {
        return (SecurityContext) request.getAttribute(REQUEST_ATTRIBUTE);
    }
    
    /**
     * @return the principal
     */
    public Principal getPrincipal() {
        return principal;
    }

    /**
     * @return the security token.
     */
    public String getToken() {
        return token;
    }
    
    /**
     * Reset the expirty timeout for the current security token.
     * 
     * <p>This method is provided so that tokens can be kept alive by code
     * which potentially does long running operations that may exceed the token
     * expiry timeout.
     * @return <code>true</code> if token was still valid and its expiry was reset, <code>false</code> otherwise
     */
    public boolean resetTokenExpiry() {
        // XXX should not need null-test here, something is bad in design
        // (A SecurityContext should not be allowed to exist without a backing security manager instance)
        if (securityInitializer != null) {
            return securityInitializer.resetTokenExpiry(token);
        }
        return false;
    }
    
    public SecurityInitializer securityInitializer() {
        return securityInitializer;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getName());
        sb.append(": ");
        sb.append("token = ").append(this.token).append("; ");
        sb.append("principal = ").append(this.principal);
        return sb.toString();
    }
    
}
