/* Copyright (c) 2014–2015, University of Oslo, Norway
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

package vtk.repository.index;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexDeletionPolicy;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.store.SimpleFSLockFactory;
import org.apache.lucene.util.Version;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Required;
import vtk.util.threads.Mutex;

/**
 * Lower level management of single Lucene index.
 * 
 * <p>Handles the following, more or less:
 * <ul>
 *   <li>Access to <code>IndexSearcher</code> and <code>IndexWriter</code> instances.
 *   <li>Management of index searcher instances for refresh and warming.
 *   <li>Index life-cycle (initialization/open/close) and commit.
 *   <li>Index storage details.
 *   <li>Optional mutex-locking for exclusive write access between threads that modify
 * the index.
 * </ul>
 * 
 * <p>Configurable properties:
 * <ul>
 *   <li><code>indexPath</code> - absolute path to file system directory where index should be created.
 *   <li>TODO complete me.
 * </ul>
 */
public class IndexManager implements DisposableBean {
    
    private final Log logger = LogFactory.getLog(IndexManager.class.getName());
    
    private File storageRootPath;
    private String storageId;
    private boolean batchIndexingMode = false;

    private int maxLockAcquireTimeOnShutdown = 30; // 30 seconds max to wait for mutex lock when shutting down
    private boolean forceUnlock = true;
    private int writeLockTimeoutSeconds = 30;
    private int keepOldCommits = 0;
    private boolean useSimpleLockFactory = false;

    // Lucene directory abstraction
    private volatile Directory directory;
    
    // The IndexWriter instance used to modify the index.
    private IndexWriter writer;
    
    // Manages searching and general reading of index
    private SearcherManager searcherManager;
    
    // Searcher factory is used to create new IndexSearcher instances in SearcherManager
    private SearcherFactory searcherFactory;

    // Internal mutex lock backing the public locking functions of this class.
    private final Mutex lock = new Mutex();


    /**
     * Open the underlying index for writing and searching, optionally specify
     * if a new index should be created at the time of opening.
     * 
     * <p>If index is already open when this method is called, a full close + open
     * initialization will be performed, and this may interreupt ongoing searches.
     * Use {@link #reopen(boolean) } instead in such cases, to avoid disrupting
     * ongoing searches.
     * 
     * @param createNewIndex if <code>true</code>, then any existing index at the
     * storage location is cleared and a new and empty index is created. Use
     * with caution.
     * @param readOnly if <code>true></code>, then index is opened in read-only
     * mode, and you will not be able to obtain an <code>IndexWriter</code> instance.
     * If <code>createNewIndex</code> is <code>true</code> at the same time, an exception will be thrown.
     * 
     * @throws IOException in case of errors with index or storage.
     */
    public synchronized void open(boolean createNewIndex, boolean readOnly) throws IOException {
        if (!isClosed()) {
            close();
        }
        
        if (createNewIndex && readOnly) {
            throw new IllegalArgumentException("Cannot create new index when opening read-only");
        }
        
        if (storageRootPath != null && storageId != null) {
            directory = makeDirectory(initStorage(storageRootPath, storageId));
        } else {
            directory = makeDirectory(null);
        }
        
        if (!readOnly) {
            checkIndexLock(directory);
        }
        
        initIndex(directory, createNewIndex);

        if (!readOnly) {
            writer = new IndexWriter(directory, newIndexWriterConfig());
        }
        // For Lucene NRT (Near Real Time) searching, the writer instance could be provided to
        // the searcher factory here. However, due to how we update documents, it is
        // undesirable to let searches see uncomitted index changes. So we simply
        // don't use NRT.
        searcherManager = new SearcherManager(directory, searcherFactory);
    }

    /**
     * Close the index to free resources (I/O and memory). After this method
     * has returned, the index must be {@link #open() opened} again to be used.
     * 
     * Calling this method on an already closed index will have no effect, and
     * will not produce any errors.
     * 
     * @throws IOException in case of errors closing down.
     */
    public synchronized void close() throws IOException {
        if (searcherManager != null) {
            searcherManager.close();
        }
        
        if (writer != null) {
            writer.close();
            writer = null;
        }
        
        if (directory != null) {
            directory.close();
            directory = null;
        }
        
        // Unset searchManager last after nullifying directory to avoid NPE for
        // concurrent searching while this close method runs.
        searcherManager = null;
    }
    
    /**
     * Reopen index, possibly with a change in read-only state.
     * 
     * <p>Note that this method never force-unlocks the index if <code>readOnly == false</code>.
     * 
     * <p>
     * In all cases, reopening index will cause any existing writer to be
     * committed and closed, and the latest changes will become visible by index
     * searchers/readers obtained through this class.
       * 
     * <p>
     * If index was closed, then this method will simply open the index like {@link #open(boolean, boolean)}
     * , either in read-only or read-write mode.
     * 
     * <p>If index was open in read-write mode and is
     * reopened in read-only mode, then the writer will be committed+closed and the existing
     * index reader refreshed to reflect the latest state of the underlying index.
     * 
     * <p>If index was open in read-only mode, and is reopened in read-write mode, then
     * an <code>IndexWriter</code> is instantiated.
     * 
     * 
     * <p>If index is closed when this is called
     * @param readOnly if <code>true</code>, reopen index read-only mode, otherwise reopen in normal mode.
     * @throws IOException if an IO error occurs
     */
    public synchronized void reopen(boolean readOnly) throws IOException {
        if (isClosed()) {
            open(false, readOnly);
        } else {
            // Index is open, which means directory is not null.
            if (writer != null) {
                writer.close();
                writer = null;
            }
            
            if (searcherManager == null) {
                searcherManager = new SearcherManager(directory, searcherFactory);
            } else {
                searcherManager.maybeRefreshBlocking();
            }
            
            if (!readOnly) {
                // Possible need more advanced lock handling here in clustered scenario
                // We may need to wait a certain grace period, before we try claim index write lock,
                // if a lock was detected. Force unlocking is not safe in a clustered scenario.
                writer = new IndexWriter(directory, newIndexWriterConfig());
            } 
        }
    }

    /**
     * Check whether the index is currently closed.
     * @return <code>true</code> if the index is closed.
     */
    public boolean isClosed() {
        return directory == null;
    }

    /**
     * Check if the index is currently open and in read-only mode.
     * 
     * @return <code>true</code> if index is open and in read-only mode, <code>false</code>
     * in all other cases.
     */
    public boolean isReadOnly() {
        return ! isClosed() && writer == null;
    }
    
    /**
     * Commit all changes made through the provided {@link #getIndexWriter() IndexWriter }
     * instance and refresh readers for searching.
     * 
     * This call will block until all changes are flushed to index and reader
     * instances have been refreshed.
     * 
     * @throws IOException in case of errors comitting the changes or if index is closed.
     */
    public synchronized void commit() throws IOException {
        if (isClosed()) {
            throw new IOException("Index is closed");
        }
        
        writer.commit();
        searcherManager.maybeRefreshBlocking();
    }

    /**
     * Get access to {@link IndexWriter} instance.
     * 
     * @return a (shared) <code>IndexWriter</code> instance.
     * @throws IOException in case the index is closed
     */
    public synchronized IndexWriter getIndexWriter() throws IOException {
        if (isClosed()) {
            throw new IOException("Index is closed");
        }
        if (isReadOnly()) {
            throw new IOException("Index is opened in read-only-mode and no writer instance can be provided");
        }
        
        return writer;
    }
    
    /**
     * Obtain an index searcher.
     * 
     * You should release the obtained searcher after use in a finally block, by calling
     * {@link #releaseIndexSearcher(org.apache.lucene.search.IndexSearcher) }.
     * 
     * @return 
     * @throws IOException 
     */
    public IndexSearcher getIndexSearcher() throws IOException {
        if (isClosed()) {
            throw new IOException("Index is closed");
        }
        
        // Guard against possible NPE if index is being closed at the same time
        // this method is called
        SearcherManager sm = searcherManager;
        if (sm != null) {
            return sm.acquire();
        } else {
            throw new IOException("Index is closed");
        }
    }

    /**
     * Release a search previously obtained with {@link #getIndexSearcher() }. This
     * is necessary to free resources and should be done in a <code>finally</code> block
     * whenever a searcher is used.
     * 
     * @param searcher the index searcher. May be <code>null</code>, and in that
     * case the call does nothing.
     * 
     * @throws IOException in case of errors with index
     */
    public void releaseIndexSearcher(IndexSearcher searcher) throws IOException {
        if (searcher == null) return;
        
        
        // Guard against possible NPE at closing time
        SearcherManager sm = searcherManager;
        if (sm != null) {
            sm.release(searcher);
        }
        
        logger.debug("searcher.getIndexReader().getRefCount() = " + searcher.getIndexReader().getRefCount());
    }
    
    /** Check index filesystem-lock, force-unlock if requested. */
    private void checkIndexLock(Directory directory) throws IOException {
        if (IndexWriter.isLocked(directory)) {
            // See if we should try to force-unlock it
            if (forceUnlock) {
                logger.warn("Index directory is locked, forcing unlock");
                IndexWriter.unlock(directory);
            } else {
                throw new IOException("Index directory " + directory
                        + " is locked and 'forceUnlock' is set to 'false'.");
            }
        }
    }
    
    private void initIndex(Directory directory, boolean createNew) throws IOException {
        if (!DirectoryReader.indexExists(directory) || createNew) {
            IndexWriterConfig conf = newIndexWriterConfig();
            conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            new IndexWriter(directory, conf).close();
            logger.info("Created new index in directory " + directory);
        }
    }
    
    private File initStorage(File storageRootPath, String storageId)
        throws IOException {
        
        File storageDirectory = new File(storageRootPath, storageId);
        
        if (storageDirectory.isDirectory()) {
            if (! storageDirectory.canWrite()) {
                throw new IOException("Resolved storage directory '"
                        + storageDirectory.getAbsolutePath() 
                        + "' is not writable");
            }
        } else if (storageDirectory.isFile()) {
            throw new IOException("Resolved storage directory '" 
                    + storageDirectory.getAbsolutePath()
                    + "' is a file");
        } else {
            // Directory does not exist, we need to create it.
            if (!storageDirectory.mkdir()) {
                throw new IOException("Failed to create resolved storage directory '"
                        + storageDirectory.getAbsolutePath() 
                        + "'");
            }
        }
        
        return storageDirectory;
    }
    
    private Directory makeDirectory(File path) throws IOException {
        if (path != null) {
            return FSDirectory.open(path, useSimpleLockFactory ? new SimpleFSLockFactory() : null);
        } else {
            logger.warn("No storage path provided, using volatile memory index.");
            return new RAMDirectory();
        }
    }

    
    private IndexWriterConfig newIndexWriterConfig() {
        IndexWriterConfig cfg = new IndexWriterConfig(Version.LATEST, new KeywordAnalyzer());
        
        cfg.setMaxThreadStates(1); // We have at most one writing thread
        
        // Disable stored field compression, because it hurts performance
        // badly for our usage patterns:
        cfg.setCodec(new Lucene410CodecWithNoFieldCompression());
        
        cfg.setWriteLockTimeout(writeLockTimeoutSeconds*1000);
        
        cfg.setIndexDeletionPolicy(new IndexDeletionPolicy() {
            @Override
            public void onInit(List<? extends IndexCommit> commits) throws IOException {
                onCommit(commits);
            }
            @Override
            public void onCommit(List<? extends IndexCommit> commits) throws IOException {
                final int toDelete = Math.max(commits.size() - keepOldCommits - 1, 0);
                int deleteCount = 0;
                for (IndexCommit commit: commits) {
                    if (deleteCount++ < toDelete) {
                        commit.delete();
                    } else {
                        return;
                    }
                }
            }
        });
        
        // XXX switch to LogByteSizeMergePolicy if problems with (default) TieredMergePolicy arise.
//        LogByteSizeMergePolicy mp = new LogByteSizeMergePolicy();
//        mp.setMergeFactor(this.batchIndexingMode ? 25: 5);
//        cfg.setMergePolicy(mp);

        cfg.setRAMBufferSizeMB(batchIndexingMode ? 32.0 : IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB);
        return cfg;
    }
    
    /**
     * Execute a thorough low-level index check. Can be time consuming.
     * 
     * This method should only be called with mutex lock acquired, to ensure
     * directory is not modified while check is running. 
     * @return <code>true</code> if no problems were found with the index, <code>false</code>
     * if problems were detected. Details of any problems are not available, but
     * a rebuild is likely wise to do if this method returns <code>false</code>.
     * @throws IOException in case of errors during check or if index is closed.
     * Assume the index is corrupt if this occurs for any other reason than index
     * being closed.
     */
    public boolean checkIndex() throws IOException {
        if (isClosed()) {
            throw new IOException("Index is closed");
        }
        
        CheckIndex ci = new CheckIndex(directory);
        CheckIndex.Status status = ci.checkIndex();
        return status.clean;
    }
    
    
    /**
     * Explicit usage mutex locking inside a single JVM. Should be acquired
     * before doing any write or life cycle operations on index. This locking
     * records no thread ownership and is present merely as a tool for the
     * caller to ensure low level exclusive index access.
     */
    public boolean lockAcquire() {
        if (this.lock.lock()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Thread '" + Thread.currentThread().getName()
                        + "' got lock on index '"
                        + this.storageId + "'.");
            }
            return true;
        }

        return false;
    }

    /**
     * Explicit write locking with timeout.
     * 
     * @param timeout timeout in milliseconds
     * @return <code>true</code> if lock was successfully obtained, <code>false</code>
     * otherwise.
     * 
     * @see #lockAcquire() 
     */
    public boolean lockAttempt(long timeout) {
        if (this.lock.tryLock(timeout, TimeUnit.MILLISECONDS)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Thread '" + Thread.currentThread().getName()
                        + "' got lock on index '" + this.storageId + "'.");
            }
            return true;

        } else if (logger.isDebugEnabled()) {
            logger.debug("Thread '" + Thread.currentThread().getName()
                    + "' failed to acquire lock on index '"
                    + this.storageId
                    + "' after waiting for " + timeout + " ms");
        }

        return false;
    }

    /**
     * Release explicit write lock.
     */
    public void lockRelease() {
        this.lock.unlock();
        if (logger.isDebugEnabled()) {
            logger.debug("Thread '" + Thread.currentThread().getName() +
                         "' unlocked write lock on index '"
                    + this + "'.");
        }
    }

    /**
     * 
     * @return storage id as a string
     */
    public String getStorageId() {
        return storageId;
    }
    
    // Framework life-cycle
    @Override
    public void destroy() throws Exception {
       logger.info("Index shutdown, waiting for write lock on index '"
               + this.storageId + "' ..");
       if (lockAttempt(this.maxLockAcquireTimeOnShutdown * 1000)) {
           logger.info("Got write lock on index '" + this.storageId
                   + "', closing down.");
           
           close();
       } else {
           logger.warn("Failed to acquire the write lock on index '"
              + this.storageId + "' within "
              + " the time limit of " + this.maxLockAcquireTimeOnShutdown 
              + " seconds, index might be corrupted.");
       }
    }
    
    /**
     * Set the {@link SearcherFactory} to be used for creating new {@link IndexSearcher}
     * instances. (Provide a factory that does warmup for better performance
     * after write operations.)
     * 
     * @param searcherFactory the <code>SearcherFactory</code> instance.
     */
    public void setSearcherFactory(SearcherFactory searcherFactory) {
        this.searcherFactory = searcherFactory;
    }
    
    /**
     * Set the storage id of this index. The storage id is the name of the index
     * directory created under the storage root path. Thus the provided id should
     * be file name friendly and not contain for instance a slash character.
     * 
     * @param storageId the storage Id as a string.
     */
    @Required
    public void setStorageId(String storageId) {
        this.storageId = storageId;
    }
    
    @Required
    public void setStorageRootPath(File rootPath) {
        this.storageRootPath = rootPath;
    }

    /**
     * @param batchIndexingMode the batchIndexingMode to set
     */
    public void setBatchIndexingMode(boolean batchIndexingMode) {
        this.batchIndexingMode = batchIndexingMode;
    }

    /**
     * @param maxLockAcquireTimeOnShutdown the maxLockAcquireTimeOnShutdown to set
     */
    public void setMaxLockAcquireTimeOnShutdown(int maxLockAcquireTimeOnShutdown) {
        this.maxLockAcquireTimeOnShutdown = maxLockAcquireTimeOnShutdown;
    }

    /**
     * Force unlock cannot be safely used in a clustered environment.
     * @param forceUnlock the forceUnlock to set
     */
    public void setForceUnlock(boolean forceUnlock) {
        this.forceUnlock = forceUnlock;
    }
    
    /**
     * Set limit for how long we wait to obtain Lucene write lock when opening
     * in read-write mode.
     * @param writeLockTimeoutSeconds 
     */
    public void setWriteLockTimeoutSeconds(int writeLockTimeoutSeconds) {
        this.writeLockTimeoutSeconds = writeLockTimeoutSeconds;
    }
    
    /**
     * Needs to be set to <code>true</code> if on NFS.
     * @param useSimpleLockFactory 
     */
    public void setUseSimpleLockFactory(boolean useSimpleLockFactory) {
        this.useSimpleLockFactory = useSimpleLockFactory;
    }
    
    /**
     * Set how many old commits should be kept in index. The latest commit is
     * never affected and comes in addtition to the number of old commits specified
     * here.
     *
     * <p>
     * The default value is 0, which means that only the most recent commit is
     * kept. However, on shared file systems like NFS, it is necessary to
     * additionally keep some old commits around, so that searchers reading
     * index from older points in time will not fail.
     *
     * @param keepOldCommits the number of commits to keep. 
     */
    public void setKeepOldCommits(int keepOldCommits) {
        this.keepOldCommits = keepOldCommits >= 0 ? keepOldCommits : 0;
    }

}
