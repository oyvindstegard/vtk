/* Copyright (c) 2010, University of Oslo, Norway
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
package vtk.security.web.saml;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Path;
import vtk.security.AuthenticationException;
import vtk.security.AuthenticationProcessingException;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;
import vtk.security.web.AuthenticationChallenge;
import vtk.security.web.AuthenticationHandler;
import vtk.security.web.InvalidAuthenticationRequestException;
import vtk.util.web.HttpUtil;
import vtk.web.InvalidRequestException;
import vtk.web.RequestContext;
import vtk.web.service.WebAssertion;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * SAML authentication handler.
 */
public class SamlAuthenticationHandler implements AuthenticationChallenge, AuthenticationHandler,
        Controller, ApplicationEventPublisherAware {

    private String identifier;
    private Challenge challenge;
    private Login login;
    private Logout logout;
    private LostPostHandler postHandler;

    private String spCookieDomain = null;

    private String ieCookieTicket;

    private String vrtxAuthSP;

    private String uioAuthIDP;

    private String uioAuthSSO;

    private String ieReturnURL;

    private Map<String, String> staticHeaders = new HashMap<>();

    private PrincipalFactory principalFactory;

    private static Logger logger = LoggerFactory.getLogger(SamlAuthenticationHandler.class);

    private static Logger authLogger = LoggerFactory.getLogger("vtk.security.web.AuthLog");

    private IECookieStore iECookieStore;
    
    private static boolean ieCookieHandlingEnabled;

    private Service redirectToViewService;

    private Service redirectToAdminService;

    private String ieCookieSetterURI;

    private WebAssertion manageAssertion;

    private String backstepParameter = "backsteps";

    private final Logger authDebugLog = LoggerFactory.getLogger("vtk.security.web.AUTH_DEBUG");

    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void challenge(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationProcessingException, ServletException, IOException {
        if ("POST".equals(request.getMethod())) {
            this.postHandler.saveState(request, response);

            // Debugging VTK-2653
            if (authDebugLog.isDebugEnabled()) {
                authDebugLog.debug("Saved lost POST state"
                        + (request.getParameter("RelayState") != null ? ", RelayState: " + request.getParameter("RelayState") : "")
                        + ", requestURL: " + request.getRequestURL() + (request.getQueryString() != null ? "?" + request.getQueryString() : "")
                        + ", remote addr: " + request.getRemoteAddr());
            }
        }
        this.challenge.challenge(request, response);
        setHeaders(response);
    }

    @Override
    public boolean isRecognizedAuthenticationRequest(HttpServletRequest req) throws AuthenticationProcessingException,
            InvalidAuthenticationRequestException {
        return this.login.isLoginResponse(req);
    }

    /**
     * Performs the authentication based on the SAMLResponse request parameter
     */
    @Override
    public AuthResult authenticate(HttpServletRequest request) throws AuthenticationProcessingException,
            AuthenticationException, InvalidAuthenticationRequestException {

        if (this.login.isUnsolicitedLoginResponse(request)) {
            this.challenge.prepareUnsolicitedChallenge(request);
            authLogger.debug(request.getRemoteAddr() + " - request-URI: " + request.getRequestURI() + " - "
                    + "Unsolicited authentication request: " + request);
            throw new AuthenticationException("Unsolicited authentication request: " + request);
        }

        UserData userData = this.login.login(request);
        if (userData == null) {
            authLogger.debug(request.getRemoteAddr() + " - request-URI: " + request.getRequestURI() + " - "
                    + "Unable to authenticate request " + request);
            throw new AuthenticationException("Unable to authenticate request " + request);
        }
        String id = userData.getUsername();
        Principal principal = this.principalFactory.getPrincipal(id, Principal.Type.USER);

        publishLoginEvent(request, principal, userData);

        return new AuthResult(principal.getQualifiedName());
    }

    /**
     * Does a redirect to the original resource after a successful authentication
     */
    @Override
    public boolean postAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationProcessingException, InvalidAuthenticationRequestException {

        Random generator = new Random();
        String currentTime = String.valueOf(new Date().getTime() + generator.nextInt(120000));
        Cookie ssoCookie = new Cookie(uioAuthSSO, currentTime);
        ssoCookie.setPath("/");

        if (this.spCookieDomain != null) {
            ssoCookie.setDomain(this.spCookieDomain);
        }

        response.addCookie(ssoCookie);

        if (logger.isDebugEnabled()) {
            logger.debug("Setting cookie: " + ssoCookie + ": " + this.getIdentifier());
        }

        if (this.postHandler.hasSavedState(request)) {
            // Debugging VTK-2653
            if (authDebugLog.isDebugEnabled()) {
                authDebugLog.debug("postAuthentication: Replaying lost POST for requestURL: "
                + request.getRequestURL() + (request.getQueryString() != null ? "?" + request.getQueryString() : "")
                + ", remote addr: " + request.getRemoteAddr());
            }
            
            this.postHandler.redirect(request, response);
            setHeaders(response);
            return true;
        }

        if (browserIsIE(request)) {
            Map<String, String> cookieMap = new HashMap<>();

            URL resourceURL = this.login.getRelayStateURL(request);

            resourceURL.removeParameter("authTicket");

            boolean inManageMode = false;
            if (manageAssertion.matches(request, null, null)) {
                inManageMode = true;
            }

            String backstepValue = request.getParameter(backstepParameter);

            if (backstepValue != null && !inManageMode) {
                if (resourceURL.getParameter("authTarget") == null) {
                    resourceURL.addParameter("authTarget", "https");
                }
                resourceURL.addParameter(backstepParameter, backstepValue);
            }

            cookieMap.put(uioAuthIDP, this.getIdentifier());
            cookieMap.put(vrtxAuthSP, this.getIdentifier());
            cookieMap.put(uioAuthSSO, ssoCookie.getValue());
            cookieMap.put(ieReturnURL, resourceURL.toString());

            String cookieTicket = iECookieStore.addToken(request, cookieMap).toString();


            URL currentURL = null;
            if (inManageMode) {
                currentURL = redirectToViewService.urlConstructor(URL.create(request))
                        .withURI(Path.fromString(ieCookieSetterURI))
                        .constructURL();
            }
            else {
                currentURL = redirectToAdminService.urlConstructor(URL.create(request))
                        .withURI(Path.fromString(ieCookieSetterURI))
                        .constructURL();
            }
            currentURL.setProtocol("https");
            currentURL.addParameter(ieCookieTicket, cookieTicket);

            if (authLogger.isDebugEnabled()) {
                authLogger.debug(request.getRemoteAddr() + " - request-URI: " + request.getRequestURI() + " - "
                        + "Cookie-setter redirecting to: " + currentURL);
            }

            try {
                currentURL.addParameter(ieReturnURL, URLEncoder.encode(resourceURL.toString(), "UTF-8"));
                setHeaders(response);
                response.sendRedirect(currentURL.toString());
            } catch (UnsupportedEncodingException ue) {
                ue.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.login.redirectAfterLogin(request, response);
        setHeaders(response);
        return true;
    }

    public static boolean browserIsIE(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (ieCookieHandlingEnabled
                && (userAgent != null && (userAgent.contains("MSIE 7.0") || (userAgent.contains("MSIE") && userAgent
                        .contains("Trident"))))) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Initiates logout process with IDP
     */
    @Override
    public boolean logout(Principal principal, HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationProcessingException, ServletException, IOException {
        publishLogoutEvent(request, principal);

        removeCookies(request, response);

        this.logout.initiateLogout(request, response);

        setHeaders(response);
        return true;
    }

    private void removeCookies(HttpServletRequest request, HttpServletResponse response) {
        HttpUtil.getCookie(request, uioAuthSSO).ifPresent(c -> {
            if (logger.isDebugEnabled()) {
                logger.debug("Deleting cookie " + uioAuthSSO);
            }
            Cookie deleteCookie = new Cookie(c.getName(), c.getValue());
            deleteCookie.setPath("/");
            if (this.spCookieDomain != null) {
                deleteCookie.setDomain(this.spCookieDomain);
            }
            deleteCookie.setMaxAge(0);
            response.addCookie(deleteCookie);
        });

        for (String cookieName : new String[]{vrtxAuthSP, uioAuthIDP}) {
            HttpUtil.getCookie(request, cookieName).ifPresent(c -> {
                if (logger.isDebugEnabled()) {
                    logger.debug("Deleting cookie " + cookieName);
                }
                Cookie deleteCookie = new Cookie(c.getName(), c.getValue());
                deleteCookie.setSecure(true);
                deleteCookie.setPath("/");
                if (this.spCookieDomain != null) {
                    deleteCookie.setDomain(this.spCookieDomain);
                }
                deleteCookie.setMaxAge(0);
                response.addCookie(deleteCookie);
            });
        }
    }

    /**
     * Handles incoming logout requests (originated from IDP) and responses (from IDP based on request from us)
     */
    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (request.getParameter("SAMLResponse") == null && request.getParameter("SAMLRequest") == null) {
            throw new InvalidRequestException(
                    "Invalid SAML request: expected one of 'SAMLRequest' or 'SAMLResponse' parameters");
        }
        URL url = null;

        if (login.isLoginResponse(request)) {
            // User typically hit 'back' button after logging in and got sent here from IDP:
            String relayState = request.getParameter("RelayState");
            if (relayState != null) {
                url = URL.parse(relayState);
            }
            if (url != null) {
                if (url.getParameter("authTicket") != null) {
                    url.removeParameter("authTicket");
                }
                response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
                response.setHeader("Location", url.toString());
                setHeaders(response);
                return null;
            }
        } else if (logout.isLogoutRequest(request)) {
            // Logout request from IDP (based on some other SP's request)

            // In this state, Vortex should normally have a user token stored
            final Principal principal = RequestContext.getRequestContext(request).getPrincipal();
            if (principal != null) {
                publishLogoutEvent(request, principal);
            }

            this.logout.handleLogoutRequest(request, response);

            setHeaders(response);
            return null;
        } else if (logout.isLogoutResponse(request)) {
            // Logout response (based on our request) from IDP

            // Local LogoutEvent has already been published earlier in the logout() method of this class

            this.logout.handleLogoutResponse(request, response);

            setHeaders(response);
            return null;
        }
        // Request is neither logout request nor logout response nor login response.
        throw new InvalidRequestException("Invalid SAML request: ");
    }

    private void publishLoginEvent(HttpServletRequest request, Principal principal, UserData userData) {
        logger.debug("SAML login event for principal {} on host {}", principal.getQualifiedName(), request.getHeader("Host"));
        applicationEventPublisher.publishEvent(new SamlLoginEvent(this, request, principal, userData));
    }

    private void publishLogoutEvent(HttpServletRequest request, Principal principal) {
        logger.debug("SAML logout event for principal {} on host {}", principal.getQualifiedName(), request.getHeader("Host"));
        applicationEventPublisher.publishEvent(new SamlLogoutEvent(this, request, principal));
    }

    @Override
    public AuthenticationChallenge getAuthenticationChallenge() {
        return this;
    }

    @Override
    public boolean isLogoutSupported() {
        return true;
    }

    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }

    @Required
    public void setChallenge(Challenge challenge) {
        this.challenge = challenge;
    }

    @Required
    public void setLogin(Login login) {
        this.login = login;
    }

    @Required
    public void setLogout(Logout logout) {
        this.logout = logout;
    }

    @Required
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public void setSpCookieDomain(String spCookieDomain) {
        if (spCookieDomain != null && !"".equals(spCookieDomain.trim())) {
            this.spCookieDomain = spCookieDomain;
        }
    }

    public void setIeCookieTicket(String ieCookieTicket) {
        this.ieCookieTicket = ieCookieTicket;
    }

    public void setVrtxAuthSP(String vrtxAuthSP) {
        this.vrtxAuthSP = vrtxAuthSP;
    }

    public void setUioAuthIDP(String uioAuthIDP) {
        this.uioAuthIDP = uioAuthIDP;
    }

    public void setUioAuthSSO(String uioAuthSSO) {
        this.uioAuthSSO = uioAuthSSO;
    }

    public void setIeReturnURL(String ieReturnURL) {
        this.ieReturnURL = ieReturnURL;
    }

    @Required
    public void setPostHandler(LostPostHandler postHandler) {
        this.postHandler = postHandler;
    }

    public void setStaticHeaders(Map<String, String> staticHeaders) {
        this.staticHeaders = staticHeaders;
    }

    public String getIdentifier() {
        return this.identifier;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + ":" + this.identifier;
    }

    private void setHeaders(HttpServletResponse response) {
        for (String header : this.staticHeaders.keySet()) {
            response.setHeader(header, this.staticHeaders.get(header));
        }
    }

    public void setiECookieStore(IECookieStore iECookieStore) {
        this.iECookieStore = iECookieStore;
    }

    public void setIeCookieHandlingEnabled(boolean ieCookieHandlingEnabled) {
        this.ieCookieHandlingEnabled = ieCookieHandlingEnabled;
    }

    public void setRedirectToViewService(Service redirectToViewService) {
        this.redirectToViewService = redirectToViewService;
    }

    public void setRedirectToAdminService(Service redirectToAdminService) {
        this.redirectToAdminService = redirectToAdminService;
    }

    public void setIeCookieSetterURI(String ieCookieSetterURI) {
        this.ieCookieSetterURI = ieCookieSetterURI;
    }

    public void setManageAssertion(WebAssertion manageAssertion) {
        this.manageAssertion = manageAssertion;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

}
