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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import vtk.text.html.HtmlNodeFilter;
import vtk.text.html.HtmlPage;
import vtk.text.html.HtmlPageParser;

public class DecoratingServletOutputStream extends ServletOutputStream {
    private static Log logger = LogFactory.getLog(DecoratingServletOutputStream.class);
    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private HttpServletRequest request;
    private HttpServletResponse response;
    private Map<String, Object> model;
    private Map<String, Object> templateParameters;
    private OutputStream out;
    private Charset encoding;
    private Template template;
    private HtmlPageParser htmlParser;
    private List<HtmlNodeFilter> filters;
    private boolean committed = false;

    public DecoratingServletOutputStream(OutputStream out,
            HttpServletRequest request,
            HttpServletResponse response,
            Map<String, Object> model,
            Map<String, Object> templateParameters,
            Charset encoding,
            Template template, 
            HtmlPageParser htmlParser,
            List<HtmlNodeFilter> filters) {
        this.request = request;
        this.response = response;
        this.model = model;
        this.templateParameters = templateParameters;
        this.out = out;
        this.encoding = encoding;
        this.template = template;
        this.htmlParser = htmlParser;
        if (filters != null) {
            this.filters = new ArrayList<>(filters);
        }
        else {
            this.filters = new ArrayList<>();
        }
    }
    
    @Override
    public void flush() throws IOException {
    }

    @Override
    public void close() throws IOException {
        if (committed) {
            return;
        }
        try {
            decorate();
        }
        catch (Exception e) { 
            throw new IOException(e);
        }
        finally {
            committed = true;
            out.close();
        }
    }

    @Override
    public void write(int b) throws IOException {
        if (committed) throw new IOException("Closed");
        buffer.write(b);
    }

    private void decorate() throws Exception {
        InputStream in = new ByteArrayInputStream(buffer.toByteArray());
        HtmlPage page = htmlParser.parse(in, encoding.toString(), filters);
        if (template != null && !page.isFrameset()) {
            
            template.render(page, out, encoding, request, model, templateParameters);
        }
        else {
            String s = page.getStringRepresentation();
            out.write(s.getBytes(encoding));
        }
        out.flush();
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
