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
package vtk.repository.search;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import vtk.repository.Acl;

import vtk.repository.PropertySet;
import vtk.repository.Resource;

/**
 * Contains the result set of a repository query.
 * 
 * TODO consider converting interface to provide Resource instances
 * instead of PropertySet instances. Issues to consider are if sparse
 * resource representations should be allowed when not all index fields are
 * loaded at query time (e.g. a Resource instance will not have all properties
 * available in it), and some other aspects of resource objects not available
 * in the search index.
 * 
 * Alternatively, consider introducing a new class for a single search result,
 * which then encapsulates all aspects. Then consolidate with iteration-API, so the
 * same result class is used in both places. (Iteration-API uses the Searcher.MatchingResult interface
 * for similar purpose.)
 */
public interface ResultSet extends Iterable<PropertySet> {

    /**
     * Get the result at a given index position in the
     * result set.  
     * @param index The position of the desired result.
     *              First result is at position zero (0), 
     *              last result is at position n-1, where n is
     *              the total number of results in the result set.
     *        
     * @return The result object at the given position.
     */
    public PropertySet getResult(int index);
    
    /**
     * Returns <code>true</code> if a result is available for the given index,
     *         <code>false</code> otherwise. 
     *
     *         This can be used to determine if there are more results 
     *         in paging logic (i.e. test if "endIdx" + 1 exists without having
     *         to call {@link #getAllResults()}).
     * 
     * @param index
     * @return <code>true</code> if a result is available for the given index
     */
    public boolean hasResult(int index);
    
    /**
     * Get all the results up to, but not including, the result
     * at position <code>maxIndex</code>. 
     * Example:
     * List tenFirstResults = resultSet.getResults(10);
     * List allResults = resultSet.getResults
     * 
     * @param maxIndex 
     * @return A <code>List</code> of the results 
     */
    public List<PropertySet> getResults(int maxIndex);
    
    /**
     * Get a subset of the result-set (fromIndex inclusive, toIndex exclusive).
     * @param fromIndex
     * @param toIndex
     * @return a list with a subset of the results in this result set.
     * @throws IndexOutOfBoundsException
     */
    public List<PropertySet> getResults(int fromIndex, int toIndex)
        throws IndexOutOfBoundsException;
    
    /**
     * Get all the results in the result set, as a 
     * <code>List</code>
     * 
     * @return <code>List</code> with all the results in the
     *         result set.
     */
    public List<PropertySet> getAllResults();
    
    /**
     * Get the size of the result set (number of query hits).
     * 
     * @return Size of the result set.
     */
    public int getSize();
 
    /**
     * Iterate over results.
     * @see Iterable#iterator() 
     */
    @Override
    public Iterator<PropertySet> iterator();
    
    /**
     * Get total number of hits the actual query produced, regardless of the
     * result set size or cursor+maxresults.
     * @return number of search hits in total
     */
    public int getTotalHits();
    
    /**
     * A timestamp approximating the point in time at which the search results
     * reflect the repository resource state.
     *
     * <p>May not be available, depending on search backend which produced
     * the search result set and options set in search. Therefore it is marked as optional.
     * @return an approximated point in time for the recency of the search results
     */
    public Optional<Instant> recency();
    
}
