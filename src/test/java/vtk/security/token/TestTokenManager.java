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
package vtk.security.token;

import vtk.security.Principal;
import vtk.security.web.AuthenticationHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TestTokenManager implements TokenManager {
    private final Map<String, Principal> principalMap = new HashMap<>();

    @Override
    public Principal getPrincipal(String token) {
        return principalMap.get(token);
    }

    @Override
    public String newToken(Principal principal, AuthenticationHandler authenticationHandler) {
        String token = generateID();
        principalMap.put(token, principal);
        return token;
    }

    @Override
    public void removeToken(String token) {
        principalMap.remove(token);
    }

    @Override
    public String getAuthenticationHandlerID(String token) {
        return "test";
    }

    @Override
    public String getRegisteredToken(Principal principal) {
        if (this.principalMap.containsValue(principal)) {
            for (String token: this.principalMap.keySet()) {
                if (this.principalMap.get(token).equals(principal))
                    return token;
            }
        }
        return null;
    }

    private String generateID() {
        return UUID.randomUUID().toString();
    }
}
