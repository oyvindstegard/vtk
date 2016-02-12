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
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * JBoss cluster manager using a JBoss singleton service to detect the
 * MASTER/SLAVE role. A started service is equated with MASTER.
 */
public class JBossClusterManager implements ApplicationListener<ContextRefreshedEvent> {
    private final Log log = LogFactory.getLog(JBossClusterManager.class);

    /**
     * The underlying components that receive synchronous notification.
     * Calling them directly is a bad idea as they may block the manager.
     */
    private final List<ClusterAware> underlyingClusterComponents;

    /**
     * List of components to notify of state change.
     * List cannot be filled on construction, because the
     * underlying list may not be fully populated yet.
     */
    private final List<ClusterAware> clusterComponents = new ArrayList<ClusterAware>();

    private ClusterRole currentRole = ClusterRole.SLAVE;

    /**
     * Message system.
     */
    private final JGroupsChannel channel;

    private final String nodeName = System.getProperty("jboss.node.name");

    public JBossClusterManager(List<ClusterAware> clusterComponents) throws Exception {
        this.underlyingClusterComponents = clusterComponents;
        channel = new JGroupsChannel(nodeName);
    }

    /**
     * Called by Spring on tear down.
     */
    public void destroy() {
        channel.close();
        log.info("DESTROY: " + this);
    }

    /**
     * Short status.
     */
    @Override
    public String toString() {
        return String.format(
            "%s is %s at %s with %d components",
            getClass().getSimpleName(),
            currentRole,
            nodeName,
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
        for (ClusterAware clusterAware : underlyingClusterComponents) {
            clusterComponents.add(new AsyncClusterAware(clusterAware));
        }

        log.info(String.format("CONFIG: %d cluster components registered", clusterComponents.size()));
        try {
            channel.setComponents(clusterComponents);
        } catch (Exception e) {
            // TODO: throw exception instead of just logging it?
            log.error("Failed to set components for JGroups channel", e);
        }

        // Register with singleton notification service. This will provide
        // callbacks with current status
        JBossSingletonNotificationService.setJBossClusterManager(this);
    }
}
