/* Copyright (c) 2006, University of Oslo, Norway
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
package org.vortikal.repository;

import java.util.Set;

import org.vortikal.security.Principal;

public interface Acl extends Cloneable {

    public void addEntry(String action, Principal principal);

    public void removeEntry(String privilegeName, Principal principal);

    public Principal getOwner();
    
    /**
     * Gets the set of privileges on this resource for a given principal.
     */
    public String[] getPrivilegeSet(Principal principal);
    
    /**
     * @param privilegeName
     * @return a list of <code>Principal</code> objects
     */
    public Principal[] listPrivilegedUsers(String privilegeName);

    /**
     * @param privilegeName
     * @return a list of <code>String</code> group names.
     */
    public Principal[] listPrivilegedGroups(String privilegeName);
    
    public Principal[] listPrivilegedPseudoPrincipals(String action);

    public boolean isInherited();

    public void setInherited(boolean inherited);
    
    public boolean hasPrivilege(Principal principal, String privilegeName);

    public Object clone() throws CloneNotSupportedException;
    
    
    // XXX: From impl...

    public Set getPrincipalSet(String action);

    public Set getActions();

}
