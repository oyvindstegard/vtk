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
package org.vortikal.web.filter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.vortikal.context.BaseContext;

/**
 * Capture/copy all request data that is read from input stream.
 * 
 * This filter will store an instance of {@link DavLoggingRequestWrapper } in
 * {@link BaseContext} under class name key and capture all input that is read.
 *
 */
public class DAVLoggingRequestFilter extends AbstractRequestFilter {

    @Override
    public HttpServletRequest filterRequest(HttpServletRequest request) {
        BaseContext ctx = BaseContext.getContext();

        // Add self to thread local context (corresponding response filter needs access).
        DavLoggingRequestWrapper requestWrapper = new DavLoggingRequestWrapper(request);
        ctx.setAttribute(DavLoggingRequestWrapper.class.getName(), requestWrapper);

        return requestWrapper;
    }


    static class DavLoggingRequestWrapper extends HttpServletRequestWrapper {

        private InputStreamCopyWrapper streamWrapper;

        public DavLoggingRequestWrapper(HttpServletRequest request) {
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
        
        byte[] getInputBytes() {
            if (this.streamWrapper != null) {
                return this.streamWrapper.getInputBytes();
            }
            return new byte[0];
        }

    }

    static class InputStreamCopyWrapper extends ServletInputStream {

        private ByteArrayOutputStream streamCopyBuffer;
        private InputStream wrappedStream;

        InputStreamCopyWrapper(InputStream wrappedStream) {
            this.wrappedStream = wrappedStream;
            this.streamCopyBuffer = new ByteArrayOutputStream();
        }

        @Override
        public int read() throws IOException {
            int b = this.wrappedStream.read();
            if (b > -1) {
                streamCopyBuffer.write(b);
            }
            return b;
        }

        byte[] getInputBytes() {
            return this.streamCopyBuffer.toByteArray();
        }
    }
}
