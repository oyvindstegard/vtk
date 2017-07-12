/* Copyright (c) 2006, 2007, University of Oslo, Norway
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
package vtk.web.interceptors;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.RepositoryAction;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.util.repository.LocaleHelper;
import vtk.util.web.HttpUtil;
import vtk.web.RequestContext;

/**
 * Interceptor for controlling various HTTP response headers.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>includeLastModifiedHeader</code> - boolean deciding
 *   whether to set the <code>Last-Modified</code> response
 *   header. Default value is <code>true</code>.
 *   <li><code>expiresHeaderProperty</code> - a {@link
 *   PropertyTypeDefinition} of a (numeric) property to look for on
 *   the current resource. If this property exists, its value will be
 *   used as the HTTP <code>Expires</code> header.
 *   <li><code>includeContentLanguageHeader</code> - whether or not to
 *   to attempt to set the <code>Content-Language</code> HTTP header
 *   to that of the resource (default <code>true</code>.)
 *   <li><code>includeEtagHeader</code> - boolean deciding whether
 *   to attempt to set the <code>Etag</code> HTTP header. 
 *   The default value is <code>true</code>.
 *   <li><code>Last-Modified</code> if the configuration property
 *   <code>includeLastModifiedHeader</code> is set to
 *   <code>true</code> (the default).
 *   <li><code>Expires</code> if the configuration property
 *   <code>expiresHeaderProperty</code> is configured and the resource
 *   has the corresponding property set. The value of the header will
 *   be the same as the value of the property. NOTE: This is only set for resources 
 *   with anonymous read processed (or read) access.
 *   <li><code>Cache-Control: no-cache</code> if the configuration
 *   property <code>expiresHeaderProperty</code> is not set,
 *   or it is set, but the corresponding resource property
 *   (see above) is not set.
 *   <li><code>Content-Language</code> if the configuration property
 *   <code>includeContentLanguageHeader</code> is <code>true</code>
 *   and the resource has a content locale defined. (Note: a
 *   limitation in the Spring framework (<code>setLocale()</code> is
 *   always called on every response with the value of the resolved
 *   request locale) causes this view to always set this header. In
 *   cases where the resource has no content locale set, or this view
 *   is not configured to include the header, the value of the header
 *   is empty.
 *   <li><code>staticHeaders</code> - a {@link Map} of
 *   <code>(headerName, value)</code> pairs, listing a set of static
 *   headers to always set on the response. These headers are the last
 *   to be set, and will thus override any of the more dynamic
 *   headers.
 * </ul>
 *
 */
public class HeaderControlHandlerInterceptor implements HandlerInterceptor {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private boolean includeLastModifiedHeader = false;
    private boolean includeContentLanguageHeader = false;
    private boolean includeEtagHeader = false;
    private boolean includeNoCacheHeader = false;
    private Map<String, String> staticHeaders = new HashMap<String, String>();
    
    public void setIncludeLastModifiedHeader(boolean includeLastModifiedHeader) {
        this.includeLastModifiedHeader = includeLastModifiedHeader;
    }

    public void setIncludeContentLanguageHeader(boolean includeContentLanguageHeader) {
        this.includeContentLanguageHeader = includeContentLanguageHeader;
    }
    
    public void setIncludeEtagHeader(boolean includeEtagHeader) {
        this.includeEtagHeader = includeEtagHeader;
    }
    
    public void setIncludeNoCacheHeader(boolean includeNoCacheHeader) {
        this.includeNoCacheHeader = includeNoCacheHeader;
    }


    public void setStaticHeaders(Map<String, String> staticHeaders) {
        this.staticHeaders = staticHeaders;
    }
    

    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        return true;
    }
    

    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) throws Exception {
        if (modelAndView == null || response.isCommitted()) {
            return;
        }
        Resource resource = null;
        @SuppressWarnings("rawtypes") Map model = modelAndView.getModel();

        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        try {
            resource = repository.retrieve(token, uri, true);
        } catch (Throwable t) { }

        if (resource != null) {
            setLastModifiedHeader(resource, model, request, response);
            setEtagHeader(resource, model, request, response);
            setCacheControlHeader(resource, model, request, response);
            setContentLanguageHeader(resource, model, request, response);
        }
        setStaticHeaders(request, response);
    }


    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
    }
    


    /**
     * Sets the content language header based on the resource.
     * 
     * @param resource the served resource
     * @param model the MVC model
     * @param request the servlet request
     * @param response the servlet response
     * @throws Exception
     */
    @SuppressWarnings("rawtypes") 
    protected void setContentLanguageHeader(Resource resource, 
                                            Map model,
                                            HttpServletRequest request,
                                            HttpServletResponse response) throws Exception {
        // Fix for DispatcherServlet's behavior (always sets the
        // response's locale to that of the request).
        response.setHeader("Content-Language", "");

        if (this.includeContentLanguageHeader) {
            Locale locale = LocaleHelper.getLocale(resource.getContentLanguage());
            if (locale != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Setting header Content-Language: " + locale.getLanguage());
                }
                response.setHeader("Content-Language", locale.getLanguage());
            }
        }
    }
    

    /**
     * Sets the last modified header based on the resource.
     * 
     * @param resource the served resource
     * @param model the MVC model
     * @param request the servlet request
     * @param response the servlet response
     * @throws Exception
     */
    @SuppressWarnings("rawtypes") 
    protected void setLastModifiedHeader(Resource resource, 
                                         Map model,
                                         HttpServletRequest request,
                                         HttpServletResponse response) throws Exception {
        
        if (this.includeLastModifiedHeader) {
            if (logger.isDebugEnabled()) {
                logger.debug("Setting header Last-Modified: "
                             + HttpUtil.getHttpDateString(resource.getLastModified()));
            }
            response.setHeader("Last-Modified", 
                               HttpUtil.getHttpDateString(
                                       resource.getLastModified()));
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("Setting HTTP status code: " 
                    + HttpServletResponse.SC_OK);
        }
    }


    /**
     * Sets the ETag header based on the resource.
     * 
     * @param resource the served resource
     * @param model the MVC model
     * @param request the servlet request
     * @param response the servlet response
     * @throws Exception
     */
    @SuppressWarnings("rawtypes") 
    protected void setEtagHeader(Resource resource, Map model, 
                                HttpServletRequest request, 
                                HttpServletResponse response) throws Exception {
        if (this.includeEtagHeader) {
            String etag = resource.getEtag();
            if (logger.isDebugEnabled()) {
                logger.debug("Setting header Etag: " + etag);
            }
            response.setHeader("ETag", etag);
        }
    }
    

    /**
     * Sets the cache control header based on the resource.
     * 
     * @param resource the served resource
     * @param model the MVC model
     * @param request the servlet request
     * @param response the servlet response
     * @throws Exception
     */
    @SuppressWarnings("rawtypes") 
    protected void setCacheControlHeader(Resource resource, Map model, 
                                         HttpServletRequest request, HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        if (resource == null || this.includeNoCacheHeader) {
            response.setHeader("Cache-Control", "no-cache");
        } else if (!repository.isAuthorized(resource, RepositoryAction.READ_PROCESSED, null, false)) {
            response.setHeader("Cache-Control", "private");
        }
    }

    /**
     * Sets static headers based on configuration.
     * 
     * @param request the servlet request
     * @param response the servlet response
     * @throws Exception
     */
    protected void setStaticHeaders(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (this.staticHeaders == null || this.staticHeaders.size() == 0) {
            return;
        }
        for (String header: this.staticHeaders.keySet()) {
            String value = (String) this.staticHeaders.get(header);
            if (logger.isDebugEnabled()) {
                logger.debug("Setting header: " + header + ": " + value);
            }
            response.setHeader(header, value);
        }
    }
}
