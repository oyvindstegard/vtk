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
package vtk.util.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;

public class RedisContentCache<K, V> implements ContentCache<K, V> {
    private Pool<Jedis> pool;
    private String prefix;
    private int timeoutSeconds;
    private boolean updateTimeouts;
    private ContentCacheLoader<K, CacheWrapper<V>> loader;
    
    public RedisContentCache(Pool<Jedis> jedisPool, String prefix, 
            int timeoutSeconds, boolean updateTimeouts, 
            ContentCacheLoader<K, V> loader) {
        this.pool = jedisPool;
        this.prefix = prefix;
        this.timeoutSeconds = timeoutSeconds;
        this.updateTimeouts = updateTimeouts;
        this.loader = key -> new CacheWrapper<>(loader.load(key));
    }

    @Override
    public V get(K identifier) throws Exception {
        if (identifier == null) return null;
        try (Jedis jedis = pool.getResource()) {
            byte[] bkey = (prefix + identifier.toString()).getBytes("utf-8");
            byte[] bs = jedis.get(bkey);
            V result;
            
            if (bs == null) {
                CacheWrapper<V> wrapper = loader.load(identifier);
                ByteArrayOutputStream valueStream = new ByteArrayOutputStream();
                ObjectOutputStream oout = new ObjectOutputStream(valueStream);
                oout.writeObject(wrapper);
                if (timeoutSeconds > 0) {
                    jedis.setex(bkey, timeoutSeconds, valueStream.toByteArray());
                }
                else {
                    jedis.set(bkey, valueStream.toByteArray());
                }
                result = wrapper.value;
            }
            else {
                ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(bs));
                @SuppressWarnings("unchecked")
                CacheWrapper<V> wrapper = (CacheWrapper<V>) is.readObject();
                result = wrapper.value;
            }
            
            if (updateTimeouts) {
                jedis.expire(bkey, timeoutSeconds);
            }
            return result;
        }
        catch (IOException|ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public int getSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }
    
    private static class CacheWrapper<T> implements Serializable {
        private static final long serialVersionUID = -6837995693990222559L;
        public final T value;
        public CacheWrapper(T value) {
            this.value = value;
        }
    }
}
