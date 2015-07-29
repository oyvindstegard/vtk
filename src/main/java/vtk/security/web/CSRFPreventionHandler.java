/* Copyright (c) 2009, 2015, University of Oslo, Norway
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
package vtk.security.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import vtk.repository.AuthorizationException;
import vtk.repository.Path;
import vtk.security.AuthenticationException;
import vtk.text.html.HtmlPushTokenizer;
import vtk.text.html.HtmlPushTokenizer.Attribute;
import vtk.text.html.HtmlPushTokenizer.ElementNode;
import vtk.text.html.HtmlUtil;
import vtk.util.io.CharsetPushDecoder;
import vtk.util.text.TextUtils;
import vtk.web.RequestContext;
import vtk.web.filter.AbstractRequestFilter;
import vtk.web.filter.AbstractResponseFilter;
import vtk.web.filter.ServiceFilter;
import vtk.web.filter.ServiceFilterChain;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * Cross Site Request Forgery (CSRF) prevention handler. This class performs two
 * tasks:
 * <ol>
 * <li>Generates tokens in HTML forms on the page being served</li>
 * <li>Verifies that valid tokens are present in POST requests</li>
 * </ol>
 */
public class CSRFPreventionHandler implements ServiceFilter {

    public static final String TOKEN_REQUEST_PARAMETER = "csrf-prevention-token";
    private static final String SECRET_SESSION_ATTRIBUTE = "csrf-prevention-secret";
    private static Log logger = LogFactory.getLog(CSRFPreventionHandler.class);
    private static String ALGORITHM = "HmacSHA1";

    /**
     * Utility method that can be called, e.g. from views
     * 
     * @return a new CSRF prevention token
     */
    public String newToken(URL url) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        HttpServletRequest servletRequest = requestContext.getServletRequest();
        HttpSession session = servletRequest.getSession(false);
        if (session == null) {
            throw new IllegalStateException("Session does not exist");
        }
        url = new URL(url);
        url.setRef(null);
        return generateToken(url, session);
    }

    @Override
    public void filter(HttpServletRequest request,
            HttpServletResponse response, ServiceFilterChain chain)
            throws Exception {
        
        HttpSession session = request.getSession(false);
        if (session == null) return;
        
        if ("POST".equals(request.getMethod())) {
            chain.filter(new AbstractRequestFilter() {
                @Override
                public HttpServletRequest filterRequest(HttpServletRequest request) {
                    return new TokenVerifyingRequest(request);
                }
            });
        }
        
        chain.filter(new AbstractResponseFilter() {
            @Override
            public HttpServletResponse filter(HttpServletRequest request,
                    HttpServletResponse response) {
                return new TokenGeneratingFilter(response, session);
            }
        });
    }

    private static String generateToken(URL url, HttpSession session) {
        SecretKey secret = (SecretKey) session.getAttribute(SECRET_SESSION_ATTRIBUTE);
        if (secret == null) {
            secret = generateSecret();
            session.setAttribute(SECRET_SESSION_ATTRIBUTE, secret);
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secret);
            byte[] buffer = (url.toString() + session.getId()).getBytes("utf-8");
            byte[] hashed = mac.doFinal(buffer);
            String result = new String(TextUtils.toHex(hashed));
            if (logger.isDebugEnabled()) {
                logger.debug("Generate token: url: " + url + ", token: " + result + ", secret: " + secret);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate token", e);
        }
    }

    private static SecretKey generateSecret() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(ALGORITHM);
            return kg.generateKey();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate secret key", e);
        }
    }

    private URL parseActionURL(String action) {
        if (action.startsWith("http://") || action.startsWith("https://")) {
            URL url = URL.parse(HtmlUtil.decodeBasicEntities(action));
            return url;
        }

        HttpServletRequest request = RequestContext.getRequestContext().getServletRequest();
        URL url = URL.create(request);
        url.clearParameters();
        Path path;
        String[] segments = action.split("/");
        int startIdx = 0;
        if (action.startsWith("/")) {
            path = Path.ROOT;
            startIdx = 1;
        }
        else {
            path = RequestContext.getRequestContext().getCurrentCollection();
        }

        String query = null;
        for (int i = startIdx; i < segments.length; i++) {
            String elem = segments[i];
            if (elem.contains("?")) {
                query = elem.substring(elem.indexOf("?"));
                elem = elem.substring(0, elem.indexOf("?"));
            }
            path = path.expand(elem);
        }

        url.setPath(path);
        if (query != null) {
            Map<String, String[]> queryMap = URL.splitQueryString(query);
            for (String key : queryMap.keySet()) {
                for (String value : queryMap.get(key)) {
                    url.addParameter(key, value);
                }
            }
        }
        return url;
    }

    private class TokenVerifyingRequest extends HttpServletRequestWrapper {
        private HttpServletRequest req;
        private boolean verified = false;
        
        public TokenVerifyingRequest(HttpServletRequest request) {
            super(request);
            this.req = request;
        }
        
        @Override
        public ServletInputStream getInputStream() throws IOException {
            verifyToken(req);
            return super.getInputStream();
        }

        @Override
        public String getParameter(String name) {
            verifyToken(req);
            return super.getParameter(name);
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            verifyToken(req);
            return super.getParameterMap();
        }

        @Override
        public Enumeration<String> getParameterNames() {
            verifyToken(req);
            return super.getParameterNames();
        }

        @Override
        public String[] getParameterValues(String name) {
            verifyToken(req);
            return super.getParameterValues(name);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            verifyToken(req);
            return super.getReader();
        }
        
        protected void verifyToken(HttpServletRequest request) {
            if (verified) return;
            RequestContext requestContext = RequestContext.getRequestContext();
            if (requestContext.getPrincipal() == null) {
                throw new AuthenticationException("Illegal anonymous action");
            }
            HttpSession session = request.getSession(false);
            if (session == null) {
                throw new IllegalStateException("A session must be present");
            }
            Service service = requestContext.getService();
            if (Boolean.TRUE.equals(service.getAttribute("disable-csrf-checking"))) {
                return;
            }
            SecretKey secret = (SecretKey) session.getAttribute(SECRET_SESSION_ATTRIBUTE);
            if (secret == null) {
                throw new AuthorizationException("Missing CSRF prevention secret in session");
            }

            String suppliedToken = request.getParameter(TOKEN_REQUEST_PARAMETER);
            if (suppliedToken == null) {
                throw new AuthorizationException("Missing CSRF prevention token in request");
            }

            URL requestURL = URL.create(request);
            String computed = generateToken(
                    requestURL.removeParameter(TOKEN_REQUEST_PARAMETER), session);

            logger.warn("Check token: url: " + requestURL + ", supplied token: " + suppliedToken
                    + ", computed token: " + computed + ", secret: " + secret);
            if (logger.isDebugEnabled()) {
                logger.debug("Check token: url: " + requestURL + ", supplied token: " + suppliedToken
                        + ", computed token: " + computed + ", secret: " + secret);
            }
            if (!computed.equals(suppliedToken)) {
                throw new AuthorizationException("CSRF prevention token mismatch");
            }
            verified = true;
        }
    }
    
    
    private class Form {
        private HtmlPushTokenizer.ElementNode orig;
        private String method = null;
        private URL action = null;
        private String enctype = null;
        private List<Attribute> attributes;
        private String csrfPreventionToken = null;
        
        public Form(HtmlPushTokenizer.ElementNode orig, HttpSession session) {
            this.orig = orig;
            attributes = new ArrayList<>();
            for (Attribute attr: orig.attributes()) {
                if ("method".equalsIgnoreCase(attr.name())) {
                    method = attr.value();
                }
                else if ("action".equalsIgnoreCase(attr.name())) {
                    String action = attr.value();
                    if (action != null) {
                        try {
                            this.action = parseActionURL(action);
                        } catch (Exception e) {}
                    }
                }
                else if ("enctype".equalsIgnoreCase(attr.name()))
                    enctype = attr.value();
                else attributes.add(attr);
            }
            if (action != null) {
                csrfPreventionToken = generateToken(action, session);
                if (enctype != null && "multipart/form-data".equalsIgnoreCase(enctype.trim()))
                    action = action.addParameter(TOKEN_REQUEST_PARAMETER, csrfPreventionToken);
            }
        }
        
        public String toHtml() {
            if (action == null || method == null) {
                StringBuilder result = new StringBuilder();
                result.append(orig.toHtml());
                return result.toString();
            }

            StringBuilder result = new StringBuilder();
            result.append("<form action=\"" ).append(action.toString()).append("\" ");
            result.append("method=\"").append(method).append("\" ");
            if (enctype != null) {
                result.append("enctype=\"").append(enctype).append("\" ");
            }
            for (Attribute attr: attributes) {
                result.append(attr.name());
                if (attr.value() != null)
                    result.append("=").append(attr.value());
                result.append(" ");
            }
            result.append(">");
            result.append("\n<input type=\"hidden\" name=\"").append(TOKEN_REQUEST_PARAMETER);
            result.append("\" value=\"").append(csrfPreventionToken).append("\"/>\n");
            return result.toString();
        }
    }


    private class TokenGeneratingFilter extends HttpServletResponseWrapper {
        private ServletOutputStream out = null;
        private HttpSession session;
        
        public TokenGeneratingFilter(HttpServletResponse response, HttpSession session) {
            super(response);
            this.session = session;
        }

        @Override
        public void setHeader(String name, String value) {
            if ("Content-Length".equals(name.toLowerCase()))
                return;
            super.setHeader(name, value);
        }
        
        @Override
        public void flushBuffer() throws IOException {
            super.flushBuffer();
        }
        
        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (!getContentType().toLowerCase().startsWith("text/html"))
                return super.getOutputStream();
            
            final String characterEncoding = getCharacterEncoding();
            final ServletOutputStream dest = super.getOutputStream();
            final HtmlPushTokenizer parser = new HtmlPushTokenizer(new HtmlPushTokenizer.NodeHandler() {
                private Form form = null;
                
                @Override
                public boolean node(HtmlPushTokenizer.Node node) {
                    if (node instanceof ElementNode) {
                        ElementNode elem = (ElementNode) node;
                        if ("form".equals(elem.name())) {
                            form = new Form(elem, session);
                            try { 
                                dest.write(form.toHtml().getBytes(characterEncoding)); 
                                return true;
                            }
                            catch (IOException e) { 
                                throw new RuntimeException(e); 
                            }
                        }
                    }
                    try {
                        dest.write(node.toHtml().getBytes(characterEncoding));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return true;
                }
                
                @Override
                public void end() {
                    try { dest.flush(); dest.close(); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }

                @Override
                public void error(String msg) {
                    try { dest.flush(); dest.close(); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }
            });
            
            final CharsetPushDecoder decoder = new CharsetPushDecoder(
                Charset.forName(getCharacterEncoding()), 2048, new CharsetPushDecoder.Handler() {
                    @Override
                    public void chars(CharBuffer buffer) {
                        parser.push(buffer.toString());
                    }
                    @Override
                    public void done() {
                        parser.eof();
                    }
                    @Override
                    public void error(Throwable err) {
                        throw new RuntimeException(err);
                    }
                });
    
            
            out = new ServletOutputStream() {
                private ByteBuffer input = ByteBuffer.allocate(2048);
                
                @Override
                public void write(int b) throws IOException {
                    if (!input.hasRemaining()) {
                        input.flip();
                        decoder.push(input, false);
                    }
                    input.put((byte) b);
                }

                @Override
                public void flush() throws IOException {
                    if (input.position() > 0) {
                        input.flip();
                        decoder.push(input, false);
                    }
                }

                @Override
                public void close() throws IOException {
                    input.flip();
                    decoder.push(input, true);
                }
            };
            return out;
        }
        
        @Override
        public PrintWriter getWriter() throws IOException {
            out = getOutputStream();
            return new PrintWriter(out);
        }

        @Override
        public void setContentLength(int len) {
        }

        @Override
        public void setContentType(String type) {
            super.setContentType(type);
        }
    }
}
