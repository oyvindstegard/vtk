/* Copyright (c) 2009, University of Oslo, Norway
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.search.QueryException;
import org.vortikal.repository.search.ResultSet;
import org.vortikal.repository.search.Search;
import org.vortikal.repository.store.Cache;
import org.vortikal.security.AuthenticationException;
import org.vortikal.security.Principal;

/**
 * Handles synchronization in URI namespace of repository read/write operations
 * and does some extra cache flushing after transactions have completed.
 * 
 * Repository service calls which entail writing use exclusive locks on the involved
 * <code>Path</code>s, while reading calls use shared locks.
 */
public class LockingCacheControlRepositoryWrapper implements Repository {

    private Cache cache;
    private Repository wrappedRepository;
    private final Log logger = LogFactory.getLog(LockingCacheControlRepositoryWrapper.class);
    private final PathLockManager lockManager = new PathLockManager();
    private File tempDir = new File(System.getProperty("java.io.tmpdir"));

    @Override
    public Comment addComment(String token, Resource resource, String title, String text) throws RepositoryException,
            AuthenticationException {
        return this.wrappedRepository.addComment(token, resource, title, text);
    }

    @Override
    public Comment addComment(String token, Comment comment) throws AuthenticationException {
        return this.wrappedRepository.addComment(token, comment); // Tx
    }

    @Override
    public void copy(String token, Path srcUri, Path destUri, Depth depth, boolean overwrite, boolean preserveACL)
            throws IllegalOperationException, AuthorizationException, AuthenticationException,
            FailedDependencyException, ResourceOverwriteException, ResourceLockedException, ResourceNotFoundException,
            ReadOnlyException, Exception {

        // Synchronize on:
        // - Destination parent URI
        // - Destination URI
        // - Any cached descendant of destination URI in case of overwrite.
        List<Path> lockUris = new ArrayList<Path>(2);
        if (destUri.getParent() != null) {
            lockUris.add(destUri.getParent());
        }
        lockUris.add(destUri);
        if (overwrite) {
            lockUris.addAll(getCachedDescendants(destUri));
        }
                
        final List<Path> locked = this.lockManager.lock(lockUris, true);
        
        try {
            this.wrappedRepository.copy(token, srcUri, destUri, depth, overwrite, preserveACL); // Tx

            // Purge destination URI and destination parent URI from cache
            flushFromCache(destUri, true, "copy");
            if (destUri.getParent() != null) {
                flushFromCache(destUri.getParent(), false, "copy");
            }
        } finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public void move(String token, Path srcUri, Path destUri, boolean overwrite) throws IllegalOperationException,
            AuthorizationException, AuthenticationException, FailedDependencyException, ResourceOverwriteException,
            ResourceLockedException, ResourceNotFoundException, ReadOnlyException, Exception {

        // Synchronize on:
        // - Source URI and any cached descendant of source URI
        // - Source parent URI
        // - Destination parent URI (may be same as source parent URI)
        // - Destination URI
        // - Any cached descendant of destination URI in case of overwrite.
        List<Path> lockUris = new ArrayList<Path>();
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
            this.wrappedRepository.move(token, srcUri, destUri, overwrite); // Tx

            // Purge source, source parent and dest-parent from cache after
            // transaction has been comitted.
            flushFromCache(srcUri, true, "move");
            flushFromCache(destUri, true, "move");

            if (srcParent != null) {
                flushFromCache(srcParent, false, "move");
            }
            if (destParent != null) {
                flushFromCache(destParent, false, "move");
            }
        } finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public Resource createCollection(String token, Path uri) throws AuthorizationException, AuthenticationException,
            IllegalOperationException, ResourceLockedException, ReadOnlyException, Exception {

        // Synchronize on:
        // - Parent URI
        // - URI
        List<Path> lockUris = new ArrayList<Path>(2);
        if (uri.getParent() != null) {
            lockUris.add(uri.getParent());
        }
        lockUris.add(uri);
        
        final List<Path> locked = this.lockManager.lock(lockUris, true);
        
        try {
            Resource resource = this.wrappedRepository.createCollection(token, uri); // Tx

            Path parent = resource.getURI().getParent();
            if (parent != null) {
                flushFromCache(parent, false, "createCollection"); // Purge parent from cache after
                // transaction has been comitted.
            }
            
            return resource;
        } finally {
            this.lockManager.unlock(locked, true);
        }
    }
    

    
    @Override
    public Resource createDocument(String token, Path uri, InputStream byteStream) throws IllegalOperationException,
            AuthorizationException, AuthenticationException, ResourceLockedException, ReadOnlyException, Exception {

        File tempFile = null;
        try {
            // Convert input stream to file FileInputStream if necessary, to ensure
            // most efficient transfer to repository content store while holding locks.
            if (! ((byteStream instanceof FileInputStream)
                    || (byteStream instanceof ByteArrayInputStream))) {
                
                tempFile = writeTempFile(uri.getName(), byteStream);
                byteStream = new FileInputStream(tempFile);
            }

            // Synchronize on:
            // - Parent URI
            // - URI
            List<Path> lockUris = new ArrayList<Path>(2);
            if (uri.getParent() != null) {
                lockUris.add(uri.getParent());
            }
            lockUris.add(uri);

            final List<Path> locked = this.lockManager.lock(lockUris, true);

            try {
                Resource resource = this.wrappedRepository.createDocument(token, uri, byteStream); // Tx

                Path parent = resource.getURI().getParent();
                if (parent != null) {
                    // Purge parent from cache after transaction has been comitted.
                    flushFromCache(parent, false, "createDocument");
                }

                return resource;
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
    public void delete(String token, Path uri, boolean restorable) throws IllegalOperationException,
            AuthorizationException, AuthenticationException, ResourceNotFoundException, ResourceLockedException,
            FailedDependencyException, ReadOnlyException, Exception {

        // Synchronize on:
        // - Parent URI
        // - URI
        // - Any cached descendants of URI
        List<Path> lockUris = new ArrayList<Path>();
        if (uri.getParent() != null) {
            lockUris.add(uri.getParent());
        }
        lockUris.add(uri);
        lockUris.addAll(getCachedDescendants(uri));

        final List<Path> locked = this.lockManager.lock(lockUris, true);
        
        try {
            this.wrappedRepository.delete(token, uri, restorable); // Tx

            flushFromCache(uri, true, "delete");
            Path parent = uri.getParent();
            if (parent != null) {
                flushFromCache(parent, false, "delete");
            }
        } finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public List<RecoverableResource> getRecoverableResources(String token, Path uri) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, Exception {
        return this.wrappedRepository.getRecoverableResources(token, uri);
    }

    @Override
    public void recover(String token, Path parentUri, RecoverableResource recoverableResource)
            throws ResourceNotFoundException, AuthorizationException, AuthenticationException, Exception {

        // Synchronize on:
        // - Parent URI
        // - URI of recovered resource
        List<Path> lockUris = new ArrayList<Path>(2);
        lockUris.add(parentUri);
        lockUris.add(parentUri.extend(recoverableResource.getName()));

        final List<Path> locked = this.lockManager.lock(lockUris, true);
        
        try {
            this.wrappedRepository.recover(token, parentUri, recoverableResource);
            flushFromCache(parentUri, false, "recover");
        } finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public void deleteRecoverable(String token, Path parentUri, RecoverableResource recoverableResource)
            throws Exception {
        this.wrappedRepository.deleteRecoverable(token, parentUri, recoverableResource);
    }

    @Override
    public void deleteAllComments(String token, Resource resource) throws RepositoryException, AuthenticationException {
        this.wrappedRepository.deleteAllComments(token, resource); // Tx

    }

    @Override
    public void deleteComment(String token, Resource resource, Comment comment) throws RepositoryException,
            AuthenticationException {
        this.wrappedRepository.deleteComment(token, resource, comment); // Tx
    }

    @Override
    public boolean exists(String token, Path uri) throws AuthorizationException, AuthenticationException, Exception {
        // Acquired shared lock
        final List<Path> locked = this.lockManager.lock(uri, false);
        try {
            return this.wrappedRepository.exists(token, uri); // Tx
        } finally {
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
    public InputStream getInputStream(String token, Path uri, boolean forProcessing) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, Exception {
        
        // XXX perhaps not lock at all for getInputStream ..
        //     If a slow writer is uploading to the same resource, getting the input stream will block.
        //     On the other hand, not locking can typically result in a bad half-written input stream.
        
        List<Path> locked = this.lockManager.lock(uri, false);
        try {
            return this.wrappedRepository.getInputStream(token, uri, forProcessing); // Tx
        } finally {
            this.lockManager.unlock(locked, false);
        }
    }

    @Override
    public boolean isReadOnly() {
        return this.wrappedRepository.isReadOnly(); // Tx
    }

    @Override
    public Resource[] listChildren(String token, Path uri, boolean forProcessing) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, Exception {
        
        // Acquire a shared read-lock on parent path
        final List<Path> locked = this.lockManager.lock(uri, false);
        try {
            return this.wrappedRepository.listChildren(token, uri, forProcessing); // Tx
        } finally {
            this.lockManager.unlock(locked, false);
        }
    }

    @Override
    public Resource lock(String token, Path uri, String ownerInfo, Depth depth, int requestedTimoutSeconds,
            String lockToken) throws ResourceNotFoundException, AuthorizationException, AuthenticationException,
            FailedDependencyException, ResourceLockedException, IllegalOperationException, ReadOnlyException, Exception {
        
        // Synchronize on:
        // - URI
        final List<Path> locked = this.lockManager.lock(uri, true);
        try {
            return this.wrappedRepository.lock(token, uri, ownerInfo, depth, requestedTimoutSeconds, lockToken); // Tx
        } finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public void unlock(String token, Path uri, String lockToken) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, ResourceLockedException, ReadOnlyException, Exception {
        
        // Synchronize on:
        // - URI
        final List<Path> locked = this.lockManager.lock(uri, true);
        try {
            this.wrappedRepository.unlock(token, uri, lockToken); // Tx
        } finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public Resource retrieve(String token, Path uri, boolean forProcessing) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, Exception {
        // Acquire a shared read-lock on path
        final List<Path> locked = this.lockManager.lock(uri, false);
        try {
            return this.wrappedRepository.retrieve(token, uri, forProcessing); // Tx
        } finally {
            this.lockManager.unlock(locked, false);
        }
    }

    @Override
    public void setReadOnly(String token, boolean readOnly) throws AuthorizationException, Exception {
        this.wrappedRepository.setReadOnly(token, readOnly); // Tx
    }

    @Override
    public Resource store(String token, Resource resource) throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, ResourceLockedException, IllegalOperationException, ReadOnlyException, Exception {
        
        // Synchronize on:
        // - URI
        
        final List<Path> locked = this.lockManager.lock(resource.getURI(), true);
        
        try {
            return this.wrappedRepository.store(token, resource); // Tx
        } finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public Resource storeACL(String token, Path uri, Acl acl) throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IllegalOperationException, ReadOnlyException, Exception {
        
        // Synchronize on:
        // - URI
        // - Any cached descendant of URI (due to ACL inheritance)
        List<Path> lockUris = new ArrayList<Path>();
        lockUris.add(uri);
        lockUris.addAll(getCachedDescendants(uri));
        
        final List<Path> locked = this.lockManager.lock(lockUris, true);
        
        try {
            Resource resource = this.wrappedRepository.storeACL(token, uri, acl); // Tx
            flushFromCache(uri, true, "storeACL");

            return resource;
        } finally {
            this.lockManager.unlock(locked, true);
        }
    }

    @Override
    public Resource storeACL(String token, Path uri, Acl acl, boolean validateACL) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IllegalOperationException, ReadOnlyException, Exception {
        
        // Synchronize on:
        // - URI
        // - Any cached descendant of URI (due to ACL inheritance)
        List<Path> lockUris = new ArrayList<Path>();
        lockUris.add(uri);
        lockUris.addAll(getCachedDescendants(uri));
        
        final List<Path> locked = this.lockManager.lock(lockUris, true);
        
        try {
            Resource resource = this.wrappedRepository.storeACL(token, uri, acl, validateACL); // Tx
            flushFromCache(uri, true, "storeACL");
            
            return resource;
        } finally {
            this.lockManager.unlock(locked, true);
        }
    }
    
    @Override
    public Resource deleteACL(String token, Path uri)
            throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IllegalOperationException,
            ReadOnlyException, Exception {
        
        // Synchronize on:
        // - URI
        // - Any cached descendant of URI (due to ACL inheritance)
        List<Path> lockUris = new ArrayList<Path>();
        lockUris.add(uri);
        lockUris.addAll(getCachedDescendants(uri));

        final List<Path> locked = this.lockManager.lock(lockUris, true);
        
        try {
            Resource resource = this.wrappedRepository.deleteACL(token, uri); // Tx

            flushFromCache(uri, true, "deleteACL");
            
            return resource;
        } finally {
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
    public Resource storeContent(String token, Path uri, InputStream byteStream) throws AuthorizationException,
            AuthenticationException, ResourceNotFoundException, ResourceLockedException, IllegalOperationException,
            ReadOnlyException, Exception {
        
        // Synchronize on:
        // - URI
        final List<Path> locked = this.lockManager.lock(uri, true);
        try {
           return this.wrappedRepository.storeContent(token, uri, byteStream); // Tx
        } finally {
            this.lockManager.unlock(locked, true);
        }    
    }


    @Override
    public Comment updateComment(String token, Resource resource, Comment comment) throws RepositoryException,
            AuthenticationException {
        return this.wrappedRepository.updateComment(token, resource, comment); // Tx
    }

    @Override
    public ResultSet search(String token, Search search) throws QueryException {
        return this.wrappedRepository.search(token, search);
    }

    @Override
    public boolean isAuthorized(Resource resource, RepositoryAction action, Principal principal, boolean considerLocks)
            throws Exception {
        return this.wrappedRepository.isAuthorized(resource, action, principal, considerLocks);
    }

    @Override
    public TypeInfo getTypeInfo(String token, Path uri) throws Exception {
        return this.wrappedRepository.getTypeInfo(token, uri);
    }

    @Override
    public TypeInfo getTypeInfo(Resource resource) {
        return this.wrappedRepository.getTypeInfo(resource);
    }

    @Override
    public void purgeTrash() {
        this.wrappedRepository.purgeTrash();
    }
    
    @Required
    public void setCache(Cache cache) {
        this.cache = cache;
    }

    @Required
    public void setWrappedRepository(Repository wrappedRepository) {
        this.wrappedRepository = wrappedRepository;
    }
    
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
    
    /**
     * Writes to a temporary file (used to avoid lengthy blocking on file
     * uploads).
     */
    private File writeTempFile(String name, InputStream byteStream) throws IOException {
        ReadableByteChannel src = Channels.newChannel(byteStream);
        File tempFile = File.createTempFile("tmpfile-" + name, null, this.tempDir);
        FileChannel dest = new FileOutputStream(tempFile).getChannel();
        int chunk = 100000;
        long pos = 0;
        while (true) {
            long n = dest.transferFrom(src, pos, chunk);
            if (n == 0) {
                break;
            }
            pos += n;
        }
        src.close();
        dest.close();
        return tempFile;
    }
    
    private void flushFromCache(Path uri, boolean includeDescendants, String serviceMethodName) {
        this.cache.flushFromCache(uri, includeDescendants);
        if (logger.isDebugEnabled()) {
            logger.debug(serviceMethodName + "() completed, purged from cache: " 
                    + uri + (includeDescendants ? " (including descendants)" : ""));
        }
    }
    
    private List<Path> getCachedDescendants(Path uri) {
        return this.cache.getCachedDescendantPaths(uri);
    }

}
