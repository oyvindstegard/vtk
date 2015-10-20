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

import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Response;
import org.opensaml.util.storage.MapBasedStorageService;
import org.opensaml.util.storage.ReplayCache;
import org.opensaml.util.storage.ReplayCacheEntry;
import org.opensaml.util.storage.StorageService;

import vtk.security.AuthenticationException;
import vtk.security.AuthenticationProcessingException;
import vtk.web.InvalidRequestException;
import vtk.web.service.URL;

public class Login extends SamlService {

    private static Log logger = LogFactory.getLog(Login.class);

    private static Log authLogger = LogFactory.getLog("vtk.security.web.AuthLog");

    int replayMinutes = 60;
    private StorageService<String, ReplayCacheEntry> replayStorage = new MapBasedStorageService<String, ReplayCacheEntry>();
    private ReplayCache replayCache = new ReplayCache(replayStorage, 60 * 1000 * replayMinutes);

    private String urlSessionAttribute = null;

    public void setUrlSessionAttribute(String urlSessionAttribute) {
        if (urlSessionAttribute != null && !"".equals(urlSessionAttribute.trim())) {
            this.urlSessionAttribute = urlSessionAttribute;
        }
    }

    public boolean isLoginResponse(HttpServletRequest req) {
        URL url = URL.create(req);
        if (!url.getPath().equals(getServiceProviderURI())) {
            return false;
        }
        if (!"POST".equals(req.getMethod())) {
            return false;
        }
        if (req.getParameter("SAMLResponse") == null) {
            return false;
        }
        if (req.getParameter("RelayState") == null) {
            return false;
        }
        try {
            Response samlResponse = decodeSamlResponse(req.getParameter("SAMLResponse"));
            List<Assertion> assertions = samlResponse.getAssertions();
            if (assertions == null || assertions.isEmpty()) {
                return false;
            }
            return true;
        } catch (Throwable t) {
            //if (logger.isDebugEnabled()) {
                logger.warn("Failed to decode SAML response in request " + req, t);
            //}
        }
        return false;
    }

    public boolean isUnsolicitedLoginResponse(HttpServletRequest req) {
        // XXX: Check for responseID here ?  Currently checked in #login(HttpServletRequest) using requestID
        return isLoginResponse(req) && req.getSession(false) == null;
    }

    public UserData login(HttpServletRequest request) throws AuthenticationException, AuthenticationProcessingException {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new InvalidRequestException("No session exists");
        }
        String relayState = request.getParameter("RelayState");
        URL url = URL.parse(relayState);
//        UUID expectedRequestID = getRequestIDSessionAttribute(request, url);
        UUID expectedRequestID = getRequestId(request, url);
        if (expectedRequestID == null) {
            throw new InvalidRequestException("Missing request ID"
                    + " for authentication attempt: " + relayState 
                    + ", session: " + session.getId()
                    + ", user agent: " + request.getHeader("User-Agent"));
        }

//        setRequestIDSessionAttribute(request, url, null);
        removeRequestId(request, url);

        UserData userData = getUserData(request, expectedRequestID);
        if (userData == null) {
            throw new AuthenticationException("Unable to authenticate request " + request);
        }
        if (logger.isDebugEnabled()) {
            debugLogin(userData);
        }
        return userData;
    }

    public void redirectAfterLogin(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationProcessingException {
        // Get original request URL (saved in challenge()) from session, redirect to it
        String relayState = request.getParameter("RelayState");
        if (relayState == null) {
            throw new InvalidRequestException("Missing RelayState parameter");
        }
        URL url = URL.parse(relayState);
        if (this.urlSessionAttribute != null) {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute(this.urlSessionAttribute) != null) {
                url = (URL) session.getAttribute(this.urlSessionAttribute);
            }
        }

        if (url.getParameter("authTicket") != null) {
            url.removeParameter("authTicket");
        }

        response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
        response.setHeader("Location", url.toString());
    }

    public URL getRelayStateURL(HttpServletRequest request) {
        // Get original request URL (saved in challenge()) from session, redirect to it
        String relayState = request.getParameter("RelayState");
        if (relayState == null) {
            throw new InvalidRequestException("Missing RelayState parameter");
        }
        URL url = URL.parse(relayState);
        if (this.urlSessionAttribute != null) {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute(this.urlSessionAttribute) != null) {
                url = (URL) session.getAttribute(this.urlSessionAttribute);
            }
        }
        return url;
    }

    UserData getUserData(HttpServletRequest request, UUID expectedRequestID) {
        String encodedSamlResponseString = request.getParameter("SAMLResponse");

        Response samlResponse = decodeSamlResponse(encodedSamlResponseString);

        checkReplay(samlResponse);

        String inResponseToID = samlResponse.getInResponseTo();
        if (!expectedRequestID.toString().equals(inResponseToID)) {
            authLogger.debug(request.getRemoteAddr() + " - request-URI: " + request.getRequestURI() + " - "
                    + "Request IDs not equal - expectedReqID: " + expectedRequestID + " inresponseToID: "
                    + inResponseToID);

            throw new AuthenticationException("Request ID mismatch");
            // throw new InvalidRequestException("Request ID mismatch");
        }
        authLogger.debug(request.getRemoteAddr() + " - request-URI: " + request.getRequestURI() + " - "
                + "Request IDs are equal - expectedReqID: " + expectedRequestID + " inresponseToID: "
                + inResponseToID);

        verifyStatusCodeIsSuccess(samlResponse);
        verifyDestinationAddressIsCorrect(samlResponse);

        Assertion assertion = samlResponse.getAssertions().get(0);
        verifyCryptographicAssertionSignature(assertion);
        validateAssertionContent(assertion);

        return new UserData(assertion);

    }

    public void setReplayMinutes(int replayMinutes) {
        if (replayMinutes <= 0) {
            throw new IllegalArgumentException("Replay cache minutes must be greater than 0");
        }
        this.replayMinutes = replayMinutes;
    }

    private void checkReplay(Response response) {
        boolean replay = this.replayCache.isReplay(response.getIssuer().getValue(), response.getID());
        if (replay) {
            throw new InvalidRequestException("Replay attempt discovered");
        }
        response.getIssuer();
        response.getID();
    }

    private void debugLogin(UserData userData) {
        StringBuilder message = new StringBuilder("Login: " + userData.getUsername());
        message.append(", attributes: [");
        String separator = "";
        for (String attr : userData.getAttributeNames()) {
            message.append(separator).append(attr).append(":");
            message.append(userData.getAttribute(attr));
            if ("".equals(separator)) {
                separator = ",";
            }
        }
        message.append("]");
        logger.debug(message);
    }
}
