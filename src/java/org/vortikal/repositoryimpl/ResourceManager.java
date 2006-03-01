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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import org.vortikal.repository.AclException;
import org.vortikal.repository.AuthorizationException;
import org.vortikal.repository.IllegalOperationException;
import org.vortikal.repository.PrivilegeDefinition;
import org.vortikal.repository.ResourceLockedException;
import org.vortikal.repositoryimpl.dao.DataAccessor;
import org.vortikal.security.AuthenticationException;
import org.vortikal.security.InvalidPrincipalException;
import org.vortikal.security.Principal;
import org.vortikal.security.PrincipalManager;
import org.vortikal.security.roles.RoleManager;
import org.vortikal.util.repository.ContentTypeHelper;
import org.vortikal.util.repository.MimeHelper;


public class ResourceManager {

    private PrincipalManager principalManager;
    private RoleManager roleManager;
    private DataAccessor dao;


    public ResourceManager(PrincipalManager principalManager,
                           RoleManager roleManager, DataAccessor dao) {
        this.principalManager = principalManager;
        this.roleManager = roleManager;
        this.dao = dao;
    }
    

    public void authorize(Resource resource, Principal principal, String privilege)
        throws IOException, AuthenticationException, AuthorizationException {
        
        resource.getACL().authorize(principal, privilege, resource,
                                    this.principalManager, this.roleManager);
    }
    
    public void authorizeRecursively(Resource resource, Principal principal,
                                     String privilege)
        throws IOException, AuthenticationException, AuthorizationException {

        this.authorize(resource, principal, privilege);
        if (resource instanceof Collection) {
            String[] uris = this.dao.discoverACLs(resource);
            for (int i = 0; i < uris.length; i++) {
                Resource ancestor = this.dao.load(uris[i]);
                this.authorize(ancestor, principal, privilege);
            }
        }
    }
    

    public void lockAuthorize(Resource resource, Principal principal, String privilege)
                  throws ResourceLockedException, IOException, AuthenticationException {
        if (resource.getLock() != null) {

            if (!org.vortikal.repository.PrivilegeDefinition.WRITE.equals(
                    privilege)) {
                return;
            }

            if (principal == null) {
                throw new AuthenticationException();
            }

            if (!resource.getLock().getUser().equals(principal.getQualifiedName())) {
                throw new org.vortikal.repository.ResourceLockedException();
            }
        }
    }

    public void lockAuthorizeRecursively(Collection collection, Principal principal,
                                         String privilege) 
        throws ResourceLockedException, IOException, AuthenticationException {

        this.lockAuthorize(collection, principal, privilege);

        String[] uris = this.dao.discoverLocks(collection);

        for (int i = 0; i < uris.length;  i++) {
            Resource ancestor = this.dao.load(uris[i]);
            this.lockAuthorize(ancestor, principal, privilege);
        }
    }



    public String lockResource(Resource resource, Principal principal,
                               String ownerInfo, String depth,
                               int desiredTimeoutSeconds, boolean refresh)
        throws AuthenticationException, AuthorizationException, 
            ResourceLockedException, IOException {


        this.authorize(resource, principal,
            org.vortikal.repository.PrivilegeDefinition.WRITE);

        if (resource.getLock() != null) {
            this.lockAuthorize(resource, principal,
                org.vortikal.repository.PrivilegeDefinition.WRITE);
        }

        if (!refresh) {
            resource.setLock(null);
        }

        if (resource.getLock() == null) {
            resource.setLock(new Lock(principal, ownerInfo, depth,
                new Date(System.currentTimeMillis() +
                         (desiredTimeoutSeconds * 1000))));
        } else {
            resource.setLock(
                new Lock(resource.getLock().getLockToken(), principal.getQualifiedName(),
                         ownerInfo, depth, 
                         new Date(System.currentTimeMillis() + (desiredTimeoutSeconds * 1000))));
        }
        this.dao.store(resource);
        return resource.getLock().getLockToken();
    }

    public void unlockResource(Resource resource, Principal principal, String lockToken)
        throws AuthenticationException, AuthorizationException, 
            ResourceLockedException, IOException {

        this.authorize(
            resource, principal, org.vortikal.repository.PrivilegeDefinition.WRITE);

        if (resource.getLock() != null) {
            if (!this.roleManager.hasRole(principal.getQualifiedName(), RoleManager.ROOT)) {
                this.lockAuthorize(resource, principal,
                                   org.vortikal.repository.PrivilegeDefinition.WRITE);
            }
            resource.setLock(null);
            this.dao.store(resource);
        }
    }
    

    public void deleteResource(Resource resource, Principal principal)
        throws AuthorizationException, AuthenticationException, 
            ResourceLockedException, IOException {
        
        if (resource.getLock() != null) {
            // XXX: remove authorization here, 
            this.lockAuthorize(resource, principal,
                org.vortikal.repository.PrivilegeDefinition.WRITE);
        }
        this.dao.delete(resource);
    }


    /**
     * Creates a collection with an inherited ACL
     *
     */
    public Resource createCollection(Collection parent, Principal principal, String path)
        throws IllegalOperationException, AuthenticationException, 
            AuthorizationException, AclException, IOException {
        return this.createCollection(parent, principal, principal.getQualifiedName(), path,
            new ACL(new HashMap()), true);
    }

    /**
     * Creates a collection with a specified owner and (possibly inherited) ACL.
     */
    public Resource createCollection(Collection parent, Principal principal, String owner,
        String path, ACL acl, boolean inheritedACL)
        throws IllegalOperationException, AuthenticationException, 
            AuthorizationException, AclException, IOException {

        this.authorize(parent, principal, PrivilegeDefinition.WRITE);

        Resource r = new Collection(path, owner, principal.getQualifiedName(),
                principal.getQualifiedName(), new ACL(new HashMap()),
                true, null, dao, this.principalManager, new String[] {  });

        Date now = new Date();

        r.setCreationTime(now);
        r.setContentLastModified(now);
        r.setPropertiesLastModified(now);
        this.dao.store(r);
        r = this.dao.load(path);

        if (!inheritedACL) {
            //acl.setResource(r);
            r.setInheritedACL(false);
            this.storeACL(r, principal, acl.toAceList(r));
        }

        parent.addChildURI(r.getURI());

        // Update timestamps:
        parent.setContentLastModified(new Date());
        parent.setPropertiesLastModified(new Date());

        // Update principal info:
        parent.setContentModifiedBy(principal.getQualifiedName());
        parent.setPropertiesModifiedBy(principal.getQualifiedName());

        this.dao.store(parent);

        return r;
    }


    public Resource create(Collection parent, Principal principal, String path)
        throws IllegalOperationException, AuthenticationException, 
            AuthorizationException, AclException, IOException {
        return this.create(parent, principal, principal, path,
            new ACL(new HashMap()), true);
    }

    public Resource create(Collection parent, Principal principal,
                           Principal owner, String path, ACL acl, boolean inheritedACL)
        throws IllegalOperationException, AuthenticationException, 
            AuthorizationException, AclException, IOException {
        this.authorize(parent, principal, PrivilegeDefinition.WRITE);

        String childName = path.substring(path.indexOf(parent.getURI()));

        /* FIXME: if write access = "dav:all": who creates the resource? */
        if (principal == null) {
            throw new AuthenticationException(
                "Principal must be specified in order to create a resource");
        }

        Resource r = new Document(path, owner.getQualifiedName(), principal.getQualifiedName(),
                principal.getQualifiedName(), new ACL(new HashMap()),
                true, null, this.dao, this.principalManager);

        Date now = new Date();

        r.setCreationTime(now);
        r.setContentLastModified(now);
        r.setPropertiesLastModified(now);

        r.setContentType(MimeHelper.map(r.getName()));

        this.dao.store(r);

        try {
            r = this.dao.load(path);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }

        if (!inheritedACL) {
            r.setInheritedACL(false);
            this.storeACL(r, principal, acl.toAceList(r));
        }

        parent.addChildURI(r.getURI());

        // Update timestamps:
        parent.setContentLastModified(new Date());
        parent.setPropertiesLastModified(new Date());

        // Update principal info:
        parent.setContentModifiedBy(principal.getQualifiedName());
        parent.setPropertiesModifiedBy(principal.getQualifiedName());

        this.dao.store(parent);

        return r;
    }


    public void copy(Principal principal, Resource resource, String destUri,
                     boolean preserveACL, boolean preserveOwner)
        throws IllegalOperationException, AuthenticationException, 
            AuthorizationException, IOException, ResourceLockedException, 
            AclException {

        ACL acl = (!preserveACL || resource.isInheritedACL())
            ? new ACL(new HashMap()) : resource.getACL();

        boolean aclInheritance = (!preserveACL || resource.isInheritedACL());

        Principal owner = (preserveOwner) ?
            principalManager.getPrincipal(resource.getOwner()) : principal;

        String parentURI = Resource.getParent(destUri);
        Collection parent = (Collection) this.dao.load(parentURI);


        if (resource instanceof Collection) {

            Collection child = (Collection) this.createCollection(parent,
                principal, owner.getQualifiedName(),
                destUri, acl, aclInheritance);

            child.setProperties(resource.getPropertyDTOs());
            dao.store(child);

            Resource[] children = this.dao.loadChildren((Collection) resource);
            for (int i = 0; i < children.length; i++) {

                Resource r = children[i];
                this.copy(principal, r,
                    child.getURI() + "/" +
                    r.getURI().substring(r.getURI().lastIndexOf("/") + 1),
                           preserveACL, preserveOwner);
            }

            return;
        }

        Document src = (Document) resource;
        Document doc = (Document) this.create(parent, principal, owner, destUri, acl,
                                              aclInheritance);

        doc.setContentType(src.getContentType());
        doc.setContentLocale(src.getContentLocale());
        doc.setCharacterEncoding(src.getCharacterEncoding());
        doc.setProperties(src.getPropertyDTOs());

        dao.store(doc);

        InputStream input = this.getResourceInputStream(src, owner, PrivilegeDefinition.READ);

        OutputStream output = null;

        output = dao.getOutputStream(doc);

        byte[] buf = new byte[1024];
        int bytesRead = 0;

        while ((bytesRead = input.read(buf)) > 0) {
            output.write(buf, 0, bytesRead);
        }

        input.close();
        output.close();
    }


    public void delete(Resource resource, Principal principal)
        throws AuthorizationException, AuthenticationException, 
            ResourceLockedException, IOException {
        if (resource.isCollection()) {
            Collection collection = (Collection) resource;
            if (collection.getLock() != null) {
                this.lockAuthorizeRecursively(collection, principal, PrivilegeDefinition.WRITE);
            }
        }
        this.dao.delete(resource);
    }


    public void storeProperties(Resource resource, Principal principal,
                                org.vortikal.repository.Resource dto)
        throws AuthenticationException, AuthorizationException, 
            ResourceLockedException, IllegalOperationException, IOException {

        this.authorize(resource, principal, PrivilegeDefinition.WRITE);
        this.lockAuthorize(resource, principal, PrivilegeDefinition.WRITE);
        
        if (!resource.getOwner().equals(dto.getOwner().getQualifiedName())) {
            /* Attempt to take ownership, only the owner of a parent
             * resource may do that, so do it in a secure manner: */
            this.setResourceOwner(resource, principal, dto, dto.getOwner().getQualifiedName());
        }

        if (dto.getOverrideLiveProperties()) {
            resource.setPropertiesLastModified(dto.getPropertiesLastModified());
            resource.setContentLastModified(dto.getContentLastModified());
            resource.setCreationTime(dto.getCreationTime());

        } else {
            resource.setPropertiesLastModified(new Date());
            resource.setPropertiesModifiedBy(principal.getQualifiedName());
        }
        
        if (!resource.isCollection()) {

            resource.setContentType(dto.getContentType());
            resource.setCharacterEncoding(null);
            ((Document) resource).setContentLocale(dto.getContentLocale());

            if ((resource.getContentType() != null)
                && ContentTypeHelper.isTextContentType(resource.getContentType()) &&
                (dto.getCharacterEncoding() != null)) {
                try {
                    /* Force checking of encoding */
                    new String(new byte[0], dto.getCharacterEncoding());

                    resource.setCharacterEncoding(dto.getCharacterEncoding());
                } catch (java.io.UnsupportedEncodingException e) {
                    // FIXME: Ignore unsupported character encodings?
                }
            }

        }

        resource.setDisplayName(dto.getDisplayName());
        resource.setProperties(dto.getProperties());
        
        this.dao.store(resource);
    }
    


    protected void setResourceOwner(Resource resource, Principal principal,
        org.vortikal.repository.Resource dto, String owner)
        throws AuthorizationException, IllegalOperationException, IOException {
        if ((owner == null) || owner.trim().equals("")) {
            throw new IllegalOperationException(
                "Unable to set owner of resource " + this +
                ": invalid owner: '" + owner + "'");
        }

        /*
         * Only principals of the ROOT role or owners are allowed to
         * set owner:
         */
        if (!(this.roleManager.hasRole(principal.getQualifiedName(), RoleManager.ROOT) ||
              principal.getQualifiedName().equals(resource.getOwner()))) {
            throw new AuthorizationException(
                "Principal " + principal.getQualifiedName()
                + " is not allowed to set owner of "
                + "resource " + resource.getURI());
        }

        Principal principal2 = null;
        
        try {
            principal2 = principalManager.getPrincipal(owner);
        } catch (InvalidPrincipalException e) {
            throw new IllegalOperationException(
                "Unable to set owner of resource " + resource.getURI()
                + ": invalid owner: '" + owner + "'");
        }
        
        if (!principalManager.validatePrincipal(principal2)) {
            throw new IllegalOperationException(
                "Unable to set owner of resource " + resource.getURI()
                + ": invalid owner: '" + owner + "'");
        }

        resource.setOwner(owner);
    }


    public void storeACL(Resource resource, Principal principal,
                         org.vortikal.repository.Ace[] aceList)
        throws AuthorizationException, AuthenticationException, 
            IllegalOperationException, IOException, AclException {

        ACL acl = ACL.buildACL(aceList, principalManager);
        resource.setACL(acl);

        //this.acl.setResource(this);

        /* If the first ACE has set inheritance, we know that the
         * whole ACL has valid inheritance (ACL.validateACL() ensures
         * this), so we can go ahead and set it here: */
        boolean inheritedACL = aceList[0].getInheritedFrom() != null;

        if (!"/".equals(resource.getURI()) && inheritedACL) {
            /* When the ACL is inherited, make our ACL a copy of our
             * parent's ACL, since the supplied one may contain other
             * ACEs than the one we now inherit from. */
            try {
                ACL parentACL = (ACL) this.dao.load(resource.getParentURI()).getACL().clone();

                //parentACL.setResource(this);
                resource.setACL(parentACL);
            } catch (CloneNotSupportedException e) {
            }
        }

        try {
            resource.setInheritedACL(inheritedACL);
            resource.setDirtyACL(true);

            this.dao.store(resource);
        } catch (Exception e) {
            
            throw new IOException(e.getMessage());
        } finally {
            resource.setDirtyACL(false);
        }
    }


    public InputStream getResourceInputStream(Document resource,
                                              Principal principal, String privilege)
        throws AuthenticationException, AuthorizationException, IOException, 
            ResourceLockedException {
        this.authorize(resource, principal, privilege);
        this.lockAuthorize(resource, principal, privilege);

        return dao.getInputStream(resource);
    }

    public OutputStream getResourceOutputStream(Document resource, Principal principal)
        throws AuthenticationException, AuthorizationException, IOException, 
            ResourceLockedException {
        this.authorize(resource, principal, PrivilegeDefinition.WRITE);
        this.lockAuthorize(resource, principal, PrivilegeDefinition.WRITE);

        resource.setContentLastModified(new Date());
        resource.setContentModifiedBy(principal.getQualifiedName());
        this.dao.store(resource);

        return dao.getOutputStream(resource);
    }


}
