/* Copyright (c) 2013 University of Oslo, Norway
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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import vtk.web.RequestContext;
import vtk.web.service.Service;

/**
 * HTTP protocol logger/dumper. Logs headers and body by providing a request and
 * response filter, and then logging the captured data after the request has 
 * completed.
 * 
 * There is one log message sent per request-response cycle, and log output will
 * consist of multiple lines of data. So configuring a separate log
 * appender/formatter for this class makes sense. It will generally use a logger
 * with the same name as the fully qualified class, but if a specific service is
 * configured, that service name will be appended to the logger id.
 */
public class CaptureOutputLoggingFilter extends AbstractServletFilter 
    implements InitializingBean {

    private int maxCaptureBytes = 4096;
    private Service service;
    private Logger logger;

    @Override
    public void afterPropertiesSet() throws Exception {
        String loggerId = CaptureOutputLoggingFilter.class.getName();
        if (this.service != null) {
            loggerId += "." + this.service.getName();
        }
        this.logger = LoggerFactory.getLogger(loggerId);
    }

    @Override
    protected void doFilter(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!logForService(RequestContext.getRequestContext(request).getService())) {
            chain.doFilter(request, response);
            return;
        }

        CaptureInputRequestWrapper requestWrapper = new CaptureInputRequestWrapper(request);
        CaptureOutputResponseWrapper responseWrapper = new CaptureOutputResponseWrapper(response);
        
        chain.doFilter(requestWrapper, responseWrapper);
        
        log(requestWrapper, responseWrapper);
    }

    private void log(CaptureInputRequestWrapper reqWrap, CaptureOutputResponseWrapper resWrap) {

        StringBuilder logbuf = new StringBuilder();

        // Request
        logbuf.append("\n--- REQUEST:\n");
        logbuf.append(reqWrap.getMethod()).append(" ").append(reqWrap.getRequestURI());
        logbuf.append(reqWrap.getQueryString() != null ? "?" + reqWrap.getQueryString() : "").append('\n');
        addHeadersForLogging(reqWrap, logbuf);
        logbuf.append('\n');
        byte[] body = reqWrap.getCapturedBytes();
        addBytesForLogging(body, reqWrap.getStreamBytesRead(), reqWrap.getHeader("Content-Type"), logbuf);


        // Response
        logbuf.append("\n--- RESPONSE:\n");
        logbuf.append(resWrap.getStatus()).append('\n');
        addHeadersForLogging(resWrap, logbuf);
        logbuf.append('\n');
        body = resWrap.getCapturedBytes();
        addBytesForLogging(body, resWrap.getStreamBytesWritten(),
                resWrap.getHeaderValue("Content-Type") != null ? resWrap.getHeaderValue("Content-Type").toString()
                        : null, logbuf);

        logbuf.append("\n--- END\n");

        logger.info(logbuf.toString());
    }

    private boolean logForService(Service requestService) {
        if (this.service == null) {
            return true;
        }
        while (requestService != null) {
            if (this.service == requestService) {
                return true;
            }
            requestService = requestService.getParent();
        }
        return false;
    }
    
    
    class CaptureInputRequestWrapper extends HttpServletRequestWrapper {

        private InputStreamCopyWrapper streamWrapper;

        public CaptureInputRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (this.streamWrapper == null) {
                this.streamWrapper = new InputStreamCopyWrapper(super.getInputStream());
            }
            return this.streamWrapper;
        }

        InputStreamCopyWrapper getInputStreamWrapper() {
            return this.streamWrapper;
        }
        
        byte[] getCapturedBytes() {
            if (this.streamWrapper != null) {
                return this.streamWrapper.getCopiedBytes();
            }
            return new byte[0];
        }
        
        int getStreamBytesRead() {
            if (this.streamWrapper != null) {
                return this.streamWrapper.getStreamBytesRead();
            }
            return 0;
        }

    }

    private class InputStreamCopyWrapper extends ServletInputStream {

        private final ByteArrayOutputStream streamCopyBuffer;
        private final ServletInputStream wrappedStream;
        private int streamBytesRead = 0;

        InputStreamCopyWrapper(ServletInputStream wrappedStream) {
            this.wrappedStream = wrappedStream;
            this.streamCopyBuffer = new ByteArrayOutputStream();
        }

        @Override
        public int read() throws IOException {
            int b = this.wrappedStream.read();
            if (b > -1 && streamBytesRead++ < maxCaptureBytes) {
                streamCopyBuffer.write(b);
            }
            return b;
        }

        byte[] getCopiedBytes() {
            return streamCopyBuffer.toByteArray();
        }
        
        int getStreamBytesRead() {
            return streamBytesRead;
        }

        @Override
        public boolean isFinished() {
            return wrappedStream.isFinished();
        }

        @Override
        public boolean isReady() {
            return wrappedStream.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            wrappedStream.setReadListener(readListener);
        }

    }
    
    private void addHeadersForLogging(CaptureInputRequestWrapper requestWrapper, StringBuilder logBuffer) {
        Enumeration<String> headerNames = requestWrapper.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!headerName.equalsIgnoreCase("Authorization") && !headerName.equalsIgnoreCase("SSL_SERVER_CERT")) {
                logBuffer.append(headerName).append(": ").append(requestWrapper.getHeader(headerName)).append('\n');
            } else {
                logBuffer.append(headerName).append(": ****\n");
            }
        }
    }

    private void addHeadersForLogging(CaptureOutputResponseWrapper responseWrapper, StringBuilder logBuffer) {
        for (String header: responseWrapper.getHeaderNames()) {
            List<Object> values = responseWrapper.getHeaderValues(header);
            for (Object value : values) {
                logBuffer.append(header).append(": ").append(value).append('\n');
            }
        }
    }

    private void addBytesForLogging(byte[] data, int totalBytes, String contentType, StringBuilder logBuffer) {
        int logBytes = Math.min(maxCaptureBytes, data.length);
        String rawString = getRawAsciiString(data, 0, logBytes, isProbablyBinary(contentType));
        logBuffer.append(rawString);
        if (totalBytes > logBytes) {
            logBuffer.append(" [").append(totalBytes - logBytes).append(" more bytes truncated ...]");
        }
    }

    private String getRawAsciiString(byte[] b, int offset, int length, boolean replaceUnprintable) {
        char[] rawChars = new char[b.length];
        int j = 0;
        for (int i = offset; i < length; i++) {
            char c = (char) b[i];
            if (replaceUnprintable && (c < 0x20 || c > 0x7F)) {
                c = '.';
            }
            rawChars[j++] = c;
        }
        return new String(rawChars, 0, j);
    }

    private boolean isProbablyBinary(String contentTypeHeaderValue) {
        if (contentTypeHeaderValue == null)
            return true;
        if (contentTypeHeaderValue.matches("^[^/]+/xml")) {
            return false;
        }
        return !contentTypeHeaderValue.startsWith("text/");
    }

    private class CaptureOutputResponseWrapper extends HeaderAwareResponseWrapper {

        private OutputStreamCopyWrapper streamWrapper;
        private PrintWriter printWriter;

        public CaptureOutputResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (printWriter == null) {
                if (streamWrapper != null) {
                    // Illegal state according to ServletResponse documentation
                    throw new IllegalStateException(
                            "getOutputStream has already been called for this response");
                }
                
                streamWrapper = new OutputStreamCopyWrapper(super.getOutputStream());
                printWriter = new PrintWriter(
                        new OutputStreamWriter(streamWrapper, getCharacterEncoding()));
            }
            
            return this.printWriter;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (printWriter != null) {
                // Illegal state according to ServletResponse documentation
                throw new IllegalStateException("getWriter has already been called for this response");
            }
            if (streamWrapper == null) {
                streamWrapper = new OutputStreamCopyWrapper(super.getOutputStream());
            }
            
            return streamWrapper;
        }

        byte[] getCapturedBytes() {
            if (this.streamWrapper != null) {
                return this.streamWrapper.getCopiedBytes();
            }
            return new byte[0];
        }
        
        int getStreamBytesWritten() {
            if (this.streamWrapper != null) {
                return this.streamWrapper.getStreamBytesWritten();
            }
            return 0;
        }
    }

    private class OutputStreamCopyWrapper extends ServletOutputStream {

        private ByteArrayOutputStream streamCopyBuffer;
        private ServletOutputStream wrappedStream;
        private int streamBytesWritten=0;

        OutputStreamCopyWrapper(ServletOutputStream wrappedStream) {
            this.wrappedStream = wrappedStream;
            this.streamCopyBuffer = new ByteArrayOutputStream();
        }

        @Override
        public void write(int b) throws IOException {
            if (b > -1 && (streamBytesWritten++ < maxCaptureBytes)) {
                streamCopyBuffer.write(b);
            }
            wrappedStream.write(b);
        }

        @Override
        public void close() throws IOException {
            wrappedStream.close();
        }

        byte[] getCopiedBytes() {
            return streamCopyBuffer.toByteArray();
        }
        
        int getStreamBytesWritten() {
            return streamBytesWritten;
        }

        @Override
        public boolean isReady() {
            return wrappedStream.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            wrappedStream.setWriteListener(writeListener);
        }

    }

    /**
     * Set maximum nunber of bytes to capture and store in memory 
     * from request or response body.
     * 
     * <p>Default value is <code>4096</code> bytes.
     * @param maxCaptureBytes the maxCapturedBytes to set
     */
    public void setMaxCaptureBytes(int maxCaptureBytes) {
        this.maxCaptureBytes = maxCaptureBytes;
    }


    /**
     * Set an explicit service filter for logging. Only requests for this
     * particular service (or any child service) will be logged.
     * 
     * @param service
     */
    public void setService(Service service) {
        this.service = service;
    }

}
