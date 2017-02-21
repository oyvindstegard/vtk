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

import java.util.Objects;
import java.util.function.Supplier;


/**
 * Implementation of {@link ReusableObjectCache}. Uses an array-based
 * stack internally for minimum overhead in {@link #getInstance()} and 
 * {@link #putInstance(Object)}. It has a maximum capacity which can
 * optionally be set using constructor {@link #ReusableObjectArrayStackCache(int) }.
 *  
 * @param <T> type of objects stored in cache
 * @see vtk.util.cache.ReusableObjectCache
 */
public class ArrayStackCache<T> implements ReusableObjectCache<T> {

    public static final int DEFAULT_CAPACITY = 10;
    
    private int top = -1;
    private final T[] stack;
    private final Supplier<? extends T> factory;

    /**
     * Construct an instance with a default maximum capacity and no default
     * factory.
     *
     * <p>When constructed with this method, calls to {@link #getInstance() } may
     * return <code>null</code> if cache is empty.
     */
    public ArrayStackCache() {
        this(null, DEFAULT_CAPACITY);
    }

    /**
     * Construct a cache with a specified max capacity and no default factory.
     *
     * <p>When constructed with this method, calls to {@link #getInstance() } may
     * return <code>null</code> if cache is empty.
     *
     * @param capacity maxiumum number of objects the cache can hold for reuse
     */
    public ArrayStackCache(int capacity) {
        this(null, capacity);
    }

    /**
     * Construct a cache with default capacity and a provided default factory
     * for creating new instances.
     *
     * @param factory a factory for creating new instances. Must always return a new
     * instance and never <code>null</code>
     */
    public ArrayStackCache(Supplier<? extends T> factory) {
        this(factory, DEFAULT_CAPACITY);
    }

    /**
     * Construct a cache with a specified max capacity and a default factory.
     *
     * @param factory a factory for creating new instances. Must always return a new
     * instance and never <code>null</code>
     * @param capacity maxiumum number of objects the cache can hold for reuse, number &gt; 0
     */
    public ArrayStackCache(Supplier<? extends T> factory, int capacity) {
        this.factory = factory;
        this.stack =  (T[]) new Object[capacity > 0 ? capacity : DEFAULT_CAPACITY];
    }

    /**
     * Get an instance, possibly using configured default factory to create it.
     *
     * @return a new or cached instance, or <code>null</code> if cache is empty and no default factory has been provided
     */
    @Override
    public T getInstance() {
        if (factory == null) return null;
        return getInstance(factory);
    }

    /**
     * Get an instance, possibly using the provided factory to create it.
     *
     * <p>The provided factory will override any default factory configured
     * at construction time.
     *
     * @return a new or cached instance
     * @param factory a factory
     * @throws NullPointerException if provided factory is <code>null</code>
     * @see ReusableObjectCache#getInstance(java.util.function.Supplier)
     */
    @Override
    public final T getInstance(Supplier<? extends T> factory) {
        T instance = pop();
        if (instance == null) {
            instance = factory.get();
        }
        return instance;
    }
    
    /**
     * @param instance the instance to return to the cache
     * @see vtk.util.cache.ReusableObjectCache#putInstance(java.lang.Object)
     */
    @Override
    public final boolean putInstance(T instance) {
        Objects.requireNonNull(instance);
        return push(instance);
    }
    
    /**
     * @return number of objects currently in cache
     * @see vtk.util.cache.ReusableObjectCache#size()
     */
    @Override
    public final synchronized int size() {
        return this.top + 1;
    }
    
    private synchronized T pop() {
        if (this.top == -1) {
            return null; // Cache empty
        }
        // Return instance at the top
        T object = this.stack[this.top];
        this.stack[this.top--] = null;
        return object;
    }

    private synchronized boolean push(T instance) {
        if (this.top == this.stack.length - 1) {
            // Cache full
            return false;
        }

        // Add at the top
        this.stack[++this.top] = instance;
        return true;
    }
 
}
