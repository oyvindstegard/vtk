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
package vtk.web.display.classpath;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.mvc.LastModified;

import vtk.resourcemanagement.StaticResourceResolver;
import vtk.util.io.IO;
import vtk.util.repository.MimeHelper;
import vtk.web.service.URL;

/**
 * Controller that serves class path resources.
 * 
 * <p>
 * Configurable JavaBean properties:
 * <ul>
 * <li><code>headers</code> - map of (name, value) pairs that will be sent as
 * response headers</li>
 * </ul>
 */
public class DisplayClassPathResourceController implements
        Controller, LastModified, ApplicationContextAware {
    private static final Logger logger = LoggerFactory
            .getLogger(DisplayClassPathResourceController.class);
    private StaticResourceResolver staticResourceResolver;
    private ApplicationContext applicationContext;
    private Map<String, String> headers;
    private boolean handleLastModified;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return null;
        }

        URL url = URL.create(request);
        Resource resource = this.staticResourceResolver.resolve(url.getPath());
        if (resource == null) {
            logger.debug("Unable to serve resource: {}: not found", url.getPath());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        else if (!resource.exists()) {
            logger.debug("Unable to serve resource: {} from {}: not found", 
                    url.getPath(), resource.getDescription());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        else if (!resource.isReadable()) {
            logger.debug("Unable to serve resource: {} from {}: not readable", 
                    url.getPath(), resource.getDescription());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        try {
            Stream stream = openStream(resource);
            InputStream inStream = stream.stream;
            int contentLength = stream.contentLength;

            response.setContentType(MimeHelper.map(request.getRequestURI()));
            if (contentLength != -1) {
                response.setContentLength(contentLength);
            }
            for (String header : this.headers.keySet()) {
                response.addHeader(header, this.headers.get(header));
            }

            if ("GET".equals(request.getMethod())) {
                IO.copy(inStream, response.getOutputStream()).perform();
            }

            logger.debug("Successfully served resource: {} from {}", 
                    resource, resource.getDescription());
        }
        catch (IOException e) {
            logger.debug("Unable to serve resource: {} from {}", 
                    resource, resource.getDescription(), e);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
        return null;
    }

    @Override
    public long getLastModified(HttpServletRequest request) {
        if (!this.handleLastModified) {
            return -1;
        }
        if (!("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
            return -1;
        }

        URL url = URL.create(request);
        Resource resource = this.staticResourceResolver.resolve(url.getPath());
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

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void setStaticResourceResolver(StaticResourceResolver staticResourceResolver) {
        this.staticResourceResolver = staticResourceResolver;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void setHandleLastModified(boolean handleLastModified) {
        this.handleLastModified = handleLastModified;
    }

    private static final class Stream {
        int contentLength = -1;
        InputStream stream;
    }

    private Stream openStream(Resource resource) throws IOException {
        Stream stream = new Stream();
        if (resource instanceof ClassPathResource) {
            java.net.URL url = resource.getURL();
            URLConnection connection = url.openConnection();
            stream.contentLength = connection.getContentLength();
            stream.stream = connection.getInputStream();
        }
        else if (resource instanceof FileSystemResource) {
            File file = resource.getFile();
            stream.contentLength = (int) file.length();
            stream.stream = resource.getInputStream();
        }
        else {
            stream.stream = resource.getInputStream();
        }
        return stream;
    }

}
