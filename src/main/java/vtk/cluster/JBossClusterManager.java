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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * Implemented using a cluster wide singleton pattern.
 * Only one cluster manager is active at any one point.
 *
 * NB! Internal state is kept in static variables because Spring will
 * create a new instance of the manager on the local server in addition
 * to the instance created by JBossClusterServiceActivator.
 */
public class JBossClusterManager implements Service<String>, ApplicationListener<ContextRefreshedEvent> {
    public static final ServiceName SINGLETON_SERVICE_NAME = ServiceName.JBOSS.append("vtk", "cluster", "JBossClusterManager");

    /**
     * Logger is non-static on purpose in order to separate instances.
     */
    private Log log = LogFactory.getLog(JBossClusterManager.class);
    private static int createCount = 0;

    /**
     * A flag whether the service is started.
     */
    private static final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * List of components to notify of state change.
     */
    private static List<ClusterAware> clusterComponents = null;

    /**
     * Message system.
     */
    private static JGroupsChannel channel = null;

    public JBossClusterManager() {
        log = LogFactory.getLog(JBossClusterManager.class.getName() + "-" + createCount);
        createCount++;
        log.info("CONSTRUCT: " + getValue());
    }

    @Override
    protected void finalize() {
        if (channel != null) {
            channel.close();
            channel = null;
        }
        log.info("FINALIZE: " + getValue());
    }

    /**
     * @return the name of the server node
     */
    public String getValue() throws IllegalStateException, IllegalArgumentException {
        return String.format(
            "%s is %s at %s with %d components",
            JBossClusterManager.class.getSimpleName(),
            (started.get() ? "started" : "not started"),
            System.getProperty("jboss.node.name"),
            (clusterComponents == null) ? -1 : clusterComponents.size());
    }

    public void start(StartContext startContext) throws StartException {
        if (!started.compareAndSet(false, true)) {
            throw new StartException("The service is still started!");
        }
        log.info("Start HASingleton service '" + this.getClass().getName() + "', become MASTER.");
        notifyRole();
    }

    public void stop(StopContext stopContext) {
        if (!started.compareAndSet(true, false)) {
            log.warn("The service '" + this.getClass().getName() + "' is not active!");
        } else {
            log.info("Stop HASingleton service '" + this.getClass().getName() + "', become SLAVE.");
        }
        notifyRole();
    }

    /**
     * Set cluster aware components.
     * Set up messaging.
     * Delay role notification until Spring has completed setup.
     * (NB! Assumes this class will be used in a Spring environment.)
     */
    public void setClusterComponents(List<ClusterAware> clusterComponents) throws Exception {
        log.info(String.format("CONFIG: Setting %d cluster components", clusterComponents.size()));
        JBossClusterManager.clusterComponents = Collections.unmodifiableList(new ArrayList<>(clusterComponents));
        if (channel == null) {
            channel = new JGroupsChannel(JBossClusterManager.clusterComponents);
        }
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        notifyRole();
    }

    /*
     * Notify the role to the cluster aware components.
     */
    private void notifyRole() {
        ClusterRole role = started.get() ? ClusterRole.MASTER : ClusterRole.SLAVE;
        if (clusterComponents == null) {
            log.warn(String.format("NOTIFY ROLE: %s. The service has no components yet!", role));
        } else {
            log.info(String.format("NOTIFY ROLE: %s to %d components.", role, clusterComponents.size()));
            for (ClusterAware clusterAware : clusterComponents) {
                // NB! Must not block!
                clusterAware.roleChange(role);
            }
        }
    }
}
