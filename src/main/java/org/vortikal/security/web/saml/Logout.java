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
package org.vortikal.security.web.saml;

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
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.security.AuthenticationProcessingException;
import org.vortikal.security.web.SecurityInitializer;
import org.vortikal.web.InvalidRequestException;
import org.vortikal.web.service.Assertion;
import org.vortikal.web.service.Service;
import org.vortikal.web.service.URL;

public class Logout extends SamlService {

    private Service redirectService;
    private SecurityInitializer securityInitializer;

    private String ieCookieTicket;
    private String vrtxAuthSP;
    private String uioAuthIDP;
    private String uioAuthSSO;
    private IECookieStore iECookieStore;

    private Assertion manageAssertion;

    public void initiateLogout(HttpServletRequest request, HttpServletResponse response) {

        URL savedURL = URL.create(request);
        if (this.redirectService != null) {
            savedURL = this.redirectService.constructURL(savedURL.getPath());
        }

        if (SamlAuthenticationHandler.browserIsIE(request) && manageAssertion.matches(request, null, null)) {
            Map<String, String> cookieMap = new HashMap<String, String>();

            if (SamlAuthenticationHandler.getCookie(request, vrtxAuthSP) != null) {
                cookieMap.put(vrtxAuthSP, SamlAuthenticationHandler.getCookie(request, vrtxAuthSP).getValue());
            }
            if (SamlAuthenticationHandler.getCookie(request, uioAuthIDP) != null) {
                cookieMap.put(uioAuthIDP, SamlAuthenticationHandler.getCookie(request, uioAuthIDP).getValue());
            }
            if (SamlAuthenticationHandler.getCookie(request, uioAuthSSO) != null) {
                cookieMap.put(uioAuthSSO, SamlAuthenticationHandler.getCookie(request, uioAuthSSO).getValue());
            }
            String cookieTicket = iECookieStore.addToken(request, cookieMap).toString();
            savedURL.addParameter(ieCookieTicket, cookieTicket);
        }

        // Generate request ID, save in session
        UUID requestID = UUID.randomUUID();
        setRequestIDSessionAttribute(request, savedURL, requestID);

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

        UUID responseID = UUID.randomUUID();
        SamlConfiguration samlConfiguration = newSamlConfiguration(request);
        LogoutResponse logoutResponse = createLogoutResponse(samlConfiguration, logoutRequest, responseID);

        String relayState = request.getParameter("RelayState");

        String redirectURL = buildRedirectURL(logoutResponse, relayState, signingCredential);

        System.out.println("handleLogoutRequest" + " : " + redirectURL);

        // Remove authentication state
        this.securityInitializer.removeAuthState(request, response);

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

        UUID expectedRequestID = getRequestIDSessionAttribute(request, url);
        if (expectedRequestID == null) {
            throw new InvalidRequestException("Missing request ID attribute in session");
        }
        setRequestIDSessionAttribute(request, url, null);

        LogoutResponse logoutResponse = getLogoutResponse(request);
        logoutResponse.validate(true);

        this.securityInitializer.removeAuthState(request, response);

        System.out.println("handleLogoutResponse" + " : " + url.toString());

        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", url.toString());
    }

    private String urlToLogoutServiceForDomain(SamlConfiguration config, UUID requestID, String relayState) {
        LogoutRequest logoutRequest = createLogoutRequest(config, requestID);
        String url = buildSignedAndEncodedLogoutRequestUrl(logoutRequest, relayState);
        return url;
    }

    private LogoutRequest logoutRequestFromServletRequest(HttpServletRequest request) {
        BasicSAMLMessageContext<LogoutRequest, ?, ?> messageContext = new BasicSAMLMessageContext<LogoutRequest, SAMLObject, SAMLObject>();
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
        queryParams.add(new Pair<String, String>("SAMLResponse", message));
        queryParams.add(new Pair<String, String>("RelayState", relayState));

        if (signingCredential != null) {
            try {
                queryParams.add(new Pair<String, String>("SigAlg", enc
                        .getSignatureAlgorithmURI(signingCredential, null)));
                String sigMaterial = urlBuilder.buildQueryString();
                queryParams.add(new Pair<String, String>("Signature", enc.generateSignature(signingCredential, enc
                        .getSignatureAlgorithmURI(signingCredential, null), sigMaterial)));
            } catch (MessageEncodingException ex) {
                throw new AuthenticationProcessingException("Exception caught when encoding and signing parameters", ex);
            }
        }
        return urlBuilder.buildURL();
    }

    @Required
    public void setSecurityInitializer(SecurityInitializer securityInitializer) {
        this.securityInitializer = securityInitializer;
    }

    public void setRedirectService(Service redirectService) {
        this.redirectService = redirectService;
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

    public void setiECookieStore(IECookieStore iECookieStore) {
        this.iECookieStore = iECookieStore;
    }

    public void setManageAssertion(Assertion manageAssertion) {
        this.manageAssertion = manageAssertion;
    }
}
