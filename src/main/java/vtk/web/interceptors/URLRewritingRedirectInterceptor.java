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
package vtk.web.interceptors;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;



/**
 * Interceptor that redirects URLs that match a given pattern. The
 * redirect URL is configurable, either as a fixed string, or as a
 * rewrite of the requested URL, using regular expressions and
 * substitution variables.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>urlPattern</code> - a regular expression on the request
 *   URLs to match
 *   <li><code>rewritePattern</code> - the URL to redirect to. May
 *   contain substitution variables (e.g. <code>$1, $2, ...</code>),
 *   referring to the matching groups in the <code>urlPattern</code>,
 *   allowing more dynamic redirect control.
 * </ul>
 *
 */
public class URLRewritingRedirectInterceptor
  implements HandlerInterceptor, InitializingBean  {


    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private Pattern urlPattern = null;
    private String rewritePattern = null;


    public void setUrlPattern(String urlPattern) {
        this.urlPattern = Pattern.compile(urlPattern);
    }

    public void setRewritePattern(String rewritePattern) {
        this.rewritePattern = rewritePattern;
    }


    public void afterPropertiesSet() {
        if (this.urlPattern == null) {
            throw new BeanInitializationException(
                "JavaBean property 'urlPattern' not set");
        }
        if (this.rewritePattern == null) {
            throw new BeanInitializationException(
                "JavaBean property 'rewritePattern' not set");
        }
    }


    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        StringBuffer requestURL = request.getRequestURL();
        String queryString = request.getQueryString();
        if (queryString != null) {
            requestURL.append("?").append(queryString);
        }

        Matcher m = this.urlPattern.matcher(requestURL);
        if (!m.matches()) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Pattern does not match request URL '" + requestURL
                             + "', returning");
            }
            return true;
        }

        StringBuffer redirectURL = new StringBuffer(this.rewritePattern);
        if (m.groupCount() > 0) {

            for (int i = 0; i < m.groupCount(); i++) {

                String substitutionVar = "$" + (i + 1);
                if (this.logger.isDebugEnabled()) {
                    this.logger.debug("Checking for substitution variable " + substitutionVar);
                }

                int pos = -1;
                while ((pos = redirectURL.indexOf(substitutionVar)) != -1) {
                    int end = pos + substitutionVar.length();
                    redirectURL.replace(pos, end, m.group(i + 1));
                }
            }
        }

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Constructed rewritten redirect URL '" + redirectURL + "'");
        }

        response.sendRedirect(redirectURL.toString());
        return false;
    }
    

    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
    }

    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
    }
    

}
