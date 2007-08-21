/* Copyright (c) 2006, 2007, University of Oslo, Norway
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
package org.vortikal.repositoryimpl.index.observation;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.PropertySet;
import org.vortikal.repositoryimpl.ChangeLogEntry;
import org.vortikal.repositoryimpl.ChangeLogEntry.Operation;
import org.vortikal.repositoryimpl.index.PropertySetIndex;
import org.vortikal.repositoryimpl.store.IndexDao;
import org.vortikal.repositoryimpl.store.PropertySetHandler;

/**
 * Incremental index updates from resource changes.
 * Hooking up to the old resource change event system, for now.
 *  
 * TODO: Should consider batch processing of a set of changes and indexing
 *       to a volatile (memory) index, then merge back each finished batch.
 * 
 * @author oyviste
 *
 */
public class PropertySetIndexUpdater implements BeanNameAware, 
                                        ResourceChangeObserver, InitializingBean {

    private static final Log LOG = LogFactory.getLog(PropertySetIndexUpdater.class);
    
    private PropertySetIndex index;
    private String beanName;
    private ResourceChangeNotifier notifier;
    private IndexDao indexDao;
    private boolean enabled;
    
    public void afterPropertiesSet() throws BeanInitializationException {
        // If a notifier is configured, we register ourselves.
        enable();
    }
    
    /**
     * @see org.vortikal.repositoryimpl.index.observation.ResourceChangeObserver#disable()
     */
    public synchronized void disable() {
        if (this.notifier != null) {
            if (this.notifier.unregisterObserver(this)) {
                LOG.info("Un-registered from resource change notifier.");
            }
        }
        this.enabled = false;
        LOG.info("Disabled.");
    }
    
    /**
     * @see org.vortikal.repositoryimpl.index.observation.ResourceChangeObserver#enable()
     */
    public synchronized void enable() {
        if (this.notifier != null) {
            if (this.notifier.registerObserver(this)) {
                LOG.info("Registered with resource change notifier.");
            }
        }
        this.enabled = true;
        LOG.info("Enabled.");
    }
    
    /**
     * @see org.vortikal.repositoryimpl.index.observation.ResourceChangeObserver#isEnabled()
     */
    public boolean isEnabled() {
        return this.enabled;
    }
    
    /**
     * 
     */
    public void setBeanName(String beanName) {
        this.beanName = beanName;
        
    }
    
    public void notifyResourceChanges(List<ChangeLogEntry> changes) {

        synchronized (this) {
            if (! this.enabled) {
                LOG.info("Ignoring resource changes, disabled.");
                return;
            }
        }
        
        try {
            // Take lock immediately, we'll be doing some writing.
            if (! this.index.lock()) {
                LOG.error("Unable to acquire lock on index, will not attempt to " +
                             "apply modifications, changes are lost !");
                return;
            }
            
            List<ChangeLogEntry> updates = new ArrayList<ChangeLogEntry>();
            List<ChangeLogEntry> deletes = new ArrayList<ChangeLogEntry>();

            // Sort out deletes and updates
            for (ChangeLogEntry change: changes) {
                if (change.getOperation() == Operation.DELETED) {
                    deletes.add(change);
                } else {
                    updates.add(change);
                }
            }

            // Apply changes to index
            // Regular deletes (might include collections)
            for (ChangeLogEntry deletion: deletes) {
                // Delete by resource ID, this info must be provided in the event.
                if (deletion.isCollection()) {
                    this.index.deletePropertySetTreeByUUID(String.valueOf(deletion.getResourceId()));
                } else {
                    this.index.deletePropertySetByUUID(String.valueOf(deletion.getResourceId()));
                }
            }
            
            
            // Updates/additions
            if (updates.size() > 0) {
                List<String> updateUris = new ArrayList<String>(updates.size());
                
                // Remove updated property sets from index in one batch, first, 
                // before re-adding them. This is very necessary to keep things
                // efficient.
                for (ChangeLogEntry update: updates) {
                    this.index.deletePropertySet(update.getUri());
                    updateUris.add(update.getUri());
                }
                
                // Now query index dao for a list of all property sets that 
                // need updating.
                PropertySetHandler handler = new PropertySetHandler() {

                    public void handlePropertySet(PropertySet propertySet) {
                        PropertySetIndexUpdater.this.index.addPropertySet(propertySet);
                    }
                    
                };
                
                this.indexDao.orderedPropertySetIterationForUris(updateUris, handler);
            }
            
            this.index.commit();
            
        } catch (Exception e) {
            LOG.error("Something went wrong while updating new index with changes", e);
        } finally {
            this.index.unlock();
        }
        
    }

    public String getObserverId() {
        return this.beanName;
    }

    public void setNotifier(ResourceChangeNotifier notifier) {
        this.notifier = notifier;
    }

    @Required
    public void setIndex(PropertySetIndex index) {
        this.index = index;
    }

    @Required
    public void setIndexDao(IndexDao indexDao) {
        this.indexDao = indexDao;
    }
}
