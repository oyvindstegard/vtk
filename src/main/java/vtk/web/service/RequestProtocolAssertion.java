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
package vtk.web.service;

import javax.servlet.http.HttpServletRequest;

import org.springframework.util.StringUtils;

import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.web.RequestContext;


/**
 * Assertion on the URL protocol fragment.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <lI><code>protocol</code> - the name of the protocol. Legal
 *   values are <code>*</code> (match any), <code>http</code> and
 *   <code>https</code>. A comma separated list of values is accepted.
 * </ul>
 */
public class RequestProtocolAssertion implements Assertion {
	
    private final static String PROTO_HTTP = "http";
    private final static String PROTO_HTTPS = "https";
    private final static String PROTO_ANY = "*";
    
    private boolean preferRequestProtocol = false;


    private String[] protocols;

    public void setProtocol(String protocol) {
        if (protocol == null || protocol.trim().equals("")) {
            throw new IllegalArgumentException("Illegal protocol value: '" + protocol + "'");
        }
        
        this.protocols = StringUtils.tokenizeToStringArray(protocol, ", ");
        if (protocols.length == 0) {
            throw new IllegalArgumentException(
                "Unable to find protocol in argument: '" + protocol + "'");
        }
        for (String proto: protocols) {
            if (PROTO_HTTP.equals(proto) || PROTO_HTTPS.equals(proto)
                || PROTO_ANY.equals(proto)) {
                continue;
            }
            throw new IllegalArgumentException("Illegal protocol value: '" + protocol + "'");
        }
    }
    
    
    public void setPreferRequestProtocol(boolean preferRequestProtocol) {
        this.preferRequestProtocol = preferRequestProtocol;
    }
    
    @Override
    public boolean conflicts(Assertion assertion) {
        if (assertion instanceof RequestProtocolAssertion) {
            boolean conflict = true;

           for (String proto: protocols) {
               if (PROTO_ANY.equals(proto)) {
                    conflict = false;
                    break;
                }
                String[] otherProtocols = ((RequestProtocolAssertion)assertion).protocols;
                for (String otherProto: otherProtocols) {
                    if (PROTO_ANY.equals(otherProto)) {
                        conflict = false;
                        break;
                    }

                    if (proto.equals(otherProto)) {
                        conflict = false;
                        break;
                    }
                }
            }
           return conflict;
        }
        return false;
    }

    @Override
    public void processURL(URL url) {
        RequestContext requestContext = RequestContext.getRequestContext();
        if (requestContext != null && preferRequestProtocol) {
            String requestProtocol = getProtocol(requestContext.getServletRequest());

            for (String proto: protocols) {
                if (PROTO_ANY.equals(proto)) {
                    url.setProtocol(requestProtocol);
                    break;
                }
                if (PROTO_HTTPS.equals(requestProtocol)) {
                    // We are already on https, so stay there
                    url.setProtocol(PROTO_HTTPS);
                    break;
                }
                if (proto.equals(requestProtocol)) {
                    url.setProtocol(requestProtocol);
                    break;
                }
            }
        }
        else {
            boolean set = false;
            for (String proto: protocols) {

                if (!PROTO_ANY.equals(proto)) {
                    url.setProtocol(proto);
                    set = true;
                    break;
                }
            }
            if (!set) {
                if (requestContext != null) {
                    String requestProtocol = getProtocol(requestContext.getServletRequest());
                    url.setProtocol(requestProtocol);
                } else {
                    url.setProtocol(PROTO_HTTP);
                }
            }

        }
        
    }
    
    @Override
    public boolean processURL(URL url, Resource resource,
                              Principal principal, boolean match) {
        processURL(url);
        return true;
    }


    @Override
    public boolean matches(HttpServletRequest request, Resource resource,
                           Principal principal) {

        String requestProtocol = getProtocol(request);

        for (String proto: protocols) {
            if (PROTO_ANY.equals(proto)) {
                return true;
            }
            if (preferRequestProtocol && PROTO_HTTPS.equals(requestProtocol)) {
                // We are on https, so allow it
                return true;
            }
            if (proto.equals(requestProtocol)) {
                return true;
            }
        }
            
        return false;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("request.protocol in (");
        for (int i = 0; i < protocols.length; i++) {
            sb.append(protocols[i]);
            if (i < protocols.length - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }

    private String getProtocol(HttpServletRequest request) {
        return request.isSecure() ? PROTO_HTTPS : PROTO_HTTP;
    }
}
