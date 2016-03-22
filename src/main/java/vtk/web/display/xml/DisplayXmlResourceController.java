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
package vtk.web.display.xml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jdom.Document;
import org.jdom.input.SAXBuilder;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.mvc.LastModified;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.RepositoryException;
import vtk.repository.Resource;
import vtk.security.AuthenticationException;
import vtk.web.RequestContext;
import vtk.xml.TransformerManager;

/**
 * Controller that fetches an XML resource from the repository and
 * puts it in the model.
 * 
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>transformerManager</code> - the XSLT {@link
 *   TransformerManager transformer manager}
 *   <li><code>viewName</code> - the name used for the submodel
 *   provided. The default name is <code>transformXmlResource</code>.
 *   <li><code>childName</code> - if this optional property is set, it
 *   is appended to the current resource URI when retrieving the
 *   resource to display. This is typically used when displaying
 *   index resources (i.e. <code>childName = 'index.xml'</code>, for
 *   instance).
 *   <li><code>handleLastModified</code> - whether to return the real
 *   last modified value of the resource, or <code>-1</code> (the
 *   default)
 *   <li><code>ignoreXMLErrors</code> - whether or not to catch
 *   document build failures. Default is <code>false</code>.
 * </ul>
 *
 * <p>Model data provided:
 * <ul>
 *   <li><code>resource</code> - the {@link Resource} being displayed
 *   <li><code>jdomDocument</code> - the {@link org.jdom.Document}
 *   representation of the resource. If <code>ignoreXMLErrors</code>
 *   is <code>true</code> and there is an error building the document,
 *   this entry will NOT get placed in the model.
 * </ul>
 *
 */
public class DisplayXmlResourceController implements Controller, LastModified {

    private static Logger logger = LoggerFactory.getLogger(DisplayXmlResourceController.class);

    public static final String DEFAULT_VIEW_NAME = "transformXmlResource";
    private TransformerManager transformerManager;
    private String childName;
    private String viewName = DEFAULT_VIEW_NAME;
    private boolean handleLastModified = false;
    private boolean ignoreXMLErrors = false;    
    private LastModifiedEvaluator lastModifiedEvaluator;
    
    public void setChildName(String childName) {
        this.childName = childName;
    }

    @Required
    public void setTransformerManager(TransformerManager transformerManager) {
        this.transformerManager = transformerManager;
    }

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public void setHandleLastModified(boolean handleLastModified) {
        this.handleLastModified = handleLastModified;
    }

    public void setIgnoreXMLErrors(boolean ignoreXMLErrors) {
        this.ignoreXMLErrors = ignoreXMLErrors;
    }
    
    public void setLastModifiedEvaluator(LastModifiedEvaluator lastModifiedEvaluator) {
        this.lastModifiedEvaluator = lastModifiedEvaluator;
    }

    public long getLastModified(HttpServletRequest request) {
        
        if (!this.handleLastModified) {
            return -1;
        }

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();

        Path uri = requestContext.getResourceURI();

        if (this.childName != null) {
            uri = uri.extend(this.childName);
        }
        
        Resource resource = null;

        try {
            resource = repository.retrieve(token, uri, true);
                         
        } catch (RepositoryException e) {
            // These exceptions are expected
            return -1;

        } catch (AuthenticationException e) {
            // These exceptions are expected
            return -1;

        } catch (Throwable t) {
            if (logger.isInfoEnabled()) {
                logger.info("Unable to get the last modified date for resource " + uri, t);
            }
            return -1;
        }

        if (resource.isCollection()) {
            return -1;
        }

        if (lastModifiedEvaluator != null && !lastModifiedEvaluator.reportLastModified(resource)) {
            return -1;
        }
        
        return resource.getLastModified().getTime();        
    }
    

    public ModelAndView handleRequest(HttpServletRequest request,
                                      HttpServletResponse response) throws Exception {
		
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        Map<String, Object> model = new HashMap<String, Object>();
        Resource resource = repository.retrieve(token, uri, true);

        if (resource.isCollection()) {
            throw new IllegalStateException(
                "Unable to display collections");
        }
        model.put("resource", resource);

        InputStream stream = repository.getInputStream(token, uri, true);

        // Build a JDOM tree of the input stream:
        Document document = null;
        try {
                
            SAXBuilder builder = new SAXBuilder();
            document = builder.build(stream);
            document.setBaseURI(uri.toString());

        } catch (Throwable t) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to build JDOM document of resource " + resource, t);
            }
            if (!this.ignoreXMLErrors)
                throw new RuntimeException(t.fillInStackTrace());
        }

        if (document != null) {

            if (!this.ignoreXMLErrors) {
                this.transformerManager.getTransformer(document);
                if (logger.isDebugEnabled()) {
                    logger.debug("Successfully obtained XSLT transformer for resource "
                                 + resource);
                }
            }
            model.put("jdomDocument", document);
        }
        return new ModelAndView(this.viewName, model);
    }

}
