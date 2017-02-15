/* Copyright (c) 2006,2014 University of Oslo, Norway
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
package vtk.repository.index;

import java.util.Iterator;
import java.util.Set;
import vtk.repository.Acl;

import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.security.Principal;

/**
 * <p>Defines an interface for modifying and inspecting the contents of 
 * an index of <code>PropertySet</code> instances.
 * 
 * <p>Note that this interface cannot be used for searching the index.
 * 
 * <p>Each <code>PropertySet</code> is identified by its URI.
 * 
 * <p>
 * A node (<code>PropertySet</code>) can be a parent of other nodes 
 * (collection). This relationship, however, is only reflected in the URIs themselves.
 * The index does not need to strictly enforce that any given node
 * has an existing parent. In addition to this, the implmementation does not 
 * have to ensure that there isn't multiple property sets for a given URI. 
 * This is for the sake of efficiency.
 * </p>
 * 
 * <p>
 * The {@link #lock()}, {@link #unlock()} and {@link #commit()} methods
 * should provide the possibility of executing a set of
 * operations that cannot be mixed with other write operations from other threads
 * at the same time. Any modifying operation will not be visible by
 * other index users/readers before {@link commit()} has been called, as long
 * as the lock has been acquired properly first.
 * </p>
 *
 * @author oyviste
 */
public interface PropertySetIndex {
    
    /**
     * Add a <code>PropertySet</code> to index, in addition to a set of principals
     * which are allowed to read it. 
     * 
     * <em>Does not erasing any existing property sets at the same URI.</em>
     *  
     * @param propertySet
     * @param acl
     * @throws IndexException
     */
    public void addPropertySet(PropertySet propertySet, Acl acl) throws IndexException;
    
    
    /**
     * Updates a <code>PropertySet</code> in index. If no property set exists
     * at the URI, this method will give the same result as {@link #addPropertySet(PropertySet, Set)}.
     * 
     * This method will always erase any existing property sets at the same URI for
     * the property set to update.
     * @param propertySet
     * @param acl
     */
    public void updatePropertySet(PropertySet propertySet,
                                 Acl acl) throws IndexException;
    
    
    /**
     * Delete any <code>PropertySet</code> with the given URI. If there
     * are multiple property sets with the same URI, then <em>they should all be
     * deleted</em>.
     * 
     * @param uri
     * @throws IndexException
     */
    public void deletePropertySet(Path uri) throws IndexException;
    
    /**
     * Delete the <code>PropertySet</code> at the given root URI and all its
     * descendants. This method should <em>also delete URI duplicates in the tree</em>, 
     * if there are any.
     * 
     * @param rootUri

     * @throws IndexException
     */
    public void deletePropertySetTree(Path rootUri) throws IndexException;
    
    /**
     * Get a {@link PropertySetIndexRandomAccessor} instances for this index.
     *
     * Note that calling this method will implicitly commit all changes made earlier
     * using any of the methods for deleting, updating or adding property sets.
     *
     * @return
     * @throws IndexException
     */
    public PropertySetIndexRandomAccessor randomAccessor() throws IndexException;
    
    /**
     * Get an {@link java.util.Iterator} over all existing URI paths in index. 
     * 
     * The iteration is ordered by URI lexicographically. Any URI-duplicates are included. 
     * 
     * @return an iterator of paths 
     * @throws IndexException
     */
    public Iterator<Path> orderedUriIterator() throws IndexException;
    
    /**
     * Count all property set instances currently in index. This number includes any multiples
     * for a single URI.
     *  
     * @return
     * @throws IndexException
     */
    public int countAllInstances() throws IndexException;
    
    /**
     * Close a previously obtained <code>java.util.Iterator</code> instance to free index resources.
     * 
     * @param iterator
     * @throws IndexException
     */
    public void close(Iterator<?> iterator) throws IndexException;
    
    /**
     * Clear all contents of index (create a new and empty index).
     * 
     * @throws IndexException
     */
    public void clear() throws IndexException;
    
    /**
     * Close down an index to free associated resources. 
     * This method should implicitly commit any changes before closing down.
     * 
     * @throws IndexException
     */
    public void close() throws IndexException;
    
    /**
     * Determine if underlying index is closed for access or not. 
     * @return <code>true</code> if underlying index is closed.
     */
    public boolean isClosed();

    /**
     * High level test of index compatiblity to application code compatiblity.
     *
     * <p>This method can be used to test if a reindexing should be performed
     * to make the underlying index compatible.
     *
     * @return <code>true</code> if compatible, <code>false</code> otherwise.
     */
    public boolean isApplicationLevelCompatible() throws IndexException;
    
    /**
     * Re-initialize the index. Should be used to re-open a previously
     * closed instance.
     * 
     * Note that calling this method will implicitly commit all changes made earlier
     * using any of the methods for deleting, updating or adding property sets.
     * 
     * @throws IndexException
     */
    public void reinitialize() throws IndexException;
    
    /**
     * Merge the contents of another <code>PropertySetIndex</code> into this index.
     * It is implementation dependent whether this method removes duplicate
     * URIs resulting from the merge, or not.
     * 
     * @param index
     */
    public void addIndexContents(PropertySetIndex index) throws IndexException;
    
    /**
     * Set index mutex write lock.
     * 
     * @return <code>true</code> iff the lock was acquired, <code>false</code>
     *         otherwise.
     *         
     * @throws IndexException
     */
    public boolean lock();
    
    /**
     * Try to set index mutex write lock.
     * 
     * @param timeout The number of milliseconds to wait before failing.
     * 
     * @return <code>true</code> iff the lock was acquired, 
     *         <code>false</code< otherwise.
     *         
     * @throws IndexException
     */
    public boolean lock(long timeout);
    
    /**
     * Unset index mutex write lock.
     * @throws IndexException
     */
    public void unlock();
 
    /**
     * Commit any changes using the methods for updating, delete or adding property sets.
     * This makes the changes visible in searches.
     * 
     * @throws IndexException
     */
    public void commit() throws IndexException;
    
    /**
     * Optimize underlying storage facility. May do nothing, if not 
     * applicable to the implementation.
     * 
     * @throws IndexException
     */
    public void optimize() throws IndexException;
    
    /**
     * Request a low-level validation of the underlying physical storage facility (ie. a corruption test).
     * You should obtain the index lock before doing this.
     *  
     * Implementations are free to ignore this, if it is not relevant or necessary.
     * 
     * @throws StorageCorruptionException if corruption is detected in the storage facility.
     */
    public void validateStorageFacility() throws StorageCorruptionException;
    
    /**
     * Check whether the index is currently in read-only mode. This typically
     * indicates that node has cluster SLAVE role and is using shared index.
     * @return whether index is currently read-only mode.
     */
    public boolean isClusterSharedReadOnly();
    
    /**
     * Return a runtime ID for the index instance.
     * @return id as a string
     */
    public String getId();
    
}
