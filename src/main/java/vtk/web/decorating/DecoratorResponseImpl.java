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
package vtk.web.decorating;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Locale;


public class DecoratorResponseImpl implements DecoratorResponse {

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    private String doctype;
    private Locale locale;
    private String characterEncoding;
    
    public DecoratorResponseImpl(String doctype, Locale locale, String characterEncoding) {
        this.doctype = doctype;
        this.locale = locale;
        this.characterEncoding = characterEncoding;
    }
    
    @Override
    public void setDoctype(String doctype) {
        this.doctype = doctype;
    }
    
    @Override
    public String getDoctype() {
        return this.doctype;
    }

    @Override
    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    @Override
    public Locale getLocale() {
        return this.locale;
    }
    
    @Override
    public void setCharacterEncoding(String characterEncoding) {
        java.nio.charset.Charset.forName(characterEncoding);
        this.characterEncoding = characterEncoding;
    }
    
    @Override
    public String getCharacterEncoding() {
        return this.characterEncoding;
    }

    @Override
    public OutputStream getOutputStream() {
        return this.outputStream;
    }
    
    @Override
    public Writer getWriter() throws IOException {
        return new OutputStreamWriter(this.outputStream, this.characterEncoding);
    }
    
    public String getContentAsString() throws Exception {
        return this.outputStream.toString(this.characterEncoding);
    }
    
    public byte[] getContent() throws Exception {
        return this.outputStream.toByteArray();
    }
    
}
