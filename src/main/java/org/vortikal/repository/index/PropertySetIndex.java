/* Copyright (c) 2006, University of Oslo, Norway
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
package org.vortikal.repository.index;

import java.util.Iterator;

import org.vortikal.repository.Path;
import org.vortikal.repository.PropertySet;

/**
 * <p>Defines an interface for modifying and inspecting the contents of 
 * an index of <code>PropertySet</code> instances.
 * 
 * <p>Note that this interface cannot be used for searching the index.
 * 
 * <p>
 * Each <code>PropertySet</code> is primarily identified by its URI. In addition, 
 * implementations may provide an auxilliary UUID, which should be uniqe for any given <code>
 * PropertySet</code> <em>and also independent of time</em> (UUIDs should thus never 
 * be re-used, even for new nodes at the same URI in the same index instance).
 * 
 * This might be used in the implementation for increased efficiency when
 * modifying or searching an index hierarchially and for consistency reasons. 
 * The benefits of using UUIDs is entirely implementation specific. This interface
 * does not state how a UUID should be generated for a <code>PropertySet</code>.
 * This is up to the implementation.
 * </p>
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
 * should provide the possibility of executing a set
 * operations that cannot be mixed with other write operations from other threads
 * at the same time. Any modifying operation is not guaranteed to be visible by
 * other index users before {@link commit()} has been called.
 * </p>
 *
 * @author oyviste
 */
public interface PropertySetIndex {
    
    /**
     * Add <code>PropertySet</code> to index.
     * @param propertySet
     * @throws IndexException
     */
    public void addPropertySet(PropertySet propertySet) throws IndexException;
    
    /**
     * Delete any <code>PropertySet</code> with the given URI. If there
     * are multiple property sets with the same URI, then <em>they should all be
     * deleted</em>.
     * 
     * @param uri
     * @return The number of instances deleted.
     * @throws IndexException
     */
    public int deletePropertySet(Path uri) throws IndexException;
    
    /**
     * Delete <code>PropertySet</code> with the given auxilliary UUID.
     * Optional.
     * 
     * @param uuid
     * @return The number of instances deleted.
     * @throws IndexException
     */
    public int deletePropertySetByUUID(String uuid) throws IndexException;
    
    /**
     * Delete the <code>PropertySet</code> at the given root URI and all its
     * descendants. This method should <em>also delete URI duplicates in the tree</em>, 
     * if there are any.
     * 
     * @param rootUri
     * @return The number of deleted instances. If it's not 0 or 1, 
     *         then something is very wrong with the implementation.
     *         
     * @throws IndexException
     */
    public int deletePropertySetTree(Path rootUri) throws IndexException;
    
    /**
     * Delete the <code>PropertySet</code> with the given auxilliary UUID and all its
     * descendants.
     * Optional.
     * 
     * @param rootUuid
     * @return
     * @throws IndexException
     */
    public int deletePropertySetTreeByUUID(String rootUuid) throws IndexException;
    
    /**
     * Get a {@link PropertySetIndexRandomAccessor} instances for this index.
     * @return
     * @throws IndexException
     */
    public PropertySetIndexRandomAccessor randomAccessor() throws IndexException;
    
    /**
     * Get an {@link java.util.Iterator} over all existing URIs in index. 
     * 
     * The iteration is ordered by URI lexicographically. Any URI-duplicates are included. 
     * 
     * @return
     * @throws IndexException
     */
    public Iterator<Object> orderedUriIterator() throws IndexException;
    
    /**
     * Get an un-ordered <code>Iterator</code> over all <code>PropertySet</code> instances
     * in index. Any URI-duplicates are included.
     * 
     * @return
     * @throws IndexException
     */
    public Iterator<Object> propertySetIterator() throws IndexException;
    
    /**
     * Get an {@link java.util.Iterator} over all <code>PropertySet</code> instances
     * in index.
     * 
     * The iteration is ordered by URI lexicographically. Any URI-duplicates are included, 
     * and should <em>directly</em> follow each other because of the sorting.
     * 
     * @return
     * @throws IndexException
     */
    public Iterator<Object> orderedPropertySetIterator() throws IndexException;
    
    /**
     * Get an {@link java.util.Iterator} over all <code>PropertySet</code> instances
     * in the sub-tree given by the root URI.
     * 
     * The iteration is ordered by URI lexicographically. Any duplicates are included
     * and should <em>directly</em> follow each other because of the sorting.
     * 
     * @param rootUri
     * @return
     * @throws IndexException
     */
    public Iterator<Object> orderedSubtreePropertySetIterator(Path rootUri) throws IndexException;

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
    public void close(Iterator<Object> iterator) throws IndexException;
    
    /**
     * Clear all contents of index.
     * 
     * @throws IndexException
     */
    public void clearContents() throws IndexException;
    
    /**
     * Close down an index to free associated resources. 
     * This method should implicitly commit any changes before closing down.
     * 
     * @throws IndexException
     */
    public void close() throws IndexException;
    
    /**
     * Determine if underlying index is closed for access or not. 
     */
    public boolean isClosed();
    
    /**
     * Re-initialize the index. Should be used to re-open a previously
     * closed instance.
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
     * Obtain index mutex write lock.
     * 
     * @return <code>true</code> iff the lock was acquired, <code>false</code>
     *         otherwise.
     *         
     * @throws IndexException
     */
    public boolean lock();
    
    /**
     * Try to obtain index mutex write lock.
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
     * Release index mutex write lock.
     * @throws IndexException
     */
    public void unlock();
 
    /**
     * Commit any changes
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
     * Return a runtime ID for the index instance.
     */
    public String getId();
    
}
