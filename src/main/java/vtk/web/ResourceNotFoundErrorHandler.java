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

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.View;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.PreviousLocationsResolver.RelocatedResource;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Assertion;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class ResourceNotFoundErrorHandler implements ErrorHandler {
    private static Logger logger = 
            LoggerFactory.getLogger(ResourceNotFoundErrorHandler.class);
    private String defaultView;    
    private List<ReferenceDataProvider> referenceDataProviders;
    private Optional<Assertion> redirectAssertion = Optional.empty();
    private Optional<String> securityToken = Optional.empty();
    
    public ResourceNotFoundErrorHandler(String defaultView, 
            List<ReferenceDataProvider> referenceDataProviders, 
            Optional<Assertion> redirectAssertion, Optional<String> securityToken) {
        this.defaultView = defaultView;
        this.referenceDataProviders = referenceDataProviders;
        this.redirectAssertion = redirectAssertion;
        this.securityToken = securityToken;
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
        
        PropertyTypeDefinition unpublishedCollectionPropDef = 
                requestContext.getRepository()
                .getTypeInfo("resource")
                .getPropertyTypeDefinition(Namespace.DEFAULT_NAMESPACE, 
                        "unpublishedCollection");

        String token = securityToken.orElse(requestContext.getSecurityToken());

        PreviousLocationsResolver resolver = 
                new PreviousLocationsResolver(locationHistoryPropDef, 
                        unpublishedCollectionPropDef,
                        () -> requestContext.getRepository(),
                        () -> token);
       
        Boolean resolveRedirects = !redirectAssertion.isPresent() 
                || redirectAssertion.get()
                .matches(request, null, requestContext.getPrincipal());
        
        Collection<RelocatedResource> resolved = resolveRedirects ? 
                resolver.resolve(uri) : Collections.emptyList();

        List<RelocatedResource> locations = resolved.stream()
                .sorted(LOC_COMPARATOR)
                .collect(Collectors.toList());
        request.setAttribute(getClass().getName() + ".locations", locations);
        model.put("locations", locations);
        
        for (ReferenceDataProvider p: referenceDataProviders) {
            p.referenceData(model,  request);
        }
        return model;
    }

    @Override
    public Object getErrorView(HttpServletRequest request,
            HttpServletResponse response, Throwable error) throws Exception {

        @SuppressWarnings("unchecked")
        List<RelocatedResource> locations = (List<RelocatedResource>) 
            request.getAttribute(getClass().getName() + ".locations");

        if (request.getParameterMap().containsKey("log")) {
            logger.info("404 request " + request.getRequestURL() 
                + ": redirect candidates" + locations);
        }
        if (locations.isEmpty()) {
            return defaultView;
        }
        PropertySet propSet = locations.get(0).resource;
        String location = URL.create(request).setPath(propSet.getURI()).toString();
        return new RedirectView(location);
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
            // Cache headers, permanently/temporary:
            response.sendRedirect(location);
        }
    }

    private static Comparator<RelocatedResource> LOC_COMPARATOR = (o1, o2) -> {
        if (o1.time.isPresent() && o2.time.isPresent()) {
            return o2.time.get().compareTo(o1.time.get());
        }
        else if (!o1.time.isPresent() && !o2.time.isPresent()) {
            return 0;
        }
        else if (o1.time.isPresent()) {
            return 1;
        }
        return -1;
    };


}
