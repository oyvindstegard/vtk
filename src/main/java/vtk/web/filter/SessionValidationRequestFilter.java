/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.web.filter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.web.servlet.AbstractServletFilter;

/**
 * Provides a minimum of protection against session hijacking.
 * Upon session creation the client address is stored in the session, 
 * and this address is validated against the request address on every call 
 * to <code>getSession()</code>. If the two addresses do not match, the 
 * session is invalidated. 
 * 
 * <p>In case of a session hijack attempt, using this filter may 
 * cause the valid session to be invalidated (servlet container dependent). 
 * This is unfortunate, but still better than allowing an attacker to steal 
 * the session.</p>
 * 
 * <p>Of course, this filter does not offer any protection against IP spoofing, 
 * nor does it work if both clients are behind the same IP.</p>
 */
public class SessionValidationRequestFilter extends AbstractServletFilter {

    private static final Logger logger = LoggerFactory.getLogger(SessionValidationRequestFilter.class);
    private Set<String> authorizedAddresses = new HashSet<>();
    
    public SessionValidationRequestFilter(Set<String> authorizedAddresses) {
        this.authorizedAddresses = new HashSet<>(Objects.requireNonNull
                (authorizedAddresses, "authorizedAddresses cannot be null"));
    }
    
    @Override
    protected void doFilter(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String clientAddress = request.getRemoteAddr();
        if (this.authorizedAddresses.contains(clientAddress)) {
            chain.doFilter(request, response);
        }
        else {
            chain.doFilter(new RequestWrapper(request), response);
        }
    }
    
    private static class RequestWrapper extends HttpServletRequestWrapper {
        private static final String CLIENT_ADDR_SESSION_ATTRIBUTE = 
            RequestWrapper.class.getName() + ".clientAddrAttribute";

        private HttpServletRequest request;

        public RequestWrapper(HttpServletRequest request) {
            super(request);
            this.request = request;
        }
        
        @Override
        public HttpSession getSession() {
            return getSession(false);
        }

        @Override
        public HttpSession getSession(boolean create) {
            boolean existed = this.request.getSession(false) != null;
            HttpSession s = this.request.getSession(create);

            if (s == null) {
                return null;
            }
            
            String clientAddress = this.request.getRemoteAddr();
            if (!existed) {
                s.setAttribute(CLIENT_ADDR_SESSION_ATTRIBUTE, clientAddress);
                return s;
            }

            Object o = s.getAttribute(CLIENT_ADDR_SESSION_ATTRIBUTE);
            if (o == null || ! (o instanceof String)) {
                logger.warn("Expected attribute of type string in session under key: " 
                            + CLIENT_ADDR_SESSION_ATTRIBUTE + ", found: " + o
                            + "; invalidating session");
                s.invalidate();
                s = this.request.getSession(create);
                if (s != null) {
                    s.setAttribute(CLIENT_ADDR_SESSION_ATTRIBUTE, clientAddress);
                }
                return s;
            }

            String originatingAddress = (String) o;
            if (!clientAddress.equals(originatingAddress)) {
                logger.info("Client address mismatch in session of request " 
                        + this.request.getRequestURL() + "; actual client address: " 
                        + clientAddress + ", originating address: " + originatingAddress
                        + "; invalidating session");
                s.invalidate();
                s = this.request.getSession(create);
                if (s != null) {
                    s.setAttribute(CLIENT_ADDR_SESSION_ATTRIBUTE, clientAddress);
                }
            }
            return s;
        }
        
        @Override
        public String toString() {
            return SessionValidationRequestFilter.class.getSimpleName() + 
                    "." + getClass().getSimpleName() + "(" + request + ")";
        }
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
