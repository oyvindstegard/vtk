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

import java.io.InputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.LogManager;
import org.apache.log4j.xml.DOMConfigurator;
import org.jboss.as.clustering.singleton.SingletonService;
import org.jboss.as.clustering.singleton.election.NamePreference;
import org.jboss.as.clustering.singleton.election.PreferredSingletonElectionPolicy;
import org.jboss.as.clustering.singleton.election.SimpleSingletonElectionPolicy;
import org.jboss.msc.service.DelegatingServiceContainer;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceRegistryException;

public class JBossClusterServiceActivator implements ServiceActivator {
    private final Log log = LogFactory.getLog(this.getClass());
    private String preferredMaster = null;

    /**
     * Try to merge in additional configurations.
     *
     * Setup is done in this class because it is the earliest accessible
     * point in the application when running on JBoss.
     * Also, this setup is required because when JBoss is in charge of log
     * configuration the property is ignored.
     */
    private void readLogConfiguration() {
        String logConfigLocation = System.getProperty("log4j.configuration");
        if (logConfigLocation != null) {
            try {
                String resourceName = "/" + logConfigLocation;
                log.info("Appending application specific log configuration from " + resourceName);
                InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourceName);
                if (is == null) {
                    log.warn("InputStream is null: " + resourceName);
                } else {
                    new DOMConfigurator().doConfigure(is, LogManager.getLoggerRepository());
                }
            } catch (Exception e) {
                log.error("Failed to configure from: " + logConfigLocation, e);
            }
        }
    }

    @Override
    public void activate(ServiceActivatorContext context) {
        readLogConfiguration();

        log.info("JBossClusterManager will be installed!");
        try {
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
        } catch (Exception e) {
            throw new ServiceRegistryException("Unable to activate JBossClusterManager", e);
        }
    }

    public String getPreferredMaster() {
        return this.preferredMaster;
    }

    public void setPreferredMaster(String preferredMaster) {
        this.preferredMaster = preferredMaster;
    }
}
