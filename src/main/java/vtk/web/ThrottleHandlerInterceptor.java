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
package vtk.web;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;


/**
 * Handler interceptor for limiting the number of concurrent requests
 * from a single IP.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>maxConcurrentRequests</code> - the number of concurrent
 *   requests to allow from a single client. A negative number means
 *   no limit. The default value is <code>-1</code>.
 *   <li><code>rejectStatus</code> - the HTTP status code to send when
 *   rejecting a request. The default is <code>503</code> (Service
 *   Temporarily Unavailable).
 * </ul>
 */
public class ThrottleHandlerInterceptor implements HandlerInterceptor {

    private final Map<String, Integer> requestCache = new HashMap<>();
    private int maxConcurrentRequests = -1;
    private int rejectStatus = 503;

    private static Logger logger = LoggerFactory.getLogger(
        ThrottleHandlerInterceptor.class);


    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }
    

    public void setRejectStatus(int rejectStatus) {
        this.rejectStatus = rejectStatus;
    }
    

    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        if (this.maxConcurrentRequests <= 0) {
            return true;
        }

        String ip = request.getRemoteAddr();
        boolean reject = false;

        int count = 0;
        synchronized (this.requestCache) {

            Integer number = (Integer) this.requestCache.get(ip);
            if (number != null) {
                count = number.intValue();
            }
            
            if (count < this.maxConcurrentRequests) {
                count++;
                this.requestCache.put(ip, new Integer(count));
            } else {
                reject = true;
            }
        }
        if (reject) {
            logger.info("Client " + ip + " already has " + this.maxConcurrentRequests + " active requests, "
                        + "rejecting current request with status " + this.rejectStatus);
            response.setContentLength(0);
            response.sendError(this.rejectStatus);
            return false;
        }
        return true;
    }
    

    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler,
                           ModelAndView modelAndView) throws Exception {
    }
    

    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception e) throws Exception {
        if (this.maxConcurrentRequests <= 0) {
            return;
        }

        String ip = request.getRemoteAddr();

        synchronized (this.requestCache) {
            Integer number = (Integer) this.requestCache.get(ip);
            if (number != null) {
                if (number.intValue() <= 1) {
                    this.requestCache.remove(ip);
                } else {
                    this.requestCache.put(ip, new Integer(number.intValue() - 1));
                }
            }
        }
    }
    
}
