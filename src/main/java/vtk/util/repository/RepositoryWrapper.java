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
package vtk.util.repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

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
import vtk.repository.ResourceLockedException;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.ResourceOverwriteException;
import vtk.repository.Revision;
import vtk.repository.Revision.Type;
import vtk.repository.StoreContext;
import vtk.repository.TypeInfo;
import vtk.repository.search.QueryException;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.security.AuthenticationException;
import vtk.security.Principal;

public class RepositoryWrapper implements Repository {
    private Repository repository;
    
    public RepositoryWrapper(Repository repository) {
        this.repository = repository;
    }
    
    @Override
    public boolean isReadOnly() {
        return repository.isReadOnly();
    }

    @Override
    public boolean isReadOnly(Path path, boolean forDelete) {
        return repository.isReadOnly(path, forDelete);
    }

    @Override
    public List<Path> getReadOnlyRoots() {
        return repository.getReadOnlyRoots();
    }

    @Override
    public void setReadOnly(String token, boolean readOnly)
            throws AuthorizationException, IOException {
        repository.setReadOnly(token, readOnly);
        
    }

    @Override
    public Resource retrieve(String token, Path uri, boolean forProcessing)
            throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IOException {
        return repository.retrieve(token, uri, forProcessing);
        
    }

    @Override
    public Resource retrieve(String token, Path uri, boolean forProcessing,
            Revision revision) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {
        return repository.retrieve(token, uri, forProcessing, revision);
        
    }

    @Override
    public TypeInfo getTypeInfo(Resource resource) {
        return repository.getTypeInfo(resource);
    }

    @Override
    public TypeInfo getTypeInfo(String name) {
        return repository.getTypeInfo(name);
    }

    @Override
    public Resource[] listChildren(String token, Path uri,
            boolean forProcessing) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {
        return repository.listChildren(token, uri, forProcessing);
    }

    @Override
    public Resource store(String token, String lockToken, Resource resource)
            throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, ResourceLockedException,
            IllegalOperationException, ReadOnlyException, IOException {
        return repository.store(token, lockToken, resource);
    }

    @Override
    public Resource store(String token, String lockToken, Resource resource,
            StoreContext storeContext) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException,
            ResourceLockedException, IllegalOperationException,
            ReadOnlyException, IOException {
        return repository.store(token, lockToken, resource);
    }

    @Override
    public Resource storeContent(String token, String lockToken, Path uri, ContentInputSource content)
            throws AuthorizationException, AuthenticationException,
            ResourceNotFoundException, ResourceLockedException,
            IllegalOperationException, ReadOnlyException, IOException {
        return repository.storeContent(token, lockToken, uri, content);
    }

    @Override
    public Resource storeContent(String token, String lockToken, Path uri, ContentInputSource content, Revision revision)
            throws AuthorizationException, AuthenticationException,
            ResourceNotFoundException, ResourceLockedException,
            IllegalOperationException, ReadOnlyException, IOException {
        return repository.storeContent(token, lockToken, uri, content, revision);
    }

    @Override
    public InputStream getInputStream(String token, Path uri,
            boolean forProcessing) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {
        return repository.getInputStream(token, uri, forProcessing);
    }

    @Override
    public InputStream getInputStream(String token, Path uri,
            boolean forProcessing, Revision revision)
            throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IOException {
        return repository.getInputStream(token, uri, forProcessing, revision);
    }

    @Override
    public InputStream getAlternativeInputStream(String token,
            Path uri, boolean forProcessing, String contentIdentifier)
            throws NoSuchContentException, ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {
        return repository.getAlternativeInputStream(token, uri, forProcessing, contentIdentifier);
    }

    @Override
    public Resource createDocument(String token, String lockToken, Path uri, ContentInputSource content)
            throws IllegalOperationException,
            AuthorizationException, AuthenticationException,
            ResourceLockedException, ReadOnlyException, IOException {
        return repository.createDocument(token, lockToken, uri, content);
    }

    @Override
    public Resource createCollection(String token, String lockToken, Path uri)
            throws AuthorizationException, AuthenticationException,
            IllegalOperationException, ResourceLockedException,
            ReadOnlyException, IOException {
        return repository.createCollection(token, lockToken, uri);
    }

    @Override
    public void copy(String token, String lockToken, Path srcUri, Path destUri,
            boolean overwrite, boolean preserveACL)
            throws IllegalOperationException, AuthorizationException,
            AuthenticationException, FailedDependencyException,
            ResourceOverwriteException, ResourceLockedException,
            ResourceNotFoundException, ReadOnlyException, IOException {
        repository.copy(token, lockToken, srcUri, destUri, overwrite, preserveACL);
    }

    @Override
    public void move(String token, String lockToken, Path srcUri, Path destUri,
            boolean overwrite) throws IllegalOperationException,
            AuthorizationException, AuthenticationException,
            FailedDependencyException, ResourceOverwriteException,
            ResourceLockedException, ResourceNotFoundException,
            ReadOnlyException, IOException {
        repository.move(token, lockToken, srcUri, destUri, overwrite);
    }

    @Override
    public void delete(String token, String lockToken, Path uri, boolean restoreable)
            throws IllegalOperationException, AuthorizationException,
            AuthenticationException, ResourceNotFoundException,
            ResourceLockedException, FailedDependencyException,
            ReadOnlyException, IOException {
        repository.delete(token, lockToken, uri, restoreable);
    }

    @Override
    public List<RecoverableResource> getRecoverableResources(String token,
            Path uri) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {
        return repository.getRecoverableResources(token, uri);
    }

    @Override
    public void recover(String token, String lockToken, Path parentUri,
            RecoverableResource recoverableResource)
            throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IOException {
        repository.recover(token, lockToken, parentUri, recoverableResource);
    }

    @Override
    public void deleteRecoverable(String token, String lockToken, Path parentUri,
            RecoverableResource recoverableResource) throws IOException {
        repository.deleteRecoverable(token, lockToken, parentUri, recoverableResource);
        
    }

    @Override
    public boolean exists(String token, Path uri)
            throws AuthorizationException, AuthenticationException,
            IOException {
        return repository.exists(token, uri);
    }

    @Override
    public Resource lock(String token, Path uri, String ownerInfo,
            Depth depth, int requestedTimoutSeconds, String lockToken, Lock.Type lockType)
            throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, FailedDependencyException,
            ResourceLockedException, IllegalOperationException,
            ReadOnlyException, IOException {
        return repository.lock(token, uri, ownerInfo, depth, requestedTimoutSeconds, lockToken, lockType);
    }

    @Override
    public void unlock(String token, Path uri, String lockToken)
            throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, ResourceLockedException,
            ReadOnlyException, IOException {
        repository.unlock(token, uri, lockToken);
    }

    @Override
    public Resource storeACL(String token, String lockToken, Path uri, Acl acl)
            throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IllegalOperationException,
            ReadOnlyException, IOException {
        return repository.storeACL(token, lockToken, uri, acl);
    }

    @Override
    public Resource storeACL(String token, String lockToken, Path uri, Acl acl,
            boolean validateAcl) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException,
            IllegalOperationException, ReadOnlyException, IOException {
        return repository.storeACL(token, lockToken, uri, acl, validateAcl);
    }

    @Override
    public Resource deleteACL(String token, String lockToken, Path uri)
            throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IllegalOperationException,
            ReadOnlyException, IOException {
        return repository.deleteACL(token, lockToken, uri);
    }

    @Override
    public boolean isValidAclEntry(Privilege privilege, Principal principal) {
        return repository.isValidAclEntry(privilege, principal);
    }

    @Override
    public boolean isBlacklisted(Privilege privilege, Principal principal) {
        return repository.isBlacklisted(privilege, principal);
    }

    @Override
    public boolean authorize(Principal principal, Acl acl,
            Privilege privilege) {
        return repository.authorize(principal, acl, privilege);
    }

    @Override
    public boolean isAuthorized(Resource resource, RepositoryAction action,
            Principal principal, boolean considerLocks) throws IOException {
        return repository.isAuthorized(resource, action, principal, considerLocks);

    }
    @Override
    public boolean isAuthorized(Resource resource, RepositoryAction action,
            Principal principal, String lockToken) throws IOException {
        return repository.isAuthorized(resource, action, principal, lockToken);

    }

    @Override
    public List<Revision> getRevisions(String token, Path uri)
            throws AuthorizationException, ResourceNotFoundException,
            AuthenticationException, IOException {
        return repository.getRevisions(token, uri);
    }

    @Override
    public Revision createRevision(String token, String lockToken, Path uri, Type type)
            throws AuthorizationException, ResourceNotFoundException,
            AuthenticationException, IOException {
        return repository.createRevision(token, lockToken, uri, type);
    }

    @Override
    public void deleteRevision(String token, String lockToken, Path uri, Revision revision)
            throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IOException {
        repository.deleteRevision(token, lockToken, uri, revision);
    }

    @Override
    public List<Comment> getComments(String token, Resource resource)
            throws RepositoryException, AuthenticationException {
        return repository.getComments(token, resource);
    }

    @Override
    public List<Comment> getComments(String token, Resource resource,
            boolean deep, int max) throws RepositoryException,
            AuthenticationException {
        return repository.getComments(token, resource);
    }

    @Override
    public Comment addComment(String token, String lockToken, Resource resource,
            String title, String text) throws RepositoryException,
            AuthenticationException {
        return repository.addComment(token, lockToken, resource, title, text);
    }

    @Override
    public Comment addComment(String token, String lockToken, Comment comment)
            throws AuthenticationException {
        return repository.addComment(token, lockToken, comment);
    }

    @Override
    public void deleteComment(String token, String lockToken, Resource resource,
            Comment comment) throws RepositoryException,
            AuthenticationException {
        repository.deleteComment(token, lockToken, resource, comment);
    }

    @Override
    public void deleteAllComments(String token, String lockToken, Resource resource)
            throws RepositoryException, AuthenticationException {
        repository.deleteAllComments(token, lockToken, resource);
    }

    @Override
    public Comment updateComment(String token, String lockToken, Resource resource,
            Comment comment) throws RepositoryException,
            AuthenticationException {
        return repository.updateComment(token, lockToken, resource, comment);
    }

    @Override
    public String getId() {
        return repository.getId();
    }

    @Override
    public ResultSet search(String token, Search search)
            throws QueryException {
        return repository.search(token, search);
    }        
}

