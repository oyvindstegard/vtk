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
package org.vortikal.web.service;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.vortikal.web.RequestContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;




/**
 * HandlerMapping which uses a service tree to resolve the handler to
 * delegate the request to. Note: this handler mapper must be
 * configured in the application context in order for the Vortex
 * framework to be able to function correctly (i.e. map requests to
 * the correct handler based on the current service).
 */
public class ServiceHandlerMapping implements HandlerMapping {

    private static Log logger = LogFactory.getLog(ServiceHandlerMapping.class);

    /**
     * Look up a handler for the given request, falling back to the default
     * handler if no specific one is found.
     * @param request current HTTP request
     * @return the looked up handler instance, or the default handler
     */
    public final HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
        Service service = RequestContext.getRequestContext().getService();

        Object handler = getController(service);

        if (handler == null) {
            return null;
        }

        List handlerInterceptorsList = getHandlerInterceptors(service);
        HandlerInterceptor[] handlerInterceptors;
        
        if (handlerInterceptorsList.size() < 1) 
            handlerInterceptors = null;
        else {
            handlerInterceptors = 
                (HandlerInterceptor[]) handlerInterceptorsList.toArray(
                    new HandlerInterceptor[handlerInterceptorsList.size()]);
        }
        return new HandlerExecutionChain(handler, handlerInterceptors);

    }

    

    private Object getController(Service service) {
        Object controller = service.getHandler();
        if (controller == null && service.getParent() != null) {
            return getController(service.getParent());
        }

        return controller;
    }

    private List getHandlerInterceptors(Service service) {
        List handlerInterceptors = new ArrayList();
        
        if (service.getParent() != null) {
            List parentInterceptors = getHandlerInterceptors(service.getParent());
            if (parentInterceptors != null) handlerInterceptors.addAll(parentInterceptors);
        }
        List myHandlerInterceptors = service.getHandlerInterceptors();
        if (myHandlerInterceptors != null) handlerInterceptors.addAll(myHandlerInterceptors);
        
        return handlerInterceptors;
    }
}
