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
package vtk.security.web;

import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.core.Ordered;

import vtk.security.AuthenticationException;
import vtk.security.AuthenticationProcessingException;
import vtk.security.InvalidPrincipalException;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;
import vtk.util.cache.SimpleCache;
import vtk.util.codec.MD5;
import vtk.web.service.WebAssertion;

/**
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>recognizedDomains</code> - a {@link Set} specifying the
 *     recognized principal {@link Principal#getDomain domains}. If
 *     this property is not specified, all domains are matched.
 *   <li><code>cache</code> - simple {@link SimpleCache cache} to
 *   allow for clients that don't send cookies (optional)
 *   <li><code>recognizedPrincipals</code> - a {@link Set} of principal
 *     names to recognize when matching authentication
 *     requests. Takes precedence over recognizedDomains. If the principal in an 
 *     authentication request is not present in this set, the authentication 
 *     request is treated as if it were not recognized.
 *   <li><code>order</code> - the bean order returned in {@link
 *   Ordered#getOrder}
 * </ul>
 */
public abstract class AbstractAuthenticationHandler implements
        AuthenticationHandler, Ordered {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    private String identifier;
    
    private PrincipalFactory principalFactory;
    
    /* Simple cache to allow for clients that don't send cookies */
    private SimpleCache<String, AuthResult> cache;

    private List<WebAssertion> requestAssertions; 
    
    private Set<String> recognizedDomains = null;

    private int order = Integer.MAX_VALUE;
    

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public boolean isRecognizedAuthenticationRequest(HttpServletRequest req)
            throws AuthenticationProcessingException {

        String username = null;

        try {
            username = getUserName(req);
        } catch (IllegalArgumentException e) {
            throw new InvalidAuthenticationRequestException(e);
        }

        Principal principal = null;
        try {
            principal = principalFactory.getPrincipal(username, Principal.Type.USER);
        } catch (InvalidPrincipalException e) {
            return false;
        }

        if (this.requestAssertions != null) {
            for (WebAssertion assertion : this.requestAssertions) {
                if (!assertion.matches(req, null, principal)) {
                    return false;
                }
            }
        }
        
        if (this.recognizedDomains == null
                || this.recognizedDomains.contains(principal.getDomain()))
            return true;

        return false;
    }

    @Override
    public AuthResult authenticate(HttpServletRequest request)
            throws AuthenticationProcessingException, AuthenticationException {

        String username = null;
        String password = null;
        try {

            username = getUserName(request);
            password = getPassword(request);

        } catch (IllegalArgumentException e) {
            throw new InvalidAuthenticationRequestException(e);
        }

        AuthResult authResult = new AuthResult(username);

        String md5sum = null;
        if (this.cache != null) {
            // Only calculate if useful
            md5sum = MD5.md5sum(authResult.getUID() + password);
            
            AuthResult cachedResult = this.cache.get(md5sum);
            if (cachedResult != null) {
                if (this.logger.isDebugEnabled())
                    this.logger.debug("Found authenticated principal '"
                            + username + "' in cache.");
                return cachedResult;
            }
        }

        authenticateInternal(username, password);

        if (this.cache != null)
            /* add to cache */
            this.cache.put(md5sum, authResult);

        return authResult;
    }

    protected abstract void authenticateInternal(String uid,
            String password) throws AuthenticationProcessingException,
            AuthenticationException;

    protected abstract String getUserName(HttpServletRequest request);

    protected abstract String getPassword(HttpServletRequest request);

    public void setRequestAssertions(List<WebAssertion> requestAssertions) {
        this.requestAssertions = requestAssertions;
    }

    public void setCache(SimpleCache<String, AuthResult> cache) {
        this.cache = cache;
    }

    public void setRecognizedDomains(Set<String> recognizedDomains) {
        this.recognizedDomains = recognizedDomains;
    }

    public void setOrder(int order) {
        this.order = order;
    }
    
    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }

    @Override
    public String getIdentifier() {
        return this.identifier;
    }

    @Required
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

}
