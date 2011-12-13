/* Copyright (c) 2004, 2005, 2006, 2007, University of Oslo, Norway
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.vortikal.repository.Revision.Type;
import org.vortikal.repository.content.ContentImpl;
import org.vortikal.repository.content.ContentRepresentationRegistry;
import org.vortikal.repository.content.InputStreamWrapper;
import org.vortikal.repository.event.ACLModificationEvent;
import org.vortikal.repository.event.ContentModificationEvent;
import org.vortikal.repository.event.ResourceCreationEvent;
import org.vortikal.repository.event.ResourceDeletionEvent;
import org.vortikal.repository.event.ResourceModificationEvent;
import org.vortikal.repository.resourcetype.Content;
import org.vortikal.repository.search.QueryException;
import org.vortikal.repository.search.ResultSet;
import org.vortikal.repository.search.Search;
import org.vortikal.repository.search.Searcher;
import org.vortikal.repository.store.CommentDAO;
import org.vortikal.repository.store.ContentStore;
import org.vortikal.repository.store.DataAccessException;
import org.vortikal.repository.store.DataAccessor;
import org.vortikal.repository.store.RevisionStore;
import org.vortikal.repository.store.Revisions;
import org.vortikal.repository.store.Revisions.ChecksumWrapper;
import org.vortikal.security.AuthenticationException;
import org.vortikal.security.InvalidPrincipalException;
import org.vortikal.security.Principal;
import org.vortikal.security.PrincipalManager;
import org.vortikal.security.token.TokenManager;
import org.vortikal.util.io.StreamUtil;

/**
 * A semi-transactional implementation of the
 * <code>org.vortikal.repository.Repository</code> interface. (A transaction-aware
 * content-store implementation is needed to make repository fully transactional.)
 * 
 * Any operation that modifies the content store cannot be rolled back, so
 * most checks/verifications and DAO operations should be done before any content-store
 * operation.
 * 
 * XXX: implement locking of depth 'infinity' XXX: namespace locking/concurrency
 * XXX: Evaluate exception practice, handling and propagation
 * XXX: transaction demarcation
 * XXX: externalize caching
 * XXX: duplication of owner and inherited between resource and acl.
 */
public class RepositoryImpl implements Repository, ApplicationContextAware {

    private ApplicationContext context;
    private DataAccessor dao;
    private CommentDAO commentDAO;
    private RevisionStore revisionStore;
    private ContentStore contentStore;
    private ContentRepresentationRegistry contentRepresentationRegistry;
    private TokenManager tokenManager;
    private ResourceTypeTree resourceTypeTree;
    private RepositoryResourceHelper resourceHelper;
    private AuthorizationManager authorizationManager;
    private PrincipalManager principalManager;
    private Searcher searcher;
    private String id;
    private int maxComments = 1000;
    private PeriodicThread periodicThread;
    private int maxResourceChildren = 3000;
    private File tempDir;

    private Map<Privilege, List<Pattern>> usersBlacklist = 
            new EnumMap<Privilege, List<Pattern>>(Privilege.class);
    private Map<Privilege, List<Pattern>> groupsBlacklist =
            new EnumMap<Privilege, List<Pattern>>(Privilege.class);

    // Default value of 60 days before recoverable resources are purged from
    // trash can
    private int permanentDeleteOverdueLimitInDays = 60;

    public final static long LOCK_DEFAULT_TIMEOUT = 30 * 60 * 1000; // 30
    // minutes
    public final static long LOCK_MAX_TIMEOUT = LOCK_DEFAULT_TIMEOUT;

    private long lockDefaultTimeout = LOCK_DEFAULT_TIMEOUT;
    private long lockMaxTimeout = LOCK_DEFAULT_TIMEOUT;

    private static final int FILE_COPY_BUF_SIZE = 122880;
    
    private static Log searchLogger = LogFactory.getLog(RepositoryImpl.class.getName() + ".Search");
    private static Log trashLogger = LogFactory.getLog(RepositoryImpl.class.getName() + ".Trash");

    @Override
    public boolean isReadOnly() {
        return this.authorizationManager.isReadOnly();
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public boolean exists(String token, Path uri) throws IOException {

        return (this.dao.load(uri) != null);

    }

    @Override
    public Resource retrieve(String token, Path uri, boolean forProcessing) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI is NULL");
        }
        
        Principal principal = this.tokenManager.getPrincipal(token);

        ResourceImpl resource = this.dao.load(uri);

        if (resource == null) {
            throw new ResourceNotFoundException(uri);
        }
        
        if (forProcessing) {
            this.authorizationManager.authorizeReadProcessed(uri, principal);
        } else {
            this.authorizationManager.authorizeRead(uri, principal);
        }

        try {
            return (Resource) resource.clone();
        } catch (CloneNotSupportedException e) {
            throw new IOException("An internal error occurred: unable to " + "clone() resource: " + resource);
        }
    }
    
    
    @Override
    public Resource retrieve(String token, Path uri, boolean forProcessing,
            Revision revision) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, Exception {

        if (uri == null) {
            throw new IllegalArgumentException("URI is NULL");
        }
        if (revision == null) {
            throw new IllegalArgumentException("Revision is NULL");
        }
        Principal principal = this.tokenManager.getPrincipal(token);
        if (principal == null) {
            throw new AuthenticationException(
                    "Principal NULL not permitted to retrieve resource revisions");
        }

        ResourceImpl resource = this.dao.load(uri);

        if (resource == null) {
            throw new ResourceNotFoundException(uri);
        }
        
        if (forProcessing) {
            this.authorizationManager.authorizeReadProcessed(uri, principal);
        } else {
            this.authorizationManager.authorizeRead(uri, principal);
        }

        List<Revision> revisions = this.revisionStore.list(resource);
        boolean found = false;
        for (Revision r: revisions) {
            if (r.getID() == revision.getID()) {
                found = true;
                revision = r;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("No such revision: " + revision.getID());
        }
        
        this.authorizationManager.authorizeReadRevision(principal, revision);
        
        // Evaluate revision content (as content-modification)
        Content content = getContent(resource, revision);
        ResourceImpl result = this.resourceHelper.contentModification(resource, principal, content);
        result.setAcl(revision.getAcl());
        return result;
    }

    @Override
    public TypeInfo getTypeInfo(String token, Path uri) throws Exception {
        Principal principal = this.tokenManager.getPrincipal(token);

        ResourceImpl resource = this.dao.load(uri);
        if (resource == null) {
            throw new ResourceNotFoundException(uri);
        }
        
        this.authorizationManager.authorizeReadProcessed(uri, principal);
        return new TypeInfo(this.resourceTypeTree, resource.getResourceType());
    }

    @Override
    public TypeInfo getTypeInfo(Resource resource) {
        return new TypeInfo(this.resourceTypeTree, resource.getResourceType());
    }

    @Override
    public InputStream getInputStream(String token, Path uri, boolean forProcessing) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, ResourceLockedException, IOException {

        Principal principal = this.tokenManager.getPrincipal(token);
        ResourceImpl r = this.dao.load(uri);

        if (r == null) {
            throw new ResourceNotFoundException(uri);
        } else if (r.isCollection()) {
            throw new IllegalOperationException("Resource is collection");
        }

        if (forProcessing) {
            this.authorizationManager.authorizeReadProcessed(uri, principal);
        } else {
            this.authorizationManager.authorizeRead(uri, principal);
        }
        
        return this.contentStore.getInputStream(uri).getInputStream();
    }

    @Override
    public InputStream getInputStream(String token, Path uri, boolean forProcessing, Revision revision) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, ResourceLockedException, IOException {

        Principal principal = this.tokenManager.getPrincipal(token);
        ResourceImpl r = this.dao.load(uri);

        if (r == null) {
            throw new ResourceNotFoundException(uri);
        } else if (r.isCollection()) {
            throw new IllegalOperationException("Resource is collection");
        }

        if (forProcessing) {
            this.authorizationManager.authorizeReadProcessed(uri, principal);
        } else {
            this.authorizationManager.authorizeRead(uri, principal);
        }
        
        List<Revision> revisions = this.revisionStore.list(r);
        boolean found = false;
        for (Revision rev: revisions) {
            if (rev.getID() == revision.getID()) {
                found = true;
            }
        }
        if (!found) {
            throw new IllegalOperationException("No such revision: " + revision);
        }
        this.authorizationManager.authorizeReadRevision(principal, revision);
        return this.revisionStore.getContent(r, revision);
    }

    @Override
    public Resource[] listChildren(String token, Path uri, boolean forProcessing) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {

        Principal principal = this.tokenManager.getPrincipal(token);
        ResourceImpl collection = this.dao.load(uri);

        if (collection == null) {
            throw new ResourceNotFoundException(uri);
        } else if (!collection.isCollection()) {
            throw new IllegalOperationException("Can't list children for non-collection resources");
        }

        if (forProcessing) {
            this.authorizationManager.authorizeReadProcessed(uri, principal);
        } else {
            this.authorizationManager.authorizeRead(uri, principal);
        }
        ResourceImpl[] list = this.dao.loadChildren(collection);
        Resource[] children = new Resource[list.length];

        for (int i = 0; i < list.length; i++) {
            try {
                children[i] = (Resource) list[i].clone();

            } catch (CloneNotSupportedException e) {
                throw new IOException("An internal error occurred: unable to " + "clone() resource: " + list[i]);
            }
        }

        return children;
    }

    @Override
    public Resource createCollection(String token, Path uri) throws IllegalOperationException, AuthorizationException,
            AuthenticationException, ResourceLockedException, ReadOnlyException, IOException {
        Principal principal = this.tokenManager.getPrincipal(token);
        ResourceImpl resource = this.dao.load(uri);
        if (resource != null) {
            throw new ResourceOverwriteException(uri);
        }
        ResourceImpl parent = this.dao.load(uri.getParent());
        if ((parent == null) || !parent.isCollection()) {
            throw new IllegalOperationException("Either parent doesn't exist " + "or parent is document");
        }

        checkLock(parent, principal);
        this.authorizationManager.authorizeCreate(parent.getURI(), principal);
        checkMaxChildren(parent);

        ResourceImpl newResource = new ResourceImpl(uri);
        newResource.setChildURIs(new ArrayList<Path>());
        Content content = getContent(newResource);
        newResource = this.resourceHelper.create(principal, newResource, true, content);

        try {
            // Content modification on parent for bind
            final ResourceImpl originalParent = (ResourceImpl)parent.clone();
            parent.addChildURI(uri);

            content = getContent(parent);
            parent = this.resourceHelper.contentModification(parent, principal, content);
            parent = this.dao.store(parent);
            this.context.publishEvent(new ContentModificationEvent(this, (Resource)parent.clone(), originalParent));

            // Store new resource
            newResource.setAcl(parent.getAcl());
            newResource.setInheritedAcl(true);
            int aclIneritedFrom = parent.isInheritedAcl() ? parent.getAclInheritedFrom() : parent.getID();
            newResource.setAclInheritedFrom(aclIneritedFrom);
            newResource = this.dao.store(newResource);
            this.contentStore.createResource(newResource.getURI(), true);

            this.context.publishEvent(new ResourceCreationEvent(this, (Resource)newResource.clone()));
            return (Resource)newResource.clone();
        } catch (CloneNotSupportedException e) {
            throw new IOException("An internal error occurred: unable to " + "clone() resource: " + uri);
        }

    }

    @Override
    public void copy(String token, Path srcUri, Path destUri, Repository.Depth depth, boolean overwrite,
            boolean preserveACL) throws IllegalOperationException, AuthorizationException, AuthenticationException,
            FailedDependencyException, ResourceOverwriteException, ResourceLockedException, ResourceNotFoundException,
            ReadOnlyException, IOException {

        Principal principal = this.tokenManager.getPrincipal(token);
        validateCopyURIs(srcUri, destUri);

        ResourceImpl src = this.dao.load(srcUri);
        if (src == null) {
            throw new ResourceNotFoundException(srcUri);
        }

        ResourceImpl dest = this.dao.load(destUri);

        if (dest == null) {
            overwrite = false;
        } else if (!overwrite) {
            throw new ResourceOverwriteException(destUri);
        }

        Path destParentUri = destUri.getParent();
        ResourceImpl destParent = this.dao.load(destParentUri);
        if ((destParent == null) || !destParent.isCollection()) {
            throw new IllegalOperationException("destination is either a document or does not exist");
        }

        if (dest != null) {
            checkLock(dest, principal);
        }
        checkLock(destParent, principal);
        this.authorizationManager.authorizeCopy(srcUri, destUri, principal, overwrite);
        checkMaxChildren(destParent);

        if (dest != null) {
            this.dao.delete(dest);
            this.contentStore.deleteResource(dest.getURI());
            this.context.publishEvent(new ResourceDeletionEvent(this, dest.getURI(),
                                      dest.getID(), dest.isCollection()));
        }

        try {
            PropertySet fixedProps = this.resourceHelper.getFixedCopyProperties(src, principal, destUri);

            ResourceImpl newResource = src.createCopy(destUri);
            Content content = getContent(src);
            // XXX: why nameChange() on copy?
            newResource = this.resourceHelper.nameChange(src, newResource, principal, content);

            final ResourceImpl destParentOriginal = (ResourceImpl)destParent.clone();
            destParent.addChildURI(destUri);
            content = getContent(destParent);
            destParent = this.resourceHelper.contentModification(destParent, principal, content);

            // Both new resource and destParent are stored in DAO copy call
            // Probably better to not touch destparent in DAO copy code and explicitly
            // store it here instead (for better clarity).
            newResource = this.dao.copy(src, destParent, newResource, preserveACL, fixedProps);
            this.contentStore.copy(src.getURI(), newResource.getURI());

            this.context.publishEvent(new ResourceCreationEvent(this, (Resource)newResource.clone()));
            this.context.publishEvent(new ContentModificationEvent(this, destParent, destParentOriginal));
            
        } catch (CloneNotSupportedException e) {
            throw new IOException("An internal error occurred: unable to clone()", e);
        }
    }

    @Override
    public void move(String token, Path srcUri, Path destUri, boolean overwrite) throws IllegalOperationException,
            AuthorizationException, AuthenticationException, FailedDependencyException, ResourceOverwriteException,
            ResourceLockedException, ResourceNotFoundException, ReadOnlyException, IOException {

        Principal principal = this.tokenManager.getPrincipal(token);
        validateCopyURIs(srcUri, destUri);

        // Loading and checking source resource
        final ResourceImpl src = this.dao.load(srcUri);
        ResourceImpl srcParent = this.dao.load(srcUri.getParent());

        if (src == null) {
            throw new ResourceNotFoundException(srcUri);
        }

        // Checking dest
        final ResourceImpl dest = this.dao.load(destUri);
        if (dest == null) {
            overwrite = false;
        } else if (!overwrite) {
            throw new ResourceOverwriteException(destUri);
        }

        // checking destParent
        ResourceImpl destParent = this.dao.load(destUri.getParent());
        if ((destParent == null) || !destParent.isCollection()) {
            throw new IllegalOperationException("Invalid destination resource");
        }

        checkLock(src, principal);
        checkLock(srcParent, principal);
        if (dest != null) {
            checkLock(dest, principal);
        }
        checkLock(destParent, principal);

        this.authorizationManager.authorizeMove(srcUri, destUri, principal, overwrite);
        checkMaxChildren(destParent);

        // Performing delete operation if destination exists (overwrite).
        if (dest != null) {
            this.dao.delete(dest);
            this.contentStore.deleteResource(dest.getURI());
            this.context.publishEvent(new ResourceDeletionEvent(this, dest.getURI(), dest.getID(), dest.isCollection()));
        }

        try {
            // Process parent collections
            if (destParent.getURI().equals(srcParent.getURI())) {
                destParent.removeChildURI(src.getURI());
            } else {
                final ResourceImpl srcParentOriginal = (ResourceImpl) srcParent.clone();
                srcParent.removeChildURI(src.getURI());
                Content content = getContent(srcParent);
                srcParent = this.resourceHelper.contentModification(srcParent, principal, content);
                this.dao.store(srcParent);
                this.context.publishEvent(new ContentModificationEvent(this, (Resource)srcParent.clone(), srcParentOriginal));
            }

            final ResourceImpl destParentOriginal = (ResourceImpl)destParent.clone();
            destParent.addChildURI(destUri);
            Content content = getContent(destParent);
            destParent = this.resourceHelper.contentModification(destParent, principal, content);
            this.dao.store(destParent);
            this.context.publishEvent(new ContentModificationEvent(this, (Resource)destParent.clone(), destParentOriginal));

            // Process move
            ResourceImpl newResource = src.createCopy(destUri);
            newResource.setAcl(src.getAcl());
            newResource.setInheritedAcl(src.isInheritedAcl());
            newResource.setAclInheritedFrom(src.getAclInheritedFrom());
            content = getContent(src);
            newResource = this.resourceHelper.nameChange(src, newResource, principal, content);
            newResource = this.dao.move(src, newResource);
            this.contentStore.move(src.getURI(), newResource.getURI());

            this.context.publishEvent(new ResourceCreationEvent(this, (Resource) newResource.clone()));
            this.context.publishEvent(new ResourceDeletionEvent(this, srcUri, src.getID(), src.isCollection()));
        } catch (CloneNotSupportedException e) {
            throw new IOException("clone() operation failed");
        }
    }

    @Override
    public void delete(String token, Path uri, boolean restorable) throws IllegalOperationException,
            AuthorizationException, AuthenticationException, ResourceNotFoundException, ResourceLockedException,
            FailedDependencyException, ReadOnlyException, IOException, CloneNotSupportedException {

        Principal principal = this.tokenManager.getPrincipal(token);

        if (uri.isRoot()) {
            throw new IllegalOperationException("Cannot delete the root resource ('/')");
        }

        final ResourceImpl resourceToDelete = this.dao.load(uri);

        if (resourceToDelete == null) {
            throw new ResourceNotFoundException(uri);
        }

        // Store parent collection first to avoid dead-lock between Cache
        // locking and database inter-transaction synchronization (which leads
        // to "11-iterations"-problem)
        ResourceImpl parentCollection = this.dao.load(uri.getParent());
        this.authorizationManager.authorizeDelete(uri, principal);
        checkLock(parentCollection, principal);
        checkLock(resourceToDelete, principal);
        
        final ResourceImpl originalParent = (ResourceImpl)parentCollection.clone();
        parentCollection.removeChildURI(uri);
        Content content = getContent(parentCollection);
        parentCollection = this.resourceHelper.contentModification(parentCollection, principal, content);
        parentCollection = this.dao.store(parentCollection);
        this.context.publishEvent(new ContentModificationEvent(this, (Resource)parentCollection.clone(), originalParent));

        if (restorable) {
            // Check ACL before moving to trash can,
            // if inherited, take snapshot of current ACL
            if (resourceToDelete.isInheritedAcl()) {
                ResourceImpl clone = (ResourceImpl) resourceToDelete.clone();
                clone.setInheritedAcl(false);
                this.dao.storeACL(clone);
            }

            final String trashID = "trash-" + resourceToDelete.getID();
            this.dao.markDeleted(resourceToDelete, parentCollection, principal, trashID);
            this.contentStore.moveToTrash(resourceToDelete.getURI(), trashID);

        } else {
            this.dao.delete(resourceToDelete);
            this.contentStore.deleteResource(resourceToDelete.getURI());
        }

        ResourceDeletionEvent event = new ResourceDeletionEvent(this, uri, resourceToDelete.getID(), resourceToDelete.isCollection());
        this.context.publishEvent(event);
    }

    @Override
    public List<RecoverableResource> getRecoverableResources(String token, Path uri) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {

        Principal principal = this.tokenManager.getPrincipal(token);
        ResourceImpl resource = this.dao.load(uri);
        this.authorizationManager.authorizeRead(uri, principal);

        if (resource == null) {
            throw new ResourceNotFoundException(uri);
        }
        return this.dao.getRecoverableResources(resource.getID());
    }

    @Override
    public void recover(String token, Path parentUri, RecoverableResource recoverableResource)
            throws ResourceNotFoundException, AuthorizationException, AuthenticationException, IOException {

        Principal principal = this.tokenManager.getPrincipal(token);
        ResourceImpl parent = this.dao.load(parentUri);

        if (parent == null) {
            throw new ResourceNotFoundException(parentUri);
        }

        checkLock(parent, principal);
        this.authorizationManager.authorizeReadWrite(parentUri, principal);

        try {
            ResourceImpl recovered = this.dao.recover(parentUri, recoverableResource);
            this.contentStore.recover(parentUri, recoverableResource);
            this.context.publishEvent(new ResourceCreationEvent(this, (Resource) recovered.clone()));

            // Content modification on parent
            final ResourceImpl originalParent = (ResourceImpl)parent.clone();
            parent.addChildURI(recovered.getURI());
            Content content = getContent(parent);
            parent = this.resourceHelper.contentModification(parent, principal, content);
            parent = this.dao.store(parent);
            this.context.publishEvent(new ContentModificationEvent(this, (Resource)parent.clone(), originalParent));
            
        } catch (CloneNotSupportedException c) {
            throw new IOException("Internal error, clone failed", c);
        }
    }

    @Override
    public void deleteRecoverable(String token, Path parentUri, RecoverableResource recoverableResource)
            throws Exception {

        ResourceImpl parent = this.dao.load(parentUri);

        if (parent == null) {
            throw new ResourceNotFoundException(parentUri);
        }

        this.dao.deleteRecoverable(recoverableResource);
        this.contentStore.deleteRecoverable(recoverableResource);
    }

    @Override
    public Resource lock(String token, Path uri, String ownerInfo, Repository.Depth depth, int requestedTimeoutSeconds,
            String lockToken) throws ResourceNotFoundException, AuthorizationException, AuthenticationException,
            FailedDependencyException, ResourceLockedException, IllegalOperationException, ReadOnlyException,
            IOException {

        Principal principal = this.tokenManager.getPrincipal(token);
        if (depth == Depth.ONE || depth == Depth.INF) {
            throw new IllegalOperationException("Unsupported depth parameter: " + depth);
        }
        ResourceImpl r = this.dao.load(uri);
        if (r == null) {
            throw new ResourceNotFoundException(uri);
        }

        if (lockToken != null) {
            if (r.getLock() == null) {
                throw new IllegalOperationException("Invalid lock refresh request: lock token '" + lockToken
                        + "' does not exists on resource " + r.getURI());
            }
            if (!r.getLock().getLockToken().equals(lockToken)) {
                throw new IllegalOperationException("Invalid lock refresh request: lock token '" + lockToken
                        + "' does not match existing lock token on resource " + uri);
            }
        }

        checkLock(r, principal);
        this.authorizationManager.authorizeReadWrite(uri, principal);

        boolean refresh = lockToken != null;

        if (!refresh) {
            r.setLock(null);
        }

        if (r.getLock() == null) {
            String newLockToken = "opaquelocktoken:" + UUID.randomUUID().toString();

            Date timeout = new Date(System.currentTimeMillis() + this.lockDefaultTimeout);

            if ((requestedTimeoutSeconds * 1000) > this.lockMaxTimeout) {
                timeout = new Date(System.currentTimeMillis() + this.lockDefaultTimeout);
            }

            LockImpl lock = new LockImpl(newLockToken, principal, ownerInfo, depth, timeout);
            r.setLock(lock);
        } else {
            r.setLock(new LockImpl(r.getLock().getLockToken(), principal, ownerInfo, depth, new Date(System
                    .currentTimeMillis()
                    + (requestedTimeoutSeconds * 1000))));
        }
        this.dao.store(r);
        // this.authorizationManager.lockResource(r, principal, ownerInfo,
        // depth, requestedTimeoutSeconds, (lockToken != null));

        return r;
    }

    public void checkLock(Resource resource, Principal principal) throws ResourceLockedException, IOException,
            AuthenticationException {
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
        throw new ResourceLockedException();
    }

    public String lockResource(ResourceImpl resource, Principal principal, String ownerInfo, Repository.Depth depth,
            int desiredTimeoutSeconds, boolean refresh) throws AuthenticationException, AuthorizationException,
            ResourceLockedException, IOException {

        if (!refresh) {
            resource.setLock(null);
        }

        if (resource.getLock() == null) {
            String lockToken = "opaquelocktoken:" + UUID.randomUUID().toString();

            Date timeout = new Date(System.currentTimeMillis() + this.lockDefaultTimeout);

            if ((desiredTimeoutSeconds * 1000) > this.lockMaxTimeout) {
                timeout = new Date(System.currentTimeMillis() + this.lockDefaultTimeout);
            }

            LockImpl lock = new LockImpl(lockToken, principal, ownerInfo, depth, timeout);
            resource.setLock(lock);
        } else {
            resource.setLock(new LockImpl(resource.getLock().getLockToken(), principal, ownerInfo, depth, new Date(
                    System.currentTimeMillis() + (desiredTimeoutSeconds * 1000))));
        }
        this.dao.store(resource);
        return resource.getLock().getLockToken();
    }

    @Override
    public void unlock(String token, Path uri, String lockToken) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, ResourceLockedException, ReadOnlyException, IOException {
        Principal principal = this.tokenManager.getPrincipal(token);
        ResourceImpl r = this.dao.load(uri);
        if (r == null) {
            throw new ResourceNotFoundException(uri);
        }

        this.authorizationManager.authorizeUnlock(uri, principal);

        if (r.getLock() != null) {
            r.setLock(null);
            this.dao.store(r);
        }
    }

    @Override
    public Resource store(String token, Resource resource) throws ResourceNotFoundException, AuthorizationException,
            ResourceLockedException, AuthenticationException, IllegalOperationException, ReadOnlyException, IOException {

        Principal principal = this.tokenManager.getPrincipal(token);

        if (resource == null) {
            throw new IllegalOperationException("Can't store nothingness.");
        }

        if (!(resource instanceof ResourceImpl)) {
            throw new IllegalOperationException("Can't store unknown implementation of resource..");
        }
        Path uri = resource.getURI();

        ResourceImpl original = this.dao.load(uri);
        if (original == null) {
            throw new ResourceNotFoundException(uri);
        }

        checkLock(original, principal);
        this.authorizationManager.authorizeReadWrite(uri, principal);

        try {
            ResourceImpl originalClone = (ResourceImpl) original.clone();

            ResourceImpl suppliedResource = (ResourceImpl) resource;
            ResourceImpl newResource;
            Content content = getContent(original);
            if (suppliedResource.getSystemJobContext() == null) {
                newResource = this.resourceHelper.propertiesChange(original, principal, suppliedResource, content);
            } else {
                newResource = this.resourceHelper.systemChange(original, principal, suppliedResource, content);
            }

            newResource = this.dao.store(newResource);

            ResourceModificationEvent event = new ResourceModificationEvent(this, (Resource)newResource.clone(), originalClone);
            this.context.publishEvent(event);

            return (Resource) newResource.clone();
        } catch (CloneNotSupportedException e) {
            throw new IOException("An internal error occurred: unable to " + "clone() resource: " + original);
        }
    }

    @Override
    public Resource createDocument(String token, Path uri, InputStream inStream) throws IllegalOperationException,
            AuthorizationException, AuthenticationException, ResourceLockedException, ReadOnlyException, IOException {

        Principal principal = this.tokenManager.getPrincipal(token);
        ResourceImpl resource = this.dao.load(uri);
        if (resource != null) {
            throw new ResourceOverwriteException(uri);
        }
        ResourceImpl parent = this.dao.load(uri.getParent());
        if ((parent == null) || !parent.isCollection()) {
            throw new IllegalOperationException("Either parent doesn't exist " + "or parent is document");
        }

        checkLock(parent, principal);
        this.authorizationManager.authorizeCreate(parent.getURI(), principal);
        checkMaxChildren(parent);

        try {
            this.contentStore.storeContent(uri, inStream);
            ResourceImpl newResource = new ResourceImpl(uri);
            Content content = getContent(newResource);
            newResource = this.resourceHelper.create(principal, newResource, false, content);

            // Content modification on parent
            final ResourceImpl originalParent = (ResourceImpl)parent.clone();
            parent.addChildURI(uri);
            content = getContent(parent);
            parent = this.resourceHelper.contentModification(parent, principal, content);
            parent = this.dao.store(parent);
            this.context.publishEvent(new ContentModificationEvent(this, (Resource)parent.clone(), originalParent));

            // Store new resource
            newResource.setAcl(parent.getAcl());
            newResource.setInheritedAcl(true);
            int aclIneritedFrom = parent.isInheritedAcl() ? parent.getAclInheritedFrom() : parent.getID();
            newResource.setAclInheritedFrom(aclIneritedFrom);
            newResource = this.dao.store(newResource);

            this.context.publishEvent(new ResourceCreationEvent(this, (Resource) newResource.clone()));
            
            return (Resource) newResource.clone();
        } catch (CloneNotSupportedException e) {
            throw new IOException("An internal error occurred: unable to " + "clone() resource: " + uri);
        }
    }

    /**
     * Requests that an InputStream be written to a resource.
     */
    @Override
    public Resource storeContent(String token, Path uri, InputStream byteStream) throws AuthorizationException,
            AuthenticationException, ResourceNotFoundException, ResourceLockedException, IllegalOperationException,
            ReadOnlyException, IOException {

        Principal principal = this.tokenManager.getPrincipal(token);

        ResourceImpl r = this.dao.load(uri);

        if (r == null) {
            throw new ResourceNotFoundException(uri);
        } else if (r.isCollection()) {
            throw new IllegalOperationException("resource is collection");
        }

        checkLock(r, principal);
        this.authorizationManager.authorizeReadWrite(uri, principal);
        try {
            final Resource original = (ResourceImpl) r.clone();

            this.contentStore.storeContent(uri, byteStream);
            Content content = getContent(r);
            r = this.resourceHelper.contentModification(r, principal, content);

            Resource newResource = this.dao.store(r);
            
            this.context.publishEvent(new ContentModificationEvent(this, (Resource)newResource.clone(), original));

            return (Resource)newResource.clone();
        } catch (CloneNotSupportedException e) {
            throw new IOException("An internal error occurred: unable to " + "clone() resource: " + r);
        }
    }


    /**
     * Requests that an InputStream be written to a resource.
     */
    @Override
    public Resource storeContent(String token, Path uri, InputStream stream, Revision revision) throws AuthorizationException,
            AuthenticationException, ResourceNotFoundException, ResourceLockedException, IllegalOperationException,
            ReadOnlyException, IOException {

        if (uri == null) {
            throw new IllegalArgumentException("URI is NULL");
        }
        if (stream == null) {
            throw new IllegalArgumentException("stream is NULL");
        }
        if (revision == null) {
            throw new IllegalArgumentException("revision is NULL");
        }
        if (revision.getType() != Revision.Type.WORKING_COPY) {
            throw new IllegalArgumentException("Only WORKING_COPY is supported");
        }
        Principal principal = this.tokenManager.getPrincipal(token);
        
        ResourceImpl r = this.dao.load(uri);

        if (r == null) {
            throw new ResourceNotFoundException(uri);
        } else if (r.isCollection()) {
            throw new IllegalOperationException("resource is collection");
        }
        
        Revision existing = null;
        for (Revision rev: this.revisionStore.list(r)) {
            if (rev.getID() == revision.getID()) {
                existing = rev;
                break;
            }
        }
        if (existing == null) {
            throw new IllegalOperationException(
                    "No revision WORKING_COPY exists for resource " + r);
        }
        if (existing.getType() != Type.WORKING_COPY) {
            throw new IllegalOperationException(
                    "Only revisions of type WORKING_COPY may be updated" + r);
        }
        
        checkLock(r, principal);
        this.authorizationManager.authorizeReadWrite(uri, principal);
        
        this.authorizationManager.authorizeWriteRevision(principal, revision); 
                
        long id = existing.getID();
        Acl acl = r.getAcl();
        String name = existing.getName();
        String uid = principal.getQualifiedName();
        Type type = existing.getType();
        Date timestamp = new Date();
        String checksum = null;
        Integer changeAmount = null;
        
        File tempFile = null;
        try {
            tempFile = File.createTempFile("revision", null, this.tempDir);
            ChecksumWrapper wrapper = Revisions.wrap(stream);
            OutputStream out = new FileOutputStream(tempFile);
            StreamUtil.pipe(wrapper, out, FILE_COPY_BUF_SIZE, true);
            checksum = wrapper.checksum();
            changeAmount = Revisions.changeAmount(new FileInputStream(tempFile), 
                    this.contentStore.getInputStream(r.getURI()));

            stream = new FileInputStream(tempFile);
            
            Revision.Builder builder = Revision.newBuilder();
            
            Revision rev = 
                    builder.id(id)
                    .acl(acl)
                    .changeAmount(changeAmount)
                    .checksum(checksum)
                    .name(name)
                    .type(type)
                    .timestamp(timestamp)
                    .uid(uid).build();
            this.revisionStore.store(r, rev, stream);
            
            Content content = getContent(r, existing);
            ResourceImpl result = this.resourceHelper.contentModification(r, principal, content);
            return result;
            
        } finally {
            if (tempFile != null) {
                if (!tempFile.delete()) {
                    throw new DataAccessException("Failed to delete temporary file " + tempFile);
                }
            }
            
        }
    }

    @Override
    public boolean isAuthorized(Resource resource, RepositoryAction action, Principal principal, boolean considerLocks)
            throws Exception {
        if (resource == null) {
            throw new IllegalArgumentException("Resource is NULL");
        }
        if (action == null) {
            throw new IllegalArgumentException("Action is NULL");
        }
        if (action == RepositoryAction.COPY || action == RepositoryAction.MOVE) {
            throw new IllegalArgumentException("Cannot authorize action " + action + " on a single resource");
        }
        try {
            if (considerLocks) {
                if (action == RepositoryAction.DELETE) {
                    if (resource.getURI().isRoot()) {
                        return false;
                    }
                    Resource parent = this.dao.load(resource.getURI().getParent());
                    checkLock(parent, principal);
                    checkLock(resource, principal);

                } else if (action == RepositoryAction.ALL 
                        || action == RepositoryAction.ADD_COMMENT
                        || action == RepositoryAction.EDIT_COMMENT
                        || action == RepositoryAction.REPOSITORY_ADMIN_ROLE_ACTION
                        || action == RepositoryAction.REPOSITORY_ROOT_ROLE_ACTION
                        || action == RepositoryAction.UNEDITABLE_ACTION 
                        || action == RepositoryAction.UNLOCK
                        || action == RepositoryAction.CREATE 
                        || action == RepositoryAction.WRITE 
                        || action == RepositoryAction.READ_WRITE
                        || action == RepositoryAction.WRITE_ACL) {
                    checkLock(resource, principal);
                }
            }
            this.authorizationManager.authorizeAction(resource.getURI(), action, principal);
            return true;
        } catch (AuthenticationException e) {
            return false;
        } catch (RepositoryException e) {
            return false;
        }
    }

    @Override
    public Resource storeACL(String token, Path uri, Acl acl) throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IllegalOperationException, ReadOnlyException, IOException {
        return this.storeACL(token, uri, acl, true);
    }

    @Override
    public Resource storeACL(String token, Path uri, Acl acl, boolean validateACL) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IllegalOperationException, ReadOnlyException, IOException {

        if (uri == null) {
            throw new IllegalArgumentException("URI is null");
        }
        if (acl == null) {
            throw new IllegalArgumentException("ACL is null");
        }
        Principal principal = this.tokenManager.getPrincipal(token);

        ResourceImpl r = this.dao.load(uri);
        if (r == null) {
            throw new ResourceNotFoundException(uri);
        }
        checkLock(r, principal);
        this.authorizationManager.authorizeAll(r.getURI(), principal);

        try {
            Resource original = (Resource) r.clone();
            r.setAcl(acl);
            r.setInheritedAcl(false);
            r.setAclInheritedFrom(PropertySetImpl.NULL_RESOURCE_ID);

            if (validateACL) {
                validateNewAcl(acl, original.getAcl());
            }
            
            ResourceImpl newResource = this.dao.storeACL(r);
            
            newResource = (ResourceImpl) newResource.clone(); // Clone for event publishing
            
            ACLModificationEvent event = new ACLModificationEvent(this, newResource, original, newResource.getAcl(),
                    original.getAcl());

            this.context.publishEvent(event);

            return (Resource) newResource.clone(); // Clone for return value

        } catch (CloneNotSupportedException e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public Resource deleteACL(String token, Path uri) throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IllegalOperationException, ReadOnlyException, Exception {
        if (uri == null) {
            throw new IllegalArgumentException("URI is null");
        }
        if (uri == Path.ROOT) {
            throw new IllegalOperationException("The root resource cannot have an inherited ACL");
        }
        Principal principal = this.tokenManager.getPrincipal(token);

        ResourceImpl r = this.dao.load(uri);
        if (r == null) {
            throw new ResourceNotFoundException(uri);
        }
        checkLock(r, principal);
        this.authorizationManager.authorizeAll(r.getURI(), principal);

        if (r.isInheritedAcl()) {
            throw new IllegalOperationException("Resource " + r.getURI() + " already has an inherited ACL");
        }
        try {
            ResourceImpl parent = this.dao.load(uri.getParent());

            Resource original = (Resource) r.clone();
            r.setAcl(parent.getAcl());
            r.setAclInheritedFrom(parent.getID());
            r.setInheritedAcl(true);

            ResourceImpl newResource = this.dao.storeACL(r);
            
            newResource = (ResourceImpl) newResource.clone(); // clone for event publish
            
            ACLModificationEvent event = new ACLModificationEvent(this, newResource, original, newResource.getAcl(),
                    original.getAcl());

            this.context.publishEvent(event);

            return (Resource) newResource.clone(); // clone for return value

        } catch (CloneNotSupportedException e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public boolean isValidAclEntry(Privilege privilege, Principal principal) {
        return validateAclEntry(privilege, principal);
    }

    @Override
    public boolean isBlacklisted(Privilege privilege, Principal principal) {
        return blacklisted(principal, privilege);
    }
    
    @Override
    public List<Revision> getRevisions(String token, Path uri) throws AuthorizationException, ResourceNotFoundException, AuthenticationException, IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI is NULL");
        }
        ResourceImpl resource = this.dao.load(uri);
        if (resource == null) {
            throw new ResourceNotFoundException(uri);
        }
        Principal principal = this.tokenManager.getPrincipal(token);
        this.authorizationManager.authorizeReadProcessed(resource.getURI(), principal);
        return Collections.unmodifiableList(this.revisionStore.list(resource));
    }

    @Override
    public Revision createRevision(String token, Path uri, Revision.Type type) throws ReadOnlyException, AuthorizationException, ResourceNotFoundException, AuthenticationException, IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI is NULL");
        }
        if (type == null) {
            throw new IllegalArgumentException("Type is NULL");
        }
        ResourceImpl resource = this.dao.load(uri);
        if (resource == null) {
            throw new ResourceNotFoundException(uri);
        }
        Principal principal = this.tokenManager.getPrincipal(token);
        
        checkLock(resource, principal);
        this.authorizationManager.authorizeReadWrite(resource.getURI(), principal);
        List<Revision> revs = this.revisionStore.list(resource);

        Revision newest = null;
        for (Revision rev: revs) {
            if (type == Type.WORKING_COPY) {
                if (rev.getName().equals(Type.WORKING_COPY.name())) {
                    throw new IllegalOperationException(
                            "Working copy revision already exists for " + uri);
                }
                continue;
            }
            if (rev.getType() == Type.WORKING_COPY) {
                continue;
            }
            if (newest == null) {
                newest = rev;
                continue;
            }
            if (rev.getTimestamp().getTime() > newest.getTimestamp().getTime()) {
                newest = rev;
            }
        }
        String name;
        if (type == Type.WORKING_COPY) {
            name = Type.WORKING_COPY.name();
        } else if (newest != null) {
            name = String.valueOf(Integer.parseInt(newest.getName()) + 1);
        } else {
            name = "1";
        }
        InputStream content = this.contentStore.getInputStream(resource.getURI());
        InputStream prev = null;
        
        if (Revision.Type.WORKING_COPY.name().equals(name)) {
            prev = this.contentStore.getInputStream(resource.getURI());
        } else {
            List<Revision> list = this.revisionStore.list(resource);
            if (list.size() > 0) {
                Revision r = list.get(0);
                prev = this.revisionStore.getContent(resource, r);
            }
        }
        
        long id = this.revisionStore.newRevisionID();
        Integer changeAmount = null;
        String uid = resource.getModifiedBy().getQualifiedName();
        String checksum = null;
        Date timestamp = new Date();
        Acl acl = resource.getAcl();
        
        File tempFile = null;
        try {
            tempFile = File.createTempFile("revision", null, this.tempDir);
            Revisions.ChecksumWrapper wrapper = Revisions.wrap(content);
            OutputStream out = new FileOutputStream(tempFile);
            StreamUtil.pipe(wrapper, out, FILE_COPY_BUF_SIZE, true);
            checksum = wrapper.checksum();

            if (prev != null && tempFile != null) {
                changeAmount = Revisions.changeAmount(
                        prev, new FileInputStream(tempFile));
                
            }
            content = new FileInputStream(tempFile);
            Revision.Builder builder = Revision.newBuilder();
            
            Revision revision = 
                    builder.id(id)
                    .acl(acl)
                    .changeAmount(changeAmount)
                    .checksum(checksum)
                    .name(name)
                    .type(type)
                    .timestamp(timestamp)
                    .uid(uid).build();
            this.revisionStore.create(resource, revision, content);
            return revision;

        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    
    @Override
    public void deleteRevision(String token, Path uri, Revision revision)
            throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, Exception {
        
        if (uri == null) {
            throw new IllegalArgumentException("URI is NULL");
        }
        if (revision == null) {
            throw new IllegalArgumentException("Revision is NULL");
        }
        ResourceImpl resource = this.dao.load(uri);
        if (resource == null) {
            throw new ResourceNotFoundException(uri);
        }
        Principal principal = this.tokenManager.getPrincipal(token);
        
        checkLock(resource, principal);
        this.authorizationManager.authorizeReadWrite(resource.getURI(), principal);

        Revision found = null;
        Revision older = null;
        Revision newer = null;
        Iterator<Revision> it = this.revisionStore.list(resource).iterator();
        while (it.hasNext()) {
            Revision rev = it.next();
            if (revision.getID() == rev.getID()) {
                found = rev;
                if (it.hasNext()) {
                    Revision next = it.next();
                    if (next.getType() != Type.WORKING_COPY) {
                        older = next;
                    }
                }
                break;
            }
            if (rev.getType() != Type.WORKING_COPY) {
                newer = rev;
            }
        }

        
        if (found == null) {
            throw new IllegalOperationException("Revision not found: " + revision.getID());
        }
        
        this.authorizationManager.authorizeDeleteRevision(principal, revision);
        this.revisionStore.delete(resource, revision);

        
        if (newer != null && older != null) {
            // compare newer -> older (like newer.storecontent)
            Revision.Builder builder = newer.changeBuilder();
            InputStream newerStream = this.revisionStore.getContent(resource, newer);
            InputStream olderStream = this.revisionStore.getContent(resource, older);
            Integer changeAmount = Revisions.changeAmount(newerStream, olderStream);
            builder.changeAmount(changeAmount);
            newer = builder.build();
            this.revisionStore.store(resource, newer, 
                    this.revisionStore.getContent(resource, newer));
            
        } else if (newer != null) {
            Revision.Builder builder = newer.changeBuilder();
            builder.changeAmount(null);
            newer = builder.build();
            this.revisionStore.store(resource, newer, 
                    this.revisionStore.getContent(resource, newer));
        }
    }
    

    @Override
    public List<Comment> getComments(String token, Resource resource) {
        return getComments(token, resource, false, 500);
    }

    @Override
    public List<Comment> getComments(String token, Resource resource, boolean deep, int max) {
        Principal principal = this.tokenManager.getPrincipal(token);

        if (resource == null) {
            throw new IllegalOperationException("Resource argument cannot be NULL");
        }

        try {
            ResourceImpl original = this.dao.load(resource.getURI());
            if (original == null) {
                throw new ResourceNotFoundException(resource.getURI());
            }
            this.authorizationManager.authorizeReadProcessed(resource.getURI(), principal);
            List<Comment> comments = this.commentDAO.listCommentsByResource(resource, deep, max);
            List<Comment> result = new ArrayList<Comment>();
            Set<Path> authCache = new HashSet<Path>();
            // Fetch N comments, authorize on the result set:
            for (Comment c : comments) {
                try {
                    if (!authCache.contains(c.getURI())) {
                        this.authorizationManager.authorizeReadProcessed(c.getURI(), principal);
                        authCache.add(c.getURI());
                    }
                    result.add(c);
                } catch (Throwable t) {
                }
            }
            return Collections.unmodifiableList(result);
        } catch (IOException e) {
            throw new RuntimeException("Unhandled IO exception", e);
        }
    }

    @Override
    public Comment addComment(String token, Resource resource, String title, String text) {
        Principal principal = this.tokenManager.getPrincipal(token);

        if (resource == null) {
            throw new IllegalOperationException("Resource argument cannot be NULL");
        }

        if (!(resource instanceof ResourceImpl)) {
            throw new IllegalOperationException("Can't store unknown implementation of resource..");
        }

        try {
            ResourceImpl original = this.dao.load(resource.getURI());
            if (original == null) {
                throw new ResourceNotFoundException(resource.getURI());
            }

            checkLock(resource, principal);
            this.authorizationManager.authorizeAddComment(resource.getURI(), principal);

            List<Comment> comments = this.commentDAO.listCommentsByResource(resource, false, this.maxComments);
            if (comments.size() > this.maxComments) {
                throw new IllegalOperationException("Too many comments on resource " + resource.getURI());
            }

            Comment comment = new Comment();
            comment.setURI(original.getURI());
            comment.setTime(new java.util.Date());
            comment.setAuthor(principal);
            comment.setTitle(title);
            comment.setContent(text);
            comment.setApproved(true);

            comment = this.commentDAO.createComment(comment);

            Content content = getContent(original);
            ResourceImpl newResource = this.resourceHelper.commentsChange(original, principal, (ResourceImpl) resource, content);
            newResource = this.dao.store(newResource);

            // Publish resource modification event (necessary to trigger
            // re-indexing, since a prop is now modified)
            ResourceModificationEvent event = new ResourceModificationEvent(this, (Resource)newResource.clone(), original);
            this.context.publishEvent(event);

            return comment;
        } catch (IOException e) {
            throw new RuntimeException("Unhandled IO exception", e);
        } catch (CloneNotSupportedException c) {
            throw new RuntimeException("Internal error", c);
        }
    }

    @Override
    public Comment addComment(String token, Comment comment) {

        Principal principal = this.tokenManager.getPrincipal(token);

        // XXX Only allow for users with root privilege?
        this.authorizationManager.authorizeRootRoleAction(principal);

        return this.commentDAO.createComment(comment);
    }

    @Override
    public void deleteComment(String token, Resource resource, Comment comment) {
        Principal principal = this.tokenManager.getPrincipal(token);

        if (resource == null) {
            throw new IllegalOperationException("Resource argument cannot be NULL");
        }

        if (!(resource instanceof ResourceImpl)) {
            throw new IllegalOperationException("Can't store unknown implementation of resource..");
        }

        try {
            ResourceImpl original = this.dao.load(resource.getURI());
            if (original == null) {
                throw new ResourceNotFoundException(resource.getURI());
            }

            checkLock(resource, principal);
            this.authorizationManager.authorizeEditComment(resource.getURI(), principal);
            this.commentDAO.deleteComment(comment);

            Content content = getContent(original);
            ResourceImpl newResource = this.resourceHelper.commentsChange(original, principal, (ResourceImpl) resource, content);
            newResource = this.dao.store(newResource);

            // Publish resource modification event (necessary to trigger
            // re-indexing, since a prop is now modified)
            ResourceModificationEvent event = new ResourceModificationEvent(this, (Resource)newResource.clone(), original);
            this.context.publishEvent(event);

        } catch (Exception e) {
            throw new RuntimeException("Unhandled exception", e);
        }
    }

    @Override
    public void deleteAllComments(String token, Resource resource) {
        Principal principal = this.tokenManager.getPrincipal(token);

        if (resource == null) {
            throw new IllegalOperationException("Resource argument cannot be NULL");
        }

        if (!(resource instanceof ResourceImpl)) {
            throw new IllegalOperationException("Can't store unknown implementation of resource..");
        }

        try {
            ResourceImpl original = this.dao.load(resource.getURI());
            if (original == null) {
                throw new ResourceNotFoundException(resource.getURI());
            }

            checkLock(resource, principal);
            this.authorizationManager.authorizeEditComment(resource.getURI(), principal);
            this.commentDAO.deleteAllComments(resource);

            Content content = getContent(original);
            ResourceImpl newResource = this.resourceHelper.commentsChange(original, principal, (ResourceImpl) resource, content);
            newResource = this.dao.store(newResource);

            // Publish resource modification event (necessary to trigger
            // re-indexing, since a prop is now modified)
            ResourceModificationEvent event = new ResourceModificationEvent(this, (Resource)newResource.clone(), original);
            this.context.publishEvent(event);

        } catch (Exception e) {
            throw new RuntimeException("Unhandled exception", e);
        }
    }

    @Override
    public Comment updateComment(String token, Resource resource, Comment comment) {
        Principal principal = this.tokenManager.getPrincipal(token);

        if (resource == null) {
            throw new IllegalOperationException("Resource argument cannot be NULL");
        }

        if (!(resource instanceof ResourceImpl)) {
            throw new IllegalOperationException("Can't store unknown implementation of resource..");
        }

        try {
            ResourceImpl original = this.dao.load(resource.getURI());
            if (original == null) {
                throw new ResourceNotFoundException(resource.getURI());
            }

            Comment old = null;
            List<Comment> comments = this.commentDAO.listCommentsByResource(resource, false, this.maxComments);
            for (Comment c : comments) {
                if (c.getID().equals(comment.getID()) && c.getURI().equals(comment.getURI())) {
                    old = c;
                    break;
                }
            }
            if (old == null) {
                throw new IllegalArgumentException("Trying to update a non-existing comment");
            }

            checkLock(resource, principal);
            this.authorizationManager.authorizeEditComment(resource.getURI(), principal);
            comment = this.commentDAO.updateComment(comment);
            return comment;

        } catch (IOException e) {
            throw new RuntimeException("Unhandled IO exception", e);
        }
    }

    private void checkMaxChildren(ResourceImpl resource) {
        if (resource.getChildURIs().size() >= this.maxResourceChildren) {
            throw new AuthorizationException("Collection " + resource.getURI() + " has too many children, maximum is "
                    + this.maxResourceChildren);
        }
    }

    private void validateCopyURIs(Path srcPath, Path destPath) throws IllegalOperationException {

        if (srcPath.isRoot()) {
            throw new IllegalOperationException("Cannot copy or move the root resource ('/')");
        }

        if (destPath.isRoot()) {
            throw new IllegalOperationException("Cannot copy or move to the root resource ('/')");
        }

        if (destPath.equals(srcPath)) {
            throw new IllegalOperationException("Cannot copy or move a resource to itself");
        }

        if (srcPath.isAncestorOf(destPath)) {
            throw new IllegalOperationException("Cannot copy or move a resource into itself");
        }
    }

    private void validateNewAcl(Acl acl, Acl originalAcl) throws InvalidPrincipalException {
        Set<Privilege> actions = acl.getActions();
        for (Privilege action : actions) {
            Set<Principal> principals = acl.getPrincipalSet(action);
            for (Principal principal : principals) {
                boolean valid = validateAclEntry(action, principal);
                if (!valid) {
                    // Preserve invalid principals already in ACL
                    if (!originalAcl.containsEntry(action, principal)) {
                        throw new InvalidPrincipalException(principal);
                    }
                }
            }
        }
    }

    private boolean validateAclEntry(Privilege action, Principal principal) {
        boolean valid = false;
        if (principal.getType() == Principal.Type.USER) {
            valid = this.principalManager.validatePrincipal(principal);
        } else if (principal.getType() == Principal.Type.GROUP) {
            valid = this.principalManager.validateGroup(principal);
        } else {
            valid = true;
        }
        if (blacklisted(principal, action)) {
            valid = false;
        }
        return valid;
    }

    private boolean blacklisted(Principal principal, Privilege action) {
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


    @Override
    public void setReadOnly(String token, boolean readOnly) throws AuthorizationException {

        Principal principal = this.tokenManager.getPrincipal(token);
        this.authorizationManager.authorizeRootRoleAction(principal);
        this.authorizationManager.setReadOnly(readOnly);
    }

    private void periodicJob() {
        if (!this.isReadOnly()) {
            this.dao.deleteExpiredLocks(new Date());
        }
    }

    @Override
    public synchronized void purgeTrash() {
        List<RecoverableResource> overdue = this.dao.getTrashCanOverdue(this.permanentDeleteOverdueLimitInDays);
        if (overdue != null && overdue.size() > 0) {
            trashLogger.info("Found " + overdue.size()
                    + " recoverable resources that are overdue for permanent deletion.");
            for (RecoverableResource rr : overdue) {
                trashLogger.info("Permanently deleting recoverable resource: " + rr);
                this.dao.deleteRecoverable(rr);
                this.contentStore.deleteRecoverable(rr);
            }
        }
        List<RecoverableResource> orphans = this.dao.getTrashCanOrphans();
        if (orphans != null && orphans.size() > 0) {
            trashLogger.info("Found " + orphans.size() + " recoverable resources that are orphans.");
            for (RecoverableResource rr : orphans) {
                trashLogger.info("Permanently deleting orphan: " + rr);
                this.dao.deleteRecoverable(rr);
                this.contentStore.deleteRecoverable(rr);
            }
        }
    }

    @Required
    public void setTokenManager(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Required
    public void setDao(DataAccessor dao) {
        this.dao = dao;
    }
    
    @Required
    public void setRevisionStore(RevisionStore revisionStore) {
        this.revisionStore = revisionStore;
    }

    @Required
    public void setCommentDAO(CommentDAO commentDAO) {
        this.commentDAO = commentDAO;
    }

    @Required
    public void setContentStore(ContentStore contentStore) {
        this.contentStore = contentStore;
    }

    @Required
    public void setId(String id) {
        this.id = id;
    }
    
    public void setLockMaxTimeout(long lockMaxTimeout) {
        this.lockMaxTimeout = lockMaxTimeout;
    }

    public void setLockDefaultTimeout(long lockDefaultTimeout) {
        this.lockDefaultTimeout = lockDefaultTimeout;
    }

    @Required
    public void setAuthorizationManager(AuthorizationManager authorizationManager) {
        this.authorizationManager = authorizationManager;
    }

    @Required
    public void setPrincipalManager(PrincipalManager principalManager) {
        this.principalManager = principalManager;
    }

    @Override
    public void setApplicationContext(ApplicationContext context) {
        this.context = context;
    }

    @Required
    public void setRepositoryResourceHelper(RepositoryResourceHelper resourceHelper) {
        this.resourceHelper = resourceHelper;
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }
    
    @Required
    public void setContentRepresentationRegistry(ContentRepresentationRegistry contentRepresentationRegistry) {
        this.contentRepresentationRegistry = contentRepresentationRegistry;
    }
    
    public void setTempDir(String tempDir) {
        File f = new File(tempDir);
        if (!f.exists()) {
            throw new IllegalStateException(
                    "Directory " + tempDir + " does not exist");
            
        }
        this.tempDir = f;
    }

    public void setMaxComments(int maxComments) {
        if (maxComments < 0) {
            throw new IllegalArgumentException("Argument must be an integer >= 0");
        }

        this.maxComments = maxComments;
    }

    public void setMaxResourceChildren(int maxResourceChildren) {
        if (maxResourceChildren < 1) {
            throw new IllegalArgumentException("Argument must be an integer >= 1");
        }
        this.maxResourceChildren = maxResourceChildren;
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

    public void init() {
        this.periodicThread = new PeriodicThread(600);
        this.periodicThread.start();
    }

    public void destroy() {
        this.periodicThread.kill();
    }

    private static Log periodicLogger = LogFactory.getLog(PeriodicThread.class);

    private class PeriodicThread extends Thread {

        private long sleepSeconds;
        private boolean alive = true;

        public PeriodicThread(long sleepSeconds) {
            this.sleepSeconds = sleepSeconds;
        }

        public void kill() {
            this.alive = false;
            this.interrupt();
        }

        @Override
        public void run() {
            while (this.alive) {
                try {
                    sleep(1000 * this.sleepSeconds);
                    periodicJob();
                } catch (InterruptedException e) {
                    this.alive = false;
                } catch (Throwable t) {
                    periodicLogger.warn("Caught exception in cleanup thread", t);
                }
            }
            periodicLogger.info("Terminating refresh thread");
        }
    }

    @Override
    public ResultSet search(String token, Search search) throws QueryException {
        if (this.searcher != null) {
            // Enforce searching in published resources only when going through
            // Repository.search(String, Search)
            search.setOnlyPublishedResources(true);
            long before = System.currentTimeMillis();
            try {
                return this.searcher.execute(token, search);
            } finally {
                long duration = System.currentTimeMillis() - before;
                if (searchLogger.isTraceEnabled()) {
                    searchLogger.trace("search: " + search.toString() + ": " + duration + " ms");
                } else if (searchLogger.isDebugEnabled()) {
                    searchLogger.debug("search: " + duration + " ms");
                }
            }

        } else {
            throw new IllegalStateException("No repository searcher has been configured.");
        }
    }

    public void setSearcher(Searcher searcher) {
        this.searcher = searcher;
    }

    public void setPermanentDeleteOverdueLimitInDays(int permanentDeleteOverdueLimitInDays) {
        if (permanentDeleteOverdueLimitInDays < 1) {
            throw new IllegalArgumentException("Limit cannot be less than 1. Currently set to: "
                    + permanentDeleteOverdueLimitInDays);
        }
        this.permanentDeleteOverdueLimitInDays = permanentDeleteOverdueLimitInDays;
    }
    
    private Content getContent(Resource resource) {
        if (resource == null || resource.isCollection()) {
            return null;
        }
        return new ContentImpl(resource.getURI(), this.contentStore, this.contentRepresentationRegistry);
    }
    
    private Content getContent(final ResourceImpl resource, final Revision revision) {
        if (resource == null || resource.isCollection() || revision == null) {
            return null;
        }
        final RevisionStore revisionStore = this.revisionStore;

        ContentStore cs = new ContentStore() {

            @Override
            public long getContentLength(Path uri) throws DataAccessException {
                return revisionStore.getContentLength(resource, revision);
            }
            @Override
            public InputStreamWrapper getInputStream(Path uri)
                    throws DataAccessException {
                return revisionStore.getContent(resource, revision);
            }

            @Override
            public void deleteResource(Path uri) throws DataAccessException {
                throw new IllegalStateException("Don't call me");
            }
            @Override
            public void createResource(Path uri, boolean isCollection)
                    throws DataAccessException {
                throw new IllegalStateException("Don't call me");
            }

            @Override
            public void storeContent(Path uri, InputStream inputStream)
                    throws DataAccessException {
                throw new IllegalStateException("Don't call me");
            }

            @Override
            public void copy(Path srcURI, Path destURI)
                    throws DataAccessException {
                throw new IllegalStateException("Don't call me");
            }

            @Override
            public void move(Path srcURI, Path destURI)
                    throws DataAccessException {
                throw new IllegalStateException("Don't call me");
            }

            @Override
            public void moveToTrash(Path srcURI, String trashIdDir)
                    throws DataAccessException {
                throw new IllegalStateException("Don't call me");
            }

            @Override
            public void recover(Path destURI,
                    RecoverableResource recoverableResource)
                    throws DataAccessException {
                throw new IllegalStateException("Don't call me");
            }

            @Override
            public void deleteRecoverable(
                    RecoverableResource recoverableResource)
                    throws DataAccessException {
                throw new IllegalStateException("Don't call me");
            }

        };
        ContentImpl content = new ContentImpl(resource.getURI(), cs, this.contentRepresentationRegistry);
        return content;
    }
}
