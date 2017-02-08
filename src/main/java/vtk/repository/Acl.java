/* Copyright (c) 2006-2012, University of Oslo, Norway
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
package vtk.repository;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import vtk.security.Principal;
import vtk.security.PrincipalFactory;

/**
 * Class representing a resource ACL as a set of privileges, where each
 * privilege is mapped to a set of principals.
 * 
 * <p>Objects of this class are immutable.
 */
public final class Acl implements Serializable {
    
    private static final long serialVersionUID = -6996873030892397695L;

    public static final Acl EMPTY_ACL = new Acl(Collections.<Privilege, Set<Principal>>emptyMap());

    /**
     * map: [Privilege --> Set(Principal)]
     */
    private final Map<Privilege, Set<Principal>> actionSets;
    
    public Acl(Map<Privilege, Set<Principal>> actionSets) {
        if (actionSets == null) {
            throw new IllegalArgumentException("Argument is NULL");
        }
        this.actionSets = copyActionSets(actionSets);
    }

    /**
     * Tests if a principal U has privilege P in this ACL.
     * 
     * @param privilege
     * @param principal
     * @return <code>true</code> if principal has privilege.
     */
    public boolean hasPrivilege(Privilege privilege, Principal principal) {
        Set<Principal> actionSet = this.actionSets.get(privilege);
        if (actionSet != null && actionSet.contains(principal)) { 
            return true;
        }
        return false;
    }
    
    /**
     * Returns all privileges present in this ACL.
     * @return set of all privileges present in this ACL.
     */
    public Set<Privilege> getActions() {
        return Collections.unmodifiableSet(this.actionSets.keySet());
    }

    /**
     * Returns the set of principals which have the provided privilege in
     * this ACL.
     * 
     * @param action the <code>Privilege</code> to get set of principals for.
     * @return the set of principals for the given privilege. If no principals have
     *         the given privilege in this ACL, then the empty set is returned.
     */
    public Set<Principal> getPrincipalSet(Privilege action) {
        Set<Principal> set = this.actionSets.get(action);
        if (set == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(set);
    }

    /**
     * Returns <code>true</code> if this ACL is empty.
     */
    public boolean isEmpty() {
        return this.actionSets.isEmpty();
    }

    /**
     * Returns the total number of principals for all privileges present
     * in this ACL.
     */
    public int size() {
        int total = 0;
        for (Set<Principal> principalSet: this.actionSets.values()) {
            total += principalSet.size();
        }
        return total;
    }
    
    
    public boolean isValidEntry(Privilege privilege, Principal principal) {
        if (privilege == null) {
            throw new IllegalArgumentException("Privilege is NULL");
        }
        if (principal == null) {
            throw new IllegalArgumentException("Principal is NULL");
        }
        if (PrincipalFactory.ALL.equals(principal)) {
            if (Privilege.ALL == privilege
                    || Privilege.READ_WRITE_UNPUBLISHED == privilege
                    || Privilege.READ_WRITE == privilege
                    || Privilege.ADD_COMMENT == privilege) {
                return false;
            }
        }
        return true;
    }
    
    
    public Acl addEntry(Privilege privilege, Principal principal) {
        if (!isValidEntry(privilege, principal)) {
            throw new IllegalArgumentException(
                    "Not allowed to add principal '" + principal + "' to privilege '"
                    + privilege + "'" );
        }
        
        Map<Privilege, Set<Principal>> newAcl = copyActionSets(this.actionSets);
        
        Set<Principal> actionEntry = newAcl.get(privilege);
        if (actionEntry == null) {
            actionEntry = new HashSet<Principal>();
            newAcl.put(privilege, actionEntry);
        }
        actionEntry.add(principal);
        return new Acl(newAcl);
    }
    
    public Acl addEntryNoValidation(Privilege privilege, Principal principal) {
        if (privilege == null) {
            throw new IllegalArgumentException("Privilege is NULL");
        }
        if (principal == null) {
            throw new IllegalArgumentException("Principal is NULL");
        }
        
        Map<Privilege, Set<Principal>> newAcl = copyActionSets(this.actionSets);
        
        Set<Principal> actionEntry = newAcl.get(privilege);
        if (actionEntry == null) {
            actionEntry = new HashSet<Principal>();
            newAcl.put(privilege, actionEntry);
        }
        actionEntry.add(principal);
        return new Acl(newAcl);
    }
    
    public Acl removeEntry(Privilege privilege, Principal principal)
        throws IllegalArgumentException {
        if (privilege == null) {
            throw new IllegalArgumentException("Privilege is NULL");
        }
        if (principal == null) {
            throw new IllegalArgumentException("Principal is NULL");
        }
            
        Map<Privilege, Set<Principal>> newAcl = copyActionSets(this.actionSets);
        
        Set<Principal> actionEntry = newAcl.get(privilege);
        if (actionEntry != null) {
            actionEntry.remove(principal);
            if (actionEntry.isEmpty()) {
                newAcl.remove(privilege);
            }
        }
        return new Acl(newAcl);
    }
    
    
    public Acl clear(Privilege privilege) {
        if (privilege == null) {
            throw new IllegalArgumentException("Privilege is NULL");
        }

        Map<Privilege, Set<Principal>> newAcl = copyActionSets(this.actionSets);
        newAcl.remove(privilege);
        return new Acl(newAcl);
    }



    public Principal[] listPrivilegedUsers(Privilege privilege) {
        if (privilege == null) {
            throw new IllegalArgumentException("Privilege is NULL");
        }
        Set<Principal> principals = this.actionSets.get(privilege);

        if (principals == null) {
            return new Principal[0];
        }
        
        List<Principal> userList = new ArrayList<Principal>();
        for (Principal p: principals) {
            if (p.getType() == Principal.Type.USER) {
                userList.add(p);
            }
            
        }
        return userList.toArray(new Principal[userList.size()]);
    }

    public Principal[] listPrivilegedGroups(Privilege privilege) {
        Set<Principal> principals = this.actionSets.get(privilege);
        
        if (principals == null) {
            return new Principal[0];
        }
        
        List<Principal> groupList = new ArrayList<Principal>();
        for (Principal p: principals) {
            if (p.getType() == Principal.Type.GROUP) {
                groupList.add(p);
            }
        }
        return groupList.toArray(new Principal[groupList.size()]);
    }
    
    public Principal[] listPrivilegedPseudoPrincipals(Privilege privilege) {
        Set<Principal> principals = this.actionSets.get(privilege);
        
        if (principals == null) {
            return new Principal[0];
        }
        
        List<Principal> principalList = new ArrayList<Principal>();
        for (Principal p: principals) {
            if (p.getType() == Principal.Type.PSEUDO) {
                principalList.add(p);
            }
            
        }
        return principalList.toArray(new Principal[principalList.size()]);
    }


    public Privilege[] getPrivilegeSet(Principal principal) {
        Set<Privilege> actions = EnumSet.noneOf(Privilege.class);
        
        for (Map.Entry<Privilege, Set<Principal>> entry: this.actionSets.entrySet()) {
            Privilege action = entry.getKey();
            Set<Principal> actionEntries = entry.getValue();
            if (actionEntries != null && actionEntries.contains(principal))
                actions.add(action);
        }
        
        return actions.toArray(new Privilege[actions.size()]);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Acl other = (Acl) obj;
        return this.actionSets.equals(other.actionSets);
    }

    @Override
    public int hashCode() {
        return this.actionSets.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("[ACL: ");
        sb.append("access: ");
        for (Privilege action: this.actionSets.keySet()) {
            Set<Principal> principalSet = this.actionSets.get(action);

            sb.append(" [");
            sb.append(action);
            sb.append(":");
            for (Iterator<Principal> j = principalSet.iterator(); j.hasNext();) {
                Principal p = j.next();

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
    
    private Map<Privilege, Set<Principal>> copyActionSets(Map<Privilege, Set<Principal>> actionSets) {
        Map<Privilege, Set<Principal>> copy = new EnumMap<Privilege, Set<Principal>>(Privilege.class);
        for (Privilege privilege: actionSets.keySet()) {
            if (privilege == null) {
                throw new IllegalArgumentException("Privileges cannot be NULL");
            }
            Set<Principal> value = actionSets.get(privilege);
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException(
                        "Illegal empty mapping in ACL for privilege " + privilege);
            }
            Set<Principal> principals = new HashSet<Principal>();
            principals.addAll(value);
            copy.put(privilege, principals);
        }
        return copy;
    }

}
