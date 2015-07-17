/* Copyright (c) 2007, University of Oslo, Norway
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
package vtk.web.decorating.components;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Locale;

import vtk.text.html.HtmlElement;
import vtk.text.html.HtmlPage;
import vtk.text.html.HtmlPageParser;
import vtk.web.decorating.DecoratorResponse;


public class MockDecoratorResponse implements DecoratorResponse {
        
    private Charset charset = Charset.forName("utf-8");

    private ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    public MockDecoratorResponse() {
    }
        
    public void setLocale(Locale locale) {
    }
        
    public void setDoctype(String doctype) {
    }

    public void setCharacterEncoding(String characterEncoding) {
        this.charset = Charset.forName(characterEncoding);
    }

    public void setDoctype() {
    }

    public OutputStream getOutputStream() {
        return this.outputStream;
    }

    public Writer getWriter() {
        return new OutputStreamWriter(this.outputStream, this.charset);
    }

    public String getResult() throws Exception {
        return new String(this.outputStream.toByteArray(), this.charset.name());
    }

    public HtmlElement[] getParsedResult() throws Exception {
        String result = "<html type=\"dummy-root-element\">"
            + new String(this.outputStream.toByteArray(), this.charset.name())
            + "</html>";
        HtmlPageParser parser = new HtmlPageParser();
        HtmlPage page = parser.parse(new java.io.ByteArrayInputStream(
                                         result.getBytes("utf-8")), "utf-8");
        HtmlElement dummy = page.getRootElement();
        return dummy.getChildElements();
    }
    
    public void flush() throws IOException {
        outputStream.flush();
    }
}
