/* Copyright (c) 2007, University of Oslo, Norway All rights reserved.
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
package vtk.security.web.openid;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.VerificationResult;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.discovery.Identifier;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.ParameterList;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.core.Ordered;

import vtk.repository.Path;
import vtk.security.AuthenticationException;
import vtk.security.AuthenticationProcessingException;
import vtk.security.Principal;
import vtk.security.web.AuthenticationChallenge;
import vtk.security.web.AuthenticationHandler;
import vtk.web.InvalidRequestException;
import vtk.web.service.Service;
import vtk.web.service.URL;


public class OpenIDAuthenticationHandler
  implements AuthenticationHandler, AuthenticationChallenge, Ordered, InitializingBean {

    private static final String DISCOVERY_SESSION_ATTRIBUTE = OpenIDAuthenticationHandler.class.getName() + ".discovered";
    private static final String ORIGINAL_URI_SESSION_ATTRIBUTE = OpenIDAuthenticationHandler.class.getName() + ".original_uri";
    
    private static Logger logger = LoggerFactory.getLogger(OpenIDAuthenticationHandler.class);

    private String identifier;
    
    private int order = Integer.MAX_VALUE;
    
    private ConsumerManager consumerManager;
    private Service formService;
    private Service openIDAuthenticationService;
    
    private Set<?> categories = Collections.EMPTY_SET;

    
    public void setConsumerManager(ConsumerManager consumerManager) {
        this.consumerManager = consumerManager;
    }
    
    public void setOpenIDAuthenticationService(Service openIDAuthenticationService) {
        this.openIDAuthenticationService = openIDAuthenticationService;
    }

    public void setFormService(Service formService) {
        this.formService = formService;
    }

    public void setOrder(int order) {
        this.order = order;
    }
    
    public int getOrder() {
        return this.order;
    }

    public void setCategories(Set<?> categories) {
        this.categories = categories;
    }

    public void afterPropertiesSet() {
        if (this.consumerManager == null) {
            throw new BeanInitializationException(
                "JavaBean property 'consumerManager' not set.");
        }
        if (this.openIDAuthenticationService == null) {
            throw new BeanInitializationException(
                "JavaBean property 'openIDAuthenticationService' not set.");
        }
        if (this.formService == null) {
            throw new BeanInitializationException(
                "JavaBean property 'formService' not set.");
        }
    }


    public boolean isLogoutSupported() {
        return true;
    }


    public boolean logout(Principal principal, HttpServletRequest req,
                          HttpServletResponse resp) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.removeAttribute(DISCOVERY_SESSION_ATTRIBUTE);
            session.removeAttribute(ORIGINAL_URI_SESSION_ATTRIBUTE);
        }
        return false;
    }


    public AuthenticationChallenge getAuthenticationChallenge() {
        return this;
    }


    public boolean isRecognizedAuthenticationRequest(HttpServletRequest request) {

        String openidResponse = request.getParameter("openid_response");
        if (openidResponse != null) {
            return true;
        }

        if (!"POST".equals(request.getMethod())) {
            return false;
        }
        String identifier = request.getParameter("openid_identifier");
        return identifier != null;
    }

    public void challenge(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(true);
        String identifier = request.getParameter("openid_identifier");

        if (identifier == null) {
            // Save original URL in session:
            URL originalURL = URL.create(request);
            session.setAttribute(ORIGINAL_URI_SESSION_ATTRIBUTE, originalURL);
            String redirectURL = this.formService.constructLink(originalURL.getPath());
                try {
                    response.sendRedirect(redirectURL);
                } catch (Exception e) {
                    throw new AuthenticationProcessingException("Unable to redirect to form service", e);
                }
            return;
        }

        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("openid_response", "true");
        String returnURL = this.openIDAuthenticationService.constructLink(
            Path.fromString(request.getRequestURI()), parameters);

        // perform discovery on the user-supplied identifier
        List<?> discoveries;

        try {
            discoveries = this.consumerManager.discover(identifier);
        } catch (Exception e) {
            throw new AuthenticationProcessingException(
                "Failed to perform discovery on identifier '" + identifier + "'", e);
        }

        // attempt to associate with the OpenID provider
        // and retrieve one service endpoint for authentication
        DiscoveryInformation discovered = this.consumerManager.associate(discoveries);

        // store the discovery information in the user's session for later use
        session.setAttribute(DISCOVERY_SESSION_ATTRIBUTE, discovered);

        try {
            // obtain a AuthRequest message to be sent to the OpenID provider
            AuthRequest authReq = this.consumerManager.authenticate(discovered, returnURL);
            String redirURL = authReq.getDestinationUrl(true);
            response.sendRedirect(redirURL);
        } catch (Exception e) {
            throw new AuthenticationProcessingException("Unable to redirect to OpenID URL", e);
        }
    }



    public AuthResult authenticate(HttpServletRequest request)
        throws AuthenticationException {

        if (request.getParameter("openid_response") == null) {
            throw new AuthenticationException();
        }
        if (logger.isDebugEnabled()) {
            logger.debug("openid_response: " + request.getParameter("openid_response"));
        }

        // extract the parameters from the authentication response
        // (which comes in as a HTTP request from the OpenID provider)
        ParameterList openidResp = new ParameterList(request.getParameterMap());

        // retrieve the previously stored discovery information
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new InvalidRequestException("No session exists");
        }
        DiscoveryInformation discovey = (DiscoveryInformation)
            session.getAttribute(DISCOVERY_SESSION_ATTRIBUTE);
        if (discovey == null) {
            throw new InvalidRequestException("No discovery information in session");
        }
        session.removeAttribute(DISCOVERY_SESSION_ATTRIBUTE);

        // extract the receiving URL from the HTTP request
        StringBuffer receivingURL = request.getRequestURL();
        String queryString = request.getQueryString();
        if (queryString != null && queryString.length() > 0) {
            receivingURL.append("?").append(request.getQueryString());
        }
        // verify the response

        VerificationResult verification;
        try {
            verification = this.consumerManager.verify(
                receivingURL.toString(), openidResp, discovey);
        } catch (Exception e) {
            throw new AuthenticationProcessingException(
                "Failed to verify authentication request", e);
        }

        // examine the verification result and extract the verified identifier
        Identifier verified = verification.getVerifiedId();

        if (verified == null) {
            throw new AuthenticationException();
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Authenticated principal: " + verified.getIdentifier());
        }
        return new AuthResult(verified.getIdentifier());
    }


    public boolean postAuthentication(HttpServletRequest request,
                                      HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        URL redirectURL = (URL) session.getAttribute(ORIGINAL_URI_SESSION_ATTRIBUTE);
        try {
            session.removeAttribute(ORIGINAL_URI_SESSION_ATTRIBUTE);
            if (logger.isDebugEnabled()) {
                logger.debug("Redirect after authentication: " + redirectURL);
            }
            response.sendRedirect(redirectURL.toString());
            return true;
        } catch (Exception e) {
            throw new AuthenticationProcessingException("Unable to redirect to URL: '"
                                                        + redirectURL + "'", e);
        }
    }

    public String getIdentifier() {
        return this.identifier;
    }
    
    @Required
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

}
