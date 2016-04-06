/* Copyright (c) 2006, 2007, 2013 University of Oslo, Norway
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
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * This cache stores items for <code>timeoutSeconds</code> seconds,
 * defaulting to zero seconds.
 * 
 * You can set refreshTimestampOnGet to false if you don't want to 
 * refresh an item's time stamp when it's retrieved from the cache. 
 * @param <K> type of cache keys
 * @param <V> type of cache values
 */
public class SimpleCacheImpl<K, V> implements SimpleCache<K, V>, BeanNameAware,
                                        InitializingBean, DisposableBean {
    
    private final Logger logger = LoggerFactory.getLogger(SimpleCacheImpl.class);
    
    private final Map<K, Item> cache = new ConcurrentHashMap<K, Item>();
    private int timeoutSeconds = 0;
    private boolean refreshTimestampOnGet = true;

    private String name;
    
    private CleanupThread cleanupThread;

    public SimpleCacheImpl() {}

    public SimpleCacheImpl(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
    

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }


    @Override
    public void put(K key, V value) {
        this.cache.put(key, new Item(value));
    }


    public void setRefreshTimestampOnGet(boolean refreshTimestampOnGet) {
        this.refreshTimestampOnGet = refreshTimestampOnGet;
    }


    @Override
    public void setBeanName(String name) {
        this.name = name;
    }


    @Override
    public void afterPropertiesSet() {
        if (this.timeoutSeconds > 0) {
            this.cleanupThread = new CleanupThread(this.timeoutSeconds);
            this.cleanupThread.start();
        }
    }
    

    @Override
    public void destroy() {
        if (this.cleanupThread != null) {
            this.cleanupThread.interrupt();
        }
    }
    

    @Override
    public V get(K key) {
        if (key == null) {
            return null;
        }

        Item item = this.cache.get(key);
        if (item == null)
            return null;
        else if (item.getTimestamp().getTime() + this.timeoutSeconds * 1000 > System.currentTimeMillis()) {
            return item.getValue();
        } 
            
        if (logger.isDebugEnabled())
            logger.debug("Cache " + this.name + " expiring item with key='" + key + "'");
        this.cache.remove(key);
        return null;
    }


    @Override
    public V remove(K key) {
        if (key == null) {
            return null;
        }
        Item i = this.cache.remove(key);
        if (i == null) {
            return null;
        }
        return i.getValue();
    }

    
    @Override
    public Set<K> getKeys() {
        return Collections.unmodifiableSet(this.cache.keySet());
    }

    @Override
    public int getSize() {
        return this.cache.size();
    }
    

    /**
     * Cleans up expired cached information periodically.
     * 
     */
    private void cleanupExpiredItems() {

        if (this.timeoutSeconds < 1) return;
    
        for (Iterator<K> i = this.cache.keySet().iterator(); i.hasNext();) {
            K key = i.next();
            Item item = this.cache.get(key);
            if (item != null &&
                item.getTimestamp().getTime() + this.timeoutSeconds * 1000
                < System.currentTimeMillis()) {
                i.remove();
            }
        }
    }
    

    private class Item {
        Date date;
        V value;
        
        public Item(V value) {
            this.date = new Date();
            this.value = value;
        }

        public Date getTimestamp() {
            return this.date;
        }
        
        public V getValue() {
            if (SimpleCacheImpl.this.refreshTimestampOnGet) this.date = new Date();
            return this.value;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + value + "," + date + ")";
        }
    }




    private class CleanupThread extends Thread {

        private final long sleepSeconds;
        private boolean alive = true;
    
        public CleanupThread(long sleepSeconds) {
            this.sleepSeconds = sleepSeconds;
            super.setDaemon(true);
        }

        @Override
        public void run() {

            while (this.alive) {
                try {

                    sleep(1000 * this.sleepSeconds);
                    cleanupExpiredItems();
                    
                } catch (InterruptedException e) {
                    this.alive = false;

                } catch (Throwable t) {
                    logger.warn("Caught exception in cleanup thread", t);
                }
            }
            
        }
    }
    
    
}

