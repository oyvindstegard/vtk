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
package org.vortikal.security.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.OrderComparator;
import org.vortikal.security.AuthenticationException;
import org.vortikal.security.AuthenticationProcessingException;
import org.vortikal.security.Principal;
import org.vortikal.security.SecurityContext;
import org.vortikal.security.TokenManager;



/**
 * Initializer for the {@link SecurityContext security context}. A
 * security context is created for every request. Also detects
 * authentication information in requests (using {@link
 * AuthenticationHandler authentication handlers}) and tries to
 * process them.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>authenticationHandlers</code> the list of {@link
 *       AuthenticationHandler authentication handlers} to use. These
 *       handlers are invoked in the same order they are provided.  If
 *       unspecified, the application context is searched for
 *       authentication handlers.
 *   <li><code>tokenManager</code> the {@link TokenManager} which
 *       stores repository tokens for authenticated principals
 * </ul>
 */
public class SecurityInitializer implements InitializingBean, ApplicationContextAware {


    private static Log logger = LogFactory.getLog(SecurityContext.class);
    private TokenManager tokenManager;

    private AuthenticationHandler[] authenticationHandlers = null;
    private ApplicationContext applicationContext;


    public void setTokenManager(
            TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }


    public void setAuthenticationHandlers(
        AuthenticationHandler[] authenticationHandlers) {
        this.authenticationHandlers = authenticationHandlers;
    }
    

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    

    public void afterPropertiesSet() {
        if (this.authenticationHandlers == null) {
            logger.info("No authentication handlers specified, looking in context");

            Map matchingBeans = this.applicationContext.getBeansOfType(
                AuthenticationHandler.class, false, false);

            List handlers = new ArrayList(matchingBeans.values());
            if (handlers.size() == 0) {
                throw new IllegalStateException(
                    "At least one authentication handler must be specified, "
                    + "either explicitly or in application context");
            }

            Collections.sort(handlers, new OrderComparator());
            this.authenticationHandlers = (AuthenticationHandler[])
                handlers.toArray(new AuthenticationHandler[handlers.size()]);

        }

        logger.info("Using authentication handlers: "
                    + Arrays.asList(this.authenticationHandlers));
    }
    

    public boolean createContext(HttpServletRequest req,
                                 HttpServletResponse resp) {

        HttpSession session = req.getSession(true);

        String token = null;
        try {
            token = (String) session.getAttribute(
            SecurityContext.SECURITY_TOKEN_ATTRIBUTE);
        } catch (IllegalStateException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Session has been invalidated, creating new");
            }
            session.invalidate();
            session = req.getSession(true);
        }

        
        if (token != null) {
            Principal principal = tokenManager.getPrincipal(token);
            if (principal == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug(
                        "Invalid token '" + token + "' in request session, "
                        + "will proceed to check authentication");
                }
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug(
                        "Found valid token '" + token + "', principal " + principal
                        + " in request session, setting security context");
                }
                SecurityContext.setSecurityContext(
                    new SecurityContext(token, principal));
                return true;
            }
        }
        
        AuthenticationHandler handler = null;
                
        for (int i = 0; i < this.authenticationHandlers.length; i++) {
            handler = this.authenticationHandlers[i];

            if (handler.isRecognizedAuthenticationRequest(req)) {
                if (logger.isDebugEnabled()) {
                    logger.debug(
                        "Request " + req + " is recognized as an authentication "
                        + "attempt by handler " + handler + ", will try to authenticate");
                }

                try {
                    Principal principal = handler.authenticate(req);
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                            "Successfully authenticated principal: " + principal
                            + " using authentication handler " + handler + ". "
                            + "Setting security context.");
                    }
                    
                    token = tokenManager.newToken(principal, handler);
                    SecurityContext securityContext = 
                        new SecurityContext(token, tokenManager.getPrincipal(token));
                        
                    SecurityContext.setSecurityContext(securityContext);
                    req.getSession(true).setAttribute(
                        SecurityContext.SECURITY_TOKEN_ATTRIBUTE,
                        token);

                    if (!handler.postAuthentication(req, resp)) {
                        if (logger.isDebugEnabled()) {
                            logger.debug(
                                "Authentication post-processing completed by "
                                + "authentication handler " + handler 
                                + ", request processing will proceed");
                        }
                        return true;
                    }

                    if (logger.isDebugEnabled()) {
                        logger.debug(
                            "Authentication post-processing completed by "
                            + "authentication handler " + handler 
                            + ", response already committed.");
                    }
                    return false;
                
                } catch (AuthenticationException exception) {

                    AuthenticationChallenge challenge = 
                        handler.getAuthenticationChallenge();

                    if (logger.isDebugEnabled()) {
                        logger.debug(
                            "Authentication attempt " + req + " rejected by " +
                            "handler " + handler + " with message "
                            + exception.getMessage() + ", presenting challenge "
                            + challenge + " to the client");
                    }
                    challenge.challenge(req, resp);
                    return false;
                }
            }
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug(
                "Request " + req + " is not recognized as an authentication "
                + "attempt by any authentication handler. Creating default "
                + "security context.");
        }

        SecurityContext.setSecurityContext(new SecurityContext(null, null));
        return true;
    }


    /**
     * @see org.vortikal.web.ContextInitializer#destroyContext()
     */
    public void destroyContext() {
        if (logger.isDebugEnabled())
            logger.debug("Destroying security context: "
                         + SecurityContext.getSecurityContext());
        SecurityContext.setSecurityContext(null);
    }

    /**
     * Logs out the client from the authentication system. Clears the
     * {@link SecurityContext}, removes the principal from the {@link
     * TokenManager} and invalidates the request {@link
     * javax.servlet.http.HttpSession session}. Finally, calls the
     * authentication handler's {@link AuthenticationHandler#logout
     * logout} method.
     * 
     * @param req the request
     * @param resp the response
     * @return the return value of the authentication handler's
     * <code>logout()</code> method.
     * @throws AuthenticationProcessingException if an underlying
     *  problem prevented the request from being processed
     * @see AuthenticationHandler#logout
     */
    public boolean logout(HttpServletRequest req, HttpServletResponse resp)
            throws AuthenticationProcessingException {

        SecurityContext securityContext = SecurityContext.getSecurityContext();
        Principal principal = securityContext.getPrincipal();
        if (principal == null) return false;

        AuthenticationHandler handler = tokenManager.getAuthenticationHandler(
            securityContext.getToken());

        // FIXME: what if handler.isLogoutSupported() == false?
        boolean result = handler.logout(principal, req, resp);

        tokenManager.removeToken(securityContext.getToken());
        SecurityContext.setSecurityContext(null);
        req.getSession(true).invalidate();

        return result;
        
    }


    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(getClass().getName());
        sb.append(": ").append(System.identityHashCode(this));
        sb.append(", authenticationHandlers: [");
        sb.append(java.util.Arrays.asList(this.authenticationHandlers));
        sb.append("]");
        return sb.toString();
    }
}

