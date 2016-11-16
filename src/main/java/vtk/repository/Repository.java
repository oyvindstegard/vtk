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
package vtk.repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import vtk.repository.search.QueryException;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.Searcher;
import vtk.security.AuthenticationException;
import vtk.security.Principal;

/**
 * Resource repository.
 */
public interface Repository {
    
    /**
     * Hierarchical depth of an operation, for instance with regard to locking.
     */
    public static enum Depth {
        ZERO("0"), ONE("1"), INF("infinity");

        private String val;

        private Depth(String val) {
            this.val = val;
        }

        @Override
        public String toString() {
            return this.val;
        }

        public static Depth fromString(String s) {
            if ("0".equals(s)) {
                return ZERO;
            } else if ("1".equals(s)) {
                return ONE;
            } else if ("infinity".equals(s)) {
                return INF;
            } else {
                throw new IllegalArgumentException("Unknown value: " + s);
            }
        }
    }
    
    /**
     * Is the repository globally set to read only mode?
     * @return <code>true</code> if repository is in read-only mode.
     */
    public boolean isReadOnly();

    /**
     * Is the path set to read-only state ?
     * @param path The path to test.
     * @param forDelete whether to check read-only state wrt. deletion
     *        of path, or just modification of the path or any of its descendants.
     * @return read-only state
     */
    public boolean isReadOnly(Path path, boolean forDelete);

    /**
     * Returns list of path roots which have been set to read-only. If no paths
     * have been set to read-only, then the empty list is returned.
     * @return list of {@link Path paths} which are read-only roots.
     */
    public List<Path> getReadOnlyRoots();
    
    /**
     * Set repository read only option dynamically.
     * 
     * @param token security token
     * @param readOnly desired read-only state
     * @exception AuthorizationException
     *                if an authenticated user is not authorized to set
     *                repository properties
     */
    public void setReadOnly(String token, boolean readOnly) throws AuthorizationException, Exception;

    /**
     * Retrieve a resource at a specified URI authenticated with the session
     * identified by token.
     * 
     * @param token
     *            identifies the client's authenticated session
     * @param uri
     *            the resource identifier
     * @param forProcessing
     *            is the request for uio:read-processed (true) or dav:read
     *            (false)
     * @return a <code>Resource</code> object containing metadata about the
     *         resource
     * @exception ResourceNotFoundException
     *                if the URI does not identify an existing resource
     * @exception AuthorizationException
     *                if an authenticated user is not authorized to access the
     *                resource
     * @exception AuthenticationException
     *                if the resource demands authorization and the client does
     *                not supply a token identifying a valid client session
     * @exception Exception
     *                if an I/O error occurs
     */
    public Resource retrieve(String token, Path uri, boolean forProcessing) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, Exception;

    public Resource retrieve(String token, Path uri, boolean forProcessing, Revision revision) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, Exception;

    /**
     * Gets type information for a given resource
     */
    public TypeInfo getTypeInfo(Resource resource);

    /**
     * Gets type information for a given resource type
     * @param name the name of the resource type
     */
    public TypeInfo getTypeInfo(String name);


    
    /**
     * Returns a listing of the immediate children of a resource.
     * 
     * XXX:: clarify semantics of this operation: if the user is not allowed to
     * retrieve ALL children, what should this method return? A list of only the
     * accessible resources, or some kind of "multi"-status object? With today's
     * RepositoryImpl the behavior is that users that are not allowed to do a
     * retrieve() on a given resource can still view the properties of that
     * resource if they have read access to the parent resource, via a
     * listChildren() call.
     * 
     * @param token
     *            identifies the client's authenticated session
     * @param uri
     *            identifies the resource for which to list children
     * @param forProcessing
     *            is the request for uio:read-processed (true) or dav:read
     *            (false)
     * @return an array of <code>Resource</code> objects representing the
     *         resource's children
     * @exception ResourceNotFoundException
     *                if the resource identified by <code>uri</code> does not
     *                exists
     * @exception AuthorizationException
     *                if an authenticated user is not authorized to access the
     *                resource or any of its immediate children
     * @exception AuthenticationException
     *                if the resource or any of its children demands
     *                authorization and the client does not supply a token
     * @exception Exception
     *                if an I/O error occurs
     */
    public Resource[] listChildren(String token, Path uri, boolean forProcessing) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, Exception;

    /**
     * Store resource properties (metadata) at a specified URI authenticated
     * with the session identified by token.
     * 
     * @param token
     *            identifies the client's authenticated session
     * @param resource the resource to store
     * @return a refreshed instance of the newly stored resource
     * @throws ResourceNotFoundException
     *                if the URI does not identify an existing resource
     * @throws AuthorizationException
     *                if an authenticated user is not authorized to access the
     *                resource
     * @throws AuthenticationException
     *                if the resource demands authorization and the client does
     *                not supply a token identifying a valid client session
     * @throws ReadOnlyException
     *                if the resource is read-only or the repository is in
     *                read-only mode
     * @throws Exception
     *                if an I/O error occurs
     */
    public Resource store(String token, Resource resource) throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, ResourceLockedException, IllegalOperationException, ReadOnlyException, Exception;
    
    /**
     * Store resource properties (metadata) with system context at a specified URI
     * authenticated with the session identified by token. 
     * 
     * @param token
     *            identifies the client's authenticated session
     * @param resource
     *            modified resource to store
     * @param storeContext
     *            specialized store context (store mode)
     * @exception ResourceNotFoundException
     *                if the URI does not identify an existing resource
     * @exception AuthorizationException
     *                if an authenticated user is not authorized to access the
     *                resource
     * @exception AuthenticationException
     *                if the resource demands authorization and the client does
     *                not supply a token identifying a valid client session
     * @exception ReadOnlyException
     *                if the resource is read-only or the repository is in
     *                read-only mode
     * @exception Exception
     *                if an I/O error occurs
     */
    public Resource store(String token, Resource resource, StoreContext storeContext) throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, ResourceLockedException, IllegalOperationException, ReadOnlyException, Exception;
    
    default public Resource storeContent(String token, Path uri, InputStream stream) throws AuthorizationException,
            AuthenticationException, ResourceNotFoundException, ResourceLockedException, IllegalOperationException,
            ReadOnlyException, Exception {
        return storeContent(token, uri, ContentInputSources.fromStream(stream));
    }

    /**
     * Requests that a a byte stream be written to the content of a resource in
     * the repository.
     *
     * @param token
     *            identifies the client's authenticated session
     * @param uri
     *            the resource identifier
     * @param content
     *            rhe resource content to store
     * @return the modified resource object
     * @exception ResourceNotFoundException
     *                if the URI does not identify an existing resource
     * @exception AuthorizationException
     *                if an authenticated user is not authorized to access the
     *                resource
     * @exception AuthenticationException
     *                if the resource demands authorization and the client does
     *                not supply a token identifying a valid client session
     * @exception ReadOnlyException
     *                if the resource is read-only or the repository is in
     *                read-only mode
     * @exception Exception
     *                if an I/O error occurs
     */
    public Resource storeContent(String token, Path uri, ContentInputSource content) throws AuthorizationException,
            AuthenticationException, ResourceNotFoundException, ResourceLockedException, IllegalOperationException,
            ReadOnlyException, Exception;
    
    default public Resource storeContent(String token, Path uri, InputStream stream, Revision revision) throws AuthorizationException,
        AuthenticationException, ResourceNotFoundException, ResourceLockedException, IllegalOperationException, ReadOnlyException, Exception {
        return storeContent(token, uri, ContentInputSources.fromStream(stream), revision);
    }

    public Resource storeContent(String token, Path uri, ContentInputSource content, Revision revision) throws AuthorizationException,
        AuthenticationException, ResourceNotFoundException, ResourceLockedException, IllegalOperationException,
        ReadOnlyException, Exception;

    /**
     * Obtains a stream to input bytes from a resource stored in the repository.
     * 
     * @param token
     *            identifies the client's authenticated session
     * @param uri
     *            the resource identifier
     * @param forProcessing
     *            is the request for uio:read-processed (true) or dav:read
     *            (false)
     * @return a <code>java.io.InputStream</code> representing the byte stream
     *         to be read from
     * @exception ResourceNotFoundException
     *                if the URI does not identify an existing resource
     * @exception AuthorizationException
     *                if an authenticated user is not authorized to access the
     *                resource
     * @exception AuthenticationException
     *                if the resource demands authorization and the client does
     *                not supply a token identifying a valid client session
     * @exception Exception
     *                if an I/O error occurs
     */
    public InputStream getInputStream(String token, Path uri, boolean forProcessing) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, Exception;

    /**
     * Obtains input stream for a particular revision. Not all resource types
     * have revision support.
     * 
     * @param token
     * @param uri
     * @param forProcessing
     * @param revision
     * @return
     * @throws ResourceNotFoundException
     * @throws AuthorizationException
     * @throws AuthenticationException
     * @throws Exception 
     */
    public InputStream getInputStream(String token, Path uri, boolean forProcessing, Revision revision) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, Exception;
    
    /**
     * Obtains an alternative {@link InputStreamWithLength content stream} for the
     * resource at the given path. The repository generally does not
     * support alternative streams, but they may be available through
     * extensions. To get an alternative stream, you must know the particular
     * content identifier, which may be extension-specific.
     * 
     * @param token
     * @param uri
     * @param forProcessing
     * @param contentIdentifier an implementation specific content identifier.
     * @return instance of {@link InputStreamWithLength} with alternative content
     * @throws NoSuchContentException if no such alternative content exists for
     * the given resource.
     */
    public InputStream getAlternativeInputStream(String token, Path uri, boolean forProcessing,
            String contentIdentifier)
            throws NoSuchContentException, ResourceNotFoundException,
            AuthorizationException, AuthenticationException, Exception;

    /**
     * Creates a new document in the repository.
     *
     * @param token
     *            identifies the client's authenticated session
     * @param uri
     *            the resource identifier to be created
     * @param content
     *            the resource's content
     * @return a <code>Resource</code> representing metadata about the newly
     *         created resource
     * @exception IllegalOperationException
     *                if the resource identified by the URI alredy exists in the
     *                repository
     * @exception AuthorizationException
     *                if an authenticated user is not authorized to create the
     *                resource
     * @exception AuthenticationException
     *                if the resource demands authorization and the client does
     *                not supply a token identifying a valid client session
     * @exception ResourceLockedException
     *                if the parent resource is locked
     * @exception ReadOnlyException
     *                if the resource is read-only or the repository is in
     *                read-only mode
     * @exception Exception
     *                if an I/O error occurs
     */
    public Resource createDocument(String token, Path uri, ContentInputSource content) throws IllegalOperationException,
            AuthorizationException, AuthenticationException, ResourceLockedException, ReadOnlyException, Exception;
    
    default public Resource createDocument(String token, Path uri, InputStream inputStream) throws IllegalOperationException,
            AuthorizationException, AuthenticationException, ResourceLockedException, ReadOnlyException, Exception {
        return createDocument(token, uri, ContentInputSources.fromStream(inputStream));
    }

    /**
     * Creates a new collection resource in the repository.
     * 
     * @param token
     *            identifies the client's authenticated session
     * @param uri
     *            the resource identifier to be created
     * @return a <code>Resource</code> representing metadata about the newly
     *         created collection
     * @exception IllegalOperationException
     *                if the resource identified by the URI alredy exists in the
     *                repository, or if an invalid URI is supplied
     * @exception AuthorizationException
     *                if an authenticated user is not authorized to create the
     *                resource
     * @exception AuthenticationException
     *                if the resource demands authorization and the client does
     *                not supply a token identifying a valid client session
     * @exception ReadOnlyException
     *                if the resource is read-only or the repository is in
     *                read-only mode
     * @exception ResourceLockedException
     *                if the parent resource is locked
     * @exception Exception
     *                if an I/O error occurs
     */
    public Resource createCollection(String token, Path uri) throws AuthorizationException, AuthenticationException,
            IllegalOperationException, ResourceLockedException, ReadOnlyException, Exception;


    /**
     * Performs a copy operation on a resource.
     * 
     * After the operation has completed successfully, the resource identified
     * by <code>destUri</code> will be a duplicate of the original resource,
     * including properties. If the resource to be copied is a collection, the
     * <code>depth</code> determines whether all internal members should also be
     * copied. The legal values of <code>depth</code> for collections are: "0"
     * and "infinity". When copying resources, the value of <code>depth</code>
     * is ignored.
     * 
     * <p>
     * Access Control Lists (ACLs) are not preserved on the destination resource
     * unless the parameter <code>preserveACL</code> is <code>true</code>.
     * 
     * <p>
     * The destination URI must be valid in the sense that it must not
     * potentially cause namespace inconsistency in the repository. For example,
     * when trying to copy the source URI <code>/a/b</code> to
     * <code>/c/d/e</code>, the requirement is that the URI <code>/c/d</code>
     * must be an existing collection.
     * </p>
     * 
     * @param token
     *            identifies the client's authenticated session
     * @param srcUri
     *            identifies the resource to copy from
     * @param destUri
     *            identifies the resource to copy to
     * @param overwrite
     *            determines if the operation should overwrite existing
     *            resources
     * @exception IllegalOperationException
     *                if the resource identified by the destination URI can not
     *                be created due to namespace inconsistency
     * @exception AuthorizationException
     *                if an authenticated user is not authorized to either
     *                create the resource specified by <code>destUri</code> or
     *                read the resource specified by <code>srcUri</code>
     * @exception AuthenticationException
     *                if the resource demands authorization and the client does
     *                not supply a token identifying a valid client session
     * @exception FailedDependencyException
     *                if the copying of <code>srcUri</code> failed due to a
     *                dependency of another resource (e.g. the client is not
     *                authorized to read an internal member of the
     *                <code>srcUri</code>)
     * @exception ResourceOverwriteException
     *                if <code>overwrite</code> is set to <code>false</code> but
     *                the resource identified by <code>destUri</code> already
     *                exists
     * @exception ResourceLockedException
     *                if the resource identified by <code>destUri</code> is
     *                locked
     * @exception ReadOnlyException
     *                if the resource is read-only or the repository is in
     *                read-only mode
     * @exception Exception
     *                if an I/O error occurs
     */
    public void copy(String token, Path srcUri, Path destUri, boolean overwrite, boolean preserveACL)
            throws IllegalOperationException, AuthorizationException, AuthenticationException,
            FailedDependencyException, ResourceOverwriteException, ResourceLockedException, ResourceNotFoundException,
            ReadOnlyException, Exception;

    /**
     * Moves a resource from one URI to another.
     * 
     * @param token
     *            identifies the client's authenticated session
     * @param srcUri
     *            identifies the resource to move from
     * @param destUri
     *            identifies the resource to move to
     * @param overwrite
     *            determines if the operation should overwrite existing
     *            resources
     * @exception IllegalOperationException
     *                if the resource identified by the destination URI can not
     *                be created due to namespace inconsistency
     * @exception AuthorizationException
     *                if an authenticated user is not authorized to create the
     *                resource
     * @exception AuthenticationException
     *                if the resource demands authorization and the client does
     *                not supply a token identifying a valid client session
     * @exception FailedDependencyException
     *                if the copying of <code>srcUri</code> failed due to a
     *                dependency of another resource (e.g. the client is not
     *                authorized to read an internal member of the
     *                <code>srcUri</code>)
     * @exception ResourceOverwriteException
     *                if <code>overwrite</code> is set to <code>false</code> but
     *                the resource identified by <code>destUri</code> already
     *                exists
     * @exception ResourceLockedException
     *                if the resource identified by <code>destUri</code> is
     *                locked
     * @exception ReadOnlyException
     *                if the resource is read-only or the repository is in
     *                read-only mode
     * @exception Exception
     *                if an I/O error occurs
     */
    public void move(String token, Path srcUri, Path destUri, boolean overwrite) throws IllegalOperationException,
            AuthorizationException, AuthenticationException, FailedDependencyException, ResourceOverwriteException,
            ResourceLockedException, ResourceNotFoundException, ReadOnlyException, Exception;

    /**
     * Deletes a resource by either moving it to trash can or deleting it
     * permanently (decided by parameter "restorable").
     * 
     * @param token
     *            identifies the client's authenticated session
     * @param uri
     *            identifies the resource to delete
     * @param restoreable
     *            whether or not resource is to be permanently deleted or just
     *            moved to trash can
     * 
     * @exception IllegalOperationException
     *                if the resource identified by the destination URI can not
     *                be deleted due to namespace inconsistency
     * @exception AuthorizationException
     *                if an authenticated user is not authorized to delete the
     *                resource
     * @exception AuthenticationException
     *                if the resource demands authorization and the client does
     *                not supply a token identifying a valid client session
     * @exception ResourceNotFoundException
     *                if the resource identified by <code>destUri</code> does
     *                not exists
     * @exception ResourceLockedException
     *                if the resource identified by <code>destUri</code> is
     *                locked
     * @exception FailedDependencyException
     *                if the deletion of <code>uri</code> failed due to a
     *                dependency of another resource (e.g. the client is not
     *                authorized to read an internal member of the
     *                <code>uri</code>)
     * @exception ReadOnlyException
     *                if the resource is read-only or the repository is in
     *                read-only mode
     * @exception Exception
     *                if an I/O error occurs
     */
    public void delete(String token, Path uri, boolean restoreable) throws IllegalOperationException,
            AuthorizationException, AuthenticationException, ResourceNotFoundException, ResourceLockedException,
            FailedDependencyException, ReadOnlyException, Exception;

    /**
     * @param token
     *            Security token
     * @param uri
     *            Uri og collection to get recoverable resources fro
     * @return List of recoverable resources, i.e. resources that have been
     *         marked for deletion under the parent collection given by uri
     */
    public List<RecoverableResource> getRecoverableResources(String token, Path uri) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, Exception;

    /**
     * @param token
     *            Security token
     * @param parentUri
     *            Collection containing recoverable resources
     * 
     * @param recoverableResource
     *            The recoverable resource to recover
     */
    public void recover(String token, Path parentUri, RecoverableResource recoverableResource)
            throws ResourceNotFoundException, AuthorizationException, AuthenticationException, Exception;

    /**
     * Permanently delete a resource from trash can.
     * 
     * @param token
     *            client's authenticated session
     * @param parentUri
     *            path of resource containing the recoverable resource
     * @param recoverableResource
     *            the recoverable resource to delete permanently
     * @throws Exception
     */
    public void deleteRecoverable(String token, Path parentUri, RecoverableResource recoverableResource)
            throws Exception;


    /**
     * Tests whether a resource identified by this URI exists.
     * 
     * @param token
     *            identifies the client's authenticated session
     * @param uri
     *            identifies the resource
     * @return a <code>true</code> if an only if the resource identified by the
     *         uri exists, <code>false</code> otherwise
     * @exception AuthorizationException
     *                if an authenticated user is denied access to the resource
     * @exception AuthenticationException
     *                if the resource demands authorization and the client does
     *                not supply a token identifying a valid client session
     * @exception Exception
     *                if an I/O error occurs
     */
    public boolean exists(String token, Path uri) throws AuthorizationException, AuthenticationException, Exception;

    /**
     * Performs a lock operation on a resource.
     * 
     * @param token
     *            identifies the client's authenticated session
     * @param uri
     *            identifies the resource to lock
     * @param ownerInfo
     *            user supplied information about the person requesting the
     *            lock, e.g. an email address, etc. Note that this is not the
     *            actual <i>username</i> of the person, such information is
     *            obtained using the token.
     * @param depth
     *            specifies whether all internal members of a resource should be
     *            locked or not. Legal values are <code>0</code> or
     *            <code>infinity</code>
     * @param requestedTimoutSeconds
     *            the timeout period wanted (in seconds)
     * @param lockToken
     *            - if <code>null</code>, the a new lock is obtained, otherwise
     *            it is interpreted as a lock refresh request (the resource must
     *            be locked by the same principal and the lock token must match
     *            the existing one).
     * 
     *            XXX: This return value description is wrong:
     * @return a string representing the lock token obtained
     * 
     * @exception ResourceNotFoundException
     *                if the resource identified by <code>uri</code> does not
     *                exists
     * @exception AuthorizationException
     *                if an authenticated user is denied access to the resource
     * @exception AuthenticationException
     *                if the resource demands authorization and the client does
     *                not supply a token identifying a valid client session
     * @exception FailedDependencyException
     *                if the locking of <code>uri</code> failed due to a
     *                dependency on another resource (e.g. read an internal
     *                member resource is already locked by another client)
     * @exception ResourceLockedException
     *                if the resource identified by <code>uri</code> is already
     *                locked
     * @exception IllegalOperationException
     *                if invalid parameters are supplied
     * @exception ReadOnlyException
     *                if the resource is read-only or the repository is in
     *                read-only mode
     * @exception Exception
     *                if an I/O error occurs
     */
    public Resource lock(String token, Path uri, String ownerInfo, Depth depth, int requestedTimoutSeconds,
            String lockToken) throws ResourceNotFoundException, AuthorizationException, AuthenticationException,
            FailedDependencyException, ResourceLockedException, IllegalOperationException, ReadOnlyException, Exception;

    /**
     * Performs an unlock operation on a resource.
     * 
     * @param token
     *            identifies the client's authenticated session
     * @param uri
     *            identifies the resource to unlock
     * @exception ResourceNotFoundException
     *                if the resource identified by <code>uri</code> does not
     *                exists
     * @exception AuthorizationException
     *                if an authenticated user is denied access to the resource
     * @exception AuthenticationException
     *                if the resource demands authorization and the client does
     *                not supply a token identifying a valid client session
     * @exception ResourceLockedException
     *                if the resource identified by <code>uri</code> is already
     *                locked by another client
     * @exception ReadOnlyException
     *                if the resource is read-only or the repository is in
     *                read-only mode
     * @exception Exception
     *                if an I/O error occurs
     */
    public void unlock(String token, Path uri, String lockToken) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, ResourceLockedException, ReadOnlyException, Exception;

    /**
     * Stores the Access Control List (ACL) for a resource.
     * 
     * @param token
     *            identifies the client's authenticated session
     * @param uri
     *            identifies the resource for which to store the ACL.
     * @exception ResourceNotFoundException
     *                if the resource identified by <code>uri</code> does not
     *                exists
     * @exception AuthorizationException
     *                if an authenticated user is denied access to the resource
     * @exception AuthenticationException
     *                if the resource demands authorization and the client does
     *                not supply a token identifying a valid client session
     * @exception IllegalOperationException
     *                if the supplied ACL is invalid
     * @exception ReadOnlyException
     *                if the resource is read-only or the repository is in
     *                read-only mode
     * @exception Exception
     *                if an I/O error occurs
     */
    public Resource storeACL(String token, Path uri, Acl acl) throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IllegalOperationException, ReadOnlyException, Exception;

    /**
     * Store ACL, like {@link #storeACL(java.lang.String, vtk.repository.Path, vtk.repository.Acl) }, but
     * with optionally disabling ACL validation.
     * 
     * Used by resource archiver expansion and from WebADV special propset APIs.
     * @see #storeACL(java.lang.String, vtk.repository.Path, vtk.repository.Acl) 
     * @param validateAcl whether to validate ACL before store or not
     */
    public Resource storeACL(String token, Path uri, Acl acl, boolean validateAcl) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IllegalOperationException, ReadOnlyException, Exception;

    /**
     * Delete ACL at the given path (turn <strong>on</strong> inheritance from
     * nearest ancestor node).
     *
     * @param token
     * @param uri
     * @return
     * @throws ResourceNotFoundException
     * @throws AuthorizationException
     * @throws AuthenticationException
     * @throws IllegalOperationException
     * @throws ReadOnlyException
     * @throws Exception
     */
    public Resource deleteACL(String token, Path uri) throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IllegalOperationException, ReadOnlyException, Exception;

    public boolean isValidAclEntry(Privilege privilege, Principal principal); 
    
    public boolean isBlacklisted(Privilege privilege, Principal principal);

    public boolean authorize(Principal principal, Acl acl, Privilege privilege);
    
    /**
     * Checks whether a principal is allowed to perform an operation on a
     * resource.
     * 
     * @param resource
     *            the resource in question
     * @param action
     *            the operation in question
     * @param principal
     *            the principal in question
     * @param considerLocks
     *            whether or not to take resource locks into account
     * @return <code>true</code> if the principal is allowed to perform the
     *         operation, <code>false</code> otherwise
     * @throws Exception
     *             if an error occurs
     */
    public boolean isAuthorized(Resource resource, RepositoryAction action, Principal principal, boolean considerLocks)
            throws Exception;
    
    
    public List<Revision> getRevisions(String token, Path uri) throws AuthorizationException, ResourceNotFoundException, AuthenticationException, IOException;
    
    public Revision createRevision(String token, Path uri, Revision.Type type) throws AuthorizationException, ResourceNotFoundException, AuthenticationException, IOException;

    
    public void deleteRevision(String token, Path uri, Revision revision) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, Exception;
    
    /**
     * Lists all comments on a resource. Comments on child resources will not be
     * listed.
     * 
     * @param token
     *            the security token of the current principal
     * @param resource
     *            the resource for which to list comments
     * @return a list of comments
     * @exception RepositoryException
     *                if an error occurs
     * @exception AuthenticationException
     *                if an error occurs
     */
    public List<Comment> getComments(String token, Resource resource) throws RepositoryException,
            AuthenticationException;

    /**
     * Lists a number of comments on a resource or its descendants, sorted by
     * the comments' date values. The number of comments returned may vary
     * depending on the permissions settings of the commented resource(s), the
     * only guarantee is that no more than <code>max</code> comments are
     * returned.
     * 
     * @param token
     *            the security token of the current principal
     * @param resource
     *            the resource for which to list comments
     * @param deep
     *            determines whether or not comments on descendant resources are
     *            listed
     * @param max
     *            the maximum number of comments that are listed
     * @return a list of comments
     * @exception RepositoryException
     *                if an error occurs
     * @exception AuthenticationException
     *                if an error occurs
     */
    public List<Comment> getComments(String token, Resource resource, boolean deep, int max)
            throws RepositoryException, AuthenticationException;

    /**
     * Adds a comment on a resource
     * 
     * @param token
     *            the security token of the current principal
     * @param resource
     *            the resource
     * @param title
     *            the title of the comment
     * @param text
     *            the text of the comment
     * @return the newly added comment
     * @exception RepositoryException
     *                if an error occurs
     * @exception AuthenticationException
     *                if an error occurs
     */
    public Comment addComment(String token, Resource resource, String title, String text) throws RepositoryException,
            AuthenticationException;

    /**
     * 
     * Store a single comment object
     * 
     * @param token
     *            the security token of the current principal
     * @param comment
     *            The comment to store
     * @return The stored comment
     */
    public Comment addComment(String token, Comment comment) throws AuthenticationException;

    /**
     * Deletes a comment on a resource
     * 
     * @param token
     *            the security token of the current principal
     * @param resource
     *            the resource
     * @param comment
     *            the comment to delete
     * @exception RepositoryException
     *                if an error occurs
     * @exception AuthenticationException
     *                if an error occurs
     */
    public void deleteComment(String token, Resource resource, Comment comment) throws RepositoryException,
            AuthenticationException;

    /**
     * Deletes all comments on a resource
     * 
     * @param token
     *            the security token of the current principal
     * @param resource
     *            the resource
     * @exception RepositoryException
     *                if an error occurs
     * @exception AuthenticationException
     *                if an error occurs
     */
    public void deleteAllComments(String token, Resource resource) throws RepositoryException, AuthenticationException;

    /**
     * Updates a comment on a resource.
     * 
     * @param token
     *            the security token of the current principal
     * @param resource
     *            the resource
     * @exception RepositoryException
     *                if an error occurs
     * @exception AuthenticationException
     *                if an error occurs
     */
    public Comment updateComment(String token, Resource resource, Comment comment) throws RepositoryException,
            AuthenticationException;

    /**
     * Get the repository ID.
     * @return repository identifier as a string. This is usually the same
     * as the fully qualified host name.
     */
    public String getId();

    /**
     * Execute a repository search (higher-level access to search API).
     * Searching through this method will enforce that all search results are
     * published resources.
     * 
     * @param token
     * @param search
     * @return A search result set.
     * @see Searcher#execute(java.lang.String, vtk.repository.search.Search) 
     * 
     * @throws QueryException
     */
    public ResultSet search(String token, Search search) throws QueryException;

}
