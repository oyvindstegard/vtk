/* Copyright (c) 2011, University of Oslo, Norway
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
package vtk.web.display;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.context.ServletContextAware;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Path;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;
import vtk.web.servlet.ConfigurableRequestWrapper;
import vtk.web.servlet.VTKServlet;

/**
 * Internal forward of a request to a configurable service, on the same URI
 * as the original request.
 * 
 * <p>Configurable addition of extra headers, preservation of original request parameters
 * and addition of extra request parameters and/or attributes.
 * 
 * <p>The protocol of the original request URL is preserved if {@link #setPreserveRequestProtocol(boolean) }
 * is set to <code>true</code>.
 */
public class ForwardingController implements Controller, ServletContextAware {

    private ServletContext servletContext;
    private Service service;
    
    private Map<String, String> headers;
    private Map<String, Object> requestAttributes;
    private Map<String, String> requestParameters;
    private Set<String> preservedRequestParameters;
    private boolean preserveRequestProtocol = false;
    
    @Override
    public ModelAndView handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Path uri = requestContext.getResourceURI();
        URL requestedURL = requestContext.getRequestURL();
        
        URL dispatchURL = service.urlConstructor(requestContext.getRequestURL())
                .withURI(uri)
                .constructURL()
                .setCollection(requestedURL.isCollection());
        
        if (this.preserveRequestProtocol) {
            dispatchURL.setProtocol(requestedURL.getProtocol());
        }
        
        if (this.preservedRequestParameters != null) {
            for (String name: this.preservedRequestParameters) {
                List<String> values = requestedURL.getParameters(name);
                if (values != null) {
                    for (String value: values) {
                        dispatchURL.addParameter(name, value);
                    }
                }
            }
        }
        if (this.requestParameters != null) {
            for (String name: this.requestParameters.keySet()) {
                dispatchURL.addParameter(name, this.requestParameters.get(name));
            }
        }
        ConfigurableRequestWrapper requestWrapper = new ConfigurableRequestWrapper(request, dispatchURL);
        if (this.headers != null) {
            for (String name: this.headers.keySet()) {
                requestWrapper.addHeader(name, this.headers.get(name));
            }
        }
        if (this.requestAttributes != null) {
            for (String name: this.requestAttributes.keySet()) {
                requestWrapper.setAttribute(name, this.requestAttributes.get(name));
            }
        }

        String servletName = (String) request.getAttribute(
                VTKServlet.SERVLET_NAME_REQUEST_ATTRIBUTE);
        RequestDispatcher rd = this.servletContext.getNamedDispatcher(servletName);
        rd.forward(requestWrapper, response);
        return null;
    }

    @Override
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void setRequestAttributes(Map<String, Object> requestAttributes) {
        this.requestAttributes = requestAttributes;
    }

    public void setRequestParameters(Map<String, String> requestParameters) {
        this.requestParameters = requestParameters;
    }
    
    public void setPreservedRequestParameters(Set<String> preservedRequestParameters) {
        this.preservedRequestParameters = preservedRequestParameters;
    }
    
    /**
     * @param val set to <code>true</code> to preseve the protocol of the
     * request being forwarded. This will override the protocol in URL constructed
     * for the target service.
     */
    public void setPreserveRequestProtocol(boolean val) {
        this.preserveRequestProtocol = val;
    }
}
