/* Copyright (c) 2009,2016 University of Oslo, Norway
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
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import vtk.repository.index.IndexException;

/**
 * A stupid bean which uses system index operation manager to perform reindexing
 * at application startup.
 */
public class ReindexAtStartupBean implements InitializingBean, ApplicationListener<ContextRefreshedEvent> {
    
    private final Logger logger = LoggerFactory.getLogger(ReindexAtStartupBean.class);

    private IndexOperationManager indexOperationManager;
    private boolean enabled = true;
    private boolean afterInit = false;
    
    @Override
    public void afterPropertiesSet() throws Exception {
        if (!enabled || afterInit) return;
        
        logger.info("Performing synchronous re-indexing of index with ID '"
                + indexOperationManager.getManagedInstance().getId() + "' using IndexOperationManager ..");
        indexOperationManager.reindex(false);
        Exception e;
        if ((e = indexOperationManager.getLastReindexingException()) != null) {
            throw new BeanInitializationException("Re-indexing failed", e);
        }
        
        try {
            // Optimize index afterwords
            logger.info("Optimizing index ..");
            indexOperationManager.optimize();
            logger.info("Optimization completed.");
        } catch (IndexException ie) {
            throw new BeanInitializationException("Optimizing index failed", ie);
        }
        
        logger.info("Re-indexing finished.");
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!enabled || !afterInit) return;

        // Perform async reindexing after application init
        logger.info("Starting asynchronous re-indexing of index with ID '"
                + indexOperationManager.getManagedInstance().getId() + "' using IndexOperationManager ..");

        indexOperationManager.reindex(true);
    }

    @Required
    public void setIndexOperationManager(IndexOperationManager manager) {
        this.indexOperationManager = manager;
    }

    /**
     * Set whether this is enabled or not.
     *
     * <p>When disabled, nothing is done at all.
     * @param enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Set whether reindexing should be done after context initialization or not.
     *
     * <p>When this is <code>false</code> (the default), reindexing is performed
     * synchronously at startup and will delay the application initialization until
     * the operation is finished. When <code>true</code>, the reindexing will
     * be started asynchronously <em>after</em> the application context has finished
     * initializing.
     *
     * @param afterInit
     */
    public void setAfterInit(boolean afterInit) {
        this.afterInit = afterInit;
    }

}
