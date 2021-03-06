/* Copyright (c) 2015, University of Oslo, Norway
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
package vtk.web.display.feed;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyType;
import vtk.util.codec.Base64;
import vtk.web.RequestContext;
import vtk.web.service.URL;
import vtk.web.servlet.AbstractServletFilter;

public class FeedImageInlineFilter extends AbstractServletFilter {
    private static Logger logger = LoggerFactory.getLogger(FeedImageInlineFilter.class);

    @Override
    protected void doFilter(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
        if ("true".equals(request.getParameter("inline-thumbnails"))) {
            RequestContext requestContext = RequestContext.getRequestContext(request);
            chain.doFilter(request, new FilterHttpServletResponse(response, requestContext));
        }
        else {
            chain.doFilter(request, response);
        }
    }

    
    private static class FilterHttpServletResponse extends HttpServletResponseWrapper {
        private long contentLength = -1L;
        
        boolean filter = false;
        private ServletOutputStream out = null;
        private RequestContext requestContext;

        public FilterHttpServletResponse(HttpServletResponse response, RequestContext requestContext) {
            super(response);
            this.requestContext = requestContext;
        }
        
        @Override
        public void setStatus(int sc) {
            filter = sc == HttpServletResponse.SC_OK;
            super.setStatus(sc);
        }
        
        @Override
        public void setStatus(int sc, String sm) {
            filter = sc == HttpServletResponse.SC_OK;
            super.setStatus(sc, sm);
        }
        
        @Override
        public void setContentType(String contentType) {
            if (contentType.startsWith("application/atom")) {
                filter = true;
            }
            super.setContentType(contentType);
        }
        
        @Override
        public void setHeader(String header, String value) {
            if ("Content-Length".equalsIgnoreCase(header)) {
                try {
                    Long contentLength = Long.parseLong(value);
                    this.contentLength = contentLength;
                }
                catch (Exception e) { }             
            }
            else {
                super.setHeader(header, value);
            }
        }
        
        @Override
        public PrintWriter getWriter() throws IOException {
            if (out != null) throw new IOException("Output stream already opened");
            return new PrintWriter(getOutputStream());
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (out != null) throw new IOException("Output stream already opened");
            if (filter) {
                FilterServletOutputStream filterStream = 
                        new FilterServletOutputStream(super.getOutputStream(), requestContext);
                this.out = filterStream;
                return filterStream;
            }
            else {
                if (contentLength > 0) {
                    super.setHeader("Content-Length", String.valueOf(contentLength));
                }
                return super.getOutputStream();
            }
        }
    }
    
    private static class FilterServletOutputStream extends ServletOutputStream {
        private static final long MAX_IMG_INLINE_SIZE = 1000000L;
        private OutputStream out;
        private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private boolean committed = false;
        private RequestContext requestContext;
        
        public FilterServletOutputStream(OutputStream out, RequestContext requestContext) {
            this.out = out;
            this.requestContext = requestContext;
        }
        
        @Override
        public void flush() throws IOException {
        }
        
        @Override
        public void close() throws IOException {
            if (committed) return;
            
            try { 
                transform(); 
            } 
            catch (Exception e) {
                throw new IOException(e);
            }
            out.close();
            committed = true;
        }

        @Override
        public void write(int b) throws IOException {
            if (committed) throw new IOException("Committed");
            buffer.write(b);
        }
        
        private void transform() throws Exception {
            XMLInputFactory inputFactory = XMLInputFactory.newFactory();
            XMLEventReader xmlReader = inputFactory.createXMLEventReader(
                  new ByteArrayInputStream(buffer.toByteArray()));
          
          Writer writer = new OutputStreamWriter(out);
          
          byte[] imgBuffer = new byte[1024];
          
          boolean expectImageData = false;
          
          while (xmlReader.hasNext()) {
              XMLEvent event = xmlReader.nextEvent();
              if (event.isStartElement()) {
                  StartElement elem = event.asStartElement();
                  expectImageData = "image-thumbnail".equals(elem.getName().getLocalPart());
                  event.writeAsEncodedUnicode(writer);
              }
              else if (event.isEndElement()) {
                  expectImageData = false;
                  event.writeAsEncodedUnicode(writer);                  
              }
              else if (event.isCharacters() && expectImageData) {
                  Characters chars = event.asCharacters();
                  String data = chars.getData();
                  if (data.startsWith("/") || data.startsWith("//")) {
                      try {
                          inlineImage(requestContext, data, writer, imgBuffer);
                      }
                      catch (Throwable t) {
                          logger.debug("Failed to inline image thumbnail: " + data, t);
                          writer.write(data);
                      }
                  }
                  else {
                      event.writeAsEncodedUnicode(writer);
                  }
                  expectImageData = false;
              }
              else {
                  event.writeAsEncodedUnicode(writer);
              }
          }
          writer.flush();
          writer.close();
        }
        
        private void inlineImage(RequestContext requestContext, String ref, 
                Writer writer, byte[] buffer) throws Exception {
            Repository repo = requestContext.getRepository();
            String token = requestContext.getSecurityToken();
            
            URL requestURL = new URL(requestContext.getRequestURL());
            URL url;
            if (ref.startsWith("//") && 
                    ref.startsWith(requestURL.setPath(Path.ROOT).protocolRelativeURL())) {
                url = URL.parse("https:" + ref);
            }
            else {
                url = requestContext.getRequestURL().relativeURL(ref);
            }
            Path uri = url.getPath();
            InputStream imageStream = null;
            String contentType = null;
            Resource resource = repo.retrieve(token, uri, true);
            Property thumbnail = resource.getProperty(
                    Namespace.DEFAULT_NAMESPACE, PropertyType.THUMBNAIL_PROP_NAME);
            
            if (thumbnail != null) {
                imageStream = thumbnail.getBinaryStream();
                contentType = thumbnail.getBinaryContentType();
            }
            else if (resource.getContentLength() < MAX_IMG_INLINE_SIZE) {
                imageStream = repo.getInputStream(token, uri, true);
                contentType = resource.getContentType();
            }

            if (imageStream != null) {
                try (InputStream encoderStream = Base64.encoderStream(imageStream)) {
                    writer.write("data:" + contentType + ";base64,");
                    int n = 0;
                    while ((n = encoderStream.read(buffer)) > 0) {
                        writer.write(new String(buffer, 0, n, StandardCharsets.US_ASCII));
                    }
                }
            }
        }

        @Override
        public boolean isReady() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            throw new UnsupportedOperationException();
        }
    }
}
