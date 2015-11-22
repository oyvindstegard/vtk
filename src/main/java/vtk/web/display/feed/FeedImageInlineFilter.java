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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyType;
import vtk.util.codec.Base64;
import vtk.web.RequestContext;
import vtk.web.filter.AbstractResponseFilter;
import vtk.web.service.URL;

public class FeedImageInlineFilter extends AbstractResponseFilter {
    
    private static Log logger = LogFactory.getLog(FeedImageInlineFilter.class);

    @Override
    public HttpServletResponse filter(HttpServletRequest request,
            HttpServletResponse response) {
        if ("true".equals(request.getParameter("inline-thumbnails"))) {
            return new FilterHttpServletResponse(response);
        }
        return response;
    }

    
    private static class FilterHttpServletResponse extends HttpServletResponseWrapper {
        private long contentLength = -1L;
        
        boolean filter = false;
        private ServletOutputStream out = null;

        public FilterHttpServletResponse(HttpServletResponse response) {
            super(response);
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
                        new FilterServletOutputStream(super.getOutputStream());
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
        private OutputStream out;
        private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private boolean committed = false;
        
        public FilterServletOutputStream(OutputStream out) {
            this.out = out;
        }
        
        @Override
        public void flush() throws IOException {
        }
        
        @Override
        public void close() throws IOException {
            if (committed) throw new IOException("Committed");
            
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
                          inlineImage(data, writer, imgBuffer);
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
        
        private void inlineImage(String ref, Writer writer, byte[] buffer) throws Exception {
            RequestContext requestContext = RequestContext.getRequestContext();
            Repository repo = requestContext.getRepository();
            String token = requestContext.getSecurityToken();
            
            URL requestURL = requestContext.getRequestURL();
            URL url;
            if (ref.startsWith("//") && 
                    ref.startsWith(requestURL.setPath(Path.ROOT).protocolRelativeURL())) {
                url = URL.parse("https:" + ref);
            }
            else {
                url = requestContext.getRequestURL().relativeURL(ref);
            }
            Path uri = url.getPath();
            Resource resource = repo.retrieve(token, uri, true);
            Property thumbnail = resource.getProperty(
                    Namespace.DEFAULT_NAMESPACE, PropertyType.THUMBNAIL_PROP_NAME);
            
            if (thumbnail != null) {
                InputStream encoderStream = Base64.encoderStream(thumbnail.getBinaryStream().getStream());
                writer.write("data:" + thumbnail.getBinaryContentType() + ";base64,");
                int n = 0;
                while ((n = encoderStream.read(buffer)) > 0) {
                    writer.write(new String(buffer, 0, n, "US-ASCII"));
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
