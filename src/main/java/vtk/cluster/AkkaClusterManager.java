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
package vtk.cluster;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.LeaderChanged;
import akka.cluster.ClusterEvent.MemberEvent;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.cluster.Member;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.collection.JavaConversions;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import vtk.context.ApplicationInitializedEvent;

public class AkkaClusterManager implements ApplicationListener<ApplicationInitializedEvent> {
    private final static Logger logger = LoggerFactory.getLogger(AkkaClusterManager.class);
    private final static Object LEAVE = new Object();

    Collection<ClusterAware> clusterComponents;
    
    private ActorSystem system;
    private ActorRef clusterListener;

    public AkkaClusterManager(ActorSystem system, Collection<ClusterAware> clusterComponents) {
        this.system = system;
        this.clusterComponents = clusterComponents;
    }
    
    @Override
    public void onApplicationEvent(ApplicationInitializedEvent event) {
        ActorRef subscriptionActor = system.actorOf(
                Props.create(SubscriptionActor.class), "subscription-actor");
        this.clusterListener = system.actorOf(
                Props.create(ClusterListener.class, subscriptionActor, clusterComponents),
                "cluster-listener"
        );
    }

    public Thread destroy() throws InterruptedException {
        Cluster cluster = Cluster.get(system);
        logger.info("Removing node from cluster: {}", cluster.selfAddress());
        cluster.registerOnMemberRemoved(new Runnable() {
            @Override
            public void run() {
                // shut down ActorSystem
                system.terminate();
            }
        });
        Thread akkaShutdown =  new Thread() {
            @Override
            public void run() {
                // In case ActorSystem shutdown takes longer than 10 seconds,
                // exit the JVM forcefully anyway.
                // We must spawn a separate thread to not block current thread,
                // since that would have blocked the shutdown of the ActorSystem.
                try {
                    Await.ready(system.whenTerminated(), Duration.create(10, TimeUnit.SECONDS));
                    logger.info("Node gracefully removed: {}", cluster.selfAddress());
                } catch (Exception e) {
                    logger.warn("Node abruptly removed: {}", cluster.selfAddress());
                }

            }
        };
        akkaShutdown.start();
        clusterListener.tell(LEAVE, null);
        return akkaShutdown;
    }
    
    private static class SubscriptionActor extends UntypedActor {
        LoggingAdapter log = Logging.getLogger(getContext().system(), this);

        private List<Subscription> subscriptions = new ArrayList<>();
        
        @Override
        public void onReceive(Object msg) throws Exception {
            if (msg instanceof Subscription) {
                Subscription sub = (Subscription) msg;
                subscriptions.add(sub);
            }
            else if (msg instanceof Unsubscribe) {
                Unsubscribe unsub = (Unsubscribe) msg;
                subscriptions = subscriptions.stream()
                    .filter(s -> s.clusterComponent != unsub.clusterComponent)
                    .collect(Collectors.toList());
            }
            else if (msg instanceof ClusterToAppMessage) {
                ClusterToAppMessage clusterMsg = (ClusterToAppMessage) msg;
                
                subscriptions.forEach(sub -> {
                    if (clusterMsg.payload.getClass().isAssignableFrom(sub.msgClass)) {
                        log.debug("Forward cluster message {} to {}", msg, sub.clusterComponent);
                        try { 
                            sub.clusterComponent.clusterMessage(clusterMsg.payload); 
                        }
                        catch (Exception e) {
                            log.error(e, "Forward cluster message {} to {} failed",
                                    msg, sub.clusterComponent);
                        }
                    }
                });
            }
            else {
                unhandled(msg);
            }
        }
    }

    private static class ClusterListener extends UntypedActor {
        LoggingAdapter log = Logging.getLogger(getContext().system(), this);
        private ActorRef subscriptionActor;
        Cluster cluster = Cluster.get(getContext().system());
        private List<ClusterAware> appClusterComponents;
        
        @SuppressWarnings("unused")
        public ClusterListener(ActorRef subscriptionActor, List<ClusterAware> clusterComponents) {
            this.subscriptionActor = subscriptionActor;
            this.appClusterComponents = clusterComponents;
            log.info("Create cluster listener: components=" + clusterComponents);
            
            appClusterComponents.forEach(clusterComponent -> {
                ClusterContext context = new ClusterContextImpl(
                        subscriptionActor, clusterComponent, getSelf());
                clusterComponent.clusterContext(context);
            });
        }
        
        private void switchState(ClusterState state) {
            appClusterComponents.forEach(component -> {
                log.debug("Notify: " + component + ": " + state);
                component.roleChange(state.role());
            });
        }

        @Override
        public void preStart() {
            cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(),
                              MemberEvent.class, UnreachableMember.class,
                              LeaderChanged.class);
        }

        @Override
        public void postStop() {
            cluster.unsubscribe(getSelf());
        }

        @Override
        public void onReceive(Object message) {
            log.debug("Recv: {}", message);
            if (cluster.isTerminated()) return;
            if (message == LEAVE) {
                cluster.leave(cluster.selfAddress());
            }
            else if (message instanceof MemberUp) {
                MemberUp up = (MemberUp) message;
                log.info("Member is Up: {}", up.member());
            }
            else if (message instanceof UnreachableMember) {
                UnreachableMember unreachable = (UnreachableMember) message;
                log.info("Member detected as unreachable: {}", unreachable.member());
            }
            else if (message instanceof MemberRemoved) {
                MemberRemoved removed = (MemberRemoved) message;
                log.info("Member is Removed: {}", removed.member());

            }
            else if (message instanceof LeaderChanged) {
                LeaderChanged changed = (LeaderChanged) message;

                if (changed.getLeader().equals(cluster.selfAddress())) {
                    log.info("Change to master mode: {}", cluster.selfAddress());
                    switchState(new ClusterState(
                            ClusterRole.MASTER,
                            cluster.selfAddress().toString(), members()));
                }
                else {
                    log.info("Change to slave mode: {}", cluster.selfAddress());
                    switchState(new ClusterState(
                            ClusterRole.SLAVE,
                            cluster.selfAddress().toString(), members()));
                }
            }
            else if (message instanceof AppToClusterMessage) {
                AppToClusterMessage msg = (AppToClusterMessage) message;

                List<Member> members = JavaConversions
                        .seqAsJavaList(cluster.state().members().toList());
                
                members.forEach(member -> {
                    if (!member.address().equals(cluster.selfAddress())) {
                        ClusterMessage clusterMessage = new ClusterMessage(msg.payload);
                        getContext().actorSelection(member.address() + "/user/cluster-listener")
                            .tell(clusterMessage, getSelf());
                    }
                });
            }
            else if (message instanceof ClusterMessage) {
                ClusterMessage msg = (ClusterMessage) message;
                subscriptionActor.tell(new ClusterToAppMessage(msg.payload), getSelf());
            }
            else {
                unhandled(message);
            }
        }

        private List<String> members() {
            List<Member> members = JavaConversions.seqAsJavaList(cluster.state().members().toList());
            List<String> memberAddrs = members.stream()
                    .map(m -> m.address().toString())
                    .collect(Collectors.toList());
            return memberAddrs;
        }
    }

    private static class Subscription {
        public final ClusterAware clusterComponent;
        public final Class<? extends Serializable> msgClass;
        
        public Subscription(ClusterAware clusterComponent, Class<? extends Serializable> msgClass) {
            this.clusterComponent = clusterComponent;
            this.msgClass = msgClass;
        }
        
        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + clusterComponent + "," + msgClass + ")";
        }
    }

    private static class Unsubscribe {
        public final ClusterAware clusterComponent;
        
        public Unsubscribe(ClusterAware clusterComponent) {
            this.clusterComponent = clusterComponent;
        }
        
        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + clusterComponent + ")";
        }
    }

    private static class AppToClusterMessage implements Serializable {
        private static final long serialVersionUID = 882137978891170594L;
        public final Object payload;
        public AppToClusterMessage(Object payload) {
            this.payload = payload;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + payload + ")";
        }
    }

    private static class ClusterToAppMessage implements Serializable {
        private static final long serialVersionUID = 140652585953838528L;
        public final Object payload;
        
        public ClusterToAppMessage(Object payload) {
            this.payload = payload;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + payload + ")";
        }
    }

    private static class ClusterMessage implements Serializable {
        private static final long serialVersionUID = -8821132695623282742L;
        public final Object payload;
        public ClusterMessage(Object payload) {
            this.payload = payload;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + payload + ")";
        }
    }


    private static class ClusterContextImpl implements ClusterContext {
        private ActorRef subscriptionActor;
        private ClusterAware clusterComponent;
        private ActorRef clusterListener;

        public ClusterContextImpl(ActorRef subscriptionActor,
                ClusterAware clusterComponent, ActorRef clusterListener) {
            this.subscriptionActor = subscriptionActor;
            this.clusterComponent = clusterComponent;
            this.clusterListener = clusterListener;
        }

        @Override
        public void subscribe(Class<? extends Serializable> msgClass) {
            subscriptionActor.tell(new Subscription(clusterComponent, msgClass), null);
        }

        @Override
        public void unsubscribe() {
            subscriptionActor.tell(new Unsubscribe(clusterComponent), null);
        }

        @Override
        public void clusterMessage(Object msg) {
            clusterListener.tell(new AppToClusterMessage(msg), null);
        }
    }
    
    private static final class ClusterState {
        private ClusterRole role;
        private List<String> members;
        private String self;

        ClusterState(ClusterRole role, String self, List<String> members) {
            if (role == null) throw new NullPointerException("role");
            if (self == null) throw new NullPointerException("self");
            if (members == null) throw new NullPointerException("members");
            this.role = role;
            this.self = self;
            this.members = Collections.unmodifiableList(members);
        }

        public ClusterRole role() { return role; }

        public List<String> members() { return members; }

        public String self() { return self; }

        @Override
        public String toString() {
            return getClass().getSimpleName() 
                    + "(" + role + ", " + members + ", " + self + ")";
        }
    }

}
