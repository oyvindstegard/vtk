/* Copyright (c) 2007, University of Oslo, Norway
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
package vtk.web.decorating.components;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.context.ServletContextAware;

import com.sun.syndication.feed.synd.SyndFeed;

import vtk.web.decorating.DecoratorRequest;
import vtk.web.service.URL;
import vtk.web.servlet.BufferedResponse;
import vtk.web.servlet.ConfigurableRequestWrapper;
import vtk.web.servlet.VTKServlet;

public class LocalFeedFetcher implements ServletContextAware {

    private ServletContext servletContext;
    private SyndFeedBuilder feedBuilder;

    public SyndFeed getFeed(URL url, DecoratorRequest request) throws Exception {
        InputStream stream = openStream(url, request);
        SyndFeed feed = this.feedBuilder.build(stream);
        return feed;
    }
    

    private InputStream openStream(URL url, DecoratorRequest request)
        throws Exception {
        HttpServletRequest servletRequest = request.getServletRequest();

        if (servletRequest.getAttribute(IncludeComponent.INCLUDE_ATTRIBUTE_NAME) != null) {
            throw new DecoratorComponentException("Error including feed '" + url
                    + "': possible include loop detected ");
        }

        ConfigurableRequestWrapper requestWrapper = new ConfigurableRequestWrapper(servletRequest, url);
        requestWrapper.setAttribute(IncludeComponent.INCLUDE_ATTRIBUTE_NAME, new Object());

        String servletName = (String) servletRequest
                .getAttribute(VTKServlet.SERVLET_NAME_REQUEST_ATTRIBUTE);

        RequestDispatcher rd = this.servletContext.getNamedDispatcher(servletName);

        if (rd == null) {
            throw new RuntimeException("No request dispatcher for name '"
                    + servletName + "' available");
        }

        BufferedResponse servletResponse = new BufferedResponse(200);
        rd.forward(requestWrapper, servletResponse);

        int status = servletResponse.getStatus();
        
        // Follow one redirect:
        if (status == HttpServletResponse.SC_MOVED_PERMANENTLY 
                || status == HttpServletResponse.SC_MOVED_TEMPORARILY) {
            for (String name: servletResponse.getHeaderNames()) {
                if ("Location".equals(name)) {
                    String value = (String) servletResponse.getHeaderValue(name);
                    URL location = URL.parse(value);

                    if (location.getHost().equals(url.getHost())) {
                         requestWrapper = new ConfigurableRequestWrapper(
                                 servletRequest, location);
                        servletResponse = new BufferedResponse(200);
                        rd.forward(requestWrapper, servletResponse);
                    }
                }
            }
        }
        
        if (servletResponse.getStatus() != HttpServletResponse.SC_OK) {
            return null;
        }
        requestWrapper.setAttribute(IncludeComponent.INCLUDE_ATTRIBUTE_NAME, null);
        return new ByteArrayInputStream(servletResponse.getContentBuffer());
    }

    @Required
    public void setFeedBuilder(SyndFeedBuilder feedBuilder) {
        this.feedBuilder = feedBuilder;
    }

    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }
}
