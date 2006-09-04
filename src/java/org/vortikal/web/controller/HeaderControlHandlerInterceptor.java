/* Copyright (c) 2006, University of Oslo, Norway
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
package org.vortikal.web.controller;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import org.vortikal.repository.Property;
import org.vortikal.repository.Resource;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.util.repository.LocaleHelper;
import org.vortikal.util.web.HttpUtil;

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
 *   the current resource. If this property exists, its value wlil be
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
 *   be the same as the value of the property.
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
public class HeaderControlHandlerInterceptor
  implements HandlerInterceptor, InitializingBean  {


    private Log logger = LogFactory.getLog(this.getClass());

    private PropertyTypeDefinition expiresHeaderProperty;
    private boolean includeLastModifiedHeader = false;
    private boolean includeContentLanguageHeader = false;
    private boolean includeEtagHeader = false;
    private boolean includeNoCacheHeader = false;
    private Map staticHeaders = new HashMap();
    
    public void setExpiresHeaderProperty(PropertyTypeDefinition expiresHeaderProperty) {
        if (expiresHeaderProperty != null) {
            if (expiresHeaderProperty.getType() != PropertyType.TYPE_LONG) {
                throw new IllegalArgumentException(
                    "expiresHeaderProperty must be a numeric property type definition");
            }
        }
        this.expiresHeaderProperty = expiresHeaderProperty;

    }

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


    public void setStaticHeaders(Map staticHeaders) {
        this.staticHeaders = staticHeaders;
    }
    

    public void afterPropertiesSet() {
        if (this.expiresHeaderProperty != null) {
            if (this.expiresHeaderProperty.getType() != PropertyType.TYPE_LONG) {
                throw new BeanInitializationException(
                    "JavaBean property 'expiresHeaderProperty' must be of type Long");
            }
        }
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
        Map model = modelAndView.getModel();

        resource = (Resource) model.get("resource");
        if (resource != null) {
            setLastModifiedHeader(resource, model, request, response);
            setEtagHeader(resource, model, request, response);
            setCacheControlHeader(resource, model, request, response);
            setExpiresHeader(resource, model, request, response);
            setContentLanguageHeader(resource, model, request, response);
        }
        setStaticHeaders(response);
    }


    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
    }
    


    protected void setExpiresHeader(Resource resource, Map model, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        if (this.expiresHeaderProperty != null) {
            
            Property expiresProperty = resource.getProperty(
                this.expiresHeaderProperty);

            if (expiresProperty != null && expiresProperty.getValue() != null) {

                long expiresMilliseconds = expiresProperty.getLongValue() * 1000;
                Date expires = new Date(new Date().getTime() + expiresMilliseconds);
                response.setHeader("Expires", HttpUtil.getHttpDateString(expires));

                if (logger.isDebugEnabled()) {
                    logger.debug("Setting header: Expires: " + HttpUtil.getHttpDateString(expires));
                }
            }
        }
    }

    protected void setContentLanguageHeader(Resource resource, Map model,
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
    

    protected void setLastModifiedHeader(Resource resource, Map model,
                                         HttpServletRequest request,
                                         HttpServletResponse response) throws Exception {
        
        if (this.includeLastModifiedHeader) {
            if (logger.isDebugEnabled()) {
                logger.debug("Setting header Last-Modified: "
                             + HttpUtil.getHttpDateString(resource.getLastModified()));
            }
            response.setHeader("Last-Modified", 
                               HttpUtil.getHttpDateString(resource.getLastModified()));
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("Setting HTTP status code: " + HttpServletResponse.SC_OK);
        }
    }


    protected void setEtagHeader(Resource resource, Map model, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        if (this.includeEtagHeader) {
            String etag = resource.getEtag();
            if (logger.isDebugEnabled()) {
                logger.debug("Setting header Etag: " + etag);
            }
            response.setHeader("ETag", etag);
        }
    }
    

    protected void setCacheControlHeader(Resource resource, Map model, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        if (this.includeNoCacheHeader) {
            response.setHeader("Cache-Control", "no-cache");
            if (logger.isDebugEnabled()) {
                logger.debug("Setting header Cache-Control: no-cache");
            }
        }
    }

    protected void setStaticHeaders(HttpServletResponse response) throws Exception {
        if (this.staticHeaders == null || this.staticHeaders.size() == 0) {
            return;
        }
        for (Iterator i = this.staticHeaders.keySet().iterator(); i.hasNext();) {
            String name = (String) i.next();
            String value = (String) this.staticHeaders.get(name);
            if (logger.isDebugEnabled()) {
                logger.debug("Setting header: " + name + ": " + value);
            }
            response.setHeader(name, value);
        }
    }

}
