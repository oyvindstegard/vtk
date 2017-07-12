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
package vtk.web.display.index;

import java.util.Enumeration;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.mvc.LastModified;
import vtk.repository.AuthorizationException;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.security.AuthenticationException;
import vtk.web.RequestContext;
import vtk.web.service.URL;
import vtk.web.servlet.ConfigurableRequestWrapper;
import vtk.web.servlet.VTKServlet;

/**
 * Index file controller. Uses {@link RequestContext#indexFileURI} to retrieve
 * the actual index file. If the current resource is not a collection, this
 * controller will fail.
 * 
 * <p>
 * The controller gets the name of the servlet to dispatch requests to (a
 * <code>ServletContext.getNamedDispatcher()</code> from a required request
 * attribute with id <code>VTKServlet.SERVLET_NAME_REQUEST_ATTRIBUTE</code>.
 * 
 * <p>
 * Configurable JavaBean properties:
 * <ul>
 * <li><code>repository</code> - the content {@link Repository repository}
 * </ul>
 */
public class IndexFileController implements Controller, LastModified, InitializingBean, ServletContextAware {
    private static final Logger logger = LoggerFactory.getLogger(IndexFileController.class);

    private ServletContext servletContext;

    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;

    }

    public void afterPropertiesSet() {
        if (this.servletContext == null) {
            throw new BeanInitializationException("JavaBean property 'servletContext' not set");
        }
    }

    public long getLastModified(HttpServletRequest request) {
        return -1L;
    }

    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext(request);
        String token = requestContext.getSecurityToken();
        Path currentURI = requestContext.getResourceURI();
        Repository repository = requestContext.getRepository();
        Resource res = repository.retrieve(token, currentURI, true);
        if (!res.isCollection()) {
            throw new IllegalStateException("Resource " + res + " is not a collection");
        }

        Path indexURI = requestContext.getIndexFileURI();
        Resource indexFile = null;
        try {
            indexFile = repository.retrieve(token, indexURI, true);
        } catch (AuthenticationException e) {
            throw e;
        } catch (AuthorizationException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("No index file found under " + res, t);
        }
        if (indexFile.isCollection()) {
            throw new IllegalStateException("Index file '" + indexURI + "' not a regular file");
        }
        URL indexFileURL = URL.create(request);
        indexFileURL.setCollection(false);
        indexFileURL.setPath(indexURI);

        /* Forwarding parameters for the index page request */
        Enumeration<String> p = request.getParameterNames();
        while (p.hasMoreElements()) {
            String key = p.nextElement();
            indexFileURL.addParameter(key, request.getParameter(key));
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Dispatch index file request to: " + indexFileURL);
        }
        ConfigurableRequestWrapper requestWrapper = new ConfigurableRequestWrapper(request, indexFileURL);

        String servletName = (String) request.getAttribute(VTKServlet.SERVLET_NAME_REQUEST_ATTRIBUTE);
        RequestDispatcher rd = this.servletContext.getNamedDispatcher(servletName);

        if (rd == null) {
            throw new RuntimeException("No request dispatcher for name '" + servletName + "' available");
        }

        try {
            requestWrapper.setAttribute(VTKServlet.INDEX_FILE_REQUEST_ATTRIBUTE, "true");
            rd.forward(requestWrapper, response);
        } finally {
            requestWrapper.removeAttribute(VTKServlet.INDEX_FILE_REQUEST_ATTRIBUTE);
        }
        return null;
    }
}
