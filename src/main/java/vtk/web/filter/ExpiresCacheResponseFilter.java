/* Copyright (c) 2009, University of Oslo, Norway
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
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.RepositoryAction;
import vtk.repository.Resource;
import vtk.repository.TypeInfo;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.servlet.AbstractServletFilter;

public class ExpiresCacheResponseFilter extends AbstractServletFilter {

    private static Logger logger = LoggerFactory.getLogger(ExpiresCacheResponseFilter.class);
    
    private PropertyTypeDefinition expiresPropDef;
    private int globalMaxAge = -1;
    private Service rootService;
    private Set<Service> excludedServices;
    private Set<String> excludedResourceTypes;
    
    @Override
    protected void doFilter(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(request, filterResponse(request, response));
    }
    
    private HttpServletResponse filterResponse(HttpServletRequest request, 
            HttpServletResponse response) throws IOException, ServletException {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Path uri = requestContext.getResourceURI();

        if (!requestContext.isInRepository()) {
            if (logger.isDebugEnabled()) {
                logger.debug(uri + ": ignore: not in repository");
            }
            return response;
        }

        if (this.rootService != null) {
            Service service = requestContext.getService();
            if (!service.isDescendantOf(this.rootService)) {
                if (logger.isDebugEnabled()) {
                    logger.debug(uri + ": ignore: service=" + service.getName());
                }
                return response;
            }
        }
        
        if (this.excludedServices != null) {
            Service service = requestContext.getService();
            for (Service excluded : this.excludedServices) {
                if (service.isDescendantOf(excluded) || service.equals(excluded)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(uri + ": ignore: service=" + service.getName());
                    }
                    return response;
                }
            }
        }
        Service service = requestContext.getService();

        while (service != null) {
            if ("true".equals(service.getAttribute("remove-caching"))) {
                if (logger.isDebugEnabled()) {
                    logger.debug(uri + ": remove: service-attr remove-caching=true");
                }
                return new CacheControlResponseWrapper(response, 0);
            }
            if ("true".equals(service.getAttribute("inhibit-caching"))) {
                if (logger.isDebugEnabled()) {
                    logger.debug(uri + ": ignore: service-attr inhibit-caching=true");
                }
                return response;
            }
            service = service.getParent();
        }
        
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        try {
            Resource resource = repository.retrieve(token, uri, true);
            if (this.excludedResourceTypes != null) {
                TypeInfo typeInfo = repository.getTypeInfo(resource);
                
                for (String t: this.excludedResourceTypes) {
                    if (typeInfo.isOfType(t)) {
                        if (logger.isDebugEnabled()) {
                            logger.debug(uri + ": ignore: type=" + t);
                        }
                        return response;
                    }
                }
            }
            boolean anonymousReadable = 
                repository.isAuthorized(resource, RepositoryAction.READ_PROCESSED, null, false);
            if (!anonymousReadable) {
                if (logger.isDebugEnabled()) {
                    logger.debug(uri + ": ignore: restricted");
                }
                return response;
            }

            Property expiresProp = resource.getProperty(this.expiresPropDef);
            if (expiresProp != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug(uri + ": property max-age=" + expiresProp.getLongValue());
                }
                return new CacheControlResponseWrapper(response, expiresProp.getLongValue());
            }
            if (this.globalMaxAge > 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug(uri + ": default max-age=" + this.globalMaxAge);
                }
                return new CacheControlResponseWrapper(response, this.globalMaxAge);
            }
        }
        catch (Throwable t) {

        }
        logger.debug(uri + ": ignore");
        return response;
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    public void setExpiresPropDef(PropertyTypeDefinition expiresPropDef) {
        this.expiresPropDef = expiresPropDef;
    }

    public void setRootService(Service rootService) {
        this.rootService = rootService;
    }

    public void setExcludedServices(Set<Service> excludedServices) {
        this.excludedServices = excludedServices;
    }

    public void setGlobalMaxAge(int globalMaxAge) {
        this.globalMaxAge = globalMaxAge;
    }
    
    public void setExcludedResourceTypes(Set<String> excludedResourceTypes) {
        this.excludedResourceTypes = excludedResourceTypes;
    }
    
    private static final Set<String> DROPPED_HEADERS = new HashSet<>();
    static {
        DROPPED_HEADERS.add("Expires");
        DROPPED_HEADERS.add("Cache-Control");
        DROPPED_HEADERS.add("Pragma");
        DROPPED_HEADERS.add("Last-Modified");
        DROPPED_HEADERS.add("Vary");
    }
    
    private static class CacheControlResponseWrapper extends HttpServletResponseWrapper {

        private HttpServletResponse response;
        
        public CacheControlResponseWrapper(HttpServletResponse response, 
                long seconds) {
            super(response);
            this.response = response;
            this.response.setHeader("Cache-Control", "max-age=" + seconds);
        }

        @Override
        public void addDateHeader(String name, long date) {
            if (DROPPED_HEADERS.contains(name)) {
                return;
            }
            super.addDateHeader(name, date);
        }

        @Override
        public void addHeader(String name, String value) {
            if (DROPPED_HEADERS.contains(name)) {
                return;
            }
            super.addHeader(name, value);
        }

        @Override
        public void addIntHeader(String name, int value) {
            if (DROPPED_HEADERS.contains(name)) {
                return;
            }
            super.addIntHeader(name, value);
        }

        @Override
        public boolean containsHeader(String name) {
            if (DROPPED_HEADERS.contains(name)) {
                return false;
            }
            return super.containsHeader(name);
        }

        @Override
        public void setDateHeader(String name, long date) {
            if (DROPPED_HEADERS.contains(name)) {
                return;
            }
            super.setDateHeader(name, date);
        }
        
        @Override
        public void setHeader(String name, String value) {
            if (DROPPED_HEADERS.contains(name)) {
                return;
            }
            if ("Content-Type".equals(name)) {
                this.response.setContentType(value);
                if (contentTypeMatch(value))
                    this.response.setHeader("Vary", "Cookie");
            }
            this.response.setHeader(name, value);
        }

        @Override
        public void setIntHeader(String name, int value) {
            if (DROPPED_HEADERS.contains(name)) {
                return;
            }
            super.setIntHeader(name, value);
        }
        
        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (contentTypeMatch(this.response.getContentType())) {
                this.response.setHeader("Vary", "Cookie");
            }
            return super.getOutputStream();
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (contentTypeMatch(this.response.getContentType())) {
                this.response.setHeader("Vary", "Cookie");
            }
            return super.getWriter();
        }

        @Override
        public void setContentType(String type) {
            if (contentTypeMatch(type)) {
                this.response.setHeader("Vary", "Cookie");
            }
            super.setContentType(type);
        }
        
        @Override
        public void setContentLength(int length) {
            if (!DROPPED_HEADERS.contains("Content-Length")) {
                // Avoid bug in Resin 4 (http://bugs.caucho.com/view.php?id=5807)
                // by not calling setHeader in addition to setContentLength.
                this.response.setContentLength(length);
            }
        }
        
        @Override
        public String toString() {
            return "CacheControlResponseWrapper[" + super.toString() + "]";
        }

        private boolean contentTypeMatch(String contentType) {
            if (contentType == null) {
                return false;
            }
            return contentType.startsWith("text/html") || contentType.startsWith("image/");
        }
    }

}
