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

import org.apache.log4j.Logger;
import org.jboss.as.clustering.singleton.SingletonService;
import org.jboss.as.clustering.singleton.election.NamePreference;
import org.jboss.as.clustering.singleton.election.PreferredSingletonElectionPolicy;
import org.jboss.as.clustering.singleton.election.SimpleSingletonElectionPolicy;
import org.jboss.msc.service.DelegatingServiceContainer;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceController;

public class JBossClusterServiceActivator implements ServiceActivator {
    private final Logger log = Logger.getLogger(this.getClass());
    private String preferredMaster = null;

    @Override
    public void activate(ServiceActivatorContext context) {
        log.info("JBossClusterManager will be installed!");

        JBossClusterManager service = new JBossClusterManager();
        SingletonService<String> singleton = new SingletonService<String>(service, JBossClusterManager.SINGLETON_SERVICE_NAME);
        /*
         * The NamePreference is a combination of the node name (-Djboss.node.name) and the name of
         * the configured cache "singleton". If there is more than 1 node, it is possible to add more than
         * one name and the election will use the first available node in that list.
         *   -  To pass a chain of election policies to the singleton and tell JGroups to run the
         * singleton on a node with a particular name, uncomment the first line  and
         * comment the second line below.
         *   - To pass a list of more than one node, comment the first line and uncomment the
         * second line below.
         */
        if (preferredMaster != null) {
            singleton.setElectionPolicy(
                new PreferredSingletonElectionPolicy(
                    new SimpleSingletonElectionPolicy(), 
                    new NamePreference(preferredMaster + "/singleton")));
        }

        singleton.build(new DelegatingServiceContainer(context.getServiceTarget(), context.getServiceRegistry()))
            .setInitialMode(ServiceController.Mode.ACTIVE)
            .install()
        ;
    }
    
    public String getPreferredMaster() {
        return this.preferredMaster;
    }

    public void setPreferredMaster(String preferredMaster) {
        this.preferredMaster = preferredMaster;
    }
}
