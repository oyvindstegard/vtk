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


    public boolean hasPrivilege(RepositoryAction privilege, Principal principal);

    public RepositoryAction[] getPrivilegeSet(Principal principal);
    
    public Principal[] listPrivilegedUsers(RepositoryAction privilege);

    public Principal[] listPrivilegedGroups(RepositoryAction privilege);
    
    public Principal[] listPrivilegedPseudoPrincipals(RepositoryAction action);

    public boolean isInherited();
    
    public Set getPrincipalSet(RepositoryAction action);

    public Set getActions();

    /**
     * @return if any modifications has been performed on the ACL
     */
    public boolean isDirty();
    
    public void clear();

    public void setInherited(boolean inherited);

    public void addEntry(RepositoryAction action, Principal principal) 
        throws IllegalArgumentException;

    public void removeEntry(RepositoryAction privilegeName, Principal principal) 
        throws IllegalArgumentException;

    public boolean containsEntry(RepositoryAction privilegeName, Principal principal)
        throws IllegalArgumentException;
    

    public Object clone() throws CloneNotSupportedException;
    
}
