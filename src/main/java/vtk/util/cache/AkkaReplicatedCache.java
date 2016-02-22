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

import static akka.cluster.ddata.Replicator.readLocal;
import static akka.cluster.ddata.Replicator.writeLocal;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ddata.DistributedData;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.LWWMap;
import akka.cluster.ddata.LWWMapKey;
import akka.cluster.ddata.Replicator.Get;
import akka.cluster.ddata.Replicator.GetSuccess;
import akka.cluster.ddata.Replicator.NotFound;
import akka.cluster.ddata.Replicator.Update;
import akka.cluster.ddata.Replicator.UpdateResponse;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.util.Timeout;
import scala.Option;
import scala.collection.JavaConversions;
import scala.collection.immutable.Seq;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;


public class AkkaReplicatedCache<K, V> implements SimpleCache<K, V> {
    
    private ActorRef actor;
    Timeout timeout = new Timeout(Duration.create(5, "seconds"));

    public AkkaReplicatedCache(ActorSystem system, int maxAge) {
        this(system, maxAge, true);
    }
    
    public AkkaReplicatedCache(ActorSystem system, int maxAge, boolean refresh) {
        Optional<Integer> optMaxAge = maxAge > 0 ? 
                Optional.of(maxAge) : Optional.empty();
        actor = system.actorOf(CacheActor.props(optMaxAge, refresh));
    }
    
    @Override
    public void put(K key, V value) {
        actor.tell(new CacheActor.PutRequest(key.toString(), value), null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(K key) {
        try {
            Future<Object> future = Patterns.ask(
                    actor, new CacheActor.GetRequest(key.toString()), timeout);
            
            CacheActor.GetReply result = 
                    (CacheActor.GetReply) Await.result(future, timeout.duration());
            if (result.value.isPresent()) {
                return (V) result.value.get();
            }
            return null;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public V remove(K key) {
        V result = get(key);
        actor.tell(new CacheActor.RemoveRequest(key.toString()), null);
        return result;
        
    }

    @Override
    public int getSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<K> getKeys() {
        throw new UnsupportedOperationException();
    }
    
    
    private static class CacheActor extends AbstractActor {

        /**
         * Holds request context across actor messages
         */
        static class DefaultContextObject {
            public final String key;
            public final ActorRef replyTo;

            public DefaultContextObject(String key, ActorRef replyTo) {
                this.key = key;
                this.replyTo = replyTo;
            }
            
            @Override
            public String toString() {
                return getClass().getSimpleName() 
                        + "(" + key + "," + replyTo + ")";
            }
        }
        
        /**
         * Request to put data in cache
         */
        public static class PutRequest {
            public final String key;
            public final Object value;

            public PutRequest(String key, Object value) {
                this.key = key;
                this.value = value;
            }
            @Override
            public String toString() {
                return getClass().getSimpleName() 
                        + "(" + key + "," + value + ")";
            }

        }

        /**
         * Request to get data from cache
         */
        public static class GetRequest {
            public final String key;

            public GetRequest(String key) {
                this.key = key;
            }
            
            @Override
            public String toString() {
                return getClass().getSimpleName() + "(" + key + ")";
            }
        }

        /**
         * Request to remove an entry from cache
         */
        public static class RemoveRequest {
            public final String key;

            public RemoveRequest(String key) {
                this.key = key;
            }
            
            @Override
            public String toString() {
                return getClass().getSimpleName();
            }
        }
        
        /**
         * Reply to a GetRequest with optional data
         */
        public static class GetReply {
            public final String key;
            public final Optional<Object> value;

            public GetReply(String key, Optional<Object> value) {
                this.key = key;
                this.value = value;
            }
            
            @Override
            public String toString() {
                return getClass().getSimpleName() 
                        + "(" + key + "," + value + ")";
            }
        }
        
        private static class CacheItem implements Serializable {
            private static final long serialVersionUID = -3172599560055335146L;
            public final String key;
            public final Object value;
            public final long timestamp;
            
            public CacheItem(String key, Object value, long timestamp) {
                this.key = key;
                this.value = value;
                this.timestamp = timestamp;
            }
        }
        
        private long lastCleanup = 0L;

        private static Props props(Optional<Integer> maxAge, boolean refresh) {
            List<Object> args = new ArrayList<>();
            args.add(maxAge);
            args.add(refresh);
            Seq<Object> asScalaBuffer = JavaConversions.asScalaBuffer(args).toList();
            return new Props(Deploy.local(), CacheActor.class, asScalaBuffer);
        }

        private final ActorRef replicator = DistributedData.get(context().system()).replicator();
        private final Cluster node = Cluster.get(context().system());
        
        private Optional<Integer> maxAge = Optional.empty();
        private boolean refresh = false;

        private CacheActor(Optional<Integer> maxAge, boolean refresh) {
            this.maxAge = maxAge;
            this.refresh = refresh;
            receive(ReceiveBuilder
                    .match(PutRequest.class, req -> receivePutRequest(req.key, req.value))
                    .match(RemoveRequest.class, req -> receiveRemoveRequest(req.key))
                    .match(GetRequest.class, req -> receiveGetRequest(req.key))
                    .match(GetSuccess.class, reply -> receiveGetSuccess(reply))
                    .match(NotFound.class, reply -> receiveGetNotFound(reply))
                    .match(UpdateResponse.class, reply -> {})
                    .build());
        }
        

        /**
         * Client request to put an entry
         */
        private void receivePutRequest(String key, Object value) {
            CacheItem item = new CacheItem(
                    key, value, System.currentTimeMillis());
            
            Update<LWWMap<Object>> update = new Update<>(dataKey(key), LWWMap.create(), writeLocal(),
                    curr -> curr.put(node, key, item));
            replicator.tell(update, self());
        }

        /**
         * Client request to remove an entry
         */
        private void receiveRemoveRequest(String key) {
            Update<LWWMap<Object>> update = new Update<>(dataKey(key), LWWMap.create(), writeLocal(),
                    curr -> curr.remove(node, key));
            replicator.tell(update, self());
        }
        
        /**
         * Receive a client request for cache entry
         */
        private void receiveGetRequest(String key) {
            Optional<Object> ctx = Optional.of(new DefaultContextObject(key, sender()));
            Get<LWWMap<Object>> get = new Get<>(dataKey(key), readLocal(), ctx);
            replicator.tell(get, self());
        }

        /**
         * Receive successful response to a GetRequest from replicator
         */
        private void receiveGetSuccess(GetSuccess<LWWMap<Object>> getReply) {
            
            if (maxAge.isPresent() && lastCleanup + (30 * 1000) < System.currentTimeMillis()) {
                cleanup(getReply.dataValue());
                lastCleanup = System.currentTimeMillis();
            }
            
            DefaultContextObject req = (DefaultContextObject) getReply.getRequest().get();
            Option<Object> valueOption = getReply.dataValue().get(req.key);
            Optional<Object> reply = Optional.empty();
            
            if (valueOption.isDefined()) {
                CacheItem item = (CacheItem) valueOption.get();
                
                reply = Optional.of(item.value);

                if (maxAge.isPresent()) {
                    long age = (System.currentTimeMillis() - item.timestamp);
                    if (age > maxAge.get() * 1000) {
                        // Expired
                        reply = Optional.empty();
                        receiveRemoveRequest(req.key);
                    }
                    else if (refresh) {
                        // Insert new item with updated timestamp:
                        Object value = item.value;
                        receivePutRequest(req.key, value);
                    }
                }
            }
            req.replyTo.tell(new GetReply(req.key, reply), self());
        }
        
        /**
         * Receive "not found" response from replicator
         */
        private void receiveGetNotFound(NotFound<LWWMap<Object>> n) {
            DefaultContextObject req = (DefaultContextObject) n.getRequest().get();
            req.replyTo.tell(new GetReply(req.key, Optional.empty()), self());
        }

        private Key<LWWMap<Object>> dataKey(String entryKey) {
            return LWWMapKey.create("cache-" + Math.abs(entryKey.hashCode()) % 100);
        }
        
        
        private void cleanup(LWWMap<Object> lwwmap) {
            Map<String, Object> map = 
                    JavaConversions.mapAsJavaMap(lwwmap.entries());
            for (String k: map.keySet()) {
                CacheItem item = (CacheItem) map.get(k);
                maybeExpire(item);
            }
        }

        private void maybeExpire(CacheItem item) {
            if (maxAge.isPresent()) {
                long age = (System.currentTimeMillis() - item.timestamp);
                if (age > maxAge.get() * 1000) {
                    // Expired
                    receiveRemoveRequest(item.key);
                }
            }
        }
    }
}
