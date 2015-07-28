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
package vtk.util.io;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ReadListener;


public class ServletInputStream extends javax.servlet.ServletInputStream {

    private InputStream inputStream;
    private boolean eof = false;

    public ServletInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    // Override all methods of abstract class java.io.InputStream, and delegate
    // them all to the wrapped instance. We want to make sure the most efficient
    // implementation of these methods are actually used.
    
    @Override
    public int read() throws IOException {
        int n = inputStream.read();
        eof = n == -1;
        return n;
    }

    @Override
    public int available() throws IOException {
        return inputStream.available();
    }

    @Override
    public void close() throws IOException {        
        inputStream.close();
    }

    @Override
    public void mark(int readlimit) {
        inputStream.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        return inputStream.markSupported();
    }

    @Override
    public int read(byte[] b) throws IOException {
        int n = inputStream.read(b);
        eof = n == -1;
        return n;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = inputStream.read(b, off, len);
        eof = n == -1;
        return n;
    }

    @Override
    public void reset() throws IOException {
        inputStream.reset();
    }

    @Override
    public long skip(long n) throws IOException {
        return inputStream.skip(n);
    }

    @Override
    public boolean isFinished() {
        return eof;
    }

    @Override
    public boolean isReady() {
        try {
            return inputStream.available() > 0;
        }
        catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        // Noop
    }
    
}
