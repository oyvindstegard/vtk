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
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
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
public class IndexManager implements InitializingBean, DisposableBean {
    
    private final Log logger = LogFactory.getLog(IndexManager.class.getName());
    
    private File storageRootPath;
    private String storageId;
    private boolean batchIndexingMode = false;
    private int maxLockAcquireTimeOnShutdown = 30; // 30 seconds max to wait for mutex lock when shutting down
    private boolean forceUnlock = true;
    private boolean closeAfterInit = false;

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
     * Open the underlying index for writing and searching.
     * @throws IOException in case of errors with index or storage.
     */
    public synchronized void open() throws IOException {
        open(false);
    }

    /**
     * Open the underlying index for writing and searching, optionally specify
     * if a new index should be created at the time of opening.
     * 
     * @param createNewIndex if <code>true</code>, then any existing index at the
     * storage location is cleared and a new and empty index is created. Use
     * with caution.
     * @throws IOException in case of errors with index or storage.
     */
    public synchronized void open(boolean createNewIndex) throws IOException {
        if (!isClosed()) {
            return;
        }
        
        if (storageRootPath != null && storageId != null) {
            directory = makeDirectory(initStorage(storageRootPath, storageId));
        } else {
            directory = makeDirectory(null);
        }
        
        checkIndexLock(directory);
        
        initIndex(directory, createNewIndex);

        writer = new IndexWriter(directory, newIndexWriterConfig());
        
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
     * Check whether the index is currently closed.
     * @return <code>true</code> if the index is closed.
     */
    public boolean isClosed() {
        return directory == null;
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
                logger.warn("Index directory is locked, forcing unlock.");
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
            return FSDirectory.open(path);
        } else {
            logger.warn("No storage path provided, using volatile memory index.");
            return new RAMDirectory();
        }
    }

    
    private IndexWriterConfig newIndexWriterConfig() {
        IndexWriterConfig cfg = new IndexWriterConfig(Version.LATEST, new KeywordAnalyzer());
        
        cfg.setMaxThreadStates(1); // We have at most one writing thread.
        
        // Disable stored field compression, because it hurts performance
        // badly for our usage patterns:
        cfg.setCodec(new Lucene410CodecWithNoFieldCompression());
        
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
     * Explicit locking. Should be acquired before doing any write or life cycle
     * operations on index. This locking records no thread ownership and is
     * present merely as a tool for the caller to ensure exclusive index access.
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
    public void afterPropertiesSet() throws IOException {
        open();
        if (closeAfterInit) {
            close();
        }
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
     * Set whether the underlying index should be explicitly closed
     * after the <em>first</em> initialization is complete. Can be used for
     * index instances you don't want to have open before it is  needed.
     * Such an instance must be initialized again before it can be used
     * (by calling {@link #open() }). 
     * @param closeAfterInit <code>true</code> if index should be closed after
     * initialization is complete.
     */
    public void setCloseAfterInit(boolean closeAfterInit) {
        this.closeAfterInit = closeAfterInit;
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
     * @param forceUnlock the forceUnlock to set
     */
    public void setForceUnlock(boolean forceUnlock) {
        this.forceUnlock = forceUnlock;
    }
    
}
