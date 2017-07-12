/* Copyright (c) 2010, University of Oslo, Norway
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
package vtk.web.service;

import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.web.RequestContext;

/**
 * This class acts as a request protocol assertion ('*', 'http' or 'https'). 
 * If config property <code>selectiveAccessEnabled</code> is set,
 * it also allows access using unencrypted ('http') protocol for open 
 * resources on given services.
 * 
 * In order to function properly, this bean must be configured 
 * to handle the following aspects of a service:
 * <ul>
 *   <li>assertion</li>
 *   <li>handler interceptor</li>
 *   <li>url post processor</li>
 * </ul>
 */
public class SelectiveProtocolManager extends RequestProtocolAssertion 
    implements WebAssertion, HandlerInterceptor, URLPostProcessor {

    private Set<Service> genURLFileServices;
    private Set<Service> genURLCollectionServices;
    private Set<Service> matchFileServices;
    private Set<Service> matchCollectionServices;
    private boolean selectiveAccessEnabled = false;
    
    

    /**
     * {@link URLPostProcessor#processURL(URL, Service, Optional<Resource>)}
     */
    @Override
    public BiFunction<URL, Optional<Resource>, URL> urlProcessor(
            Service service, URL base) {
        return (url, optResource) -> {
            if (!this.selectiveAccessEnabled) {
                return url;
            }
            if (!optResource.isPresent()) {
                return url;
            }
            Resource resource = optResource.get();
            if (resource.isReadRestricted()) {
                return url;
            }
            Set<Service> services = resource.isCollection() ? 
                    this.genURLCollectionServices : this.genURLFileServices;
            if (services == null) {
                return url;
            }
            if (!services.contains(service)) {
                return url;
            }
            // XXX this is wrong if request [page] context is secure and the URL
            // points to an element which will be used inline in document (causes mixed-mode
            // in such cases). However, don't know if fix for such situations is appropriate here.
            return url.setProtocol("http");
        };
    }
//    /**
//     * {@link URLPostProcessor#processURL(URL, Service, Optional<Resource>)}
//     */
//    @Override
//    public void processURL(URL url, Service service,
//            Optional<Resource> optResource) {
//        if (!this.selectiveAccessEnabled) {
//            return;
//        }
//        if (!optResource.isPresent()) {
//            return;
//        }
//        Resource resource = optResource.get();
//        if (resource.isReadRestricted()) {
//            return;
//        }
//        Set<Service> services = resource.isCollection() ? 
//                this.genURLCollectionServices : this.genURLFileServices;
//        if (services == null) {
//            return;
//        }
//        if (!services.contains(service)) {
//            return;
//        }
//        // XXX this is wrong if request [page] context is secure and the URL
//        // points to an element which will be used inline in document (causes mixed-mode
//        // in such cases). However, don't know if fix for such situations is appropriate here.
//        url.setProtocol("http");
//    }

    @Override
    public boolean matches(HttpServletRequest request, Resource resource,
            Principal principal) {
        if (!this.selectiveAccessEnabled) {
            return super.matches(request, resource, principal);
        }
        if (!request.isSecure()) {
            return true;
        }
        return super.matches(request, resource, principal);
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
            HttpServletResponse response, Object handler) throws Exception {
        if (!this.selectiveAccessEnabled) {
            return true;
        }
        if (request.isSecure()) {
            return true;
        }
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Resource resource = retrieveResource(request);

        if (resource.isReadRestricted()) {
            redirectSSL(request, response);
            return false;
        }

        Service service = requestContext.getService();
        Set<Service> services = resource.isCollection() ? 
                this.matchCollectionServices : this.matchFileServices;

        for (Service s: services) {
            if (service.equals(s) || service.isDescendantOf(s)) {
                return true;
            }
        }
        redirectSSL(request, response);
        return false;
    }
    
    private void redirectSSL(HttpServletRequest request, HttpServletResponse response) throws Exception {
        URL url = URL.create(request);
        url.setProtocol("https");
        response.sendRedirect(url.toString());
    }
    
    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
    }
    
    public void setGenURLFileServices(Set<Service> services) {
        this.genURLFileServices = services;
    }

    public void setGenURLCollectionServices(Set<Service> services) {
        this.genURLCollectionServices = services;
    }

    public void setMatchFileServices(Set<Service> services) {
        this.matchFileServices = services;
    }

    public void setMatchCollectionServices(Set<Service> services) {
        this.matchCollectionServices = services;
    }

    public void setSelectiveAccessEnabled(boolean selectiveAccessEnabled) {
        this.selectiveAccessEnabled = selectiveAccessEnabled;
    }

    private Resource retrieveResource(HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Resource resource = repository.retrieve(token, requestContext.getResourceURI(), true);
        return resource;
    }

}
