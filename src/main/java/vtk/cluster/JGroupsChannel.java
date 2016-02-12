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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.util.ExposedByteArrayInputStream;
import org.jgroups.util.Util;

/**
 * Message system based on JGroups.
 */
public class JGroupsChannel extends ReceiverAdapter {
    private static final Log log = LogFactory.getLog(JGroupsChannel.class);
    private static final String channelName = "VtkCluster";

    /**
     * List of contexts for messaging.
     */
    private final List<ClusterContextImpl> clusterContexts =
         new ArrayList<ClusterContextImpl>();

    private final JChannel channel;

    public JGroupsChannel(String nodeName) throws Exception {
        channel = new JChannel();
        channel.setReceiver(this);
        // Does not work with multiple servers on a single machine. Discards too much.
        // Our own checks in receive(message) work better.
        //channel.setDiscardOwnMessages(true);
        channel.setName(nodeName);
        channel.connect(channelName);
        log.info("CHANNEL: connected to " + channelName);
    }

    public void close() {
        log.info("CHANNEL: closing " + channelName);
        channel.close();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(contexts=" + clusterContexts.size() + ")";
    }

    public void setComponents(List<ClusterAware> clusterComponents) throws Exception {
        // Prepare all the contexts first (since message receiving relies on them)
        for (ClusterAware clusterAware : clusterComponents) {
            clusterContexts.add(new ClusterContextImpl(clusterAware, channel));
        }
        // Tell the components
        for (ClusterContextImpl context : clusterContexts) {
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
     * Extract object within the message.
     *
     * This override of JGroups message.getObj() is required because the class 
     * loader used by JGroups internally may not know about the custom class.
     *
     * NB! This is only needed on receive, because sending is
     * already done in the context of the application.
     *
     * TODO: Passing a custom class loader to JGroups will be possible with
     * JGroups 3.5 and higher. Sadly, this first requires a much newer version
     * of JBoss EAP.
     *
     * JBoss EAP 6.4 has JGroups 3.2.13
     * JBoss EAP 7.0 Beta has JGroups 3.6.x (as of 2016-02-11)
     */
    private Object getMessageObject(Message msg) throws Exception {
        final Object obj;
        final byte[] buffer = msg.getRawBuffer();
        final int offset = msg.getOffset();
        final int length = msg.getLength();
        if (buffer[offset] == 2 /* TYPE_SERIALIZABLE (JGroups private API!) */) {
            // Skip type field at first position
            final ByteArrayInputStream inStream=new ExposedByteArrayInputStream(buffer, offset+1, length-1);
            final InputStream in=new ObjectInputStream(inStream);
            try {
                obj=((ObjectInputStream)in).readObject();
            }
            finally {
                Util.close(in);
            }
        } else {
            obj = msg.getObject();
        }
        return obj;
    }

    @Override
    public void receive(Message msg) {
        try {
            Object obj = getMessageObject(msg);
            log.debug("Received msg from " + msg.getSrc() + ": " + obj);
            if (clusterContexts.isEmpty()) {
                log.error(String.format("Lost msg '%s' from cluster because contexts are not set up yet.", msg));
            } else if (msg.getSrc().equals(channel.getAddress())) {
                log.debug(String.format("Ignored msg '%s' sent from self node", msg));
            } else {
                int forwardCount = 0;
                for (ClusterContextImpl context : clusterContexts) {
                    try {
                        forwardCount += context.receive(obj);
                    } catch (Exception e) {
                        log.error(String.format("Receive msg '%s' from cluster potentially to %s", msg, context), e);
                    }
                }
                if (forwardCount == 0) {
                    log.debug(String.format("No subscriptions for msg '%s': %s", msg, obj));
                }
            }
        } catch (Exception e) {
            log.error(String.format("Receive msg '%s' from cluster", msg), e);
        }
    }

    @Override
    public void viewAccepted(View view) {
        log.info("CHANNEL: Members = " + view.getMembers());
    }

    private static class ClusterContextImpl implements ClusterContext {
        private ClusterAware clusterComponent = null;
        private JChannel channel = null;
        private Set<Class<? extends Serializable>> msgClasses = new HashSet<Class<? extends Serializable>>(); //concurrency?

        private ClusterContextImpl(ClusterAware clusterComponent, JChannel channel) {
            this.clusterComponent = clusterComponent;
            this.channel = channel;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + clusterComponent + ")";
        }

        private ClusterAware getComponent() {
            return clusterComponent;
        }

        private int receive(Object obj) throws Exception {
            int forwardCount = 0;
            for (Class<? extends Serializable> msgClass : msgClasses) {
                if (obj.getClass().isAssignableFrom(msgClass)) {
                    log.debug(String.format("Forward cluster message %s to %s", obj, clusterComponent));
                    clusterComponent.clusterMessage(obj);
                    forwardCount++;
                }
            }
            return forwardCount;
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
