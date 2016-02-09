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
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.util.ExposedByteArrayInputStream;
import org.jgroups.util.Util;

/**
 * Message system based on JGroups.
 */
public class JGroupsChannel extends ReceiverAdapter {
    private static Log log = LogFactory.getLog(JGroupsChannel.class);
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
        channel.setReceiver(this);
        channel.connect(channelName);
        log.info("CHANNEL: connected to " + channelName);
    }

    /**
     * Extract object within the message.
     * @param msg
     * @return
     * @throws Exception
     */
    private Object getMessageObject(Message msg) throws Exception {
        Object obj = null;
        try {
            obj = msg.getObject();
        } catch (IllegalArgumentException e) {
            /**
             * Assume deserialization failed because it could not find the class
             * because of wrong class loader.
             * Try again with the application class loader.
             * NB! This hack is only needed on receive, because sending is
             * already done in the context of the application.
             */
            byte[] buffer = msg.getRawBuffer();
            int offset = msg.getOffset();
            int length = msg.getLength();
            // Skip type field at first position
            ByteArrayInputStream in_stream=new ExposedByteArrayInputStream(buffer, offset+1, length-1);
            ClassLoader loader = this.getClass().getClassLoader();
            InputStream in=new ObjectInputStream(in_stream) {
                protected Class<?> resolveClass(ObjectStreamClass desc)
                        throws IOException, ClassNotFoundException {
                    return Class.forName(desc.getName(), false, loader);
                }
            };
            try {
                obj=((ObjectInputStream)in).readObject();
            }
            finally {
                Util.close(in);
            }
        }
        return obj;
    }

    @Override
    public void receive(Message msg) {
        try {
            Object obj = getMessageObject(msg);
            log.debug("Received msg from " + msg.getSrc() + ": " + obj);
            if (!msg.getSrc().equals(channel.getAddress())) {
                for (ClusterContextImpl context : clusterContexts) {
                    context.receive(msg.getSrc(), obj);
                }
            } else {
                log.debug(String.format("Ignored msg '%s' sent from self node", msg));
            }
        } catch (Exception e) {
            log.error(String.format("Receive msg '%s' from cluster", msg), e);
        }
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

        private void receive(Address src, Object obj) throws Exception {
            for (Class<? extends Serializable> msgClass : msgClasses) {
                if (obj.getClass().isAssignableFrom(msgClass)) {
                    log.debug(String.format("Forward cluster message %s to %s", obj, clusterComponent.getClass().getName()));
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
