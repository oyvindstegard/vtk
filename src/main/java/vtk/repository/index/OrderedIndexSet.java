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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.SerialMergeScheduler;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;

/**
 * Disk based temporary index for an ordered set of unique string values.
 *
 * <p>
 * Index can be used to check for value existence and iteration of all values
 * in lexicographic order. (Strictly only Unicode ordering currently, with no support
 * for Locale-specific rules.)
 *
 * <p>
 * It can handle a very large set of values (depending on available
 * disk space) without consuming huge amounts of memory or suffering large
 * performance degradations. It will however perform many times slower than
 * a regular in-memory structure, but that may not always be a problem.
 *
 * <p>When building up an index set, make sure to use {@link #addValue(java.lang.String) }
 * instead of {@link #add(java.lang.String) } for best performance. The latter
 * method is only present so that this implementation can adhere 100% to the {@link Set} API.
 *
 * <p>
 * There is a limitation on the size of the string values that can be added,
 * which is an internal limit in Lucene. This limit is:
 * {@link IndexWriter#MAX_TERM_LENGTH} and applies the the UTF-8 representation
 * of the string value. You should obviously not use this class for string values
 * that are potentially larger than this limit.
 *
 * <p>XXX has issue with reopening for every modification, regardless of intermixing of public read calls.
 *
 * <p>
 * Instances of this class are <em>not thread safe</em> and should not be used by
 * multiple threads simultaneously.
 *
 */
public class OrderedIndexSet extends AbstractSet<String> implements Closeable {

    private static final String FIELD = "V";

    private final IndexWriter writer;
    private final Directory directory;
    private DirectoryReader reader;
    private IndexSearcher searcher;

    private final java.nio.file.Path indexDir;
    private boolean closed = false;
    private boolean readerOutdated = false;

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
        IndexWriterConfig iwc = new IndexWriterConfig(new KeywordAnalyzer())
                                        .setCommitOnClose(true)
                                        .setOpenMode(OpenMode.CREATE)
                                        .setRAMBufferSizeMB(1f)
                                        .setMergeScheduler(new SerialMergeScheduler());
        directory = FSDirectory.open(indexDir);
        writer = new IndexWriter(directory, iwc);
        writer.commit();
    }

    /**
     * Test if index contains a value.
     *
     * @param val the value
     * @return <code>true</code> if the provided value exists in the index
     * @throws UncheckedIOException in case an IOException occurs
     */
    @Override
    public boolean contains(Object val) {
        try {
            return containsTermValue(new BytesRef((String)val));
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    /**
     * Adds a value to index set without first testing if the value already
     * exists.
     *
     * <p>This operation is a lot less expensive than the {@link #add(java.lang.String) add method} that
     * is part of the {@link Set} API. Whenever possible, prefer to use this
     * method when building an indexed set.
     * 
     * @param val value to add
     * @throws UncheckedIOException in case an IOException occurs
     */
    public void addValue(String val) {
        try {
            addInternal(new BytesRef(val));
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    /**
     * Adds a value to index set.
     *
     * <p>
     * If value already exists, then set of values in index will not change.
     *
     * <p>This method is slow. Consider using the method {@link #addValue(java.lang.String) } instead, which
     * is a lot less expensive when adding many terms in sequence and you don't
     * care about knowing whethey they existed beforehand.
     *
     * <p>Another alternative is use {@link #addAll(java.util.Collection) }, which
     * is also much more efficient.
     *
     * @param val value to add
     * @return {@code true} if index changed as a result of adding the value, {@code false} otherwise.
     * @throws UncheckedIOException in case an IOException occurs
     */
    @Override
    public boolean add(String val) {
        final BytesRef termValue = new BytesRef(val);

        try {
            if (containsTermValue(termValue)) {
                return false;
            }

            addInternal(termValue);
            return true;
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    /**
     * Adds all values present in collection to index set.
     *
     * <p>
     * Any duplicates values are consoldiated to single unique value.
     *
     * @param values
     * @return {@code true} if the set was modified as a result of the added elements,
     * {@code false} otherwise (already containted all elements).
     * @throws UncheckedIOException in case an IOException occurs
     */
    @Override
    public boolean addAll(Collection<? extends String> values) {
        Collection<BytesRef> termValues = values.stream().map(
                v -> new BytesRef(v)).collect(Collectors.toList());
        try {
            if (countIntersectingTerms(termValues) == termValues.size()) {
                return false;
            }

            addInternal(termValues);
            return true;
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    /**
     * Removes a value from index set.
     *
     * <p>Change is not visible until after call to {@link #commit() }.
     *
     * @param value
     * @return {@code true} if element was removed and set was modified
     * @throws UncheckedIOException in case an IOException occurs
     */
    @Override
    public boolean remove(Object value) {
        BytesRef termValue = new BytesRef((String)value);
        try {
            if (!containsTermValue(termValue)) {
                return false;
            }

            deleteInternal(termValue);
            return true;
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    /**
     * Removes all values from index set.
     *
     * <p>Change is not visible until after call to {@link #commit() }.
     * @throws UncheckedIOException in case an IOException occurs
     */
    @Override
    public void clear()  {
        try {
            deleteAllInternal();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Return number of unique values in index.
     *
     * @return
     * @throws UncheckedIOException in case an IOException occurs
     */
    @Override
    public int size() {
        try {
            return numValuesInternal();
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    /**
     * Check if index contains all provided values.
     * @param c
     * @return {@code true} if index contains all provided values
     * @throws UncheckedIOException in case an IOException occurs
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        try {
            return countIntersectingTerms(c.stream().map(
                    v -> new BytesRef((String)v)).collect(Collectors.toList())) == c.size();
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    /**
     * Retains only the elements in this set that are contained in the
     * specified collection.
     *
     * @param c collection of values
     * @return {@code true} if index set changed as a result of this operation
     * @throws UncheckedIOException in case an IOException occurs
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        Collection<BytesRef> termValues = c.stream()
                .map(v -> new BytesRef((String)v)).collect(Collectors.toList());

        try {
            if (countIntersectingTerms(termValues) < numValuesInternal()) {
                deleteInternal(termValues, true);
                return true;
            }

            return false;
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    /**
     * Removes all values provided in a collection.
     * @param c collection of values to remove
     * @return {@code true} if the index changed as a result of this operation
     * @throws UncheckedIOException in case an IOException occurs
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        Collection<BytesRef> termValues = c.stream()
                .map(v -> new BytesRef((String)v)).collect(Collectors.toList());
        try {
            if (countIntersectingTerms(termValues) == 0) {
                return false;
            }

            deleteInternal(termValues, false);
            return true;
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    /**
     * Obtain a value iterator.
     *
     * <p>The iterator provides values in UTF-8 lexicographic order.
     *
     * @return an iterator over set values.
     */
    @Override
    public Iterator<String> iterator() {
        try {
            ensureUpdatedReader();
            return new ValueIterator(reader);
        } catch (IOException io) {
            throw new UncheckedIOException(io);
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
     * @throws UncheckedIOException in case an IOException occurs
     */
    @Override
    public void close() {
        if (closed) return;
        try {
            if (reader != null) {
                reader.close();
            }
            writer.close();
            directory.close();
            // We assume Lucene index format does not use any sub-directories of its own
            for (File indexFile : indexDir.toFile().listFiles()) {
                if (indexFile.isFile()) {
                    indexFile.delete();
                }
            }
            Files.delete(indexDir);
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        } finally {
            closed = true;
        }
    }

    private Iterable<IndexableField> toDocument(BytesRef termValue) {
        Document doc = new Document();
        doc.add(new StringField(FIELD, termValue, Store.NO));
        return doc;
    }

    private boolean containsTermValue(BytesRef termValue) throws IOException {
        ensureUpdatedReader();
        return searcher.count(new TermQuery(new Term(FIELD, termValue))) > 0;
    }

    private int countIntersectingTerms(Collection<BytesRef> termValues) throws IOException {
        ensureUpdatedReader();
        return searcher.count(new TermInSetQuery(FIELD, termValues));
    }

    private void deleteInternal(BytesRef termValue) throws IOException {
        writer.deleteDocuments(new Term(FIELD, termValue));
        indexModified();
    }

    private void deleteInternal(Collection<BytesRef> values, boolean invert) throws IOException {
        Query q = new TermInSetQuery(FIELD, values);
        if (invert) {
            q = new BooleanQuery.Builder()
                    .add(new MatchAllDocsQuery(), BooleanClause.Occur.FILTER)
                    .add(q, BooleanClause.Occur.MUST_NOT).build();
        }

        writer.deleteDocuments(q);
        indexModified();
    }

    private void deleteAllInternal() throws IOException {
        writer.deleteAll();
        indexModified();
    }

    private void addInternal(BytesRef termValue) throws IOException {
        writer.updateDocument(new Term(FIELD, termValue), toDocument(termValue));
        indexModified();
    }

    private void addInternal(Collection<BytesRef> termValues) throws IOException {
        writer.deleteDocuments(new TermInSetQuery(FIELD, termValues));
        writer.addDocuments(termValues.stream().map(this::toDocument)
                                           .collect(Collectors.toList()));
        indexModified();
    }

    private int numValuesInternal() throws IOException {
        ensureUpdatedReader();
        return reader.numDocs();
    }

    private void indexModified() {
        readerOutdated = true;
    }
    
    private void ensureUpdatedReader() throws IOException {
        if (reader == null) {
            reader = DirectoryReader.open(writer, true, false);
            searcher = new IndexSearcher(reader);
        } else {
            if (readerOutdated) {
                DirectoryReader newReader = DirectoryReader.openIfChanged(reader, writer, true);
                if (newReader != null && newReader != reader) {
                    reader.close();
                    reader = newReader;
                    searcher = new IndexSearcher(reader);
                }
                readerOutdated = false;
            }
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

    /**
     * Commit (access for test)
     */
    void commit() {
        try {
            writer.commit();
            indexModified();
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    private final class ValueIterator implements Iterator<String> {

        private final Terms terms;
        private final Bits liveDocs;
        private TermsEnum te = null;
        private PostingsEnum pe = null;
        private String next;

        ValueIterator(IndexReader reader) throws IOException {
            this.terms = MultiFields.getTerms(reader, FIELD);
            this.liveDocs = MultiFields.getLiveDocs(reader);
            this.next = nextValue();
        }

        private String nextValue() throws IOException {
            if (te == null) {
                if (terms == null) {
                    return null;
                }
                te = terms.iterator();
            }
            if (te.next() == null) {
                return null;
            }

            pe = te.postings(pe, PostingsEnum.NONE);
            while (! hasLiveDoc(pe, liveDocs)) {
                if (te.next() == null) {
                    return null;
                }
                pe = te.postings(pe, PostingsEnum.NONE);
            }

            return te.term().utf8ToString();
        }

        private boolean hasLiveDoc(PostingsEnum pe, Bits liveDocs) throws IOException {
            int docId;
            while ((docId = pe.nextDoc()) != PostingsEnum.NO_MORE_DOCS) {
                if (liveDocs == null || liveDocs.get(docId)) {
                    return true;
                }
            }
            return false;
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
