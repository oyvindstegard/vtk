/* Copyright (c) 2004, 2007, 2008, University of Oslo, Norway
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import vtk.util.io.BoundedOutputStream;
import vtk.util.io.IO;
import vtk.util.web.HttpUtil;

/**
 * A HttpServletResponse implementation that buffers the content and
 * headers. An optional limit can be set on the buffer size.
 */
public class BufferedResponse implements HttpServletResponse {

    private static final String DEFAULT_CHAR_ENCODING = "utf-8";
    private static final String CONTENT_LENGTH = "Content-Length";
    private static final String CONTENT_TYPE = "Content-Type";

    private static final Pattern CONTENT_TYPE_HEADER_PATTERN
                                    = Pattern.compile(".+/.+;.*charset.*=.+");

    private long maxBufferSize = -1;
    private int status = 200;
    private String statusMessage;
    private ByteArrayOutputStream bufferStream = new ByteArrayOutputStream();
    private Map<String, List<Object>> headers = new HashMap<>(); 
    private List<Cookie> cookies = new ArrayList<>();
    private int bufferSize = 1000;
    private String contentType = null;
    private long contentLength = -1L;
    private Locale locale = Locale.getDefault();
    private String characterEncoding = null;
    private boolean committed = false;
    
    /**
     * Creates a buffered response with no limit on the buffer size.
     */
    public BufferedResponse(int status) {
        this.status = status;
    }
    
    /**
     * Creates a buffered response with a limited buffer size.
     *
     * @param maxBufferSize the maximum number of bytes that can be
     * buffered in this response wrapper. A negative number means no
     * limit.
     */
    public BufferedResponse(int status, long maxBufferSize) {
        this.status = status;
        this.maxBufferSize = maxBufferSize;
    }

    @Override
    public String getContentType() {
        if (this.characterEncoding != null) {
            return this.contentType + ";charset=" + this.characterEncoding;
        }
        return this.contentType;
    }
    
    public byte[] getContentBuffer() {
        return this.bufferStream.toByteArray();
    }

    public String getContentString() throws Exception {
        if (this.characterEncoding != null) {
            return new String(this.bufferStream.toByteArray(), this.characterEncoding);
        }

        return new String(this.bufferStream.toByteArray(), DEFAULT_CHAR_ENCODING);
    }

    @Override
    public int getStatus() {
        return this.status;
    }
    
    public int getContentLength() {
        if (this.contentLength >= 0) {
            return (int) this.contentLength;
        }
        return this.bufferStream.size();
    }
    
    @Override
    public String getCharacterEncoding() {
        if (this.characterEncoding == null) {
            return DEFAULT_CHAR_ENCODING;
        }
        return this.characterEncoding;
    }

    @Override
    public PrintWriter getWriter() {
        return new WrappedServletOutputStreamWriter(new WrappedServletOutputStream(
                                            this.bufferStream, this.getCharacterEncoding()));
    }

    @Override
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    @Override
    public void resetBuffer() {
        this.bufferStream.reset();
    }
    
    @Override
    public boolean isCommitted() {
        return this.committed;
    }

    @Override
    public void reset() {
        if (this.committed) {
            throw new IllegalStateException(
                "Cannot call reset(): Response has already been committed");
        }

        this.characterEncoding = null;
        this.contentLength = -1L;
        this.bufferStream.reset();
    }

    @Override
    public void flushBuffer() {
        this.committed = true;
    }

    @Override
    public ServletOutputStream getOutputStream() {
        OutputStream outputStream = this.bufferStream;
        if (this.maxBufferSize > 0) {
            outputStream = new BoundedOutputStream(outputStream, this.maxBufferSize);
        }
        ServletOutputStream servletStream = new WrappedServletOutputStream(outputStream,
                                                this.characterEncoding);
        return servletStream;
    }

    @Override
    public void setContentType(String contentType) {
        if (contentType == null) {
            return;
        }
        processContentTypeHeader(contentType);
    }
    
    @Override
    public void setContentLengthLong(long contentLength) {
        if (contentLength < 0L) {
            return;
        }
        this.contentLength = contentLength;
    }

    @Override
    public void setContentLength(int contentLength) {
        setContentLengthLong(contentLength);
    }

    @Override
    public Locale getLocale() {
        return this.locale;
    }

    @Override
    public void setLocale(Locale locale) {
        if (locale == null) {
            return;
        }
        this.locale = locale;
    }

    @Override
    public int getBufferSize() {
        return this.bufferSize;
    }

    @Override
    public void addCookie(Cookie cookie) {
        if (cookie == null) {
            return;
        }
        this.cookies.add(cookie);
    }
    
    public List<Cookie> getCookies() {
        return Collections.unmodifiableList(this.cookies);
    }

    @Override
    public boolean containsHeader(String header) {
        return this.headers.containsKey(header);
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
    public void sendError(int status, String statusMessage) {
        this.status = status;
        this.committed = true;
    }

    @Override
    public void sendError(int status) {
        this.status = status;
        this.committed = true;
    }

    @Override
    public void sendRedirect(String url) {
        this.status = HttpServletResponse.SC_MOVED_TEMPORARILY;
        setHeaderInternal("Location", url);
        this.committed = true;
    }

    @Override
    public void setDateHeader(String header, long date) {
        if (header == null || date <= 0) {
            return;
        }
        setHeaderInternal(header, new Date(date));
    }

    @Override
    public void addDateHeader(String header, long date) {
        if (header == null || date <= 0) {
            return;
        }
        addHeaderInternal(header, new Date(date));
    }

    @Override
    public void setHeader(String header, String value) {
        if (header == null || value == null) {
            return;
        }
        header = header.trim();
        value = value.trim();
        setHeaderInternal(header, value);
        applyHeaderSideEffects(header, value);
    }

    @Override
    public void addHeader(String header, String value) {
        if (header == null || value == null) {
            return;
        }
        header = header.trim();
        addHeaderInternal(header, value);
        applyHeaderSideEffects(header, value);
    }

    @Override
    public void setIntHeader(String header, int value) {
        if (header == null) {
            return;
        }
        header = header.trim();
        setHeaderInternal(header, new Integer(value));
        applyHeaderSideEffects(header, String.valueOf(value));
    }

    @Override
    public void addIntHeader(String header, int value) {
        if (header == null) {
            return;
        }
        header = header.trim();
        addHeaderInternal(header, new Integer(value));
        applyHeaderSideEffects(header, String.valueOf(value));
    }
    
    @Override
    public void setCharacterEncoding(String characterEncoding) {
        this.characterEncoding = characterEncoding;
    }
    
    @Override
    public void setStatus(int status) {
        this.status = status;
        this.statusMessage = HttpUtil.getStatusMessage(status);
    }

    @Override
    public void setStatus(int status, String statusMessage) {
        this.status = status;
        this.statusMessage = statusMessage;
    }

    /**
     * Gets the set of header names
     * @return the set of header names
     */
    public Collection<String> getHeaderNames() {
        return Collections.unmodifiableCollection(this.headers.keySet());
    }
    
    /**
     * Gets the set of values for a given header name
     * @param header the header name
     * @return the set of values for the header, or <code>null</code> 
     * if no values exists
     */
    public Collection<Object> getHeaderValues(String header) {
        List<Object> values = this.headers.get(header);
        if (values == null) {
            return null;
        }
        if (values.size() == 0) {
            return null;
        }
        return Collections.unmodifiableCollection(values);
    }

    /**
     * Gets the value for a given header name. If the header has multiple entries, 
     * only one value is returned (order unspecified)
     * @param header the header name
     * @return the value of the header
     */
    public Object getHeaderValue(String header) {
        List<Object> values = this.headers.get(header);
        if (values == null) {
            return null;
        }
        if (values.size() == 0) {
            return null;
        }
        return values.get(0);
    }
    
    
    /**
     * Write metadata and contents of buffered response to another response.
     */
    @SuppressWarnings("deprecation")
    public void writeTo(HttpServletResponse response, boolean closeOutputStream) 
        throws IOException {
        // Write/copy metadata
        response.setContentLength(getContentLength());
        String contentType = getContentType();
        if (contentType != null) {
            response.setContentType(contentType);
        }
        if (this.statusMessage != null) {
            response.setStatus(this.status, this.statusMessage);
        } else {
            response.setStatus(this.status);
        }
        response.setLocale(this.locale);

        for (String header: getHeaderNames()) {
            Collection<Object> values = getHeaderValues(header);
            boolean add = values.size() == 1;
            for (Object value: values) {
                writeHeaderTo(response, header, value, add);
            }
        }
        for (Cookie cookie: getCookies()) {
            response.addCookie(cookie);
        }

        // Write/copy content
        IO.write(getContentBuffer(), response.getOutputStream())
                .closeOut(closeOutputStream).perform();
    }

    private void writeHeaderTo(HttpServletResponse response, String header, Object value, boolean add) {
        if (value instanceof String) {
            if (add) {
                response.addHeader(header, (String) value);
            } else {
                response.setHeader(header, (String) value);
            }
        } else if (value instanceof Integer) {
            if (add) {
                response.addIntHeader(header, ((Integer)value).intValue());
            } else {
                response.setIntHeader(header, ((Integer)value).intValue());
            }
        } else if (value instanceof Date) {
            if (add) {
                response.addDateHeader(header, ((Date)value).getTime());
            } else {
                response.setDateHeader(header, ((Date)value).getTime());
            }
        } else {
            if (add) {
                response.addHeader(header, value.toString());
            } else {
                response.setHeader(header, value.toString());
                
            }
        }
    }

    private void addHeaderInternal(String header, Object value) {
        List<Object> list = this.headers.get(header);
        if (list == null) {
            list = new ArrayList<>();
            this.headers.put(header, list);
        }
        list.add(value);
    }

    private void setHeaderInternal(String header, Object value) {
        List<Object> list = new ArrayList<>();
        list.add(value);
        this.headers.put(header, list);
        
    }

    
    private void applyHeaderSideEffects(String header, String value) {
        if (CONTENT_TYPE.equalsIgnoreCase(header)) {
            processContentTypeHeader(value);
        } else if (CONTENT_LENGTH.equalsIgnoreCase(header)) {
            try {
                long longValue = Long.parseLong(value);
                this.contentLength = longValue;
            } catch (Exception e) {
                
            }
        }
    }
    
    private void processContentTypeHeader(String value) {
        if (CONTENT_TYPE_HEADER_PATTERN.matcher(value).matches()) {

            String contentType = value.substring(
                0, value.indexOf(";")).trim();
            String characterEncoding = value.substring(
                value.indexOf("=") + 1).trim();

            if (characterEncoding.startsWith("\"")
                && characterEncoding.endsWith("\"")) {

                characterEncoding = characterEncoding.substring(
                    1, characterEncoding.length() - 1).trim();
            }
            this.contentType = contentType;
            this.characterEncoding = characterEncoding;
            setHeaderInternal(CONTENT_TYPE, contentType);
        } else {
            this.contentType = value;
            setHeaderInternal(CONTENT_TYPE, value);
        }
    }

    @Override
    public String getHeader(String name) {
        List<Object> values = headers.get(name);
        if (values != null && !values.isEmpty()) {
            return formatHeader(values.get(0));
        }
        return null;
    }

    @Override
    public Collection<String> getHeaders(String name) {
        List<String> result = new ArrayList<>();
        List<Object> values = headers.get(name);
        if (values != null && !values.isEmpty()) {
            for (Object val: values)
                result.add(formatHeader(val));
        }
        return result;
    }

    private String formatHeader(Object value) {
        if (value instanceof Date) {
            return HttpUtil.getHttpDateString((Date) value);
        }
        return value.toString();
    }

}
