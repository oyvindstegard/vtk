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
package vtk.web.display.file;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.mvc.LastModified;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.RepositoryException;
import vtk.repository.Resource;
import vtk.security.AuthenticationException;
import vtk.util.io.BoundedInputStream;
import vtk.util.io.IO;
import vtk.web.RequestContext;


/**
 * Controller that provides the requested resource and its input
 * stream in the model.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>childName</code> - if childName is set and the current
 *       resource is a collection, the child resource of that name is
 *       retrieved instead of the requested resource
 *   <li><code>streamToString</code> - set this to true if you
 *       want to provide the resource as resourceString model data
 *       instead of resourceStream if the content type is text based 
 *   <li><code>viewName</code> - name of the returned view. The
 *       default value is <code>displayResourceView</code>
 *   <li><code>view</code> - the actual {@link View} object (overrides
 *   <code>viewName</code>.
 *   <li><code>unsupportedResourceView</code> - name of returned view
 *       if the resource type is unsupported. Default value is
 *       <code>HTTP_STATUS_NOT_FOUND</code>
 *   <li><code>unsupportedResourceTypes</code> - list of content types
 *       that should return <code>unsupportedResourceView</code>.
 *   <li><code>displayProcessed</code> - wether the resource should be
 *       retrieved for processing (uio:readProcessed) or for raw
 *       access (dav:read). Defaults to false.
 *   <li><code>ignoreLastModified</code> - wether or not to ignore the
 *       resource's <code>lastModified</code> value. Setting this
 *       property to <code>true</code> means that the resource content
 *       cannot be cached by the client. Default is
 *       <code>true</code>.
 *   <li><code>ignoreLastModifiedOnCollections</code> - wether or not to ignore the
 *       resource's <code>lastModified</code> value when the resource is a collection.
 *       Default is <code>true</code>.
 * </ul>
 * </p>
 *
 * <p>Model data provided:
 * <ul>
 *   <li><code>resource</code> - the {@link Resource} object</li>
 *   <li><code>resourceStream</code> - the {@link InputStream} of the
 *       resource. (Note: be sure to couple this controller with a
 *       view that closes this stream)</li>
 *   <li><code>resourceString</code> - a {@link String} representation
 *       of the resource if <code>streamToString</code> is set and
 *       it's a text resource.
 * </ul>
 */
public class DisplayResourceController 
    implements Controller, LastModified, InitializingBean {

    public static final String DEFAULT_VIEW_NAME = "displayResourceView";
    private static final String defaultCharacterEncoding = "utf-8";
    private static final long streamToStringLimit = 2000000L;
    
    private boolean displayProcessed = false;

    private static Logger logger = LoggerFactory.getLogger(DisplayResourceController.class);

    private String childName;
    private String viewName = DEFAULT_VIEW_NAME;
    private View view = null;
    private String unsupportedResourceView = "HTTP_STATUS_NOT_FOUND";
    private Set<String> unsupportedResourceTypes = null;
    private boolean streamToString = false;
    private boolean ignoreLastModified = true;
    private boolean ignoreLastModifiedOnCollections = true;
    
    public void setChildName(String childName) {
        this.childName = childName;
    }


    public void setViewName(String viewName) {
        this.viewName = viewName;
    }
    

    public void setView(View view) {
        this.view = view;
    }
    
    public void setIgnoreLastModified(boolean ignoreLastModified) {
        this.ignoreLastModified = ignoreLastModified;
    }
    

    public void setIgnoreLastModifiedOnCollections(boolean ignoreLastModifiedOnCollections) {
        this.ignoreLastModifiedOnCollections = ignoreLastModifiedOnCollections;
    }
    

    public void setUnsupportedResourceTypes(Set<String> unsupportedResourceTypes) {
        this.unsupportedResourceTypes = unsupportedResourceTypes;
    }
    

    public void setStreamToString(boolean streamToString) {
        this.streamToString = streamToString;
    }


    public void setDisplayProcessed(boolean displayProcessed) {
        this.displayProcessed = displayProcessed;
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.unsupportedResourceTypes == null) {
            this.unsupportedResourceTypes = new HashSet<>();
        }
        if (this.viewName == null && this.view == null) {
            throw new BeanInitializationException(
                "At least one of JavaBean properties 'viewName' or 'view' must "
                + "be specified");
        }
    }


    @Override
    public ModelAndView handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();

        if (this.childName != null) {
            uri = uri.extend(this.childName);
        }

        Map<String, Object> model = new HashMap<>();
        Resource resource = repository.retrieve(token, uri, this.displayProcessed);
        if (this.unsupportedResourceTypes.contains(resource.getContentType())) {
            return new ModelAndView(this.unsupportedResourceView);
        }

        model.put("resource", resource);

        if (!resource.isCollection()) {

            InputStream stream = repository.getInputStream(token, uri, true);
            
            if (this.streamToString) {
                stream = new BoundedInputStream(stream, streamToStringLimit);
            }
            else {
                model.put("resourceStream", stream);
                if (this.view != null) {
                    return new ModelAndView(this.view, model);
                }
                return new ModelAndView(this.viewName, model);
            }
            

            
            // Provide as string instead of stream
            String characterEncoding = resource.getCharacterEncoding();
            if (characterEncoding == null) {
                characterEncoding = defaultCharacterEncoding;
            }

            String content = IO.readString(stream, characterEncoding).perform();
            model.put("resourceString", content);
        
        }
        if (this.view != null) {
            return new ModelAndView(this.view, model);
        }
        return new ModelAndView(this.viewName, model);
    }


    @Override
    public long getLastModified(HttpServletRequest request) {

        if (this.ignoreLastModified) {
            if (logger.isDebugEnabled()) {
                logger.debug("Ignoring last-modified value for request "
                             + request.getRequestURI());
            }
            return -1;
        }
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Resource resource = null;
        
        Path uri = requestContext.getResourceURI();
        
        if (this.childName != null) {
            uri = uri.extend(this.childName);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Getting last-modified value for resource "
                         + uri);
        }

        try {
            resource = repository.retrieve(token, uri, true);
        } catch (RepositoryException e) {
            // These exceptions are expected
            if (logger.isDebugEnabled()) {
                logger.debug("Unable to get last-modified value for resource "
                             + uri, e);
            }
            return -1;
        } catch (AuthenticationException e) {
            // These exceptions are expected
            if (logger.isDebugEnabled()) {
                logger.debug("Unable to get last-modified value for resource "
                             + uri, e);
            }
            return -1;
        } catch (Throwable t) {
            if (logger.isInfoEnabled()) {
                logger.info(
                    "Unable to get the last-modified value for resource "
                    + uri, t);
            }
            return -1;
        }
        if (resource.isCollection() && this.ignoreLastModifiedOnCollections) {
            logger.debug("Ignorig last-modified value for resource "
                         + uri + ": resource is collection");
            return -1;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Returning last-modified value for resource "
                         + uri + ": " + resource.getLastModified());
        }
        return resource.getLastModified().getTime();
    }

}
