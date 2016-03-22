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
package vtk.repository.index.management;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.index.IndexException;
import vtk.repository.index.PropertySetIndex;
import vtk.repository.store.IndexDao;

/**
 * A stupid bean which starts a synchronous re-indexing of the configured
 * property set index at bean initialization time. 
 *
 */
public class ReindexAtStartupBean implements InitializingBean {
    
    private Logger logger = LoggerFactory.getLogger(ReindexAtStartupBean.class);
    
    private PropertySetIndex index;
    private IndexDao indexDao;
    private boolean enabled = false;
    
    public void afterPropertiesSet() throws Exception {
        if (!enabled) return;
        
        IndexOperationManager manager = new IndexOperationManagerImpl(this.index, 
                                                                      this.indexDao);
        logger.info("Starting synchronous re-indexing of index with ID '" 
                + this.index.getId() + "' using IndexOperationManager ..");
        manager.reindex(false);
        if (manager.getLastReindexingException() != null) {
            throw new BeanInitializationException("Re-indexing failed", 
                                          manager.getLastReindexingException());
        }
        
        try {
            // Optimize index afterwords
            logger.info("Optimizing index ..");
            manager.optimize();
            logger.info("Optimization completed.");
        } catch (IndexException ie) {
            throw new BeanInitializationException("Optimizing index failed", ie);
        }
        
        logger.info("Re-indexing finished.");
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
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
