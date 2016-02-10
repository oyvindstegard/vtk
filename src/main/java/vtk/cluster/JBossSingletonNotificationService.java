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

import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * JBoss service for notifying master role to {@link JBossClusterManager}.
 * 
 * This class is not instantiated by Spring, but through JBoss Modular Service Container framework.
 * 
 * It is connected to {@link JBossClusterManager} through static fields.
 */
public class JBossSingletonNotificationService implements Service<String> {

    public static final ServiceName SINGLETON_SERVICE_NAME = 
            ServiceName.JBOSS.append("vtk", "cluster", "JBossSingletonNotificationService");
    
    private static JBossClusterManager clusterManagerInstance;
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    
    @Override
    public void start(StartContext sc) throws StartException {
        if (!STARTED.compareAndSet(false, true)) {
            throw new StartException("Unexpected state: already started");
        }
        notify(STARTED);
    }

    @Override
    public void stop(StopContext sc) {
        if (!STARTED.compareAndSet(true, false)) {
            // TODO log warn ? or why bother
        }
        
        notify(STARTED);
    }
    
    // Called by whatever thread that is doing the Spring context init (from JBossClusterManager)
    static void setJBossClusterManager(JBossClusterManager instance) {
        clusterManagerInstance = instance;
        notify(STARTED); 
    }
    
    private static void notify(AtomicBoolean serviceStarted) {
        if (clusterManagerInstance != null) {
            if (serviceStarted.get()) {
                clusterManagerInstance.singletonServiceStarted();
            } else {
                clusterManagerInstance.singletonServiceStopped();
            }
        }
    }

    @Override
    public String getValue() throws IllegalStateException, IllegalArgumentException {
        if (clusterManagerInstance != null) {
            return clusterManagerInstance.getValue();
        }
        throw new IllegalStateException("No JBossClusterManager registered yet");
    }
    
}
