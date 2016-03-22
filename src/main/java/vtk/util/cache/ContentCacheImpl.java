/* Copyright (c) 2007, University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Default content cache implementation using a <code>ConcurrentHashMap</code>
 * as backing storage.
 *
 * May be used for content that is potentially expensive to
 * load for certain periods of time before refreshing it. Typical
 * examples of such content include network resources, compiled XML
 * stylesheets, etc. A {@link ContentCacheLoader} is responsible for
 * loading the actual items.
 *
 * <p>Note: This cache is not meant as a generalized read/write cache,
 * as it never shrinks (it does not contain a <code>remove()</code>
 * method, although a size limit can be specified). 
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>cacheLoader</code> - the {@link ContentCacheLoader}
 *   <li><code>cacheSeconds</code> - the cache timeout in seconds
 *   <li><code>cacheMilliseconds</code> - the cache timeout in milliseconds
 *   <li><code>asynchronousRefresh</code> - if an item is expired and
 *   this property is <code>true</code>, the item is returned as is
 *   and a refresh is triggered in a separate thread. Otherwise, the
 *   current thread blocks while the item is refreshed.
 *   <li><code>refreshInterval</code> - if set to a positive number of
 *   seconds, a thread is created at initialization time, triggering a
 *   refresh of expired items at regular intervals (in a separate
 *   thread).
 *   <li><code>maxItems</code> - the maximum number of items to allow
 *   in the cache. A negative number means no limit (the default).
 * </ul>
 *
 * @param <K> key of the cached objects
 * @param <V> value of the cached objects
 * 
 * XXX Should probably not allow unlimited number of asynchronous refresh threads
 * to be created. What if the loader is slow and a common and expired key is requested
 * several times ? That will dispatch a new async refresh thread for each
 * get on that key, which will all hit loader pretty hard or bog down system.
 * Switch to a queue and fixed max number of refresh threads instead ?
 */
public final class ContentCacheImpl<K,V> implements ContentCache<K,V>, InitializingBean, DisposableBean {
    
    private static Logger logger = LoggerFactory.getLogger(ContentCacheImpl.class);    

    private String name;
    private ContentCacheLoader<K, V> loader;
    private int cacheTimeout;
    private ConcurrentHashMap<K, Item> cache = new ConcurrentHashMap<K, Item>();
    private boolean asynchronousRefresh = false;
    private int refreshInterval = -1;
    private RefreshThread refreshThread;
    private int maxItems = -1;
    
    public void setName(String name) {
        this.name = name;
    }

    public void setCacheLoader(ContentCacheLoader<K, V> loader) {
        this.loader = loader;
    }
    
    public void setCacheSeconds(int cacheSeconds) {
        this.cacheTimeout = cacheSeconds * 1000;
    }

    public void setCacheMilliSeconds(int cacheTimeout) {
        this.cacheTimeout = cacheTimeout;
    }

    public void setAsynchronousRefresh(boolean asynchronousRefresh) {
        this.asynchronousRefresh = asynchronousRefresh;
    }
    
    public void setRefreshInterval(int refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }
    

    @Override
    public void afterPropertiesSet() {
        if (this.name == null) {
            throw new BeanInitializationException("JavaBean property 'name' not set");
        }
        if (this.loader == null) {
            throw new BeanInitializationException("JavaBean property 'loader' not set");
        }
        if (this.maxItems == 0) {
            throw new BeanInitializationException(
                "JavaBean property 'maxItems' has an illegal value: specify "
                + "either a positive or negative integer");
        }

        if (this.refreshInterval > 0) {
            this.refreshThread = new RefreshThread(this.refreshInterval);
            this.refreshThread.setName(this.name + ".periodic-refresh");
            this.refreshThread.start();
        }
    }
    
    @Override
    public void destroy() {
        if (this.refreshThread != null) {
            this.refreshThread.interrupt();
        }
    }
    
    @Override
    public V get(K identifier) throws Exception {
        if (identifier == null) {
            throw new IllegalArgumentException("Cache identifiers cannot be NULL");
        }
        if (this.cacheTimeout <= 0) {
            if (logger.isTraceEnabled()) {
                logger.trace("Returning uncached object: '" + identifier + "'");
            }
            return this.loader.load(identifier);
        }

        Item item = this.cache.get(identifier);
        if (item == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Caching object: '" + identifier + "'");
            } 
            item = cacheItem(identifier);
        }
        
        if (item.getTimestamp().getTime() + this.cacheTimeout <= System.currentTimeMillis()) {
            if (this.asynchronousRefresh) {
                triggerAsynchronousRefresh(identifier);
            } else {
                item = cacheItem(identifier);
            }
        }
        
        if (this.refreshThread == null && this.cache.size() > this.maxItems) {
            // Shrink cache synchronously (no refresh thread)
            removeOldestExceedingSizeLimit();
        }
        
        if (logger.isTraceEnabled()) {
            logger.trace("Returning object '" + item + "' from cache");
        }
        return item.getObject();
    }
    
    @Override
    public int getSize() {
        return this.cache.size();
    }

    @Override
    public void clear() {
        this.cache.clear();
    }
    
    /**
     * 
     * @param identifier cache key to pass to laoder
     * @return newly loaded item, or item already in cache if it has not
     *         expired.
     * @throws Exception in case of loader failure
     */
    private Item cacheItem(K identifier) throws Exception {

        Item item = this.cache.get(identifier);
        long now = System.currentTimeMillis();

        if (item == null ||
            (item.getTimestamp().getTime() + this.cacheTimeout <= now)) {
            V object = this.loader.load(identifier);
            Item newItem = new Item(identifier, object);
            this.cache.put(identifier, newItem);
            item = newItem;
        }
        
        return item;
    }


    private void triggerAsynchronousRefresh(final K identifier) {
        Runnable fetcher = new Runnable() {
           @Override
           public void run() {
              try {
                 cacheItem(identifier);
              } catch (Exception e) {
                 logger.info("Error refreshing object '" + identifier + "'", e);
              }
           }
        };
        new Thread(fetcher, this.name + ".async-refresh").start();
    }


    private synchronized void removeOldestExceedingSizeLimit() {
        if (this.maxItems <= 0) return;
        int size = this.cache.size();
        int n = size - this.maxItems;
        if (n <= 0) return;


        List<Map.Entry<K, Item>> sortedList =
            new ArrayList<Map.Entry<K, Item>>(this.cache.entrySet());

        Collections.sort(sortedList, new Comparator<Map.Entry<K, Item>>() {
                @Override
                public int compare(Map.Entry<K, Item> entry1, Map.Entry<K, Item> entry2) {
                    Item i1 = entry1.getValue();
                    Item i2 = entry2.getValue();

                    return new Long(i1.getTimestamp().getTime()).compareTo(
                        new Long(i2.getTimestamp().getTime()));
                }
            });
        
        List<Map.Entry<K, Item>> removeList = sortedList.subList(0, n);
        if (logger.isDebugEnabled()) {
            logger.debug("Cache size limit exceeded, removing " + n
                         + " oldest items (of total " + size + ")");
        }

        for (Map.Entry<K, Item> entry: removeList) {
            Item item = entry.getValue();
            this.cache.remove(item.getKey());
        }
    }
    
    private synchronized void refreshExpired() {
        int size = this.cache.size();
        if (size > this.maxItems) {
            removeOldestExceedingSizeLimit();
            size = this.cache.size();
        }

        List<K> refreshList = new ArrayList<K>();

        for (Map.Entry<K, Item> entry: this.cache.entrySet()) {
            K identifier = entry.getKey();
            Item item =  entry.getValue();
            long now = System.currentTimeMillis();
            if (item.getTimestamp().getTime() + this.cacheTimeout < now) {
                refreshList.add(identifier);
            }
        }

        if (logger.isTraceEnabled()) {
            logger.trace("Checking expired items: " + refreshList.size()
                         + " expired items found (of total " + size + ")");
        }

        for (K identifier: refreshList) {
            try {
                cacheItem(identifier);
                if (logger.isDebugEnabled()) {
                    logger.debug("Refreshed expired cache item: '" + identifier + "'");
                }
            } catch (Throwable t) {
                logger.warn("Unable to refresh cached object " + identifier, t);
            }
        }
    }


    private class Item {
        private K key;
        private V object;
        private Date timestamp;

        public Item(K key, V object) {
            this.key = key;
            this.object = object;
            this.timestamp = new Date();
        }
        public K getKey() {
            return this.key;
        }
        public V getObject() {
            return this.object;
        }
        public Date getTimestamp() {
            return this.timestamp;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("item: [");
            sb.append(this.key.toString()).append("=");
            sb.append(this.object.getClass().getName());
            sb.append("; timestamp=").append(this.timestamp);
            sb.append("]");
            return sb.toString();
        }
        
    }

    private class RefreshThread extends Thread {

        private long sleepSeconds;
        private boolean alive = true;
    
        public RefreshThread(long sleepSeconds) {
            this.sleepSeconds = sleepSeconds;
        }
        
        @Override
        public void run() {
            while (this.alive) {
                try {
                    sleep(1000 * this.sleepSeconds);
                    refreshExpired();
                } catch (InterruptedException e) {
                    this.alive = false;
                } catch (Throwable t) {
                    logger.warn("Caught exception in cleanup thread", t);
                }
            }
            logger.info("Terminating refresh thread");
        }
    }

}
