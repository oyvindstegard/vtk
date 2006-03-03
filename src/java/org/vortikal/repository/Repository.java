/* Copyright (c) 2004, University of Oslo, Norway
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

import org.vortikal.security.AuthenticationException;

import java.io.IOException;
import java.io.InputStream;


/**
 * This interface defines the content repositorys
 * externally available webDAV-like functionality.
 */
public interface Repository {
    /**
     * Retrieves a description of various (runtime) repository configuration
     * options.
     * @exception IOException if an I/O error occurs
     */
    public Configuration getConfiguration() throws IOException;

    /**
     * Sets repository configuration options dynamically.
     * @exception AuthorizationException if an authenticated user is
     * not authorized to set repository properties
     * @exception IOException if an I/O error occurs
     */
    public void setConfiguration(String token, Configuration configuration)
        throws AuthorizationException, IOException;

    /**
     * Retrieve a resource at a specified URI authenticated with the
     * session identified by token.
     *
     * @param token identifies the client's authenticated session
     * @param uri the resource identifier
     * @param forProcessing is the request for uio:read-processed
     * (true) or dav:read (false)
     * @return a <code>Resource</code> object containing metadata
     * about the resource
     * @exception ResourceNotFoundException if the URI does not identify
     * an existing resource
     * @exception AuthorizationException if an authenticated user
     * is not authorized to access the resource
     * @exception AuthenticationException if the resource demands
     * authorization and the client does not supply a token
     * identifying a valid client session
     * @exception IOException if an I/O error occurs
     */
    public Resource retrieve(String token, String uri, boolean forProcessing)
        throws ResourceNotFoundException, AuthorizationException, 
            AuthenticationException, IOException;

    /**
     * Returns a listing of the immediate children of a
     * resource.
     *
     * FIXME: clarify semantics of this operation: if the user is not
     * allowed to retrieve ALL children, what should this method
     * return? A list of only the accessible resources, or some kind
     * of "multi"-status object? With today's RepositoryImpl the
     * behavior is that users that are not allowed to do a retrieve()
     * on a given resource can still view the properties of that
     * resource if they have read access to the parent resource, via a
     * listChildren() call.
     *
     * @param token identifies the client's authenticated session
     * @param uri identifies the resource for which to list children
     * @param forProcessing is the request for uio:read-processed
     * (true) or dav:read (false)
     * @return an array of <code>Resource</code> objects
     * representing the resource's children
     * @exception ResourceNotFoundException if the resource identified
     * by <code>uri</code> does not exists
     * @exception AuthorizationException if an authenticated user is
     * not authorized to access the resource or any of its immediate
     * children
     * @exception AuthenticationException if the resource or any of
     * its children demands authorization and the client does not
     * supply a token
     * @exception IOException if an I/O error occurs
     */
    public Resource[] listChildren(String token, String uri,
        boolean forProcessing)
        throws ResourceNotFoundException, AuthorizationException, 
            AuthenticationException, IOException;

    /**
     * Store resource properties (metadata) at a specified URI
     * authenticated with the session identified by token.
     *
     * @param token identifies the client's authenticated session
     * @exception ResourceNotFoundException if the URI does not identify
     * an existing resource
     * @exception AuthorizationException if an authenticated user
     * is not authorized to access the resource
     * @exception AuthenticationException if the resource demands
     * authorization and the client does not supply a token
     * identifying a valid client session
     * @exception ReadOnlyException if the resource is read-only or
     * the repository is in read-only mode
     * @exception IOException if an I/O error occurs
     */
    public void store(String token, Resource resource)
        throws ResourceNotFoundException, AuthorizationException, 
            AuthenticationException, ResourceLockedException, 
            IllegalOperationException, ReadOnlyException, IOException;

    /**
     * Requests that a a byte stream be written to the content of a
     * resource in the repository.
     *
     * @param token identifies the client's authenticated session
     * @param uri the resource identifier
     * @param byteStream a <code>java.io.InputStream</code> representing the
     * byte stream to be read from
     * @exception ResourceNotFoundException if the URI does not identify
     * an existing resource
     * @exception AuthorizationException if an authenticated user
     * is not authorized to access the resource
     * @exception AuthenticationException if the resource demands
     * authorization and the client does not supply a token
     * identifying a valid client session
     * @exception ReadOnlyException if the resource is read-only or
     * the repository is in read-only mode
     * @exception IOException if an I/O error occurs
     */
    public void storeContent(String token, String uri, InputStream byteStream)
        throws AuthorizationException, AuthenticationException, 
            ResourceNotFoundException, ResourceLockedException, 
            IllegalOperationException, ReadOnlyException, IOException;

    /**
     * Obtains a stream to input bytes from a resource stored in the
     * repository.
     *
     * @param token identifies the client's authenticated session
     * @param uri the resource identifier
     * @param forProcessing is the request for uio:read-processed
     * (true) or dav:read (false)
     * @return a <code>java.io.InputStream</code> representing the
     * byte stream to be read from
     * @exception ResourceNotFoundException if the URI does not identify
     * an existing resource
     * @exception AuthorizationException if an authenticated user
     * is not authorized to access the resource
     * @exception AuthenticationException if the resource demands
     * authorization and the client does not supply a token
     * identifying a valid client session
     * @exception IOException if an I/O error occurs
     */
    public InputStream getInputStream(String token, String uri,
        boolean forProcessing)
        throws ResourceNotFoundException, AuthorizationException, 
            AuthenticationException, IOException;

    /**
     * Creates a new empty (document) resource in the repository.
     *
     * @param token identifies the client's authenticated session
     * @param uri the resource identifier to be created
     * @return a <code>Resource</code> representing metadata about
     * the newly created resource
     * @exception IllegalOperationException if the resource identified
     * by the URI alredy exists in the repository
     * @exception AuthorizationException if an authenticated user
     * is not authorized to create the resource
     * @exception AuthenticationException if the resource demands
     * authorization and the client does not supply a token
     * identifying a valid client session
     * @exception ResourceLockedException if the parent resource is locked
     * @exception ReadOnlyException if the resource is read-only or
     * the repository is in read-only mode
     * @exception IOException if an I/O error occurs
     */
    public Resource createDocument(String token, String uri)
        throws IllegalOperationException, AuthorizationException, 
            AuthenticationException, ResourceLockedException, ReadOnlyException, 
            IOException;

    /**
     * Creates a new collection resource in the repository.
     *
     * @param token identifies the client's authenticated session
     * @param uri the resource identifier to be created
     * @return a <code>Resource</code> representing metadata about
     * the newly created collection
     * @exception IllegalOperationException if the resource identified
     * by the URI alredy exists in the repository, or if an invalid
     * URI is supplied
     * @exception AuthorizationException if an authenticated user
     * is not authorized to create the resource
     * @exception AuthenticationException if the resource demands
     * authorization and the client does not supply a token
     * identifying a valid client session
     * @exception ReadOnlyException if the resource is read-only or
     * the repository is in read-only mode
     * @exception ResourceLockedException if the parent resource is locked
     * @exception IOException if an I/O error occurs
     */
    public Resource createCollection(String token, String uri)
        throws AuthorizationException, AuthenticationException, 
            IllegalOperationException, ResourceLockedException, 
            ReadOnlyException, IOException;

    /**
     * Performs a copy operation on a resource.
     *
     * After the operation has completed successfully, the resource
     * identified by <code>destUri</code> will be a duplicate of the
     * original resource, including properties. If the resource to be
     * copied is a collection, the <code>depth</code> determines
     * whether all internal members should also be copied. The legal
     * values of <code>depth</code> for collections are: "0" and
     * "infinity". When copying resources, the value of
     * <code>depth</code> is ignored.
     *
     * <p>Access Control Lists (ACLs) are not preserved on the
     * destination resource unless the parameter
     * <code>preserveACL</code> is <code>true</code>.
     *
     * <p>The destination URI must be valid in the sense that it must not
     * potentially cause namespace inconsistency in the repository. For
     * example, when trying to copy the source URI <code>/a/b</code>
     * to <code>/c/d/e</code>, the requirement is that the URI
     * <code>/c/d</code> must be an existing collection.</p>
     *
     * @param token identifies the client's authenticated session
     * @param srcUri identifies the resource to copy from
     * @param destUri identifies the resource to copy to
     * @param depth determines if all or none of the internal member
     * resources of a collection should be copied (legal values are
     * <code>0</code> or <code>infinity</code>)
     * @param overwrite determines if the operation should overwrite
     * existing resources
     * @exception IllegalOperationException if the resource identified
     * by the destination URI can not be created due to namespace
     * inconsistency
     * @exception AuthorizationException if an authenticated user is
     * not authorized to either create the resource specified by
     * <code>destUri</code> or read the resource specified by
     * <code>srcUri</code>
     * @exception AuthenticationException if the resource demands
     * authorization and the client does not supply a token
     * identifying a valid client session
     * @exception FailedDependencyException if the copying of
     * <code>srcUri</code> failed due to a dependency of another
     * resource (e.g. the client is not authorized to read an internal
     * member of the <code>srcUri</code>)
     * @exception ResourceOverwriteException if <code>overwrite</code>
     * is set to <code>false</code> but the resource identified by
     * <code>destUri</code> already exists
     * @exception ResourceLockedException if the resource identified
     * by <code>destUri</code> is locked
     * @exception ReadOnlyException if the resource is read-only or
     * the repository is in read-only mode
     * @exception IOException if an I/O error occurs
     */
    public void copy(String token, String srcUri, String destUri, String depth,
        boolean overwrite, boolean preserveACL)
        throws IllegalOperationException, AuthorizationException, 
            AuthenticationException, FailedDependencyException, 
            ResourceOverwriteException, ResourceLockedException, 
            ResourceNotFoundException, ReadOnlyException, IOException;

    /**
     * Moves a resource from one URI to another.
     *
     * @param token identifies the client's authenticated session
     * @param srcUri identifies the resource to move from
     * @param destUri identifies the resource to move to
     * @param overwrite determines if the operation should overwrite
     * existing resources
     * @exception IllegalOperationException if the resource identified
     * by the destination URI can not be created due to namespace
     * inconsistency
     * @exception AuthorizationException if an authenticated user
     * is not authorized to create the resource
     * @exception AuthenticationException if the resource demands
     * authorization and the client does not supply a token
     * identifying a valid client session
     * @exception FailedDependencyException if the copying of
     * <code>srcUri</code> failed due to a dependency of another
     * resource (e.g. the client is not authorized to read an internal
     * member of the <code>srcUri</code>)
     * @exception ResourceOverwriteException if <code>overwrite</code>
     * is set to <code>false</code> but the resource identified by
     * <code>destUri</code> already exists
     * @exception ResourceLockedException if the resource identified
     * by <code>destUri</code> is locked
     * @exception ReadOnlyException if the resource is read-only or
     * the repository is in read-only mode
     * @exception IOException if an I/O error occurs
     */
    public void move(String token, String srcUri, String destUri,
        boolean overwrite)
        throws IllegalOperationException, AuthorizationException, 
            AuthenticationException, FailedDependencyException, 
            ResourceOverwriteException, ResourceLockedException, 
            ResourceNotFoundException, ReadOnlyException, IOException;

    /**
     * Permanently deletes a resource.
     *
     * @param token identifies the client's authenticated session
     * @param uri identifies the resource to delete
     * @exception IllegalOperationException if the resource identified
     * by the destination URI can not be deleted due to namespace
     * inconsistency
     * @exception AuthorizationException if an authenticated user
     * is not authorized to delete the resource
     * @exception AuthenticationException if the resource demands
     * authorization and the client does not supply a token
     * identifying a valid client session
     * @exception ResourceNotFoundException if the resource identified
     * by <code>destUri</code> does not exists
     * @exception ResourceLockedException if the resource identified
     * by <code>destUri</code> is locked
     * @exception FailedDependencyException if the deletion of
     * <code>uri</code> failed due to a dependency of another
     * resource (e.g. the client is not authorized to read an internal
     * member of the <code>uri</code>)
     * @exception ReadOnlyException if the resource is read-only or
     * the repository is in read-only mode
     * @exception IOException if an I/O error occurs
     */
    public void delete(String token, String uri)
        throws IllegalOperationException, AuthorizationException, 
            AuthenticationException, ResourceNotFoundException, 
            ResourceLockedException, FailedDependencyException, 
            ReadOnlyException, IOException;

    /**
     * Tests whether a resource identified by this URI exists.
     *
     * @param token identifies the client's authenticated session
     * @param uri identifies the resource to delete
     * @return a <code>true</code> if an only if the resource
     * identified by the uri exists, <code>false</code> otherwise
     * @exception AuthorizationException if an authenticated user
     * is denied access to the resource
     * @exception AuthenticationException if the resource demands
     * authorization and the client does not supply a token
     * identifying a valid client session
     * @exception IOException if an I/O error occurs
     */
    public boolean exists(String token, String uri)
        throws AuthorizationException, AuthenticationException, IOException;

    /**
     * Performs a lock operation on a resource.
     *
     * @param token identifies the client's authenticated session
     * @param uri identifies the resource to lock
     * @param lockType the type of lock requested. Currently, the
     * legal values are <code>read</code> and <code>write</code>
     * @param ownerInfo user supplied information about the person
     * requesting the lock, e.g. an email address, etc. Note that this
     * is not the actual <i>username</i> of the person, such
     * information is obtained using the token.
     * @param depth specifies whether all internal members of a
     * resource should be locked or not. Legal values are
     * <code>0</code> or <code>infinity</code>
     * @param requestedTimoutSeconds the timeout period wanted (in seconds)
     * @param lockToken - if <code>null</code>, the a new lock is
     * obtained, otherwise it is interpreted as a lock refresh request
     * (the resource must be locked by the same principal and the lock
     * token must match the existing one).
     * @return a string representing the lock token obtained
     * @exception ResourceNotFoundException if the resource identified
     * by <code>uri</code> does not exists
     * @exception AuthorizationException if an authenticated user
     * is denied access to the resource
     * @exception AuthenticationException if the resource demands
     * authorization and the client does not supply a token
     * identifying a valid client session
     * @exception FailedDependencyException if the locking of
     * <code>uri</code> failed due to a dependency on another resource
     * (e.g. read an internal member resource is already locked by
     * another client)
     * @exception ResourceLockedException if the resource identified
     * by <code>uri</code> is already locked
     * @exception IllegalOperationException if invalid parameters are supplied
     * @exception ReadOnlyException if the resource is read-only or
     * the repository is in read-only mode
     * @exception IOException if an I/O error occurs
     */
    public String lock(String token, String uri, String lockType,
            String ownerInfo, String depth, int requestedTimoutSeconds, String lockToken)
        throws ResourceNotFoundException, AuthorizationException, 
            AuthenticationException, FailedDependencyException, 
            ResourceLockedException, IllegalOperationException, 
            ReadOnlyException, IOException;

    /**
     * Performs an unlock operation on a resource.
     *
     * @param token identifies the client's authenticated session
     * @param uri identifies the resource to unlock
     * @exception ResourceNotFoundException if the resource identified
     * by <code>uri</code> does not exists
     * @exception AuthorizationException if an authenticated user
     * is denied access to the resource
     * @exception AuthenticationException if the resource demands
     * authorization and the client does not supply a token
     * identifying a valid client session
     * @exception ResourceLockedException if the resource identified
     * by <code>uri</code> is already locked by another client
     * @exception ReadOnlyException if the resource is read-only or
     * the repository is in read-only mode
     * @exception IOException if an I/O error occurs
     */
    public void unlock(String token, String uri, String lockToken)
        throws ResourceNotFoundException, AuthorizationException, 
            AuthenticationException, ResourceLockedException, ReadOnlyException, 
            IOException;

    /* Below: new methods corresponding to WebDAV ACL functionality */

    /**
     * Retrieves the Access Control List (ACL) for a resource.
     *
     * @param token identifies the client's authenticated session
     * @param uri identifies the resource for which to get the ACL.
     * @exception ResourceNotFoundException if the resource identified
     * by <code>uri</code> does not exists
     * @exception AuthorizationException if an authenticated user
     * is denied access to the resource
     * @exception AuthenticationException if the resource demands
     * authorization and the client does not supply a token
     * identifying a valid client session
     * @exception IOException if an I/O error occurs
     */
    public Ace[] getACL(String token, String uri)
        throws ResourceNotFoundException, AuthorizationException, 
            AuthenticationException, IOException;

    /**
     * Stores the Access Control List (ACL) for a resource.
     *
     * @param token identifies the client's authenticated session
     * @param uri identifies the resource for which to store the ACL.
     * @exception ResourceNotFoundException if the resource identified
     * by <code>uri</code> does not exists
     * @exception AuthorizationException if an authenticated user
     * is denied access to the resource
     * @exception AuthenticationException if the resource demands
     * authorization and the client does not supply a token
     * identifying a valid client session
     * @exception IllegalOperationException if the supplied ACL is
     * invalid
     * @exception ReadOnlyException if the resource is read-only or
     * the repository is in read-only mode
     * @exception IOException if an I/O error occurs
     */
    public void storeACL(String token, String uri, Ace[] acl)
        throws ResourceNotFoundException, AuthorizationException, 
            AuthenticationException, AclException, IllegalOperationException, 
            ReadOnlyException, IOException;

    
    /**
     * Get the repository ID.
     */
    public String getId();
    
}
