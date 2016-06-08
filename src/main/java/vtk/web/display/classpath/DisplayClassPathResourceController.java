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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import vtk.repository.Path;
import vtk.util.io.IO;
import vtk.util.repository.MimeHelper;
import vtk.util.web.LinkTypesPrefixes;
import vtk.web.RequestContext;
import vtk.web.StaticResourceLocation;
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
public class DisplayClassPathResourceController implements Controller, LastModified, InitializingBean,
        ApplicationContextAware {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private Map<Path, String> locationsMap;
    private Map<String, String> headers;
    private ApplicationContext applicationContext;
    private boolean handleLastModified;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void setHandleLastModified(boolean handleLastModified) {
        this.handleLastModified = handleLastModified;
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        Map<String, StaticResourceLocation> matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(
                this.applicationContext, StaticResourceLocation.class, true, false);
        Collection<StaticResourceLocation> allLocations = matchingBeans.values();
        this.locationsMap = new HashMap<Path, String>();

        for (StaticResourceLocation location : allLocations) {
            Path uri = location.getPrefix();
            String resourceLocation = location.getResourceLocation();
            this.locationsMap.put(uri, resourceLocation);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Locations map: " + this.locationsMap);
        }
    }

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        if (!("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return null;
        }

        Resource resource = resolveResource(request);
        if (resource == null || !resource.exists()) {
            if (this.logger.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder("Unable to serve resource: " + resource);
                if (resource != null) {
                    sb.append(" from " + resource.getDescription() + ": resource does not exist");
                }
                this.logger.debug(sb.toString());
            }
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

            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Successfully served resource: " + resource + " from " + resource.getDescription());
            }
        } catch (IOException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Unable to serve resource: " + resource + " from " + resource.getDescription(), e);
            }
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
        } else if (resource instanceof FileSystemResource) {
            File file = resource.getFile();
            stream.contentLength = (int) file.length();
            stream.stream = resource.getInputStream();
        } else {
            stream.stream = resource.getInputStream();
        }
        return stream;
    }

    private Resource resolveResource(HttpServletRequest request) {
        URL url = URL.create(request);
        List<Path> paths = url.getPath().getPaths();

        Path uriPrefix = null;
        String resourceLocation = null;
        for (int i = paths.size() - 1; i >= 0; i--) {
            Path prefix = paths.get(i);
            if (this.locationsMap.containsKey(prefix)) {
                resourceLocation = this.locationsMap.get(prefix);
                uriPrefix = prefix;
            }
        }

        if (resourceLocation == null) {
            return null;
        }

        RequestContext requestContext = RequestContext.getRequestContext();
        Path uri = requestContext.getResourceURI();
        if (uriPrefix != null) {
            Path p = Path.ROOT;
            int offset = uriPrefix.getDepth() + 1;
            List<String> elements = uri.getElements();
            for (int i = offset; i < elements.size(); i++) {
                p = p.extend(elements.get(i));
            }
            uri = p;
        }

        String loc = resourceLocation;
        if (loc.endsWith("/")) {
            loc = loc.substring(0, loc.length() - 1);
        }
        loc += uri;

        if (loc.startsWith(LinkTypesPrefixes.FILE + "//")) {
            String actualPath = loc.substring((LinkTypesPrefixes.FILE + "//").length());
            return new FileSystemResource(actualPath);
        }

        if (loc.startsWith("classpath://")) {
            String actualPath = loc.substring("classpath://".length());
            return new ClassPathResource(actualPath);
        }
        return null;
    }

}
