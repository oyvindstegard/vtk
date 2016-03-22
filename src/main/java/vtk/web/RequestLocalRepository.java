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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import vtk.repository.Acl;
import vtk.repository.AuthorizationException;
import vtk.repository.Comment;
import vtk.repository.ContentStream;
import vtk.repository.FailedDependencyException;
import vtk.repository.IllegalOperationException;
import vtk.repository.NoSuchContentException;
import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.ReadOnlyException;
import vtk.repository.RecoverableResource;
import vtk.repository.Repository;
import vtk.repository.RepositoryAction;
import vtk.repository.RepositoryException;
import vtk.repository.Resource;
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

public class RequestLocalRepository implements Repository {

    private static Logger logger = LoggerFactory.getLogger(RequestLocalRepository.class);
    private Repository repository;

    @Required
    public void setRepository(Repository repository) {
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
    public Resource retrieve(String token, Path uri, boolean forProcessing) throws Exception {

        RepositoryContext ctx = RepositoryContext.getRepositoryContext();
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
            AuthorizationException, AuthenticationException, Exception {
        return this.repository.retrieve(token, uri, forProcessing, revision);
    }

    @Override
    public Resource[] listChildren(String token, Path uri, boolean forProcessing) throws Exception {
        return this.repository.listChildren(token, uri, forProcessing);
    }

    @Override
    public Resource store(String token, Resource resource, StoreContext storeContext) 
            throws Exception {
        
        RepositoryContext ctx = RepositoryContext.getRepositoryContext();
        if (ctx != null) {
            ctx.clear();
        }
        
        return this.repository.store(token, resource, storeContext);
    }

    @Override
    public Resource store(String token, Resource resource) throws Exception {

        RepositoryContext ctx = RepositoryContext.getRepositoryContext();
        if (ctx != null) {
            ctx.clear();
        }

        return this.repository.store(token, resource);
    }

    @Override
    public Resource storeContent(String token, Path uri, InputStream byteStream) throws Exception {

        RepositoryContext ctx = RepositoryContext.getRepositoryContext();
        if (ctx != null) {
            ctx.clear();
        }
        return this.repository.storeContent(token, uri, byteStream);
    }

    @Override
    public Resource storeContent(String token, Path uri, InputStream byteStream, Revision revision) throws Exception {

        RepositoryContext ctx = RepositoryContext.getRepositoryContext();
        if (ctx != null) {
            ctx.clear();
        }
        return this.repository.storeContent(token, uri, byteStream, revision);
    }

    @Override
    public InputStream getInputStream(String token, Path uri, boolean forProcessing) throws Exception {

        return this.repository.getInputStream(token, uri, forProcessing);
    }
    
    @Override
    public InputStream getInputStream(String token, Path uri, boolean forProcessing, Revision revision) throws Exception {

        return this.repository.getInputStream(token, uri, forProcessing, revision);
    }

    @Override
    public ContentStream getAlternativeContentStream(String token, Path uri, boolean forProcessing, String contentIdentifier) 
            throws NoSuchContentException, ResourceNotFoundException, AuthorizationException, AuthenticationException, Exception {
        return this.repository.getAlternativeContentStream(token, uri, forProcessing, contentIdentifier);
    }
    
    @Override
    public Resource createDocument(String token, Path uri, InputStream inStream) throws Exception {

        RepositoryContext ctx = RepositoryContext.getRepositoryContext();
        if (ctx != null) {
            ctx.clear();
        }
        return this.repository.createDocument(token, uri, inStream);
    }

    @Override
    public Resource createCollection(String token, Path uri) throws Exception {
        return this.repository.createCollection(token, uri);
    }

    @Override
    public void copy(String token, Path srcUri, Path destUri, boolean overwrite, boolean preserveACL)
            throws Exception {

        RepositoryContext ctx = RepositoryContext.getRepositoryContext();
        if (ctx != null) {
            ctx.clear();
        }
        this.repository.copy(token, srcUri, destUri, overwrite, preserveACL);
    }

    @Override
    public void move(String token, Path srcUri, Path destUri, boolean overwrite) throws Exception {

        RepositoryContext ctx = RepositoryContext.getRepositoryContext();
        if (ctx != null) {
            ctx.clear();
        }
        this.repository.move(token, srcUri, destUri, overwrite);
    }

    @Override
    public void delete(String token, Path uri, boolean restorable) throws Exception {

        RepositoryContext ctx = RepositoryContext.getRepositoryContext();
        if (ctx != null) {
            ctx.clear();
        }
        this.repository.delete(token, uri, restorable);
    }

    @Override
    public List<RecoverableResource> getRecoverableResources(String token, Path uri) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, Exception {
        return this.repository.getRecoverableResources(token, uri);
    }

    @Override
    public void recover(String token, Path parentUri, RecoverableResource recoverableResource)
            throws ResourceNotFoundException, AuthorizationException, AuthenticationException, Exception {
        this.repository.recover(token, parentUri, recoverableResource);
    }

    @Override
    public void deleteRecoverable(String token, Path parentUri, RecoverableResource recoverableResource)
            throws Exception {
        this.repository.deleteRecoverable(token, parentUri, recoverableResource);
    }

    @Override
    public boolean exists(String token, Path uri) throws Exception {

        RepositoryContext ctx = RepositoryContext.getRepositoryContext();
        if (ctx != null
                && (ctx.getResourceHit(token, uri, true) != null || ctx.getResourceHit(token, uri, false) != null)) {
            return true;
        }
        return this.repository.exists(token, uri);
    }

    @Override
    public Resource lock(String token, Path uri, String ownerInfo, Depth depth, int requestedTimoutSeconds,
            String lockToken) throws Exception {

        RepositoryContext ctx = RepositoryContext.getRepositoryContext();
        if (ctx != null) {
            ctx.clear();
        }
        return this.repository.lock(token, uri, ownerInfo, depth, requestedTimoutSeconds, lockToken);
    }

    @Override
    public void unlock(String token, Path uri, String lockToken) throws Exception {

        RepositoryContext ctx = RepositoryContext.getRepositoryContext();
        if (ctx != null) {
            ctx.clear();
        }
        this.repository.unlock(token, uri, lockToken);
    }

    @Override
    public Resource storeACL(String token, Path uri, Acl acl) throws Exception {

        RepositoryContext ctx = RepositoryContext.getRepositoryContext();
        if (ctx != null) {
            ctx.clear();
        }
        return this.repository.storeACL(token, uri, acl);
    }

    @Override
    public Resource storeACL(String token, Path uri, Acl acl, boolean validateACL) throws Exception {

        RepositoryContext ctx = RepositoryContext.getRepositoryContext();
        if (ctx != null) {
            ctx.clear();
        }
        return this.repository.storeACL(token, uri, acl, validateACL);
    }
    
    @Override
    public Resource deleteACL(String token, Path uri)
            throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IllegalOperationException,
            ReadOnlyException, Exception {
        RepositoryContext ctx = RepositoryContext.getRepositoryContext();
        if (ctx != null) {
            ctx.clear();
        }
        return this.repository.deleteACL(token, uri);
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
    public Comment addComment(String token, Resource resource, String title, String text) throws RepositoryException,
            AuthenticationException {
        return this.repository.addComment(token, resource, title, text);
    }

    @Override
    public Comment addComment(String token, Comment comment) {
        return this.repository.addComment(token, comment);
    }

    @Override
    public void deleteComment(String token, Resource resource, Comment comment) throws RepositoryException,
            AuthenticationException {
        this.repository.deleteComment(token, resource, comment);
    }

    @Override
    public void deleteAllComments(String token, Resource resource) throws RepositoryException, AuthenticationException {
        this.repository.deleteAllComments(token, resource);
    }

    @Override
    public Comment updateComment(String token, Resource resource, Comment comment) throws RepositoryException,
            AuthenticationException {
        return this.repository.updateComment(token, resource, comment);
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
    public void setReadOnly(String token, boolean readOnly) throws Exception {
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
    public boolean isAuthorized(Resource resource, RepositoryAction action, Principal principal, boolean considerLocks)
            throws Exception {
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
    public Revision createRevision(String token, Path uri, Revision.Type type) throws AuthorizationException, ResourceNotFoundException, AuthenticationException, IOException {
        return this.repository.createRevision(token, uri, type);
    }

    @Override
    public void deleteRevision(String token, Path uri, Revision revision)
            throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, Exception {
        this.repository.deleteRevision(token, uri, revision);
    }

}
