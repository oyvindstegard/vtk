/* Copyright (c) 2006, University of Oslo, Norway
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
package vtk.web.context;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.ApplicationListener;
import org.springframework.web.context.support.ServletRequestHandledEvent;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

import vtk.web.RequestContext;
import vtk.web.service.Service;

public class RequestLoadListener implements ApplicationListener<ServletRequestHandledEvent> {
    private final ExecutorService executorService;
    private final MetricsHandler metricsHandler;
    
    public RequestLoadListener(MetricRegistry registry) {
        this.executorService = Executors.newSingleThreadExecutor(
                r -> new Thread(r));
        this.metricsHandler = new MetricsHandler(registry);
    }
    

    @Override
    public void onApplicationEvent(ServletRequestHandledEvent event) {
        RequestContext requestContext = RequestContext.getRequestContext();
        if (requestContext == null) return;
        Service service = requestContext.getService();
        boolean auth = requestContext.getPrincipal() != null;
        VrtxEvent vrtxEvent = new VrtxEvent(event, service, auth);
        executorService.submit(() -> {
            metricsHandler.event(vrtxEvent);
        });
    }


    private static class VrtxEvent {
        public final Service service;
        public final boolean auth;
        public final boolean failure;
        public final long processingTimeMillis;
        public final long statusCode;
        public VrtxEvent(ServletRequestHandledEvent reqEvent, Service service, boolean auth) {
            this.service = service;
            this.auth = auth;
            this.failure = reqEvent.wasFailure();
            this.processingTimeMillis = reqEvent.getProcessingTimeMillis();
            this.statusCode = reqEvent.getStatusCode();
        }
    }

    private static final class MetricsHandler {
        private final MetricRegistry registry;
        private final Counter requests;
        private final Meter errors;
        private final Meter authenticated;
        private final Histogram processing;
        private final Map<String, Counter> services = new HashMap<>();;
        private final Map<String, Counter> status = new HashMap<>();;

        public MetricsHandler(MetricRegistry registry) {
            this.registry = registry;
            this.requests = registry.counter("requests.handled");
            this.errors = registry.meter("requests.failed");
            this.authenticated = registry.meter("requests.authenticated");
            this.processing = registry.histogram("requests.processing.time");
        }

        public void event(VrtxEvent event) {
            requests.inc();
            processing.update(event.processingTimeMillis);
            if (event.failure) {
                errors.mark();
            }
            if (event.service != null) {
                String name = "requests.services." + event.service.getName();
                Counter counter = services.get(name);
                if (counter == null) {
                    counter = registry.counter(name);
                    services.put(name, counter);
                }
                counter.inc();
            }
            if (event.auth) {
                authenticated.mark();
            }
            if (event.statusCode != -1) {
                String statusKey = String.valueOf("requests.status." + event.statusCode);
                Counter counter = status.get(statusKey);
                if (counter == null) {
                    counter = registry.counter(statusKey);
                    status.put(statusKey, counter);
                }
                counter.inc();
            }
        }
    }
}
