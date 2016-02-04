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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;

/**
 * Message system based on JGroups.
 */
public class JGroupsChannel {
    private static Logger log = Logger.getLogger(JGroupsChannel.class);
    private static final String channelName = "VtkCluster";

    /**
     * List of contexts for messaging.
     */
    private List<ClusterContextImpl> clusterContexts = null;

    private JChannel channel = null;

    public JGroupsChannel(List<ClusterAware> clusterComponents) throws Exception {
        connect();
        setComponents(clusterComponents);
    }

    @Override
    protected void finalize() {
        close();
    }

    public void close() {
        if (channel != null) {
            log.info("CHANNEL: closing " + channelName);
            channel.close();
            channel = null;
        }
    }

    private void setComponents(List<ClusterAware> clusterComponents) throws Exception {
        // Prepare all the contexts first (since message receiving relies on them)
        List<ClusterContextImpl> contexts = new ArrayList<ClusterContextImpl>();
        for (ClusterAware clusterAware : clusterComponents) {
            contexts.add(new ClusterContextImpl(clusterAware, channel));
        }
        // Tell the components
        clusterContexts = Collections.unmodifiableList(new ArrayList<>(contexts));
        for (ClusterContextImpl context : contexts) {
            ClusterAware clusterAware = context.getComponent();
            try {
                log.info(String.format("NOTIFY CONTEXT: to %s", clusterAware));
                clusterAware.clusterContext(context);
            } catch (Exception e) {
                log.error(String.format("NOTIFY CONTEXT: to %s", clusterAware), e);
            }
        }
    }

    /**
     * Connect to messaging system.
     */
    private void connect() throws Exception {
        channel = new JChannel();
        channel.setReceiver(new ReceiverAdapter() {
            public void receive(Message msg) {
                try {
                    log.debug("Received msg from " + msg.getSrc() + ": " + msg.getObject());
                    for (ClusterContextImpl context : clusterContexts) {
                        context.receive(msg);
                    }
                } catch (Exception e) {
                    log.error(String.format("Receive msg '%s' from cluster", msg), e);
                }
            }
        });
        channel.connect(channelName);
        log.info("CHANNEL: connected to " + channelName);
    }

    private static class ClusterContextImpl implements ClusterContext {
        private ClusterAware clusterComponent = null;
        private JChannel channel = null;
        private Set<Class<? extends Serializable>> msgClasses = new HashSet<Class<? extends Serializable>>(); //concurrency?

        private ClusterContextImpl(ClusterAware clusterComponent, JChannel channel) {
            this.clusterComponent = clusterComponent;
            this.channel = channel;
        }

        private ClusterAware getComponent() {
            return clusterComponent;
        }

        private void receive(Message msg) throws Exception {
            Object obj = msg.getObject();
            for (Class<? extends Serializable> msgClass : msgClasses) {
                if (obj.getClass().isAssignableFrom(msgClass)) {
                    log.debug(String.format("Forward cluster message %s to %s", msg, msg.getSrc()));
                    clusterComponent.clusterMessage(obj);
                }
            }
        }

        @Override
        public void subscribe(Class<? extends Serializable> msgClass) {
            msgClasses.add(msgClass);
        }

        @Override
        public void unsubscribe() {
            msgClasses.clear();
        }

        @Override
        public void clusterMessage(Object msg) {
            try {
                channel.send(new Message((Address)null, msg));
            } catch (Exception e) {
                log.error(String.format("Send msg '%s' to cluster", msg), e);
            }
        }
    }
}
