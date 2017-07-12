/* Copyright (c) 2014, University of Oslo, Norway
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.search.QueryParserFactory;
import vtk.repository.search.Search;
import vtk.repository.search.SortingParserFactory;
import vtk.repository.search.query.LuceneQueryBuilder;
import vtk.util.text.TextUtils;
import vtk.web.search.SearchParser;

/**
 *
 */
public class WarmupSearcherFactory extends SearcherFactory implements InitializingBean {

    private LuceneQueryBuilder luceneQueryBuilder;
    
    private QueryParserFactory queryParserFactory;
    private SortingParserFactory sortingParserFactory;
    
    private List<Search> warmupSearches = Collections.emptyList();
    
    private List<String> warmupSearchSpecs = Collections.emptyList();
    
    private final Logger logger = LoggerFactory.getLogger(WarmupSearcherFactory.class.getName());

    @Override
    public void afterPropertiesSet() throws Exception {
        this.warmupSearches = buildWarmupSearches(warmupSearchSpecs);
    }
    
    @Override
    public IndexSearcher newSearcher(IndexReader reader) throws IOException {
        IndexSearcher searcher = super.newSearcher(reader);
        warmSearcher(searcher);
        return searcher;
    }
    
    private List<Search> buildWarmupSearches(List<String> searchSpecs) throws Exception {
        List<Search> searches = new ArrayList<>();
        for (String spec: searchSpecs) {
            String queryString = "";
            String sortString = "";
            String limitString = "";
            String[] components = TextUtils.parseCsv(spec, ',', TextUtils.TRIM|TextUtils.IGNORE_INVALID_ESCAPE);
            if (components.length >= 1) {
                queryString = components[0];
            }
            if (components.length >= 2) {
                sortString = components[1];
            }
            if (components.length >= 3) {
                limitString = components[2];
            }
            
            if (queryString.isEmpty()) {
                throw new IllegalArgumentException("Invalid search spec, query part cannot be empty: " + spec);
            }
            
            Search search  = new Search();
            search.setQuery(queryParserFactory.getParser().parse(queryString));
            if (!sortString.isEmpty()) {
                if ("null".equals(sortString)) {
                    search.setSorting(null);
                }
                else {
                    search.setSorting(sortingParserFactory.parser().parse(sortString));
                }
            }
            if (!limitString.isEmpty()) {
                search.setLimit(Integer.parseInt(limitString));
            }
            
            searches.add(search);
        }
        return searches;
    }
    
    private void warmSearcher(IndexSearcher searcher) throws IOException {
        for (Search search : warmupSearches) {
            Query luceneQuery = luceneQueryBuilder.buildQuery(search.getQuery(), searcher);
            Sort luceneSorting = luceneQueryBuilder.buildSort(search.getSorting());
            Filter luceneFilter = luceneQueryBuilder.buildSearchFilter(null, search, searcher);
            int limit = search.getLimit();
            
            TopDocs docs;
            if (luceneSorting != null) {
                docs = searcher.search(luceneQuery, luceneFilter, limit, luceneSorting);
            } else {
                docs = searcher.search(luceneQuery, luceneFilter, limit);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Search " + search + " matched " + docs.scoreDocs.length + " docs.");
            }
            int max = Math.min(250, docs.scoreDocs.length);
            for (int i = 0; i < max; i++) {
                searcher.doc(docs.scoreDocs[i].doc);
            }
        }
    }

    @Required
    public void setLuceneQueryBuilder(LuceneQueryBuilder luceneQueryBuilder) {
        this.luceneQueryBuilder = luceneQueryBuilder;
    }
    
    @Required
    public void setQueryParserFactory(QueryParserFactory queryParserFactory) {
        this.queryParserFactory = queryParserFactory;
    }

    @Required
    public void setSortingParserFactory(SortingParserFactory sortingParserFactory) {
        this.sortingParserFactory = sortingParserFactory;
    }

    /**
     * Set warmup searches as a list of comma-separated values. Searches use
     * the VTK syntax and are parsed by {@link SearchParser}.
     * First value is query, second is sorting and third is limit.
     * @param searchSpecs List of 3-part comma-separated tuples on the form
     * "&lt;query&gt;, &lt;sort&gt;, &lt;limit&gt;". Use backslashes to escape
     * commas inside values if necessary. Values for sort and limit are optional.
     */
    public void setWarmupSearchSpecs(List<String> searchSpecs) {
        this.warmupSearchSpecs = searchSpecs;
    }

}
