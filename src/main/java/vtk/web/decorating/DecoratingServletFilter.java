/* Copyright (c) 2017, University of Oslo, Norway
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
package vtk.web.decorating;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.text.html.HtmlNodeFilter;
import vtk.text.html.HtmlPageParser;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.servlet.AbstractServletFilter;
import vtk.web.servlet.VTKServlet;

public class DecoratingServletFilter extends AbstractServletFilter {
    private static Logger logger = LoggerFactory.getLogger(DecoratingServletFilter.class);
    
    private HtmlPageParser htmlParser;
    private List<HtmlNodeFilterFactory> filters;
    private DecorationResolver resolver;
    private String preventParsingParameter;
    private String preventDecoratingParameter;
    private Optional<Map<String, String>> staticHeaders;
    private long maxSize;
    
    public DecoratingServletFilter(HtmlPageParser htmlParser,
            List<HtmlNodeFilterFactory> filters, DecorationResolver resolver,
            String preventDecoratingParameter,
            String preventParsingParameter,
            Map<String, String> staticHeaders,
            long maxSize) {
        this.htmlParser = htmlParser;
        this.filters = new ArrayList<>(filters);
        this.resolver = resolver;
        this.preventDecoratingParameter = preventDecoratingParameter;
        this.preventParsingParameter = preventParsingParameter;
        this.staticHeaders = Optional.ofNullable(staticHeaders).map(hdrs -> new HashMap<>(hdrs));
        this.maxSize = maxSize;
    }
    
    @Override
    public void doFilter(HttpServletRequest request, HttpServletResponse response, 
            FilterChain chain) throws IOException, ServletException {
        
        if ("true".equals(request.getAttribute(VTKServlet.INDEX_FILE_REQUEST_ATTRIBUTE))) {
            chain.doFilter(request, response);
            return;
        }
        
        boolean preventParsing = findParameter(request, preventParsingParameter).orElse(false);
        boolean preventDecorating = findParameter(request, preventDecoratingParameter).orElse(false);
        
        logger.debug("Decorating {}: prevention parameters: {}={}, {}={}", 
                request.getRequestURI(), 
                preventParsingParameter, preventParsing, 
                preventDecoratingParameter, preventDecorating);
        
        if (preventParsing && preventDecorating) {
            logger.debug("Not decorating {}: prevented by parameters", request.getRequestURI()); 
            chain.doFilter(request, response);
            return;
        }
        
        List<HtmlNodeFilter> htmlFilters = preventParsing ? Collections.emptyList() : 
            filters.stream()
                .map(factory -> factory.nodeFilter(request))
                .collect(Collectors.toList());
        
        logger.debug("HTML parse filters on request {}: {}", 
                request.getRequestURI(), htmlFilters); 
        
        DecorationResolver overridingResolver  = overridingResolver(
                preventDecorating ? Optional.empty() : Optional.of(resolver),
                ! preventParsing);
        
        response = new DecoratingServletResponse(
                request, response, overridingResolver, htmlParser, 
                htmlFilters, maxSize);
        
        if (staticHeaders.isPresent()) {
            logger.debug("Decorating request {} wih static headers: {}", 
                    request.getRequestURI(), staticHeaders); 

            response = new StaticHeadersResponse(response, staticHeaders.get());
        }
        chain.doFilter(request, response);
        response.flushBuffer();
    }
    
    /**
     * Checks request if parameter exists, or service attributes for {@code parameter=true}
     */
    private Optional<Boolean> findParameter(HttpServletRequest request, String parameter) {
        if (parameter == null) return Optional.empty();
        String requestParameter = request.getParameter(parameter);
        if (requestParameter != null) {
            if ("true".equals(requestParameter) || "".equals(requestParameter))
                return Optional.of(true);
        }
        Service service = RequestContext.getRequestContext(request).getService();
        while (service != null) {
            Object attribute = service.getAttribute(parameter);
            if (attribute != null) {
                if ("true".equals(attribute) || "".equals(attribute)) {
                    return Optional.of(true);
                }
                return Optional.of(false);
            }
            else {
                service = service.getParent();
            }
        }
        return Optional.empty();
    }
    
    

    private static class StaticHeadersResponse extends HttpServletResponseWrapper {
        private Map<String, String> staticHeaders;
        private boolean written = false;
        
        public StaticHeadersResponse(HttpServletResponse response, Map<String, String> staticHeaders) {
            super(response);
            this.staticHeaders = new HashMap<>(staticHeaders);
        }
        
        @Override
        public void setStatus(int sc) {
            if (200 <= sc && sc < 400) writeHeaders();
            super.setStatus(sc);
        }
        
        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            writeHeaders();
            return super.getOutputStream();
        }
        
        @Override
        public PrintWriter getWriter() throws IOException {
            writeHeaders();
            return super.getWriter();
        }
        
        @Override
        public String toString() {
            return "StaticHeadersResponse(" + getResponse() + ", " + staticHeaders.keySet() + ")";
        }
        
        private void writeHeaders() {
            if (written) return;
            for (Map.Entry<String, String> entry: staticHeaders.entrySet()) {
                super.setHeader(entry.getKey(), entry.getValue());
            }
            written = true;
        }        
    }
    
    private DecorationResolver overridingResolver(final Optional<DecorationResolver> resolver, 
            final boolean parse) {
        return new DecorationResolver() {
            @Override
            public DecorationDescriptor resolve(HttpServletRequest request,
                    HttpServletResponse response) {
                
                Optional<DecorationDescriptor> resolved = 
                        resolver.map(r -> r.resolve(request, response));
                
                return new DecorationDescriptor() {
                    @Override
                    public boolean decorate() {
                        return resolved.map(descriptor -> descriptor.decorate())
                                .orElse(parse);
                    }
                    @Override
                    public boolean tidy() {
                        return resolved.map(descriptor -> descriptor.tidy()).orElse(false);
                        
                    }
                    @Override
                    public boolean parse() {
                        return resolved.map(descriptor -> descriptor.parse())
                                .orElse(parse);
                    }

                    @Override
                    public List<Template> getTemplates() {
                        return resolved.map(descriptor -> descriptor.getTemplates())
                                .orElse(Collections.emptyList());
                    }

                    @Override
                    public Map<String, Object> getParameters(
                            Template template) {
                        return resolved.map(descriptor -> descriptor.getParameters(template))
                                .orElse(Collections.emptyMap());
                    }
                };
            }
            
        };
    }
}
