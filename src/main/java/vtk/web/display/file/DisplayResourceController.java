/* Copyright (c) 2004, University of Oslo, Norway
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
package vtk.web.display.file;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.util.io.StreamUtil;
import vtk.web.RequestContext;


/**
 * Writes the contents of a resource to the client.
 * 
 * <p><a name="config">Configurable properties</a>
 * <ul>
 *   <li><code>streamBufferSize</code> - (int) the size of the buffer
 *   used when executing the (read from resource, write to response)
 *   loop. The default value is <code>5000</code>.
 * </ul>
 *
 * <p>Requires the following data to be present in the model:
 * <ul>
 *   <li><code>resource</code> - the {@link Resource} object requested
 *   <li><code>resourceStream</code> - the content {@link InputStream} to write to the
 *   client 
 * </ul>
 * 
 * <p>Sets the following HTTP headers, based on metadata in the
 * resource and request headers:
 * <ul>
 *   <li><code>Content-Type</code>
 *   <li><code>Content-Length</code>
 *   <li><code>Accept-Ranges</code>
 *   <li><code>Content-Range</code>
 * </ul>
 */
public class DisplayResourceController implements Controller {

    private static final String defaultCharacterEncoding = "utf-8";

    private boolean displayProcessed = false;
    private boolean supportRangeRequests = false;
    private int streamBufferSize = 5000;
    

    private static Log logger = LogFactory.getLog(DisplayResourceController.class);

    public void setDisplayProcessed(boolean displayProcessed) {
        this.displayProcessed = displayProcessed;
    }


     @Override
    public ModelAndView handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();

        Map<String, Object> model = new HashMap<String, Object>();
        Resource resource = repository.retrieve(token, uri, this.displayProcessed);

        model.put("resource", resource);

        InputStream stream = repository.getInputStream(token, uri, true);

        // Provide as string instead of stream
        String characterEncoding = resource.getCharacterEncoding();
        if (characterEncoding == null) {
            characterEncoding = defaultCharacterEncoding;
        }

         Range range = this.supportRangeRequests ? 
                 getRangeHeader(request, resource) : null;
         request.setAttribute(Range.class.getName(), range);
         setHeaders(resource, request, response);

         if ("HEAD".equals(request.getMethod())) {
             if (logger.isDebugEnabled()) {
                 logger.debug("Request is HEAD, not writing content");
             }
             response.flushBuffer();
             return null;
         }
         writeResponse(stream, request, response);
         return null;
     }


    protected void setHeaders(Resource resource, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        if (this.supportRangeRequests) {
            setHeader(response, "Accept-Ranges", "0-" + (resource.getContentLength() - 1));
        }

        Range range = (Range) request.getAttribute(Range.class.getName());
        setContentTypeHeader(resource, response);
        if (range != null) {
            setStatus(response, HttpServletResponse.SC_PARTIAL_CONTENT);
            setHeader(response, "Content-Range", "bytes " + range.from + "-" 
                    + range.to + "/" + resource.getContentLength());
            setHeader(response, "Content-Length", String.valueOf(range.to - range.from + 1));
        }
        else {
            setStatus(response, HttpServletResponse.SC_OK);
            setContentLengthHeader(resource, response);
        }
    }
    
    protected void writeResponse(InputStream resourceStream,
            HttpServletRequest request, HttpServletResponse response) throws Exception {
        Range range = (Range) request.getAttribute(Range.class.getName());
        long bytesWritten = 0L;
        if (range != null) {
            long nbytes = range.to - range.from + 1;
            if (logger.isDebugEnabled()) {
                logger.debug("Writing range: " + range.from + "-" + range.to);
            }
            bytesWritten = StreamUtil.pipe(
                    resourceStream, response.getOutputStream(), 
                    range.from, nbytes,
                    this.streamBufferSize, true);
        }
        else {
            bytesWritten = StreamUtil.pipe(
                    resourceStream, response.getOutputStream(), 
                    this.streamBufferSize, false);
        }
        
        response.flushBuffer();

        if (logger.isDebugEnabled()) {
            logger.debug("Wrote a total of " + bytesWritten + " bytes to response");
        }
    }

    protected void setStatus(HttpServletResponse response, int status) {
        if (logger.isDebugEnabled()) {
            logger.debug("Setting status: " + status);
        }
        response.setStatus(status);
    }

    protected void setHeader(HttpServletResponse response, String name, Object value) {
        if (logger.isDebugEnabled()) {
            logger.debug("Setting header " + name + ": " + value);
        }
        response.setHeader(name, value.toString());
    }
    
    protected void setContentTypeHeader(Resource resource, HttpServletResponse response) {
        response.setContentType(resource.getContentType());
        if (resource.getCharacterEncoding() != null)
            response.setCharacterEncoding(resource.getCharacterEncoding());
    }
    
    protected void setContentLengthHeader(Resource resource, HttpServletResponse response) 
            throws Exception {
        setHeader(response, "Content-Length", String.valueOf(resource.getContentLength()));
    }

    private static class Range {
        long from; long to;
        public Range(long from, long to) {
            this.from = from; this.to = to;
        }
        public String toString() {
            return "Range: " + from + ":" + to;
        }
    }

    private Range getRangeHeader(HttpServletRequest request, Resource resource) {
        String hdr = request.getHeader("Range");
        if (hdr == null) {
            return null;
        }
        if (!hdr.startsWith("bytes=")) {
            return null;
        }
        StringBuilder fromStr = new StringBuilder();
        StringBuilder toStr = new StringBuilder();
        StringBuilder cur = fromStr;
        for (int i = "bytes=".length(); i < hdr.length(); i++) {
            char c = hdr.charAt(i);
            if (c == '-') {
                cur = toStr;
            }
            else if (c < '0' || c > '9') {
                return null;
            }
            else {
                cur.append(c);
            }
        }
        long from = 0;
        if (fromStr.length() > 0) {
            from = Long.parseLong(fromStr.toString());
        }
        long to = resource.getContentLength() - 1;
        if (toStr.length() > 0) {
            to = Long.parseLong(toStr.toString());
        }
        if (to <= from) {
            return null;
        }
        if (to > resource.getContentLength() - 1) {
            return null;
        }
        return new Range(from, to);
    }
}
