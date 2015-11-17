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
package vtk.web.servlet;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.ServletOutputStream;


/**
 * A servlet output stream wrapper.
 */
public class WrappedServletOutputStream extends ServletOutputStream {

    private final OutputStream out;
    private final String characterEncoding;
        
    public WrappedServletOutputStream(OutputStream out,
                                      String characterEncoding) {
        this.out = out;
        this.characterEncoding = characterEncoding;
    }

    public String getCharacterEncoding() {
        return this.characterEncoding;
    }

    @Override
    public void print(boolean b) throws IOException {
        this.out.write(String.valueOf(b).getBytes(this.characterEncoding));
    }
        
    @Override
    public void print(char c) throws IOException {
        this.out.write(String.valueOf(c).getBytes(this.characterEncoding));
    }
        
    @Override
    public void print(int n) throws IOException {
        this.out.write(String.valueOf(n).getBytes(this.characterEncoding));
    }

    @Override
    public void print(long l) throws IOException {
        this.out.write(String.valueOf(l).getBytes(this.characterEncoding));
    }

    @Override
    public void print(String s) throws IOException {
        this.out.write(s.getBytes(this.characterEncoding));
    }

    @Override
    public void print(float f) throws IOException {
        this.out.write(String.valueOf(f).getBytes(this.characterEncoding));
    }

    @Override
    public void print(double d) throws IOException {
        this.out.write(String.valueOf(d).getBytes(this.characterEncoding));
    }

    @Override
    public void println() throws IOException {
        this.out.write("\r\n".getBytes(this.characterEncoding));
    }

    @Override
    public void println(String s) throws IOException {
        print(s);
        println();
    }

    @Override
    public void println(boolean b) throws IOException {
        print(b);
        println();
    }

    @Override
    public void println(char c) throws IOException {
        print(c);
        println();
    }

    @Override
    public void println(int n) throws IOException {
        print(n);
        println();
    }
        
    @Override
    public void println(long l) throws IOException {
        print(l);
        println();
    }

    @Override
    public void println(double d) throws IOException {
        print(d);
        println();
    }

    @Override
    public void println(float f) throws IOException {
        print(f);
        println();
    }

    @Override
    public void write(int n) throws IOException {
        this.out.write(n);
    }

    @Override
    public void write(byte[] b) throws IOException {
        this.out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        this.out.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public void flush() throws IOException {
    }

}
