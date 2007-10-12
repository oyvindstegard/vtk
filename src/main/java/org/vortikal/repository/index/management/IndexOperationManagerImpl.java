/* Copyright (c) 2007, University of Oslo, Norway
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
package org.vortikal.repository.index.management;

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.vortikal.repository.index.DirectReindexer;
import org.vortikal.repository.index.IndirectReindexer;
import org.vortikal.repository.index.PropertySetIndex;
import org.vortikal.repository.index.PropertySetIndexReindexer;
import org.vortikal.repository.index.consistency.ConsistencyCheck;
import org.vortikal.repository.store.IndexDao;

/**
 * 
 * @author oyviste
 *
 */
public class IndexOperationManagerImpl implements IndexOperationManager {

    public static final String CONSISTENCY_CHECK_THREAD_NAME = 
                                                          "consistencyChecker";
    public static final String REINDEXER_THREAD_NAME = "reindexer";
    
    private static final Log LOG = LogFactory.getLog(
                                            IndexOperationManagerImpl.class);
    
    private final PropertySetIndex index;
    private final PropertySetIndex secondaryIndex; // only used for re-indexing of primary index, and not required
    private final IndexDao indexDao;

    private ConsistencyCheck lastConsistencyCheck = null;
    private boolean isCheckingConsistency = false;
    private Exception lastConsistencyCheckException = null;
    private Date lastConsistencyCheckCompletionTime = null;
    
    private boolean isReindexing = false;
    private int resourcesReindexed = -1;
    private Exception lastReindexingException = null;
    private Date lastReindexingCompletionTime = null;
    
    public IndexOperationManagerImpl(PropertySetIndex index, 
                                           IndexDao indexDao) {
        this.index = index;
        this.indexDao = indexDao;
        this.secondaryIndex = null;
    }
    
    public IndexOperationManagerImpl(PropertySetIndex index,
                                           PropertySetIndex secondaryIndex,
                                           IndexDao indexDao) {
        this.index = index;
        this.secondaryIndex = secondaryIndex;
        this.indexDao = indexDao;
    }
    
    public synchronized void checkConsistency(boolean asynchronous)
            throws IllegalStateException {
        if (this.isCheckingConsistency) {
            throw new IllegalStateException("Consistency check is already running");
        } else if (this.isReindexing) {
            throw new IllegalStateException("Cannot do consistency check while re-indexing is running");
        } else if (isClosed()) {
            throw new IllegalStateException("Cannot do consistency check on closed index");
        }
        
        if (asynchronous) {
            initiateAsynchronousConsistencyCheck();
        } else {
            runConsistencyCheckInternal();
        }
    }
    
    public synchronized boolean lastConsistencyCheckCompletedNormally() {
        if (this.lastConsistencyCheck == null) {
            throw new IllegalStateException("No consistency check has been done yet");
        }
        
        return (this.lastConsistencyCheckException == null);
    }
    
    private void initiateAsynchronousConsistencyCheck() {
        Runnable worker = new Runnable() {
            public void run() {
                IndexOperationManagerImpl.this.runConsistencyCheckInternal();
            }
        };
        
        Thread workerThread = new Thread(worker);
        workerThread.setName(CONSISTENCY_CHECK_THREAD_NAME);
        workerThread.start();
    }
    
    private void runConsistencyCheckInternal() {
        this.isCheckingConsistency = true;
        this.lastConsistencyCheckException = null;
        
        LOG.info("Waiting for lock on index");
        this.index.lock();
        LOG.info("Lock acquired");
        
        try {
            this.lastConsistencyCheck = 
                ConsistencyCheck.run(
                    IndexOperationManagerImpl.this.index,
                    IndexOperationManagerImpl.this.indexDao);
        } catch (Exception e) {
            this.lastConsistencyCheckException = e;
        } finally {
            this.index.unlock();
            LOG.info("Lock released");
            this.isCheckingConsistency = false;
            this.lastConsistencyCheckCompletionTime = new Date();
        }
    }

    public ConsistencyCheck getLastConsistencyCheck() {
        return this.lastConsistencyCheck;
    }
    
    public Exception getLastConsistencyCheckException() {
        return this.lastConsistencyCheckException;
    }

    public Date getLastConsistencyCheckCompletionTime() {
        return this.lastConsistencyCheckCompletionTime;
    }

    public synchronized boolean isCheckingConsistency() {
        return this.isCheckingConsistency;
    }

    public synchronized boolean isReindexing() {
        return this.isReindexing;
    }

    public synchronized void reindex(boolean asynchronous) throws IllegalStateException {
        if (this.isReindexing) {
            throw new IllegalStateException("Reindexing is already running");
        } else if (isClosed()) {
            throw new IllegalStateException("Cannot start reindexing, index is closed");
        } else if (this.isCheckingConsistency) {
            throw new IllegalStateException("Cannot start reindexing, consistency check is running");
        }
        
        if (asynchronous) {
            initiateAsynchronousReindexing();
        } else {
            runReindexingInternal();
        }
    }
    
    private void initiateAsynchronousReindexing() {
        Runnable worker = new Runnable() {
            public void run() {
                IndexOperationManagerImpl.this.runReindexingInternal();
            }
        };
        
        Thread workerThread = new Thread(worker);
        workerThread.setName(REINDEXER_THREAD_NAME);
        workerThread.start();
    }
    
    private void runReindexingInternal() {
        
        this.isReindexing = true;
        this.lastReindexingException = null;
        
        PropertySetIndexReindexer reindexer;
        if (this.secondaryIndex != null) {
             reindexer = new IndirectReindexer(this.index,
                            this.secondaryIndex, this.indexDao);
        } else {
            reindexer = new DirectReindexer(this.index, this.indexDao);
        }

        try {
            this.resourcesReindexed = reindexer.run();
        } catch (Exception e) {
            this.lastReindexingException = e;
        } finally {
            this.isReindexing = false;
            this.lastReindexingCompletionTime = new Date();
        }
        
    }

    public Exception getLastReindexingException() {
        return this.lastReindexingException;
    }


    public Date getLastReindexingCompletionTime() {
        return this.lastReindexingCompletionTime;
    }

    public synchronized boolean lastReindexingCompletedNormally()
            throws IllegalStateException {
        
        if (this.lastReindexingException == null) { 
            if (this.resourcesReindexed == -1) {
                throw new IllegalStateException("No re-indexing has been run");
            }
            return true;
        } else {
            return false;
        }
    }

    public void reinitialize() {
        boolean acquired = false;
        try {
            LOG.info("Waiting for lock");
            acquired = this.index.lock();
            LOG.info("Lock acquired");
            LOG.info("Re-initializing ..");
            this.index.reinitialize();
        } finally {
            if (acquired) {
                this.index.unlock();
                LOG.info("Lock released");
            }
        }
    }

    public void optimize() {
        boolean acquired = false;
        try {
            LOG.info("Waiting for lock");
            acquired = this.index.lock();
            LOG.info("Lock acquired");
            LOG.info("Optimizing ..");
            this.index.optimize();
        } finally {
            if (acquired) {
                this.index.unlock();
                LOG.info("Lock released");
            }
        }
    }

    public boolean isClosed() {
        return this.index.isClosed();
    }

    public void close() {
        if (this.index.isClosed()) {
            throw new IllegalStateException("Index is already closed");
        }
        
        boolean acquired = false;
        try {
            LOG.info("Waiting for lock");
            acquired = this.index.lock();
            LOG.info("Lock acquired");
            LOG.info("Closing ..");
            this.index.close();
        } finally {
            if (acquired) {
                this.index.unlock();
                LOG.info("Lock released");
            }
        }
    }

    public boolean lock() {
        return this.index.lock();
    }

    public void unlock() {
        this.index.unlock();
    }

    public boolean isLocked() {
        // Short timeout, only for probing lock-status.
        long timeout = 100;
        boolean acquired = false;
        try {
            acquired = this.index.lock(timeout);
        } finally {
            if (acquired) index.unlock();
        }

        return !acquired;
    }
    
    public PropertySetIndex getManagedInstance() {
        return this.index;
    }

    public synchronized void clearLastConsistencyCheckResults() {
        if (this.isCheckingConsistency) {
            throw new IllegalStateException(
                    "Cannot clear results while consistency check is running");
        }
        this.lastConsistencyCheck = null;
        this.lastConsistencyCheckCompletionTime = null;
        this.lastConsistencyCheckException = null;
    }

    public synchronized boolean hasReindexingResults() {
        if (this.isReindexing) {
            return false;
        }
        
        return this.resourcesReindexed != -1;
    }
    
    public synchronized int getLastReindexingResourceCount() {
        if (this.isReindexing) {
            throw new IllegalStateException(
                    "Cannot get resource count while reindexing is running");
        }
        
        return this.resourcesReindexed;
    }
    
    public synchronized void clearLastReindexingResults() {
        if (this.isReindexing){
            throw new IllegalStateException(
                    "Cannot clear results while reindexing is running");
        }
        
        this.lastReindexingCompletionTime = null;
        this.lastReindexingException = null;
        this.resourcesReindexed = -1;
    }
}
