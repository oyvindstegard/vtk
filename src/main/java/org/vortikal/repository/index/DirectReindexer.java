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
package org.vortikal.repository.index;

import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.vortikal.repository.Acl;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.index.mapping.AclFields;
import org.vortikal.repository.store.IndexDao;
import org.vortikal.repository.store.PropertySetHandler;
import org.vortikal.security.Principal;

/**
 * A simple re-indexer that works directly on the provided <code>PropertySetIndex</code> instance.
 * Locking is handled internally.
 * 
 * @author oyviste
 *
 */
public class DirectReindexer implements PropertySetIndexReindexer {

    private PropertySetIndex targetIndex;
    private IndexDao indexDao;
    private final Log logger = LogFactory.getLog(DirectReindexer.class);
    
    public DirectReindexer(PropertySetIndex targetIndex, IndexDao indexDao) {
        this.targetIndex = targetIndex;
        this.indexDao = indexDao;
    }
    
    @Override
    public int run() throws IndexException {
        if (! this.targetIndex.lock()) {
            throw new IndexException("Unable to acquire exclusive write lock on target index '"
                    + this.targetIndex.getId() + "'");
        }
        
        if (logger.isInfoEnabled()) {
            logger.info("Exclusive write lock acquired on target index '"
                    + this.targetIndex.getId() + "', initiating direct re-indexing");
        }
        
        try {
            return runWithExternalLocking();
        } finally {
            this.targetIndex.unlock();
        }
    }
    
    /**
     * Run the re-indexing assuming that index is properly locked.
     * 
     * @return
     * @throws IndexException
     */
    protected int runWithExternalLocking() throws IndexException {
        
        try {

            logger.info("Clearing index contents ..");
            targetIndex.clear();

            logger.info("Starting re-indexing ..");
            AddAllPropertySetHandler handler = 
                new AddAllPropertySetHandler(this.targetIndex);
            
            this.indexDao.orderedPropertySetIteration(handler);
            
            targetIndex.commit();
            if (logger.isInfoEnabled()) {
                logger.info("Index '" + this.targetIndex.getId() + "' committed, "
                        + handler.getCount() + " property sets indexed successfully");
            }
            
            return handler.getCount();
        } catch (Exception e) {
            logger.warn("Exception while re-indexing", e);
            throw new IndexException(e);
        }
    }
    
    private class AddAllPropertySetHandler implements PropertySetHandler {
        
        private final PropertySetIndex index;
        private int count;
        
        public AddAllPropertySetHandler(PropertySetIndex index) {
            this.index = index;
        }
        
        @Override
        public void handlePropertySet(PropertySet propertySet, 
                                      Acl acl) {

            Set<Principal> aclReadPrincipals = AclFields.aggregatePrincipalsForRead(acl);
            
            this.index.addPropertySet(propertySet, aclReadPrincipals);

            if (++count % 10000 == 0) {
                DirectReindexer.this.logger.info("Reindexing progress: " + count + " resources indexed.");
            }
        }
        
        public int getCount(){
            return this.count;
        }
        
    }

}
