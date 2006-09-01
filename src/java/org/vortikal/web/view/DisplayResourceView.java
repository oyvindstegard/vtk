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
package org.vortikal.web.view;



import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.servlet.view.AbstractView;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Property;
import org.vortikal.repository.Resource;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.util.repository.ContentTypeHelper;
import org.vortikal.util.repository.LocaleHelper;
import org.vortikal.util.web.HttpUtil;
import org.vortikal.web.InvalidModelException;
import org.vortikal.web.referencedata.ReferenceDataProvider;
import org.vortikal.web.referencedata.ReferenceDataProviding;


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
 * resource:
 * <ul>
 *   <li><code>Content-Type</code>
 *   <li><code>Content-Length</code>
 * </ul>
 *
 */
public class DisplayResourceView extends AbstractView
  implements ReferenceDataProviding {

    private static Log logger = LogFactory.getLog(DisplayResourceView.class);
    
    private int streamBufferSize = 5000;

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
    

    public void renderMergedOutputModel(Map model, HttpServletRequest request,
                                        HttpServletResponse response) throws Exception {

        Resource resource = getResource(model, request, response);
        InputStream resourceStream = getResourceStream(resource, model, request, response);

        setHeaders(resource, model, request, response);
        writeResponse(resource, resourceStream, model, request, response);
    }
    


    /**
     * Gets the {@link Resource} object being served. Defaults to
     * examining the model for the key <code>resource</code>.
     */
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
     */
    protected InputStream getResourceStream(Resource resource, Map model,
                                   HttpServletRequest request,
                                   HttpServletResponse response) throws Exception {
        Object o = model.get("resourceStream");
        if (o == null || ! (o instanceof InputStream)) {
            throw new InvalidModelException(
                "Missing InputStream in model " +
                "(expected an InputStream object having key 'resourceStream')");
        }
        return (InputStream) o;
    }
    

    protected void setHeaders(Resource resource, Map model, HttpServletRequest request,
                              HttpServletResponse response) throws Exception {

        setContentTypeHeader(resource, model, request, response);
        setContentLengthHeader(resource, model, request, response);
        response.setStatus(HttpServletResponse.SC_OK);
    }
    


    protected void writeResponse(Resource resource, InputStream resourceStream,
                                 Map model, HttpServletRequest request,
                                 HttpServletResponse response) throws Exception {
        OutputStream out = null;
        int bytesWritten = 0;
        try {
            if ("HEAD".equals(request.getMethod())) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Request is HEAD, not writing content");
                }
                response.flushBuffer();
            } else {

                out = response.getOutputStream();
                byte[] buffer = new byte[this.streamBufferSize];
                int n = 0;
                while (((n = resourceStream.read(buffer, 0, this.streamBufferSize)) > 0)) {
                    out.write(buffer, 0, n);
                    bytesWritten += n;
                }
            }

        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Wrote a total of " + bytesWritten
                             + " bytes to response");
            }

            if (out != null) {
                out.flush();
                out.close();
            }
            if (resourceStream != null) resourceStream.close();
        }
    }


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
        } else if (ContentTypeHelper.isTextContentType(resource.getContentType())
                   && resource.getCharacterEncoding() != null) {
            contentType = resource.getContentType() + ";charset="
                + resource.getCharacterEncoding();
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("Setting header Content-Type: " + contentType);
        }
        response.setHeader("Content-Type", contentType);
    }
    

    protected void setContentLengthHeader(Resource resource, Map model,
                                          HttpServletRequest request,
                                          HttpServletResponse response) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("Setting header Content-Length: " + resource.getContentLength());
        }
        response.setHeader("Content-Length", String.valueOf(resource.getContentLength()));
    }
    
    
    
    
    

}
