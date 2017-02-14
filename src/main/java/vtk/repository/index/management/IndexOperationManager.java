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
package vtk.repository.index.management;

import java.util.Date;

import vtk.repository.index.PropertySetIndex;
import vtk.repository.index.consistency.ConsistencyCheck;

/**
 * Interface for high level index operation management.
 */
public interface IndexOperationManager {


    /**
     * Get the managed index instance.
     * @return
     */
    public PropertySetIndex getManagedInstance();

    /**
     * Set mutex lock on managed index instance.
     *
     * <p><em>Note that this should not be used before invoking other manager
     * operations, as those operations will themselves perform locking internally.</em>
     * @return
     */
    public boolean lock();

    /**
     * Remove mutex lock on managed index instance.
     */
    public void unlock();

    /**
     * Test if managed instance is currently mutex locked.
     *
     * <p>This method may block for a short time, but never indefinitely.
     * @return
     */
    public boolean isLocked();
    

    /**
     * Close the index.
     * @throws IllegalStateException
     */
    public void close() throws IllegalStateException;

    /**
     * Reinitialize
     * @throws IllegalStateException
     */
    public void reinitialize() throws IllegalStateException;

    /**
     * Test whether the managed instance is closed
     * @return
     */
    public boolean isClosed();
    
    /**
     * Start reindexing operation.
     * @param asynchronous if operation should be started in a background thread
     * @throws IllegalStateException if reindexing is already running, if consistency check is already running, if closed
     */
    public void reindex(boolean asynchronous) throws IllegalStateException;

    /**
     * Test whether reindexing is currently running.
     * @return
     */
    public boolean isReindexing();

    /**
     * Optimize underlying index.
     */
    public void optimize();

    /**
     * Test whether the last reindexing completed normally
     * @return
     * @throws IllegalStateException
     */
    public boolean lastReindexingCompletedNormally() throws IllegalStateException;

    /**
     * Get last reindexing exception, if any.
     * @return
     */
    public Exception getLastReindexingException();

    /**
     * Get completion time of last reindexing.
     * @return
     */
    public Date getLastReindexingCompletionTime();

    /**
     * Test whether there are results from the latest reindexing operation.,
     * @return
     */
    public boolean hasReindexingResults();

    /**
     * Clear results from the latest reindexing operation.
     * @throws IllegalStateException
     */
    public void clearLastReindexingResults() throws IllegalStateException;

    /**
     * Get resource count for latest reindexing opertion.
     * @return
     * @throws IllegalStateException
     */
    public int getLastReindexingResourceCount() throws IllegalStateException;

    /**
     * Initiate a consistency check.
     * @param asynchronous
     * @throws IllegalStateException if reindexing is already running, if consistency check is already running, if closed
     */
    public void checkConsistency(boolean asynchronous) throws IllegalStateException;

    /**
     * Get result of last consistency check.
     * @return
     * @throws IllegalStateException
     */
    public ConsistencyCheck getLastConsistencyCheck() throws IllegalStateException;

    /**
     * Get error of last consistency check.
     * @return
     */
    public Exception getLastConsistencyCheckException();

    /**
     * Time of last consistency check completion.
     * @return
     */
    public Date getLastConsistencyCheckCompletionTime();

    /**
     * Query if last consistency check completed normally.
     * @return
     * @throws IllegalStateException
     */
    public boolean lastConsistencyCheckCompletedNormally() throws IllegalStateException;

    /**
     * Query if currently running consistency check.
     * @return
     */
    public boolean isCheckingConsistency();

    /**
     * Clear results of last consistency check.
     * @throws IllegalStateException
     */
    public void clearLastConsistencyCheckResults() throws IllegalStateException;

}
