/* Copyright (c) 2006, 2007, University of Oslo, Norway
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.vortikal.repository.store.DataAccessor;
import org.vortikal.security.AuthenticationException;
import org.vortikal.security.Principal;
import org.vortikal.security.PrincipalFactory;
import org.vortikal.security.PrincipalManager;
import org.vortikal.security.roles.RoleManager;

/**
 * Manager for authorizing principals at specific authorization level.
 */
public final class AuthorizationManager {

    private RoleManager roleManager;
    private PrincipalManager principalManager;
    private DataAccessor dao;
    
    private boolean readOnly = false;

    private Map<Privilege, List<Pattern>> usersBlacklist = 
            new EnumMap<Privilege, List<Pattern>>(Privilege.class);
    private Map<Privilege, List<Pattern>> groupsBlacklist =
            new EnumMap<Privilege, List<Pattern>>(Privilege.class);

    /**
     * Map of a single privilege P to the set of privileges A{P,..} in which
     * all privileges shall imply permissions equal to or broader than P. The
     * set A always includes the privilege P itself.
     */
    private static final Map<Privilege, Privilege[]> PRIVILEGE_HIERARCHY =
                           new EnumMap<Privilege, Privilege[]>(Privilege.class);
    static {
        PRIVILEGE_HIERARCHY.put(Privilege.READ_PROCESSED, new Privilege[] {
                Privilege.READ_PROCESSED,
                Privilege.READ,
                Privilege.READ_WRITE,
                Privilege.READ_WRITE_UNPUBLISHED,
                Privilege.ALL
        });
        PRIVILEGE_HIERARCHY.put(Privilege.READ, new Privilege[] {
                Privilege.READ,
                Privilege.READ_WRITE,
                Privilege.READ_WRITE_UNPUBLISHED,
                Privilege.ALL
        });
        PRIVILEGE_HIERARCHY.put(Privilege.ADD_COMMENT, new Privilege[] {
                Privilege.ADD_COMMENT,
                Privilege.READ_WRITE,
                Privilege.READ_WRITE_UNPUBLISHED,
                Privilege.ALL
        });
        PRIVILEGE_HIERARCHY.put(Privilege.READ_WRITE_UNPUBLISHED, new Privilege[] {
                Privilege.READ_WRITE_UNPUBLISHED,
                Privilege.READ_WRITE,
                Privilege.ALL
        });
        PRIVILEGE_HIERARCHY.put(Privilege.READ_WRITE, new Privilege[] {
                Privilege.READ_WRITE,
                Privilege.ALL
        });
        PRIVILEGE_HIERARCHY.put(Privilege.ALL, new Privilege[] {
                Privilege.ALL
        });
    }
    
    
    /**
     * Authorizes a principal for a root role action. Should throw an
     * AuthorizationException if the principal in question does not
     * have root privileges.
     */
    public void authorizeRootRoleAction(Principal principal) throws AuthorizationException {
        if (!this.roleManager.hasRole(principal, RoleManager.Role.ROOT)) {
            throw new AuthorizationException(
                "Principal " + principal + " not authorized for root role action.");
        }
    }
    

    /**
     * Authorizes a principal for a given action on a resource
     * URI. Equivalent to calling one of the <code>authorizeYYY(uri,
     * principal)</code> methods (with <code>YYY</code> mapping to one of
     * the actions).
     *
     * @param uri a resource URI
     * @param action the action to perform. One of the action types
     * defined in {@link #ACTION_AUTHORIZATIONS}.
     * @param principal the principal performing the action
     */
    public void authorizeAction(Path uri, RepositoryAction action, 
            Principal principal) throws AuthenticationException, AuthorizationException,
            IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be NULL");
        }
        if (action == null) {
            throw new IllegalArgumentException("Action cannot be NULL");
        }
        if (RepositoryAction.COPY == action || RepositoryAction.MOVE == action) {
            throw new IllegalArgumentException(
                    "Unable to authorize for COPY/MOVE actions");
        }
        
        switch (action) {
        case UNEDITABLE_ACTION:
            throw new AuthorizationException(uri + ": uneditable");
        case READ_PROCESSED:
            authorizeReadProcessed(uri, principal);
            break;
        case READ:
            authorizeRead(uri, principal);
            break;
        case CREATE:
            authorizeCreate(uri, principal);
            break;
        case WRITE:
        case READ_WRITE:
            authorizeReadWrite(uri, principal);
            break;
        case EDIT_COMMENT:
            authorizeEditComment(uri, principal);
            break;
        case ADD_COMMENT:
            authorizeAddComment(uri, principal);
            break;
        case ALL:
        case WRITE_ACL:
            authorizeAll(uri, principal);
            break;
        case UNLOCK:
            authorizeUnlock(uri, principal);
            break;
        case DELETE:
            authorizeDelete(uri, principal);
            break;
        case READ_WRITE_UNPUBLISHED:
            authorizeReadWriteUnpublished(uri, principal);
            break;
        case CREATE_UNPUBLISHED:
            authorizeCreateUnpublished(uri, principal);
            break;
        case DELETE_UNPUBLISHED:
            authorizeDeleteUnpublished(uri, principal);
            break;
        case PUBLISH_UNPUBLISH:
            authorizePublishUnpublish(uri, principal);
            break;
        case REPOSITORY_ADMIN_ROLE_ACTION:
            authorizePropertyEditAdminRole(uri, principal);
            break;
        case REPOSITORY_ROOT_ROLE_ACTION:
            authorizePropertyEditRootRole(uri, principal);
            break;
            default:
                throw new IllegalArgumentException("Cannot authorize action " + action);
        }
    }

    public void authorizeReadRevision(Principal principal, Revision revision) {
        if (this.roleManager.hasRole(principal, RoleManager.Role.ROOT) ||
                this.roleManager.hasRole(principal, RoleManager.Role.READ_EVERYTHING)) {
            return;
        }
        
        aclAuthorize(revision.getAcl(), principal, PRIVILEGE_HIERARCHY.get(Privilege.READ));
    }

    /*
    public void authorizeWriteRevision(Principal principal, Revision revision) {
        if (this.roleManager.hasRole(principal, RoleManager.Role.ROOT) ||
                this.roleManager.hasRole(principal, RoleManager.Role.READ_EVERYTHING))
            return;
        
        aclAuthorize(revision.getAcl(), READ_WRITE_AUTH_PRIVILEGES, principal);
    }
    
    public void authorizeDeleteRevision(Principal principal, Revision revision) {
        if (this.roleManager.hasRole(principal, RoleManager.Role.ROOT) ||
                this.roleManager.hasRole(principal, RoleManager.Role.READ_EVERYTHING))
            return;
        
        aclAuthorize(revision.getAcl(), READ_WRITE_AUTH_PRIVILEGES, principal);
    }
    */
    
    /**
     * <ul>
     *   <li>Privilege READ_PROCESSED, READ, READ_WRITE, READ_WRITE_UNPUBLISHED or ALL in ACL
     *   <li>Role ROOT or READ_EVERYTHING
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizeReadProcessed(Path uri, Principal principal) 
        throws AuthenticationException, AuthorizationException, IOException, ResourceNotFoundException {

        ResourceImpl resource = loadResource(uri);

        if (this.roleManager.hasRole(principal, RoleManager.Role.ROOT) ||
                this.roleManager.hasRole(principal, RoleManager.Role.READ_EVERYTHING)) {
            return;
        }

        aclAuthorize(resource, principal, PRIVILEGE_HIERARCHY.get(Privilege.READ_PROCESSED));
    }

    /**
     * <ul>
     *   <li>Privilege READ, READ_WRITE, READ_WRITE_UNPUBLISHED or ALL in ACL
     *   <li>Role ROOT or READ_EVERYTHING
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizeRead(Path uri, Principal principal) 
        throws AuthenticationException, AuthorizationException,
        IOException, ResourceNotFoundException {

        ResourceImpl resource = loadResource(uri);

        if (this.roleManager.hasRole(principal, RoleManager.Role.ROOT) ||
                this.roleManager.hasRole(principal, RoleManager.Role.READ_EVERYTHING)) {
            return;
        }
        aclAuthorize(resource, principal, PRIVILEGE_HIERARCHY.get(Privilege.READ));
    }

    /**
     * TODO: CREATE is used for old bind ("bare opprett") which is currently disabled
     *       by not having the privilege in CREATE_AUTH_PRIVILEGES. Needs other changes
     *       as part of VTK-2135 before it can be used again
     * <ul>
     *   <li>Privilege READ_WRITE or ALL on resource
     *   <li>Role ROOT
     * </ul>
     */
    public void authorizeCreate(Path uri, Principal principal)
        throws AuthenticationException, AuthorizationException, ReadOnlyException, 
        IOException, ResourceNotFoundException {

        checkReadOnly(principal);

        ResourceImpl resource = loadResource(uri);
        
        if (this.roleManager.hasRole(principal, RoleManager.Role.ROOT)) {
            return;
        }
        aclAuthorize(resource, principal, Privilege.ALL,
                                          Privilege.READ_WRITE);
    }
    
    /**
     * TODO: CREATE is used for old bind ("bare opprett") which is currently disabled
     *       by not having the privilege in CREATE_AUTH_PRIVILEGES. Needs other changes
     *       as part of VTK-2135 before it can be used again
     * <ul>
     *   <li>Privilege READ_WRITE, READ_WRITE_UNPUBLISHED or ALL on resource
     *   <li>Role ROOT
     * </ul>
     */
    public void authorizeCreateUnpublished(Path uri, Principal principal)
        throws AuthenticationException, AuthorizationException, ReadOnlyException, 
        IOException, ResourceNotFoundException {

        checkReadOnly(principal);

        ResourceImpl resource = loadResource(uri);
        
        if (this.roleManager.hasRole(principal, RoleManager.Role.ROOT)) {
            return;
        }
        aclAuthorize(resource, principal, PRIVILEGE_HIERARCHY.get(Privilege.READ_WRITE_UNPUBLISHED));
    }
    
    /**
     * <ul>
     *   <li>Privilege READ_WRITE or ALL in ACL
     *   <li>Role ROOT
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizeReadWrite(Path uri, Principal principal)
        throws AuthenticationException, AuthorizationException, ReadOnlyException,
        IOException, ResourceNotFoundException {

        checkReadOnly(principal);

        ResourceImpl resource = loadResource(uri);
        
        if (this.roleManager.hasRole(principal, RoleManager.Role.ROOT)) {
            return;
        }
        aclAuthorize(resource, principal, PRIVILEGE_HIERARCHY.get(Privilege.READ_WRITE));
    }
    
    /**
     * <ul>
     *   <li>Privilege READ_WRITE, READ_WRITE_UNPUBLISHED or ALL in ACL
     *   <li>Role ROOT
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizeReadWriteUnpublished(Path uri, Principal principal)
        throws AuthenticationException, AuthorizationException, ReadOnlyException,
        IOException, ResourceNotFoundException {

        checkReadOnly(principal);

        ResourceImpl resource = loadResource(uri);
        
        if (this.roleManager.hasRole(principal, RoleManager.Role.ROOT)) {
            return;
        }
        aclAuthorize(resource, principal, PRIVILEGE_HIERARCHY.get(Privilege.READ_WRITE_UNPUBLISHED));
    }
    
    /**
     * <ul>
     *   <li>Privilege READ_WRITE or ALL in ACL
     *   <li>Role ROOT
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizePublishUnpublish(Path uri, Principal principal)
        throws AuthenticationException, AuthorizationException, ReadOnlyException,
        IOException, ResourceNotFoundException {
        authorizeReadWrite(uri, principal);
    }
    
    /**
     * <ul>
     *   <li>Privilege ALL, READ_WRITE, READ_WRITE_UNPUBLISHED or ADD_COMMENT in ACL
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizeAddComment(Path uri, Principal principal)
        throws AuthenticationException, AuthorizationException, ReadOnlyException,
        IOException, ResourceNotFoundException {

        checkReadOnly(principal);

        ResourceImpl resource = loadResource(uri);
        
        if (this.roleManager.hasRole(principal, RoleManager.Role.ROOT)) {
            return;
        }
        aclAuthorize(resource, principal, PRIVILEGE_HIERARCHY.get(Privilege.ADD_COMMENT));
    }
    
    /**
     * <ul>
     *   <li>Privilege ALL in ACL
     *   <li>Role ROOT
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizeEditComment(Path uri, Principal principal)
        throws AuthenticationException, AuthorizationException, ReadOnlyException,
        IOException, ResourceNotFoundException {

        checkReadOnly(principal);

        ResourceImpl resource = loadResource(uri);
        
        if (this.roleManager.hasRole(principal, RoleManager.Role.ROOT)) {
            return;
        }
        aclAuthorize(resource, principal, Privilege.ALL);
    }
    

    /**
     * <ul>
     *   <li>Privilege ALL in ACL
     *   <li>Role ROOT
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizeAll(Path uri, Principal principal)
        throws AuthenticationException, AuthorizationException, ReadOnlyException,
        IOException, ResourceNotFoundException {

        checkReadOnly(principal);
        
        ResourceImpl resource = loadResource(uri);
        
        if (this.roleManager.hasRole(principal, RoleManager.Role.ROOT)) {
            return;
        }
        aclAuthorize(resource, principal, Privilege.ALL);
    }
    
    /**
     * <ul>
     *   <li>Principal owns lock
     *   <li>Privilege ALL in Acl 
     *   <li>Role ROOT
     * </ul>
     */
    public void authorizeUnlock(Path uri, Principal principal) 
        throws AuthenticationException, AuthorizationException, ReadOnlyException,
        IOException, ResourceNotFoundException {

        checkReadOnly(principal);
        
        if (this.roleManager.hasRole(principal, RoleManager.Role.ROOT)) {
            return;
        }

        ResourceImpl resource = loadResource(uri);

        Lock lock = resource.getLock();
        if (lock == null) {
            return;
        }
        if (principal == null) {
            throw new AuthenticationException();
        }
        if (lock.getPrincipal().equals(principal)) {
            return;
        }

        aclAuthorize(resource, principal, Privilege.ALL);
    }


    /**
     * One of:
     * <ul>
     *   <li>Privilege READ_WRITE on parent
     *   <li>Privilege ALL on resource to be deleted.
     * </ul>
     * 
     * And resource must not be root resource.
     */
    public void authorizeDelete(Path uri, Principal principal) 
        throws AuthenticationException, AuthorizationException, ReadOnlyException, 
        IOException, ResourceNotFoundException {

        checkReadOnly(principal);

        if (uri.isRoot()) {
            // Not allowed to delete root resource.
            // Avoid sending null as Path to DAO layer (uri.getParent() below ..),
            // which results in a NullPointerException in Cache, hidden by catch(Exception) below.
            throw new AuthorizationException("Not allowed to delete root resource");
        }
        
        Resource resource = loadResource(uri);

        // Delete is authorized if either of these conditions hold:
        try {
            // 1. Principal has write permission on the parent resource, or
            authorizeReadWrite(uri.getParent(), principal);
            return;
        } catch (AuthorizationException e) {
            // Continue to #2
        }
        // 2. Principal has privilege ALL directly on the resource itself
        aclAuthorize(resource, principal, Privilege.ALL);
    }
    
    /**
     * One of:
     * <ul>
     *   <li>Privilege READ_WRITE or READ_WRITE_UNPUBLISHED on parent
     *   <li>Privilege ALL on resource to be deleted.
     * </ul>
     * 
     * And resource must not be root resource.
     */
    public void authorizeDeleteUnpublished(Path uri, Principal principal) 
        throws AuthenticationException, AuthorizationException, ReadOnlyException, 
        IOException, ResourceNotFoundException {

        checkReadOnly(principal);

        if (uri.isRoot()) {
            throw new AuthorizationException("Not allowed to delete root resource");
        }

        // Delete is authorized if either of these conditions hold:
        try {
            // 1. Principal has read-write-unpublished permission on the parent resource, or
            authorizeReadWriteUnpublished(uri.getParent(), principal);
            return;
        } catch (AuthorizationException e) {
            // Continue to #2
        }
        // 2. Principal has privilege ALL directly on the resource itself
        Resource resource = loadResource(uri);
        aclAuthorize(resource, principal, Privilege.ALL);
    }

    /**
     * All of:
     * <ul>
     *   <li>Action ALL
     *   <li>Role ROOT
     * </ul>
     * @return is authorized
     * @throws IOException
     */
    public void authorizePropertyEditAdminRole(Path uri, Principal principal) 
        throws AuthenticationException, AuthorizationException,
        IOException, ResourceNotFoundException {
        if (principal == null) {
            throw new AuthorizationException(
                "NULL principal not authorized to edit properties using admin privilege ");
        }
        
        if (this.roleManager.hasRole(principal, RoleManager.Role.ROOT)) {
            return;
        }
        checkReadOnly(principal);
        Resource resource = loadResource(uri);
        aclAuthorize(resource, principal, Privilege.ALL);
    }


    /**
     * All of:
     * <ul>
     *   <li>Role ROOT
     * </ul>
     */
    public void authorizePropertyEditRootRole(Path uri, Principal principal)
        throws AuthenticationException, AuthorizationException,
        IOException {
        // Check existence:
        loadResource(uri);

        if (this.roleManager.hasRole(principal, RoleManager.Role.ROOT)) {
            return;
        }
        throw new AuthorizationException();
    }
    
    /**
     * All of:
     * <ul>
     *   <li>Action READ on source tree
     *   <li>One of:
     *   <ul>
     *     <li>Action CREATE on destination if resource to copy is a collection
     *     <li>Action CREATE_UNPUBLISHED on destination if resource to copy is not a collection.
     *   </ul>
     *   <li>If overwrite, action DELETE on dest.
     * </ul>
     * 
     */
    public void authorizeCopy(Path srcUri, Path destUri, 
            Principal principal, boolean deleteDestination) 
        throws AuthenticationException, AuthorizationException, ReadOnlyException,
        IOException, ResourceNotFoundException {

        checkReadOnly(principal);

        authorizeRead(srcUri, principal);

        Resource resource = loadResource(srcUri);

        if (resource.isCollection()) {
            Path[] uris = this.dao.discoverACLs(srcUri);
            for (int i = 0; i < uris.length; i++) {
                authorizeRead(uris[i], principal);
            }
            
            // For copy of collection we require full CREATE at destination
            authorizeCreate(destUri.getParent(), principal);
        } else {
            // If not collection, we allow if principal only has CREATE_UNPUBLISHED at minimum.
            authorizeCreateUnpublished(destUri.getParent(), principal);
        }

        if (deleteDestination) {
            Resource dest = this.dao.load(destUri);
            if (dest != null) {
                if (dest.isPublished()) {
                    authorizeDelete(destUri, principal);
                } else {
                    authorizeDeleteUnpublished(destUri, principal);
                }
            }
        }
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
    public void authorizeMove(Path srcUri, Path destUri,
            Principal principal, boolean deleteDestination) 
        throws AuthenticationException, AuthorizationException, ReadOnlyException,
        IOException {

        checkReadOnly(principal);

        authorizeDelete(srcUri, principal);

        boolean srcHasACLs = (this.dao.discoverACLs(srcUri).length > 0);
        if (srcHasACLs) {
            /* src has ACLs and move will therefore impact ACLs of sutbree rooted 
             * at destination.
             * Therefore require all privilege at destination (which would be required to 
             * create similar subtree at destination using other operations).
             * 
             *  All on destParentUri implies delete on destUri
             */
            
            Path destParentUri = destUri.getParent();
            authorizeAll(destParentUri, principal);                  
        } else {
            /* Source does not contain ACLs. 
             * Only need create (and possibly delete).
             */
            Path destParentUri = destUri.getParent();
            authorizeCreate(destParentUri, principal);
        }
        
        if (deleteDestination) {
            authorizeDelete(destUri, principal);
        }
    }
    
    /**
     * Authorizes a principal to perform a given action 
     * (or a set of actions) on a resource. 
     * 
     * Delegates to {@link #aclAuthorize(Acl, Privilege[], Principal)}
     * 
     * @param principal the principal performing an action
     * @param resource the resource in question
     * @param privileges the set of privileges to authorize for
     * @throws AuthenticationException if the principal is not authenticated 
     *  and the action requires a principal
     * @throws AuthorizationException if the principal is authenticated but 
     *  does not have sufficient privileges to perform the action
     */
    private void aclAuthorize(Resource resource, Principal principal, Privilege... privileges) 
        throws AuthenticationException, AuthorizationException {

        Acl acl = resource.getAcl();
        try {
            aclAuthorize(acl, principal, privileges);
        } catch (AuthenticationException e) {
            throw new AuthenticationException(
                    "Unauthenticated principal not authorized to access " 
                    + resource.getURI() + " for privilege(s) " 
                    + Arrays.asList(privileges));
            
        } catch (AuthorizationException e) {
            throw new AuthorizationException(
                    "Principal " + principal + " not authorized to access " 
                    + resource.getURI() + " for privilege(s) " 
                    + Arrays.asList(privileges));
        }
    }
    
    

    /**
     * Tests if a principal has the provided privilege according to the provided
     * ACL.a
     * 
     * @param principal The principal which shall be authorized for the privelege.
     * @param acl       The resource ACL.
     * @param privilege The privilege to be tested against ACL.
     * @return Returns <code>true</code> if principal has the privilege according
     *         to the provided ACL, <code>false</code> otherwise.
     */
    public boolean authorize(Principal principal, Acl acl, Privilege privilege) {
        if (this.roleManager.hasRole(principal, RoleManager.Role.ROOT)) {
            return true;
        }
        if (privilege == Privilege.READ_PROCESSED || privilege == Privilege.READ) {
            if (this.roleManager.hasRole(principal, RoleManager.Role.READ_EVERYTHING)) {
                return true;
            }
        }
        Privilege[] privs = PRIVILEGE_HIERARCHY.get(privilege);
        try {
            aclAuthorize(acl, principal, privs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * A principal is granted access if one of these conditions are met for one
     * of the privileges supplied:
     * 
     * <p>1) (ALL, privilege) is present in the ACL.<br> 
     * NOTE: This is limited to read privileges
     * 
     * <p>The rest requires that the user is authenticated:
     *  
     * <p>The rest is meaningless if principalList == null:
     * 
     * <p>2) (principal, privilege) is present in the resource's ACL
     * 
     * <p>3) (g, privilege) is present in the resource's ACL, where g is a group
     * identifier and the user is a member of that group
     */
    private void aclAuthorize(Acl acl, Principal principal, Privilege... privileges) {
        for (int i = 0; i < privileges.length; i++) {
            Privilege privilege = privileges[i];
            Set<Principal> principalSet = acl.getPrincipalSet(privilege);
            
            // Dont't need to test the conditions if principalSet is empty.
            if (principalSet.isEmpty()) {
                continue;
            }

            // Condition 1:
            if (principalSet.contains(PrincipalFactory.ALL)) {
                return;
            }

            // If not condition 1 - needs to be authenticated
            if (principal == null) {
                continue;
            }

            // Condition 2:

            if (principalSet.contains(principal)) {
                return;
            }
        }

        // At this point a principal should always be available:
        if (principal == null) {
            throw new AuthenticationException(
                    "Unauthenticated principal not authorized by ACL " 
                    + acl + " for any of privilege(s) " + Arrays.asList(privileges));
        }

        for (int i = 0; i < privileges.length; i++) {
            Privilege action = privileges[i];
            Set<Principal> principalSet = acl.getPrincipalSet(action);
            
            // Condition 3:
            if (groupMatch(principalSet, principal)) {
                return;
            }
        }
        throw new AuthorizationException(
                "Principal " + principal + " not authorized by ACL " 
                + acl + " for any of privilege(s) " + Arrays.asList(privileges));
    }

    // Load a resource directly from DAO.
    private ResourceImpl loadResource(Path uri) throws ResourceNotFoundException {
        ResourceImpl resource = this.dao.load(uri);
        if (resource == null) {
            throw new ResourceNotFoundException(uri);
        }
        return resource;
    }

    private boolean groupMatch(Set<Principal> principalList, Principal principal) {

        for (Principal p: principalList) {
            if (p.getType() == Principal.Type.GROUP) {
                if (this.principalManager.isMember(principal, p)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isBlackListed(Principal principal, Privilege action) {
        Map<Privilege, List<Pattern>> map = principal.isUser() ? this.usersBlacklist : this.groupsBlacklist;
        if (map == null) {
            return false;
        }
        List<Pattern> list = map.get(action);
        if (list == null) {
            return false;
        }
        for (Pattern pattern : list) {
            Matcher m = pattern.matcher(principal.getQualifiedName());
            if (m.find()) {
                return true;
            }
        }
        return false;
    }

    public boolean isValidAclEntry(Privilege action, Principal principal) {
        boolean valid;
        if (principal.getType() == Principal.Type.USER) {
            valid = this.principalManager.validatePrincipal(principal);
        } else if (principal.getType() == Principal.Type.GROUP) {
            valid = this.principalManager.validateGroup(principal);
        } else {
            valid = true;
        }
        if (isBlackListed(principal, action)) {
            valid = false;
        }
        return valid;
    }

    private void checkReadOnly(Principal principal) throws ReadOnlyException {
        if (isReadOnly() && !this.roleManager.hasRole(principal, RoleManager.Role.ROOT)) {
            throw new ReadOnlyException();
        }
    }
    
    public boolean isReadOnly() {
        return this.readOnly;
    }

    /**
     * Set repository read-only state. Setting this to <code>true</code> will
     * cause all actions that modify repository to be denied (except if principal
     * has root role).
     */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public void setPermissionBlacklist(Map<Privilege, List<String>> blacklist) {
        for (Privilege privilege : blacklist.keySet()) {
            List<String> principals = blacklist.get(privilege);
            for (String spec : principals) {
                String principal;
                boolean user;
                if (spec.startsWith("user:")) {
                    principal = spec.substring("user:".length());
                    user = true;
                } else if (spec.startsWith("group:")) {
                    principal = spec.substring("group:".length());
                    user = false;
                } else {
                    throw new IllegalArgumentException("Illegal principal specification: " + spec);
                }
                principal = principal.replaceAll("\\.", "\\\\.");
                principal = principal.replaceAll("\\*", ".*");
                Pattern pattern = Pattern.compile(principal);
                Map<Privilege, List<Pattern>> map = user ? this.usersBlacklist : this.groupsBlacklist;
                if (!map.containsKey(privilege)) {
                    map.put(privilege, new ArrayList<Pattern>());
                }
                map.get(privilege).add(pattern);
            }
        }
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
}
