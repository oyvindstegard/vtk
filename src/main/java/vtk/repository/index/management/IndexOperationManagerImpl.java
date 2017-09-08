/* Copyright (c) 2007-2017 University of Oslo, Norway
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
package vtk.repository.index.management;

import java.io.File;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import vtk.repository.index.DirectReindexer;
import vtk.repository.index.IndexException;
import vtk.repository.index.IndirectReindexer;
import vtk.repository.index.PropertySetIndex;
import vtk.repository.index.PropertySetIndexReindexer;
import vtk.repository.index.consistency.ConsistencyCheck;
import vtk.repository.index.consistency.TooManyErrorsException;
import vtk.repository.store.IndexDao;

/**
 * High level stateful management of system index operations.
 */
public class IndexOperationManagerImpl implements IndexOperationManager, DisposableBean,
        InitializingBean, ApplicationListener<ContextRefreshedEvent>{

    private final Logger logger = LoggerFactory.getLogger(
                                            IndexOperationManagerImpl.class);

    @Override
    public void afterPropertiesSet() throws Exception {
        switch (autoReindex) {
            case AT_INIT:
                logger.info("Starting automatic synchronous reindexing of index " + index.getId() + " ..");
                reindex(false);
            default:
        }
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        switch (autoReindex) {
            case AFTER_INIT:
                logger.info("Starting automatic reindexing of index " + index.getId() + " ..");
                reindex(true);
                break;
            case AFTER_INIT_IF_INCOMPATIBLE:
                if (!index.isApplicationLevelCompatible()) {
                    logger.info("Starting automatic reindexing of index "
                            + index.getId() + " due to application level incompatibility ..");
                    reindex(true);
                    logger.info("So long, and thanks for all the great years.. - oyviste");
                }
                break;
            default:
        }
    }

    /**
     * Enum which specifices when to trigger automatic reindexing operation.
     */
    public enum AutoReindex {
        /**
         * Unconditionally reindex synchronously during bean init, before
         * application context has finished initialization.
         */
        AT_INIT,
        /**
         * Unconditionally reindex asynchronously after application context has finished initialization.
         */
        AFTER_INIT,
        /**
         * Reindex asynchronously after application context has finished initialization
         * <em>if index on disk is incompatible.</em>
         */
        AFTER_INIT_IF_INCOMPATIBLE,
        /**
         * Never perform any auto-reindexing at application init time.
         */
        NEVER
    }

    private PropertySetIndex index;
    private PropertySetIndex secondaryIndex; // only used for re-indexing of primary index, and not required
    private IndexDao indexDao;
    private File tempDir;
    private AutoReindex autoReindex = AutoReindex.AFTER_INIT_IF_INCOMPATIBLE;

    private ConsistencyCheck lastConsistencyCheck = null;
    private boolean isCheckingConsistency = false;
    private Exception lastConsistencyCheckException = null;
    private Date lastConsistencyCheckCompletionTime = null;
    
    private boolean isReindexing = false;
    private int resourcesReindexed = -1;
    private Exception lastReindexingException = null;
    private Date lastReindexingCompletionTime = null;

    private final ExecutorService executor = new ThreadPoolExecutor(0, 1, 1,
            TimeUnit.SECONDS, new SynchronousQueue<>(), r -> new Thread(r, "index-operation"));
    
    @Override
    public synchronized void checkConsistency(boolean asynchronous)
            throws IllegalStateException {
        if (isCheckingConsistency) {
            throw new IllegalStateException("Consistency check is already running");
        } else if (isReindexing) {
            throw new IllegalStateException("Cannot do consistency check while re-indexing is running");
        } else if (isClosed()) {
            throw new IllegalStateException("Cannot do consistency check on closed index");
        }
        
        if (asynchronous) {
            executor.submit(this::runConsistencyCheckInternal);
        } else {
            runConsistencyCheckInternal();
        }
    }
    
    @Override
    public synchronized boolean lastConsistencyCheckCompletedNormally() {
        if (lastConsistencyCheck == null) {
            throw new IllegalStateException("No consistency check has been done yet");
        }
        
        return (lastConsistencyCheckException == null);
    }
    
    private void runConsistencyCheckInternal() {
        isCheckingConsistency = true;
        lastConsistencyCheckException = null;
        
        logger.info("Waiting for lock on index");
        index.lock();
        logger.info("Lock acquired");
        
        try {
            lastConsistencyCheck = 
                ConsistencyCheck.run(
                    IndexOperationManagerImpl.this.index,
                    IndexOperationManagerImpl.this.indexDao,
                    IndexOperationManagerImpl.this.tempDir);
        } catch (TooManyErrorsException tme) {
            logger.info("Consistency check found too many errors");
            lastConsistencyCheck = tme.getPartialCheck();
            lastConsistencyCheckException = tme;
        } catch (IndexException e) {
            logger.info("Error running consistency check: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            lastConsistencyCheckException = e;
        } finally {
            index.unlock();
            logger.info("Lock released");
            isCheckingConsistency = false;
            lastConsistencyCheckCompletionTime = new Date();
        }
    }

    @Override
    public ConsistencyCheck getLastConsistencyCheck() {
        return lastConsistencyCheck;
    }
    
    @Override
    public Exception getLastConsistencyCheckException() {
        return lastConsistencyCheckException;
    }

    @Override
    public Date getLastConsistencyCheckCompletionTime() {
        return lastConsistencyCheckCompletionTime;
    }

    @Override
    public synchronized boolean isCheckingConsistency() {
        return isCheckingConsistency;
    }

    @Override
    public synchronized boolean isReindexing() {
        return isReindexing;
    }

    @Override
    public synchronized void reindex(boolean asynchronous) throws IllegalStateException {
        if (isReindexing) {
            throw new IllegalStateException("Reindexing is already running");
        } else if (isClosed()) {
            throw new IllegalStateException("Cannot start reindexing, index is closed");
        } else if (isCheckingConsistency) {
            throw new IllegalStateException("Cannot start reindexing, consistency check is running");
        }
        
        if (asynchronous) {
            executor.submit(this::runReindexingInternal);
        } else {
            runReindexingInternal();
        }
    }
    
    private void runReindexingInternal() {
        
        isReindexing = true;
        lastReindexingException = null;
        
        final PropertySetIndexReindexer reindexer;
        if (secondaryIndex != null) {
             reindexer = new IndirectReindexer(index, secondaryIndex, indexDao);
        } else {
            reindexer = new DirectReindexer(index, indexDao);
        }

        try {
            resourcesReindexed = reindexer.run();
        } catch (Exception e) {
            lastReindexingException = e;
        } finally {
            isReindexing = false;
            lastReindexingCompletionTime = new Date();
        }
        
    }

    @Override
    public Exception getLastReindexingException() {
        return lastReindexingException;
    }


    @Override
    public Date getLastReindexingCompletionTime() {
        return lastReindexingCompletionTime;
    }

    @Override
    public synchronized boolean lastReindexingCompletedNormally()
            throws IllegalStateException {
        
        if (lastReindexingException == null) { 
            if (resourcesReindexed == -1) {
                throw new IllegalStateException("No re-indexing has been run");
            }
            return true;
        }
        return false;
    }

    @Override
    public void reinitialize() {
        boolean acquired = false;
        try {
            logger.info("Waiting for lock");
            acquired = index.lock();
            logger.info("Lock acquired");
            logger.info("Re-initializing ..");
            index.reinitialize();
        } finally {
            if (acquired) {
                index.unlock();
                logger.info("Lock released");
            }
        }
    }

    @Override
    public void optimize() {
        boolean acquired = false;
        try {
            logger.info("Waiting for lock");
            acquired = index.lock();
            logger.info("Lock acquired");
            logger.info("Optimizing ..");
            index.optimize();
        } finally {
            if (acquired) {
                index.unlock();
                logger.info("Lock released");
            }
        }
    }

    @Override
    public boolean isClosed() {
        return index.isClosed();
    }

    @Override
    public void close() {
        if (index.isClosed()) {
            throw new IllegalStateException("Index is already closed");
        }
        
        boolean acquired = false;
        try {
            logger.info("Waiting for lock");
            acquired = index.lock();
            logger.info("Lock acquired");
            logger.info("Closing ..");
            index.close();
        } finally {
            if (acquired) {
                index.unlock();
                logger.info("Lock released");
            }
        }
    }

    @Override
    public boolean lock() {
        return index.lock();
    }

    @Override
    public void unlock() {
        index.unlock();
    }

    @Override
    public boolean isLocked() {
        // Short timeout, only for probing lock-status.
        long timeout = 100;
        boolean acquired = false;
        try {
            acquired = index.lock(timeout);
        } finally {
            if (acquired) index.unlock();
        }

        return !acquired;
    }
    
    @Override
    public PropertySetIndex getManagedInstance() {
        return index;
    }

    @Override
    public synchronized void clearLastConsistencyCheckResults() {
        if (isCheckingConsistency) {
            throw new IllegalStateException(
                    "Cannot clear results while consistency check is running");
        }
        lastConsistencyCheck = null;
        lastConsistencyCheckCompletionTime = null;
        lastConsistencyCheckException = null;
    }

    @Override
    public synchronized boolean hasReindexingResults() {
        if (isReindexing) {
            return false;
        }
        
        return resourcesReindexed != -1;
    }
    
    @Override
    public synchronized int getLastReindexingResourceCount() {
        if (isReindexing) {
            throw new IllegalStateException(
                    "Cannot get resource count while reindexing is running");
        }
        
        return resourcesReindexed;
    }
    
    @Override
    public synchronized void clearLastReindexingResults() {
        if (isReindexing){
            throw new IllegalStateException(
                    "Cannot clear results while reindexing is running");
        }
        
        lastReindexingCompletionTime = null;
        lastReindexingException = null;
        resourcesReindexed = -1;
    }

    @Override
    public void destroy() throws Exception {
        executor.shutdownNow();
    }

    @Required
    public void setIndex(PropertySetIndex index) {
        this.index = index;
    }

    public void setSecondaryIndex(PropertySetIndex secondaryIndex) {
        this.secondaryIndex = secondaryIndex;
    }

    @Required
    public void setIndexDao(IndexDao indexDao) {
        this.indexDao = indexDao;
    }

    @Required
    public void setTempDir(File tempDir) {
        this.tempDir = tempDir;
    }

    /**
     * Set condition for when to initiate automatic reindexing of main index
     * at startup.
     *
     * <p>Default is {@link AutoReindex#AFTER_INIT_IF_INCOMPATIBLE}.
     * @param autoReindex
     * @see AutoReindex
     */
    public void setAutoReindex(AutoReindex autoReindex) {
        this.autoReindex = autoReindex;
    }
    
}
