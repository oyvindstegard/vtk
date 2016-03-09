/* Copyright (c) 2006, 2007, 2016, University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.transaction.annotation.Transactional;

import vtk.cluster.ClusterAware;
import vtk.cluster.ClusterRole;
import vtk.repository.Acl;
import vtk.repository.ChangeLogEntry;
import vtk.repository.ChangeLogEntry.Operation;
import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.index.PropertySetIndex;
import vtk.repository.store.ChangeLogDAO;
import vtk.repository.store.IndexDao;
import vtk.repository.store.PropertySetHandler;

/**
 * Incremental index updates from database resource change log.
 */
public class IncrementalUpdater implements ClusterAware {

    private final Log logger = LogFactory.getLog(IncrementalUpdater.class);

    private PropertySetIndex index;
    private IndexDao indexDao;
    private boolean enabled = true;

    private ChangeLogDAO changeLogDAO;
    private int loggerType;
    private int loggerId;
    private int maxChangesPerUpdate = 40000;

    private Optional<ClusterRole> clusterRole = Optional.empty();

    /**
     * This method should be called periodically to poll database for resource
     * change events and apply the updates to the index.
     *
     * @throws java.lang.Exception for any kind of failure that occurs during update.
     * In general, the database transaction should always be rolled back if updating
     * index fails for some reason. Index updates are also idempotent, which
     * works well with rollback.
     */
    @Transactional(readOnly=false)
    public synchronized void update() throws Exception {

        if (clusterRole.isPresent() && clusterRole.get() == ClusterRole.SLAVE) {
            logger.debug("update(): slave mode, returning");
            return;
        }
        try {
            List<ChangeLogEntry> changes =
            	this.changeLogDAO.getChangeLogEntries(this.loggerType,
                                                      this.loggerId,
                                                      this.maxChangesPerUpdate);

            if (logger.isDebugEnabled() && changes.size() > 0) {
                logger.debug("");
                logger.debug("--- update(): Start of window");
                logger.debug("--- update(): Got the following changelog events from DAO");
                for (ChangeLogEntry change: changes) {
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
                if (isEnabled()) {
                    // Index changes
                    logger.debug("--- update(): applying changes to index");
                    applyChanges(changes);
                    logger.debug("--- update(): finished applying changes to index.");
                } else {
                    logger.warn("Index updates disabled, discarding resource change events.");
                }

                // Remove changelog entries from DAO
                this.changeLogDAO.removeChangeLogEntries(changes);

                if (logger.isDebugEnabled()) {
                    logger.debug("--- update(): End of window");
                    logger.debug("");
                    logger.debug("");
                }
            }
        } catch (Throwable t) {
            logger.error("Unexpected exception during index update", t);
            throw t; // Rethrow to roll back transaction
        }
    }

    private void applyChanges(final List<ChangeLogEntry> changes) throws Exception {

        try {
            // Take lock immediately, we'll be doing some writing.
            if (! this.index.lock()) {
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
                        this.index.deletePropertySetTree(change.getUri());
                    } else {
                        this.index.deletePropertySet(change.getUri());
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
                        logger.debug(uri);

                        this.index.deletePropertySet(uri);
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
                            // Log some progress to update
                            logger.info("Incremental index update progress: "  + count + " resources indexed of "
                                    + updateUris.size() + " total in current update batch.");
                        }
                    }

                }

                CountingPropertySetHandler handler = new CountingPropertySetHandler();

                this.indexDao.orderedPropertySetIterationForUris(updateUris, handler);

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
            this.index.commit();

        } finally {
            this.index.unlock();
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
    public void setChangeLogDAO(ChangeLogDAO changeLogDAO) {
        this.changeLogDAO = changeLogDAO;
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

    @Override
    public void roleChange(ClusterRole role) {
        this.clusterRole = Optional.of(role);
    }
}
