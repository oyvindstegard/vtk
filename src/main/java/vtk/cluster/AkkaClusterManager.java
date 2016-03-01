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
import java.util.stream.Collectors;

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

public class AkkaClusterManager {

    private final static Object LEAVE = new Object();
    
    Collection<ClusterAware> clusterComponents;
    
    private ActorSystem system;
    private ActorRef clusterListener;

    public AkkaClusterManager(ActorSystem system, Collection<ClusterAware> clusterComponents) {
        this.system = system;
        this.clusterComponents = clusterComponents;
    }

    public void init() {
        
        ActorRef subscriptionActor = system.actorOf(
                Props.create(SubscriptionActor.class), "subscription-actor");

        clusterListener = system.actorOf(
                Props.create(ClusterListener.class, subscriptionActor, clusterComponents),
                "cluster-listener");
    }
    
    public void destroy() {
        clusterListener.tell(LEAVE, null);
        system.terminate();
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
                for (Subscription sub: subscriptions) {
                    if (clusterMsg.payload.getClass().isAssignableFrom(sub.msgClass)) {
                        log.debug("Forward cluster message {} to {}", msg, sub.clusterComponent);
                        sub.clusterComponent.clusterMessage(clusterMsg.payload);
                    }
                }
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
            this.appClusterComponents = Collections.unmodifiableList(new ArrayList<>(clusterComponents));

            for (ClusterAware clusterComponent: appClusterComponents) {
                ClusterContext context = new ClusterContextImpl(
                        subscriptionActor, clusterComponent, getSelf());
                clusterComponent.clusterContext(context);
            }
        }

        private void switchState(ClusterState state) {
            for (ClusterAware aware: appClusterComponents) {
                aware.roleChange(state.role());
            }
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
            if (message == LEAVE) {
                cluster.leave(cluster.selfAddress());
            }
            else if (message instanceof MemberUp) {
                MemberUp mUp = (MemberUp) message;

                log.info("Member is Up: {}", mUp.member());
            }
            else if (message instanceof UnreachableMember) {
                UnreachableMember mUnreachable = (UnreachableMember) message;
                log.info("Member detected as unreachable: {}", mUnreachable.member());

            }
            else if (message instanceof MemberRemoved) {
                MemberRemoved mRemoved = (MemberRemoved) message;
                log.info("Member is Removed: {}", mRemoved.member());

            }
            else if (message instanceof LeaderChanged) {
                LeaderChanged lch = (LeaderChanged) message;
                if (lch.getLeader().equals(cluster.selfAddress())) {
                    log.info("Change to master mode");
                    switchState(new ClusterState(
                            ClusterRole.MASTER,
                            cluster.selfAddress().toString(), members()));
                }
                else {
                    log.info("Change to slave mode");
                    switchState(new ClusterState(
                            ClusterRole.SLAVE,
                            cluster.selfAddress().toString(), members()));
                }
            }
            else if (message instanceof MemberEvent) {

            }
            else if (message instanceof AppToClusterMessage) {
                AppToClusterMessage msg = (AppToClusterMessage) message;

                List<Member> members = JavaConversions.seqAsJavaList(cluster.state().members().toList());
                for (Member member: members) {

                    if (!member.address().equals(cluster.selfAddress())) {
                        ClusterMessage clusterMessage = new ClusterMessage();
                        clusterMessage.payload = msg.payload;
                        getContext().actorSelection(member.address() + "/user/cluster-listener")
                            .tell(clusterMessage, getSelf());
                    }
                }
            }
            else if (message instanceof ClusterMessage) {
                ClusterMessage msg = (ClusterMessage) message;
                ClusterToAppMessage appMsg = new ClusterToAppMessage();
                appMsg.payload = msg.payload;
                subscriptionActor.tell(appMsg, getSelf());
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
        ClusterAware clusterComponent;
        Class<? extends Serializable> msgClass;
        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + clusterComponent + "," + msgClass + ")";
        }
    }

    private static class Unsubscribe {
        ClusterAware clusterComponent;
    }

    private static class AppToClusterMessage implements Serializable {
        private static final long serialVersionUID = 882137978891170594L;
        Object payload;

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + payload + ")";
        }
    }

    private static class ClusterToAppMessage implements Serializable {
        private static final long serialVersionUID = 140652585953838528L;
        Object payload;

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + payload + ")";
        }
    }

    private static class ClusterMessage implements Serializable {
        private static final long serialVersionUID = -8821132695623282742L;
        Object payload;

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
            Subscription sub = new Subscription();
            sub.clusterComponent = this.clusterComponent;
            sub.msgClass = msgClass;
            subscriptionActor.tell(sub, null);
        }

        @Override
        public void unsubscribe() {
            Unsubscribe unsub = new Unsubscribe();
            unsub.clusterComponent = this.clusterComponent;
            subscriptionActor.tell(unsub, null);
        }

        @Override
        public void clusterMessage(Object msg) {
            AppToClusterMessage clusterMessage = new AppToClusterMessage();
            clusterMessage.payload = msg;
            clusterListener.tell(clusterMessage, null);
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
            return getClass().getSimpleName() + "(" + role + ")";
        }
    }

}
