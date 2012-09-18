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
package org.vortikal.web.service;

import org.vortikal.repository.Resource;
import org.vortikal.security.AuthenticationException;
import org.vortikal.security.Principal;

/**
 * Assert that a principal is/is not authenticated,
 * optionally requiring authentication if not authenticated.
 *
 * Properties:
 * 
 * <ul>
 *   <li>invert - assert <code>not</code> authenticated if true, defaults to false
 *   </li>
 *   <li>requiresAuthentication - whether authentication is explicitly
 *       required. An AuthenticationException will be thrown on matching
 *       if there is no principal. Defaults to false.
 *   </li>
 * </ul>
 */
public class IsAuthenticatedAssertion extends AbstractRepositoryAssertion {


    private boolean requiresAuthentication = false;
    private boolean invert = false;
    
    @Override
    public boolean matches(Resource resource, Principal principal) {
        if (this.requiresAuthentication && principal == null)
            throw new AuthenticationException();
        
        if (principal == null) 
            return this.invert;

        return !this.invert;
    }

    @Override
    public boolean conflicts(Assertion assertion) {
        if (assertion instanceof IsAuthenticatedAssertion) {
            IsAuthenticatedAssertion a = (IsAuthenticatedAssertion) assertion;
            return (isInvert() != a.isInvert());
        }
        return false;
    }

    public boolean isInvert() {
        return this.invert;
    }
    
    public void setInvert(boolean invert) {
        this.invert = invert;
    }
    

    public void setRequiresAuthentication(boolean requiresAuthentication) {
        this.requiresAuthentication = requiresAuthentication;
    }
    
    @Override
    public String toString() {
        return "request.isAuthenticated";
    }

}
