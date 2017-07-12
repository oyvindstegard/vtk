/* Copyright (c) 2005, 2008, University of Oslo, Norway
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
package vtk.web.service;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

import vtk.repository.Resource;
import vtk.security.Principal;

/**
 * Assertion that matches a regular expression (or a list of regular
 * expression) against the value of a configurable request header.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>header</code> - the name of the header to check
 *   <li><code>pattern</code> - a regular expression to match
 *   <li><code>patterns</code> - a list of regular expressions to
 *   match. If one of these matches, the assertion also matches. This
 *   setting is compatible with the <code>pattern</code> setting
 *   (meaning if both is specified, they are both matched).
 *   <li><code>invert</code> - if set to <code>true</code>, match only when 
 *   none of the patterns match.
 * </ul>
 */
public class RequestHeaderRegexpAssertion implements WebAssertion, InitializingBean {

    private List<Pattern> patternsList = new ArrayList<>();
    private String header;
    private boolean invert = false;


    public void setPattern(String pattern) {
        if (pattern != null) {
            this.patternsList.add(Pattern.compile(pattern));
        }
    }
    
    
    public void setPatterns(String[] patterns) {
        if (patterns != null) {
            for (int i = 0; i < patterns.length; i++) {
                this.patternsList.add(Pattern.compile(patterns[i]));
            }
        }
    }
    

    public void setHeader(String header) {
        if (header == null) throw new IllegalArgumentException(
            "Property 'header' cannot be null");
    
        this.header = header;
    }
    

    public void afterPropertiesSet() {
        if (this.patternsList.size() == 0) {
            throw new BeanInitializationException(
                "At least one of the JavaBean properties 'pattern' or 'patterns' must be specified");
        }
        if (this.header == null) {
            throw new BeanInitializationException(
                "JavaBean property 'header' must be specified");
        }
    }
    

    public boolean conflicts(WebAssertion assertion) {
        return false;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("request.headers[");
        sb.append(this.header).append("] ");
        if (this.invert) sb.append("!");
        sb.append("~ ");
        if (this.patternsList.size() == 1) {
            sb.append(this.patternsList.get(0).pattern());
            return sb.toString();
        }
        sb.append("(");
        for (Iterator<Pattern> i = this.patternsList.iterator(); i.hasNext();) {
            sb.append(i.next().pattern());
            if (i.hasNext()) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }


    @Override
    public boolean matches(HttpServletRequest request, Resource resource,
                           Principal principal) {
        boolean matched = match(request); 
        if (this.invert) return !matched;
        return matched;

    }
     
    @Override
    public Optional<URL> processURL(URL url, Resource resource, Principal principal) {
        return Optional.of(url);
    }

    @Override
    public URL processURL(URL url) {
        return url;
    }

    private boolean match(HttpServletRequest request) {
        String headerValue = request.getHeader(this.header);
        if (headerValue == null) {
            return false;
        }
              
        for (Pattern pattern: this.patternsList) {
            if (pattern.matcher(headerValue).matches()) {
                return true;
            }
        }
        return false;
    }


    public void setInvert(boolean invert) {
        this.invert = invert;
    }


}
