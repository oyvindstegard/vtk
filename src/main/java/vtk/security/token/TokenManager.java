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
package vtk.security.token;

import vtk.security.Principal;
import vtk.security.web.AuthenticationHandler;

/**
 * Token management interface. Token management involves creation,
 * storing and removing authenticated tokens as well as mapping
 * tokens to principal objects.
 * 
 * This interface is not intended to be exposed directly to
 * application code. Such components should rather be provided a
 * token and a principal object directly, through a PrincipalManager.
 *
 * @see vtk.security.PrincipalStore
 */
public interface TokenManager {

    /**
     * Maps a token to a principal object.
     *
     * @param token a <code>String</code> value
     * @return a <code>Principal</code>, or <code>null</code> if no
     * such session exists
     */
    public Principal getPrincipal(String token);

    /**
     * Inserts a new principal into the token manager (typically
     * after a successful authentication operation).
     *
     * @param principal The {@link Principal} to register
     * @param authenticationHandler the handler through which this
     * principal was authenticated.
     * @return the created token for the principal
     */
    public String newToken(Principal principal, AuthenticationHandler authenticationHandler);

    /**
     * Removes a principal from the principal manager (typically after
     * a user has logged out).
     *
     * @param token a <code>String</code> value
     */
    public void removeToken(String token);

    /**
     * Gets the authentication handler ID that authenticated the
     * principal identified by this token.
     *
     * @param token a the security token
     * @return an authentication handler ID, or <code>null</code> if the
     * token does not map to an authenticated principal, or the
     * principal was authenticated using a different method.
     */
    public String getAuthenticationHandlerID(String token);
    
    public String getRegisteredToken(Principal principal);
}
