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
package vtk.security.web.clientaddr;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.security.AuthenticationException;
import vtk.security.AuthenticationProcessingException;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;
import vtk.security.PrincipalManager;
import vtk.security.roles.RoleManager;
import vtk.security.roles.RoleManager.Role;
import vtk.security.web.AuthenticationChallenge;
import vtk.security.web.AuthenticationHandler;
import vtk.security.web.InvalidAuthenticationRequestException;
import vtk.util.Result;

public class ClientAddressAuthenticationHandler
        implements AuthenticationHandler {
    private String identifier;
    private Supplier<List<Result<ClientAddrAuthSpec>>> provider;
    private PrincipalFactory principalFactory;
    private PrincipalManager principalManager;
    private Optional<RoleManager> roleManager;
    
    public ClientAddressAuthenticationHandler(String identifier, 
            Supplier<List<Result<ClientAddrAuthSpec>>> provider,
            PrincipalFactory principalFactory,
            PrincipalManager principalManager,
            Optional<RoleManager> roleManager) {
        this.identifier = identifier;
        this.provider = provider;
        this.principalFactory = principalFactory;
        this.principalManager = principalManager;
        this.roleManager = roleManager;
    }
    
    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public boolean isRecognizedAuthenticationRequest(HttpServletRequest req)
            throws AuthenticationProcessingException,
            InvalidAuthenticationRequestException {
        Result<Optional<String>> auth = auth(req);
        if (auth.failure.isPresent()) {
            /// XXX: throw AuthenticationProcessingException()?
            return false;
        }
        if (!auth.result.get().isPresent()) {
            return false;
        }
        return true;
    }

    @Override
    public AuthResult authenticate(HttpServletRequest req)
            throws AuthenticationProcessingException, AuthenticationException,
            InvalidAuthenticationRequestException {
        Result<Optional<String>> auth = auth(req);

        if (auth.failure.isPresent()) {
            throw new AuthenticationProcessingException(auth.failure.get());
        }
        Optional<String> uid = auth.result.get();
        if (!uid.isPresent()) {
            throw new AuthenticationException("No match for client: " + req.getRemoteAddr());
        }
        Result<Optional<Principal>> principal = principal(uid.get());
        if (principal.failure.isPresent()) {
            throw new AuthenticationProcessingException(principal.failure.get());
        }
        if (!principal.result.get().isPresent()) {
            throw new AuthenticationException("Invalid principal: " + uid.get());
        }
        return new AuthResult(uid.get());
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
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                Writer writer = resp.getWriter();
                Result<Optional<String>> auth = auth(req);
                if (auth.result.isPresent()) {
                    Optional<String> uid = auth.result.get();
                    if (uid.isPresent()) {
                        writer.write("Invalid principal: " + uid.get());
                    }
                    else {
                        writer.write("Could not map request to a valid principal");
                    }
                }
                resp.flushBuffer();
            }
        };
    }
    
    public Controller displayRulesHandler() {
        return new Controller() {
            @Override
            public ModelAndView handleRequest(HttpServletRequest request,
                    HttpServletResponse response) throws Exception {
                List<Result<ClientAddrAuthSpec>> config = provider.get();
                response.setContentType("text/plain;charset=utf-8");
                PrintWriter writer = response.getWriter();
                writer.write("Entries (executed in order on each request):\n\n");
                config.forEach(entry -> writer.write("- " + entry.toString() + "\n\n"));
                response.flushBuffer();
                return null;
            }
        };
    }
    
    private Result<Optional<Principal>> principal(String uid) {
        try {
            Principal principal = 
                    principalFactory.getPrincipal(uid, Principal.Type.USER);
            if (principal == null) {
                return Result.success(Optional.empty());
            }
            if (!principalManager.validatePrincipal(principal)) {
                return Result.success(Optional.empty());
            }
            Optional<Boolean> roleDenied = roleManager.map(mgr -> 
                mgr.hasRole(principal, Role.ROOT) || mgr.hasRole(principal, Role.READ_EVERYTHING));
            if (roleDenied.orElse(false)) {
                return Result.success(Optional.empty());
            }
            return Result.success(Optional.of(principal));
        }
        catch (Throwable t) {
            return Result.failure(t);
        }
    }
    
    private Result<Optional<String>> auth(HttpServletRequest req) {
        String clientAddr = req.getRemoteAddr();
        List<Result<ClientAddrAuthSpec>> config = provider.get();
        
        for (Result<ClientAddrAuthSpec> entry: config) {
            if (entry.failure.isPresent()) {
                return Result.failure(entry.failure.get());
            }
            ClientAddrAuthSpec spec = entry.result.get();
            Matcher m = spec.net.matcher(clientAddr);
            if (!m.matches()) {
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
            return Result.success(Optional.of(spec.uid));
        }
        return Result.success(Optional.empty());
    }
}
