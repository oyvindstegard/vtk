/* Copyright (c) 2009, 2016, University of Oslo, Norway
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.cluster.ClusterAware;
import vtk.cluster.ClusterContext;
import vtk.cluster.ClusterRole;
import vtk.repository.search.QueryException;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.store.Cache;
import vtk.repository.store.ContentStore;
import vtk.security.AuthenticationException;
import vtk.security.Principal;
import vtk.security.token.TokenManager;
import vtk.util.io.IO;

/**
 * Handles synchronization in URI namespace of repository read/write operations
 * and does some extra cache flushing after transactions have completed.
 *
 * <p>Also buffers content to temporary files before actual storage calls to repository
 * are made, to increase robustness and reduce locking time.
 *
 * Repository service calls which entail writing use exclusive locks on the involved
 * <code>Path</code>s, while reading calls use shared locks.
 * 
 * <p>One could argue that, since this code is necessary for proper synchronization of multithreaded access,
 * some or all of this code belongs somewhere inside or underneath
 * {@link RepositoryImpl}, but the code was cleaner when modelling it as a wrapper around it.
 * 
 * <p>TODO rename to RepositoryNamespaceSynchronizer ?
 * <p>TODO Model as subclass of RepositoryImpl instead ? And make RepositoryImpl abstract ?
 * <p>XXX we allow potentially huge uploads content store calls before actually verifying
 *        authentication and repository permissions.
 *        This makes proper HTTP 100-Continue-handling more cumbersome for client code.
 *        (Servlet containers typically detect when to send a 100-continue response
 *        by monitoring when input stream is opened for reading by application code.)
 */
public class LockingCacheControlRepositoryWrapper implements Repository, ClusterAware {

    private Cache cache;
    private Repository wrappedRepository;
    private final Logger logger = LoggerFactory.getLogger(LockingCacheControlRepositoryWrapper.class);
    private TokenManager tokenManager;
    private final PathLockManager lockManager = new PathLockManager();
    private File tempDir = new File(System.getProperty("java.io.tmpdir"));

    private Optional<ClusterContext> clusterContext = Optional.empty();
    private Optional<ClusterRole> clusterRole = Optional.empty();

    @Override
    public Comment addComment(String token, String lockToken, Resource resource, String title, String text) throws RepositoryException,
            AuthenticationException {
        return this.wrappedRepository.addComment(token, lockToken, resource, title, text);
    }

    @Override
    public Comment addComment(String token, String lockToken, Comment comment) throws AuthenticationException {
        return this.wrappedRepository.addComment(token, lockToken, comment); // Tx
    }

    @Override
    public void copy(String token, String lockToken, Path srcUri, Path destUri, 
            boolean overwrite, boolean copyAcls)
            throws IllegalOperationException, AuthorizationException, AuthenticationException,
            FailedDependencyException, ResourceOverwriteException, ResourceLockedException, 
            ResourceNotFoundException, ReadOnlyException, IOException {

        // Synchronize on:
        // - Destination parent URI
        // - Destination URI
        // - Any cached descendant of destination URI in case of overwrite.
        List<Path> lockUris = new ArrayList<>(2);
        if (destUri.getParent() != null) {
            lockUris.add(destUri.getParent());
        }
        lockUris.add(destUri);
        if (overwrite) {
            lockUris.addAll(getCachedDescendants(destUri));
        }

        final List<Path> locked = this.lockManager.lock(lockUris, true);

        try {
            this.wrappedRepository.copy(token, lockToken, srcUri, destUri, overwrite, copyAcls); // Tx

            // Purge destination URI and destination parent URI from cache
            flushFromCache(destUri, true, "copy");
            notifyFlush(destUri, true, "copy");

            if (destUri.getParent() != null) {
                flushFromCache(destUri.getParent(), false, "copy");
                notifyFlush(destUri.getParent(), false, "copy");
            }
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public void move(String token, String lockToken, Path srcUri, Path destUri, boolean overwrite) throws IllegalOperationException,
            AuthorizationException, AuthenticationException, FailedDependencyException, ResourceOverwriteException,
            ResourceLockedException, ResourceNotFoundException, ReadOnlyException, IOException {

        // Synchronize on:
        // - Source URI and any cached descendant of source URI
        // - Source parent URI
        // - Destination parent URI (may be same as source parent URI)
        // - Destination URI
        // - Any cached descendant of destination URI in case of overwrite.
        List<Path> lockUris = new ArrayList<>();
        Path srcParent = srcUri.getParent();
        Path destParent = destUri.getParent();

        if (srcParent != null) {
            lockUris.add(srcParent);
        }
        lockUris.add(srcUri);
        lockUris.addAll(getCachedDescendants(srcUri));

        if (destParent != null && ! destParent.equals(srcParent)) {
            lockUris.add(destParent);
        }
        if (!srcUri.equals(destUri)) {
            lockUris.add(destUri);
            if (overwrite) {
                lockUris.addAll(getCachedDescendants(destUri));
            }
        }

        final List<Path> locked = this.lockManager.lock(lockUris, true);

        try {
            this.wrappedRepository.move(token, lockToken, srcUri, destUri, overwrite); // Tx

            // Purge source, source parent and dest-parent from cache after
            // transaction has been comitted.
            flushFromCache(srcUri, true, "move");
            notifyFlush(srcUri, true, "move");
            
            flushFromCache(destUri, true, "move");
            notifyFlush(destUri, true, "move");

            if (srcParent != null) {
                flushFromCache(srcParent, false, "move");
                notifyFlush(srcParent, false, "move");
            }
            if (destParent != null) {
                flushFromCache(destParent, false, "move");
                notifyFlush(destParent, false, "move");
            }
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public Resource createCollection(String token, String lockToken, Path uri, AclMode aclMode) 
            throws AuthorizationException, AuthenticationException,
            IllegalOperationException, ResourceLockedException, ReadOnlyException, IOException {

        // Synchronize on:
        // - Parent URI
        // - URI
        List<Path> lockUris = new ArrayList<>(2);
        if (uri.getParent() != null) {
            lockUris.add(uri.getParent());
        }
        lockUris.add(uri);

        final List<Path> locked = this.lockManager.lock(lockUris, true);

        try {
            Resource resource = this.wrappedRepository
                    .createCollection(token, lockToken, uri, aclMode); // Tx

            Path parent = resource.getURI().getParent();
            if (parent != null) {
                flushFromCache(parent, false, "createCollection"); // Purge parent from cache after
                notifyFlush(parent, false, "createCollection"); // Purge parent from cache after
                // transaction has been comitted.
            }
            return resource;
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }


    @Override
    public Resource createDocument(final String token, final String lockToken, final Path uri, 
            ContentInputSource content, AclMode aclMode) throws IllegalOperationException,
            AuthorizationException, AuthenticationException, ResourceLockedException, ReadOnlyException, IOException {

        IO.TempFile tempFile = null;
        try {
            Optional<IO.TempFile> buffered = maybeBufferContent(token, content);
            if (buffered.isPresent()) {
                tempFile = buffered.get();
                content = ContentInputSources.fromFile(buffered.get().file(), true);
            }

            // Synchronize on:
            // - Parent URI
            // - URI
            List<Path> lockUris = new ArrayList<>(2);
            if (uri.getParent() != null) {
                lockUris.add(uri.getParent());
            }
            lockUris.add(uri);

            final List<Path> locked = this.lockManager.lock(lockUris, true);

            try {
                Resource resource = this.wrappedRepository
                        .createDocument(token, lockToken, uri, content, aclMode); // Tx

                Path parent = resource.getURI().getParent();
                if (parent != null) {
                    // Purge parent from cache after transaction has been comitted.
                    flushFromCache(parent, false, "createDocument");
                    notifyFlush(parent, false, "createDocument");
                }

                return resource;
            }
            finally {
                this.lockManager.unlock(locked, true);
            }
        }
        finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }

    @Override
    public void delete(String token, String lockToken, Path uri, boolean restorable) throws IllegalOperationException,
            AuthorizationException, AuthenticationException, ResourceNotFoundException, ResourceLockedException,
            FailedDependencyException, ReadOnlyException, IOException {

        // Synchronize on:
        // - Parent URI
        // - URI
        // - Any cached descendants of URI
        List<Path> lockUris = new ArrayList<>();
        if (uri.getParent() != null) {
            lockUris.add(uri.getParent());
        }
        lockUris.add(uri);
        lockUris.addAll(getCachedDescendants(uri));

        final List<Path> locked = this.lockManager.lock(lockUris, true);

        try {
            this.wrappedRepository.delete(token, lockToken, uri, restorable); // Tx

            flushFromCache(uri, true, "delete");
            notifyFlush(uri, true, "delete");
            Path parent = uri.getParent();
            if (parent != null) {
                flushFromCache(parent, false, "delete");
                notifyFlush(parent, false, "delete");
            }
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public List<RecoverableResource> getRecoverableResources(String token, Path uri) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {
        return this.wrappedRepository.getRecoverableResources(token, uri);
    }

    @Override
    public void recover(String token, String lockToken, Path parentUri, RecoverableResource recoverableResource)
            throws ResourceNotFoundException, AuthorizationException, AuthenticationException, IOException {

        // Synchronize on:
        // - Parent URI
        // - URI of recovered resource
        List<Path> lockUris = new ArrayList<>(2);
        lockUris.add(parentUri);
        lockUris.add(parentUri.extend(recoverableResource.getName()));

        final List<Path> locked = this.lockManager.lock(lockUris, true);

        try {
            this.wrappedRepository.recover(token, lockToken, parentUri, recoverableResource);
            flushFromCache(parentUri, false, "recover");
            notifyFlush(parentUri, false, "recover");
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public void deleteRecoverable(String token, String lockToken, Path parentUri, RecoverableResource recoverableResource)
            throws IOException {
        this.wrappedRepository.deleteRecoverable(token, lockToken, parentUri, recoverableResource);
    }

    @Override
    public void deleteAllComments(String token, String lockToken, Resource resource) throws RepositoryException, AuthenticationException {
        List<Path> locked = this.lockManager.lock(resource.getURI(), true);
        try {
            this.wrappedRepository.deleteAllComments(token, lockToken, resource); // Tx
            notifyFlush(resource.getURI(), false, "deleteAllComments");
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public void deleteComment(String token, String lockToken, Resource resource, Comment comment) throws RepositoryException,
            AuthenticationException {
        List<Path> locked = this.lockManager.lock(resource.getURI(), true);
        try {
            this.wrappedRepository.deleteComment(token, lockToken, resource, comment); // Tx
            notifyFlush(resource.getURI(), false, "deleteComment");
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public boolean exists(String token, Path uri) throws AuthorizationException, AuthenticationException, IOException {
        // Acquired shared lock
        final List<Path> locked = this.lockManager.lock(uri, false);
        try {
            return this.wrappedRepository.exists(token, uri); // Tx
        }
        finally {
            this.lockManager.unlock(locked, false);
        }
    }

    @Override
    public List<Comment> getComments(String token, Resource resource) throws RepositoryException,
            AuthenticationException {
        return this.wrappedRepository.getComments(token, resource); // Tx
    }

    @Override
    public List<Comment> getComments(String token, Resource resource, boolean deep, int max)
            throws RepositoryException, AuthenticationException {
        return this.wrappedRepository.getComments(token, resource, deep, max); // Tx
    }

    @Override
    public String getId() {
        return this.wrappedRepository.getId();
    }

    @Override
    public InputStream getInputStream(String token, Path uri, boolean forProcessing)
            throws ResourceNotFoundException, AuthorizationException, AuthenticationException, IOException{
        // XXX perhaps not lock at all for getInputStream ..
        //     If a slow writer is uploading to the same resource, getting the input stream will block.
        //     On the other hand, not locking can typically result in a bad half-written input stream.

        List<Path> locked = this.lockManager.lock(uri, false);
        try {
            return this.wrappedRepository.getInputStream(token, uri, forProcessing); // Tx
        }
        finally {
            this.lockManager.unlock(locked, false);
        }
    }

    @Override
    public InputStream getInputStream(String token, Path uri, boolean forProcessing, Revision revision) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {

        List<Path> locked = this.lockManager.lock(uri, false);
        try {
            return this.wrappedRepository.getInputStream(token, uri, forProcessing, revision); // Tx
        }
        finally {
            this.lockManager.unlock(locked, false);
        }
    }

    @Override
    public InputStream getAlternativeInputStream(String token, Path uri, boolean forProcessing, String contentIdentifier)
            throws NoSuchContentException, ResourceNotFoundException, AuthorizationException, AuthenticationException, IOException {

        List<Path> locked = this.lockManager.lock(uri, false);
        try {
            return this.wrappedRepository.getAlternativeInputStream(token, uri, forProcessing, contentIdentifier); // Tx
        }
        finally {
            this.lockManager.unlock(locked, false);
        }
    }

    @Override
    public boolean isReadOnly() {
        return this.wrappedRepository.isReadOnly(); // Tx
    }

    @Override
    public boolean isReadOnly(Path path, boolean forDelete) {
        return this.wrappedRepository.isReadOnly(path, forDelete); // Tx
    }

    @Override
    public List<Path> getReadOnlyRoots() {
        return this.wrappedRepository.getReadOnlyRoots();
    }

    @Override
    public Resource[] listChildren(String token, Path uri, boolean forProcessing) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {

        // Acquire a shared read-lock on parent path
        final List<Path> locked = this.lockManager.lock(uri, false);
        try {
            return this.wrappedRepository.listChildren(token, uri, forProcessing); // Tx
        }
        finally {
            this.lockManager.unlock(locked, false);
        }
    }

    @Override
    public Resource lock(String token, Path uri, String ownerInfo, Depth depth, int requestedTimoutSeconds,
            String lockToken, Lock.Type lockType) throws ResourceNotFoundException, AuthorizationException, AuthenticationException,
            FailedDependencyException, ResourceLockedException, IllegalOperationException, ReadOnlyException, IOException {

        // Synchronize on:
        // - URI
        final List<Path> locked = this.lockManager.lock(uri, true);
        try {
            Resource r = this.wrappedRepository.lock(token, uri, ownerInfo, depth, requestedTimoutSeconds, lockToken, lockType); // Tx
            notifyFlush(uri, false, "lock");
            return r;
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public void unlock(String token, Path uri, String lockToken) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, ResourceLockedException, ReadOnlyException, IOException {

        // Synchronize on:
        // - URI
        final List<Path> locked = this.lockManager.lock(uri, true);
        try {
            this.wrappedRepository.unlock(token, uri, lockToken); // Tx
            notifyFlush(uri, false, "unlock");
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public Resource retrieve(String token, Path uri, boolean forProcessing) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {
        // Acquire a shared read-lock on path
        final List<Path> locked = this.lockManager.lock(uri, false);
        try {
            return this.wrappedRepository.retrieve(token, uri, forProcessing); // Tx
        }
        finally {
            this.lockManager.unlock(locked, false);
        }
    }

    @Override
    public Resource retrieve(String token, Path uri, boolean forProcessing, Revision revision) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {
        // Acquire a shared read-lock on path
        final List<Path> locked = this.lockManager.lock(uri, false);
        try {
            return this.wrappedRepository.retrieve(token, uri, forProcessing, revision); // Tx
        }
        finally {
            this.lockManager.unlock(locked, false);
        }
    }

    @Override
    public Resource retrieveById(String token, ResourceId id, boolean forProcessing)
            throws RepositoryException, AuthenticationException, AuthorizationException, IOException {

         /* XXX no path read locking when loading by ID. But it is likely better than
            alternative of looking up path by id first, in a separate tx, then locking the path.
            This is because that would be racy if the target resource was moved or deleted
            after looking up path, but before actually loading by path. */

        return wrappedRepository.retrieveById(token, id, forProcessing); // Tx

    }

    @Override
    public void setReadOnly(String token, boolean readOnly) throws AuthorizationException, IOException {
        this.wrappedRepository.setReadOnly(token, readOnly); // Tx
    }

    @Override
    public Resource store(String token, String lockToken, Resource resource, StoreContext storeContext) throws ResourceNotFoundException, AuthorizationException, AuthenticationException, ResourceLockedException, IllegalOperationException, ReadOnlyException, IOException {
        // Synchronize on:
        // - URI if NOT inheritable properties store
        // - URI and all descendants if inheritable store
        List<Path> lockUris = new ArrayList<>();
        lockUris.add(resource.getURI());
        if (storeContext instanceof InheritablePropertiesStoreContext) {
            // XXX overkill to lock all descendants ?
            lockUris.addAll(getCachedDescendants(resource.getURI()));
        }

        final List<Path> locked = this.lockManager.lock(lockUris, true);

        try {
            Resource r = this.wrappedRepository.store(token, lockToken, resource, storeContext); // Tx
            boolean flushDescendants = (storeContext instanceof InheritablePropertiesStoreContext);
            flushFromCache(resource.getURI(), flushDescendants, "store");
            notifyFlush(resource.getURI(), flushDescendants, "store");
            return r;
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public Resource store(String token, String lockToken, Resource resource) throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, ResourceLockedException, IllegalOperationException, ReadOnlyException, IOException {
        // Synchronize on:
        // - URI

        final List<Path> locked = this.lockManager.lock(resource.getURI(), true);

        try {
            Resource r = this.wrappedRepository.store(token, lockToken, resource); // Tx
            notifyFlush(resource.getURI(), false, "store");
            return r;
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public Resource storeACL(String token, String lockToken, Path uri, Acl acl) throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IllegalOperationException, ReadOnlyException, IOException {

        // Synchronize on:
        // - URI
        // - Any cached descendant of URI (due to ACL inheritance)
        List<Path> lockUris = new ArrayList<>();
        lockUris.add(uri);
        lockUris.addAll(getCachedDescendants(uri));

        final List<Path> locked = this.lockManager.lock(lockUris, true);

        try {
            Resource r = this.wrappedRepository.storeACL(token, lockToken, uri, acl); // Tx
            flushFromCache(uri, true, "storeACL");
            notifyFlush(uri, true, "storeACL");
            return r;
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public Resource storeACL(String token, String lockToken, Path uri, Acl acl, boolean validateACL) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IllegalOperationException, ReadOnlyException, IOException {

        // Synchronize on:
        // - URI
        // - Any cached descendant of URI (due to ACL inheritance)
        List<Path> lockUris = new ArrayList<>();
        lockUris.add(uri);
        lockUris.addAll(getCachedDescendants(uri));

        final List<Path> locked = this.lockManager.lock(lockUris, true);

        try {
            Resource resource = this.wrappedRepository.storeACL(token, lockToken, uri, acl, validateACL); // Tx
            flushFromCache(uri, true, "storeACL");
            notifyFlush(uri, true, "storeACL");

            return resource;
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public Resource deleteACL(String token, String lockToken, Path uri)
            throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IllegalOperationException,
            ReadOnlyException, IOException {

        // Synchronize on:
        // - URI
        // - Any cached descendant of URI (due to ACL inheritance)
        List<Path> lockUris = new ArrayList<>();
        lockUris.add(uri);
        lockUris.addAll(getCachedDescendants(uri));

        final List<Path> locked = this.lockManager.lock(lockUris, true);

        try {
            Resource r = this.wrappedRepository.deleteACL(token, lockToken, uri); // Tx
            flushFromCache(uri, true, "deleteACL");
            notifyFlush(uri, true, "deleteACL");
            return r;
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public boolean isValidAclEntry(Privilege privilege, Principal principal) {
        return this.wrappedRepository.isValidAclEntry(privilege, principal);
    }

    @Override
    public boolean isBlacklisted(Privilege privilege, Principal principal) {
        return this.wrappedRepository.isBlacklisted(privilege, principal);
    }

    @Override
    public Resource storeContent(String token, String lockToken, Path uri, ContentInputSource content) throws AuthorizationException,
            AuthenticationException, ResourceNotFoundException, ResourceLockedException, IllegalOperationException,
            ReadOnlyException, IOException {

        IO.TempFile tempFile = null;
        try {
            Optional<IO.TempFile> buffered = maybeBufferContent(token, content);
            if (buffered.isPresent()) {
                tempFile = buffered.get();
                content = ContentInputSources.fromFile(buffered.get().file(), true);
            }

            // Synchronize on:
            // - URI
            final List<Path> locked = this.lockManager.lock(uri, true);
            try {
                Resource r = this.wrappedRepository.storeContent(token, lockToken, uri, content); // Tx
                notifyFlush(uri, false, "storeContent");
                return r;
            } finally {
                this.lockManager.unlock(locked, true);
            }
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }

    @Override
    public Resource storeContent(String token, String lockToken, Path uri, ContentInputSource content, Revision revision) throws AuthorizationException,
            AuthenticationException, ResourceNotFoundException, ResourceLockedException, IllegalOperationException,
            ReadOnlyException, IOException {

        IO.TempFile tempFile = null;
        try {
            Optional<IO.TempFile> buffered = maybeBufferContent(token, content);
            if (buffered.isPresent()) {
                tempFile = buffered.get();
                content = ContentInputSources.fromFile(buffered.get().file(), true);
            }

            // Synchronize on:
            // - URI
            final List<Path> locked = this.lockManager.lock(uri, true);
            try {
                Resource r = this.wrappedRepository.storeContent(token, lockToken, uri, content, revision); // Tx
                notifyFlush(uri, false, "storeContent");
                return r;
            } finally {
                this.lockManager.unlock(locked, true);
            }
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }


    @Override
    public Comment updateComment(String token, String lockToken, Resource resource, Comment comment) throws RepositoryException,
            AuthenticationException {
        final List<Path> locked = this.lockManager.lock(resource.getURI(), true);
        try {
            Comment c = this.wrappedRepository.updateComment(token, lockToken, resource, comment); // Tx
            notifyFlush(resource.getURI(), false, "updateComment");
            return c;
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public ResultSet search(String token, Search search) throws QueryException {
        return this.wrappedRepository.search(token, search);
    }

    @Override
    public boolean authorize(Principal principal, Acl acl, Privilege privilege) {
        return this.wrappedRepository.authorize(principal, acl, privilege);
    }

    @Override
    public boolean isAuthorized(Resource resource, RepositoryAction action, Principal principal, boolean considerLocks)
            throws IOException {
        return this.wrappedRepository.isAuthorized(resource, action, principal, considerLocks);
    }
    @Override
    public boolean isAuthorized(Resource resource, RepositoryAction action, Principal principal, String lockToken)
            throws IOException {
        return this.wrappedRepository.isAuthorized(resource, action, principal, lockToken);
    }

    @Override
    public TypeInfo getTypeInfo(Resource resource) {
        return this.wrappedRepository.getTypeInfo(resource);
    }

    @Override
    public TypeInfo getTypeInfo(String name) {
        return this.wrappedRepository.getTypeInfo(name);
    }

    private void flushFromCache(Path uri, boolean includeDescendants, String serviceMethodName) {
        this.cache.flushFromCache(uri, includeDescendants);
        if (logger.isDebugEnabled()) {
            logger.debug(serviceMethodName + "() completed, purged from cache: "
                    + uri + (includeDescendants ? " (including descendants)" : ""));
        }
    }
    
    private void notifyFlush(Path uri, boolean includeDescendants, String serviceMethodName) {
        if (clusterContext.isPresent()) {
            FlushMessage flushMessage = new FlushMessage(uri, includeDescendants, serviceMethodName);
            if (logger.isDebugEnabled()) {
                logger.debug(serviceMethodName
                        + "() completed, sending cluster flush message: " + uri);
            }
            clusterContext.get().clusterMessage(flushMessage);
        }
    }

    private List<Path> getCachedDescendants(Path uri) {
        return this.cache.getCachedDescendantPaths(uri);
    }

    @Override
    public List<Revision> getRevisions(String token, Path uri) throws AuthorizationException, ResourceNotFoundException, AuthenticationException, IOException {
        // Synchronize shared read-lock on:
        // - URI
        final List<Path> locked = this.lockManager.lock(uri, false);
        try {
            return this.wrappedRepository.getRevisions(token, uri); // Tx
        }
        finally {
            this.lockManager.unlock(locked, false);
        }
    }

    @Override
    public Revision createRevision(String token, String lockToken, Path uri, Revision.Type type) throws AuthorizationException, ResourceNotFoundException, AuthenticationException, IOException {
        // Synchronize on:
        // - URI
        final List<Path> locked = this.lockManager.lock(uri, true);
        try {
            return this.wrappedRepository.createRevision(token, lockToken, uri, type); // Tx
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public void deleteRevision(String token, String lockToken, Path uri, Revision revision)
            throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IOException {

        // Synchronize on:
        // - URI
        final List<Path> locked = this.lockManager.lock(uri, true);
        try {
            this.wrappedRepository.deleteRevision(token, lockToken, uri, revision); // Tx
        }
        finally {
            this.lockManager.unlock(locked, true);
        }
    }


    @Required
    public void setCache(Cache cache) {
        this.cache = cache;
    }
    
    @Required
    public void setTokenManager(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Required
    public void setWrappedRepository(Repository wrappedRepository) {
        this.wrappedRepository = wrappedRepository;
    }

    // Convert input stream to local temporary file if necessary, to ensure
    // most efficient transfer to repository content store while holding locks, and
    // to potentially increase atomicity of repository content store calls by
    // allowing file system move operation to take place.
    private Optional<IO.TempFile> maybeBufferContent(String token, ContentInputSource source) throws IOException {
        if (!source.isFile() || !source.canDeleteSourceFile()) {
            InputStream stream = source.stream();
            if (!(stream instanceof FileInputStream
                    || stream instanceof ByteArrayInputStream)) {

                IO.TempFile tempFile = IO.tempFile(stream, tempDir)
                        .progress(p -> tokenManager.getPrincipal(token)) // Refresh token because upload may be slow
                        .progressInterval(128 * 1024 * 1024) // Refresh approx. for every 128M uploaded
                        .perform();
                return Optional.of(tempFile);
            }
        }

        return Optional.empty();
    }

    /**
     * This temporary directly should ideally reside on the same file system
     * as that used by the {@link ContentStore} implementation of the repository.
     * That will ensure the most efficient transfer of resource content, allowing
     * buffered files to be moved atomically into place.
     *
     * @param tempDirPath path to temporary directory used to buffer resource content
     */
    @Required
    public void setTempDir(String tempDirPath) {
        File tmp = new File(tempDirPath);
        if (!tmp.exists()) {
            throw new IllegalArgumentException("Unable to set tempDir: file " + tmp + " does not exist");
        }
        if (!tmp.isDirectory()) {
            throw new IllegalArgumentException("Unable to set tempDir: file " + tmp + " is not a directory");
        }
        this.tempDir = tmp;
    }

    public static class FlushMessage implements Serializable {
        private static final long serialVersionUID = 8288073797498465660L;
        public final boolean flushDescendants;
        public final Path path;
        public final String serviceMethodName;

        public FlushMessage(Path path, boolean flushDescendants, String serviceMethodName) {
            this.path = path;
            this.flushDescendants = flushDescendants;
            this.serviceMethodName = serviceMethodName;
        }

        @Override
        public String toString() { return getClass().getSimpleName() 
            + "(" + path + ", " + serviceMethodName + ", " + flushDescendants + ")"; }
    }

    @Override
    public void clusterContext(ClusterContext context) {
        this.clusterContext = Optional.of(context);
        context.subscribe(FlushMessage.class);
    }

    @Override
    public void roleChange(ClusterRole role) {
        this.clusterRole = Optional.of(role);
    }

    @Override
    public void clusterMessage(Object message) {
        if (clusterRole.isPresent() && clusterRole.get() == ClusterRole.SLAVE) {
            if (message instanceof FlushMessage) {
                FlushMessage flushMessage = (FlushMessage) message;

                if (logger.isDebugEnabled()) {
                    logger.debug("Received flush message from cluster: " + flushMessage.path);
                }

                List<Path> lockUris = new ArrayList<>();
                lockUris.add(flushMessage.path);
                if (flushMessage.flushDescendants) {
                    lockUris.addAll(getCachedDescendants(flushMessage.path));
                }

                final List<Path> locked = this.lockManager.lock(lockUris, true);

                try {
                    flushFromCache(flushMessage.path, flushMessage.flushDescendants, 
                            "remote::" + flushMessage.serviceMethodName);
                }
                finally {
                    this.lockManager.unlock(locked, true);
                }
            }
        }
    }
}
