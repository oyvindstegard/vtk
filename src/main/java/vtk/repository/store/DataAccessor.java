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
package vtk.repository.store;

import java.util.Date;
import java.util.List;
import java.util.Set;

import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.RecoverableResource;
import vtk.repository.ResourceImpl;
import vtk.security.Principal;

/**
 * Simple data access abstraction API.
 */
public interface DataAccessor {

    /**
     * Loads a single resource by uri
     * @param uri
     */
    public ResourceImpl load(Path uri) throws DataAccessException;

    /**
     * Loads the children of a given resource
     * @param parent
     */
    public ResourceImpl[] loadChildren(ResourceImpl parent) throws DataAccessException;

    /**
     * Stores a single resource.
     * 
     * @return The new ResourceImpl
     */
    public ResourceImpl store(ResourceImpl r) throws DataAccessException;

    /**
     * Stores the ACL of a resource.
     * 
     * @return The new ResourceImpl
     */
    public ResourceImpl storeACL(ResourceImpl r) throws DataAccessException;

    /**
     * Stores/deletes a lock on a resource.
     * 
     * @return The new ResourceImpl
     */
    public ResourceImpl storeLock(ResourceImpl r) throws DataAccessException;

    
    /**
     * Deletes a single resource (and any children)
     * 
     * @param resource
     *            The resource (with children) to delete
     * @throws DataAccessException
     */
    public void delete(ResourceImpl resource) throws DataAccessException;

    /**
     * Marks a resource (and any children) as deleted (moves to trash can)
     * 
     * @param resource
     *            The resource (with children) to mark as deleted
     * @param parent
     *            The parent of resource to mark for deletion
     * @param principal
     *            The user who requested the operation
     * @param trashID
     *            ID used to mark the resource as deleted
     * @throws DataAccessException
     */
    public void markDeleted(ResourceImpl resource, ResourceImpl parent, Principal principal, final String trashID)
            throws DataAccessException;

    /**
     * 
     * @param parentResourceId
     *            Unique id of resource (collection) to display recoverable
     *            resources from
     * @return A list of deleted resources within a collection
     * @throws DataAccessException
     */
    public List<RecoverableResource> getRecoverableResources(final int parentResourceId) throws DataAccessException;

    /**
     * 
     * @param parentUri
     *            Parent of item to recover. Needed for path reconstruction.
     * @param recoverableResource
     *            The resource to recover
     * 
     * @return The newly recovered ResourceImpl
     * 
     * @throws DataAccessException
     */
    public ResourceImpl recover(Path parentUri, RecoverableResource recoverableResource) throws DataAccessException;

    /**
     * Permanently deletes a recoverable resource.
     * 
     * @param recoverableResources
     *            The list of recoverable resources to delete
     * @throws DataAccessException
     */
    public void deleteRecoverable(RecoverableResource recoverableResources) throws DataAccessException;

    /**
     * Retrieves resources in the trash can that are overdue for permanent
     * removal (called periodically)
     * 
     * @param overdueLimit
     *            number of days that have to pass from deletion time before a
     *            recoverable resource is to be permanently deleted
     * @return a list or recoverable resources that are overdue for deletion
     * @throws DataAccessException
     */
    public List<RecoverableResource> getTrashCanOverdue(int overdueLimit) throws DataAccessException;

    /**
     * 
     * 
     * @return a list of deleted resource that are orphans, i.e. have their
     *         parents permanently deleted
     * @throws DataAccessException
     */
    public List<RecoverableResource> getTrashCanOrphans() throws DataAccessException;

    /**
     * Deletes all expired locks (should be called periodically)
     */
    public void deleteExpiredLocks(Date expireDate) throws DataAccessException;

    /**
     * Atomically copies a resource to a new destination.
     * 
     * @param resource
     *            the resource to copy from
     * @param destParent
     *            the resource to copy into (becomes the parent of the copied
     *            resource after the copy operation)
     * @param newResource
     *            the newly created resource: this resource is passed as an
     *            argument, as its properties may have changed as a result of
     *            the name change operation.
     * @param copyACLs
     *            whether to copy ACLs from the existing resource, or to inherit
     *            from the new parent resource
     * @param fixedProperties
     *            a set of properties to set on the new resource(s) instead of
     *            copying from the existing
     * @param uncopyableProperties set of property names (in default namespace)
     * which shall not be included in copied resource. Applies recursively to
     * all resources if copying a collection.
      * 
     * @return the new destination ResourceImpl
     */
    public ResourceImpl copy(ResourceImpl resource, ResourceImpl destParent, PropertySet newResource, boolean copyACLs,
            PropertySet fixedProperties, Set<String> uncopyableProperties) throws DataAccessException;

    /**
     * Atomically moves a resource to a new destination.
     * 
     * @param resource
     *            the resource to move
     * @param newResource
     *            the newly created resource: this resource is passed as an
     *            argument, as its properties may have changed as a result of
     *            the name change operation.
     * 
     * @return The destination ResourceImpl
     */
    public ResourceImpl move(ResourceImpl resource, ResourceImpl newResource) throws DataAccessException;

    /**
     * Finds any locks on a resource, or on resources in the URI hierarchy
     * defined by that resource.
     */
    public Path[] discoverLocks(Path uri) throws DataAccessException;

    /**
     * Finds any ACLs on a resource, or on resources in the URI hierarchy
     * defined by that resource.
     */
    public Path[] discoverACLs(Path uri) throws DataAccessException;

    /**
     * Discover all distinct groups currently present in the database.
     * 
     * @return A <code>Set</code> of <code>Principal</code> objects representing
     *         the groups.
     */
    public Set<Principal> discoverGroups() throws DataAccessException;

}
