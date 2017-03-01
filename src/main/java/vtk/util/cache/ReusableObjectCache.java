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
package vtk.util.cache;

import java.util.function.Supplier;


/**
 * Defines an interface to a cache of of reusable objects.
 * Allows them to be put back for later re-use.
 * 
 * The motivation behind this type of cache is to efficiently re-use instances
 * of types that are not thread safe, but frequently needed in multithreaded code
 * and that have expensive constructors, such as {@link java.text.SimpleDateFormat}.
 * 
 * @author oyviste
 * @param <T> type of objects stored in the cache
 *
 */
public interface ReusableObjectCache<T> {

    /**
     * Get an object instance from the cache.
     *
     * <p>
     * If the cache is empty, <code>null</code> may be returned, depending
     * on implementation.
     *
     * <p>
     * Note that this method never populates the cache with created objects, as
     * that is the responisbility of client code, by calling
     * {@link #putInstance(java.lang.Object)  putInstance}.
     *
     * <p>
     * This method must not return the same instance twice (with the exception
     * of <code>null</code>), iff the instance has never been been put back
     * twice {@link #putInstance(Object)}. Therefore, it should be safe to use
     * from multithreaded code for object types that aren't themselves thread
     * safe.
     *
     * @return An instance of T, or <code>null</code> if cache is empty and unable to create a new instance
     */
    T getInstance();

    /**
     * Get an object instance from the cache.
     *
     * <p>The provided factory must never return <code>null</code> and should
     * never return the same instance twice.
     *
     * <p>If the cache is empty, the provided factory will be used to create a new instance.
     *
     * <p>
     * Note that this method never populates the cache with created objects, as
     * that is the responisbility of client code, by calling
     * {@link #putInstance(java.lang.Object)  putInstance}.
     *
     * <p>This method must not return the same instance twice, iff the
     * instance has never been been put back twice {@link #putInstance(Object)}.
     * Therefore, it should be safe to use from multithreaded code for object
     * types that aren't themselves thread safe.
     *
     * @param factory a factory to create new instances of type T
     * @return An instance of T
     */
    T getInstance(Supplier<? extends T> factory);

    /**
     * Put an instance into the cache. This allows it to be returned later by {@link #getInstance(java.util.function.Supplier)
     * }, possibly avoiding the need to create a new object. Caller is
     * responsible for not putting in the same instance twice. If the cache is
     * full, the instance will be discarded, and this method will return
     * <code>false</code>.
     *
     * @param obj The instance to put back into the cache, must never be <code>null</code>
     * @return <code>true</code> if the object was put into the cache, <code>false</code>
     *         if cache was already full (in which case the instances is typically discarded).
     */
    public boolean putInstance(T obj);
    
    /**
     * Get current number of instances in cache.
     * @return number of instances in cache
     */
    public int size();

}
