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
package vtk.repository.search;

import vtk.repository.PropertySet;


/**
 * Simple search interface
 *
 */
public interface Searcher {

    /**
     * Callback interface that should be implemented by client code for match iteration
     * API.
     */
    @FunctionalInterface
    public interface MatchCallback {
        /**
         * Called once for each matching <code>PropertySet</code>.
         * 
         * @param result an instance of {@link PropertySet}
         * 
         * @return Return <code>false</code> to stop matching iteration, <code>true</code> to continue.
         * @throws java.lang.Exception any thrown exception from client code will stop the iteration.
         */
        boolean matching(PropertySet result) throws Exception;

    }
    
    /**
     * Execute a regular search returning a fixed size <code>ResultSet</code>.
     * 
     * @param token
     * @param search
     * @return
     * @throws QueryException 
     */
    public ResultSet execute(String token, Search search) throws QueryException;
 
    /**
     * Execute an iteration of all property sets that match the criteria in the
     * provided <code>Search</code>.  There are no limits on the number of matching
     * PropertySet instances that can be included in the iteration.
     *
     * <p>For best performance, sorting requirements should be dropped when
     * using this method.
     *
     * <p>Since an iteration can potentially load a lot of documents, client code
     * should take care to set a proper field/property-selector in <code>Search</code> for
     * better efficiency.
     *
     * <p>
     * Note that if option {@link Search#setWaitForPendingUpdates(java.time.Instant, java.time.Duration) Search#setWaitForPendingUpdates
     * } is set it will be handled like regular searches, potentially delaying
     * the thread executing the search, but this callback API provides no way to
     * check the recency of the search results afterwords.
     *
     * @param token repository security token
     * @param search A <code>Search</code> instance, encapsulating all aspects
     *        of the index search. The search query itself may be null, in which case
     *        nothing will match.
     * 
     * @param callback a provided <code>MatchCallback</code> that will be
     *        used to process matching results.
     */
    public void iterateMatching(String token, Search search, MatchCallback callback)throws QueryException;
    
}
