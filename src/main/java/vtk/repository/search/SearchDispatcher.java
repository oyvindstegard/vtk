/* Copyright (c) 2009-2017, University of Oslo, Norway
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

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import vtk.repository.ChangeLogEntry;
import vtk.repository.index.update.IncrementalUpdater;
import vtk.repository.store.ChangeLogDao;

/**
 * A <code>Searcher</code> that wraps another <code>Searcher</code> and 
 * that has the following responsibilities:
 *
 * <ul>
 *   <li>Ensures the maximum limit on number of concurrent searches is upheld.
 * When this limit is exceeded, search threads will be blocked until other searches finish first.
 *   <li>Supports handling of threads for searches which have
 * {@link Search#isWaitForPendingUpdates() } set. Those are potentially delayed
 * and synchronized with the incremental updater before search is actually executed.
 * </ul>
 */
public class SearchDispatcher implements Searcher, InitializingBean {

    private Searcher searcher;
    private int maxConcurrentQueries = 16;
    private Semaphore searchPermits;
    private IncrementalUpdater incrementalUpdater;
    private ChangeLogDao changeLog;
    private int loggerType;
    private int loggerId;

    private final Logger logger = LoggerFactory.getLogger(SearchDispatcher.class.getName());

    @Override
    public void afterPropertiesSet() {
        // Use fair queueing if contention
        this.searchPermits = new Semaphore(this.maxConcurrentQueries, true);
    }

    @Override
    public ResultSet execute(String token, Search search) throws QueryException {
        Instant recency = null;
        if (search.getWaitForPendingUpdates().isPresent()) {
            recency = waitForPendingUpdates(search.getWaitForPendingUpdates().get());
        }

        try {
            searchPermits.acquire();
        } catch (InterruptedException e) {
            throw new QueryException("Thread interrupted while waiting for search permit");
        }
        
        try {
            ResultSet rs = searcher.execute(token, search);
            if (recency != null && rs instanceof ResultSetImpl) {
                ((ResultSetImpl)rs).setRecency(recency);
            }
            return rs;
        } finally {
            searchPermits.release();
        }
    }

    @Override
    public void iterateMatching(String token, Search search, MatchCallback callback) throws QueryException {
        if (search.getWaitForPendingUpdates().isPresent()) {
            waitForPendingUpdates(search.getWaitForPendingUpdates().get());
        }

        try {
            searchPermits.acquire();
        } catch (InterruptedException e) {
            throw new QueryException("Thread interrupted while waiting for search permit");
        }
        
        try {
            searcher.iterateMatching(token, search, callback);
        } finally {
            searchPermits.release();
        }
    }

    /**
     * Wait for pending updates.
     *
     * @return an instant reflecting the estimated freshness/recency of the search
     * if executed just after this method returns.
     */
    private Instant waitForPendingUpdates(Search.WaitSpec waitSpec) throws QueryException {
        final Instant deadline = Instant.now().plus(waitSpec.timeout());

        Instant recencyEstimate = Instant.now();
        while (Instant.now().isBefore(deadline)) {
            // Check if changelog has anything unprocessed and older than provided timestamp
            List<ChangeLogEntry> oldest = changeLog.getChangeLogEntries(loggerType, loggerId, Date.from(waitSpec.timestamp()), 1);

            if (oldest.isEmpty()) {
                // No pending changes before (or equal to) provided timestamp
                return recencyEstimate;
            }

            recencyEstimate = oldest.get(0).getTimestamp().toInstant();
            if (logger.isDebugEnabled()) {
                logger.debug("Updated recency estimate to: " + recencyEstimate);
            }

            Duration waitForIncrementalUpdateTimeout = Duration.between(Instant.now(), deadline);
            // Extra deadline check
            if (waitForIncrementalUpdateTimeout.isNegative() || waitForIncrementalUpdateTimeout.isZero()) {
                return recencyEstimate;
            }
            try {
                boolean withinTimeout = incrementalUpdater.waitForNextBatch(waitForIncrementalUpdateTimeout);
                if (!withinTimeout) {
                    // We timed out before next batch of updates was complete
                    break;
                }
            } catch (InterruptedException e) {
                throw new QueryException("Search thread interrupted while waiting for incremental update");
            }
        }

        return recencyEstimate;
    }

    @Required
    public void setSearcher(Searcher searcher) {
        this.searcher = searcher;
    }

    /**
     * Set max number of concurrent index queries to allow.
     * Default is <code>16</code>.
     * @param maxConcurrentQueries
     */
    public void setMaxConcurrentQueries(int maxConcurrentQueries) {
        if (maxConcurrentQueries <= 0) throw new IllegalArgumentException("maxConcurrentQueries must be > 0");
        this.maxConcurrentQueries = maxConcurrentQueries;
    }

    @Required
    public void setIncrementalUpdater(IncrementalUpdater incrementalUpdater) {
        this.incrementalUpdater = incrementalUpdater;
    }

    @Required
    public void setChangeLogDao(ChangeLogDao changeLog) {
        this.changeLog = changeLog;
    }

    @Required
    public void setLoggerType(int loggerType) {
        this.loggerType = loggerType;
    }

    @Required
    public void setLoggerId(int loggerId) {
        this.loggerId = loggerId;
    }
    
}
