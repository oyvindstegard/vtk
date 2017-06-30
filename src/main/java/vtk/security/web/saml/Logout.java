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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.opensaml.common.SAMLObject;
import org.opensaml.common.binding.BasicSAMLMessageContext;
import org.opensaml.saml2.binding.decoding.HTTPRedirectDeflateDecoder;
import org.opensaml.saml2.core.LogoutRequest;
import org.opensaml.saml2.core.LogoutResponse;
import org.opensaml.util.URLBuilder;
import org.opensaml.ws.message.encoder.MessageEncodingException;
import org.opensaml.ws.transport.http.HttpServletRequestAdapter;
import org.opensaml.xml.security.credential.Credential;
import org.opensaml.xml.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.security.AuthenticationProcessingException;
import vtk.security.SecurityContext;
import vtk.security.web.SecurityInitializer;
import vtk.web.InvalidRequestException;
import vtk.web.service.Assertion;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class Logout extends SamlService {

    private Service redirectService;

    private String ieCookieLogoutTicket;
    private IECookieStore iECookieStore;

    private Assertion manageAssertion;

    private static Logger authLogger = LoggerFactory.getLogger("vtk.security.web.AuthLog");

    public void initiateLogout(HttpServletRequest request, HttpServletResponse response) {

        URL savedURL = URL.create(request);
        if (this.redirectService != null) {
            savedURL = redirectService.urlConstructor(savedURL)
                    .withURI(savedURL.getPath())
                    .constructURL();
        }

        if (SamlAuthenticationHandler.browserIsIE(request) && manageAssertion.matches(request, null, null)) {
            if (authLogger.isDebugEnabled()) {
                authLogger.debug(request.getRemoteAddr() + " - request-URI: " + request.getRequestURI() + " - "
                        + "IE detected, initiating cookie removal");
            }
            Map<String, String> myMap = new HashMap<>();
            myMap.put("true", "true");
            String cookieTicket = iECookieStore.addToken(request, myMap).toString();
            savedURL.addParameter(ieCookieLogoutTicket, cookieTicket);
        }

        // Generate request ID, save in session
        final UUID requestID = UUID.randomUUID();
//        setRequestIDSessionAttribute(request, savedURL, requestID);
        storeRequestId(request, savedURL, requestID);

        String relayState = savedURL.toString();

        SamlConfiguration samlConfiguration = newSamlConfiguration(request);
        String url = urlToLogoutServiceForDomain(samlConfiguration, requestID, relayState);

        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", url.toString());
    }

    public void handleLogoutRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        LogoutRequest logoutRequest = logoutRequestFromServletRequest(request);

        // String statusCode = StatusCode.SUCCESS_URI;
        // String consent = null;
        // Issuer requestIssuer = logoutRequest.getIssuer();

        // verifyLogoutRequestIssuerIsSameAsLoginRequestIssuer(requestIssuer);
        Credential signingCredential = getSigningCredential();

        if (authLogger.isDebugEnabled()) {
            authLogger.debug(request.getRemoteAddr() + " - request-URI: " + request.getRequestURI() + " - "
                    + "handleLogoutRequest: " + request.getRemoteHost() + ":" + URL.create(request));
        }

        UUID responseID = UUID.randomUUID();
        SamlConfiguration samlConfiguration = newSamlConfiguration(request);
        LogoutResponse logoutResponse = createLogoutResponse(samlConfiguration, logoutRequest, responseID);

        String relayState = request.getParameter("RelayState");

        String redirectURL = buildRedirectURL(logoutResponse, relayState, signingCredential);

        // Remove authentication state
        SecurityContext securityContext = SecurityContext.getSecurityContext();
        SecurityInitializer initializer = securityContext.securityInitializer();
        if (initializer != null) initializer.removeAuthState(request, response);
        //this.securityInitializer.removeAuthState(request, response);

        // Handle the response ourselves.
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", redirectURL);
    }

    public boolean isLogoutRequest(HttpServletRequest request) {
        if (request.getParameter("SAMLRequest") == null) {
            return false;
        }
        return true;
    }

    public boolean isLogoutResponse(HttpServletRequest request) {
        if (request.getParameter("SAMLResponse") == null) {
            return false;
        }
        return true;
    }

    public void handleLogoutResponse(HttpServletRequest request, HttpServletResponse response) throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new InvalidRequestException("Not a logout request: missing session");
        }
        if (request.getParameter("SAMLResponse") == null) {
            throw new InvalidRequestException("Not a SAML logout request");
        }
        String relayState = request.getParameter("RelayState");
        if (relayState == null) {
            throw new InvalidRequestException("Missing RelayState parameter");
        }
        URL url = URL.parse(relayState);

//        UUID expectedRequestID = getRequestIDSessionAttribute(request, url);
        UUID expectedRequestID = getRequestId(request, url);
        if (expectedRequestID == null) {
            throw new InvalidRequestException("Missing request ID");
        }
//        setRequestIDSessionAttribute(request, url, null);
        removeRequestId(request, url);

        if (authLogger.isDebugEnabled()) {
            authLogger.debug(request.getRemoteAddr() + " - request-URI: " + request.getRequestURI() + " - "
                    + "handleLogoutResponse: " + request.getRemoteHost() + ":" + URL.create(request));
        }

        LogoutResponse logoutResponse = getLogoutResponse(request);
        logoutResponse.validate(true);

        SecurityContext securityContext = SecurityContext.getSecurityContext();
        SecurityInitializer initializer = securityContext.securityInitializer();
        if (initializer != null) initializer.removeAuthState(request, response);

        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", url.toString());
    }

    private String urlToLogoutServiceForDomain(SamlConfiguration config, UUID requestID, String relayState) {
        LogoutRequest logoutRequest = createLogoutRequest(config, requestID);
        String url = buildSignedAndEncodedLogoutRequestUrl(logoutRequest, relayState);
        return url;
    }

    private LogoutRequest logoutRequestFromServletRequest(HttpServletRequest request) {
        BasicSAMLMessageContext<LogoutRequest, ?, ?> messageContext = new BasicSAMLMessageContext<>();
        HttpServletRequestAdapter adapter = new HttpServletRequestAdapter(request);
        messageContext.setInboundMessageTransport(adapter);

        HTTPRedirectDeflateDecoder decoder = new HTTPRedirectDeflateDecoder();

        try {
            decoder.decode(messageContext);
        } catch (Exception e) {
            throw new InvalidRequestException("Invalid SAML request: unable to decode LogoutRequest", e);
        }
        LogoutRequest logoutRequest = messageContext.getInboundSAMLMessage();
        return logoutRequest;

    }

    private String buildRedirectURL(LogoutResponse logoutResponse, String relayState, Credential signingCredential)
            throws Exception {
        Encoder enc = new Encoder();
        String message = enc.deflateAndBase64Encode(logoutResponse);

        URLBuilder urlBuilder = new URLBuilder(logoutResponse.getDestination());

        List<Pair<String, String>> queryParams = urlBuilder.getQueryParams();
        queryParams.clear();
        queryParams.add(new Pair<>("SAMLResponse", message));
        queryParams.add(new Pair<>("RelayState", relayState));

        if (signingCredential != null) {
            try {
                queryParams.add(new Pair<>("SigAlg", enc
                        .getSignatureAlgorithmURI(signingCredential, null)));
                String sigMaterial = urlBuilder.buildQueryString();
                queryParams.add(new Pair<>("Signature", enc.generateSignature(signingCredential, enc
                        .getSignatureAlgorithmURI(signingCredential, null), sigMaterial)));
            } catch (MessageEncodingException ex) {
                throw new AuthenticationProcessingException("Exception caught when encoding and signing parameters", ex);
            }
        }
        return urlBuilder.buildURL();
    }

    public void setRedirectService(Service redirectService) {
        this.redirectService = redirectService;
    }

    public void setIeCookieLogoutTicket(String ieCookieLogoutTicket) {
        this.ieCookieLogoutTicket = ieCookieLogoutTicket;
    }

    public void setiECookieStore(IECookieStore iECookieStore) {
        this.iECookieStore = iECookieStore;
    }

    public void setManageAssertion(Assertion manageAssertion) {
        this.manageAssertion = manageAssertion;
    }
}
