/* Copyright (c) 2010, University of Oslo, Norway
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
package vtk.web.decorating;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.support.RequestContext;

public class DisplayComponentController implements Controller {
    private static Logger logger = LoggerFactory.getLogger(DisplayComponentController.class);

    private ComponentResolver componentResolver;
    private static final String DOCTYPE = "";
    
    @Override
    public ModelAndView handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        DecoratorComponent component = getComponent(request);
        if (component == null) {
            badRequest(response, "Missing or malformed 'component' parameter");
            return null;
        }
        ComponentInvocation invocation = getComponentInvocation(component, request);
        if (logger.isDebugEnabled()) {
            logger.debug("Invoke component: " + invocation);
        }
        renderComponent(component, invocation, request, response);
        return null;
    }
    
    private DecoratorComponent getComponent(HttpServletRequest request) {
        String component = request.getParameter("component");
        if (component == null || component.indexOf(":") == -1) {
            return null;
        }
        String[] parts = component.split(":");
        if (parts.length != 2) {
            return null;
        }
        String namespace = parts[0];
        String name = parts[1];
        return this.componentResolver.resolveComponent(namespace, name);
    }
    
    private ComponentInvocation getComponentInvocation(DecoratorComponent component, 
            HttpServletRequest request) {
        Map<String, Object> actualParameters = new HashMap<String, Object>();
        @SuppressWarnings("rawtypes")
        Enumeration e = request.getParameterNames();
        while (e.hasMoreElements()) {
            String name = (String) e.nextElement();
            if (name.startsWith("p:")) {
                String value = request.getParameter(name);
                name = name.substring(2);
                actualParameters.put(name, value);
            }
        }
        return new ComponentInvocationImpl(component.getNamespace(), 
                component.getName(), actualParameters);
    }
    
    private void renderComponent(DecoratorComponent component, ComponentInvocation inv, 
            HttpServletRequest request, HttpServletResponse response) throws Exception {
        Locale locale = new RequestContext(request).getLocale();
        DecoratorRequest decoratorRequest = new DecoratorRequestImpl(
                null, request, new HashMap<String, Object>(), 
                inv.getParameters(), DOCTYPE, locale);
        DecoratorResponseImpl decoratorResponse = new DecoratorResponseImpl(
                DOCTYPE, locale, "utf-8");
        component.render(decoratorRequest, decoratorResponse);
        byte[] buffer = decoratorResponse.getContent();
        response.setHeader("Content-Type", "text/html;charset=utf-8");
        response.setHeader("Content-Length", String.valueOf(buffer.length));
        ServletOutputStream out = response.getOutputStream();
        try {
            out.write(buffer);
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }
    
    private void badRequest(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().write(message);
    }

    @Required
    public void setComponentResolver(ComponentResolver componentResolver) {
        this.componentResolver = componentResolver;
    }

}
