/* Copyright (c) 2005, University of Oslo, Norway
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

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.security.PrincipalManager;

/**
 * Assertion for matching the current principal against a list of
 * configurable principals and groups.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>principalManager</code> - a {@link vtk.security.PrincipalManager} used
 *   for validating group membership
 *   <li><code>principals</code> - a {@link Set} of (fully qualified)
 *   principal names to match the current principal against.
 *   <li><code>groups</code> - a {@link Set} of groups to match
 *   the current principal against.
 * </ul>
 */
public class PrincipalMatchAssertion extends AbstractAssertion
  implements InitializingBean {

    private PrincipalManager principalManager;

    private Set<String> principals = new HashSet<>();
    private Principal[] groups = new Principal[0];
    
    public void setPrincipals(Set<String> principals) {
        this.principals = principals;
    }
    
    public void setGroups(Principal[] groups) {
        this.groups = groups;
    }
    
    public void setPrincipalManager(PrincipalManager principalManager) {
        this.principalManager = principalManager;
    }
    
    public void afterPropertiesSet() throws Exception {
        if (this.principals == null) {
            throw new BeanInitializationException(
                "JavaBean property 'principals' cannot be null");
        }
        if (this.groups == null) {
            throw new BeanInitializationException(
                "JavaBean property 'groups' cannot be null");
        }
        if (this.principalManager == null) {
            throw new BeanInitializationException(
                "JavaBean property 'principalManager' cannot be null");
        }

    }

    @Override
    public boolean matches(HttpServletRequest request, Resource resource,
            Principal principal) {
        return matches(principal);
    }
    
    @Override
    public boolean conflicts(WebAssertion assertion) {
        return false;
    }

    @Override
    public Optional<URL> processURL(URL url, Resource resource,
            Principal principal) {
        if (!matches(principal)) {
            return Optional.empty();
        }
        return Optional.of(url);
    }

    @Override
    public URL processURL(URL url) {
        return url;
    }

    private boolean matches(Principal principal) {
        if (principal != null) {
            if (this.principals.contains(principal.getQualifiedName())) {
                return true;
            }
            for (int i = 0; i < this.groups.length; i++) {
                Principal group = this.groups[i];
                if (this.principalManager.isMember(principal, group)) {
                    return true;
                }
            }
        }
        return false;
        
    }
    
}
