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
package vtk.util.codec;

import java.io.InputStream;

import org.apache.commons.ssl.Base64InputStream;

import vtk.util.io.StreamUtil;

/**
 * A wrapper for commons codec's Base64 implementation.
 */
public class Base64 {

    public static String encode(String str) {
        org.apache.commons.codec.binary.Base64 encoder = new org.apache.commons.codec.binary.Base64();
        return new String(encoder.encode(str.getBytes()));
    }

    public static byte[] encode(byte[] buffer) {
        org.apache.commons.codec.binary.Base64 encoder = new org.apache.commons.codec.binary.Base64();
        return encoder.encode(buffer);
    }

    public static String encode(InputStream in) throws Exception {
        byte[] bytes = StreamUtil.readInputStream(in);
        org.apache.commons.codec.binary.Base64 encoder = new org.apache.commons.codec.binary.Base64();
        return new String(encoder.encode(bytes));
    }

    public static String decode(String str) {
        org.apache.commons.codec.binary.Base64 decoder = new org.apache.commons.codec.binary.Base64();
        return new String(decoder.decode(str.getBytes()));
    }
    
    public static byte[] decode(byte[] buffer) {
        org.apache.commons.codec.binary.Base64 decoder = new org.apache.commons.codec.binary.Base64();
        return decoder.decode(buffer);
    }
    
    public static InputStream decoderStream(InputStream is) {
        return new Base64InputStream(is, true);
    }
    
    public static InputStream encoderStream(InputStream is) {
        return new Base64InputStream(is, false);
    }
}
