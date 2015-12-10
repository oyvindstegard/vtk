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
package vtk.web.filter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationListener;
import org.springframework.web.context.support.ServletRequestHandledEvent;

import vtk.context.BaseContext;
import vtk.web.RequestContext;
import vtk.web.filter.CaptureInputRequestFilter.CaptureInputRequestWrapper;
import vtk.web.service.Service;
import vtk.web.servlet.HeaderAwareResponseWrapper;

/**
 * HTTP protocol logger/dumper. Logs headers and body by providing a request and
 * response filter in bean context, and then logging the captured data when a
 * {@link ServletRequestHandletEvent } is received.
 * 
 * There is one log message sent per request-response cycle, and log output will
 * consist of multiple lines of data. So configuring a separate log
 * appender/formatter for this class makes sense. It will generally use a logger
 * with the same name as the fully qualified class, but if a specific service is
 * configured, that service name will be appended to the logger id.
 * 
 * Note: in order for logging to occur, you must also configure an instance of
 * {@link CaptureInputRequestFilter} in the bean context.
 * 
 * @see CaptureInputRequestFilter
 */
public class CaptureOutputLoggingResponseFilter extends AbstractResponseFilter implements
        ApplicationListener<ServletRequestHandledEvent>, InitializingBean {

    private int maxLogBytesBody = 4096;
    private Service service;
    private Log logger;

    @Override
    public void afterPropertiesSet() throws Exception {
        String loggerId = CaptureOutputLoggingResponseFilter.class.getName();
        if (this.service != null) {
            loggerId += "." + this.service.getName();
        }
        this.logger = LogFactory.getLog(loggerId);
    }

    @Override
    public HttpServletResponse filter(HttpServletRequest request, HttpServletResponse response) {

        if (!logForService(RequestContext.getRequestContext().getService())) {
            return response;
        }

        // Add self to thread local context
        BaseContext ctx = BaseContext.getContext();
        CaptureOutputResponseWrapper responseWrapper = new CaptureOutputResponseWrapper(response);
        ctx.setAttribute(CaptureOutputResponseWrapper.class.getName(), responseWrapper);

        return responseWrapper;
    }

    @Override
    public void onApplicationEvent(ServletRequestHandledEvent event) {
        // Look for wrappers in thread local context and write captured
        // data to log

        BaseContext ctx = BaseContext.getContext();
        CaptureInputRequestWrapper reqWrap = (CaptureInputRequestWrapper) ctx
                .getAttribute(CaptureInputRequestWrapper.class.getName());
        CaptureOutputResponseWrapper resWrap = (CaptureOutputResponseWrapper) ctx
                .getAttribute(CaptureOutputResponseWrapper.class.getName());

        if (reqWrap == null) {
            logger.warn("No request wrapper found in thread local context, check that input capture filter is configured.");
        }

        if (reqWrap == null || resWrap == null) {
            return;
        }

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

    @SuppressWarnings("unchecked")
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
        int logBytes = Math.min(maxLogBytesBody, data.length);
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
            if (b > -1 && (streamBytesWritten++ < maxLogBytesBody)) {
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

    }

    /**
     * Set max number of raw bytes to capture for body of response, and max number
     * of bytes to log for body of request and response.
     * Default value is 4096 bytes.
     * 
     * @param maxLogBytesBody
     */
    public void setMaxLogBytesBody(int maxLogBytesBody) {
        if (maxLogBytesBody < 0) {
            throw new IllegalArgumentException("maxLogBytesBody must be >= 0");
        }
        this.maxLogBytesBody = maxLogBytesBody;
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
