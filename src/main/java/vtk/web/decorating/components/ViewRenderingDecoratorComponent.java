/* Copyright (c) 2007, 2008, University of Oslo, Norway
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
package vtk.web.decorating.components;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.View;

import vtk.util.web.HttpUtil;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.servlet.BufferedResponse;
import vtk.web.servlet.WrappedServletOutputStream;

public class ViewRenderingDecoratorComponent extends AbstractDecoratorComponent {

    private View view;
    private String characterEncoding = "utf-8";
    private boolean exposeMvcModel = false;
    private Set<String> exposedParameters = new HashSet<String>();

    @Required
    public void setView(View view) {
        this.view = view;
    }

    public void setExposedParameters(Set<String> exposedParameters) {
        this.exposedParameters = exposedParameters;
    }

    public void setExposeMvcModel(boolean exposeMvcModel) {
        this.exposeMvcModel = exposeMvcModel;
    }
    
    public void setCharacterEncoding(String characterEncoding) {
        Charset.forName(characterEncoding);
        this.characterEncoding = characterEncoding;
    }

    @Override
    public final void render(DecoratorRequest request, DecoratorResponse response) throws Exception {
        Map<String, Object> model = new HashMap<>();
        if (this.exposeMvcModel) {
            model.putAll(request.getMvcModel());
        }
        processModel(model, request, response);
        renderView(model, request, response);
    }

    /**
     * Process the model prior to view rendering. The default implementation
     * performs the following steps:
     * <ol>
     * <li>Gather all reference data providers (using
     * <code>getReferenceDataProviders()</code>) and invoke them in order</li>
     * <li>If <code>exposeComponentParameters</code> is set, add an entry in the
     * model under the name determined by
     * <code>exposedParametersModelName</code>, containing either the full set
     * of component parameters or a specified set, depending on whether the
     * config parameter <code>exposedParameters</code> is specified.
     * <li>
     * </ol>
     * @param model the MVC model
     * @param request the decorator request
     * @param response the decorator response
     * @exception Exception if an error occurs
     */
    protected void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {

        if (this.exposedParameters != null) {
            for (Iterator<String> i = request.getRequestParameterNames(); i.hasNext();) {
                String name = i.next();
                if (!this.exposedParameters.isEmpty() && !this.exposedParameters.contains(name)) {
                    continue;
                }
                Object value = request.getRawParameter(name);
                model.put(name, value);
            }
        }
    }

    
    private void renderView(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response) throws Exception {
        render2(model, request, response);
    }
    
    @SuppressWarnings("unused")
    private void render1(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response) throws Exception {
        HttpServletRequest servletRequest = request.getServletRequest();
        BufferedResponse bufferedResponse = new BufferedResponse();
        OutputStream out = response.getOutputStream();
        response.setCharacterEncoding(characterEncoding);
        view.render(model, servletRequest, bufferedResponse);
        out.write(bufferedResponse.getContentBuffer());
        out.flush();
        out.close();
    }
    
    private void render2(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response) throws Exception {
        HttpServletRequest servletRequest = request.getServletRequest();
        OutputStream out = response.getOutputStream();
        HttpServletResponse servletResponse = new ViewResponse(
                new WrappedServletOutputStream(out, characterEncoding));
        
        servletResponse.setCharacterEncoding(characterEncoding);
        response.setCharacterEncoding(characterEncoding);
        
        view.render(model, servletRequest, servletResponse);
        servletResponse.flushBuffer();
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("(");
        sb.append(this.view).append(")");
        return sb.toString();
    }

    @Override
    protected String getDescriptionInternal() {
        return null;
    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        return null;
    }
    
    private static class ViewResponse implements HttpServletResponse {
        private ServletOutputStream out;
        private int status = 200;
        private Map<String, List<String>> headers = new HashMap<>();
        private String characterEncoding = "utf-8";
        private String contentType = "text/html";
        private long contentLength = -1;
        private int bufferSize = 2048;
        private Locale locale = Locale.getDefault();
        boolean committed = false;
        
        public ViewResponse(ServletOutputStream out) {
            this.out = out;
        }

        @Override
        public String getCharacterEncoding() {
            return characterEncoding;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (committed) throw new IllegalStateException("Response already committed");
            committed = true;
            return out;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (committed) throw new IllegalStateException("Response already committed");
            committed = true;
            return new PrintWriter(out);
        }

        @Override
        public void setCharacterEncoding(String charset) {
            setHeader("Content-Type", contentType + ";charset=" + charset);
        }

        @Override
        public void setContentLength(int len) {
            setHeader("Content-Length", String.valueOf(contentLength));
        }

        @Override
        public void setContentLengthLong(long len) {
            setHeader("Content-Length", String.valueOf(contentLength));
        }

        @Override
        public void setContentType(String type) {
            setHeader("Content-Type", type);
        }

        @Override
        public void setBufferSize(int size) {
            this.bufferSize = size;
        }

        @Override
        public int getBufferSize() {
            return bufferSize;
        }

        @Override
        public void flushBuffer() throws IOException {
            out.flush();
            out.close();
            committed = true;
        }

        @Override
        public void resetBuffer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCommitted() {
            return committed;
        }

        @Override
        public void reset() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLocale(Locale loc) {
            this.locale = loc;
        }

        @Override
        public Locale getLocale() {
            return locale;
        }

        @Override
        public void addCookie(Cookie cookie) {
            // Noop
        }

        @Override
        public boolean containsHeader(String name) {
            return headers.containsKey(name);
        }

        @Override
        public String encodeURL(String url) {
            return url;
        }

        @Override
        public String encodeRedirectURL(String url) {
            return url;
        }

        @Override
        public String encodeUrl(String url) {
            return url;
        }

        @Override
        public String encodeRedirectUrl(String url) {
            return url;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            if (committed) throw new IllegalStateException("Response already committed");
            committed = true;
            status = sc;
            out.write(msg.getBytes(characterEncoding));
            out.flush();
            out.close();
        }

        @Override
        public void sendError(int sc) throws IOException {
            if (committed) throw new IllegalStateException("Response already committed");
            committed = true;
            status = sc;
            out.write(("Error: " + HttpUtil.getStatusMessage(sc)).getBytes(characterEncoding));
            out.flush();
            out.close();
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            // Noop
        }

        @Override
        public void setDateHeader(String name, long date) {
            setHeader(name, HttpUtil.getHttpDateString(new Date(date)));
        }

        @Override
        public void addDateHeader(String name, long date) {
            addHeader(name, HttpUtil.getHttpDateString(new Date(date)));
        }

        @Override
        public void setHeader(String name, String value) {
            processHeader(name, value);
            headers.put(name, Collections.singletonList(value));
        }
        
        @SuppressWarnings("serial")
        private static Set<String> SINGLE_HEADERS = new HashSet<String>() {{
            add("Content-Type");
        }};
        
        @Override
        public void addHeader(String name, String value) {
            if (SINGLE_HEADERS.equals(name)) {
                setHeader(name, value);
                return;
            }
            processHeader(name, value);
            List<String> values = headers.get(name);
            if (values == null) {
                setHeader(name, value);
            }
            List<String> newValues = new ArrayList<>(values);
            newValues.add(value);
            headers.put(name, newValues);
        }

        @Override
        public void setIntHeader(String name, int value) {
            setHeader(name, String.valueOf(value));
        }

        @Override
        public void addIntHeader(String name, int value) {
            addHeader(name, String.valueOf(value));
        }

        @Override
        public void setStatus(int sc) {
            status = sc;
        }

        @Override
        public void setStatus(int sc, String sm) {
            status = sc;
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public String getHeader(String name) {
            List<String> values = headers.get(name);
            if (values == null || values.isEmpty()) return null;
            return values.get(0);
        }

        @Override
        public Collection<String> getHeaders(String name) {
            List<String> values = headers.get(name);
            if (values == null || values.isEmpty()) return null;
            return Collections.unmodifiableList(values);
        }

        @Override
        public Collection<String> getHeaderNames() {
            return Collections.unmodifiableSet(headers.keySet());
        }
        
        private Pattern CT_PATTERN = 
                Pattern.compile("([^;]+)(;[ ]*charset=([a-zA-Z0-9\\-]+))?");
        
        private void processHeader(String name, String value) {
            if ("Content-Type".equals(name)) {
                Matcher m = CT_PATTERN.matcher(value);
                if (!m.find()) 
                    throw new IllegalStateException("Invalid content type: " + value);
                contentType = m.group(1);
                if (m.groupCount() == 3)
                     characterEncoding = m.group(3);
            }
        }

    }
    
}
