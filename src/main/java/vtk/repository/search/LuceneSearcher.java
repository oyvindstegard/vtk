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
import java.util.Optional;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DocumentStoredFieldVisitor;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import vtk.repository.PropertySet;

import vtk.repository.index.IndexManager;
import vtk.repository.index.mapping.DocumentMapper;
import vtk.repository.index.mapping.LazyMappedPropertySet;
import vtk.repository.search.query.ToStringVisitor;
import vtk.repository.search.query.LuceneQueryBuilder;
import vtk.repository.search.query.Query;

/**
 * Implementation of {@link vtk.repository.search.Searcher} based on Lucene.
 */
public class LuceneSearcher implements Searcher {

    private final Logger logger = LoggerFactory.getLogger(LuceneSearcher.class);

    private IndexManager indexAccessor;
    private DocumentMapper documentMapper;
    private LuceneQueryBuilder queryBuilder;

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
    public ResultSet execute(String token, Search search) throws QueryException {
        final Query query = search.getQuery();
        final Sorting sorting = search.getSorting();
        final int clientLimit = search.getLimit();
        final int clientCursor = search.getCursor();
        final PropertySelect selectedProperties = search.getPropertySelect();

        IndexSearcher searcher = null;
        try {

            searcher = indexAccessor.getIndexSearcher();

            org.apache.lucene.search.Query luceneQuery
                    = queryBuilder.buildQuery(query, searcher);

            logger.debug("Built user Lucene query '{}' from user VTK query:\n{}\n",
                            luceneQuery,
                            query != null ? query.accept(new ToStringVisitor(true), null) : "null");

            Optional<org.apache.lucene.search.Query> filterQuery =
                    queryBuilder.buildSearchFilterQuery(token, search);
            if (filterQuery.isPresent()) {
                luceneQuery = queryBuilder.combineQueryWithFilter(luceneQuery, filterQuery.get());
                logger.debug("Built Lucene filter query: '{}'", filterQuery.get());
            } else {
                logger.debug("No filter query for search.");
            }

            Optional<org.apache.lucene.search.Sort> luceneSort = queryBuilder.buildSort(sorting);
            if (luceneSort.isPresent()) {
                logger.debug("Built Lucene sorting '{}' from VTK sorting '{}'",
                      luceneSort.get(), sorting);
            } else {
                logger.debug("No sorting specified in search");
            }

            logger.debug("Combined and final Lucene query: '{}'", luceneQuery);

            if (clientLimit <= 0) {
                // Client is not interested in actual search results, just provide total hits
                int totalHits = searcher.count(luceneQuery);
                ResultSetImpl rs = new ResultSetImpl(0);
                rs.setTotalHits(totalHits);
                return rs;
            }

            int need = clientCursor + clientLimit;
            int searchLimit = Math.min(luceneSearchLimit, need);

            long totalTime = 0, startTime, endTime;
            startTime = System.currentTimeMillis();
            TopDocs topDocs = doTopDocsQuery(searcher, luceneQuery, luceneSort.orElse(null), searchLimit, null);
            endTime = System.currentTimeMillis();

            if (logger.isDebugEnabled()) {
                logger.debug("Lucene query took " + (endTime - startTime) + "ms");
            }

            totalTime += (endTime - startTime);

            ScoreDoc[] scoreDocs = topDocs.scoreDocs;

            ResultSetImpl rs;
            if (clientCursor < scoreDocs.length) {
                int end = Math.min(need, scoreDocs.length);
                rs = new ResultSetImpl(end - clientCursor);

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
                rs = new ResultSetImpl(0);
            }
            // XXX change total hits number in ResultSet from int to long
            rs.setTotalHits((int)topDocs.totalHits);

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
     * @param sort
     * @param limit
     * @param after find results after doc, only relevant when sorting results, may be {@code null}
     * @return
     * @throws IOException
     */
    private TopDocs doTopDocsQuery(IndexSearcher searcher,
            org.apache.lucene.search.Query query, org.apache.lucene.search.Sort sort,
            int limit, ScoreDoc after)
            throws IOException {


        if (sort != null) {
            return searcher.searchAfter(after, query, limit, sort, false, false);
        } else {
            if (!(query instanceof ConstantScoreQuery)) {
                query = new ConstantScoreQuery(query);
            }
            return searcher.search(query, limit);
        }
    }

    @Override
    public void iterateMatching(String token, Search search, MatchCallback callback) throws QueryException {
        final Query query = search.getQuery();
        final Sorting sorting = search.getSorting();
        final int clientLimit = search.getLimit();
        final int clientCursor = search.getCursor();
        final PropertySelect selectedProperties = search.getPropertySelect();

        IndexSearcher searcher = null;
        try {

            searcher = indexAccessor.getIndexSearcher();

            org.apache.lucene.search.Query luceneQuery
                    = queryBuilder.buildQuery(query, searcher);

            if (logger.isDebugEnabled()) {
                logger.debug("Built user Lucene query '{}' from user VTK query:\n{}\n",
                        luceneQuery,
                        query != null ? query.accept(new ToStringVisitor(true), null) : "null");
            }

            Optional<org.apache.lucene.search.Query> filterQuery
                    =                    queryBuilder.buildSearchFilterQuery(token, search);
            if (filterQuery.isPresent()) {
                luceneQuery = queryBuilder.combineQueryWithFilter(luceneQuery, filterQuery.get());
                logger.debug("Built Lucene filter query: '{}'", filterQuery.get());
            } else {
                logger.debug("No filter query for search.");
            }

            Optional<org.apache.lucene.search.Sort> luceneSort = queryBuilder.buildSort(sorting);
            if (luceneSort.isPresent()) {
                logger.debug("Built Lucene sorting '{}' from VTK sorting '{}'",
                      luceneSort.get(), sorting);
            } else {
                logger.debug("No sorting specified in search");
            }

            logger.debug("Combined and final Lucene query: '{}'", luceneQuery);

            if (clientLimit <= 0) {
                return;
            }

            if (luceneSort.isPresent()) {
                doSortedIteration(searcher, callback, luceneQuery, luceneSort.get(), clientCursor, clientLimit, selectedProperties);
            } else {
                Collector collector = new MatchCallbackInvokingCollector(callback, selectedProperties, clientCursor, clientLimit);
                searcher.search(luceneQuery, collector);
            }
        } catch (ClientIterationAbort e) {
            if (e.getCause() == null) {
                // Controlled iteration abort through MatchCallback
                return;
            }
            throw new QueryException("Client threw exception during match iteration", e.getCause());

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

    private class ClientIterationAbort extends RuntimeException {

        private static final long serialVersionUID = -2227548724212657996L;

        ClientIterationAbort() {
        }

        ClientIterationAbort(Exception cause) {
            super(cause);
        }

    }

    private void doSortedIteration(IndexSearcher searcher, MatchCallback clientCallback,
            org.apache.lucene.search.Query query, org.apache.lucene.search.Sort sort,
            int clientCursor, int clientLimit, PropertySelect selectedProperties) throws IOException {

        ScoreDoc last = null;
        long matchDocCount = 0;
        long totalHits = 0;
        int callbackCount = 0;
        do {
            // Operate with page size of 1000 when iterating sorted results
            TopFieldDocs tfd = (TopFieldDocs) doTopDocsQuery(searcher, query, sort, 1000, last);
            ScoreDoc[] scoreDocs = tfd.scoreDocs;
            totalHits = tfd.totalHits;
            last = scoreDocs.length > 0 ? scoreDocs[scoreDocs.length-1] : null;
            for (int i=0; i<scoreDocs.length; i++) {
                if (matchDocCount++ < clientCursor) {
                    continue;
                }
                if (callbackCount++ >= clientLimit) {
                    throw new ClientIterationAbort();
                }

                DocumentStoredFieldVisitor fieldVisitor = documentMapper.newStoredFieldVisitor(selectedProperties);
                searcher.doc(scoreDocs[i].doc, fieldVisitor);
                PropertySet propSet = documentMapper.getPropertySet(fieldVisitor.getDocument());

                try {
                    if (!clientCallback.matching(propSet)) {
                        throw new ClientIterationAbort();
                    }
                } catch (Exception e) {
                    throw new ClientIterationAbort(e);
                }
            }
        } while (last != null && matchDocCount < totalHits);
    }

    private class MatchCallbackInvokingCollector extends SimpleCollector {

        final MatchCallback clientCallback;
        final PropertySelect selectedProperties;
        final int clientCursor, clientLimit;
        LeafReaderContext context;
        int callbackCount = 0;
        int matchDocCount = 0;

        MatchCallbackInvokingCollector(MatchCallback clientCallback, PropertySelect selectedProperties,
                int clientCursor, int clientLimit) {
            this.clientCallback = clientCallback;
            this.selectedProperties = selectedProperties;
            this.clientCursor = clientCursor;
            this.clientLimit = clientLimit;
        }

        @Override
        protected void doSetNextReader(LeafReaderContext context) throws IOException {
            this.context = context;
        }

        @Override
        public void collect(int doc) throws IOException {
            if (matchDocCount++ < clientCursor) {
                return;
            }
            if (callbackCount++ >= clientLimit) {
                throw new ClientIterationAbort();
            }

            DocumentStoredFieldVisitor fieldVisitor = documentMapper.newStoredFieldVisitor(selectedProperties);
            context.reader().document(doc, fieldVisitor);
            PropertySet properSet = documentMapper.getPropertySet(fieldVisitor.getDocument());

            try {
                if (!clientCallback.matching(properSet)) {
                    throw new ClientIterationAbort();
                }
            } catch (Exception e) {
                throw new ClientIterationAbort(e);
            }
        }

        @Override
        public boolean needsScores() {
            return false;
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
        if (luceneSearchLimit <= 0) {
            throw new IllegalArgumentException("Property 'luceneSearchLimit' must be > 0");
        }
        this.luceneSearchLimit = luceneSearchLimit;
    }

    public void setTotalQueryTimeWarnThreshold(long totalQueryTimeWarnThreshold) {
        if (totalQueryTimeWarnThreshold <= 0) {
            throw new IllegalArgumentException("Argument cannot be zero or negative");
        }

        this.totalQueryTimeWarnThreshold = totalQueryTimeWarnThreshold;
    }

}
