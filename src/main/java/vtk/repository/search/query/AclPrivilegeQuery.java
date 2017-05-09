/* Copyright (c) 2014, University of Oslo, Norway
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

package vtk.repository.search.query;

import vtk.repository.AuthorizationManager;
import vtk.repository.Privilege;
import vtk.security.Principal;

/**
 * Query resource ACL privileges and associated principals.
 */
public class AclPrivilegeQuery extends AbstractAclQuery {

    private static final long serialVersionUID = 1051775063317822426L;
    
    private final String qualifiedName;
    private final Privilege privilege;
    private final boolean includeSuperPrivileges;

    /**
     * Like {@link AclPrivilegeQuery#AclPrivilegeQuery(vtk.repository.Privilege, java.lang.String, boolean, boolean) }, but
     * with parameters <code>inverted</code> and <code>includeSuperPrivileges</code>
     * set to <code>false</code>.
     * 
     * @param privilege
     * @param qualifiedName
     */
    public AclPrivilegeQuery(Privilege privilege, String qualifiedName) {
        this(privilege, qualifiedName, false, false);
    }
    
    /**
     * Like {@link AclPrivilegeQuery#AclPrivilegeQuery(vtk.repository.Privilege, vtk.security.Principal, boolean, boolean) }, but
     * with parameters <code>inverted</code> and <code>includeSuperPrivileges</code>
     * set to <code>false</code>.
     * 
     * @param privilege
     * @param principal
     */
    public AclPrivilegeQuery(Privilege privilege, Principal principal) {
        this(privilege, principal, false, false);
    }
    
    /**
     * <p>This constructor is equivalent to calling {@link AclPrivilegeQuery#AclPrivilegeQuery(vtk.repository.Privilege, java.lang.String, boolean, boolean) }
     * with <code>principal.getQualifiedName()</code> as second argument.
     * 
     * @param privilege
     * @param principal
     * @param inverted
     * @param includeSuperPrivileges
     */
    public AclPrivilegeQuery(Privilege privilege, Principal principal, boolean inverted, boolean includeSuperPrivileges) {
        this(privilege, principal == null ? null : principal.getQualifiedName(), inverted, includeSuperPrivileges);
    }
    
    /**
     * Construct a new ACL privilege query node.
     * 
     * <p>
     * You may provide a <code>null</code> value for either privilege or
     * principal uid, and that will be interpreted as wildcard, meaning any
     * value.
     * 
     * <p>
     * If privilege is <code>null</code>, but not uid, then the query shall
     * match all resources the have at least one occurence of the uid, for any
     * privilege.
      * 
     * <p>
     * If uid is <code>null</code>, but not privilege, then the query shall
     * match all resources where at least one uid exists for the provided
     * privilege (or optionally one of its super privileges, depending on the
     * <code>includeSuperPrivileges</code> parameter).
     * 
     * <p><em>Using wildcard for both privilege and principal makes little
     * sense and will result in a query that matches all resources.</em>
      * 
     * @param privilege the ACL privilege to search for, or <code>null</code>
     * for any privilege (wildcard)
     * @param qualifiedName fully qualified UID of a principal, or
     * <code>null</code> for any principal (wildcard)
     * @param inverted set to <code>true</code> to invert matching logic
     * @param includeSuperPrivileges if <code>true</code>, then all super
     * privileges of Privilege <code>privilege</code> will be included in
     * matching criteria. This allows to query for "all privileges
     * stronger than or equal to" the <code>privilege</code> parameter.
     * Super-privileges of the provided privilege are provided by {@link AuthorizationManager#superPrivilegesOf(vtk.repository.Privilege)
     * }
     * This parameter has no effect when <code>privilege</code> is
     * <code>null</code> (wildcard).
     */
    public AclPrivilegeQuery(Privilege privilege, String qualifiedName, boolean inverted, boolean includeSuperPrivileges) {
        super(inverted);
        this.privilege = privilege;
        this.qualifiedName = qualifiedName;
        this.includeSuperPrivileges = includeSuperPrivileges;
    }
    
    /**
     * @return the fully qualified UID, or <code>null</code> meaning any principal (wildcard).
     */
    public String getQualifiedName() {
        return qualifiedName;
    }

    /**
     * @return the privilege, or <code>null</code> meaning any privilege (wildcard).
     */
    public Privilege getPrivilege() {
        return privilege;
    }
    
    public boolean isIncludeSuperPrivileges() {
        return includeSuperPrivileges;
    }

    @Override
    public String toString() {
        return "AclPrivilegeQuery{" + "qualifiedName=" + qualifiedName + ", privilege="
                + privilege + ", includeSuperPrivileges=" + includeSuperPrivileges +", inverted=" + super.inverted + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + (this.qualifiedName != null ? this.qualifiedName.hashCode() : 0);
        hash = 89 * hash + (this.privilege != null ? this.privilege.hashCode() : 0);
        hash = 89 * hash + (this.includeSuperPrivileges ? 1 : 0);
        hash = 89 * hash + (super.inverted ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AclPrivilegeQuery other = (AclPrivilegeQuery) obj;
        if (this.qualifiedName != other.qualifiedName && (this.qualifiedName == null || !this.qualifiedName.equals(other.qualifiedName))) {
            return false;
        }
        if (this.privilege != other.privilege) {
            return false;
        }
        if (this.includeSuperPrivileges != other.includeSuperPrivileges) {
            return false;
        }
        if (super.inverted != other.inverted) {
            return false;
        }
        return true;
    }

    @Override
    public Object accept(QueryVisitor visitor, Object data) {
        return visitor.visit(this, data);
    }
}
