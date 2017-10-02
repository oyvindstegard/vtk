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
package vtk.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.repository.Acl;
import vtk.repository.AuthorizationException;
import vtk.repository.Comment;
import vtk.repository.ContentInputSource;
import vtk.repository.FailedDependencyException;
import vtk.repository.IllegalOperationException;
import vtk.repository.Lock;
import vtk.repository.NoSuchContentException;
import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.ReadOnlyException;
import vtk.repository.RecoverableResource;
import vtk.repository.Repository;
import vtk.repository.RepositoryAction;
import vtk.repository.RepositoryException;
import vtk.repository.Resource;
import vtk.repository.ResourceId;
import vtk.repository.ResourceLockedException;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.ResourceOverwriteException;
import vtk.repository.Revision;
import vtk.repository.StoreContext;
import vtk.repository.TypeInfo;
import vtk.repository.search.QueryException;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.security.AuthenticationException;
import vtk.security.Principal;

public class RequestLocalRepository {
    private static Logger logger = LoggerFactory.getLogger(RequestLocalRepository.class);

    public static final Repository create(HttpServletRequest request, Repository repository) {  
        return new RequestLocalRepositoryImpl(request, repository);
    }
    
    private static class RequestLocalRepositoryImpl implements Repository {
        private HttpServletRequest request;
        private Repository repository;
        public RequestLocalRepositoryImpl(HttpServletRequest request, Repository repository) {
            this.request = request;
            this.repository = repository;
        }


        @Override
        public TypeInfo getTypeInfo(Resource resource) {
            return this.repository.getTypeInfo(resource);
        }

        @Override
        public TypeInfo getTypeInfo(String name) {
            return this.repository.getTypeInfo(name);
        }

        @Override
        public Resource retrieve(String token, Path uri, boolean forProcessing) throws IOException {

            RepositoryContext ctx = RepositoryContext.getRepositoryContext(request);
            if (ctx == null) {
                return this.repository.retrieve(token, uri, forProcessing);
            }

            Resource r = null;
            Throwable t = null;

            t = ctx.getResourceMiss(token, uri, forProcessing);
            if (t != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Retrieval of resource " + uri + " caused throwable: " + t);
                }

                throwAppropriateException(t);
            }

            r = ctx.getResourceHit(token, uri, forProcessing);
            if (r != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Retrieve resource " + uri + ": found in cache");
                }
                return r;
            }

            try {
                if (logger.isDebugEnabled()) {
                    logger.debug("Retrieve resource " + uri + ": retrieving from repository");
                }
                r = this.repository.retrieve(token, uri, forProcessing);
                ctx.addResourceHit(token, r, forProcessing);
                return r;
            } catch (Throwable retrieveException) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Retrieve resource " + uri + ": caching throwable: " + retrieveException);
                }
                ctx.addResourceMiss(token, uri, retrieveException, forProcessing);
                throwAppropriateException(retrieveException);
                return null;
            }
        }



        @Override
        public Resource retrieve(String token, Path uri, boolean forProcessing,
                Revision revision) throws ResourceNotFoundException,
        AuthorizationException, AuthenticationException, IOException {
            return this.repository.retrieve(token, uri, forProcessing, revision);
        }

        @Override
        public Resource retrieveById(String token, ResourceId id, boolean forProcessing)
                throws RepositoryException, AuthenticationException, AuthorizationException, IOException {
            return this.repository.retrieveById(token, id, forProcessing);
        }

        @Override
        public Resource[] listChildren(String token, Path uri, boolean forProcessing) throws IOException {
            return this.repository.listChildren(token, uri, forProcessing);
        }

        @Override
        public Resource store(String token, String lockToken, Resource resource, StoreContext storeContext)
                throws IOException {

            RepositoryContext ctx = RepositoryContext.getRepositoryContext(request);
            if (ctx != null) {
                ctx.clear();
            }

            return this.repository.store(token, lockToken, resource, storeContext);
        }

        @Override
        public Resource store(String token, String lockToken, Resource resource) throws IOException {

            RepositoryContext ctx = RepositoryContext.getRepositoryContext(request);
            if (ctx != null) {
                ctx.clear();
            }

            return this.repository.store(token, lockToken, resource);
        }

        @Override
        public Resource storeContent(String token, String lockToken, Path uri, ContentInputSource content) throws IOException {

            RepositoryContext ctx = RepositoryContext.getRepositoryContext(request);
            if (ctx != null) {
                ctx.clear();
            }
            return this.repository.storeContent(token, lockToken, uri, content);
        }

        @Override
        public Resource storeContent(String token, String lockToken, Path uri, ContentInputSource content, Revision revision) throws IOException {

            RepositoryContext ctx = RepositoryContext.getRepositoryContext(request);
            if (ctx != null) {
                ctx.clear();
            }
            return this.repository.storeContent(token, lockToken, uri, content, revision);
        }

        @Override
        public InputStream getInputStream(String token, Path uri, boolean forProcessing) throws IOException {

            return this.repository.getInputStream(token, uri, forProcessing);
        }

        @Override
        public InputStream getInputStream(String token, Path uri, boolean forProcessing, Revision revision) throws IOException {

            return this.repository.getInputStream(token, uri, forProcessing, revision);
        }

        @Override
        public InputStream getAlternativeInputStream(String token, Path uri, boolean forProcessing, String contentIdentifier)
                throws NoSuchContentException, ResourceNotFoundException, AuthorizationException, AuthenticationException, IOException {
            return this.repository.getAlternativeInputStream(token, uri, forProcessing, contentIdentifier);
        }

        @Override
        public Resource createDocument(String token, String lockToken, Path uri, ContentInputSource content) throws IOException {

            RepositoryContext ctx = RepositoryContext.getRepositoryContext(request);
            if (ctx != null) {
                ctx.clear();
            }
            return this.repository.createDocument(token, lockToken, uri, content);
        }

        @Override
        public Resource createCollection(String token, String lockToken, Path uri) throws IOException {
            return this.repository.createCollection(token, lockToken, uri);
        }

        @Override
        public void copy(String token, String lockToken, Path srcUri, Path destUri, boolean overwrite, boolean preserveACL)
                throws IOException {

            RepositoryContext ctx = RepositoryContext.getRepositoryContext(request);
            if (ctx != null) {
                ctx.clear();
            }
            this.repository.copy(token, lockToken, srcUri, destUri, overwrite, preserveACL);
        }

        @Override
        public void move(String token, String lockToken, Path srcUri, Path destUri, boolean overwrite) throws IOException {

            RepositoryContext ctx = RepositoryContext.getRepositoryContext(request);
            if (ctx != null) {
                ctx.clear();
            }
            this.repository.move(token, lockToken, srcUri, destUri, overwrite);
        }

        @Override
        public void delete(String token, String lockToken, Path uri, boolean restorable) throws IOException {

            RepositoryContext ctx = RepositoryContext.getRepositoryContext(request);
            if (ctx != null) {
                ctx.clear();
            }
            this.repository.delete(token, lockToken, uri, restorable);
        }

        @Override
        public List<RecoverableResource> getRecoverableResources(String token, Path uri) throws ResourceNotFoundException,
        AuthorizationException, AuthenticationException, IOException {
            return this.repository.getRecoverableResources(token, uri);
        }

        @Override
        public void recover(String token, String lockToken, Path parentUri, RecoverableResource recoverableResource)
                throws ResourceNotFoundException, AuthorizationException, AuthenticationException, IOException {
            this.repository.recover(token, lockToken, parentUri, recoverableResource);
        }

        @Override
        public void deleteRecoverable(String token, String lockToken, Path parentUri, RecoverableResource recoverableResource)
                throws IOException {
            this.repository.deleteRecoverable(token, lockToken, parentUri, recoverableResource);
        }

        @Override
        public boolean exists(String token, Path uri) throws IOException {

            RepositoryContext ctx = RepositoryContext.getRepositoryContext(request);
            if (ctx != null
                    && (ctx.getResourceHit(token, uri, true) != null || ctx.getResourceHit(token, uri, false) != null)) {
                return true;
            }
            return this.repository.exists(token, uri);
        }

        @Override
        public Resource lock(String token, Path uri, String ownerInfo, Depth depth, int requestedTimoutSeconds,
                String lockToken, Lock.Type lockType) throws IOException {

            RepositoryContext ctx = RepositoryContext.getRepositoryContext(request);
            if (ctx != null) {
                ctx.clear();
            }
            return this.repository.lock(token, uri, ownerInfo, depth, requestedTimoutSeconds, lockToken, lockType);
        }

        @Override
        public void unlock(String token, Path uri, String lockToken) throws IOException {

            RepositoryContext ctx = RepositoryContext.getRepositoryContext(request);
            if (ctx != null) {
                ctx.clear();
            }
            this.repository.unlock(token, uri, lockToken);
        }

        @Override
        public Resource storeACL(String token, String lockToken, Path uri, Acl acl) throws IOException {

            RepositoryContext ctx = RepositoryContext.getRepositoryContext(request);
            if (ctx != null) {
                ctx.clear();
            }
            return this.repository.storeACL(token, lockToken, uri, acl);
        }

        @Override
        public Resource storeACL(String token, String lockToken, Path uri, Acl acl, boolean validateACL) throws IOException {

            RepositoryContext ctx = RepositoryContext.getRepositoryContext(request);
            if (ctx != null) {
                ctx.clear();
            }
            return this.repository.storeACL(token, lockToken, uri, acl, validateACL);
        }

        @Override
        public Resource deleteACL(String token, String lockToken, Path uri)
                throws ResourceNotFoundException, AuthorizationException,
                AuthenticationException, IllegalOperationException,
                ReadOnlyException, IOException {
            RepositoryContext ctx = RepositoryContext.getRepositoryContext(request);
            if (ctx != null) {
                ctx.clear();
            }
            return this.repository.deleteACL(token, lockToken, uri);
        }

        @Override
        public boolean isValidAclEntry(Privilege privilege, Principal principal) {
            return this.repository.isValidAclEntry(privilege, principal);
        }

        @Override
        public boolean isBlacklisted(Privilege privilege, Principal principal) {
            return this.repository.isBlacklisted(privilege, principal);
        }

        @Override
        public List<Comment> getComments(String token, Resource resource) throws RepositoryException,
        AuthenticationException {
            return this.repository.getComments(token, resource);
        }

        @Override
        public List<Comment> getComments(String token, Resource resource, boolean deep, int max)
                throws RepositoryException, AuthenticationException {
            return this.repository.getComments(token, resource, deep, max);
        }

        @Override
        public Comment addComment(String token, String lockToken, Resource resource, String title, String text) throws RepositoryException,
        AuthenticationException {
            return this.repository.addComment(token, lockToken, resource, title, text);
        }

        @Override
        public Comment addComment(String token, String lockToken, Comment comment) {
            return this.repository.addComment(token, lockToken, comment);
        }

        @Override
        public void deleteComment(String token, String lockToken, Resource resource, Comment comment) throws RepositoryException,
        AuthenticationException {
            this.repository.deleteComment(token, lockToken, resource, comment);
        }

        @Override
        public void deleteAllComments(String token, String lockToken, Resource resource) throws RepositoryException, AuthenticationException {
            this.repository.deleteAllComments(token, lockToken, resource);
        }

        @Override
        public Comment updateComment(String token, String lockToken, Resource resource, Comment comment) throws RepositoryException,
        AuthenticationException {
            return this.repository.updateComment(token, lockToken, resource, comment);
        }

        @Override
        public String getId() {
            return this.repository.getId();
        }

        @Override
        public boolean isReadOnly() {
            return this.repository.isReadOnly();
        }

        @Override
        public boolean isReadOnly(Path path, boolean forDelete) {
            return this.repository.isReadOnly(path, forDelete);
        }

        @Override
        public List<Path> getReadOnlyRoots() {
            return this.repository.getReadOnlyRoots();
        }

        @Override
        public void setReadOnly(String token, boolean readOnly) throws IOException {
            this.repository.setReadOnly(token, readOnly);
        }

        @Override
        public ResultSet search(String token, Search search) throws QueryException {
            return this.repository.search(token, search);
        }

        @Override
        public boolean authorize(Principal principal, Acl acl, Privilege privilege) {
            return this.repository.authorize(principal, acl, privilege);
        }

        @Override
        public boolean isAuthorized(Resource resource, RepositoryAction action, Principal principal, String lockToken)
                throws IOException {
            return this.repository.isAuthorized(resource, action, principal, lockToken);
        }

        @Override
        public boolean isAuthorized(Resource resource, RepositoryAction action, Principal principal, boolean considerLocks)
                throws IOException {
            return this.repository.isAuthorized(resource, action, principal, considerLocks);
        }

        // XXX: Losing stack traces unnecessary
        private void throwAppropriateException(Throwable t) throws AuthenticationException, AuthorizationException,
        FailedDependencyException, IOException, IllegalOperationException, ReadOnlyException,
        ResourceLockedException, ResourceNotFoundException, ResourceOverwriteException {

            if (logger.isDebugEnabled()) {
                logger.debug("Re-throwing exception: " + t);
            }

            if (t instanceof AuthenticationException) {
                throw (AuthenticationException) t;
            }
            if (t instanceof AuthorizationException) {
                throw (AuthorizationException) t;
            }
            if (t instanceof FailedDependencyException) {
                throw (FailedDependencyException) t;
            }
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            if (t instanceof IllegalOperationException) {
                throw (IllegalOperationException) t;
            }
            if (t instanceof ReadOnlyException) {
                throw (ReadOnlyException) t;
            }
            if (t instanceof ResourceLockedException) {
                throw (ResourceLockedException) t;
            }
            if (t instanceof ResourceNotFoundException) {
                throw (ResourceNotFoundException) t;
            }
            if (t instanceof ResourceOverwriteException) {
                throw (ResourceOverwriteException) t;
            }

            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }

            throw new RuntimeException(t);
        }

        @Override
        public List<Revision> getRevisions(String token, Path uri) throws AuthorizationException, ResourceNotFoundException, AuthenticationException, IOException {
            return this.repository.getRevisions(token, uri);
        }

        @Override
        public Revision createRevision(String token, String lockToken, Path uri, Revision.Type type) throws AuthorizationException, ResourceNotFoundException, AuthenticationException, IOException {
            return this.repository.createRevision(token, lockToken, uri, type);
        }

        @Override
        public void deleteRevision(String token, String lockToken, Path uri, Revision revision)
                throws ResourceNotFoundException, AuthorizationException,
                AuthenticationException, IOException {
            this.repository.deleteRevision(token, lockToken, uri, revision);
        }
    }
}
