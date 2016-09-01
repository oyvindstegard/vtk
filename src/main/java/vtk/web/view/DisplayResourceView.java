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
package vtk.web.view;

import java.io.InputStream;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.view.AbstractView;

import vtk.repository.Resource;
import vtk.util.io.IO;
import vtk.util.repository.ContentTypeHelper;
import vtk.web.InvalidModelException;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.referencedata.ReferenceDataProviding;


/**
 * "Web server" resembling view. Writes the contents of a
 * resource to the client.
 *
 * <p><a name="config">Configurable properties</a>
 * (and those defined by {@link AbstractView superclass}):
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
 *
 */
public class DisplayResourceView extends AbstractView
  implements ReferenceDataProviding {

    private static Logger logger = LoggerFactory.getLogger(DisplayResourceView.class);
    
    private int streamBufferSize = 5000;
    private boolean supportRangeRequests = false;

    private ReferenceDataProvider[] referenceDataProviders;
    

    public ReferenceDataProvider[] getReferenceDataProviders() {
        return this.referenceDataProviders;
    }

    public void setReferenceDataProviders(ReferenceDataProvider[] referenceDataProviders) {
        this.referenceDataProviders = referenceDataProviders;
    }

    public void setStreamBufferSize(int streamBufferSize) {
        if (streamBufferSize <= 0) {
            throw new IllegalArgumentException(
                "The value of streamBufferSize must be a positive integer");
        }
        this.streamBufferSize = streamBufferSize;
    }
    
    public void setSupportRangeRequests(boolean supportRangeRequests) {
        this.supportRangeRequests = supportRangeRequests;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void renderMergedOutputModel(Map model, HttpServletRequest request,
                                        HttpServletResponse response) throws Exception {
        Resource resource = getResource(model, request, response);

        try {
            Range range = this.supportRangeRequests ? 
                    getRangeHeader(request, resource) : null;
            request.setAttribute(Range.class.getName(), range);
        }
        catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ;
        }

        setHeaders(resource, model, request, response);
        
        if ("HEAD".equals(request.getMethod())) {
            if (logger.isDebugEnabled()) {
                logger.debug("Request is HEAD, not writing content");
            }
            response.flushBuffer();
            InputStream resourceStream = getResourceStream(resource, model, request, response);
            if (resourceStream != null) {
                resourceStream.close();
            }
            return;
        }
        InputStream resourceStream = getResourceStream(resource, model, request, response);
        if (resourceStream == null) {
            throw new InvalidModelException("Unable to write response for resoure " + resource
                                            + ": missing InputStream in model ");
        }
        writeResponse(resource, resourceStream, model, request, response);
    }
    
    /**
     * Gets the {@link Resource} object being served. Defaults to
     * examining the model for the key <code>resource</code>.
     * @param model the MVC model
     * @param request the servlet request
     * @param response
     */
    @SuppressWarnings("rawtypes")
    protected Resource getResource(Map model,
                                   HttpServletRequest request,
                                   HttpServletResponse response) throws Exception {
        Object o = model.get("resource");
        if (o == null || ! (o instanceof Resource)) {
            throw new InvalidModelException(
                "Missing resource in model " +
                "(expected a Resource object having key 'resource')");
        }
        return (Resource) o;
    }
    
    /**
     * Gets the {@link InputStream} representing the content of the
     * served resource. Defaults to examining the model for the key
     * <code>resourceStream</code>. Note to overriders: be careful to
     * always close the input stream already present in the model when
     * returning a different input stream.
     * @param resource the served resource
     * @param request the servlet request
     * @param response
     */
    @SuppressWarnings("rawtypes")
    protected InputStream getResourceStream(Resource resource, Map model,
                                   HttpServletRequest request,
                                   HttpServletResponse response) throws Exception {
        Object o = model.get("resourceStream");
        return (InputStream) o;
    }
    

    @SuppressWarnings("rawtypes")
    protected void setHeaders(Resource resource, Map model, HttpServletRequest request,
                              HttpServletResponse response) throws Exception {
        if (this.supportRangeRequests) {
            setHeader(response, "Accept-Ranges", "0-" + (resource.getContentLength() - 1));
        }
        
        Range range = (Range) request.getAttribute(Range.class.getName());
        setContentTypeHeader(resource, model, request, response);
        if (range != null) {
            setStatus(response, HttpServletResponse.SC_PARTIAL_CONTENT);
            setHeader(response, "Content-Range", "bytes " + range.from + "-" 
                    + range.to + "/" + resource.getContentLength());
            setHeader(response, "Content-Length", String.valueOf(range.to - range.from + 1));
        }
        else {
            setStatus(response, HttpServletResponse.SC_OK);
            setContentLengthHeader(resource, model, request, response);
        }
    }

    /**
     * Write the response.
     * 
     * @param resource the served resource
     * @param resourceStream the content stream
     * @param model the MVC model
     * @param request the servlet request
     * @param response the servlet response
     * @throws Exception
     */
    @SuppressWarnings("rawtypes")
    protected void writeResponse(Resource resource, InputStream resourceStream,
                                 Map model, HttpServletRequest request,
                                 HttpServletResponse response) throws Exception {
        Range range = (Range) request.getAttribute(Range.class.getName());
        long bytesWritten = 0L;
        if (range != null) {
            long nbytes = range.to - range.from + 1;
            if (logger.isDebugEnabled()) {
                logger.debug("Writing range: " + range.from + "-" + range.to);
            }
            bytesWritten = IO.copy(resourceStream, response.getOutputStream())
                            .offset(range.from).limit(nbytes)
                            .bufferSize(this.streamBufferSize)
                            .perform();
        }
        else {
            bytesWritten = IO.copy(resourceStream, response.getOutputStream())
                    .bufferSize(this.streamBufferSize).perform();
                    
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
    
    /**
     * Sets the content type header based on the resource.
     * 
     * @param resource the served resource
     * @param model the MVC model
     * @param request the servlet request
     * @param response the servlet response
     * @throws Exception
     */
    @SuppressWarnings("rawtypes")
    protected void setContentTypeHeader(Resource resource, Map model,
                                        HttpServletRequest request,
                                        HttpServletResponse response) throws Exception {
        String contentType = resource.getContentType();
        
        if (ContentTypeHelper.isHTMLContentType(resource.getContentType()) &&
            resource.getCharacterEncoding() == null) {
            // FIXME: to prevent some servlet containers (resin) from
            // trying to be "smart" and append "charset=iso-8859-1" to
            // the Content-Type header when no character encoding has
            // been specified. According to RFC 2616, sec. 4.2,
            // preceding the header value with arbitrary amount of LWS
            // is perfectly legal, although a single space is
            // preferred.
            contentType = " " + resource.getContentType();
        }
        else if (ContentTypeHelper.isTextContentType(resource.getContentType())
                   && resource.getCharacterEncoding() != null) {
            contentType = resource.getContentType() + ";charset="
                + resource.getCharacterEncoding();
        }
        setHeader(response, "Content-Type", contentType);
    }
    

    /**
     * Sets the content length header based on the resource.
     * 
     * @param resource the served resource
     * @param model the MVC model
     * @param request the servlet request
     * @param response the servlet response
     * @throws Exception
     */
    @SuppressWarnings("rawtypes")
    protected void setContentLengthHeader(Resource resource, Map model,
                                          HttpServletRequest request,
                                          HttpServletResponse response) throws Exception {
        setHeader(response, "Content-Length", String.valueOf(resource.getContentLength()));
        
    }
    
    private static class Range {
        long from; long to;
        public Range(long from, long to) {
            this.from = from; this.to = to;
        }
        @Override
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
