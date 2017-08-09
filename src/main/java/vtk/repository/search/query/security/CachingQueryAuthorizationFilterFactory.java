/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.repository.search.query.security;

import java.lang.ref.SoftReference;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;

import vtk.repository.search.query.filter.FilterFactory;
import vtk.security.Principal;
import vtk.util.cache.LruCache;

/**
 * An authorization filter factory which does caching of filters.
 *
 * <p>A small LRU cache is used internally, which caches per principal
 * authorization filters. The intention is not to cache a large amount of data
 * or cache items very long. So items have a short expiry, cache size is limited
 * and {@link SoftReference soft references} are employed around the filter values (for memory friendlyness).
 */
public class CachingQueryAuthorizationFilterFactory extends SimpleQueryAuthorizationFilterFactory {

    private final Filter cachingAclReadForAllFilter = 
            FilterFactory.cacheWrapper(SimpleQueryAuthorizationFilterFactory.ACL_READ_FOR_ALL_FILTER);

    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger misses = new AtomicInteger();

    private static class FilterCacheItem {
        final Instant expiry;
        final SoftReference<Filter> filter;

        FilterCacheItem(Filter f) {
            this.filter = f == null ? null : new SoftReference(f);
            this.expiry = Instant.now().plusSeconds(60); // Same amount of time that group store caches group memberships
        }

        boolean isExpired() {
            return expiry.isBefore(Instant.now());
        }
    }

    private final Map<String,FilterCacheItem> cache = new LruCache<>(100);

    @Override
    public Filter authorizationQueryFilter(String token, IndexSearcher searcher) {

        Principal principal = getPrincipal(token);
        if (principal == null) {
            return cachingAclReadForAllFilter;
        }

        final String cacheKey = principal.getQualifiedName();

        FilterCacheItem item;
        synchronized(cache) {
            item = cache.get(cacheKey);
        }

        if (item != null && !item.isExpired()) {
            if (item.filter == null) {
                // Cached null-filter means no filter for root roles
                return null;
            }

            Filter cachedAuthorizationFilter = item.filter.get();
            if (cachedAuthorizationFilter != null) {
                incrementHits();
                return cachedAuthorizationFilter;
            } // else filter has been garbage collected and a new one needs to be created
        }

        incrementMisses();

        Filter authorizationFilter = super.authorizationQueryFilter(token, searcher);
        if (authorizationFilter != null) {
            authorizationFilter = FilterFactory.cacheWrapper(authorizationFilter);
        }
        synchronized (cache) {
            cache.put(cacheKey, new FilterCacheItem(authorizationFilter));
        }

        return authorizationFilter;
    }

    private void incrementHits() {
        if (hits.incrementAndGet() < 0) {
            hits.set(0);
            misses.set(0);
        }
    }

    private void incrementMisses() {
        if (misses.incrementAndGet() < 0) {
            hits.set(0);
            misses.set(0);
        }
    }

    public float hitRatio() {
        int hitCount = hits.get();
        int missCount = misses.get();
        int total = hitCount + missCount;
        if (total == 0) return 0f;
        return hitCount / (float)total;
    }

    @Override
    public Filter readForAllFilter(IndexSearcher searcher) {
        return this.cachingAclReadForAllFilter;
    }
    
}
