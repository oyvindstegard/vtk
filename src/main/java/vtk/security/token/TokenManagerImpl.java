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

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;

import vtk.security.Principal;
import vtk.security.web.AuthenticationHandler;
import vtk.util.cache.SimpleCache;
import vtk.util.cache.SimpleCacheImpl;

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
public class TokenManagerImpl implements TokenManager, InitializingBean {

    private static Log logger = LogFactory.getLog(TokenManagerImpl.class);
    private ConcurrentMap<String,AuthenticationHandler> authHandlerMap = new ConcurrentHashMap<>();
    private SimpleCache<String, PrincipalItem> cache = null;
    private Map<String, Principal> registeredPrincipals = new HashMap<String, Principal>();
    private List<Principal> defaultPrincipals = null;
    

    public void setCache(SimpleCache<String, PrincipalItem> cache) {
        this.cache = cache;
    }

    public void setDefaultPrincipals(List<Principal> defaultPrincipals) {
        this.defaultPrincipals = defaultPrincipals;
    }
    
    public void afterPropertiesSet() throws Exception {

        if (this.defaultPrincipals != null) {
            for (Principal principal: this.defaultPrincipals) {
                String token = generateID();
                this.registeredPrincipals.put(token, principal);
            }
        }
        
        if (this.cache == null) {
            logger.info("No SimpleCache supplied, instantiating default");
            this.cache = new SimpleCacheImpl<String, PrincipalItem>();    
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
    public AuthenticationHandler getAuthenticationHandler(String token) {
        if (this.registeredPrincipals.containsKey(token)) {
            return null;
        }
        PrincipalItem item = this.cache.get(token);
        if (item == null) {
            return null;
        }
        return authHandlerMap.get(item.getAuthenticationHandlerId());
    }
    
    @Override
    public String newToken(Principal principal,
                           AuthenticationHandler authenticationHandler) {
        String token = generateID();
        if (!authHandlerMap.containsKey(authenticationHandler.getIdentifier())) {
            authHandlerMap.putIfAbsent(authenticationHandler.getIdentifier(), authenticationHandler);
        }
        PrincipalItem item = new PrincipalItem(principal, authenticationHandler.getIdentifier());
        this.cache.put(token, item);
        return token;
    }

    @Override
    public void removeToken(String token) {

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
    private static class PrincipalItem implements Serializable {
        private static final long serialVersionUID = -3020844029803640370L;
        private Principal principal = null;
        private String authenticationHandler = null;

        public PrincipalItem(Principal principal,
                             String authenticationHandler) {
            this.principal = principal;
            this.authenticationHandler = authenticationHandler;
        }

        public Principal getPrincipal() {
            return this.principal;
        }

        public String getAuthenticationHandlerId() {
            return this.authenticationHandler;
        }
    }

}
