/* Copyright (c) 2007-2017, University of Oslo, Norway
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
package vtk.repository.resourcetype;

import java.util.Collection;
import vtk.repository.Vocabulary;

/**
 * Interface for a multi-rooted hierarchical vocabulary of some type.
 *
 * @param <T> data type of vocabulary
 */
public interface HierarchicalVocabulary<T> extends Vocabulary<T> {

    /**
     * Get collection of root values in hierarchy.
     * @return
     */
    Collection<T> roots();

    /**
     * Get a collection of immediate parent values of the provided value.
     *
     * <p>The provided value itself shall not be included in the collection.
     *
     * <p>The number of parents can be higher than one when some or all of the parents
     * are roots.
     *
     * @param entry
     * @return
     */
    Collection<T> parents(T entry);

    /**
     * Get a collection of immediate child values of the provided value.
     *
     * <p>The provided value itself shall not be included in the collection.
     *
     * @param entry
     * @return
     */
    Collection<T> children(T entry);

    /**
     * Get a collection of all ancestor values of the provided value.
     *
     * <p>The provided value itself shall not be included in the collection.
     *
     * @param entry
     * @return
     */
    Collection<T> flattenedAncestors(T entry);

    /**
     * Get a collection of all descendant values of the provided value.
     *
     * <p>The provided value itself shall not be included in the collection.
     * @param entry
     * @return
     */
    Collection<T> flattenedDescendants(T entry);

}
