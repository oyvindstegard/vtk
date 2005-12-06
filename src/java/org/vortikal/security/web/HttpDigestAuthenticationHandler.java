/*
 * Copyright (c) 2005, University of Oslo, Norway All rights reserved.
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
package org.vortikal.security.web;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.doomdark.uuid.UUIDGenerator;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

import org.vortikal.security.AuthenticationException;
import org.vortikal.security.AuthenticationProcessingException;
import org.vortikal.security.InvalidPrincipalException;
import org.vortikal.security.MD5PasswordPrincipalStore;
import org.vortikal.security.Principal;
import org.vortikal.security.PrincipalManager;
import org.vortikal.security.SecurityContext;
import org.vortikal.util.cache.SimpleCache;
import org.vortikal.util.codec.Base64;
import org.vortikal.util.codec.MD5;
import org.vortikal.util.net.NetUtils;
import org.vortikal.util.web.HttpUtil;


/**
 * A somewhat incomplete HTTP/Digest authentication implementation.
 *
 * <p>TODO: move away from deprecated methods in {@link MD5PasswordPrincipalStore}.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>principalStore</code> - a {@link MD5PasswordPrincipalStore}
 *   <li><code>principalManager</code> - a {@link PrincipalManager}
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
 * </ul>
 */
public class HttpDigestAuthenticationHandler
  implements AuthenticationHandler, AuthenticationChallenge, InitializingBean {

    private Log logger = LogFactory.getLog(this.getClass());
    private String nonceKey = NetUtils.guessHostName() + "." + System.currentTimeMillis();
    private MD5PasswordPrincipalStore principalStore = null;
    private PrincipalManager principalManager = null;
    private Set recognizedDomains = null;
    private Set excludedPrincipals = new HashSet();
    private boolean maintainState = false;
    private SimpleCache stateMap;
    

    public void setPrincipalStore(MD5PasswordPrincipalStore principalStore) {
        this.principalStore = principalStore;
    }

    public void setPrincipalManager(PrincipalManager principalManager) {
        this.principalManager = principalManager;
    }

    public void setRecognizedDomains(Set recognizedDomains) {
        this.recognizedDomains = recognizedDomains;
    }

    public void setExcludedPrincipals(Set excludedPrincipals) {
        this.excludedPrincipals = excludedPrincipals;
    }
    
    public void setStateMap(SimpleCache stateMap) {
        this.stateMap = stateMap;
    }
    
    public void setMaintainState(boolean maintainState) {
        this.maintainState = maintainState;
    }
    
    public void afterPropertiesSet() {
        if (this.principalStore == null) {
            throw new BeanInitializationException(
                "JavaBean property 'principalStore' not set.");
        }
        if (this.principalManager == null) {
            throw new BeanInitializationException(
                "JavaBean property 'principalManager' not set.");
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
        
        String username = HttpUtil.extractHeaderField(authHeader, "username");
        if (username == null) {
            return false;
        }

        Principal principal = null;

        try {
            principal = this.principalManager.getPrincipal(username);
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



    


    public Principal authenticate(HttpServletRequest request)
        throws AuthenticationException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Digest ")) {
            throw new InvalidAuthenticationRequestException(
                "Authorization header either missing or does not start with 'Digest'");
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("Authentication header: " + authHeader);
        }
        
        String uri = HttpUtil.extractHeaderField(authHeader, "uri");

        String response = HttpUtil.extractHeaderField(authHeader, "response");
        String nc = HttpUtil.extractHeaderField(authHeader, "nc");
        String nonce = HttpUtil.extractHeaderField(authHeader, "nonce");
        String cnonce = HttpUtil.extractHeaderField(authHeader, "cnonce");
        String qop = HttpUtil.extractHeaderField(authHeader, "qop");
        String username = HttpUtil.extractHeaderField(authHeader, "username");
        String opaque = HttpUtil.extractHeaderField(authHeader, "opaque");

        if (response == null) {
            throw new InvalidAuthenticationRequestException(
                "Missing authentication header field 'response'");
        }
        if (username == null) {
            throw new InvalidAuthenticationRequestException(
                "Missing authentication header field 'username'");
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Authentication fields: response = '" + response + "', "
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

        String nonce = HttpUtil.extractHeaderField(authHeader, "nonce");
        String opaque = HttpUtil.extractHeaderField(authHeader, "opaque");
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
                String nonce = HttpUtil.extractHeaderField(authHeader, "nonce");
                String opaque = HttpUtil.extractHeaderField(authHeader, "opaque");
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



    private Principal doAuthenticate(HttpServletRequest request, String uri,
                                     String response, String nc, String nonce, String cnonce,
                                     String qop, String username, String opaque) {

        StateEntry stateEntry = null;

        if (this.maintainState) {
            stateEntry = (StateEntry) this.stateMap.get(nonce + ":" + opaque);

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
        

        Principal principal = this.principalManager.getPrincipal(username);
        boolean correctDigest = false;

        // FIXME: Why is getMD5HashString() deprecated?
        String componentA1 = null;
        if (principal != null) {
            componentA1 = this.principalStore.getMD5HashString(principal);
        }

        String componentA2 = MD5.md5sum(request.getMethod() + ":" + uri);
        
        if (componentA1 != null) {
                
            StringBuffer b = new StringBuffer();
            b.append(componentA1);

            b.append(":").append(nonce);
            if (nc != null) b.append(":").append(nc);
            if (cnonce != null) b.append(":").append(cnonce);
            if (qop != null) b.append(":").append(qop);
            b.append(":").append(componentA2);
                
            String serverDigest = MD5.md5sum(b.toString());
      
            if (logger.isDebugEnabled()) {
                logger.debug("client digest: " + response);
                logger.debug("server digest: " + serverDigest);
            }
                
            correctDigest = serverDigest.equals(response);

            if (correctDigest) {

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

                principal = this.principalManager.getPrincipal(username);
            }
        }
            
        if (principal == null) {
            throw new AuthenticationException(
                "Unable to authenticate principal using HTTP/Digest for request"
                + request);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Successfully authenticated principal " + principal);
        }
        return principal;
    }



    private String generateNonce() {
        String timestamp = getTimestamp();
        String nonce = Base64.encode(timestamp + MD5.md5sum(timestamp + ":" + this.nonceKey));
        return nonce;
    }


    private String generateOpaque() {
        return UUIDGenerator.getInstance().generateRandomBasedUUID().toString();
    }


    private String getTimestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = format.format(new Date());
        return timestamp;
    }
    


    private class StateEntry {
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
}
