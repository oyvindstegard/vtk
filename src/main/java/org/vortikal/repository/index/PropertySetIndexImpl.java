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
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.store.Directory;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.Path;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.PropertySetImpl;
import org.vortikal.repository.index.mapping.DocumentMapper;
import org.vortikal.repository.index.mapping.DocumentMappingException;
import org.vortikal.repository.index.mapping.FieldNameMapping;
import org.vortikal.security.Principal;

/**
 * <code>PropertySet</code> index using Lucene.
 */
public class PropertySetIndexImpl implements PropertySetIndex {

    Log logger = LogFactory.getLog(PropertySetIndexImpl.class);

    private LuceneIndexManager indexAccessor; // Underlying Lucene index
                                              // accessor.
    private DocumentMapper documentMapper;

    public void addPropertySet(PropertySet propertySet,
                               Set<Principal> aclReadPrincipals) throws IndexException {

        
        Document doc = null;
        // NOTE: Write-locking should be done above this level.
        // This is needed to ensure the possibility of efficiently batching
        // together operations without interruption.
        try {
            doc = this.documentMapper.getDocument((PropertySetImpl) propertySet, aclReadPrincipals);

            if (logger.isDebugEnabled()) {
                logger.debug("Adding new property set at URI '" + propertySet.getURI() + "'");
                logger.debug("Document mapper created the following document: ");

                for (Iterator<Field> iterator = doc.getFields().iterator(); iterator.hasNext();) {
                    Field field = iterator.next();
                    if (field.isBinary()) {
                        logger.debug("Field '" + field.name() + "', value: [BINARY]");
                    } else {
                        logger.debug("Field '" + field.name() + "', value: '" + field.stringValue()
                                + "'");
                    }
                }
            }

            this.indexAccessor.getIndexWriter().addDocument(doc);
        } catch (DocumentMappingException dme) {
            logger.warn("Could not map property set to index document", dme);
            throw new IndexException("Could not map property set to index document", dme);
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    public void deletePropertySetTreeByUUID(String rootUuid) throws IndexException {
        try {
            IndexWriter writer = this.indexAccessor.getIndexWriter();

            if (logger.isDebugEnabled()) {
                logger.debug("Deleting property set tree with root ID '" + rootUuid + "'");
            }

            writer.deleteDocuments(new Term(FieldNameMapping.ID_FIELD_NAME, rootUuid));

            writer.deleteDocuments(new Term(FieldNameMapping.ANCESTORIDS_FIELD_NAME, rootUuid));

        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    public int deletePropertySetTree(Path rootUri) throws IndexException {

        TermEnum tenum = null;
        TermDocs tdocs = null;
        try {

            IndexReader reader = this.indexAccessor.getIndexReader();
            Term rootUriTerm = new Term(FieldNameMapping.URI_FIELD_NAME, rootUri.toString());
            String fieldName = rootUriTerm.field();
            tenum = reader.terms(rootUriTerm);
            tdocs = reader.termDocs();

            int n = 0;

            if (logger.isDebugEnabled()) {
                logger.debug("Deleting property set tree with root URI '" + rootUri + "'");
            }

            do {
                Term term = tenum.term();
                if (term != null && term.field() == fieldName
                        && term.text().startsWith(rootUri.toString())) {

                    tdocs.seek(tenum);

                    while (tdocs.next()) {
                        reader.deleteDocument(tdocs.doc());
                        ++n;
                    }
                } else
                    break;

            } while (tenum.next());

            if (n == 0) {
                logger.warn("Consistency warning: zero index documents deleted"
                        + " for tree with root URI '" + rootUri + "'");
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Deleted " + n + " index documents.");
            }

            return n;
        } catch (IOException io) {
            throw new IndexException(io);
        } finally {
            try {
                if (tenum != null)
                    tenum.close();
                if (tdocs != null)
                    tdocs.close();
            } catch (IOException io) {
            }
        }
    }


    public void deletePropertySet(Path uri) throws IndexException {
        try {
            Term uriTerm = new Term(FieldNameMapping.URI_FIELD_NAME, uri.toString());
            IndexWriter writer = this.indexAccessor.getIndexWriter();

            if (logger.isDebugEnabled()) {
                logger.debug("Deleting property set at URI '" + uri + "' from index.");
            }

            writer.deleteDocuments(uriTerm);
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    public void deletePropertySetByUUID(String uuid) throws IndexException {
        try {
            Term uuidTerm = new Term(FieldNameMapping.ID_FIELD_NAME, uuid);
            IndexWriter writer = this.indexAccessor.getIndexWriter();

            if (logger.isDebugEnabled()) {
                logger.debug("Deleting property set with ID '" + uuid + "'");
            }

            writer.deleteDocuments(uuidTerm);
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    public int countAllInstances() throws IndexException {
        TermEnum termEnum = null;
        TermDocs termDocs = null;
        int count = 0;

        try {
            IndexReader reader = this.indexAccessor.getIndexReader();
            Term start = new Term(FieldNameMapping.URI_FIELD_NAME, "");
            String enumField = start.field();
            termEnum = reader.terms(start);
            termDocs = reader.termDocs(start);

            while (termEnum.term() != null && termEnum.term().field() == enumField) {
                termDocs.seek(termEnum);
                while (termDocs.next()) {
                    ++count;
                }
                termEnum.next();
            }
        } catch (IOException io) {
            throw new IndexException(io);
        } finally {
            try {
                termEnum.close();
                termDocs.close();
            } catch (IOException io) {
            }
        }

        return count;
    }

    public void clearContents() throws IndexException {
        try {
            this.indexAccessor.clearContents();
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    public PropertySetIndexRandomAccessor randomAccessor() throws IndexException {
        PropertySetIndexRandomAccessor accessor = null;

        try {
            accessor = new PropertySetIndexRandomAccessorImpl(this.indexAccessor.getIndexReader(),
                    this.documentMapper);
        } catch (IOException io) {
            throw new IndexException(io);
        }

        return accessor;
    }


    @SuppressWarnings("unchecked")
    public Iterator propertySetIterator() throws IndexException {
        try {
            return new PropertySetIndexUnorderedIterator(this.indexAccessor.getIndexReader(),
                    this.documentMapper);
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    @SuppressWarnings("unchecked")
    public Iterator orderedPropertySetIterator() throws IndexException {
        try {
            return new PropertySetIndexIterator(this.indexAccessor.getIndexReader(),
                    this.documentMapper);
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    @SuppressWarnings("unchecked")
    public Iterator orderedSubtreePropertySetIterator(Path rootUri) throws IndexException {
        try {
            return new PropertySetIndexSubtreeIterator(this.indexAccessor.getIndexReader(),
                    this.documentMapper, rootUri.toString());
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    @SuppressWarnings("unchecked")
    public Iterator orderedUriIterator() throws IndexException {
        try {
            return new PropertySetIndexUriIterator(this.indexAccessor.getIndexReader());
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    @SuppressWarnings("unchecked")
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


    public boolean isClosed() {
        return this.indexAccessor.isClosed();
    }


    public void commit() throws IndexException {
        try {
            this.indexAccessor.commit();
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    public void close() throws IndexException {
        try {
            logger.info("Closing index ..");
            this.indexAccessor.close();
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    public void reinitialize() throws IndexException {
        try {
            logger.info("Re-initializing index ..");
            this.indexAccessor.reinitialize();
        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    public void addIndexContents(PropertySetIndex index) throws IndexException {
        if (!(index instanceof PropertySetIndexImpl)) {
            throw new IllegalArgumentException(
                    "Only 'org.vortikal.repository.query.PropertySetIndexImpl' instances are supported.");
        }

        try {
            PropertySetIndexImpl indexImpl = (PropertySetIndexImpl) index;

            LuceneIndexManager accessor = indexImpl.indexAccessor;

            Directory[] dirs = { accessor.getIndexReader().directory() };

            if (logger.isDebugEnabled()) {
                logger.debug("Adding all contents of index '" + indexImpl.getId() + "' to '"
                        + this.getId() + "' (this index)");
            }
            
            IndexWriter indexWriter = this.indexAccessor.getIndexWriter();
            indexWriter.addIndexesNoOptimize(dirs);
            
            if (logger.isDebugEnabled()){
                logger.debug("Optimizing index ..");
            }
            indexWriter.optimize();

        } catch (IOException io) {
            throw new IndexException(io);
        }
    }


    public void validateStorageFacility() throws StorageCorruptionException {
        try {
            this.indexAccessor.corruptionTest();
        } catch (IOException io) {
            String storagePath = this.indexAccessor.getStorageRootPath() + "/"
                    + this.indexAccessor.getStorageId();

            throw new StorageCorruptionException(
                    "Possible Lucene index corruption detected (storage path = '" + storagePath
                            + "'): ", io);
        }
    }


    public void optimize() throws IndexException {
        try {
            this.indexAccessor.optimize();
        } catch (IOException io) {
            throw new IndexException("IOException while optimizing", io);
        }
    }


    public boolean lock() {
        return this.indexAccessor.writeLockAcquire();
    }


    public void unlock() throws IndexException {
        this.indexAccessor.writeLockRelease();
    }


    public boolean lock(long timeout) {
        return this.indexAccessor.writeLockAttempt(timeout);
    }


    @Required
    public void setDocumentMapper(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }


    @Required
    public void setIndexAccessor(LuceneIndexManager indexAccessor) {
        this.indexAccessor = indexAccessor;
    }


    public String getId() {
        // Delegate to using accessor storage ID
        return this.indexAccessor.getStorageId();
    }

}