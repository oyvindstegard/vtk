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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.repository.AuthorizationException;
import vtk.repository.Path;
import vtk.security.AuthenticationException;
import vtk.security.SecurityContext;
import vtk.text.html.AbstractHtmlPageFilter;
import vtk.text.html.HtmlAttribute;
import vtk.text.html.HtmlContent;
import vtk.text.html.HtmlElement;
import vtk.text.html.HtmlPage;
import vtk.text.html.HtmlText;
import vtk.text.html.HtmlUtil;
import vtk.util.io.IO;
import vtk.util.text.TextUtils;
import vtk.web.RequestContext;
import vtk.web.filter.HandlerFilter;
import vtk.web.filter.HandlerFilterChain;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * Cross Site Request Forgery (CSRF) prevention handler. This class performs two
 * tasks:
 * <ol>
 * <li>{@link #filter(HtmlContent) Generates tokens} in HTML forms on the page
 * being served</li>
 * <li>{@link #filter(HttpServletRequest, HandlerFilterChain) Verifies} that
 * valid tokens are present in POST requests</li>
 * </ol>
 */
public class CSRFPreventionHandler extends AbstractHtmlPageFilter implements HandlerFilter {

    private File tempDir = new File(System.getProperty("java.io.tmpdir"));
    private long maxUploadSize = 2*1024*1024*1024; // 2G max by default

    public static final String TOKEN_REQUEST_PARAMETER = "csrf-prevention-token";
    private static final String SECRET_SESSION_ATTRIBUTE = "csrf-prevention-secret";
    private static Logger logger = LoggerFactory.getLogger(CSRFPreventionHandler.class);
    private String ALGORITHM = "HmacSHA1";

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
    public boolean match(HtmlPage page) {
        return true;
    }

    @Override
    public void filter(HttpServletRequest request, HandlerFilterChain chain) throws Exception {
        if (!"POST".equals(request.getMethod())) {
            chain.filter(request);
            return;
        }
        String contentType = request.getContentType();
        if (contentType != null && contentType.startsWith("multipart/form-data")) {
            MultipartWrapper multipartRequest = new MultipartWrapper(request, this.tempDir, this.maxUploadSize);
            try {
                multipartRequest.writeTempFile();
                multipartRequest.parseRequest();
                verifyToken(multipartRequest);
                chain.filter(multipartRequest);
            } finally {
                multipartRequest.cleanup();
            }
        }
        else if (contentType != null && contentType.startsWith("application/x-www-form-urlencoded")) {
            verifyToken(request);
            chain.filter(request);
        }
        else {
            chain.filter(request);
        }
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
            HttpServletRequest request = RequestContext.getRequestContext().getServletRequest();
            url = URL.create(request);
        } else {
            try {
                url = parseActionURL(actionAttr.getValue());
            } catch (Throwable t) {
                logger.warn("Unable to find URL in action attribute: " + actionAttr.getValue(), t);
                return NodeResult.keep;
            }
        }
        url.setRef(null);

        RequestContext requestContext = RequestContext.getRequestContext();
        HttpSession session = requestContext.getServletRequest().getSession(false);

        if (session != null) {

            String csrfPreventionToken = generateToken(url, session);
            HtmlElement input = createElement("input", true, true);
            List<HtmlAttribute> attrs = new ArrayList<HtmlAttribute>();
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

    private String generateToken(URL url, HttpSession session) {
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

    private SecretKey generateSecret() {
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
        } else {
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

    private void verifyToken(HttpServletRequest request) throws Exception {
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
        String computed = generateToken(requestURL, session);

        if (logger.isDebugEnabled()) {
            logger.debug("Check token: url: " + requestURL + ", supplied token: " + suppliedToken
                    + ", computed token: " + computed + ", secret: " + secret);
        }
        if (!computed.equals(suppliedToken)) {
            throw new AuthorizationException("CSRF prevention token mismatch");
        }
    }

    private static class MultipartWrapper extends HttpServletRequestWrapper {
        private HttpServletRequest request;
        private IO.TempFile tempFile;
        private long maxMultipartSize;
        private File tempDir;
        private Map<String, List<String>> params = new HashMap<String, List<String>>();

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
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public Map getParameterMap() {
            Map<String, List<String>> combined = new HashMap<String, List<String>>();
            Map<String, String[]> m = super.getParameterMap();
            for (String s : m.keySet()) {
                String[] values = m.get(s);
                List<String> l = new ArrayList<String>();
                for (String v : values) {
                    l.add(v);
                }
                combined.put(s, l);
            }

            for (String s : this.params.keySet()) {
                List<String> l = combined.get(s);
                if (l == null) {
                    l = new ArrayList<String>();
                }
                for (String v : this.params.get(s)) {
                    l.add(v);
                }
                combined.put(s, l);
            }
            Map<String, String[]> result = new HashMap<String, String[]>();
            for (String name : combined.keySet()) {
                List<String> values = combined.get(name);
                result.put(name, values.toArray(new String[values.size()]));
            }
            return result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Enumeration<String> getParameterNames() {
            Set<String> result = new HashSet<String>();
            Enumeration<String> names = super.getParameterNames();
            while (names.hasMoreElements()) {
                result.add(names.nextElement());
            }
            result.addAll(this.params.keySet());
            return Collections.enumeration(result);
        }

        @Override
        public String[] getParameterValues(String name) {
            List<String> result = new ArrayList<String>();
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
                values = new ArrayList<String>();
                this.params.put(name, values);
            }
            values.add(value);
        }

        private void writeTempFile() throws IOException, FileUploadException {
            this.tempFile = IO.tempFile(request.getInputStream(), this.tempDir)
                                .limit(this.maxMultipartSize)
                                 // Potentially long running upload, make sure security token does not expire
                                .progress(p -> SecurityContext.getSecurityContext().resetTokenExpiry())
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
            List<String> multipartUploadFileItemNames = new ArrayList<String>();
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
                } else {
                    multipartUploadFileItemNames.add(item.getName());
                }
            }
            
            // Provide cached info about file item names in upload
            if (!multipartUploadFileItemNames.isEmpty()) {
                setAttribute("vtk.MultipartUploadWrapper.FileItemNames", multipartUploadFileItemNames);
            }
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
