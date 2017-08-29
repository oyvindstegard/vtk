/* Copyright (c) 2011, University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.security.PrincipalImpl;
import vtk.security.PrincipalManager;

public class GroupMembershipAssertion implements WebAssertion {
    
    private Collection<Principal> groups = new ArrayList<>();
    private boolean allowAll = false;
    private PrincipalManager principalManager;
    
    @Override
    public Optional<URL> processURL(URL url, Resource resource, Principal principal) {
        if (!match(principal)) {
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
        return match(principal);
    }

    @Override
    public boolean conflicts(WebAssertion assertion) {
        return false;
    }
    
    public void setGroups(Collection<String> groups) {
        if (groups == null) {
            return;
        }
        for (String s: groups) {
            if ("*".equals(s)) {
                this.allowAll = true;
                continue;
            }
            Principal group = new PrincipalImpl(s, Principal.Type.GROUP);
            this.groups.add(group);
        }
    }

    @Required
    public void setPrincipalManager(PrincipalManager principalManager) {
        this.principalManager = principalManager;
    }
    
    @Override
    public String toString() {
        if (allowAll) {
            return "principal.groups.anyOf(*)";
            
        }
        return "principal.groups.anyOf(" + groups + ")";
    }

    private boolean match(Principal p) {
        if (this.allowAll) {
            return true;
        }
        if (p == null) {
            return false;
        }
        for (Principal group: this.groups) {
            if (principalManager.isMember(p, group)) {
                return true;
            }
        }
        return false;
    }

}
