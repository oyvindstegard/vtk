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
package vtk.web.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.servlet.AbstractServletFilter;
import vtk.web.servlet.FilterChain;

/**
 * {@link Filter} that invokes a chain of {@link Filter 
 * servlet filters} that are configured to run on the 
 * current {@link Service} (including ancestors).
 */
public final class ServiceFilterInvoker extends AbstractServletFilter {
    
    @Override
    protected void doFilter(HttpServletRequest request,
            HttpServletResponse response, javax.servlet.FilterChain chain)
            throws IOException, ServletException {
        
        RequestContext requestContext = RequestContext.getRequestContext();
        Service service = requestContext.getService();
        List<Filter> filters = getServiceFilters(service);
        if (filters != null) {
            FilterChain serviceChain = new FilterChain(filters, (req, resp) -> 
                chain.doFilter(req, resp));
            serviceChain.doFilter(request, response);
        }
    }

    private List<Filter> getServiceFilters(Service service) {
        List<Filter> servletFilters = new ArrayList<>();
        
        if (service.getParent() != null) {
            List<Filter> parentFilters = getServiceFilters(service.getParent());
            if (parentFilters != null) {
                servletFilters.addAll(parentFilters);
            }
        }
        List<Filter> myServletFilters = service.getServletFilters();
        if (myServletFilters != null) { 
            servletFilters.addAll(myServletFilters);
        }
        return servletFilters;
    }
    
}
