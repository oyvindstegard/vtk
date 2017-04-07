/* Copyright (c) 2009-2017, University of Oslo, Norway
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
package vtk.repository.search;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DocumentStoredFieldVisitor;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfo.DocValuesType;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiDocValues;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Acl;
import vtk.repository.PropertySet;
import vtk.repository.index.IndexManager;
import vtk.repository.index.mapping.DocumentMapper;
import vtk.repository.index.mapping.LazyMappedPropertySet;
import vtk.repository.index.mapping.ResultSetWithAcls;
import vtk.repository.search.query.DumpQueryTreeVisitor;
import vtk.repository.search.query.LuceneQueryBuilder;
import vtk.repository.search.query.Query;

/**
 * Implementation of {@link vtk.repository.search.Searcher} based on Lucene.
 */
public class LuceneSearcher implements Searcher, InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(LuceneSearcher.class);

    private IndexManager indexAccessor;
    private DocumentMapper documentMapper;
    private LuceneQueryBuilder queryBuilder;

    private int resultCacheSize = LuceneResultCache.DEFAULT_MAX_ITEMS;
    private LuceneResultCache resultCache;

    private long totalQueryTimeWarnThreshold = 15000; // Warning threshold in milliseconds

    /**
     * The internal maximum number of hits allowed for any query <em>before</em>
     * processing of the results by layers above Lucene. A
     * <code>ResultSet</code> set will never be larger than this, no matter what
     * client code requests.
     *
     * {@link #iterateMatching(java.lang.String, vtk.repository.search.Search, vtk.repository.search.Searcher.MatchCallback) Iteration-style matching API}
     * is <em>not</em> affected by this limit.
     */
    private int luceneSearchLimit = 60000;

    @Override
    public void afterPropertiesSet() throws BeanInitializationException {
        if (luceneSearchLimit <= 0) {
            throw new BeanInitializationException(
                    "Property 'luceneSearchLimit' must be an integer greater than zero.");
        }
        if (resultCacheSize > 0) {
            resultCache = new LuceneResultCache(resultCacheSize);
        }
    }

    @Override
    public ResultSet execute(String token, Search search) throws QueryException {
        final Query query = search.getQuery();
        final Sorting sorting = search.getSorting();
        final int clientLimit = search.getLimit();
        final int clientCursor = search.getCursor();
        final PropertySelect selectedProperties = search.getPropertySelect();

        IndexSearcher searcher = null;
        try {

            searcher = this.indexAccessor.getIndexSearcher();

            // Build Lucene query
            org.apache.lucene.search.Query luceneQuery
                    = this.queryBuilder.buildQuery(query, searcher);

            // Should include ACL filter combined with any other necessary filters ..
            org.apache.lucene.search.Filter luceneFilter
                    = this.queryBuilder.buildSearchFilter(token, search, searcher);

            // Build Lucene sorting
            org.apache.lucene.search.Sort luceneSort
                    = this.queryBuilder.buildSort(sorting);

            if (logger.isDebugEnabled()) {
                logger.debug("Built Lucene query '" + luceneQuery
                        + "' from query '"
                        + query.accept(new DumpQueryTreeVisitor(), null) + "'");

                logger.debug("Built Lucene sorting '" + luceneSort
                        + "' from sorting '" + sorting + "'");

                logger.debug("Built Lucene filter: " + luceneFilter);
            }

            if (clientLimit <= 0) {
                // Client is not interested in actual search results, just provide total hits
                int totalHits = countQueryTotalHits(searcher, luceneQuery, luceneFilter);
                ResultSetImpl rs = new ResultSetImpl(0);
                rs.setTotalHits(totalHits);
                return rs;
            }

            int need = clientCursor + clientLimit;
            int searchLimit = Math.min(this.luceneSearchLimit, need);

            long totalTime = 0, startTime, endTime;
            TopDocs topDocs;
            startTime = System.currentTimeMillis();
            // Use result caching only for anon searches for now.
            // It will also work for authenticated queries, but a larger cache 
            // size will then likely be necessary to counteract more varations in filters.
            if (token == null && resultCache != null) {
                topDocs = resultCache.doCachedTopDocsQuery(searcher, luceneQuery, luceneFilter, luceneSort, searchLimit);
            } else {
                topDocs = doTopDocsQuery(searcher, luceneQuery, luceneFilter, luceneSort, searchLimit);
            }
            endTime = System.currentTimeMillis();
            if (logger.isDebugEnabled()) {
                logger.debug("Filtered lucene query took " + (endTime - startTime) + "ms");
                if (resultCache != null) {
                    logger.debug("Result cache hit ratio: " + resultCache.hitRatio());
                }
            }

            totalTime += (endTime - startTime);

            ScoreDoc[] scoreDocs = topDocs.scoreDocs;

            ResultSetWithAcls rs;
            if (clientCursor < scoreDocs.length) {
                int end = Math.min(need, scoreDocs.length);
                rs = new ResultSetWithAcls(end - clientCursor);

                startTime = System.currentTimeMillis();
                for (int i = clientCursor; i < end; i++) {
                    DocumentStoredFieldVisitor fieldVisitor
                            = documentMapper.newStoredFieldVisitor(selectedProperties);
                    searcher.doc(scoreDocs[i].doc, fieldVisitor);
                    Document doc = fieldVisitor.getDocument();
                    LazyMappedPropertySet propSet = this.documentMapper.getPropertySet(doc);
                    rs.addResult(propSet);
                }
                endTime = System.currentTimeMillis();

                totalTime += (endTime - startTime);

                if (logger.isDebugEnabled()) {
                    logger.debug("Document mapping took " + (endTime - startTime) + "ms");
                }
            } else {
                rs = new ResultSetWithAcls(0);
            }
            rs.setTotalHits(topDocs.totalHits);

            if (totalTime > this.totalQueryTimeWarnThreshold) {
                // Logger a warning, query took too long to complete.
                StringBuilder msg
                        = new StringBuilder("Total execution time for Lucene query '");
                msg.append(luceneQuery).append("'");
                msg.append(", with a search limit of ").append(searchLimit);
                msg.append(", was ").append(totalTime).append("ms. ");
                msg.append("This exceeds the warning threshold of ");
                msg.append(this.totalQueryTimeWarnThreshold).append("ms");
                logger.warn(msg.toString());
            }

            return rs;

        } catch (IOException io) {
            logger.warn("IOException while performing query on index", io);
            throw new QueryException("IOException while performing query on index", io);
        } finally {
            try {
                this.indexAccessor.releaseIndexSearcher(searcher);
            } catch (IOException io) {
                logger.warn("IOException while releasing index searcher", io);
            }
        }
    }

    /**
     * Execute regular query finding top-N docs with or without a specific result set sorting.
     * @param searcher
     * @param query
     * @param filter
     * @param sort
     * @param limit
     * @return
     * @throws IOException
     */
    private TopDocs doTopDocsQuery(IndexSearcher searcher,
            org.apache.lucene.search.Query query,
            org.apache.lucene.search.Filter filter,
            org.apache.lucene.search.Sort sort,
            int limit)
            throws IOException {

        if (sort != null) {
            return searcher.search(query, filter, limit, sort);
        }

        return searcher.search(query, filter, limit);
    }

    /**
     * Just count number of documents matching provided query and filter.
     * @param searcher
     * @param query
     * @param filter
     * @return
     * @throws IOException
     */
    private int countQueryTotalHits(IndexSearcher searcher,
            org.apache.lucene.search.Query query,
            org.apache.lucene.search.Filter filter) throws IOException {

        TotalHitCountCollector collector = new TotalHitCountCollector();
        searcher.search(query, filter, collector);
        return collector.getTotalHits();
    }

    @Override
    public void iterateMatching(String token, Search search, MatchCallback callback) throws QueryException {

        if (search.getLimit() <= 0) {
            return;  // Nothing to do
        }

        IndexSearcher searcher = null;
        try {
            searcher = this.indexAccessor.getIndexSearcher();

            // Build iteration filter (may be null)
            Filter iterationFilter = this.queryBuilder.buildIterationFilter(
                    token, search, searcher);

            // Build Lucene sorting (may be null)
            org.apache.lucene.search.Sort luceneSort
                    = this.queryBuilder.buildSort(search.getSorting());

            if (luceneSort != null) {
                org.apache.lucene.search.SortField[] sf = luceneSort.getSort();
                if (sf.length != 1) {
                    throw new UnsupportedOperationException("Only ONE sort field supported for iteration: " + search.getSorting());
                }

                DocValuesType dvt = docValuesType(sf[0].getField(), searcher.getIndexReader());
                if (dvt != null) {
                    switch (dvt) {
                        case SORTED:
                            iterateOnSortedDocValuesField(sf[0],
                                    iterationFilter,
                                    searcher.getIndexReader(),
                                    search.getPropertySelect(), search.getCursor(), search.getLimit(),
                                    callback);
                            return;
                        case NUMERIC:
                            iterateOnNumericDocValuesField(sf[0],
                                    iterationFilter,
                                    searcher.getIndexReader(),
                                    search.getPropertySelect(), search.getCursor(), search.getLimit(),
                                    callback);
                            return;
                        default:
                            return; // Ordered iteration not supported for doc value type
                    }
                } else {
                    if (sf[0].getReverse()) {
                        throw new UnsupportedOperationException("Only ascending sort supported for iteration on field : " + search.getSorting());
                    }
                    // Ordered iteration on index field (for non-docvalue fields)
                    iterateOnIndexField(luceneSort.getSort()[0].getField(),
                            iterationFilter,
                            searcher.getIndexReader(),
                            search.getPropertySelect(), search.getCursor(), search.getLimit(),
                            callback);
                }
            } else {
                // Index doc order iteration (unordered)
                iterate(iterationFilter,
                        searcher.getIndexReader(),
                        search.getPropertySelect(), search.getCursor(), search.getLimit(),
                        callback);
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new QueryException("Exception while performing match iteration on index", e);
        } finally {
            try {
                this.indexAccessor.releaseIndexSearcher(searcher);
            } catch (IOException io) {
                logger.warn("IOException while releasing index searcher", io);
            }
        }
    }

    private DocValuesType docValuesType(String field, IndexReader reader) {
        FieldInfos s = MultiFields.getMergedFieldInfos(reader);
        FieldInfo fieldInfo = s.fieldInfo(field);
        if (fieldInfo != null) {
            return fieldInfo.getDocValuesType();
        }
        return null;
    }

    /**
     * Iterator all docs matching filter in index document order. Filter may be
     * <code>null></code>, in which case all non-deleted docs are iterated.
     */
    private void iterate(org.apache.lucene.search.Filter iterationFilter,
            IndexReader reader,
            PropertySelect propertySelect,
            int cursor,
            int limit,
            MatchCallback callback) throws Exception {

        int matchDocCounter = 0;
        int callbackCounter = 0;
        for (AtomicReaderContext arc : reader.leaves()) {
            final AtomicReader segment = arc.reader();
            final Bits liveDocs = segment.getLiveDocs();
            if (iterationFilter != null) {
                DocIdSet matchedDocs = iterationFilter.getDocIdSet(arc, liveDocs);
                if (matchedDocs != null) {
                    DocIdSetIterator disi = matchedDocs.iterator();
                    if (disi != null) {
                        int docId;
                        while ((docId = disi.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
                            if (matchDocCounter++ < cursor) {
                                continue;
                            }

                            DocumentStoredFieldVisitor visitor = documentMapper.newStoredFieldVisitor(propertySelect);
                            segment.document(docId, visitor);
                            LazyMappedPropertySet ps = documentMapper.getPropertySet(visitor.getDocument());
                            boolean continueIteration = callback.matching(new MatchingResultImpl(ps, ps.getAcl()));
                            if (++callbackCounter == limit || !continueIteration) {
                                return;
                            }
                        }
                    }
                }
            } else { // No iteration filter
                for (int i = 0; i < segment.maxDoc(); i++) {
                    if (liveDocs != null && !liveDocs.get(i)) {
                        continue;
                    }
                    if (matchDocCounter++ < cursor) {
                        continue;
                    }
                    DocumentStoredFieldVisitor visitor = documentMapper.newStoredFieldVisitor(propertySelect);
                    segment.document(i, visitor);
                    LazyMappedPropertySet ps = documentMapper.getPropertySet(visitor.getDocument());
                    boolean continueIteration = callback.matching(new MatchingResultImpl(ps, ps.getAcl()));
                    if (++callbackCounter == limit || !continueIteration) {
                        return;
                    }
                }
            }
        }
    }

    // Iterating *without limits* on number of docs *and* requiring globally sorted order
    // (unlimited "sorted scrolling")
    // is somewhat in conflict with the Lucene segment model, where each segment
    // is completely independent and has its own doc-id and ordinal spaces. So
    // a global merge/sort in memory is necessary, because a pre-sorted global view simply
    // does not exist. This method may use considerable amounts of memory depending on number
    // of docs in index and number of docs matching query.
    private void iterateOnNumericDocValuesField(org.apache.lucene.search.SortField sortField,
            org.apache.lucene.search.Filter iterationFilter,
            IndexReader reader,
            PropertySelect propertySelect,
            int cursor,
            int limit,
            MatchCallback callback) throws Exception {

        final int reverseMultiplier = sortField.getReverse() ? -1 : 1;

        final class DocNumber implements Comparable<DocNumber> {
            final int docId;
            final long n;
            DocNumber(int docId, long n) {
                this.docId = docId;
                this.n = n;
            }
            @Override
            public int compareTo(DocNumber o) {
                int cmp = n < o.n ? -1 : (n > o.n ? 1 : 0);
                if (cmp == 0) {
                    cmp = docId < o.docId ? -1 : (docId > o.docId ? 1 : 0);
                }
                return cmp * reverseMultiplier;
            }
        }

        int docCount = reader.getDocCount(sortField.getField());
        final ArrayList<DocNumber> globalDocList = new ArrayList<>(Math.min(500000,docCount > 0 ? docCount : 500000));
        for (AtomicReaderContext arc : reader.leaves()) {
            AtomicReader segment = arc.reader();
            final NumericDocValues ndv = segment.getNumericDocValues(sortField.getField());
            if (iterationFilter != null) {
                DocIdSet matched = iterationFilter.getDocIdSet(arc, segment.getLiveDocs());
                if (matched != null) {
                    DocIdSetIterator disi = matched.iterator();
                    if (disi != null) {
                        int segmentDocId;
                        while ((segmentDocId = disi.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
                            long n = ndv != null ? ndv.get(segmentDocId) : -1;
                            globalDocList.add(new DocNumber(arc.docBase + segmentDocId, n));
                        }
                    }
                }
            } else {
                Bits liveDocs = segment.getLiveDocs();
                final int maxDoc = segment.maxDoc();
                for (int segmentDocId = 0; segmentDocId < maxDoc; segmentDocId++) {
                    if (liveDocs != null && !liveDocs.get(segmentDocId)) {
                        continue;
                    }
                    long n = ndv != null ? ndv.get(segmentDocId) : -1;
                    globalDocList.add(new DocNumber(arc.docBase + segmentDocId, n));
                }
            }
        }

        globalDocList.sort(null);

        int callbackCounter = 0;
        for (int i=0; i<globalDocList.size(); i++) {
            DocNumber doc = globalDocList.get(i);
            globalDocList.set(i, null); // help gc
            if (i < cursor) {
                continue;
            }

            DocumentStoredFieldVisitor visitor = documentMapper.newStoredFieldVisitor(propertySelect);
            reader.document(doc.docId, visitor);
            LazyMappedPropertySet ps = documentMapper.getPropertySet(visitor.getDocument());
            boolean continueIteration = callback.matching(new MatchingResultImpl(ps, ps.getAcl()));
            if (++callbackCounter == limit || !continueIteration) {
                return;
            }
        }
    }

    // Iterating *without limits* on number of docs *and* requiring globally sorted order (unlimited "sorted scrolling")
    // is somewhat in conflict with the Lucene segment model, where each segment
    // is completely independent and has its own doc-id and ordinal spaces. So
    // a global merge/sort in memory is necessary..
    private void iterateOnSortedDocValuesField(org.apache.lucene.search.SortField sortField,
            org.apache.lucene.search.Filter iterationFilter,
            IndexReader reader,
            PropertySelect propertySelect,
            int cursor,
            int limit,
            MatchCallback callback) throws Exception {

        // Slightly naive approach for global sorted iteration is implemented here. Could probably be done
        // more efficient with use of Lucene internals, but iterating is meant
        // for heavier queries where higher latency must be acceptable.
        final int reverseMultiplier = sortField.getReverse() ? -1 : 1;

        final class DocOrd implements Comparable<DocOrd> {
            final int docId;
            final int ord;
            DocOrd(int docId, int ord) {
                this.docId = docId;
                this.ord = ord;
            }
            @Override
            public int compareTo(DocOrd o) {
                int cmp = ord < o.ord ? -1 : (ord > o.ord ? 1 : 0);
                // Stabilize order with fallback for doc ids
                if (cmp == 0) {
                    cmp = docId < o.docId ? -1 : (docId > o.docId ? 1 : 0);
                }
                return cmp * reverseMultiplier;
            }
        }

        final int docCount = reader.getDocCount(sortField.getField());
        final ArrayList<DocOrd> globalDocList = new ArrayList<>(Math.min(500000, docCount > 0 ? docCount : 500000));
        SortedDocValues sdv = MultiDocValues.getSortedValues(reader, sortField.getField());  // Map from segment to global ords, this is costly
        for (AtomicReaderContext arc : reader.leaves()) {
            AtomicReader segment = arc.reader();

            if (iterationFilter != null) {
                DocIdSet matched = iterationFilter.getDocIdSet(arc, segment.getLiveDocs());
                if (matched != null) {
                    DocIdSetIterator disi = matched.iterator();
                    if (disi != null) {
                        int segmentDocId;
                        while ((segmentDocId = disi.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
                            int globalOrdinal = sdv != null ? sdv.getOrd(arc.docBase + segmentDocId) : -1;
                            globalDocList.add(new DocOrd(arc.docBase + segmentDocId, globalOrdinal));
                        }
                    }
                }
            } else {
                Bits liveDocs = segment.getLiveDocs();
                final int maxDoc = segment.maxDoc();
                for (int segmentDocId = 0; segmentDocId < maxDoc; segmentDocId++) {
                    if (liveDocs != null && !liveDocs.get(segmentDocId)) {
                        continue;
                    }
                    int ordinal = sdv != null ? sdv.getOrd(arc.docBase + segmentDocId) : -1;
                    globalDocList.add(new DocOrd(arc.docBase + segmentDocId, ordinal));
                }
            }
        }
        sdv = null; // help gc

        globalDocList.sort(null);

        int callbackCounter = 0;
        for (int i=0; i<globalDocList.size(); i++) {
            final DocOrd orderedDoc = globalDocList.get(i);
            globalDocList.set(i, null); // help gc
            if (i < cursor) {
                continue;
            }

            DocumentStoredFieldVisitor visitor = documentMapper.newStoredFieldVisitor(propertySelect);
            reader.document(orderedDoc.docId, visitor);
            LazyMappedPropertySet ps = documentMapper.getPropertySet(visitor.getDocument());
            boolean continueIteration = callback.matching(new MatchingResultImpl(ps, ps.getAcl()));
            if (++callbackCounter == limit || !continueIteration) {
                return;
            }
        }
    }

    /**
     * Iteration all docs with given field in lexicographic order. Filter may be
     * null.
     *
     * <p>For indexed fields without doc values. Iteration will not include
     * docs missing the field.
     */
    private void iterateOnIndexField(final String field,
            org.apache.lucene.search.Filter iterationFilter,
            IndexReader reader,
            PropertySelect propertySelect,
            int cursor,
            int limit,
            MatchCallback callback) throws Exception {

        final Bits acceptDocs = matchingLiveDocs(reader, iterationFilter);

        // We'll need global ordering on the ordinary index field values across all segments
        final Fields fields = MultiFields.getFields(reader);
        if (fields == null) {
            return;
        }

        Terms terms = fields.terms(field);
        if (terms == null) {
            return;
        }

        TermsEnum te = terms.iterator(null);

        int matchDocCounter = 0;
        int callbackCounter = 0;
        DocsEnum de = null;
        while (te.next() != null) {
            de = te.docs(acceptDocs, de, DocsEnum.FLAG_NONE);
            int docId;
            while ((docId = de.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
                if (matchDocCounter++ < cursor) {
                    continue;
                }
                DocumentStoredFieldVisitor visitor = documentMapper.newStoredFieldVisitor(propertySelect);
                reader.document(docId, visitor);
                LazyMappedPropertySet ps = documentMapper.getPropertySet(visitor.getDocument());
                boolean continueIteration = callback.matching(new MatchingResultImpl(ps, ps.getAcl()));
                if (++callbackCounter == limit || !continueIteration) {
                    return;
                }
            }
        }
    }

    // Get all docs matched by the filter (deleted docs are taken into account).
    // If filter is null, then all non-deleted docs will be present in returned bits.
    private Bits matchingLiveDocs(IndexReader reader, Filter filter) throws IOException {
        if (filter == null) {
            return MultiFields.getLiveDocs(reader);
        }

        final FixedBitSet fbs = new FixedBitSet(reader.maxDoc());

        for (AtomicReaderContext arc : reader.leaves()) {
            final AtomicReader segment = arc.reader();
            final Bits liveDocs = segment.getLiveDocs();
            final DocIdSet matchedDocs = filter.getDocIdSet(arc, liveDocs);
            if (matchedDocs != null) {
                DocIdSetIterator disi = matchedDocs.iterator();
                if (disi != null) {
                    int docId;
                    while ((docId = disi.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
                        fbs.set(docId + arc.docBase);
                    }
                }
            }
        }

        return fbs;
    }

    private static final class MatchingResultImpl implements MatchingResult {

        private final PropertySet ps;
        private final Acl acl;

        MatchingResultImpl(PropertySet ps, Acl acl) {
            this.ps = ps;
            this.acl = acl;
        }

        @Override
        public PropertySet propertySet() {
            return ps;
        }

        @Override
        public Acl acl() {
            return acl;
        }
    }

    @Required
    public void setDocumentMapper(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    @Required
    public void setIndexAccessor(IndexManager indexAccessor) {
        this.indexAccessor = indexAccessor;
    }

    @Required
    public void setQueryBuilder(LuceneQueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    public void setLuceneSearchLimit(int luceneSearchLimit) {
        this.luceneSearchLimit = luceneSearchLimit;
    }

    /**
     * Set size of internal search result cache. Default value is
     * {@link LuceneResultCache#DEFAULT_MAX_ITEMS}.
     *
     * <p>
     * The cache only applies to unauthenticated queries.
     *
     * @param maxItems maximum number of cached results, or -1 to disable result
     * caching.
     */
    public void setResultCacheSize(int maxItems) {
        this.resultCacheSize = maxItems;
    }

    public void setTotalQueryTimeWarnThreshold(long totalQueryTimeWarnThreshold) {
        if (totalQueryTimeWarnThreshold <= 0) {
            throw new IllegalArgumentException("Argument cannot be zero or negative");
        }

        this.totalQueryTimeWarnThreshold = totalQueryTimeWarnThreshold;
    }

}
