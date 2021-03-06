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
package vtk.web.filter;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import vtk.util.text.Json;
import vtk.util.text.SimpleTemplate;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.servlet.AbstractServletFilter;

public class TemplateHeaderProvider extends AbstractServletFilter {
    private Map<String, SimpleTemplate> headers = new LinkedHashMap<>();
    private List<ReferenceDataProvider> referenceDataProviders;
    private boolean addHeaders = true;
    
    public TemplateHeaderProvider(Map<String, String> headers, 
            List<ReferenceDataProvider> referenceDataProviders, 
            boolean addHeaders) {
        for (String name: headers.keySet()) {
            String spec = headers.get(name);
            SimpleTemplate template = SimpleTemplate.compile(spec, "%{", "}");
            this.headers.put(name, template);
        }
        this.referenceDataProviders = referenceDataProviders;
        this.addHeaders = addHeaders;
    }

    @Override
    protected void doFilter(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        final Map<String, Object> model = new HashMap<>();
        referenceDataProviders.forEach(provider -> {
                try { provider.referenceData(model, request); }
                catch (Throwable t) { }
            });
        
        for (String name: headers.keySet()) {
            SimpleTemplate template = headers.get(name);
        
            final StringBuilder header = new StringBuilder();

            template.apply(v -> {
                Object o = Json.select(model, v);
                return o != null ? o.toString() : "null";
            }, s -> header.append(s));

            if (addHeaders) {
                response.addHeader(name, header.toString());
            }
            else {
                response.setHeader(name, header.toString());
            }
        }
        chain.doFilter(request, response);
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + headers + ")";
    }
}
