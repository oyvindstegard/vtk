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
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationListener;
import org.springframework.web.context.support.ServletRequestHandledEvent;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;

import vtk.web.RequestContext;
import vtk.web.service.Service;

public class RequestLoadListener implements ApplicationListener<ServletRequestHandledEvent> {

    private final Meter requests;
    private final Meter errors;
    private final Histogram processing;
    private final MetricSet services;
    
    public RequestLoadListener(MetricRegistry registry, List<Service> services) {
        this.requests = registry.meter("requests.handled");
        this.processing = registry.histogram("requests.processing.time");
        this.errors = registry.meter("requests.failed");

        final Map<String, Metric> serviceMap = new HashMap<>();
        services.stream().forEach(service -> {
            serviceMap.put("requests.services." + service.getName(), new Counter());
        });
        this.services = new MetricSet() {
            @Override
            public Map<String, Metric> getMetrics() {
                return serviceMap;
            }
        };
        registry.registerAll(this.services);
    }
    
    @Override
    public void onApplicationEvent(ServletRequestHandledEvent event) {
        requests.mark();
        processing.update(event.getProcessingTimeMillis());
        if (event.wasFailure()) {
            errors.mark();
        }
        RequestContext requestContext = RequestContext.getRequestContext();
        Service service = requestContext.getService();
        if (service != null) {
            Counter meter = (Counter) services.getMetrics().get("request.services." + service.getName());
            meter.inc();
        }
    }
}
