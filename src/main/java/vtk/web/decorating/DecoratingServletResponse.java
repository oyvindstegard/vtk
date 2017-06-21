/* Copyright (c) 2017, University of Oslo, Norway
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
package vtk.web.decorating;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import vtk.text.html.HtmlNodeFilter;
import vtk.text.html.HtmlPageParser;

public class DecoratingServletResponse extends HttpServletResponseWrapper {
    private static Log logger = LogFactory.getLog(DecoratingServletResponse.class);
    private HttpServletRequest request;
    private ServletResponse response;
    private DecorationResolver resolver;
    private HtmlPageParser htmlParser;
    private List<HtmlNodeFilter> filters;
    private ComponentResolver componentResolver;
    private ServletOutputStream out = null;
    private long maxSize;
    private Charset encoding;
    
    private String contentType = null;
    private long contentLength = -1L;
    
    
    public DecoratingServletResponse(HttpServletRequest request, 
            HttpServletResponse response, DecorationResolver resolver,
            HtmlPageParser htmlParser,
            List<HtmlNodeFilter> filters,
            ComponentResolver componentResolver, 
            long maxSize) {
        super(response);
        this.request = request;
        this.response = response;
        this.resolver = resolver;
        this.htmlParser = htmlParser;
        this.filters = filters;
        this.componentResolver = componentResolver;
        this.contentType = getContentType();
        this.maxSize = maxSize;
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + request + ", " + response + ")";
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (out != null) throw new IOException("Output stream already opened");
        
        Map<String, Object> model = new HashMap<>();
        
        if (contentType != null && contentType.startsWith("text/html")) {
            try {
                DecorationDescriptor descriptor = resolver.resolve(request, this);
                logger.debug("Descriptor[" + getStatus() + "]: " + descriptor);
                List<Template> templates = descriptor.getTemplates();

                if (descriptor.decorate() && templates != null && !templates.isEmpty()) {
                    
                    ServletOutputStream stream = response.getOutputStream();
                    for (int i = templates.size() - 1; i >= 0; i--) {
                        Template template = templates.get(i);
                        Map<String, Object> parameters = 
                                descriptor.getParameters(template);
                        stream = wrap(stream, template, model, parameters);
                    }
                    this.out = stream;
                    return stream;
                }
            }
            catch (Exception e) {
                throw new IOException(e);
            }
        }
        out = response.getOutputStream();
        return out;
    }
    
    @Override
    public PrintWriter getWriter() throws IOException {
        if (out != null) throw new IOException("Output stream already opened");
        return new PrintWriter(getOutputStream());
    }

    @Override
    public void setContentLength(int len) {
        setHeader("Content-Length", String.valueOf(len));
    }

    @Override
    public void setHeader(String name, String value) {
        if ("Content-Length".equalsIgnoreCase(name)) {
            try {
                Long contentLength = Long.parseLong(value);
                
                if (contentType != null && contentType.startsWith("text/html") 
                        && contentLength < maxSize) {
                    return;
                }
                this.contentLength = contentLength;
            } 
            catch (Exception e) { }
        }
        else if ("Content-Type".equalsIgnoreCase(name)) {
            setContentType(value);
        }
        super.setHeader(name, value);
    }
    
    @Override
    public void addHeader(String name, String value) {
        setHeader(name, value);
    }

    @Override
    public void setContentType(String type) {
        super.setContentType(type);
        contentType = type;
    }
    
    @Override
    public void flushBuffer() throws IOException {
        if (out != null) {
            out.flush();
            out.close();
        }
    }
    
    private DecoratingServletOutputStream wrap(ServletOutputStream stream, 
            Template template, Map<String, Object> model, Map<String, Object> parameters) {
        
        Charset encoding = Charset.forName(getCharacterEncoding());
        return  new DecoratingServletOutputStream(stream,
                request, model, parameters, encoding, template, htmlParser, filters, maxSize);
    }
    
}
