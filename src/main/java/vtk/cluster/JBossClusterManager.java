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

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
public class JBossClusterManager implements ApplicationListener<ContextRefreshedEvent> {

    /**
     * Logger is non-static on purpose in order to separate instances.
     */
    private final Log log = LogFactory.getLog(JBossClusterManager.class);

    /**
     * List of components to notify of state change.
     */
    private final List<ClusterAware> clusterComponents;
    
    private ClusterRole currentRole = ClusterRole.SLAVE;

    /**
     * Message system.
     */
    private JGroupsChannel channel = null;

    public JBossClusterManager(List<ClusterAware> clusterComponents) {
        this.clusterComponents = clusterComponents;
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
            (currentRole == ClusterRole.MASTER ? "MASTER" : "SLAVE"),
            System.getProperty("jboss.node.name"),
            (clusterComponents == null) ? -1 : clusterComponents.size());
    }
    
    /**
     * Notify the role to the cluster aware components.
     */
    private void notifyRole() {
        if (clusterComponents == null) {
            log.warn(String.format("NOTIFY ROLE: %s. The service has no components yet!", currentRole));
        } else {
            log.info(String.format("NOTIFY ROLE: %s to %d components.", currentRole, 
                    clusterComponents.size()));
            for (ClusterAware clusterAware : clusterComponents) {
                // NB! Must not block!
                // TODO Maybe catch Throwable here, so failure in one component will
                // not cause message loss for later ones in list
                clusterAware.roleChange(currentRole);
            }
        }
    }

    // Callback from singleton notification service
    void singletonServiceStarted() {
        currentRole = ClusterRole.MASTER;
        notifyRole();
    }
    
    // Callback from singleton notification service
    void singletonServiceStopped() {
        currentRole = ClusterRole.SLAVE;
        notifyRole();
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        log.info(String.format("CONFIG: %d cluster components registered", clusterComponents.size()));
        if (channel == null) {
            try {
                channel = new JGroupsChannel(clusterComponents);
            } catch (Exception e) {
                log.warn("Failed to create JGroups channel", e);
            }
        }
        
        // Register with singleton notification service. This will provide
        // callbacks with current status
        JBossSingletonNotificationService.setJBossClusterManager(this);
    }
}
