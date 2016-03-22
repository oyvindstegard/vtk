/* Copyright (c) 2013, University of Oslo, Norway
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

package vtk.repository.systemjob;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.SystemChangeContext;
import vtk.security.SecurityContext;

/**
 * Abstract super class for all jobs which work on a selection of repository
 * resources, one at a time.
 * 
 * <p>
 * Needs a configured {@link PathSelector} and will for each selected path call
 * to subclass to perform the task on the resource represented by that path.
 * <p>
 * The class has a number of configurable properties for controlling behaviour
 * <ul>
 * <li><code>abortOnException</code> - whether to continue with current job as a whole 
 * when an exception is thrown during processing of a single resource.
 * <li><code>abortOnInterrupt</code> - whether to continue with job or
 * not when job thread is interrupted during execution.
 * <li><code>skipLockedResources</code> - control whether to skip locked
 * resources or not. A warning will be logged if this option is
 * <code>true</code> and a selected resource is locked at the time of execution.
 * <li><code>executeWhenReadOnly</code> - controls whether to execute jobs when
 * repository is in read-only mode.
 * </ul>
 *
 */
public abstract class AbstractResourceJob extends RepositoryJob {

    private PathSelector pathSelector;
    private boolean abortOnException = true;
    private boolean abortOnInterrupt = true;
    private boolean skipLockedResources = true;
    private boolean executeWhenReadOnly = false;
    
    private final Logger logger = LoggerFactory.getLogger(AbstractResourceJob.class);
    
    @Override
    public final void executeWithRepository(final Repository repository, 
            final SystemChangeContext context) throws Exception {

        if (repository.isReadOnly() && !isExecuteWhenReadOnly()) {
            logger.warn("Job " + getId() 
                + ": will not run as scheduled, repository is in read-only mode.");
            return;
        }
        
        final String token = SecurityContext.exists() ? 
                SecurityContext.getSecurityContext().getToken() : null;

        final ExecutionContext ctx = new ExecutionContext(repository, token, context);
        PathSelectCallback pathCallback = new PathSelectCallback() {
            
            @Override
            public void beginBatch(int total) throws Exception {
                ctx.setTotal(total);
                executeBegin(ctx);
                logger.info("Job " + getId() + " starting execution with " + total 
                            + " resource(s) selected.");
            }

            @Override
            public void select(Path path) throws Exception {
                ctx.incrementCount();
                
                if (logger.isDebugEnabled()) {
                    logger.debug("Executing job " + getId() + " for path " + path 
                            + " [" + ctx.getCount() + "/" + ctx.getTotal() + "]");
                }
                
                try {
                    Resource resource = repository.retrieve(token, path, false);
                    if (resource.getLock() != null && isSkipLockedResources()) {
                        logger.warn("Job " + getId() 
                            + ": resource is currently locked and will be skipped: " + path);
                        return;
                    }

                    executeForResource(resource, ctx);
                }
                catch (ResourceNotFoundException rnfe) {
                    logger.warn("Job " + getId() 
                        + ": resource in selection not found in repository: " + path);
                }
                catch (Exception e) {
                    logger.warn("Job " + getId() 
                            + ": exception during execution for resource " + path, e);
                    if (isAbortOnException()) {
                        throw e;
                    }
                }

                if (isAbortOnInterrupt()) {
                    checkForInterrupt();
                }
            }
        };
        
        // Start execution by invoking path selector with callback
        pathSelector.selectWithCallback(repository, context, pathCallback);
        
        executeEnd(ctx);
    }
    
    /**
     * Common execution context for current set of resources being processed
     * by a job. Gives access to commonly needed objects
     * and may be used to hold state by between invocations
     * of {@link #executeForResource(vtk.repository.Resource,
     * vtk.repository.Repository, java.lang.String,
     * vtk.repository.SystemChangeContext, int, int) executeForResource}.
     */
    public static final class ExecutionContext {
        private int total;
        private int count = 0;
        private final Map<String,Object> attributes = new HashMap<String,Object>();
        private final Repository repository;
        private final String token;
        private final SystemChangeContext scs;
        
        private ExecutionContext(Repository r, String t, SystemChangeContext scs) {
            this.repository = r;
            this.token = t;
            this.scs = scs;
        }
        
        private void incrementCount() {
            ++count;
        }
        
        private void setTotal(int total) {
            this.total = total;
        }

        /**
         * Set an arbitrary attribute on the execution context.
         * @param key the key used to store the value
         * @param value 
         */
        public void setAttribute(String key, Object value) {
            attributes.put(key, value);
        }
        
        /**
         * Get value of an attribute on the execution context.
         * @param key the key used to store the value in 
         * {@link #setAttribute(java.lang.String, java.lang.Object) setAttribute}.
         * @return the value or <code>null</code> if no such value exists.
         */
        public Object getAttribute(String key) {
            return attributes.get(key);
        }
        
        /**
         * @return current resource number in selection processing
         */
        public int getCount() {
            return count;
        }

        /**
         * @return total number of resources selected for job exection
         */
        public int getTotal() {
            return total;
        }

        /**
         * 
         * @return the <code>Repository</code>
         */
        public Repository getRepository() {
            return repository;
        }
        
        /**
         * 
         * @return the security token
         */
        public String getToken() {
            return token;
        }
        
        /**
         * 
         * @return the <code>SystemChangeContext</code> for the job execution
         */
        public SystemChangeContext getSystemChangeContext() {
            return scs;
        }
    }

    /**
     * Called once at the beginning of job execution before any resources have
     * been processed. May be overridden by subclasses to setup things.
     * By default, it does nothing.
     * 
     * @param ctx a provided execution context for this execution.
     * @throws Exception any exception thrown from this method will abort the
     *         job execution as a whole.
     */
    protected void executeBegin(ExecutionContext ctx) throws Exception {
        // Default impl does nothing
    }
    
    /**
     * Called once when all selected resources have been processed. May be used
     * to finalize the job execution.
     * 
     * @param ctx the execution context
     * @throws Exception 
     */
    protected void executeEnd(ExecutionContext ctx) throws Exception {
        // Default impl does nothing
    }
    
    /**
     * This method should be overridden by subclasses and will be invoked for each
     * path selected by the configured path selector.
     * 
     * @param resource a selected resource
     * @param ctx the execution context
     * @throws Exception
     */
    protected abstract void executeForResource(Resource resource, ExecutionContext ctx)
            throws Exception;

    /**
     * @param pathSelector the pathSelector to set
     */
    @Required
    public void setPathSelector(PathSelector pathSelector) {
        this.pathSelector = pathSelector;
    }

    /**
     * @return the abortOnException
     */
    public boolean isAbortOnException() {
        return abortOnException;
    }

    /**
     * @param abortOnException the abortOnException to set
     */
    public void setAbortOnException(boolean abortOnException) {
        this.abortOnException = abortOnException;
    }

    /**
     * @return the abortOnInterrupt
     */
    public boolean isAbortOnInterrupt() {
        return abortOnInterrupt;
    }

    /**
     * @param abortOnInterrupt the abortOnInterrupt to set
     */
    public void setAbortOnInterrupt(boolean abortOnInterrupt) {
        this.abortOnInterrupt = abortOnInterrupt;
    }

    /**
     * @return <code>true</code> if locked resources should be skipped entirely.
     */
    public boolean isSkipLockedResources() {
        return skipLockedResources;
    }

    /**
     * Set whether to skip locked resources during job execution.
     * If <code>true</code> locked resources will not be provided to method 
     * {@link #executeForResource(Resource, ExecutionContext) executeForResource}.
     * However, this is not a guarantee that the resource will <em>not</em> be locked
     * between the test in this class, and by the time the <code>executeForResource</code>
     * is invoked in the subclass. If such a guarantee is needed, then the
     * subclass implementing the job logic will need to do resource locking itself.
     * 
     * @param skipLockedResources the ignoreLockedResources to set
     */
    public void setSkipLockedResources(boolean skipLockedResources) {
        this.skipLockedResources = skipLockedResources;
    }

    /**
     * @return the executeWhenReadOnly
     */
    public boolean isExecuteWhenReadOnly() {
        return executeWhenReadOnly;
    }

    /**
     * @param executeWhenReadOnly set if job should execute even when repository
     * is in a read-only state.
     */
    public void setExecuteWhenReadOnly(boolean executeWhenReadOnly) {
        this.executeWhenReadOnly = executeWhenReadOnly;
    }
    
}
