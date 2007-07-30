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
package org.vortikal.repositoryimpl.index;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.vortikal.repositoryimpl.store.IndexDataAccessor;

/**
 * <p>A <code>PropertySetIndex</code> re-indexer that uses a temporary index instance, instead of working
 * directly on the target index. This has the advantage that the target index can be searched 
 * while re-indexing is running. When the temporary index is complete, its contents will
 * replace the target index' contents in one go. This is usually not a lengthy process, so
 * target index "downtime" should be minimal at this stage.
 * 
 * <p>The target index will be exclusively locked for writing immediately, while the re-indexing runs
 * on the temporary index. This is done to avoid losing any updates that might happen
 * during the operation. This, however, does not prevent doing queries on the target index.
 * 
 * <p>The provided temporary index will be explicitly closed after usage.
 * 
 * @author oyviste
 *
 */
public class IndirectReindexer implements PropertySetIndexReindexer {

    private IndexDataAccessor indexDataAccessor;
    private PropertySetIndex targetIndex;
    private PropertySetIndex temporaryIndex;
    private static final Log LOG = LogFactory.getLog(IndirectReindexer.class);
    
    public IndirectReindexer(PropertySetIndex targetIndex, 
                             PropertySetIndex temporaryIndex,
                             IndexDataAccessor indexDataAccessor) {
        
        this.targetIndex = targetIndex;
        this.temporaryIndex = temporaryIndex;
        this.indexDataAccessor = indexDataAccessor;
    }
    
    public int run() throws IndexException {

        // Lock target index immediately to prevent concurrent modification and updates
        if (!this.targetIndex.lock()) {
            throw new IndexException("Failed to acquire exclusive write lock on target index '"
                    + this.targetIndex.getId() + "'");
        }
        
        // Lock temporary index
        if (!this.temporaryIndex.lock()) {
            this.targetIndex.unlock();
            throw new IndexException("Failed to acquire exclusive write lock on temporary index '" 
                    + this.temporaryIndex.getId() + "'");
            
        }
        
        // Start re-indexing to provided temporary index
        LOG.info("Exclusive write lock acquired on target index '" 
                                            + this.targetIndex.getId() + "'");
        try {
            LOG.info("Initiating re-indexing to temporary index '" 
                                            + this.temporaryIndex.getId() + "'");
            int count = 
                new DirectReindexer(this.temporaryIndex, this.indexDataAccessor).runWithExternalLocking();
            
            LOG.info("Clearing contents of target index '" + this.targetIndex.getId() + "' now");
            this.targetIndex.clearContents();
            
            LOG.info("Adding contents of temporary index to target index now");
            this.targetIndex.addIndexContents(this.temporaryIndex);
            
            if (LOG.isInfoEnabled()) {
                LOG.info("Merge operation completed successfully, " + count + " property sets" 
                        + " were indexed.");
                LOG.info("Closing temporary index instance.");
            }
            
            this.temporaryIndex.close();
            
            return count;
        } catch (IndexException ie) {
            LOG.warn("Re-indexing using temporary index failed: " + ie.getMessage());
            throw ie;
        } finally {
            this.targetIndex.unlock();
            this.temporaryIndex.unlock();
        }
        
    }
    
}
