/* Copyright (c) 2017, University of Oslo, Norway
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
package vtk.web.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.web.service.URL;

public class TranslateURLFilter extends AbstractServletFilter {
    private static Logger logger = LoggerFactory.getLogger(TranslateURLFilter.class);
    private Map<Pattern, String> replacements = new HashMap<>();
    
    public TranslateURLFilter(Map<String, String> replacements) {
        if (replacements != null) {
            for (String key: replacements.keySet()) {
                String value = replacements.get(key);
                this.replacements.put(Pattern.compile(key), value);
            }
        }
    }

    @Override
    protected void doFilter(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String requestURL = request.getRequestURL().toString();

        for (Pattern pattern: replacements.keySet()) {
            String replacement = replacements.get(pattern);

            Matcher matcher = pattern.matcher(requestURL);
            if (matcher.matches()) {
                String before = requestURL;
                requestURL = matcher.replaceAll(replacement);
                URL url = URL.parse(request.getRequestURL().toString())
                        .relativeURL(requestURL).setImmutable();
                logger.debug("Map URL: " + before + " -> " + requestURL);
                chain.doFilter(new TranslatingRequest(request, url), response);
                return;
            }
        }
        chain.doFilter(request, response);
    }
    
    private static class TranslatingRequest extends HttpServletRequestWrapper {
        private URL url;
        public TranslatingRequest(HttpServletRequest request, URL url) {
            super(request);
            this.url = url;
        }
        @Override
        public String getRequestURI() {
            String uri = url.getPath().toString();
            if (url.isCollection()) return uri + "/";
            return uri;
        }
        @Override
        public StringBuffer getRequestURL() {
            return new StringBuffer(url.toString());
        }
        @Override
        public String getParameter(String name) {
            return url.getParameter(name);
            
        }
        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, List<String>> map = new HashMap<>();
            for (String key: url.getParameterNames()) {
                map.putIfAbsent(key, new ArrayList<>());
                map.get(key).add(url.getParameter(key));
            }
            Map<String, String[]> result = new HashMap<>();
            for (String key: map.keySet()) {
                List<String> list = map.get(key);
                result.put(key, list.toArray(new String[list.size()]));
            }
            return result;
        }
        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(url.getParameterNames());
        }
        @Override
        public String[] getParameterValues(String name) {
            List<String> parameters = url.getParameters(name);
            if (parameters == null) {
                return null;
            }
            return parameters.toArray(new String[parameters.size()]);
            
        }
        @Override
        public String getScheme() {
            return url.getProtocol();
        }
        @Override
        public String getServerName() {
            return url.getHost();
        }
        @Override
        public int getServerPort() {
            return url.getPort();
        }
        @Override
        public boolean isSecure() {
            return url.getProtocol().equals("https");
        }
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + replacements + ")";
    }
}
