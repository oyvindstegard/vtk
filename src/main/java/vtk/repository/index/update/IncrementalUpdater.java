/* Copyright (c) 2006-2017, University of Oslo, Norway
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
package vtk.repository.index.update;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import vtk.repository.Acl;
import vtk.repository.ChangeLogEntry;
import vtk.repository.ChangeLogEntry.Operation;
import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.index.IndexException;
import vtk.repository.index.PropertySetIndex;
import vtk.repository.store.IndexDao;
import vtk.repository.store.PropertySetHandler;
import vtk.repository.store.ChangeLogDao;
import vtk.repository.store.DataAccessException;

/**
 * Executes incremental repository index updates periodically.
 */
public class IncrementalUpdater implements DisposableBean, ApplicationListener<ContextRefreshedEvent> {

    private final Logger logger = LoggerFactory.getLogger(IncrementalUpdater.class);

    private PropertySetIndex index;
    private IndexDao indexDao;
    private ChangeLogDao changeLog;
    private int loggerType;
    private int loggerId;

    private int maxChangesPerUpdate = 40000;
    private int updateIntervalSeconds = 5;

    private TransactionTemplate transactionTemplate;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "incremental-index-updater"));
    private ScheduledFuture<?> task = null;

    // Syncro between threads waiting for current update batch to complete and incremental update thread
    private final Lock batchProcessingLock = new ReentrantLock();
    private final Condition processingBatchFinished = batchProcessingLock.newCondition();

    public synchronized void start() {
        if (task != null) {
            logger.info("Restarting");
            task.cancel(false);
        } else {
            logger.info("Starting");
        }

        task = executor.scheduleAtFixedRate(() -> {
            try {
                transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                    @Override
                    protected void doInTransactionWithoutResult(TransactionStatus ts) {
                        executeUpdateBatch(); // Rollback occurs automatically on any exceptions thrown
                    }
                });
            } catch (Throwable t) { // Must never let exceptions propagate to keep scheduled task active
                logger.error("Unexpected error during index update", t);
            } finally {
                // Signal any waiting searcher threads to continue
                batchProcessingLock.lock();
                try {
                    processingBatchFinished.signalAll();
                } finally {
                    batchProcessingLock.unlock();
                }
            }
        }, 1, updateIntervalSeconds, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (task == null) {
            logger.info("Already stopped");
            return;
        }

        task.cancel(false);
        task = null;
        logger.info("Stopped");
    }

    /**
     * Blocks the calling thread until the next batch of incremental index updates
     * has been completed.
     *
     * @param timeout how long to wait for the next batch to complete before timing out
     * @return <code>true</code> if this method returned <em>before timeout was reached</em>, <code>false</code> otherwise
     * @throws InterruptedException if thread is interrupted during wait
     */
    public boolean waitForNextBatch(Duration timeout) throws InterruptedException {
        final Instant deadline = Instant.now().plus(timeout);

        // This lock will never have much contention as each thread will immeditaly
        // wait for the condition and release lock. After wake up, the lock is immediately
        // released again before return.
        batchProcessingLock.lock();
        try {
            return processingBatchFinished.awaitUntil(Date.from(deadline));
        } finally {
            batchProcessingLock.unlock();
        }
    }

    /**
     * Executes a single incremental update round with batch size limited by {@link #maxChangesPerUpdate}.
     */
    private synchronized void executeUpdateBatch() throws DataAccessException, IndexException {

        if (index.isClusterSharedReadOnly()) {
            // We are probably not cluster MASTER, so do nothing.
            logger.debug("update(): index is not available for writing on this node, aborting update round");
            return;
        }
        
        List<ChangeLogEntry> changes
                = changeLog.getChangeLogEntries(loggerType, loggerId, maxChangesPerUpdate);

        if (logger.isDebugEnabled() && changes.size() > 0) {
            logger.debug("");
            logger.debug("--- update(): Start of window");
            logger.debug("--- update(): Got the following changelog events from DAO");
            for (ChangeLogEntry change : changes) {
                StringBuilder log = new StringBuilder();
                if (change.getOperation() == Operation.DELETED) {
                    log.append("DEL    ");
                } else {
                    log.append("UPDATE ");
                }

                if (change.isCollection()) {
                    log.append("COL ");
                }
                log.append(change.getUri());

                log.append(", RESOURCE ID=").append(change.getResourceId());
                log.append(", EVENT ID=").append(change.getChangeLogEntryId());
                logger.debug(log.toString());
            }
            logger.debug("--- update(): End of list, going to dispatch to observers");
            logger.debug("");
        }

        if (changes.size() > 0) {
            logger.debug("--- update(): applying changes to index");
            applyChanges(changes);
            logger.debug("--- update(): finished applying changes to index.");

            // Remove changelog entries from DAO
            changeLog.removeChangeLogEntries(changes);

            if (logger.isDebugEnabled()) {
                logger.debug("--- update(): End of window");
                logger.debug("");
                logger.debug("");
            }
        }

    }

    private void applyChanges(final List<ChangeLogEntry> changes) throws IndexException, DataAccessException {

        try {
            // Take lock immediately, we'll be doing some writing.
            if (! index.lock()) {
                logger.error("Unable to acquire lock on index, will not attempt to " +
                             "apply modifications, changes are lost !");
                return;
            }

            logger.debug("--- applyChanges(): Going to process change log window ---");

            // Map maintaining last change *per URI*
            Map<Path, ChangeLogEntry> lastChanges = new HashMap<>();

            for (ChangeLogEntry change: changes) {
                // If delete, we do it immediately
                if (change.getOperation() == Operation.DELETED) {
                    if (change.isCollection()) {
                        index.deletePropertySetTree(change.getUri());
                    } else {
                        index.deletePropertySet(change.getUri());
                    }
                }

                // Update map of last changes per URI
                lastChanges.put(change.getUri(), change);
            }

            // Updates/additions
            if (lastChanges.size() > 0) {
                final List<Path> updateUris = new ArrayList<>(lastChanges.size());

                // Remove updated property sets from index in one batch, first,
                // before re-adding them. This is very necessary to keep things
                // efficient.
                logger.debug("--- applyChanges(): Update list:");
                for (Map.Entry<Path, ChangeLogEntry> entry: lastChanges.entrySet()) {
                    // If not last operation on resource was delete, we add to updates
                    if (! (entry.getValue().getOperation() == Operation.DELETED)) {
                        Path uri = entry.getKey();
                        logger.debug(uri.toString());

                        index.deletePropertySet(uri);
                        updateUris.add(uri);
                    }
                }

                logger.debug("--- applyChanges(): End of update list, going to fetch from DAO and add to index:");

                // Immediately make lastChanges available for GC, since the next operation
                // can take a while, and lastChanges can be huge (tens of thousands of entries).
                lastChanges = null;

                // Now query index dao for a list of all property sets that
                // need updating.
                class CountingPropertySetHandler implements PropertySetHandler {
                    int count = 0;
                    @Override
                    public void handlePropertySet(PropertySet propertySet, Acl acl) {

                        if (logger.isDebugEnabled()) {
                            logger.debug("ADD " + propertySet.getURI());
                        }

                        // Add updated resource to index
                        index.addPropertySet(propertySet, acl);

                        if (++count % 2000 == 0) {
                            // Logger some progress to update
                            logger.info("Incremental index update progress: "  + count + " resources indexed of "
                                    + updateUris.size() + " total in current update batch.");
                        }
                    }

                }

                CountingPropertySetHandler handler = new CountingPropertySetHandler();

                indexDao.orderedPropertySetIterationForUris(updateUris, handler);

                if (logger.isInfoEnabled()) {
                    if (updateUris.size() >= 10000) {
                        logger.info("Incremental index update for current batch finished"
                               + ", final resource update count was " + handler.count);
                    }
                }

                // Note that it is OK to get less resources than requested from DAO, because
                // they can be deleted in the mean time.
                if (logger.isDebugEnabled() && updateUris.size() > 0) {
                    logger.debug("--- applyChanges(): Requested " + updateUris.size()
                            + " resources for updating, got " + handler.count + " from DAO.");
                }
            }

            logger.debug("--- applyChanges(): Committing changes to index.");
            index.commit();

        } finally {
            index.unlock();
        }
    }

    @Required
    public void setIndex(PropertySetIndex index) {
        this.index = index;
    }

    @Required
    public void setIndexDao(IndexDao indexDao) {
        this.indexDao = indexDao;
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

    public void setMaxChangesPerUpdate(int maxChanges) {
        if (maxChanges <= 0) {
            throw new IllegalArgumentException("Number must be greater than zero");
        }
        this.maxChangesPerUpdate = maxChanges;
    }

    public void setUpdateIntervalSeconds(int interval) {
        this.updateIntervalSeconds = interval;
    }

    @Override
    public void destroy() throws Exception {
        executor.shutdownNow();
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        start();
    }

    @Required
    public void setTransactionManager(PlatformTransactionManager txManager) {
        this.transactionTemplate = new TransactionTemplate(txManager);
        this.transactionTemplate.setReadOnly(false);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }
    
}
