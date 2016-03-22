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
package vtk.aop.interceptor;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

/**
 * A method interceptor that outputs a warning message if a method
 * invocation exceeds a certain time limit.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>timeLimitMilliseconds</code> - the time limit (in
 *   milliseconds)
 *   <li><code>logStackTraces</code> - whether to include a stack
 *   trace when logging (default <code>false</code>)
 * </ul>
 */
public class LoggingMethodTimerInterceptor implements MethodInterceptor, InitializingBean {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private long timeLimitMilliseconds = -1;
    private boolean logStackTraces = false;

    public void setTimeLimitMilliseconds(long timeLimitMilliseconds) {
        if (timeLimitMilliseconds <= 0) {
            throw new IllegalArgumentException(
                "Time limit must be a number of milliseconds > 0");
        }
        this.timeLimitMilliseconds = timeLimitMilliseconds;
    }
    
    public void setLogStackTraces(boolean logStackTraces) {
        this.logStackTraces = logStackTraces;
    }
    

    @Override
    public void afterPropertiesSet() {
        if (this.timeLimitMilliseconds <= 0) {
            throw new BeanInitializationException(
                "JavaBean property 'timeLimitMilliseconds' not specified");
        }
    }
    

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        long startTime = System.currentTimeMillis();
        try {
            Object retVal = methodInvocation.proceed();
            return retVal;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            if (duration > this.timeLimitMilliseconds) {
                log(methodInvocation, duration);
            }
        }
    }    
    
    private void log(MethodInvocation methodInvocation, long duration) {
        Object[] args = methodInvocation.getArguments();
        StringBuilder msg = new StringBuilder();
        msg.append("Method invocation took ").append(duration).append(" ms: ");
        msg.append(methodInvocation.getMethod().getDeclaringClass().getName()).append(".");
        msg.append(methodInvocation.getMethod().getName());

        if (args != null) {
            msg.append(", args: ").append(java.util.Arrays.asList(args));
        }

        StackTraceElement[] stackTrace = new Throwable().getStackTrace();

        if (this.logStackTraces) {

            msg.append("\nStacktrace:");
            for (StackTraceElement element: stackTrace) {
                msg.append("\n       ");
                msg.append(element.getClassName());
                msg.append(".").append(element.getMethodName());

                String file = element.getFileName();
                if (file != null) {
                    msg.append("(").append(file);
                    int line = element.getLineNumber();
                    if (line >= 0) {
                        msg.append(":").append(line);
                    }
                    msg.append(")");
                } else {
                    msg.append("(Unknown source)");
                }
            }
        }

        logger.warn(msg.toString());
        
    }
}

