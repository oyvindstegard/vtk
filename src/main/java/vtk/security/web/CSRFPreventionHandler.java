/* Copyright (c) 2009, University of Oslo, Norway
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.HtmlUtils;

import vtk.repository.Path;
import vtk.security.AuthenticationException;
import vtk.security.SecurityContext;
import vtk.text.html.AbstractHtmlPageFilter;
import vtk.text.html.HtmlAttribute;
import vtk.text.html.HtmlContent;
import vtk.text.html.HtmlElement;
import vtk.text.html.HtmlPage;
import vtk.text.html.HtmlPageFilter;
import vtk.text.html.HtmlPageParser;
import vtk.text.html.HtmlText;
import vtk.util.io.IO;
import vtk.util.text.TextUtils;
import vtk.web.RequestContext;
import vtk.web.decorating.HtmlPageFilterFactory;
import vtk.web.service.Service;
import vtk.web.service.URL;
import vtk.web.servlet.AbstractServletFilter;
import vtk.web.servlet.WrappedServletOutputStream;

/**
 * Cross Site Request Forgery (CSRF) prevention handler. This class performs two
 * tasks:
 * <ol>
 * <li>{@link FormRewritingFilter Generates tokens} in HTML forms on the page
 * being served</li>
 * <li>{@link #doFilter(HttpServletRequest, HttpServletResponse) Verifies} that
 * valid tokens are present in POST requests. In addition, a {@code csrfToken} 
 * cookie is set on each response, and is required to be present as the header 
 * {@code X-CSRF-TOKEN} in requests that are not form POSTS. 
 * 
 * The token in the cookie is more canonical than the token embedded HTML forms 
 * (corresponds with the user's session rather than the specific action URL 
 * in each form)</li>
 * </ol>
 */
public class CSRFPreventionHandler extends AbstractServletFilter implements HtmlPageFilterFactory {

    private File tempDir = new File(System.getProperty("java.io.tmpdir"));
    private long maxUploadSize = 2*1024*1024*1024; // 2G max by default

    public static final String TOKEN_REQUEST_PARAMETER = "csrf-prevention-token";
    private static final String TOKEN_COOKIE = "csrfToken";
    private static final String TOKEN_HEADER = "X-CSRF-Token";
    private static final String SECRET_SESSION_ATTRIBUTE = "csrf-prevention-secret";
    private static Logger logger = LoggerFactory.getLogger(CSRFPreventionHandler.class);
    private static final String ALGORITHM = "HmacSHA256";

    /**
     * Utility method that can be called, e.g. from views
     * 
     * @return a new CSRF prevention token
     */
    public String newToken(URL url, HttpServletRequest request) throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new IllegalStateException("Session does not exist");
        }
        url = new URL(url);
        url.setRef(null);
        return generateToken(url, session);
    }

    @Override
    public HtmlPageFilter pageFilter(HttpServletRequest request) {
        request.setAttribute("csrf-html-filtered", true);
        return new FormRewritingFilter(request);
    }
    

    @Override
    protected void doFilter(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if ("GET".equals(request.getMethod()) || "POST".equals(request.getMethod())) {
            HtmlParsingResponseWrapper responseWrapper = new HtmlParsingResponseWrapper(response, 
                    () -> request.getAttribute("csrf-html-filtered") == null,
                    () -> pageFilter(request));

            doFilterInternal(request, responseWrapper, chain);
            responseWrapper.commit();
        }
        else {
            doFilterInternal(request, response, chain);
        }
    }
    
    private void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        switch (request.getMethod()) {
        case "GET": 
            setTokenCookie(request, response);
            chain.doFilter(request, response);
            return;
        case "PUT":
        case "PATCH":
        case "DELETE":
            verifyApiRequest(request, response, chain);
            return;
        case "POST":
            String contentType = request.getContentType();
            if (contentType == null) {
                send403(response, "Content-Type is required for POST requests");
                return;
            }
            if (contentType.startsWith("multipart/form-data")) {
                verifyMultipartPost(request, response, chain);
                return;
            }
            else if (contentType.startsWith("application/x-www-form-urlencoded")) {
                verifyFormPost(request, response, chain);
                return;
            }
            else if (contentType.startsWith("application/json")) {
                verifyApiRequest(request, response, chain);
                return;
            }
            else {
                send403(response, "Invalid Content-Type: " + contentType + " for POST requests");
                return;
            }
        }
        chain.doFilter(request, response);
    }
    
    private void verifyMultipartPost(HttpServletRequest request, HttpServletResponse response, 
            FilterChain chain) throws IOException, ServletException {
        MultipartWrapper multipartRequest = new MultipartWrapper(request, this.tempDir, this.maxUploadSize);
        try {
            multipartRequest.writeTempFile();
            multipartRequest.parseRequest();
            String token = multipartRequest.getParameter(TOKEN_REQUEST_PARAMETER);
            URL url = URL.create(request);
            verifyToken(multipartRequest, response, chain, token, url);
        }
        catch (FileUploadException e) {
            throw new IOException(e);
        }
        finally {
            multipartRequest.cleanup();
        }
    }

    private void verifyFormPost(HttpServletRequest request, HttpServletResponse response, 
            FilterChain chain) throws IOException, ServletException {
        String token = request.getParameter(TOKEN_REQUEST_PARAMETER);
        URL url = URL.create(request);
        verifyToken(request, response, chain, token, url);
    }

    private void verifyApiRequest(HttpServletRequest request, HttpServletResponse response, 
            FilterChain chain) throws IOException, ServletException  {
        String token = request.getHeader(TOKEN_HEADER);
        URL url = URL.create(request)
                .clearParameters()
                .setPath(Path.ROOT);
        verifyToken(request, response, chain, token, url);
    }
    
    
    private void setTokenCookie(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        URL url = URL.create(request)
                .clearParameters().setPath(Path.ROOT);
        String token = generateToken(url, session);
        Cookie cookie = new Cookie(TOKEN_COOKIE, token);
        cookie.setSecure(request.isSecure());
        cookie.setMaxAge(-1);
        cookie.setPath("/");
        response.addCookie(cookie);
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
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to generate token", e);
        }
    }

    private static SecretKey generateSecret() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(ALGORITHM);
            return kg.generateKey();
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to generate secret key", e);
        }
    }
    
    private void verifyToken(HttpServletRequest request, HttpServletResponse response, 
            FilterChain filterChain, String token, URL requestURL) throws IOException, ServletException {

        RequestContext requestContext = RequestContext.getRequestContext(request);
        if (requestContext.getPrincipal() == null) {
            throw new AuthenticationException("Illegal anonymous action");
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new IllegalStateException("A session must be present");
        }
        Service service = requestContext.getService();
        if (Boolean.TRUE.equals(service.getAttribute("disable-csrf-checking"))) {
            filterChain.doFilter(request, response);
            return;
        }
        SecretKey secret = (SecretKey) session.getAttribute(SECRET_SESSION_ATTRIBUTE);
        if (secret == null) {
            send403(response, "Missing CSRF prevention secret in session");
            return;
        }
        if (token == null) {
            send403(response, "Missing CSRF prevention token in request: " 
                    + request.getMethod() + " " + requestURL);
            return;
        }
        String computed = generateToken(requestURL, session);
        if (logger.isDebugEnabled()) {
            logger.debug("Check token: url: " + requestURL + ", supplied token: " + token
                    + ", computed token: " + computed + ", secret: " + secret);
        }
        if (!computed.equals(token)) {
            send403(response, "CSRF prevention token mismatch");
            return;
        }
        filterChain.doFilter(request, response);
    }
    
    private void send403(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/plain;charset=utf-8");
        PrintWriter writer = response.getWriter();
        writer.write(message);
        writer.close();
    }
    
    private static class MultipartWrapper extends HttpServletRequestWrapper {
        private HttpServletRequest request;
        private IO.TempFile tempFile;
        private long maxMultipartSize;
        private File tempDir;
        private Map<String, List<String>> params = new HashMap<>();

        public MultipartWrapper(HttpServletRequest request, File tempDir, long maxMultipartSize) {
            super(request);
            this.request = request;
            this.maxMultipartSize = maxMultipartSize;
            this.tempDir = tempDir;
        }

        public void cleanup() {
            if (logger.isDebugEnabled()) {
                logger.debug("Cleanup temp file: " + this.tempFile + ", exists: " + this.tempFile.file().exists());
            }
            if (this.tempFile != null) {
                this.tempFile.delete();
            }
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            FileInputStream fileStream = this.tempFile.inputStream();
            return new vtk.util.io.ServletInputStream(fileStream);
        }

        @Override
        public String getParameter(String name) {
            if (this.params.containsKey(name)) {
                List<String> values = this.params.get(name);
                return values.get(0);
            }
            return super.getParameter(name);
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, List<String>> combined = new HashMap<>();
            Map<String, String[]> m = super.getParameterMap();
            for (String s : m.keySet()) {
                String[] values = m.get(s);
                List<String> l = new ArrayList<>();
                for (String v : values) {
                    l.add(v);
                }
                combined.put(s, l);
            }

            for (String s : this.params.keySet()) {
                List<String> l = combined.get(s);
                if (l == null) {
                    l = new ArrayList<>();
                }
                for (String v : this.params.get(s)) {
                    l.add(v);
                }
                combined.put(s, l);
            }
            Map<String, String[]> result = new HashMap<>();
            for (String name : combined.keySet()) {
                List<String> values = combined.get(name);
                result.put(name, values.toArray(new String[values.size()]));
            }
            return result;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            Set<String> result = new HashSet<>();
            Enumeration<String> names = super.getParameterNames();
            while (names.hasMoreElements()) {
                result.add(names.nextElement());
            }
            result.addAll(this.params.keySet());
            return Collections.enumeration(result);
        }

        @Override
        public String[] getParameterValues(String name) {
            List<String> result = new ArrayList<>();
            String[] names = super.getParameterValues(name);
            if (names != null) {
                for (String s : names) {
                    result.add(s);
                }
            }
            List<String> thisParams = this.params.get(name);
            if (thisParams != null) {
                result.addAll(thisParams);
            }
            return result.toArray(new String[result.size()]);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }

        private void addParameter(String name, String value) {
            List<String> values = this.params.get(name);
            if (values == null) {
                values = new ArrayList<>();
                this.params.put(name, values);
            }
            values.add(value);
        }

        private void writeTempFile() throws IOException, FileUploadException {
            this.tempFile = IO.tempFile(request.getInputStream(), this.tempDir)
                                .limit(this.maxMultipartSize)
                                 // Potentially long running upload, make sure security token does not expire
                                .progress(p -> SecurityContext.getSecurityContext(request).resetTokenExpiry())
                                .progressInterval(128*1024*1024)
                                .perform();
            if (this.tempFile.isTruncatedToSizeLimit()) {
                throw new FileUploadException("Upload limit exceeded");
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Create temp file: " + this.tempFile);
            }
        }

        private void parseRequest() throws FileUploadException, IOException {
            ServletFileUpload upload = new ServletFileUpload();
            FileItemIterator iter = upload.getItemIterator(this);
            List<String> multipartUploadFileItemNames = new ArrayList<>();
            while (iter.hasNext()) {
                FileItemStream item = iter.next();
                if (item.isFormField()) {
                    String name = item.getFieldName();
                    // XXX:
                    String encoding = this.request.getCharacterEncoding();
                    if (encoding == null) {
                        encoding = "utf-8";
                    }
                    String value = IO.readString(item.openStream(), encoding).limit(2000).perform();
                    addParameter(name, value);
                }
                else {
                    multipartUploadFileItemNames.add(item.getName());
                }
            }
            
            // Provide cached info about file item names in upload
            if (!multipartUploadFileItemNames.isEmpty()) {
                setAttribute("vtk.MultipartUploadWrapper.FileItemNames", multipartUploadFileItemNames);
            }
        }
    }

    private static class FormRewritingFilter extends AbstractHtmlPageFilter {
        private HttpServletRequest request;
        
        public FormRewritingFilter(HttpServletRequest request) {
            this.request = request;
        }
        
        @Override
        public boolean match(HtmlPage page) {
            return true;
        }

        @Override
        public NodeResult filter(HtmlContent node) {
            if (!(node instanceof HtmlElement)) {
                return NodeResult.keep;
            }
            HtmlElement element = ((HtmlElement) node);
            if (!"form".equals(element.getName().toLowerCase())) {
                return NodeResult.keep;
            }
            HtmlAttribute method = element.getAttribute("method");
            if (method == null || "".equals(method.getValue().trim()) || "get".equals(method.getValue().toLowerCase())) {
                return NodeResult.keep;
            }

            HtmlElement[] inputs = element.getChildElements("input");
            for (HtmlElement input : inputs) {
                HtmlAttribute name = input.getAttribute("name");
                if (name != null && TOKEN_REQUEST_PARAMETER.equals(name.getValue())) {
                    return NodeResult.keep;
                }
            }

            URL url;
            HtmlAttribute actionAttr = element.getAttribute("action");
            if (actionAttr == null || actionAttr.getValue() == null || "".equals(actionAttr.getValue().trim())) {
                url = URL.create(request);
            }
            else {
                try {
                    url = parseActionURL(actionAttr.getValue(), request);
                }
                catch (Throwable t) {
                    logger.warn("Unable to find URL in action attribute: " + actionAttr.getValue(), t);
                    return NodeResult.keep;
                }
            }
            url.setRef(null);

            // Don't generate and inject tokens for forms with action-URLs that have
            // hostname different from the hostname in the current request
            // (For now, we don't care about possible port differences.)
            URL requestUrl = RequestContext.getRequestContext(request).getRequestURL();
            if (!requestUrl.getHost().equals(url.getHost())) {
                return NodeResult.keep;
            }

            HttpSession session = request.getSession(false);

            if (session != null) {

                String csrfPreventionToken = generateToken(url, session);
                HtmlElement input = createElement("input", true, true);
                List<HtmlAttribute> attrs = new ArrayList<>();
                attrs.add(createAttribute("name", TOKEN_REQUEST_PARAMETER));
                attrs.add(createAttribute("type", "hidden"));
                attrs.add(createAttribute("value", csrfPreventionToken));
                input.setAttributes(attrs.toArray(new HtmlAttribute[attrs.size()]));
                element.addContent(0, input);
                element.addContent(0, new HtmlText() {
                    @Override
                    public String getContent() {
                        return "\r\n";
                    }
                });
                element.addContent(new HtmlText() {
                    @Override
                    public String getContent() {
                        return "\r\n";
                    }
                });
            }
            return NodeResult.keep;
        }
    }
    
    private static URL parseActionURL(String action, HttpServletRequest request) {
        URL url = URL.create(request);
        return url.relativeURL(HtmlUtils.htmlUnescape(action));
    }    
    
    private static class HtmlParsingResponseWrapper extends HttpServletResponseWrapper {
        private Supplier<Boolean> parse;
        private Supplier<HtmlPageFilter> filter;
        private boolean committed = false;
        private PrintWriter writer = null;
        private ByteArrayOutputStream bufferStream = null;
        
        public HtmlParsingResponseWrapper(HttpServletResponse response, 
                Supplier<Boolean> parse, Supplier<HtmlPageFilter> filter) {
            super(response);
            this.parse = parse;
            this.filter = filter;
        }
        
        private boolean parseable() {
            String ct = super.getContentType();
            if (ct == null) return false;
            return ct.startsWith("text/html");
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (!parse.get() || !parseable()) {
                return super.getOutputStream();
            }
            if (bufferStream != null) throw new IOException("Output stream already opened");
            bufferStream = new ByteArrayOutputStream();
            return new WrappedServletOutputStream(bufferStream, super.getCharacterEncoding());
        }
        
        @Override
        public PrintWriter getWriter() throws IOException {
            if (!parse.get()) return super.getWriter();
            if (!parseable()) return super.getWriter();
            writer = new PrintWriter(getOutputStream());
            return writer;
        }
        
        @Override
        public void sendError(int sc, String msg) throws IOException {
            committed = true;
            super.sendError(sc, msg);
        }

        @Override
        public void sendError(int sc) throws IOException {
            committed = true;
            super.sendError(sc);
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            committed = true;
            super.sendRedirect(location);
        }

        public void commit() throws IOException {
            if (committed) {
                return;
            }
            committed = true;
            if (writer == null && bufferStream == null) {
                return;
            }
            
            if (writer != null) {
                writer.flush();
                writer.close();
            }
            else if (bufferStream != null) {
                bufferStream.flush();
            }
            
            HtmlPageParser parser = new HtmlPageParser();
            HtmlPage page;
            
            try {
                page = parser.parse(new ByteArrayInputStream(bufferStream.toByteArray()), 
                        super.getCharacterEncoding());
            }
            catch (IOException e) {
                throw e;
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
            page.filter(filter.get());
            byte[] buffer = page.getStringRepresentation().getBytes(super.getCharacterEncoding());
            super.setContentLength(buffer.length);
            getResponse().getOutputStream().write(buffer);
        }
    }
    
    /**
     * Maximum size in bytes of POST multipart requests to process.
     * -1 means no limit.
     * 
     * @param maxUploadSize 
     */
    public void setMaxUploadSize(long maxUploadSize) {
        this.maxUploadSize = maxUploadSize;
    }

    public void setTempDir(String tempDirPath) {
        File tmp = new File(tempDirPath);
        if (!tmp.exists()) {
            throw new IllegalArgumentException("Unable to set tempDir: file " + tmp + " does not exist");
        }
        if (!tmp.isDirectory()) {
            throw new IllegalArgumentException("Unable to set tempDir: file " + tmp + " is not a directory");
        }
        this.tempDir = tmp;
    }

}
