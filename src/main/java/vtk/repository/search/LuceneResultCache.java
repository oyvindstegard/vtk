/* Copyright (c) 2015, University of Oslo, Norway
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

import vtk.util.cache.LruCache;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;

/**
 * Lucene result cache with per index reader caching using LRU eviction.
 *
 * <p>
 * Only simplistic cache map synchronization at this time, but it should not be
 * a problem with our level of concurrency in search code (which is also
 * throttled).
 *
 * <p>
 * Basically only ordered document ids are cached for each result (with some
 * additional small pieces of data, see {@link TopDocs}). The cache should
 * not be too memory demanding.
 *
 * <p>
 * A map with weak reference keys based on index reader instance is used so we
 * automatically get fresh result caches on index updates and cleanup of old
 * closed instances. This is extremely important because results are strongly
 * tied to the index reader instance which was used to produce them, and they
 * become invalid for new readers opened on updated indexes. It is also
 * important not to keep strong references to stale index readers, because that
 * would cause memory leaks.
 */
class LuceneResultCache {
    
    public static final int DEFAULT_MAX_ITEMS = 100;

    private final Map<Object, Map<Object,TopDocs>> cache;
    private final int maxItems;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger misses = new AtomicInteger();
    
    private final Log logger = LogFactory.getLog(LuceneResultCache.class.getName());

    /**
     * Construct a new cache instance with {@link #DEFAULT_MAX_ITEMS} max number
     * of items per index reader instance.
     */
    public LuceneResultCache() {
        this(DEFAULT_MAX_ITEMS);
    }

    /**
     * Construct a new cache instance with a maximal number of result items
     * per unique index reader instance.
     * @param maxItems max number of search results to cache
     */
    public LuceneResultCache(int maxItems) {
        this.maxItems = maxItems > 0 ? maxItems : DEFAULT_MAX_ITEMS;
        this.cache = Collections.synchronizedMap(new WeakHashMap<Object,Map<Object,TopDocs>>());
    }
    
    
    /**
     * Execute a query possibly returning the result directly from cache. If
     * the result is not cached, a search will be executed and the new result
     * cached.
     * @param searcher
     * @param query
     * @param filter
     * @param sort
     * @param limit
     * @return a <code>TopDocs</code> result, never <code>null</code>.
     * @throws IOException in case of index errors 
     */
    public TopDocs doCachedTopDocsQuery(IndexSearcher searcher, Query query,
                              Filter filter, Sort sort, int limit)
            throws IOException {

        final Object searchCacheKey = new SearchCacheKey(query, filter, sort, limit);
        final Map<Object,TopDocs> resultCache = resultCacheForSearcher(searcher);

        TopDocs cachedResult = resultCache.get(searchCacheKey);
        
        if (cachedResult != null) {
            if (hits.incrementAndGet() < 0) {
                hits.set(0);
                misses.set(0);
            }
            // Cache hit
            return cachedResult;
        }
        
        if (misses.incrementAndGet() < 0) {
            hits.set(0);
            misses.set(0);
        }
        
        TopDocs topDocs;
        if (sort != null) {
            topDocs = searcher.search(query, filter, limit, sort);
        } else {
            topDocs = searcher.search(query, filter, limit);
        }

        resultCache.put(searchCacheKey, topDocs);
        
        return topDocs;
    }
    
    /**
     * @return ratio of hits vs total number of cache requests
     */
    public float hitRatio() {
        int hitCount = hits.get();
        int missCount = misses.get();
        int total = hitCount + missCount;
        if (total == 0) return 0f;
        return hitCount / (float)total;
    }

    /**
     * @return number of cache hits.
     */
    public int hits() {
        return hits.get();
    }

    /**
     * @return number of cache misses.
     */
    public int misses() {
        return misses.get();
    }

    private Map<Object,TopDocs> resultCacheForSearcher(IndexSearcher s) {
        Object indexReaderCacheKey = s.getIndexReader().getCombinedCoreAndDeletesKey();
        
        Map<Object,TopDocs> resultCache = cache.get(indexReaderCacheKey);
        if (resultCache == null) {
            resultCache = Collections.synchronizedMap(new LruCache<Object,TopDocs>(maxItems));
            cache.put(indexReaderCacheKey, resultCache);
        }
        
        // Some throttled debug logging in case we get problems with weak 
        // refs not getting cleaned properly
        if (logger.isDebugEnabled() && (hits.get() + misses.get()) % 100 == 0) {
            logger.debug("Current number of index reader keys in cache: " + cache.size());
        }

        return resultCache;
    }
    
    
    // Cache key covering all search aspects that influence the result.
    // It is assumed that the provided input objects do not mutate.
    private static final class SearchCacheKey {
        private final Query q;
        private final Filter f;
        private final Sort s;
        private final int limit;

        SearchCacheKey(Query q, Filter f, Sort s, int limit) {
            this.q = q;
            this.f  = f;
            this.s = s;
            this.limit = limit;
        }

        private int hashCode;
        @Override
        public int hashCode() {
            int hash = this.hashCode;
            if (hash == 0) {
                hash = 5;
                hash = 97 * hash + Objects.hashCode(this.q);
                hash = 97 * hash + Objects.hashCode(this.f);
                hash = 97 * hash + Objects.hashCode(this.s);
                hash = 97 * hash + this.limit;
                this.hashCode = hash;
            }

            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final SearchCacheKey other = (SearchCacheKey) obj;
            if (!Objects.equals(this.q, other.q)) {
                return false;
            }
            if (!Objects.equals(this.f, other.f)) {
                return false;
            }
            if (!Objects.equals(this.s, other.s)) {
                return false;
            }
            if (this.limit != other.limit) {
                return false;
            }
            return true;
        }
        
    }
    
}
