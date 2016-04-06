/* Copyright (c) 2004, 2016 University of Oslo, Norway
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
package vtk.security.token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import vtk.security.Principal;
import vtk.security.web.AuthenticationHandler;
import vtk.util.cache.SimpleCache;

/**
 * Default implementation of the {@link TokenManager} interface. Keeps
 * tokens in a cache (using a {@link SimpleCache}). Also supports
 * generating trusted tokens for system purposes (e.g. reading all
 * resources, etc.)
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>cache</code> - a {@link SimpleCache} holding the
 *   tokens.
 *   <li><code>defaultPrincipals</code> - a {@link List} of {@link
 *   Principal principals} that exist in the token manager without
 *   having to authenticate (such as root users and other system
 *   users).
 * </ul>
 */
public class TokenManagerImpl implements TokenManager {

    private SimpleCache<String, PrincipalItem> cache = null;
    private Map<String, Principal> registeredPrincipals = new HashMap<String, Principal>();
    
    public TokenManagerImpl(SimpleCache<String, PrincipalItem> cache) {
        this.cache = cache;
    }

    public void setDefaultPrincipals(List<Principal> defaultPrincipals) {
        for (Principal principal: defaultPrincipals) {
            String token = generateID();
            registeredPrincipals.put(token, principal);
        }
    }
    
    @Override
    public Principal getPrincipal(String token) {

        if (this.registeredPrincipals.containsKey(token))
            return this.registeredPrincipals.get(token);
        
        PrincipalItem item = this.cache.get(token);
        if (item == null) {
            return null;
        }
        return item.getPrincipal();
    }

    @Override
    public String getAuthenticationHandlerID(String token) {
        if (this.registeredPrincipals.containsKey(token))
            return null;
        
        PrincipalItem item = this.cache.get(token);
        if (item == null) {
            return null;
        }
        return item.getAuthenticationHandlerId();
    }

    @Override
    public String newToken(Principal principal,
                           AuthenticationHandler authenticationHandler) {
        String token = generateID();
        PrincipalItem item = new PrincipalItem(principal, authenticationHandler.getIdentifier());
        this.cache.put(token, item);
        return token;
    }

    @Override
    public void removeToken(String token) {

        // XXX theoretically this races with background token expiry
        PrincipalItem item = this.cache.get(token);
        if (item == null) {
            throw new IllegalArgumentException(
                    "Tried to remove unexisting token: " + token);
        }
        this.cache.remove(token);
    }

    @Override
    public String getRegisteredToken(Principal principal) {
        if (this.registeredPrincipals.containsValue(principal)) {
            for (String token: this.registeredPrincipals.keySet()) {
                if (this.registeredPrincipals.get(token).equals(principal))
                        return token;
            }
        }
        return null;
    }

    private String generateID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Class holding (Principal, AuthenticationHandler) pairs.
     */
    private static class PrincipalItem implements java.io.Serializable {
        private static final long serialVersionUID = 2690838387670375336L;

        private Principal principal = null;
        private String authenticationHandlerId = null;

        public PrincipalItem(Principal principal,
                             String authenticationHandlerId) {
            this.principal = principal;
            this.authenticationHandlerId = authenticationHandlerId;
        }

        public Principal getPrincipal() {
            return this.principal;
        }

        public String getAuthenticationHandlerId() {
            return this.authenticationHandlerId;
        }
    }

}
