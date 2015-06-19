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

package vtk.util.cache;

import java.util.concurrent.*;

import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;
import net.sf.ehcache.util.TimeUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * Thin {@link ContentCache} layer above a self-populating ("pull-through") Ehcache
 * instance. All aspects of the cache behavior and item loading are controlled
 * by configuration of the injected cache instance underlying this {@link ContentCache}.
 * 
 * At most one thread will load a given key at any time,
 * other threads requesting the same key will block until the first thread has finished loading.
 *
 * TODO Ehcache also allows to set a blocking timeout, which we can consider
 * using to prevent thread pileups due to slow loading.
 * 
 * @param <K> key type for the cache
 * @param <V> value type for the cache
 */
public class EhContentCache<K, V>  implements ContentCache<K, V>, DisposableBean {
    private final static Log logger = LogFactory.getLog(EhContentCache.class);
    private final SelfPopulatingCache cache;

    private final boolean requireSerializable;
    private final int timeToIdleSeconds;
    private final int timeToLiveSeconds;
    private final boolean asynchronousRefresh;
    protected final ScheduledExecutorService refreshAllExecutor;
    protected final ExecutorService asyncRefreshExecutor;

    public EhContentCache(SelfPopulatingCache cache) {
        this(cache, true, -1, false);
    }

    /**
     * @param requireSerializable Whether to use Ehcache API which requires keys and values to be
     * serializable or not.
     */
    public EhContentCache(
            SelfPopulatingCache cache,
            boolean requireSerializable,
            int refreshIntervalSeconds,
            boolean asynchronousRefresh
    ) {
        CacheConfiguration config = cache.getCacheConfiguration();
        this.cache = cache;
        this.requireSerializable = requireSerializable;
        this.asynchronousRefresh = asynchronousRefresh;

        // Do our own cache expiration to enable us to return stale objects if we receive an error
        // from upstream.
        this.timeToIdleSeconds = TimeUtil.convertTimeToInt(config.getTimeToIdleSeconds());;
        this.timeToLiveSeconds = TimeUtil.convertTimeToInt(config.getTimeToLiveSeconds());;
        config.setTimeToIdleSeconds(0);
        config.setTimeToLiveSeconds(0);
        config.setEternal(true);

        if (asynchronousRefresh) {
            asyncRefreshExecutor = Executors.newFixedThreadPool(16, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, EhContentCache.this.cache.getName() + ".async-refresh");
                }
            });
        } else {
            asyncRefreshExecutor = null;
        }

        // One refresh thread for each vhost with shared Ehcache is unnecessary, but harmless.
        // A fix might be to expose refresh as a ContentCache API call, and have an external
        // static singleton common to all vhosts in JVM, which does refresh on shared Ehcache instances.
        if (refreshIntervalSeconds > 0) {
            refreshAllExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory(){
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, EhContentCache.this.cache.getName() + ".refresh");
                }
            });
            refreshAllExecutor.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    EhContentCache.this.cache.refresh(false);
                }
            }, refreshIntervalSeconds, refreshIntervalSeconds, TimeUnit.SECONDS);
        } else {
            refreshAllExecutor = null;
        }
    }
    
    @Override
    public V get(K identifier) throws Exception {
        if (identifier == null) {
            throw new IllegalArgumentException("Cache identifiers cannot be null");
        }
        
        // TODO cache exceptions for a short time (grace time) before letting loaders retry.
        // This may prove useful when loaders are slow and eventually fail (timeout on web resources for instance).

        Element element = cache.getQuiet(identifier);
        if (element != null) {
            if (isExpired(element)) {
                // This will not stop multiple thread trying to refresh the cache entry at the same time,
                // so it can cause unnecessary work to be done.
                if (asynchronousRefresh) {
                    triggerAsynchronousRefresh(identifier);
                } else {
                    refresh(identifier);
                }
            }
        }
        element = cache.get(identifier);

        if (requireSerializable) {
            return (V) element.getValue();
        } else {
            return (V) element.getObjectValue();
        }
    }

    @Override
    public int getSize() {
        return cache.getSize();
    }
    
    @Override
    public void clear() {
        cache.removeAll();
    }

    @Override
    public void destroy() throws Exception {
        if (refreshAllExecutor != null) {
            refreshAllExecutor.shutdown();
        }
        if (asyncRefreshExecutor != null) {
            asyncRefreshExecutor.shutdown();
        }
    }


    private boolean isExpired(Element element){
        long now = System.currentTimeMillis();
        long expirationTime = getExpirationTime(element);
        return now > expirationTime;
    }

    private long getExpirationTime(Element element) {
        long expirationTime = 0;
        long ttlExpiry = element.getCreationTime() + TimeUtil.toMillis(timeToLiveSeconds);

        long mostRecentTime = Math.max(element.getCreationTime(), element.getLastAccessTime());
        long ttiExpiry = mostRecentTime + TimeUtil.toMillis(timeToIdleSeconds);

        if (timeToLiveSeconds != 0 && (timeToIdleSeconds == 0 || element.getLastAccessTime() == 0)) {
            expirationTime = ttlExpiry;
        } else if (timeToLiveSeconds == 0) {
            expirationTime = ttiExpiry;
        } else {
            expirationTime = Math.min(ttlExpiry, ttiExpiry);
        }
        return expirationTime;
    }

    private void triggerAsynchronousRefresh(final K identifier) {
        Runnable fetcher = new Runnable() {
            @Override
            public void run() {
                refresh(identifier);
            }
        };
        asyncRefreshExecutor.execute(fetcher);
    }

    private void refresh(final K identifier) {
        try {
            cache.refresh(identifier, false);
        } catch (Exception e) {
            logger.info("Error refreshing object '" + identifier + "'", e);
        }
    }
}
