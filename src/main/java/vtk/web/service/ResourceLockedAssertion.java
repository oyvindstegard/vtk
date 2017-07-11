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

import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Lock;
import vtk.repository.Resource;
import vtk.security.Principal;

public class ResourceLockedAssertion extends AbstractAssertion {

    private boolean byCurrentUser = true;
    
    @Override
    public Optional<URL> processURL(URL url, Resource resource,
            Principal principal) {
        if (!matches(resource, principal)) {
            return Optional.empty();
        }
        return Optional.of(url);
    }


    @Override
    public URL processURL(URL url) {
        return url;
    }


    @Override
    public boolean matches(HttpServletRequest request, Resource resource,
            Principal principal) {
        return matches(resource, principal);
    }
    
    @Override
    public boolean conflicts(WebAssertion assertion) {
        return false;
    }


    @Override
    public String toString() {
        if (this.byCurrentUser) {
            return "resource.lockedBy(currentUser)";
        }
        return "resource.locked";
    }

    public void setByCurrentUser(boolean byCurrentUser) {
        this.byCurrentUser = byCurrentUser;
    }

    private boolean matches(Resource resource, Principal principal) {
        Principal principalLockedBy = null;
        if (resource == null) {
            return false;
        }
        Lock lock = resource.getLock();
        if (lock == null) {
            return false;
        }
        if (!this.byCurrentUser) {
            return true;
        }
        if (principal == null) {
            return false;
        }
        principalLockedBy = lock.getPrincipal();
        return principal.equals(principalLockedBy);
    }

}
