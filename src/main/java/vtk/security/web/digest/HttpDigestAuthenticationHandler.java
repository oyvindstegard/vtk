/* Copyright (c) 2005, University of Oslo, Norway All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *  * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *  * Neither the name of the University of Oslo nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package vtk.security.web.digest;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import vtk.security.AuthenticationException;
import vtk.security.AuthenticationProcessingException;
import vtk.security.InvalidPrincipalException;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;
import vtk.security.SecurityContext;
import vtk.security.store.MD5PasswordStore;
import vtk.security.web.AuthenticationChallenge;
import vtk.security.web.AuthenticationHandler;
import vtk.security.web.InvalidAuthenticationRequestException;
import vtk.util.cache.SimpleCache;
import vtk.util.codec.Base64;
import vtk.util.codec.MD5;
import vtk.util.net.NetUtils;
import vtk.util.web.HttpUtil;



/**
 * A somewhat incomplete HTTP/Digest authentication implementation.
 *
 * <p>TODO: move away from deprecated methods in {@link MD5PasswordPrincipalStore}.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>principalStore</code> - a {@link MD5PasswordPrincipalStore}
 *   <li><code>recognizedDomains</code> - a {@link Set} of principal
 *   domain names, for limiting the set of users that can authenticate
 *   using this authentication handler. Default is <code>null</code>
 *   (no limitation).
 *   <li><code>excludedPrincipals</code> - a {@link Set} of (fully
 *   qualified) principal names used to exclude ceratain users.
 *   <li><code>maintainState</code> - specifies whether or not to
 *   perform validations of requests by maintaining state. In
 *   particular, this involves verifying that nonce counts and nonce
 *   values are correct, and sending the
 *   <code>Authentication-Info</code> header. Note that this may
 *   degrade performance and prevent HTTP pipelining. The default
 *   value is <code>false</code>.
 *   <li><code>stateMap</code> - a {@link SimpleCache} used in
 *   conjunction with the <code>maintainState</code> setting for
 *   keeping state between requests. Note: Needs to be cleaned up
 *   periodically from an external source.
 *   <li><code>order</code> - the order returned in {@link Order#getOrder}
 * </ul>
 */
public class HttpDigestAuthenticationHandler
  implements AuthenticationHandler, AuthenticationChallenge, Ordered, InitializingBean {

    private Log logger = LogFactory.getLog(this.getClass());

    private String identifier;
    private PrincipalFactory principalFactory;
    
    private String nonceKey = NetUtils.guessHostName() + "." + System.currentTimeMillis();
    private MD5PasswordStore principalStore = null;
    private Set<String> recognizedDomains = null;
    private Set<String> excludedPrincipals = new HashSet<String>();
    private boolean maintainState = false;
    private SimpleCache<String, StateEntry> stateMap;
    private int order = Integer.MAX_VALUE;
    

    public void setPrincipalStore(MD5PasswordStore principalStore) {
        this.principalStore = principalStore;
    }

    public void setRecognizedDomains(Set<String> recognizedDomains) {
        this.recognizedDomains = recognizedDomains;
    }

    public void setExcludedPrincipals(Set<String> excludedPrincipals) {
        this.excludedPrincipals = excludedPrincipals;
    }
    
    public void setStateMap(SimpleCache<String, StateEntry> stateMap) {
        this.stateMap = stateMap;
    }
    
    public void setMaintainState(boolean maintainState) {
        this.maintainState = maintainState;
    }
    
    public void setOrder(int order) {
        this.order = order;
    }
    
    public int getOrder() {
        return this.order;
    }

    public void afterPropertiesSet() {
        if (this.principalStore == null) {
            throw new BeanInitializationException(
                "JavaBean property 'principalStore' not set.");
        }
        if (this.maintainState && this.stateMap == null) {
            throw new BeanInitializationException(
                "JavaBean property 'stateMap' not set.");
        }
    }


    public boolean isLogoutSupported() {
        return false;
    }


    public boolean logout(Principal principal, HttpServletRequest req,
                          HttpServletResponse resp) {
        // FIXME: redirect user to page explaining how to exit the browser? 
        return false;
    }


    public AuthenticationChallenge getAuthenticationChallenge() {
        return this;
    }


    public boolean isRecognizedAuthenticationRequest(HttpServletRequest req) {

        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Digest ")) {
            return false;
        }
        
        String headerFields = authHeader.substring("Digest: ".length() -1 );
        String username = HttpUtil.extractHeaderField(headerFields, "username");
        if (username == null) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Unable to extract field 'username' from header: " + authHeader);
            }
            return false;
        }

        Principal principal = null;

        try {
            principal = principalFactory.getPrincipal(username, Principal.Type.USER);
        } catch (InvalidPrincipalException e) {
            return false;
        }

        if (this.excludedPrincipals != null
            && this.excludedPrincipals.contains(principal.getQualifiedName())) {
            return false;
        }

        if (this.recognizedDomains == null
            || this.recognizedDomains.contains(principal.getDomain()))
            return true;

        return false;
    }

    public AuthResult authenticate(HttpServletRequest request)
        throws AuthenticationException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Digest ")) {
            throw new InvalidAuthenticationRequestException(
                "Authorization header either missing or does not start with 'Digest'");
        }
        
        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Authentication header: " + authHeader);
        }
        
        String headerFields = authHeader.substring("Digest: ".length() -1 );

        String uri = HttpUtil.extractHeaderField(headerFields, "uri");

        String response = HttpUtil.extractHeaderField(headerFields, "response");
        String nc = HttpUtil.extractHeaderField(headerFields, "nc");
        String nonce = HttpUtil.extractHeaderField(headerFields, "nonce");
        String cnonce = HttpUtil.extractHeaderField(headerFields, "cnonce");
        String qop = HttpUtil.extractHeaderField(headerFields, "qop");
        String username = HttpUtil.extractHeaderField(headerFields, "username");
        String opaque = HttpUtil.extractHeaderField(headerFields, "opaque");

        if (response == null) {
            throw new InvalidAuthenticationRequestException(
                "Missing authentication header field 'response'");
        }
        if (username == null) {
            throw new InvalidAuthenticationRequestException(
                "Missing authentication header field 'username'");
        }

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Authentication fields: uri = '" + uri + "', "
                         + "response = '" + response + "', "
                         + "nc = '" + nc + "', cnonce = '" + cnonce + "', "
                         + "qop = '" + qop + "', username = '" + username + "'");
        }

        return doAuthenticate(request, uri, response, nc, nonce,
                              cnonce, qop, username, opaque);
    }


    public boolean postAuthentication(HttpServletRequest req,
                                      HttpServletResponse resp) {
        
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null) {
            return false;
        }

        String headerFields = authHeader.substring("Digest: ".length() -1 );

        String nonce = HttpUtil.extractHeaderField(headerFields, "nonce");
        String opaque = HttpUtil.extractHeaderField(headerFields, "opaque");
        if (nonce == null || opaque == null) {
            return false;
        }

        Principal principal = SecurityContext.getSecurityContext().getPrincipal();
        if (principal == null) {
            return false;
        }

        if (this.maintainState) {

            StateEntry entry = (StateEntry) this.stateMap.remove(nonce + ":" + opaque);
            if (entry == null) {
                return false;
            }
            Date timestamp = new Date();
            String nextNonce = this.generateNonce();
            entry.setUsername(principal.getQualifiedName());
            entry.setNonce(nextNonce);
            entry.setTimestamp(timestamp);
            entry.setNonceCount(entry.getNonceCount() + 1);
            entry.setStale(false);

            this.stateMap.put(nextNonce + ":" + opaque, entry);
            resp.addHeader("Authentication-Info", "nextnonce=" + nextNonce);
        }

        return false;
    }


    public void challenge(HttpServletRequest req, HttpServletResponse resp) {

        boolean stale = false;

        if (this.maintainState) {

            String authHeader = req.getHeader("Authorization");
            if (authHeader != null) {

                String headerFields = authHeader.substring("Digest: ".length() -1 );

                String nonce = HttpUtil.extractHeaderField(headerFields, "nonce");
                String opaque = HttpUtil.extractHeaderField(headerFields, "opaque");
                if (nonce != null && opaque != null) {
                    StateEntry entry = (StateEntry) this.stateMap.remove(nonce + ":" + opaque);
                    if (entry != null) {
                        stale = entry.isStale();
                    }
                }
            }
        }
        
        String nonce =  generateNonce();
        String opaque = generateOpaque();

        // FIXME: Why is getRealm() deprecated?
        String challengeHeader = "Digest realm=\"" + this.principalStore.getRealm() + "\"";
        challengeHeader += ", nonce=\"" + nonce + "\"";
        challengeHeader += ", opaque=\"" + opaque + "\"";
        challengeHeader += ", stale=" + stale;
        challengeHeader += ", algorithm=MD5";
        challengeHeader += ", qop=auth";
        
        if (this.maintainState) {

            Date timestamp = new Date();
            StateEntry entry = new StateEntry(null, timestamp, nonce, 1, opaque);
            this.stateMap.put(nonce + ":" + opaque, entry);
        }
        
        try {
            resp.setHeader("WWW-Authenticate", challengeHeader);
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } catch (IOException e) {
            throw new AuthenticationProcessingException(
                "Unable to present authentication challenge to user", e);
        }
    }

    private AuthResult doAuthenticate(HttpServletRequest request, String uri,
                                     String response, String nc, String nonce, String cnonce,
                                     String qop, String username, String opaque) {

        StateEntry stateEntry = null;

        if (this.maintainState) {
            stateEntry = this.stateMap.get(nonce + ":" + opaque);

            if (stateEntry != null) {

                if (!stateEntry.getNonce().equals(nonce)) {
                    stateEntry.setStale(true);
                    throw new AuthenticationException(
                        "Authentication header field 'nonce': " + nonce
                        + " does not match expected value: " + stateEntry.getNonce());
                }

                try {
                    long nonceCount = Long.parseLong(nc, 16);
                    if (nonceCount != stateEntry.getNonceCount()) {
                        throw new AuthenticationException(
                            "Authentication header field 'nc', value " + nc
                            + " did not match expected value " + stateEntry.getNonceCount());
                    }
                } catch (NumberFormatException e) {
                    throw new InvalidAuthenticationRequestException(
                        "Authentication header field 'nc' was not a hex number: " + nc);
                }
            }
        }
        

        Principal principal = principalFactory.getPrincipal(username, Principal.Type.USER);
        if (principal == null) {
            throw new AuthenticationException(
                "Unable to authenticate principal using HTTP/Digest for request"
                + request + ": no principal found");
        }

        if (!this.principalStore.validatePrincipal(principal)) {
            throw new AuthenticationException(
                "Unknown principal in HTTP/Digest request: " + principal);
        }

        String componentA1 = this.principalStore.getMD5HashString(principal);

        if (componentA1 == null) {
            throw new AuthenticationException(
                "Password hash for principal " + principal + " not found");
        }

        String componentA2 = MD5.md5sum(request.getMethod() + ":" + uri);
        
        StringBuffer b = new StringBuffer();
        b.append(componentA1);

        b.append(":").append(nonce);
        if (nc != null) b.append(":").append(nc);
        if (cnonce != null) b.append(":").append(cnonce);
        if (qop != null) b.append(":").append(qop);
        b.append(":").append(componentA2);
                
        String serverDigest = MD5.md5sum(b.toString());
      
        if (this.logger.isDebugEnabled()) {
            this.logger.debug("client digest: '" + response
                         + "', server digest: '" + serverDigest + "'");
        }
                
        if (!serverDigest.equals(response)) {
            throw new AuthenticationException("Authentication failure for principal "
                                              + principal + " (incorrect password)");
        }

        if (this.maintainState && stateEntry == null) {

            stateEntry = new StateEntry(null, new Date(), nonce, 1, opaque);
            stateEntry.setStale(true);
            this.stateMap.put(nonce + ":" + opaque, stateEntry);
            throw new AuthenticationException(
                "Nothing known about request  " + request);

        } else if (this.maintainState && stateEntry != null && stateEntry.isStale()) {
            throw new AuthenticationException(
                "Stale nonce header field in authentication request: " + request);
        } 

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Successfully authenticated principal " + principal);
        }
        return new AuthResult(principal.getQualifiedName());
    }

    private String generateNonce() {
        String timestamp = getTimestamp();
        String nonce = Base64.encode(timestamp + MD5.md5sum(timestamp + ":" + this.nonceKey));
        return nonce;
    }


    private String generateOpaque() {
        return UUID.randomUUID().toString();
    }


    private String getTimestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = format.format(new Date());
        return timestamp;
    }
    


    @SuppressWarnings("unused")
    private static class StateEntry {
        private String username;
        private Date timestamp;
        private String nonce;
        private long nonceCount;
        private String opaque;
        private boolean stale = false;
        

        public StateEntry(String username, Date timestamp, String nonce,
                          long nonceCount, String opaque) {
            this.username = username;
            this.timestamp = timestamp;
            this.nonce = nonce;
            this.nonceCount = nonceCount;
            this.opaque = opaque;
        }
        
        public String getUsername() {
            return this.username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public Date getTimestamp() {
            return this.timestamp;
        }

        public void setTimestamp(Date timestamp) {
            this.timestamp = timestamp;
        }

        public String getNonce() {
            return this.nonce;
        }

        public void setNonce(String nonce) {
            this.nonce = nonce;
        }

        public long getNonceCount() {
            return this.nonceCount;
        }

        public void setNonceCount(long nonceCount) {
            this.nonceCount = nonceCount;
        }

        public String getOpaque() {
            return this.opaque;
        }

        public void setOpaque(String opaque) {
            this.opaque = opaque;
        }

        public boolean isStale() {
            return this.stale;
        }

        public void setStale(boolean stale) {
            this.stale = stale;
        }
        
        

        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append(this.getClass().getName()).append(": [");
            sb.append("username = ").append(this.username).append(", ");
            sb.append("timestamp = ").append(this.timestamp).append(", ");
            sb.append("nonce = ").append(this.nonce).append(", ");
            sb.append("nonce-count = ").append(this.nonceCount).append(", ");
            sb.append("opaque = ").append(this.opaque).append("]");
            return sb.toString();
        }
    }

    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }
    
    @Required
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    
    public String getIdentifier() {
        return this.identifier;
    }

}
