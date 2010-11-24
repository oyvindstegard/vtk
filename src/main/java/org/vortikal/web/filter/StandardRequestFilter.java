/* Copyright (c) 2005, University of Oslo, Norway
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
package org.vortikal.web.filter;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.vortikal.util.web.HttpUtil;
import org.vortikal.web.service.URL;

/**
 * Standard request filter.
 * 
 * <ol>
 * <li>Ensures that the URI is not empty or <code>null</code>.
 * <li>Translates '*' as request URI to '/' (relevant for a host global OPTIONS request).
 * <li>Supports the de-facto header <code>X-Forwarded-For</code> with a client IP
 * <li>Also supports translating forwarded requests using a header with optional 
 *    fields <code>{host, port, protocol, client}</code>
 *    Example: <code>X-My-Forward-Header: host=example.com,port=443,protocol=https</code>
 * </ol>
 */
public class StandardRequestFilter extends AbstractRequestFilter {

    private static Log logger = LogFactory.getLog(StandardRequestFilter.class);

    private Map<Pattern, String> urlReplacements;
    private Pattern xForwardedFor = null;
    private String requestForwardFieldHeader = null;

    public void setUrlReplacements(Map<String, String> urlReplacements) {
        this.urlReplacements = new LinkedHashMap<Pattern, String>();
        for (String key : urlReplacements.keySet()) {
            Pattern pattern = Pattern.compile(key);
            String replacement = urlReplacements.get(key);
            this.urlReplacements.put(pattern, replacement);
        }
    }

    public void setxForwardedFor(String xForwardedFor) {
        if (xForwardedFor != null && !"".equals(xForwardedFor.trim())) {
            this.xForwardedFor = Pattern.compile(xForwardedFor);
        }
    }
    
    public void setRequestForwardFieldHeader(String forwardHeader) {
        this.requestForwardFieldHeader = forwardHeader;
    }

    public HttpServletRequest filterRequest(HttpServletRequest request) {
        return new StandardRequestWrapper(request);
    }

    private class StandardRequestWrapper extends HttpServletRequestWrapper {
        private String uri;
        private URL requestURL;
        private String client = null;
        private Set<String> absorbedHeaders = new HashSet<String>();
        
        public StandardRequestWrapper(HttpServletRequest request) {
            super(request);
            String requestURI = request.getRequestURI();
            this.uri = translateUri(requestURI);
            if (logger.isDebugEnabled()) {
                logger.debug("Translated uri: from '" + requestURI + "' to '" + this.uri + "'");
            }
            this.requestURL = URL.parse(request.getRequestURL().toString());
            if (xForwardedFor != null && xForwardedFor.matcher(request.getRequestURL()).matches()) {
                String xForwardHeader = request.getHeader("X-Forwarded-For");
                if (xForwardHeader != null) {
                    xForwardHeader = xForwardHeader.split(" ")[0];
                    if (xForwardHeader.indexOf(",") != -1) {
                        xForwardHeader = xForwardHeader.substring(0, xForwardHeader.indexOf(","));
                    }
                    this.client = xForwardHeader;
                    this.absorbedHeaders.add("X-Forwarded-For");
                }
            }
            
            if (requestForwardFieldHeader == null || requestForwardFieldHeader.trim().equals("")) {
                return;
            }
            String forwardHeader = request.getHeader(requestForwardFieldHeader);
            if (forwardHeader == null) {
                return;
            }
            this.absorbedHeaders.add(requestForwardFieldHeader);
            String host = HttpUtil.extractHeaderField(forwardHeader, "host");
            String port = HttpUtil.extractHeaderField(forwardHeader, "port");
            String protocol = HttpUtil.extractHeaderField(forwardHeader, "protocol");
            String client = HttpUtil.extractHeaderField(forwardHeader, "client");
            if (protocol != null) {
                this.requestURL.setProtocol(protocol);
            }
            if (port != null) {
                this.requestURL.setPort(Integer.parseInt(port));
            }
            if (host!= null) {
                this.requestURL.setHost(host);
            }
            if (client != null) {
                this.client = client;
            }
        }
        
        @Override
        public long getDateHeader(String name) {
            if (this.absorbedHeaders.contains(name)) {
                return -1;
            }
            return super.getDateHeader(name);
        }

        @Override
        public String getHeader(String name) {
            if (this.absorbedHeaders.contains(name)) {
                return null;
            }
            return super.getHeader(name);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Enumeration getHeaders(String name) {
            if (this.absorbedHeaders.contains(name)) {
                return null;
            }
            return super.getHeaders(name);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Enumeration getHeaderNames() {
            Enumeration e = super.getHeaderNames();
            Set<String> set = new HashSet<String>();
            while (e.hasMoreElements()) {
                String h = (String) e.nextElement();
                if (!this.absorbedHeaders.contains(h)) {
                    set.add(h);
                }
            }
            return Collections.enumeration(set);
        }

        @Override
        public int getIntHeader(String name) {
            if (this.absorbedHeaders.contains(name)) {
                return -1;
            }
            return super.getIntHeader(name);
        }

        @Override
        public StringBuffer getRequestURL() {
            return new StringBuffer(this.requestURL.toString());
        }

        @Override
        public String getScheme() {
            return this.requestURL.getProtocol();
        }

        @Override
        public int getServerPort() {
            return this.requestURL.getPort();
        }

        @Override
        public String getServerName() {
            return this.requestURL.getHost();
        }

        @Override
        public boolean isSecure() {
            return "https".equals(this.requestURL.getProtocol());
        }

        @Override
        public String getRequestURI() {
            return this.uri;
        }
        
        @Override
        public String getRemoteAddr() {
            if (this.client != null) {
                return this.client;
            }
            return super.getRemoteAddr();
        }

        @Override
        public String getRemoteHost() {
            if (this.client != null) {
                return this.client;
            }
            return super.getRemoteHost();
        }

        private String translateUri(String requestURI) {
            if (requestURI == null 
                    || "".equals(requestURI)
                    || "*".equals(requestURI)) {
                return "/";
            }

            if (urlReplacements != null) {
                for (Pattern pattern : urlReplacements.keySet()) {
                    String replacement = urlReplacements.get(pattern);
                    requestURI = pattern.matcher(requestURI).replaceAll(replacement);
                }
            }
            return requestURI;
        }
    }

}
