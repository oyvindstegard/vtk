/* Copyright (c) 2004, University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.web.servlet.HandlerInterceptor;

import vtk.repository.Resource;
import vtk.security.web.AuthenticationChallenge;
import vtk.web.service.provider.ServiceNameProvider;
import vtk.web.servlet.FilterFactory;


/**
 * Default implementation of the Service interface.
 *
 * <p>Configurable properties:
 * <ul>
 *   <li><code>parent</code> - the parent {@link Service} in the service tree
 *   <li><code>handler</code> - a {@link
 *   org.springframework.web.servlet.mvc.Controller} that is executed
 *   when this service matches (see {@link ServiceHandlerMapping}).
 *   <li><code>handlerInterceptors</code> - list of
 *   {@link org.springframework.web.servlet.HandlerInterceptor
 *   interceptors} that are executed prior to (around) the controller
 *   invocation.
 *   <li><code>order</code> - integer specifying the order of this
 *   service (see {@link org.springframework.core.Ordered}). Default is
 *   <code>0</code>.
 * </ul>
 *
 */
public class ServiceImpl implements Service, BeanNameAware {
    // FIXME: Cache for all assertions, don't use directly!
    private volatile List<Assertion> allAssertions;

    private AuthenticationChallenge authenticationChallenge;
    private Object handler;
    private List<Assertion> assertions = new ArrayList<>();
    private Service parent;
    private String name;
    private Map<String, Object> attributes = new HashMap<>();
    private List<HandlerInterceptor> handlerInterceptors;
    private List<Filter> servletFilters;
    private int order = 0;
    private List<URLPostProcessor> urlPostProcessors = new ArrayList<>();
    private List<URLPostProcessor> accumulatedUrlPostProcessors = null;
    private ServiceNameProvider serviceNameProvider;
    
    @Override
    public URLConstructor urlConstructor(URL base) {
        return new Service.URLConstructor(Objects.requireNonNull(base), this);
    }

    @Override
    public List<Assertion> getAllAssertions() {
        if (this.allAssertions == null) {
            synchronized (this) {
                if (this.allAssertions != null) {
                    return this.allAssertions;
                }
                this.allAssertions = new ArrayList<>();
                if (this.parent != null) {
                    this.allAssertions.addAll(parent.getAllAssertions());
                }
                this.allAssertions.addAll(this.assertions);
            }
        }
        
        return this.allAssertions;
    }

    public void setHandler(Object handler) {
        this.handler = handler;
    }
	

    public void setAssertions(List<Assertion> assertions) {
        this.assertions = assertions;
    }
	
    @Override
    public Object getHandler() {
        return this.handler;
    }
	

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
    

    @Override
    public Object getAttribute(String name) {
        if (this.attributes == null) {
            return null;
        }
        return this.attributes.get(name);
    }
    

    public void setUrlPostProcessors(List<URLPostProcessor> urlPostProcessors) {
        this.urlPostProcessors = urlPostProcessors;
    }
    

    @Override
    public void setParent(Service parent) {
        // Looking for infinite loops
        Service service = parent;
        while (service != null) {
            if (service == this) {
                throw new BeanInitializationException(
                "Trying to set parent service " + parent.getName() + " on service " 
                + getName() + " resulting in a infinite loop");
            }
            service = service.getParent();
        }
        
        this.parent = parent;
    }
	

    @Override
    public List<Assertion> getAssertions() {
        return this.assertions;
    }
	

    private List<URLPostProcessor> getAllURLPostProcessors() {
        if (this.accumulatedUrlPostProcessors != null) {
            return this.accumulatedUrlPostProcessors;
        }

        List<URLPostProcessor> allPostProcessors = new ArrayList<>();
        Service s = this;
        while (s != null) {
            
            if ((s instanceof ServiceImpl) && ((ServiceImpl) s).urlPostProcessors != null) {
                allPostProcessors.addAll(((ServiceImpl) s).urlPostProcessors);
            }
            s = s.getParent();
        }
        // XXX This is theoretically unsafe without "this.accumulatedUrlPostProcessors" being declared volatile.
        //     (Due to instruction reordering, the instance var update may be seen by other threads before the list
        //      construction has actually finished.)
        this.accumulatedUrlPostProcessors = allPostProcessors;
        return allPostProcessors;
    }
    

    @Override
    public String getName() {
        return this.name;
    }

	
    @Override
    public void setBeanName(String name) {
        this.name = name;
    }



    @Override
    public Service getParent() {
        return this.parent;
    }
	

    @Override
    public boolean isDescendantOf(Service service) {
        if (service == null) {
            throw new IllegalArgumentException("Services cannot be null");
        }

        Service s = this.parent;

        while (s != null) {
            if (s == service) {
                return true;
            }
            s = s.getParent();
        }
        return false;
    }
    
    public void setHandlerInterceptors(List<HandlerInterceptor> handlerInterceptors) {
        this.handlerInterceptors = handlerInterceptors;
    }

    @Override
    public List<HandlerInterceptor> getHandlerInterceptors() {
        if (this.handlerInterceptors == null) {
            return null;
        }
        return Collections.unmodifiableList(this.handlerInterceptors);
    }

    public void setServletFilters(List<FilterFactory> servletFilters) {
        if (servletFilters != null) {
            this.servletFilters = servletFilters.stream()
                    .map(factory -> factory.filter())
                    .collect(Collectors.toList());
        }
    }
    
    @Override
    public List<Filter> getServletFilters() {
        if (this.servletFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(this.servletFilters);
    }
    

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + name + ")";
    }

    @Override
    public AuthenticationChallenge getAuthenticationChallenge() {
        return this.authenticationChallenge;
    }


    public void setAuthenticationChallenge(
        AuthenticationChallenge authenticationChallenge) {
        this.authenticationChallenge = authenticationChallenge;
    }
    

    @Override
    public int getOrder() {
        return this.order;
    }
    

    public void setOrder(int order) {
        this.order = order;
    }

    // XXX: clean up
    public void postProcess(URL urlObject, Resource resource) {
        List<URLPostProcessor> urlPostProcessors = getAllURLPostProcessors();

        if (urlPostProcessors != null) {
            for (URLPostProcessor urlProcessor: urlPostProcessors) {
                try {
                    if (resource != null) {
                        urlProcessor.processURL(urlObject, resource, this);
                    }
                    else {
                        urlProcessor.processURL(urlObject, this);
                    }
                }
                catch (Exception e) {
                    throw new ServiceUnlinkableException("URL Post processor " + urlProcessor
                                                         + " threw exception", e);
                }
            }
        }
    }


    public void setServiceNameProvider(ServiceNameProvider serviceNameProvider) {
        this.serviceNameProvider = serviceNameProvider;
    }

    @Override
    public String getLocalizedName(Resource resource, HttpServletRequest request) {
        if (this.serviceNameProvider != null) {
            return this.serviceNameProvider.getLocalizedName(resource, request);
        }
        return null;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((assertions == null) ? 0 : assertions.hashCode());
        result = prime * result
                + ((attributes == null) ? 0 : attributes.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + order;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ServiceImpl other = (ServiceImpl) obj;
        if (assertions == null) {
            if (other.assertions != null)
                return false;
        }
        else if (!assertions.equals(other.assertions))
            return false;
        if (attributes == null) {
            if (other.attributes != null)
                return false;
        }
        else if (!attributes.equals(other.attributes))
            return false;
        if (authenticationChallenge == null) {
            if (other.authenticationChallenge != null)
                return false;
        }
        if (name == null) {
            if (other.name != null)
                return false;
        }
        else if (!name.equals(other.name))
            return false;
        if (order != other.order)
            return false;
        if (parent == null) {
            if (other.parent != null)
                return false;
        }
        else if (!parent.equals(other.parent))
            return false;
        return true;
    }



}
