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
package vtk.security.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.security.AuthenticationException;
import vtk.security.AuthenticationProcessingException;
import vtk.security.CookieLinkStore;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;
import vtk.security.SecurityContext;
import vtk.security.token.TokenManager;
import vtk.security.web.AuthenticationHandler.AuthResult;
import vtk.util.web.HttpUtil;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.WebAssertion;

/**
 * Initializer for the {@link SecurityContext security context}. 
 * A security context is created for every request. Also
 * detects authentication information in requests 
 * (using {@link AuthenticationHandler authentication handlers}) and
 * tries to process them.
 *
 * <p>
 * Configurable JavaBean properties:
 * <ul>
 * <li><code>authenticationHandlers</code> the list of 
 * {@link AuthenticationHandler authentication handlers} to use.
 * These handlers are invoked in the same order they are provided. 
 * If unspecified, the application context is searched
 * for authentication handlers.
 * <li><code>tokenManager</code> the {@link TokenManager} which stores 
 * repository tokens for authenticated principals
 * </ul>
 */
public class SecurityInitializer {

    private static final String SECURITY_TOKEN_SESSION_ATTR = 
            SecurityInitializer.class.getName() + ".SECURITY_TOKEN";

    public static final String VRTXLINK_COOKIE = "VRTXLINK";

    private String vrtxAuthSP;

    private String uioAuthIDP;

	private String uioAuthSSO;

    private static Logger logger = LoggerFactory.getLogger(SecurityInitializer.class);

    private static Logger authLogger = LoggerFactory.getLogger("vtk.security.web.AuthLog");

    private TokenManager tokenManager;

    private PrincipalFactory principalFactory;

    private List<AuthenticationHandler> spCookieHandlers;

    private AuthenticationHandlerRegistry authHandlerRegistry;
    
    private CookieLinkStore cookieLinkStore;

    private String spCookieDomain = null;

    // Only relevant when using both https AND http and
    // different session cookie name for each protocol:
    private boolean cookieLinksEnabled = false;

    // Only relevant when using secure protocol:
    private boolean rememberAuthMethod = false;

    // Assertion that must match in order to use authentication challenge from cookie:
    private WebAssertion spCookieAssertion;

    /**
     *
     * @param req
     * @param resp
     * @return <code>true</code> if request processing should continue after context has been created,
     *         <code>false</code> otherwise (which means that security context initialization handles a challenge or any
     *         authentication post-processing requests by itself).
     *
     * @throws AuthenticationProcessingException
     * @throws ServletException
     * @throws IOException
     */
    public boolean createContext(HttpServletRequest req, HttpServletResponse resp)
            throws AuthenticationProcessingException, ServletException, IOException {

        /**
         * HttpSession session = getSession(req); String token = null;
         *
         * if (session != null) { token = (String) session.getAttribute(SECURITY_TOKEN_SESSION_ATTR); }
         */

        String token = getToken(req, resp);
        if (token != null) {
            Principal principal = tokenManager.getPrincipal(token);
            if (principal != null) {
                logger.debug("Found valid token '" + token + "', principal " + principal
                        + " in request session, setting security context");
                SecurityContext.setSecurityContext(new SecurityContext(token, principal, this), req);

                if (! HttpUtil.getCookie(req, VRTXLINK_COOKIE).isPresent() && cookieLinksEnabled) {
                    UUID cookieLinkID = cookieLinkStore.addToken(req, token);
                    Cookie c = new Cookie(VRTXLINK_COOKIE, cookieLinkID.toString());
                    c.setPath("/");
                    resp.addCookie(c);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Setting cookie: " + c + ": " + cookieLinkID.toString());
                    }
                }
                return true;
            }
            logger.debug("Invalid token '" + token + "' in request session, "
                    + "will proceed to check authentication");
        }

        for (AuthenticationHandler handler: authHandlerRegistry.orderedList()) {
            if (handler.isRecognizedAuthenticationRequest(req)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Request " + req + " is recognized as an authentication attempt by handler " + handler
                            + ", will try to authenticate");
                }

                try {
                    AuthResult result = handler.authenticate(req);
                    if (result == null) {
                        throw new IllegalStateException("Principal handler returned NULL AuthResult: " + handler
                                + " for request " + req);
                    }
                    Principal principal = principalFactory.getPrincipal(result.getUID(), Principal.Type.USER);
                    // boolean valid = this.principalManager.validatePrincipal(principal);
                    // if (!valid) {
                    // logger.warn("Unknown principal: " + principal + " returned by authentication handler "
                    // + handler + ". " + "Not setting security context.");
                    //
                    // throw new IllegalStateException("Invalid principal: " + principal);
                    // }

                    if (logger.isDebugEnabled()) {
                        logger.debug("Successfully authenticated principal: " + principal
                                + " using authentication handler " + handler + ". " + "Setting security context.");
                    }
                    if (authLogger.isDebugEnabled()) {
                        authLogger.debug(req.getRemoteAddr() + " - request-URI: " + req.getRequestURI() + " - "
                                + "Auth: principal: '" + principal + "' - method: '" + handler.getIdentifier()
                                + "' - status: OK");
                    }

                    token = this.tokenManager.newToken(principal, handler);
                    SecurityContext securityContext = new SecurityContext(token, this.tokenManager.getPrincipal(token), this);

                    SecurityContext.setSecurityContext(securityContext, req);
                    HttpSession session = req.getSession(true);
                    session.setAttribute(SECURITY_TOKEN_SESSION_ATTR, token);

                    onSuccessfulAuthentication(req, resp, handler, token);

                    if (!handler.postAuthentication(req, resp)) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Authentication post-processing completed by authentication handler "
                                    + handler + ", request processing will proceed");
                        }
                        return true;
                    }

                    if (logger.isDebugEnabled()) {
                        logger.debug("Authentication post-processing completed by authentication handler " + handler
                                + ", response already committed.");
                    }
                    return false;

                } catch (AuthenticationException exception) {

                    AuthenticationChallenge challenge = handler.getAuthenticationChallenge();

                    if (logger.isDebugEnabled()) {
                        logger.debug("Authentication attempt " + req + " rejected by " + "handler " + handler
                                + " with message " + exception.getMessage() + ", presenting challenge " + challenge
                                + " to the client");
                    }
                    if (authLogger.isDebugEnabled()) {
                        authLogger.debug(req.getRemoteAddr() + " - request-URI: " + req.getRequestURI() + " - "
                                + "Auth: request: '" + req.getRequestURI() + "' - method: '"
                                + handler.getIdentifier() + "' - status: FAIL ");
                        authLogger.debug(req.getRemoteAddr() + " - request-URI: " + req.getRequestURI() + " - "
                                + "Authentication attempt " + req + " rejected by " + "handler " + handler
                                + " with message " + exception.getMessage() + ", presenting challenge " + challenge
                                + " to the client");
                    }
                    doChallenge(req, resp, challenge);
                    return false;
                }
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Request " + req + " is not recognized as an authentication "
                    + "attempt by any authentication handler. Creating default " + "security context.");
        }

        SecurityContext.setSecurityContext(new SecurityContext(null, null, this), req);
        return true;
    }

    public void challenge(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws AuthenticationProcessingException {
        Service service = RequestContext.getRequestContext(request).getService();
        AuthenticationChallenge challenge = getAuthenticationChallenge(request, service);

        if (logger.isDebugEnabled()) {
            logger.debug("Authentication required for request " + request + ", service " + service + ". "
                    + "Using challenge " + challenge, ex);
        }
        if (challenge == null) {
            throw new IllegalStateException("Authentication challenge for service " + service
                    + " (or any of its ancestors) is not specified.");
        }
        doChallenge(request, response, challenge);
    }

    /**
     * Removes authentication state from the authentication system. The {@link SecurityContext} is cleared, the current
     * principal is removed from the {@link TokenManager}, but the {@link AuthenticationHandler#logout logout} process
     * is not initiated.
     *
     * @return <code>true</code> if any state was removed, <code>false</code> otherwise
     */
    public boolean removeAuthState(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContext securityContext = SecurityContext.getSecurityContext(request);
        if (securityContext == null) {
            return false;
        }
        Principal principal = securityContext.getPrincipal();
        if (principal == null) {
            return false;
        }
        this.tokenManager.removeToken(securityContext.getToken());
        SecurityContext.setSecurityContext(null, request);
        if (authLogger.isDebugEnabled()) {
            authLogger.debug(request.getRemoteAddr() + " - request-URI: " + request.getRequestURI() + " - "
                    +    "removeAuthState_method: Logout: principal: '" + principal
                    + "' - method: '<none>' - status: OK");
        }
        if (this.rememberAuthMethod) {
            for (String cookieName : new String[] {
                vrtxAuthSP,
                uioAuthIDP,
                VRTXLINK_COOKIE,
                uioAuthSSO
                })
            {
                HttpUtil.getCookie(request, cookieName)
                        .map(Cookie::getValue)
                        .ifPresent(cookieValue -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Deleting cookie " + cookieName);
                    }
                    if (authLogger.isDebugEnabled()) {
                        authLogger.debug(request.getRemoteAddr() + " - request-URI: " + request.getRequestURI() + " - "
                                + "Deleting cookie " + cookieName);
                    }
                    Cookie deleteCookie = new Cookie(cookieName, cookieValue);
                    if (!cookieName.equals(VRTXLINK_COOKIE)) {
                        deleteCookie.setSecure(true);
                    }
                    deleteCookie.setPath("/");
                    if (spCookieDomain != null && !cookieName.equals(VRTXLINK_COOKIE)) {
                        deleteCookie.setDomain(spCookieDomain);
                    }
                    deleteCookie.setMaxAge(0);
                    response.addCookie(deleteCookie);
                });
            }
        }

        return true;
    }

    /**
     * Logs out the client from the authentication system. Clears the {@link SecurityContext} and removes the principal
     * from the {@link TokenManager}. Finally, calls the authentication handler's {@link AuthenticationHandler#logout
     * logout} method.
     *
     * @param request
     *            the request
     * @param response
     *            the response
     * @return the return value of the authentication handler's <code>logout()</code> method.
     * @throws AuthenticationProcessingException
     *             if an underlying problem prevented the request from being processed
     * @throws IOException
     * @throws ServletException
     * @see AuthenticationHandler#logout
     */
    public boolean logout(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationProcessingException, ServletException, IOException {

        SecurityContext securityContext = SecurityContext.getSecurityContext(request);
        if (securityContext == null) {
            return false;
        }
        Principal principal = securityContext.getPrincipal();
        if (principal == null) {
            return false;
        }
        
        String handlerID  = tokenManager.getAuthenticationHandlerID(securityContext.getToken());
        Optional<AuthenticationHandler> handler = authHandlerRegistry.lookup(handlerID);
        
        if (!handler.isPresent()) {
            return false;
        }
        
        // FIXME: what if handler.isLogoutSupported() == false?
        boolean result = handler.get().logout(principal, request, response);
        String status = result ? "OK" : "FAIL";
        if (authLogger.isDebugEnabled()) {
            authLogger.debug(request.getRemoteAddr() + " - request-URI: " + request.getRequestURI() + " - "
                    + "logout_method: Logout: principal: '" + principal + "' - method: '"
                    + handlerID + "' - status: " + status);
        }

        tokenManager.removeToken(securityContext.getToken());
        SecurityContext.setSecurityContext(null, request);

        if (rememberAuthMethod) {
            List<String> spCookies = new ArrayList<>();
            spCookies.add(vrtxAuthSP);
            spCookies.add(uioAuthIDP);
            if (cookieLinksEnabled) {
                spCookies.add(VRTXLINK_COOKIE);
            }

            for (final String cookieName : spCookies) {
                HttpUtil.getCookie(request, cookieName).map(Cookie::getValue).ifPresent(cookieValue -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Deleting cookie " + cookieName);
                    }
                    Cookie deleteCookie = new Cookie(cookieName, cookieValue);
                    if (!cookieName.equals(VRTXLINK_COOKIE)) {
                        deleteCookie.setSecure(true);
                    }
                    deleteCookie.setPath("/");
                    if (spCookieDomain != null && !cookieName.equals(VRTXLINK_COOKIE)) {
                        deleteCookie.setDomain(this.spCookieDomain);
                    }
                    deleteCookie.setMaxAge(0);
                    response.addCookie(deleteCookie);
                });
            }
        }

        return result;
    }

    /**
     * Reset the expiry for the provided token.
     * @param token
     * @return <code>true</code> if token existed and its expiry was reset, <code>false</code> otherwise
     */
    public boolean resetTokenExpiry(String token) {
        return tokenManager.getPrincipal(token) != null;
    }

    /**
     * @see vtk.web.ContextInitializer#destroyContext()
     */
    public void destroyContext(HttpServletRequest request) {
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    @Required
    public void setTokenManager(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }
    
    @Required
    public void setAuthenticationHandlerRegistry(AuthenticationHandlerRegistry registry) {
        this.authHandlerRegistry = registry;
    }

    public void setCookieLinkStore(CookieLinkStore cookieLinkStore) {
        this.cookieLinkStore = cookieLinkStore;
    }

    public void setCookieLinksEnabled(boolean cookieLinksEnabled) {
        this.cookieLinksEnabled = cookieLinksEnabled;
    }

    public void setRememberAuthMethod(boolean rememberAuthMethod) {
        this.rememberAuthMethod = rememberAuthMethod;
    }

    public void setSpCookieDomain(String spCookieDomain) {
        if (spCookieDomain != null && !"".equals(spCookieDomain.trim())) {
            this.spCookieDomain = spCookieDomain;
        }
    }

    public void setSpCookieAssertion(WebAssertion spCookieAssertion) {
        this.spCookieAssertion = spCookieAssertion;
    }

    private String getToken(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (!this.cookieLinksEnabled) {
            if (session == null) {
                return null;
            }
            return (String) session.getAttribute(SECURITY_TOKEN_SESSION_ATTR);
        }
        if (session != null) {
            final String tokenFromSession = (String) session.getAttribute(SECURITY_TOKEN_SESSION_ATTR);
            if (tokenFromSession != null) {
                Principal principal = this.tokenManager.getPrincipal(tokenFromSession);
                if (principal != null) {
                    return tokenFromSession;
                }
            }
        }
        if (request.getCookies() != null && !request.isSecure()) {
            Optional<String> cookieValue = HttpUtil.getCookie(request, VRTXLINK_COOKIE).map(Cookie::getValue);
            logger.debug("Cookie: " + VRTXLINK_COOKIE + ": " + cookieValue);
            if (cookieValue.isPresent()) {
                UUID id;
                try {
                    id = UUID.fromString(cookieValue.get());
                }
                catch (Throwable t) {
                    logger.debug("Invalid UUID cookie value: " + cookieValue.get(), t);
                    return null;
                }
                String token = this.cookieLinkStore.getToken(request, id);
                if (token == null) {
                    logger.debug("No token found from cookie " + VRTXLINK_COOKIE + ", deleting cookie");
                    Cookie deleteCookie = new Cookie(VRTXLINK_COOKIE, cookieValue.get());
                    deleteCookie.setPath("/");
                    deleteCookie.setMaxAge(0);
                    response.addCookie(deleteCookie);
                }
                else {
                    logger.debug("Found token " + token + " from cookie " + VRTXLINK_COOKIE);
                    session = request.getSession(true);
                    session.setAttribute(SECURITY_TOKEN_SESSION_ATTR, token);
                    return token;
                }
            }
        }
        return null;

    }

    private void onSuccessfulAuthentication(HttpServletRequest req, HttpServletResponse resp,
            AuthenticationHandler handler, String token) {

        if (!req.isSecure()) {
            return;
        }

        if (this.rememberAuthMethod && this.spCookieHandlers.contains(handler)) {
            List<String> spCookies = new ArrayList<>();
            spCookies.add(vrtxAuthSP);
            spCookies.add(uioAuthIDP);

            for (String cookie : spCookies) {
                Cookie c = new Cookie(cookie, handler.getIdentifier());
                c.setSecure(true);
                c.setPath("/");

                if (this.spCookieDomain != null) {
                    c.setDomain(this.spCookieDomain);
                }

                resp.addCookie(c);
                if (logger.isDebugEnabled()) {
                    logger.debug("Setting cookie: " + c + ": " + handler.getIdentifier());
                }
            }
        }
        if (this.cookieLinksEnabled) {
            UUID cookieLinkID = this.cookieLinkStore.addToken(req, token);
            Cookie c = new Cookie(VRTXLINK_COOKIE, cookieLinkID.toString());
            c.setPath("/");
            resp.addCookie(c);
            if (logger.isDebugEnabled()) {
                logger.debug("Setting cookie: " + c + ": " + cookieLinkID.toString());
            }
        }
    }

    private void doChallenge(HttpServletRequest request, HttpServletResponse response, AuthenticationChallenge challenge) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object token = session.getAttribute(SECURITY_TOKEN_SESSION_ATTR);
            if (token != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Removing invalid token '" + token + "' from session");
                }
                session.removeAttribute(SECURITY_TOKEN_SESSION_ATTR);
            }
        }
        try {
            challenge.challenge(request, response);

        } catch (AuthenticationProcessingException ape) {
            // VTK-1896
            // Avoid wrapping APE in another APE, otherwise we get banana dance.
            throw ape;
        } catch (Exception e) {
            throw new AuthenticationProcessingException("Unable to present authentication challenge " + challenge, e);
        }
    }

    private AuthenticationChallenge getAuthenticationChallenge(HttpServletRequest request, Service service) {
        AuthenticationChallenge challenge = null;
        if (this.rememberAuthMethod) {
            Optional<String> id = HttpUtil.getCookie(request, vrtxAuthSP).map(Cookie::getValue);
            if (id.isPresent()) {
                Optional<AuthenticationHandler> handler = authHandlerRegistry.lookup(id.get());
                if (handler.isPresent() && spCookieHandlers.contains(handler.get())) {
                    challenge = handler.get().getAuthenticationChallenge();
                }
            }
        }
        if (challenge != null) {
            if (this.spCookieAssertion != null) {
                boolean match = this.spCookieAssertion.matches(request, null, null);
                if (!match) {
                    challenge = null;
                }
            }
        }

        if (challenge != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Using challenge from cookie " + vrtxAuthSP + ": " + challenge);
            }
            return challenge;
        }

        challenge = service.getAuthenticationChallenge();

        if (challenge == null && service.getParent() != null) {
            return getAuthenticationChallenge(request, service.getParent());
        }
        return challenge;
    }

    public void setVrtxAuthSP(String vrtxAuthSP) {
        this.vrtxAuthSP = vrtxAuthSP;
    }

    public void setUioAuthIDP(String uioAuthIDP) {
        this.uioAuthIDP = uioAuthIDP;
    }

    public void setSpCookieHandlers(List<AuthenticationHandler> spCookieHandlers) {
        this.spCookieHandlers = spCookieHandlers;
    }

	public void setUioAuthSSO(String uioAuthSSO) {
		this.uioAuthSSO = uioAuthSSO;
	}
}
