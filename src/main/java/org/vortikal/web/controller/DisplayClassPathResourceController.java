/* Copyright (c) 2005, University of Oslo, Norway
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.mvc.LastModified;

import org.vortikal.util.repository.MimeHelper;
import org.vortikal.util.web.HttpUtil;
import org.vortikal.util.web.URLUtil;
import org.vortikal.web.RequestContext;
import org.vortikal.web.StaticResourceLocation;



/**
 * Controller that serves classpath resources.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <lI><code>expiresSeconds</code> - optional integer, will be sent
 *   as the <code>Expires</code> HTTP header 
 * </ul>
 */
public class DisplayClassPathResourceController 
  implements Controller, LastModified, InitializingBean, ApplicationContextAware {

    private Log logger = LogFactory.getLog(this.getClass());
    private Map locationsMap;
    private ApplicationContext applicationContext;

    private int expiresSeconds = -1;
    
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    public void setExpiresSeconds(int expiresSeconds) {
        this.expiresSeconds = expiresSeconds;
    }
    

    public void afterPropertiesSet() throws Exception {

        Map matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(
            this.applicationContext, StaticResourceLocation.class, true, false);
        Collection allLocations = matchingBeans.values();
        this.locationsMap = new HashMap();

        for (Iterator i = allLocations.iterator(); i.hasNext();) {
            StaticResourceLocation location = (StaticResourceLocation) i.next();

            String uri = location.getUriPrefix();
            String resourceLocation = location.getResourceLocation();
            this.locationsMap.put(uri, resourceLocation);
            this.locationsMap.put(uri + "/", resourceLocation);
        }

    }


    public ModelAndView handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        if (!("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return null;
        }

        Resource resource = resolveResource(request);
        if (!resource.exists()) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Unable to serve resource: " + resource
                                  + " from " + resource.getDescription()
                                  + ": resource does not exist");
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        InputStream inStream = null;
        OutputStream outStream = null;

        try {
            if (this.expiresSeconds >= 0) {
                long expiresMilliseconds = this.expiresSeconds * 1000;
                Date expires = new Date(System.currentTimeMillis() + expiresMilliseconds);
                response.setHeader("Expires", HttpUtil.getHttpDateString(expires));
            }

            response.setContentType(MimeHelper.map(request.getRequestURI()));

            if ("GET".equals(request.getMethod())) {

                inStream = resource.getInputStream();                
                outStream  = response.getOutputStream();
                byte[] buffer = new byte[5000];

                int n = 0;
                while (((n = inStream.read(buffer, 0, 5000)) > 0)) {
                    outStream.write(buffer, 0, n);
                }
            }
            
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Successfully served resource: " + resource
                                  + " from " + resource.getDescription());
            }

        } catch (Exception e) {

            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Unable to serve resource: " + resource
                                  + " from " + resource.getDescription(), e);
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND);

        } finally {
            if (inStream != null) inStream.close();
            if (outStream != null) outStream.close();
        }
        return null;
    }


    public long getLastModified(HttpServletRequest request) {
        RequestContext requestContext = RequestContext.getRequestContext();
        
        if (!"GET".equals(request.getMethod())) {
            return -1;
        }

        Resource resource = resolveResource(request);
        if (resource.exists()) {
            try {
                File f = resource.getFile();
                return f.lastModified();
            } catch (IOException e) {
                return this.applicationContext.getStartupDate();
            }
        }

        return -1;
    }


    private Resource resolveResource(HttpServletRequest request) {
        String[] incrementalPath = URLUtil.splitUriIncrementally(request.getRequestURI());
        String uriPrefix = null;
        String resourceLocation = null;
        for (int i = incrementalPath.length - 1; i > 0; i--) {
            if (this.locationsMap.containsKey(incrementalPath[i])) {
                resourceLocation = (String) this.locationsMap.get(incrementalPath[i]);
                uriPrefix = incrementalPath[i];
            }
        }
        if (resourceLocation == null) {
            return null;
        }

        RequestContext requestContext = RequestContext.getRequestContext();
        String uri = requestContext.getResourceURI();

        if (uriPrefix != null) {
            uri = uri.substring(uriPrefix.length());
        }

        String path = resourceLocation;
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        path += uri;

        Resource resource = null;
        if (path.startsWith("file://")) {
            String actualPath = path.substring("file://".length());
            resource = new FileSystemResource(actualPath);
        } else if (path.startsWith("classpath://")) {
            String actualPath = path.substring("classpath://".length());
            resource = new ClassPathResource(actualPath);
        } 
        return resource;
    }

    


}
