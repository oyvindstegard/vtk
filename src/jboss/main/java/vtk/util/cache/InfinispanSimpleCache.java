/* Copyright (c) 2016, University of Oslo, Norway
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

import java.util.Collections;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

/**
 * Infinispan based implementation of Vortex SimpleCache.
 * Infinispan uses JGroups as underlying message protocol, same as JBoss.
 */
public class InfinispanSimpleCache<K, V> implements SimpleCache<K, V> {
    private static final Log log = LogFactory.getLog(InfinispanSimpleCache.class);
    private static final EmbeddedCacheManager manager =
        new DefaultCacheManager(GlobalConfigurationBuilder
            .defaultClusteredBuilder()
            .globalJmxStatistics().allowDuplicateDomains(true).enable()
            .build());
    private Cache<K, V> cache = null;
    
    /**
     * Setup and connect to shared cache.
     * @param name Unique identifier of shared cache within the cluster.
     */
    public InfinispanSimpleCache(String name, int timeoutSeconds, boolean updateTimeouts) {
        long timeoutMillis = timeoutSeconds * 1000;
        Configuration config = new ConfigurationBuilder()
            // Replicate all entries to all nodes
            .clustering().cacheMode(CacheMode.REPL_SYNC)
            .expiration()
                .lifespan(updateTimeouts ? -1L : timeoutMillis)
                .maxIdle(updateTimeouts ? timeoutMillis : -1L)
                .enableReaper()
            .build();

        // Set configuration overrides for this cache name
        manager.defineConfiguration(name, config);
        cache = manager.getCache(name);
    }

    @Override
    public void put(K key, V value) {
        if (key == null) {
            return;
        }
        cache.put(key, value);
        log.debug(String.format("PUT: key=%s, value=%s, count=%d", key, value, getSize()));
    }

    @Override
    public V get(K key) {
        if (key == null) {
            return null;
        }
        return cache.get(key);
    }

    @Override
    public V remove(K key) {
        if (key == null) {
            return null;
        }
        return cache.remove(key);
    }

    @Override
    public int getSize() {
        return cache.size();
    }

    /**
     * Relatively expensive operation. Is it really needed?
     */
    @Override
    public Set<K> getKeys() {
        return Collections.unmodifiableSet(cache.keySet());
    }
}

