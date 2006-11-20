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
package org.vortikal.repositoryimpl;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import org.vortikal.repository.Acl;
import org.vortikal.repository.AuthorizationException;
import org.vortikal.repository.AuthorizationManager;
import org.vortikal.repository.Privilege;
import org.vortikal.repository.ReadOnlyException;
import org.vortikal.repository.RepositoryAction;
import org.vortikal.repository.Resource;
import org.vortikal.repository.ResourceLockedException;
import org.vortikal.repositoryimpl.dao.DataAccessor;
import org.vortikal.security.AuthenticationException;
import org.vortikal.security.Principal;
import org.vortikal.security.PrincipalManager;
import org.vortikal.security.PseudoPrincipal;
import org.vortikal.security.roles.RoleManager;
import org.vortikal.util.repository.URIUtil;


public class AuthorizationManagerImpl implements AuthorizationManager {

    private RoleManager roleManager;
    private PrincipalManager principalManager;
    private DataAccessor dao;
    private LockManager lockManager;
    
    private boolean readOnly = false;

    public boolean isReadOnly() {
        return this.readOnly;
    }
    
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public void authorizeRootRoleAction(Principal principal) throws AuthorizationException {
        if (!this.roleManager.hasRole(principal, RoleManager.ROOT)) {
            throw new AuthorizationException(
                "Principal '" + principal
                + "' not authorized to perform repository administration");
        }
    }
    
    private void checkReadOnly(Principal principal) throws ReadOnlyException {
        
        if (isReadOnly() && !this.roleManager.hasRole(principal, 
                RoleManager.ROOT)) {
            throw new ReadOnlyException();
        }
    }

    public void authorizeAction(String uri, RepositoryAction action, 
            Principal principal) throws AuthenticationException, AuthorizationException,
            ResourceLockedException, IOException {

        if (!RepositoryAction.REPOSITORY_ACTION_SET.contains(action)
                || RepositoryAction.COPY.equals(action)
                || RepositoryAction.MOVE.equals(action)) {
            throw new IllegalArgumentException(
                "Unable to authorize for action " + action
                + ": must be one of (except COPY/MOVE) " + RepositoryAction.REPOSITORY_ACTION_SET);
        }

        if (RepositoryAction.UNEDITABLE_ACTION.equals(action)) {
            throw new AuthorizationException("Uneditable");
        } else if (RepositoryAction.READ_PROCESSED.equals(action)) {
            authorizeReadProcessed(uri, principal);
        } else if (RepositoryAction.READ.equals(action)) {
            authorizeRead(uri, principal);
        } else if (RepositoryAction.CREATE.equals(action)) {
            authorizeCreate(uri, principal);
        } else if (RepositoryAction.WRITE.equals(action)) {
            authorizeWrite(uri, principal);
        } else if (RepositoryAction.WRITE_ACL.equals(action) ||
                RepositoryAction.ALL.equals(action)) {
            authorizeAll(uri, principal);
        } else if (RepositoryAction.UNLOCK.equals(action)) {
            authorizeUnlock(uri, principal);
        } else if (RepositoryAction.DELETE.equals(action)) {
            authorizeDelete(uri, principal);
        } else if (RepositoryAction.REPOSITORY_ADMIN_ROLE_ACTION.equals(action)) {
            authorizePropertyEditAdminRole(uri, principal);
        } else if (RepositoryAction.REPOSITORY_ROOT_ROLE_ACTION.equals(action)) {
            authorizePropertyEditRootRole(uri, principal);
        }
        
    }
    
    
    private static final RepositoryAction[] READ_PROCESSED_AUTH_PRIVILEGES = 
        new RepositoryAction[] {Privilege.ALL, Privilege.READ, Privilege.READ_PROCESSED};

    /**
     * <ul>
     *   <li>Privilege READ_PROCESSED, READ or ALL in ACL
     *   <li>Role ROOT or READ_EVERYTHING
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizeReadProcessed(String uri, Principal principal) 
        throws AuthenticationException, AuthorizationException, IOException {
        ResourceImpl resource = this.dao.load(uri);

        if (this.roleManager.hasRole(principal, RoleManager.ROOT) ||
                this.roleManager.hasRole(principal, RoleManager.READ_EVERYTHING))
            return;

        aclAuthorize(principal, resource, READ_PROCESSED_AUTH_PRIVILEGES);
    }

    

    private static final RepositoryAction[] READ_AUTH_PRIVILEGES = 
        new RepositoryAction[] {Privilege.ALL, Privilege.READ};

    /**
     * <ul>
     *   <li>Privilege READ or ALL in ACL
     *   <li>Role ROOT or READ_EVERYTHING
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizeRead(String uri, Principal principal) 
        throws AuthenticationException, AuthorizationException,
        ResourceLockedException, IOException {

        ResourceImpl resource = this.dao.load(uri);

        if (this.roleManager.hasRole(principal, RoleManager.ROOT) ||
                this.roleManager.hasRole(principal, RoleManager.READ_EVERYTHING))
            return;
        
        aclAuthorize(principal, resource, READ_AUTH_PRIVILEGES);
    }


    private static final RepositoryAction[] CREATE_AUTH_PRIVILEGES = 
        new RepositoryAction[] {Privilege.ALL, Privilege.WRITE, Privilege.BIND};

    /**
     * <ul>
     *   <li>Privilege BIND, WRITE or ALL on resource
     *   <li>Role ROOT
     *   <li>+ parent not locked by another principal
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizeCreate(String uri, Principal principal)
        throws AuthenticationException, AuthorizationException, ReadOnlyException, 
        ResourceLockedException, IOException {

        checkReadOnly(principal);

//         ResourceImpl parent = this.dao.load(URIUtil.getParentURI(uri));
        ResourceImpl resource = this.dao.load(uri);
        
        this.lockManager.lockAuthorize(resource, principal, false);
        
        if (this.roleManager.hasRole(principal, RoleManager.ROOT))
            return;
        
        aclAuthorize(principal, resource, CREATE_AUTH_PRIVILEGES);
    }
    

    private static final RepositoryAction[] WRITE_AUTH_PRIVILEGES = 
        new RepositoryAction[] {Privilege.ALL, Privilege.WRITE};

    /**
     * <ul>
     *   <li>Privilege WRITE or ALL in ACL
     *   <li>Role ROOT
     *   <li>+ resource not locked by another principal
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizeWrite(String uri, Principal principal)
        throws AuthenticationException, AuthorizationException, ReadOnlyException,
        ResourceLockedException, IOException {

        checkReadOnly(principal);

        ResourceImpl resource = this.dao.load(uri);
        
        this.lockManager.lockAuthorize(resource, principal, false);

        if (this.roleManager.hasRole(principal, RoleManager.ROOT))
            return;
        
        aclAuthorize(principal, resource, WRITE_AUTH_PRIVILEGES);
    }
    

    private static final RepositoryAction[] ALL_AUTH_PRIVILEGES = 
        new RepositoryAction[] {Privilege.ALL};


    /**
     * <ul>
     *   <li>Privilege ALL in ACL
     *   <li>Role ROOT
     *   <li>+ resource not locked by another principal
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizeAll(String uri, Principal principal)
        throws AuthenticationException, AuthorizationException, ReadOnlyException,
        ResourceLockedException, IOException {

        checkReadOnly(principal);
        
        ResourceImpl resource = this.dao.load(uri);
        
        this.lockManager.lockAuthorize(resource, principal, false);
        
        if (this.roleManager.hasRole(principal, RoleManager.ROOT))
            return;
        
        aclAuthorize(principal, resource, ALL_AUTH_PRIVILEGES);
    }
    


    
    private static final RepositoryAction[] UNLOCK_AUTH_PRIVILEGES = 
        new RepositoryAction[] {Privilege.ALL, Privilege.WRITE};


    /**
     * <ul>
     *   <li>privilege WRITE or ALL in Acl + resource not locked by another principal
     *   <li>Role ROOT
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizeUnlock(String uri, Principal principal) 
        throws AuthenticationException, AuthorizationException, ReadOnlyException,
        ResourceLockedException, IOException {

        checkReadOnly(principal);
        
        if (this.roleManager.hasRole(principal, RoleManager.ROOT))
            return;
        
        ResourceImpl resource = this.dao.load(uri);
        
        this.lockManager.lockAuthorize(resource, principal, false);

        aclAuthorize(principal, resource, UNLOCK_AUTH_PRIVILEGES);
    }


    private static final RepositoryAction[] DELETE_AUTH_PRIVILEGES = 
        new RepositoryAction[] {Privilege.ALL};


    /**
     * <ul>
     *   <li>Privilege ALL in ACL + parent not locked
     *   <li>Action WRITE on parent
     *   <li>+ resource tree not locked by another principal
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizeDelete(String uri, Principal principal) 
        throws AuthenticationException, AuthorizationException, ReadOnlyException, 
        ResourceLockedException, IOException {

        checkReadOnly(principal);

        Resource resource = this.dao.load(uri);
        
        this.lockManager.lockAuthorize(resource, principal, true);
        
        try {
            authorizeWrite(URIUtil.getParentURI(uri), principal);
            return;
        } catch (Exception e) {
            // Continue..
        }

        aclAuthorize(principal, resource, DELETE_AUTH_PRIVILEGES);
    }
    

    private static final RepositoryAction[] ADMIN_AUTH_PRIVILEGES = 
        new RepositoryAction[] {Privilege.ALL};


    /**
     * All of:
     * <ul>
     *   <li>Action WRITE
     *   <li>Action ALL or role ROOT
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizePropertyEditAdminRole(String uri, Principal principal) 
        throws AuthenticationException, AuthorizationException,
        ResourceLockedException, IOException {
        if (principal == null) {
            throw new AuthorizationException(
                "NULL principal not authorized to edit properties using admin privilege ");
        }
        
        if (this.roleManager.hasRole(principal, RoleManager.ROOT)) {
            return;
        }

        Resource resource = this.dao.load(uri);
        aclAuthorize(principal, resource, ADMIN_AUTH_PRIVILEGES);
        authorizeWrite(uri, principal);
        
    }


    /**
     * All of:
     * <ul>
     *   <li>Action WRITE
     *   <li>Role ROOT
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizePropertyEditRootRole(String uri, Principal principal)
        throws AuthenticationException, AuthorizationException,
        ResourceLockedException, IOException {

        if (!this.roleManager.hasRole(principal, RoleManager.ROOT))
            throw new AuthorizationException();
        
        authorizeWrite(uri, principal);
        
    }
    
    /**
     * All of:
     * <ul>
     *   <li>Action READ on source tree
     *   <li>Action CREATE on destination
     *   <li>if overwrite, action DELETE on dest
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizeCopy(String srcUri, String destUri, 
            Principal principal, boolean deleteDestination) 
        throws AuthenticationException, AuthorizationException, ReadOnlyException,
        ResourceLockedException, IOException {

        checkReadOnly(principal);

        authorizeRead(srcUri, principal);

        Resource resource = this.dao.load(srcUri);

        if (resource.isCollection()) {
            String[] uris = this.dao.discoverACLs(srcUri);
            for (int i = 0; i < uris.length; i++) {
                authorizeRead(uris[i], principal);
            }
        }

        String destParentUri = URIUtil.getParentURI(destUri);
        authorizeCreate(destParentUri, principal);

        if (deleteDestination) authorizeDelete(destUri, principal);
    }
    

    /**
     * All of:
     * <ul>
     *   <li>COPY action
     *   <li>Action DELETE on source
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizeMove(String srcUri, String destUri,
            Principal principal, boolean deleteDestination) 
        throws AuthenticationException, AuthorizationException, ReadOnlyException,
        ResourceLockedException, IOException {

        checkReadOnly(principal);

        authorizeCopy(srcUri, destUri, principal, deleteDestination);
        authorizeDelete(srcUri, principal);
    }

    
    /**
     * A principal is granted access if one of these conditions are met for one
     * of the privileges supplied:
     * 
     * <p>1) (ALL, privilege) is present in the resource's ACL.<br> 
     * NOTE: This is limited to read privileges
     * 
     * <p>The rest requires that the user is authenticated:
     * 
     * <p>2) principal != null and (AUTHENTICATED, privilege) is present
     * 
     * <p>The rest is meaningless if principalList == null:
     * 
     * <p>3) Principal is resource owner and (OWNER, privilege) is present in
     * the resource's ACL
     * 
     * <p>4) (principal, privilege) is present in the resource's ACL
     * 
     * <p>5) (g, privilege) is present in the resource's ACL, where g is a group
     * identifier and the user is a member of that group
     **/
    private void aclAuthorize(Principal principal, Resource resource, RepositoryAction[] privileges) 
        throws AuthenticationException, AuthorizationException {
        
        Acl acl = resource.getAcl();

        for (int i = 0; i < privileges.length; i++) {
            RepositoryAction privilege = privileges[i];
            Set principalSet = acl.getPrincipalSet(privilege);
            
            // Dont't need to test the conditions if (principalSet == null)
            if (principalSet == null || principalSet.size() == 0) {
                continue;
            }

            // Condition 1:
            if (principalSet.contains(PseudoPrincipal.ALL)) {
                return;
            }

            // If not condition 1 - needs to be authenticated
            if (principal == null) {
                continue;
            }

            // Condition 2:
            if (principalSet.contains(PseudoPrincipal.AUTHENTICATED)) {
                return;
            }

            // Condition 3:
            if (resource.getOwner().equals(principal)
                && principalSet.contains(PseudoPrincipal.OWNER)) {
                return;
            }

            // Condition 4:

            if (principalSet.contains(principal)) {
                return;
            }
        }

        // At this point a principal should always be available:
        if (principal == null) throw new AuthenticationException();
            
        for (int i = 0; i < privileges.length; i++) {
            RepositoryAction action = privileges[i];
            Set principalSet = acl.getPrincipalSet(action);
            
            // Condition 5:
            if (groupMatch(principalSet, principal)) {
                return;
            }
        }
       throw new AuthorizationException();
    }
    

    private boolean groupMatch(Set principalList, Principal principal) {

        for (Iterator i = principalList.iterator(); i.hasNext();) {
            Principal p = (Principal) i.next();

            if (p.getType() == Principal.TYPE_GROUP) {
                if (this.principalManager.isMember(principal, p)) {
                    return true;
                }
            }
        }
        return false;
    }


    public void setPrincipalManager(PrincipalManager principalManager) {
        this.principalManager = principalManager;
    }

    public void setRoleManager(RoleManager roleManager) {
        this.roleManager = roleManager;
    }

    public void setDao(DataAccessor dao) {
        this.dao = dao;
    }

    public void setLockManager(LockManager lockManager) {
        this.lockManager = lockManager;
    }

}
