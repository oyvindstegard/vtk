/* Copyright (c) 2006, University of Oslo, Norway
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

import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

import vtk.cluster.ClusterAware;
import vtk.cluster.ClusterContext;
import vtk.cluster.ClusterRole;
import vtk.repository.Acl;
import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.PropertySetImpl;
import vtk.repository.index.mapping.DocumentMapper;
import vtk.repository.index.mapping.DocumentMappingException;
import vtk.repository.index.mapping.ResourceFields;

/**
 * <code>PropertySet</code> index using Lucene.
 */
public class PropertySetIndexImpl implements PropertySetIndex, ClusterAware, InitializingBean {

    Logger logger = LoggerFactory.getLogger(PropertySetIndexImpl.class);

    private IndexManager index;
    private DocumentMapper documentMapper;
    private boolean closeAfterInit = false;

    // Cluster context will be empty when not running in a clustered configuration
    private Optional<ClusterContext> clusterContext = Optional.empty();
    private ClusterRole clusterRole = ClusterRole.MASTER;
    private boolean clusterSharedStorage = true;
    
    @Override
    public void afterPropertiesSet() throws IOException {
        try {
            index.open(false, isClusterSharedReadOnly());
        } catch (IOException io) {
            if (!isApplicationLevelCompatible()) {
                logger.warn("Opening index failed with IOException, likely due to application incompatibility: {}: {}",
                        io.getClass().getSimpleName(), io.getMessage());

                // OK, a reindexing should be triggered automatically
                return;
            }

            // Unknown or non-obvious cause, re-throw to fail init
            throw io;
        }
        if (closeAfterInit) {
            index.close();
        }
    }

    public boolean isApplicationLevelCompatible() throws IndexException {
        try {
            return IndexManager.APPLICATION_COMPATIBILITY_LEVEL == index.getApplicationCompatibilityLevel();
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }

    @Override
    public boolean isClusterSharedReadOnly() {
        return clusterSharedStorage && clusterRole == ClusterRole.SLAVE;
    }
    
    private void checkWriteAccess() {
        if (isClusterSharedReadOnly()) {
            throw new IndexException("Cannot write to shared index when cluster role is SLAVE");
        }
    }

    @Override
    public void addPropertySet(PropertySet propertySet, Acl acl) throws IndexException {
        checkWriteAccess();
        
        // NOTE: Write-locking should be done above this level.
        // This is needed to ensure the possibility of efficiently batching
        // together operations without interruption.
        try {
            Document doc = this.documentMapper.getDocument((PropertySetImpl) propertySet, acl);
            if (logger.isDebugEnabled()) {
                StringBuilder docFields = new StringBuilder("Document mapper created the following document for " + propertySet.getURI() + ":\n");
                for (IndexableField field: doc) {
                    docFields.append(field.toString()).append('\n');
                }
                logger.debug(docFields.toString());
            }

            this.index.getIndexWriter().addDocument(doc);
        } catch (DocumentMappingException dme) {
            logger.warn("Could not map property set to index document", dme);
            throw new IndexException("Could not map property set to index document", dme);
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }
    
    @Override
    public void updatePropertySet(PropertySet propertySet, Acl acl) throws IndexException {
        checkWriteAccess();
        
        try {
            Term uriTerm = new Term(ResourceFields.URI_FIELD_NAME, 
                                        propertySet.getURI().toString());

            IndexWriter writer = this.index.getIndexWriter();
            
            writer.deleteDocuments(uriTerm);
            
            addPropertySet(propertySet, acl);
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }

    @Override
    public void deletePropertySetTree(Path rootUri) throws IndexException {
        checkWriteAccess();
        
        try {
            IndexWriter writer = this.index.getIndexWriter();
            writer.deleteDocuments(new Term(ResourceFields.URI_FIELD_NAME, rootUri.toString()), 
                                   new Term(ResourceFields.URI_ANCESTORS_FIELD_NAME, rootUri.toString()));

        } catch (IOException io) {
            throw new IndexException(io);
        }
    }

    @Override
    public void deletePropertySet(Path uri) throws IndexException {
        checkWriteAccess();
        
        try {
            Term uriTerm = new Term(ResourceFields.URI_FIELD_NAME, uri.toString());
            this.index.getIndexWriter().deleteDocuments(uriTerm);
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }

    @Override
    public int countAllInstances() throws IndexException {
        
        // Count all docs with URI field that are not deleted
        IndexSearcher searcher = null;
        try {
            int count=0;
            searcher = index.getIndexSearcher();
            IndexReader topLevel = searcher.getIndexReader();
            for (AtomicReaderContext arc: topLevel.leaves()) {
                final AtomicReader ar = arc.reader();
                final Bits liveDocs = ar.getLiveDocs();
                Terms terms = ar.terms(ResourceFields.URI_FIELD_NAME);
                if (terms == null) {
                    continue;
                }
                TermsEnum te = terms.iterator(null);
                DocsEnum de = null;
                BytesRef termText;
                while ((termText = te.next()) != null) {
                    de = te.docs(liveDocs, de, DocsEnum.FLAG_NONE);
                    while (de.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                        ++count;
                        if (logger.isDebugEnabled()) {
                            logger.debug("Count = " + count 
                                    + ", PropertySet URI = " + termText.utf8ToString());
                        }
                    }
                }
            }
            
            return count;

        } catch (IOException io) {
            throw new IndexException(io);
        }
        finally {
            if (searcher != null) {
                try {
                    index.releaseIndexSearcher(searcher);
                } catch (IOException io) {
                    throw new IndexException(io);
                }
            }
        }
    }

    @Override
    public void clear() throws IndexException {
        checkWriteAccess();
        
        try {
            this.index.open(true, false);
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    @Override
    public PropertySetIndexRandomAccessor randomAccessor() throws IndexException {
        try {
            return new PropertySetIndexRandomAccessorImpl(index, documentMapper);
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterator<Path> orderedUriIterator() throws IndexException {
        try {
            return new UriIterator(index);
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    @SuppressWarnings("unchecked")
    @Override
    public void close(Iterator<?> iterator) throws IndexException {
        try {
            if ((iterator instanceof CloseableIterator)) {
                ((CloseableIterator) iterator).close();
            } else {
                throw new IllegalArgumentException("Not a closeable iterator type");
            }

        } catch (Exception e) {
            throw new IndexException(e);
        }
    }


    @Override
    public boolean isClosed() {
        return this.index.isClosed();
    }


    @Override
    public void commit() throws IndexException {
        checkWriteAccess();

        try {
            this.index.commit();
        } catch (IOException io) {
            throw new IndexException(io);
        }
        if (clusterContext.isPresent()) {
            clusterContext.get().clusterMessage(new Reinitialize());
        }
    }


    @Override
    public void close() throws IndexException {
        try {
            logger.info("Closing index ..");
            this.index.close();
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    @Override
    public void reinitialize() throws IndexException {
        try {
            logger.info("Re-initializing index ..");
            this.index.reopen(isClusterSharedReadOnly());
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    @Override
    public void addIndexContents(PropertySetIndex propSetIndex) throws IndexException {
        checkWriteAccess();

        if (!(propSetIndex instanceof PropertySetIndexImpl)) {
            throw new IllegalArgumentException(
                    "Only 'vtk.repository.query.PropertySetIndexImpl' instances are supported.");
        }

        try {
            PropertySetIndexImpl indexImpl = (PropertySetIndexImpl) propSetIndex;
            IndexManager otherIndex = indexImpl.index;

            if (logger.isDebugEnabled()) {
                logger.debug("Adding all contents of index '" + indexImpl.getId() + "' to '"
                        + this.getId() + "' (this index)");
            }
            
            IndexWriter indexWriter = index.getIndexWriter();
            indexWriter.addIndexes(otherIndex.getIndexSearcher().getIndexReader());
            
            if (logger.isDebugEnabled()){
                logger.debug("Optimizing index ..");
            }
            indexWriter.forceMerge(1, true);

        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    @Override
    public void validateStorageFacility() throws StorageCorruptionException {
        final String storageId = index.getStorageId();
        boolean ok;
        try {
            ok = this.index.checkIndex();
        } catch (IOException io) {
            throw new StorageCorruptionException(
                    "Possible index corruption detected for index " + storageId, io);
        }
        
        if (!ok) {
            throw new StorageCorruptionException(
                    "Possible index corruption detected for index " + storageId);
        }
    }

    /**
     * It is no longer recommended to do explicit optimizations to one segment in
     * Lucene.
     */
    @Override
    public void optimize() throws IndexException {
        checkWriteAccess();
        
        try {
            this.index.getIndexWriter().forceMerge(1, true);
            this.index.getIndexWriter().commit();
        } catch (IOException io) {
            throw new IndexException("IOException while merging", io);
        }
    }

    @Override
    public String getId() {
        // Delegate to using accessor storage ID
        return index.getStorageId();
    }
    
    @Override
    public boolean lock() {
        return index.lockAcquire();
    }

    @Override
    public void unlock() throws IndexException {
        index.lockRelease();
    }


    @Override
    public boolean lock(long timeout) {
        return index.lockAttempt(timeout);
    }

    @Required
    public void setDocumentMapper(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    @Required
    public void setIndexAccessor(IndexManager index) {
        this.index = index;
    }

    /**
     * Set whether underlying index should be closed after first initialization.
     * 
     * <p>This can free resources for index instances which are only for
     * temporary use.
     * @param closeAfterInit 
     */
    public void setCloseAfterInit(boolean closeAfterInit) {
        this.closeAfterInit = closeAfterInit;
    }

    /**
     * Set assumed initial cluster role upon index initialization.
     * 
     * @param initClusterRole the initially assumed cluster role, before clustering framework
     * has communicated anything else through {@link ClusterAware#roleChange(vtk.cluster.ClusterRole) }.
     * Default value is {@link ClusterRole#MASTER}.
     */
    public void setInitClusterRole(ClusterRole initClusterRole) {
        if (initClusterRole == null) {
            throw new IllegalArgumentException("Cluster role at init cannot be null");
        }
        this.clusterRole = initClusterRole;
    }

    /**
     * Set whether the index is using storage that is shared amongst all nodes
     * in a cluster configuration.
     * @param clusterSharedStorage 
     */
    public void setClusterSharedStorage(boolean clusterSharedStorage) {
        this.clusterSharedStorage = clusterSharedStorage;
    }

    @Override
    public void roleChange(ClusterRole role) {
        if (!clusterSharedStorage) {
            // Role doesn't matter when index is not using cluster shared storage
            this.clusterRole = role;
            return;
        }
        
        ClusterRole prev = this.clusterRole;
        try {
            switch (role) {
                case MASTER:
                    this.clusterRole = role;
                    logger.info("Switch to master mode, previous=" + prev);
                    if (!index.isClosed()) {
                        index.reopen(false);
                    }
                    break;
                case SLAVE:
                    this.clusterRole = role;
                    logger.info("Switch to slave mode, previous=" + prev);
                    if (!index.isClosed()) {
                        index.reopen(true);
                    }
                    break;
            }
        } catch (IOException e) {
            logger.warn("Error handling cluster state change: " + role, e);
        }
    }

    @Override
    public void clusterContext(ClusterContext context) {
        this.clusterContext = Optional.of(context);
        context.subscribe(Reinitialize.class);
    }

    @Override
    public void clusterMessage(Object message) {
        if (clusterSharedStorage && !index.isClosed() && message instanceof Reinitialize) {
            try {
                this.index.reopen(isClusterSharedReadOnly());
            } catch (IOException io) {
                throw new IndexException(io);
            }
        }
    }

    public static class Reinitialize implements Serializable {
        private static final long serialVersionUID = -8599505901326886264L;
        
    }
}
