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
package vtk.security.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import vtk.security.AuthenticationException;
import vtk.security.AuthenticationProcessingException;
import vtk.security.Principal;

/**
 * Authentication request handler interface. Instances of this interface are invoked by the dispatcher servlet in two
 * stages:
 * <ol>
 * <li>To decide whether the request is recognized as an authentication request
 * <li>To perform the authentication (if the request was recognized)
 * </ol>
 * 
 * Implementors of this interface must supply an authentication challenge that is used when authentication is required.
 * In addition, they should provide a way of logging out authenticated users, if possible.
 */
public interface AuthenticationHandler {

    public String getIdentifier();
    
    public static final class AuthResult {
        private String uid;
        public AuthResult(String uid) {
            this.uid = uid;
        }
        public String getUID() {
            return this.uid;
        }
    }
    
    /**
     * Determines whether a request is recognized by this authentication handler.
     * 
     * @param req
     *            the request in question
     * @return <code>true</code> if the request is a recognized authentication request, <code>false</code> otherwise.
     * @throws AuthenticationProcessingException
     *             if a system error occurs
     * @throws InvalidAuthenticationRequestException
     *             if the authentication request is not valid
     */
    public boolean isRecognizedAuthenticationRequest(HttpServletRequest req) throws AuthenticationProcessingException,
            InvalidAuthenticationRequestException;


    /**
     * Authenticates a request.
     * 
     * @param req
     * @return principal if the request processing should proceed
     * @throws AuthenticationProcessingException
     *             if an underlying problem prevented the request from being processed
     * @throws AuthenticationException
     *             if the request was not successfully authenticated
     * @throws AuthenticationProcessingException
     *             if a system error occurs
     * @throws InvalidAuthenticationRequestException
     *             if the authentication request is not valid
     */
    public AuthResult authenticate(HttpServletRequest req) throws AuthenticationProcessingException,
            AuthenticationException, InvalidAuthenticationRequestException;


    /**
     * Called after a successful authentication. Allows implementations to perform custom processing on the request
     * and/or response, such as redirecting the client.
     * 
     * @param req
     *            the <code>HttpServletRequest</code>
     * @param resp
     *            the <code>HttpServletResponse</code>
     * @return <code>false</code> if the post processing did not affect the request and the processing should proceed
     *         after this method returns, or <code>true</code>, indicating that the authentication handler has written
     *         the response (e.g. sent a redirect, etc.)
     * @throws AuthenticationProcessingException
     *             if a system error occurs
     * @throws InvalidAuthenticationRequestException
     *             if the authentication request is not valid
     */
    public boolean postAuthentication(HttpServletRequest req, HttpServletResponse resp)
            throws AuthenticationProcessingException, InvalidAuthenticationRequestException;


    /**
     * Indicates whether logging out is supported by this authentication handler.
     * 
     * @return <code>true</code> if this authentication handler supports the logout operation, <code>false</code>
     *         otherwise.
     */
    public boolean isLogoutSupported();


    /**
     * Log out the client from the authentication system. Some handler implementations may want to write to the servlet
     * response (perform a redirect, etc.), and some do not. This is indicated using the return value of this method.
     * 
     * @param principal
     *            the <code>Principal</code> to log out
     * @return <code>true</code> if the response has been written to and further request processing should stop,
     *         <code>false</code> otherwise.
     * @exception AuthenticationProcessingException
     *                if an error occurs
     * @throws IOException
     * @throws ServletException
     */
    public boolean logout(Principal principal, HttpServletRequest req, HttpServletResponse resp)
            throws AuthenticationProcessingException, ServletException, IOException;


    /**
     * Gets the authentication challenge to present to the client.
     * 
     * @return the authentication challenge.
     */
    public AuthenticationChallenge getAuthenticationChallenge();

}
