/* Copyright (c) 2009, University of Oslo, Norway
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
package org.vortikal.repository.search;

import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

/**
 * A <code>Searcher</code> that wraps another <code>Searcher</code> and 
 * trottles the number of concurrent searches
 * to a maximum number. If overflow, incoming threads are blocked and queued
 * in fair order.
 *
 */
public class ThrottlingSearcherWrapper implements Searcher, InitializingBean {

    private Searcher searcher;
    private int maxConcurrentQueries = 8;
    private Semaphore searchPermits;
    
    @Override
    public void afterPropertiesSet() {
        // Use fair queueing if contention
        this.searchPermits = new Semaphore(this.maxConcurrentQueries, true);
    }

    @Override
    public ResultSet execute(String token, Search search) throws QueryException {
        try {
            this.searchPermits.acquire();
        } catch (InterruptedException e) {
            throw new QueryException("Thread interrupted while waiting for search permit");
        }
        
        try {
            return this.searcher.execute(token, search);
        } finally {
            this.searchPermits.release();
        }
    }

    @Required
    public void setSearcher(Searcher searcher) {
        this.searcher = searcher;
    }

    public void setMaxConcurrentQueries(int maxConcurrentQueries) {
        this.maxConcurrentQueries = maxConcurrentQueries;
    }

    @Override
    public void iterateMatching(String token, Search search, MatchCallback callback) throws QueryException {
        try {
            this.searchPermits.acquire();
        } catch (InterruptedException e) {
            throw new QueryException("Thread interrupted while waiting for search permit");
        }
        
        try {
            this.searcher.iterateMatching(token, search, callback);
        } finally {
            this.searchPermits.release();
        }
    }
}
