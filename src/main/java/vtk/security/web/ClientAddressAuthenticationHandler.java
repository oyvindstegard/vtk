/* Copyright (c) 2017, University of Oslo, Norway
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
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import vtk.repository.Path;
import vtk.security.AuthenticationException;
import vtk.security.AuthenticationProcessingException;
import vtk.security.Principal;
import vtk.web.service.URL;

public class ClientAddressAuthenticationHandler
        implements AuthenticationHandler {
    private String identifier;
    private SpecProvider provider;
    
    public static final class Spec {
        public final Pattern net;
        public final Path uri;
        public final String uid;
        public final Optional<Instant> validFrom;
        public final Optional<Instant> validTo;
        
        private Spec(Pattern net, Path uri, String uid, 
                Optional<Instant> validFrom, Optional<Instant> validTo) {
            this.net = net;
            this.uri = uri;
            this.uid = uid;
            this.validFrom = validFrom;
            this.validTo = validTo;
        }
    }
    
    public static interface SpecProvider {
        public Collection<Spec> specs();
    }
    
    
    public ClientAddressAuthenticationHandler(String identifier, SpecProvider provider) {
        this.identifier = identifier;
        this.provider = provider;
    }
    
    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public boolean isRecognizedAuthenticationRequest(HttpServletRequest req)
            throws AuthenticationProcessingException,
            InvalidAuthenticationRequestException {
        return auth(req).isPresent();
    }

    @Override
    public AuthResult authenticate(HttpServletRequest req)
            throws AuthenticationProcessingException, AuthenticationException,
            InvalidAuthenticationRequestException {
        Optional<String> auth = auth(req);
        if (!auth.isPresent()) {
            throw new AuthenticationException("No match for client: " + req.getRemoteAddr());
        }
        return new AuthResult(auth.get());
    }

    @Override
    public boolean postAuthentication(HttpServletRequest req,
            HttpServletResponse resp) throws AuthenticationProcessingException,
            InvalidAuthenticationRequestException {
        return false;
    }

    @Override
    public boolean isLogoutSupported() {
        return false;
    }

    @Override
    public boolean logout(Principal principal, HttpServletRequest req,
            HttpServletResponse resp) throws AuthenticationProcessingException,
            ServletException, IOException {
        return false;
    }

    @Override
    public AuthenticationChallenge getAuthenticationChallenge() {
        return new AuthenticationChallenge() {
            @Override
            public void challenge(HttpServletRequest req,
                    HttpServletResponse resp)
                    throws AuthenticationProcessingException, ServletException,
                    IOException {
            }
        };
    }

    private Optional<String> auth(HttpServletRequest req) {
        URL url = URL.parse(req.getRequestURL().toString());
        String clientAddr = req.getRemoteAddr();
        
        for (Spec spec: provider.specs()) {
            Matcher m = spec.net.matcher(clientAddr);
            if (!m.matches()) {
                continue;
            }
            if (!(spec.uri.isAncestorOf(url.getPath()) || spec.uri.equals(url.getPath()))) {
                continue;
            }
            if (spec.validFrom.isPresent()) {
                if (!spec.validFrom.get().isBefore(Instant.now())) {
                    continue;
                }
            }
            if (spec.validTo.isPresent()) {
                if (spec.validTo.get().isBefore(Instant.now())) {
                    continue;
                }
            }
            return Optional.of(spec.uid);
        }
        return Optional.empty();
    }
}
