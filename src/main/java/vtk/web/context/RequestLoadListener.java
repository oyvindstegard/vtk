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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.springframework.context.ApplicationListener;
import org.springframework.web.context.support.ServletRequestHandledEvent;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

public class RequestLoadListener implements ApplicationListener<ServletRequestHandledEvent>, Filter {
    private final ExecutorService executorService;
    private final MetricsHandler metricsHandler;
    private Counter activeRequests;
    
    public RequestLoadListener(MetricRegistry registry) {
        this.executorService = Executors.newSingleThreadExecutor(
                r -> new Thread(r));
        this.metricsHandler = new MetricsHandler(registry);
        this.activeRequests = registry.counter("requests.active"); 
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        try {
            activeRequests.inc();
            chain.doFilter(request, response);
        }
        finally {
            activeRequests.dec();
        }
    }

    @Override
    public void onApplicationEvent(ServletRequestHandledEvent event) {
        VrtxEvent vrtxEvent = new VrtxEvent(event);
        executorService.submit(() -> {
            metricsHandler.event(vrtxEvent);
        });
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException { }

    @Override
    public void destroy() { }

    private static class VrtxEvent {
        public final boolean failure;
        public final long processingTimeMillis;
        public final Optional<Long> statusCode;

        public VrtxEvent(ServletRequestHandledEvent reqEvent) {
            this.failure = reqEvent.wasFailure();
            this.processingTimeMillis = reqEvent.getProcessingTimeMillis();
            long sc = reqEvent.getStatusCode();
            this.statusCode = sc != -1 ? Optional.of(sc) : Optional.empty();
        }
    }
    
    private static final class MetricsHandler {
        private final MetricRegistry registry;
        private final Meter requests;
        private final Meter errors;
        private final Histogram processing;
        private final Map<String, Counter> status = new HashMap<>();;

        public MetricsHandler(MetricRegistry registry) {
            this.registry = registry;
            this.requests = registry.meter("requests.processed");
            this.errors = registry.meter("requests.failed");
            this.processing = registry.histogram("requests.processing.time");
        }

        public void event(VrtxEvent event) {
            requests.mark();
            processing.update(event.processingTimeMillis);
            if (event.failure) {
                errors.mark();
            }
            event.statusCode.ifPresent(code -> {
                String statusKey = String.valueOf("requests.status." + event.statusCode);
                Counter counter = status.get(statusKey);
                if (counter == null) {
                    counter = registry.counter(statusKey);
                    status.put(statusKey, counter);
                }
                counter.inc();
            });
        }
    }

}
