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
package org.vortikal.web.service;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.vortikal.repository.Resource;
import org.vortikal.security.Principal;
import org.vortikal.security.PrincipalManager;

/**
 * Assertion for matching the current principal against a list of
 * configurable principals and groups.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>principalStore</code> - a {@link org.vortikal.security.PrincipalStore} used
 *   for validating group membership
 *   <li><code>principals</code> - a {@link Set} of (fully qualified)
 *   principal names to match the current principal against.
 *   <li><code>groups</code> - a {@link Set} of group names to match
 *   the current principal against.
 * </ul>
 */
public class PrincipalMatchAssertion extends AbstractRepositoryAssertion
  implements InitializingBean {

    private PrincipalManager principalManager;
    private Set principals = new HashSet();
    private Set groupNames = new HashSet();
    private Set groups = new HashSet();
    
    public void setPrincipals(Set principals) {
        this.principals = principals;
    }
    
    public void setGroups(Set groups) {
        this.groupNames = groups;
    }
    
    public void setPrincipalManager(PrincipalManager principalManager) {
        this.principalManager = principalManager;
    }
    
    
    public void afterPropertiesSet() throws Exception {
        if (this.principals == null) {
            throw new BeanInitializationException(
                "JavaBean property 'principals' cannot be null");
        }
        if (this.groupNames == null) {
            throw new BeanInitializationException(
                "JavaBean property 'groups' cannot be null");
        }
        
        if (this.principalManager == null) {
            throw new BeanInitializationException(
                "JavaBean property 'principalManager' cannot be null");
        }

        for (Iterator iter = groupNames.iterator(); iter.hasNext();) {
            String groupName = (String) iter.next();
            groups.add(principalManager.getGroupPrincipal(groupName));
        }
        
    }


    public boolean matches(Resource resource, Principal principal) {
        if (principal != null) {
           
            if (this.principals.contains(principal.getQualifiedName())) {
                return true;
            }

            for (Iterator i = this.groups.iterator(); i.hasNext();) {
                Principal group = (Principal) i.next();
                if (this.principalManager.isMember(principal, group)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    public boolean conflicts(Assertion assertion) {
        return false;
    }
}
