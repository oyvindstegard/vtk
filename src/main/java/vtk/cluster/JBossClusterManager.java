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

import org.apache.log4j.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Implemented using a cluster wide singleton pattern.
 * Only one cluster manager is active at any one point.
 * 
 * NB! Internal state is kept in static variables because Spring will
 * create a new instance of the manager on the local server in addition
 * to the instance created by JBossClusterServiceActivator.
 *   
 * @author gyrdtl
 */
public class JBossClusterManager implements Service<String> {
    private static final Logger log = Logger.getLogger(JBossClusterManager.class);    
    public static final ServiceName SINGLETON_SERVICE_NAME = ServiceName.JBOSS.append("vtk", "cluster", "JBossClusterManager");
    /**
     * A flag whether the service is started.
     */
    private static final AtomicBoolean started = new AtomicBoolean(false);
    
    /**
     * List of components to notify of state change.
     */
    private static List<ClusterAware> clusterComponents = null;
    
    public JBossClusterManager() {
        log.info("CONSTRUCT: " + getValue());
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
        log.info("Start HASingleton service '" + this.getClass().getName() + "' (MASTER)");
        notifyRole();
    }

    public void stop(StopContext stopContext) {
        if (!started.compareAndSet(true, false)) {
            log.warn("The service '" + this.getClass().getName() + "' is not active!");
        } else {
            log.info("Stop HASingleton service '" + this.getClass().getName() + "'");
        }
        notifyRole();
    }
    
    public void setClusterComponents(List<ClusterAware> clusterComponents) {
        log.info(String.format("CONFIG: Setting %d cluster components", clusterComponents.size()));
        JBossClusterManager.clusterComponents = Collections.unmodifiableList(new ArrayList<>(clusterComponents));
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
