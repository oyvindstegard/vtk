/* Copyright (c) 2017, University of Oslo, Norway
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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;

/**
 * Temporary disk based index for an ordered set of unique string values.
 *
 * <p>
 * Index can be used to check for value existence and iteration of all values
 * in lexicographic order.
 *
 * <p>
 * It can handle a very large set of values (depending on available
 * disk space) without consuming huge amounts of memory, which would be the case
 * if using e.g. an in-memory TreeSet instead. It will perform slower than
 * a regular in-memory structure, but that may not always be a big problem.
 *
 * <p>
 * There is a limitation on the size of the string values that can be added,
 * which is an internal limit in Lucene. This limit is:
 * {@link IndexWriter#MAX_TERM_LENGTH} and applies the the UTF-8 representation
 * of the string value. You should probably avoid using this class for such large
 * string values.
 *
 * <p>
 * Instances of this class are not intended for use by multiple threads.
 */
public class OrderedIndexSet implements Iterable<String>, Closeable {

    private static final String FIELD = "VALUE";

    private final IndexWriter writer;
    private final Directory directory;
    private DirectoryReader reader;

    private final java.nio.file.Path indexDir;
    private boolean closed = false;

    /**
     * Creates a new ordered index set with storage in some directory.
     *
     * <p>Should not be shared amongst multiple threads.
     *
     * @param baseDirectory base directory under which a temporary index directory
     * will be created
     * @throws IOException
     */
    public OrderedIndexSet(File baseDirectory) throws IOException {
        indexDir = Files.createTempDirectory(baseDirectory.toPath(), "vtk.IndexSet_");
        IndexWriterConfig iwc = new IndexWriterConfig(Version.LATEST, new KeywordAnalyzer());
        iwc.setOpenMode(OpenMode.CREATE);
        iwc.setRAMBufferSizeMB(1f);
        iwc.setMergeScheduler(new SerialMergeScheduler());
        directory = FSDirectory.open(indexDir.toFile());
        writer = new IndexWriter(directory, iwc);
        writer.commit();
        reader = DirectoryReader.open(directory);
    }

    /**
     * Adds a value to index set.
     *
     * <p>
     * If value already exists, then set of values in index will not change.
     *
     * <p>Change is not visible until after call to {@link #commit() }.
     *
     * @param value
     * @throws IOException
     */
    public void add(String value) throws IOException {
        Document doc = new Document();
        doc.add(new StringField(FIELD, value, Store.NO));
        writer.updateDocument(new Term(FIELD, value), doc);
    }

    /**
     * Adds all values present in collection to index set.
     *
     * <p>
     * Any duplicates values are consoldiated to single unique value.
     *
     * <p>Change is not visible until after call to {@link #commit() }.
     *
     * @param values
     * @throws IOException
     */
    public void addAll(Collection<String> values) throws IOException {
        for (String value: values) {
            add(value);
        }
    }

    /**
     * Removes a value from index set.
     *
     * <p>Change is not visible until after call to {@link #commit() }.
     *
     * @param value
     * @throws IOException
     */
    public void remove(String value) throws IOException {
        writer.deleteDocuments(new Term(FIELD, new BytesRef(value)));
    }

    /**
     * Removes all values from index set.
     *
     * <p>Change is not visible until after call to {@link #commit() }.
     * @throws IOException
     */
    public void clear() throws IOException {
        writer.deleteAll();
    }

    /**
     * @return <code>true</code> if the expression {@code size() == 0 } is <code>true</code>
     * @throws IOException
     */
    public boolean isEmpty() throws IOException {
        return size() == 0;
    }

    /**
     * Return number of unique values in index.
     *
     * @return
     * @throws IOException
     */
    public int size() throws IOException {
        return reader.numDocs();
    }

    /**
     * Test if index contains a value.
     *
     * @param value
     * @return <code>true</code> if the provided value exists in the index
     * @throws IOException
     */
    public boolean contains(String value) throws IOException {
        // Have to jump through some hoops to take deleted docs into account when going low level
        TermsEnum te = null;
        DocsEnum de = null;
        for(AtomicReaderContext ctx: reader.leaves()) {
            AtomicReader segmentReader = ctx.reader();
            Terms terms = segmentReader.terms(FIELD);
            if (terms == null) {
                continue;
            }
            te = terms.iterator(te);
            if (!te.seekExact(new BytesRef(value))) {
                continue;
            }
            de = te.docs(segmentReader.getLiveDocs(), de, DocsEnum.FLAG_NONE);
            return de.nextDoc() != DocIdSetIterator.NO_MORE_DOCS;
        }

        return false;
    }

    /**
     * Commit must be called after adding valeus to make those values available
     * in the following operations:
     * <ul>
     *  <li>{@link #contains(java.lang.String) }
     *  <li>{@link #size() }
     *  <li>{@link #iterator() }
     * </ul>
     *
     * @throws IOException
     */
    public void commit() throws IOException {
        writer.commit();
        DirectoryReader newReader = DirectoryReader.openIfChanged(reader);
        if (newReader != null) {
            reader.close();
            reader = newReader;
        }
    }

    /**
     * Should be called to free resources after instance is no longer in use.
     *
     * <p>
     * This will destroy all added values and delete the temporary index directory.
     *
     * <p>Calling any other method on instance after this method has been called
     * will give undefined results and most likely throw IOExceptions.
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        if (closed) return;
        try {
            reader.close();
            writer.close();
            directory.close();
            // We assume Lucene index format does not use any sub-directories of its own
            for (File indexFile : indexDir.toFile().listFiles()) {
                if (indexFile.isFile()) {
                    indexFile.delete();
                }
            }
            Files.delete(indexDir);
        } finally {
            closed = true;
        }
    }

    /**
     * Get the temporary directory in which this index is stored.
     *
     * <p>This directory is deleted when index is closed.
     */
    File indexDir() {
        return indexDir.toFile();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }


    @Override
    public Iterator<String> iterator() {
        try {
            return new ValueIterator(reader);
        } catch (IOException io) {
            throw new RuntimeException("IOException while creating iterator", io);
        }
    }

    private final class ValueIterator implements Iterator<String> {

        private final AtomicReader compositeReader;
        private TermsEnum te;
        private DocsEnum de;
        private String next;

        ValueIterator(IndexReader reader) throws IOException {
            compositeReader = SlowCompositeReaderWrapper.wrap(reader);
            next = nextValue();
        }

        private String nextValue() throws IOException {
            if (te == null) {
                Terms terms = compositeReader.terms(FIELD);
                if (terms == null) {
                    return null;
                }
                te = terms.iterator(null);
                if (te.next() == null) {
                    return null;
                }
            }
            if (de == null) {
                de = te.docs(compositeReader.getLiveDocs(), de, DocsEnum.FLAG_NONE);
            }
            while (de.nextDoc() == DocIdSetIterator.NO_MORE_DOCS) {
                if (te.next() == null) {
                    return null;
                } else {
                    de = te.docs(compositeReader.getLiveDocs(), de, DocsEnum.FLAG_NONE);
                }
            }

            return te.term().utf8ToString();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public String next() {
            if (next == null) {
                throw new NoSuchElementException("No more elements");
            }

            String retVal = next;

            // Advance to next
            try {
                next = nextValue();
            } catch (IOException io) {
                next = null;
            }

            // Return current
            return retVal;
        }
    }
}
