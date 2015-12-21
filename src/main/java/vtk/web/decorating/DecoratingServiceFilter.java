/* Copyright (c) 2015, University of Oslo, Norway
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import vtk.text.html.HtmlNodeFilter;
import vtk.text.html.HtmlPageParser;
import vtk.web.RequestContext;
import vtk.web.filter.ResponseFilter;
import vtk.web.filter.ServiceFilter;
import vtk.web.filter.ServiceFilterChain;
import vtk.web.service.Service;

public class DecoratingServiceFilter implements ServiceFilter {
    private HtmlPageParser htmlParser;
    private List<HtmlNodeFilter> filters;
    private DecorationResolver resolver;
    private ComponentResolver componentResolver;
    private String preventDecoratingParameter;
    private Map<String, String> staticHeaders;
    
    public DecoratingServiceFilter(HtmlPageParser htmlParser,
            List<HtmlNodeFilter> filters, DecorationResolver resolver,
            ComponentResolver componentResolver, String preventDecoratingParameter,
            Map<String, String> staticHeaders) {
        this.htmlParser = htmlParser;
        this.filters = new ArrayList<>(filters);
        this.resolver = resolver;
        this.componentResolver = componentResolver;
        this.preventDecoratingParameter = preventDecoratingParameter;
        if (staticHeaders != null) this.staticHeaders = new HashMap<>(staticHeaders);
    }
    
    @Override
    public void filter(HttpServletRequest request,
            final HttpServletResponse response, ServiceFilterChain chain)
            throws Exception {
        
        if (preventDecorating(request)) return;
        
        chain.filter(new ResponseFilter() {
            
            @Override
            public HttpServletResponse filter(HttpServletRequest request,
                    HttpServletResponse response) {
                HttpServletResponse result = 
                    new DecoratingServletResponse(
                            request, response, resolver, htmlParser, 
                            filters, componentResolver);
                if (staticHeaders != null) {
                    result = new StaticHeadersResponse(result, staticHeaders);
                }
                return result;
            }

            @Override
            public int getOrder() {
                throw new UnsupportedOperationException();
            }
        });
    }

    private boolean preventDecorating(HttpServletRequest request) {
        if (preventDecoratingParameter == null) return false;
        if (request.getParameter(preventDecoratingParameter) != null)
            return true;
        Service service = RequestContext.getRequestContext().getService();
        while (service != null) {
            if ("true".equals(service.getAttribute(preventDecoratingParameter)))
                return true;
            else service = service.getParent();
        }
        return false;
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
    
}
