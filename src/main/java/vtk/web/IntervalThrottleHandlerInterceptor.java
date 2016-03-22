/* Copyright (c) 2013, University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * A HandlerInterceptor that limits the number of requests 
 * from a single client address within a certain time interval. 
 * If the client exceeds the maximum number of requests, the 
 * request is rejected and an error code is returned.
 * 
 * <p>Configurable properties:
 * <ul>
 * <li>window - the time interval to use (in seconds)
 * <li>limit - maximum number of requests within the time interval
 * <li>rejectStatus - the HTTP status code to use when rejecting requests 
 *     (default 503) 
 * </ul>
 * </p>
 * <p>
 * Data structures are updated in a "best-effort" fashion 
 * using little synchronization, so the numbers are not necessarily completely
 * accurate. Even if some request records may get lost in certain scenarios, 
 * this is not critical.
 * </p>
 */
public class IntervalThrottleHandlerInterceptor implements HandlerInterceptor {

    private Map<String, List<Long>> requestLog = 
            new ConcurrentHashMap<String, List<Long>>();
    private int limit = -1;
    private int window = -1;
    private Timer timer;
    
    private int rejectStatus = 503;

    private static Logger logger = LoggerFactory.getLogger(
        IntervalThrottleHandlerInterceptor.class);

    public IntervalThrottleHandlerInterceptor() {
        timer = new Timer();
        timer.schedule(new GCTask(), 5000, 5000);
    }
    
    public void setLimit(int limit) {
        this.limit = limit;
    }
    
    public void setWindow(int seconds) {
        this.window = seconds;
    }
    
    public void setRejectStatus(int rejectStatus) {
        this.rejectStatus = rejectStatus;
    }
    
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (limit <= 0 || window <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        String addr = request.getRemoteAddr();
        List<Long> timestamps = requestLog.get(addr);
        
        if (timestamps == null) {
            timestamps = new ArrayList<Long>();
            requestLog.put(addr, timestamps);
        }
        
        int count = 0;
        long earliest = now - window * 1000;

        synchronized(timestamps) {
            timestamps.add(now);
            
            for (long t: timestamps) {
                if (t < earliest) continue;
                count++;
            }
        }
        if (count >= limit) {
            logger.info("Client " + addr + " already had " + limit 
                    + " requests within window of " + window + " seconds, "
                    + "rejecting with status " + rejectStatus);
            response.setContentLength(0);
            response.sendError(rejectStatus);
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
    }
    
    private void gc() {
        if (limit <= 0 || window <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long earliest = now - window * 1000;
        for (String addr: requestLog.keySet()) {
            List<Long> list = requestLog.get(addr);
            synchronized(list) {
                for (Iterator<Long> iter = list.iterator(); iter.hasNext();) {
                    long t = iter.next();
                    if (t < earliest) {
                        iter.remove();
                    }
                }
            }
            if (list.isEmpty()) {
                requestLog.remove(addr);
            }
        }
    }

    private class GCTask extends TimerTask {
        @Override
        public void run() {
            try {
                gc();
            } catch (Throwable t) {
                logger.info("Caught throwable in GC task, ", t);
            }
        }
    }
}
