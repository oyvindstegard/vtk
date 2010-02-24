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

import java.io.IOException;
import java.util.List;
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
import org.vortikal.web.service.URL;

public class Logout extends SamlService {

    private SecurityInitializer securityInitializer;
    
    public void initiateLogout(HttpServletRequest request, HttpServletResponse response) {
        URL savedURL = URL.create(request);
        request.getSession(true).setAttribute(URL_SESSION_ATTR, savedURL);
    
        // Generate request ID, save in session
        UUID requestID = UUID.randomUUID();
        request.getSession().setAttribute(REQUEST_ID_SESSION_ATTR, requestID);
        
        UUID relayState = UUID.randomUUID();
        SamlConfiguration samlConfiguration = newSamlConfiguration(request);
        String url = urlToLogoutServiceForDomain(samlConfiguration, requestID, relayState);
        try {
            response.sendRedirect(url);
        } catch (IOException e) {
            throw new AuthenticationProcessingException(e);
        }
    }
    
    public void handleLogoutRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        LogoutRequest logoutRequest = logoutRequestFromServletRequest(request);

//        String statusCode = StatusCode.SUCCESS_URI;
//        String consent = null;
//        Issuer requestIssuer = logoutRequest.getIssuer();

        // verifyLogoutRequestIssuerIsSameAsLoginRequestIssuer(requestIssuer);
        Credential signingCredential = getSigningCredential();

        UUID responseID = UUID.randomUUID();
        SamlConfiguration samlConfiguration = newSamlConfiguration(request);
        LogoutResponse logoutResponse = createLogoutResponse(samlConfiguration, logoutRequest, responseID);

        String relayState = request.getParameter("RelayState");
        
        String redirectURL = buildRedirectURL(logoutResponse, relayState, signingCredential);

        // Remove authentication state
        this.securityInitializer.removeAuthState();
        
        // Handle the response ourselves.
        request.getSession().invalidate();
        response.sendRedirect(redirectURL);
    }

    
    
    public void handleLogoutResponse(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (request.getParameter("SAMLResponse") == null) {
            throw new IllegalStateException("Not a SAML logout request");
        }
        UUID expectedRequestID = (UUID) request.getSession(true).getAttribute(REQUEST_ID_SESSION_ATTR);
        if (expectedRequestID == null) {
            throw new AuthenticationProcessingException("Missing request ID attribute in session");
        }
        request.getSession().removeAttribute(REQUEST_ID_SESSION_ATTR);

        LogoutResponse logoutResponse = getLogoutResponse(request);
        logoutResponse.validate(true);
        
        HttpSession session = request.getSession();
        if (session == null) {
            throw new AuthenticationProcessingException("No session exists, not a post-logout request");
        }
        
        URL url = (URL) session.getAttribute(URL_SESSION_ATTR);
        if (url == null) {
            throw new AuthenticationProcessingException("No URL session attribute exists, nowhere to redirect");
        }
        session.removeAttribute(URL_SESSION_ATTR);
        session.removeAttribute(REQUEST_ID_SESSION_ATTR);

        response.sendRedirect(url.toString());
    }
    
    private String urlToLogoutServiceForDomain(SamlConfiguration config, UUID requestID, UUID relayState) {
        LogoutRequest logoutRequest = createLogoutRequest(config, requestID);
        String url = buildSignedAndEncodedLogoutRequestUrl(logoutRequest, relayState);
        return url;
    }

    
    private LogoutRequest logoutRequestFromServletRequest(HttpServletRequest request) {
        BasicSAMLMessageContext<LogoutRequest, ?, ?> messageContext = new BasicSAMLMessageContext<LogoutRequest, SAMLObject, SAMLObject>();
        messageContext.setInboundMessageTransport(new HttpServletRequestAdapter(request));

        HTTPRedirectDeflateDecoder decoder = new HTTPRedirectDeflateDecoder();

        try {
            decoder.decode(messageContext);
        } catch (Exception e) {
            throw new AuthenticationProcessingException("Unable to decode LogoutResponse.", e);
        }
        LogoutRequest logoutRequest = messageContext.getInboundSAMLMessage();
        return logoutRequest;

    }
    
    private String buildRedirectURL(LogoutResponse logoutResponse, String relayState, Credential signingCredential) throws Exception {
        Encoder enc = new Encoder();
        String message = enc.deflateAndBase64Encode(logoutResponse);
        
        URLBuilder urlBuilder = new URLBuilder(logoutResponse.getDestination());

        List<Pair<String, String>> queryParams = urlBuilder.getQueryParams();
        queryParams.clear();
        queryParams.add(new Pair<String, String>("SAMLResponse", message));
        queryParams.add(new Pair<String, String>("RelayState", relayState));

        if (signingCredential != null) {
            try {
                queryParams.add(new Pair<String, String>("SigAlg", enc.getSignatureAlgorithmURI(signingCredential, null)));
                String sigMaterial = urlBuilder.buildQueryString();
                queryParams.add(new Pair<String, String>("Signature", enc.generateSignature(signingCredential, enc.getSignatureAlgorithmURI(signingCredential, null), sigMaterial)));
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
    
}
