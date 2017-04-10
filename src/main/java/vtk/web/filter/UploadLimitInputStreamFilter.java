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
package vtk.web.filter;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import vtk.util.io.BoundedInputStream;
import vtk.util.io.SizeLimitException;
import vtk.web.servlet.AbstractServletFilter;


/**
 * Filter that pipes the request input stream trough a {@link
 * BoundedInputStream} filter, providing a limit to upload sizes.
 */
public class UploadLimitInputStreamFilter extends AbstractServletFilter {
    private long uploadLimit = 0;
    
    public UploadLimitInputStreamFilter(long uploadLimit) {
        this.uploadLimit = uploadLimit;
    }
    
    @Override
    protected void doFilter(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(new UploadLimitRequestWrapper(request, uploadLimit), response);
        }
        catch (SizeLimitException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(e.getMessage());
            response.flushBuffer();
        }
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + uploadLimit + ")";
    }
    
    private static class UploadLimitRequestWrapper extends HttpServletRequestWrapper {
        private HttpServletRequest request;
        private long uploadLimit = 0;

        public UploadLimitRequestWrapper(HttpServletRequest request,
                                         long uploadLimit) {
            super(request);
            this.request = request;
            this.uploadLimit = uploadLimit;
        }
        
        @Override
        public ServletInputStream getInputStream() throws IOException {
            InputStream inputStream = request.getInputStream();
            return new vtk.util.io.ServletInputStream(
                new BoundedInputStream(inputStream, uploadLimit));
        }
    }

}
