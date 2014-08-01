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
package org.vortikal.repository.index;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.Fields;

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
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.Path;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.PropertySetImpl;
import org.vortikal.repository.index.mapping.DocumentMapper;
import org.vortikal.repository.index.mapping.DocumentMappingException;
import org.vortikal.repository.index.mapping.FieldNames;
import org.vortikal.security.Principal;

/**
 * <code>PropertySet</code> index using Lucene.
 */
public class PropertySetIndexImpl implements PropertySetIndex {
   
    Log logger = LogFactory.getLog(PropertySetIndexImpl.class);

    private Lucene4IndexManager index; 
    private DocumentMapper documentMapper;

    @SuppressWarnings("unchecked")
    @Override
    public void addPropertySet(PropertySet propertySet,
                               Set<Principal> aclReadPrincipals) throws IndexException {

        // NOTE: Write-locking should be done above this level.
        // This is needed to ensure the possibility of efficiently batching
        // together operations without interruption.
        try {
            Document doc = this.documentMapper.getDocument((PropertySetImpl) propertySet, aclReadPrincipals);
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
    public void updatePropertySet(PropertySet propertySet,
                                  Set<Principal> aclReadPrincipals) throws IndexException {
        
        try {
            Term uriTerm = new Term(FieldNames.URI_FIELD_NAME, 
                                        propertySet.getURI().toString());

            IndexWriter writer = this.index.getIndexWriter();
            
            writer.deleteDocuments(uriTerm);
            
            addPropertySet(propertySet, aclReadPrincipals);
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }

    @Override
    public void deletePropertySetTree(Path rootUri) throws IndexException {

        try {
            IndexWriter writer = this.index.getIndexWriter();
            writer.deleteDocuments(new Term(FieldNames.URI_FIELD_NAME, rootUri.toString()), 
                                   new Term(FieldNames.URI_ANCESTORS_FIELD_NAME, rootUri.toString()));

        } catch (IOException io) {
            throw new IndexException(io);
        }
    }

    @Override
    public void deletePropertySet(Path uri) throws IndexException {
        try {
            Term uriTerm = new Term(FieldNames.URI_FIELD_NAME, uri.toString());
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
                Terms terms = ar.terms(FieldNames.URI_FIELD_NAME);
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
        
// Old Lucene3-code:        
//        TermEnum termEnum = null;
//        TermDocs termDocs = null;
//        int count = 0;
//
//        try {
//            IndexReader reader = this.indexAccessor.getIndexReader();
//            Term start = new Term(FieldNames.URI_FIELD_NAME, "");
//            String enumField = start.field();
//            termEnum = reader.terms(start);
//            termDocs = reader.termDocs(start);
//
//            while (termEnum.term() != null
//                    && termEnum.term().field() == enumField) { // Interned String comparison
//                termDocs.seek(termEnum);
//                while (termDocs.next()) {
//                    ++count;
//                }
//                termEnum.next();
//            }
//        } catch (IOException io) {
//            throw new IndexException(io);
//        } finally {
//            try {
//                termEnum.close();
//                termDocs.close();
//            } catch (IOException io) {
//            }
//        }
//
//        return count;
    }

    @Override
    public void clear() throws IndexException {
        try {
            this.index.close();
            this.index.open(true);
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
    public Iterator orderedUriIterator() throws IndexException {
        try {
            return new PropertySet4IndexUriIterator(index);
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    @SuppressWarnings("unchecked")
    @Override
    public void close(Iterator iterator) throws IndexException {
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
        try {
            this.index.commit();
        } catch (IOException io) {
            throw new IndexException(io);
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
            this.index.close();
            this.index.open();
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    @Override
    public void addIndexContents(PropertySetIndex propSetIndex) throws IndexException {
        if (!(propSetIndex instanceof PropertySetIndexImpl)) {
            throw new IllegalArgumentException(
                    "Only 'org.vortikal.repository.query.PropertySetIndexImpl' instances are supported.");
        }

        try {
            PropertySetIndexImpl indexImpl = (PropertySetIndexImpl) propSetIndex;
            Lucene4IndexManager otherIndex = indexImpl.index;

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
    public void setIndexAccessor(Lucene4IndexManager index) {
        this.index = index;
    }


}