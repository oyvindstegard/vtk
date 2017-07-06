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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;

/**
 * Implementation of {@link SimpleCache} using Redis.
 *
 * <p>XXX This implementation only supports keys of type {@code String} reliably
 * (uses {@code toString } on key objects before encoding to Redis binary key).
 *
 * @param <K> type of key, only keys with sensible {@code toString} representations are supported reliably.
 * @param <V> type of value, must be serializable
 */
public class RedisSimpleCache<K, V> implements SimpleCache<K, V> {
    
    private final Pool<Jedis> pool;
    private final String prefix;
    private final int timeoutSeconds;
    private final boolean updateTimeouts;
    
    public RedisSimpleCache(Pool<Jedis> jedisPool, String prefix, 
            int timeoutSeconds, boolean updateTimeouts) {
        this.pool = jedisPool;
        this.prefix = prefix;
        this.timeoutSeconds = timeoutSeconds;
        this.updateTimeouts = updateTimeouts;
    }

    @Override
    public void put(K key, V value) {
        if (key == null) return;
        try (Jedis jedis = pool.getResource()) {
            byte[] bkey = generateKey(key);
            ByteArrayOutputStream valueStream = new ByteArrayOutputStream();
            ObjectOutputStream oout = new ObjectOutputStream(valueStream);
            oout.writeObject(value);
            
            if (timeoutSeconds > 0) {
                jedis.setex(bkey, timeoutSeconds, valueStream.toByteArray());
            }
            else {
                jedis.set(bkey, valueStream.toByteArray());
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public V get(K key) {
        if (key == null) return null;
        try (Jedis jedis = pool.getResource()) {
            byte[] bkey = generateKey(key);
            byte[] bs = jedis.get(bkey);
            
            if (bs == null) return null;

            if (updateTimeouts) {
                jedis.expire(bkey, timeoutSeconds);
            }            
            ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(bs));
            @SuppressWarnings("unchecked")
            V result = (V) is.readObject();
            return result;
        }
        catch (IOException|ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public V remove(K key) {
        if (key == null) return null;
        try (Jedis jedis = pool.getResource()) {
            byte[] bkey = generateKey(key);
            byte[] bs = jedis.get(bkey);
            
            if (bs == null) return null;

            ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(bs));
            @SuppressWarnings("unchecked")
            V result = (V) is.readObject();

            jedis.del(bkey);
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
    public Set<K> getKeys() {
        throw new UnsupportedOperationException();
    }

    private byte[] generateKey(K key) {
        return (prefix + key.toString()).getBytes(StandardCharsets.UTF_8);
    }

}
