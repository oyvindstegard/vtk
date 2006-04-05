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
package org.vortikal.repositoryimpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.vortikal.repository.Acl;
import org.vortikal.repository.IllegalOperationException;
import org.vortikal.repository.Privilege;
import org.vortikal.security.Principal;
import org.vortikal.security.PrincipalManager;


public class AclImpl implements Acl {

    private boolean inherited;
    private boolean dirty = false; 
    
    private PrincipalManager principalManager;
    
    /**
     * map: [action --> Set(Principal)]
     */
    private Map actionSets = new HashMap();

    public AclImpl(PrincipalManager principalManager) {
        this.principalManager = principalManager;
    }
    
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public boolean hasPrivilege(String privilege, Principal principal) {
        Set actionSet = (Set)this.actionSets.get(privilege);
        
        if (actionSet != null && actionSet.contains(principal)) 
            return true;
        return false;
    }

 
    public Set getActions() {
        return actionSets.keySet();
    }

    public Set getPrincipalSet(String action) {
        return (Set) actionSets.get(action);
    }

    public void addEntry(String action, Principal p) {
        this.dirty = true;
        
        Set actionEntry = (Set) this.actionSets.get(action);
        if (actionEntry == null) {
            actionEntry = new HashSet();
            this.actionSets.put(action, actionEntry);
        }
        
        actionEntry.add(p);

    }
    
    public void removeEntry(String action, Principal principal) {
        this.dirty = true;

        Set actionEntry = (Set) this.actionSets.get(action);
        
        if (actionEntry == null) return;
        actionEntry.remove(principal);
    }

    public Principal[] listPrivilegedUsers(String action) {
        Set principals = (Set)this.actionSets.get(action);

        if (principals == null) return new Principal[0];
        
        List userList = new ArrayList();
        for (Iterator iter = principals.iterator(); iter.hasNext();) {
            Principal p = (Principal) iter.next();

            if (p.isUser()) {
                userList.add(p);
            }
            
        }
        return (Principal[]) userList.toArray(new Principal[userList.size()]);
    }

    public Principal[] listPrivilegedGroups(String action) {
        Set principals = (Set)this.actionSets.get(action);
        
        if (principals == null) return new Principal[0];
        
        List groupList = new ArrayList();
        for (Iterator iter = principals.iterator(); iter.hasNext();) {
            Principal p = (Principal) iter.next();

            if (p.getType() == Principal.TYPE_GROUP) {
                groupList.add(p);
            }
            
        }
        return (Principal[]) groupList.toArray(new Principal[groupList.size()]);
    }
    
    public Principal[] listPrivilegedPseudoPrincipals(String action) {
        Set principals = (Set)this.actionSets.get(action);
        
        if (principals == null) return new Principal[0];
        
        List principalList = new ArrayList();
        for (Iterator iter = principals.iterator(); iter.hasNext();) {
            Principal p = (Principal) iter.next();

            if (p.getType() == Principal.TYPE_PSEUDO) {
                principalList.add(p);
            }
            
        }
        return (Principal[]) principalList.toArray(new Principal[principalList.size()]);
    }

    public boolean isInherited() {
        return this.inherited;
    }

    public void setInherited(boolean inherited) {
        this.dirty = true;
        this.inherited = inherited;
    }

    public String[] getPrivilegeSet(Principal principal) {
        Set actions = new HashSet();
        
        for (Iterator iter = this.actionSets.entrySet().iterator(); iter.hasNext();) {
            Map.Entry entry = (Map.Entry) iter.next();
            String action = (String)entry.getKey();
            Set actionEntries = (Set)entry.getValue();
            if (actionEntries != null && actionEntries.contains(principal))
                actions.add(action);
        }
        
        return (String[])actions.toArray(new String[actions.size()]);
    }


    
    public boolean equals(Object o) {
        if (!(o instanceof AclImpl)) {
            return false;
        }

        AclImpl acl = (AclImpl) o;

        Set actions = actionSets.keySet();

        if (actions.size() != acl.actionSets.keySet().size()) {
            return false;
        }

        for (Iterator i = actions.iterator(); i.hasNext();) {
            String action = (String) i.next();

            if (!acl.actionSets.containsKey(action)) {
                return false;
            }

            Set myPrincipals = (Set) actionSets.get(action);
            Set otherPrincipals = (Set) acl.actionSets.get(action);

            if (myPrincipals.size() != otherPrincipals.size()) {
                return false;
            }

            for (Iterator j = myPrincipals.iterator(); j.hasNext();) {
                Principal p = (Principal) j.next();

                if (!otherPrincipals.contains(p)) {
                    return false;
                }
            }
        }

        return true;
    }

    public int hashCode() {
        int hashCode = super.hashCode();

        Set actions = actionSets.keySet();

        for (Iterator i = actions.iterator(); i.hasNext();) {
            String action = (String) i.next();
            Set principals = (Set) actionSets.get(action);

            for (Iterator j = principals.iterator(); j.hasNext();) {
                Principal p = (Principal) j.next();

                hashCode += p.hashCode();
            }
        }

        return hashCode;
    }

    public Object clone() throws CloneNotSupportedException {
        AclImpl clone = new AclImpl(principalManager);
        clone.setInherited(this.inherited);

        for (Iterator iter = actionSets.entrySet().iterator(); iter.hasNext();) {
            Map.Entry entry = (Map.Entry) iter.next();
            for (Iterator iterator = ((Set)entry.getValue()).iterator(); iterator.hasNext();) {
                Principal p = (Principal) iterator.next();
                clone.addEntry((String)entry.getKey(), p);
            }
        }
        clone.setDirty(this.dirty);
        
        return clone;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();

        sb.append("[ACL: ");
        sb.append("[inherited: ").append(this.inherited).append("] ");
        sb.append("access: ");
        for (Iterator i = actionSets.keySet().iterator(); i.hasNext();) {
            String action = (String) i.next();
            Set principalSet = (Set)actionSets.get(action);

            sb.append(" [");
            sb.append(action);
            sb.append(":");
            for (Iterator j = principalSet.iterator(); j.hasNext();) {
                Principal p = (Principal) j.next();

                sb.append(" ");
                sb.append(p);

                if (j.hasNext()) {
                    sb.append(",");
                }
            }
            sb.append("]");
        }
        sb.append("]");
        return sb.toString();
    }

    
    /**
     * Checks the validity of an ACL.
     *
     * @param aceList an <code>Ace[]</code> value
     * @exception AclException if an error occurs
     * @exception IllegalOperationException if an error occurs
     * @exception IOException if an error occurs
     */
    private void validateACL(Acl acl)
        throws AclException, IllegalOperationException {
        /*
         * Enforce ((dav:owner (dav:read dav:write dav:write-acl))
         */
        Principal p = principalManager.getPseudoPrincipal(Principal.NAME_PSEUDO_OWNER);
        if (!acl.hasPrivilege(Privilege.WRITE, p)) {
            throw new IllegalOperationException(
                "Owner must be granted write privilege in ACL.");
        }

        if (!acl.hasPrivilege(Privilege.READ, p)) {
            throw new IllegalOperationException(
                "Owner must be granted read privilege in ACL.");
        }

        if (!acl.hasPrivilege(Privilege.ALL, p)) {
            throw new IllegalOperationException(
                "Owner must be granted write-acl privilege in ACL.");
        }
        
        p = principalManager.getPseudoPrincipal(Principal.NAME_PSEUDO_ALL);
        if (acl.hasPrivilege(Privilege.WRITE, p)) {
            throw new IllegalOperationException(
            "'All users' isn't allowed write privilege in ACL.");
        }
        
        if (acl.hasPrivilege(Privilege.ALL, p)) {
            throw new IllegalOperationException(
            "'All users' isn't allowed write-acl privilege in ACL.");
        }

        /*
         * Walk trough the ACL, for every ACE, enforce that:
         * 1) Every principal is valid
         * 2) Every privilege has a supported namespace and name
         */

//        for (int i = 0; i < acl.length; i++) {
//            Ace ace = acl[i];
//
//            org.vortikal.repositoryimpl.ACLPrincipal principal = ace.getPrincipal();
//
//            if (principal.getType() == org.vortikal.repositoryimpl.ACLPrincipal.TYPE_URL) {
//                boolean validPrincipal = false;
//
//                if (principal.isUser()) {
//                    Principal p = null;
//                    try {
//                        p = principalManager.getPrincipal(principal.getURL());
//                    } catch (InvalidPrincipalException e) {
//                        throw new AclException("Invalid principal '" 
//                                + principal.getURL() + "' in ACL");
//                    }
//                    validPrincipal = principalManager.validatePrincipal(p);
//                } else {
//                    validPrincipal = principalManager.validateGroup(principal.getURL());
//                }
//
//                if (!validPrincipal) {
//                    throw new AclException(AclException.RECOGNIZED_PRINCIPAL,
//                        "Unknown principal: " + principal.getURL());
//                }
//            } else {
//                if ((principal.getType() != org.vortikal.repositoryimpl.ACLPrincipal.TYPE_ALL) &&
//                        (principal.getType() != org.vortikal.repositoryimpl.ACLPrincipal.TYPE_OWNER) &&
//                        (principal.getType() != org.vortikal.repositoryimpl.ACLPrincipal.TYPE_AUTHENTICATED)) {
//                    throw new AclException(AclException.RECOGNIZED_PRINCIPAL,
//                        "Allowed principal types are " +
//                        "either TYPE_ALL, TYPE_OWNER " + "OR  TYPE_URL.");
//                }
//            }
//
//            Privilege[] privileges = ace.getPrivileges();
//
//            for (int j = 0; j < privileges.length; j++) {
//                Privilege privilege = privileges[j];
//
//                if (privilege.getNamespace().equals(Namespace.STANDARD_NAMESPACE)) {
//                    if (!(privilege.getName().equals(Privilege.WRITE) ||
//                            privilege.getName().equals(Privilege.READ) ||
//                            privilege.getName().equals(Privilege.ALL))) {
//                        throw new AclException(AclException.NOT_SUPPORTED_PRIVILEGE,
//                            "Unsupported privilege name: " +
//                            privilege.getName());
//                    }
//                } else if (privilege.getNamespace().equals(Namespace.CUSTOM_NAMESPACE)) {
//                    if (!(privilege.getName().equals(Privilege.READ_PROCESSED))) {
//                        throw new AclException(AclException.NOT_SUPPORTED_PRIVILEGE,
//                            "Unsupported privilege name: " +
//                            privilege.getName());
//                    }
//                } else {
//                    throw new AclException(AclException.NOT_SUPPORTED_PRIVILEGE,
//                        "Unsupported privilege namespace: " +
//                        privilege.getNamespace());
//                }
//            }
//        }
    }
}
