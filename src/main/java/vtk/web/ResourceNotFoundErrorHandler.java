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
package vtk.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.View;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Service;

public class ResourceNotFoundErrorHandler implements ErrorHandler {
    
    private String defaultView;
    private boolean redirectMoved = false;
    private List<ReferenceDataProvider> referenceDataProviders;
    
    public ResourceNotFoundErrorHandler(String defaultView, boolean redirectMoved) {
        this(defaultView, redirectMoved, null);
    }
    
    public ResourceNotFoundErrorHandler(String defaultView, boolean redirectMoved, 
            List<ReferenceDataProvider> referenceDataProviders) {
        this.defaultView = defaultView;
        this.redirectMoved = redirectMoved;
        this.referenceDataProviders = referenceDataProviders;
    }

    @Override
    public Class<? extends Throwable> getErrorType() {
        return ResourceNotFoundException.class;
    }

    @Override
    public Service getService() {
        return null;
    }

    @Override
    public Map<String, Object> getErrorModel(HttpServletRequest request,
            HttpServletResponse response, Throwable error) throws Exception {
        Map<String, Object> model = new HashMap<>();
        RequestContext requestContext = RequestContext.getRequestContext();
        Path uri = requestContext.getRequestURL().getPath();
        
        PropertyTypeDefinition locationHistoryPropDef = 
                requestContext.getRepository()
                .getTypeInfo("resource")
                .getPropertyTypeDefinition(Namespace.DEFAULT_NAMESPACE, 
                        "location-history");

        List<PropertySet> locations = new ArrayList<>();
        PreviousLocationsResolver resolver = 
                new PreviousLocationsResolver(locationHistoryPropDef, requestContext);
        Set<PropertySet> result = resolver.resolve(uri);
        if (result != null) {
            locations.addAll(result);
        }
        request.setAttribute(getClass().getName() + ".locations", locations);
        model.put("locations", locations);
        
        if (referenceDataProviders != null) {
            for (ReferenceDataProvider p: referenceDataProviders) {
                p.referenceData(model,  request);
            }
        }
        return model;
    }

    @Override
    public Object getErrorView(HttpServletRequest request,
            HttpServletResponse response, Throwable error) throws Exception {

        @SuppressWarnings("unchecked")
        List<PropertySet> locations = (List<PropertySet>) 
            request.getAttribute(getClass().getName() + ".locations");

        if (locations.isEmpty()) {
            return defaultView;
        }
        
        if (locations.size() == 1 && redirectMoved) {
            PropertySet propSet = locations.get(0); 
            return new RedirectView(propSet.getURI().toString());
        }
        return defaultView;
    }

    @Override
    public int getHttpStatusCode(HttpServletRequest request,
            HttpServletResponse response, Throwable error) throws Exception {
        return 404;
    }
    
    private static class RedirectView implements View {
        private String location;
        public RedirectView(String location) {
            this.location = location;
        }
        
        @Override
        public String getContentType() {
            return "text/html";
        }
        
        @Override
        public void render(Map<String, ?> model, HttpServletRequest request,
                HttpServletResponse response) throws Exception {
            response.sendRedirect(location);
        }
    }

}
