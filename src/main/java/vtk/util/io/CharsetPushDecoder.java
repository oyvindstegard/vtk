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
package vtk.util.io;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;


/**
 * Utility class for decoding sequences of bytes to characters, using 
 * a {@link CharsetDecoder} internally. 
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 *   {@link Handler} handler = new Handler() {
 *      public void chars(CharBuffer chars) {
 *          System.out.println("Decoded: " + chars.toString());
 *      }
 *      public void error(Throwable err) { }
 *      public void done() { }
 *   };
 *   ByteBuffer in = ByteBuffer.allocate(...);
 *   Charset cs = Charset.forName("UTF-8");
 *   CharsetPushDecoder decoder = new CharsetPushDecoder(cs, 256, handler);
 *   
 *   while (moreData(...)) {
 *       read(in);
 *       in.flip();
 *       decoder.push(in, !moreData(...));
 *   }
 * </pre>
 *
 */
public final class CharsetPushDecoder {
    public static final int MIN_BUFSIZE = 32;
    private CharsetDecoder decoder;
    private CharBuffer out;
    private Handler handler;
    private boolean terminated = false;
    
    /**
     * Callback interface for {@link CharsetPushDecoder}. 
     */
    public static interface Handler {
        
        /**
         * Called when a buffer of characters has been decoded.
         * @param buffer the decoded characters
         */
        public void chars(CharBuffer buffer);
        
        /**
         * Called when the decoder encounters an error.
         * 
         * @param err the decoder error
         */
        public void error(Throwable err);
        
        /**
         * Called when the decoding process is finished.
         */
        public void done();
    }
    
    /**
     * Creates a new decoder.
     * @param cs the character encoding to use for decoding the input.
     * @param bufsize size of the internal character buffer (minimum 32)
     * @param handler the callback handler
     */
    public CharsetPushDecoder(Charset cs, int bufsize, Handler handler) {
        if (bufsize < MIN_BUFSIZE) throw new IllegalArgumentException(
                "bufsize must be minimum " + MIN_BUFSIZE);
        if (handler == null) throw new NullPointerException("handler");
        decoder = cs.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        out = CharBuffer.allocate(bufsize);
        this.handler = handler;
    }
    
    private void flush() {
        out.flip();
        handler.chars(out);
        out.clear();
    }

    /**
     * Feeds a sequence of bytes to the decoder. This method should be 
     * called repeatedly with <code>endOfInput=false</code> until there 
     * is no more input, and finally one last time with 
     * <code>endOfInput=true</code>.
     *  
     * @param in the input bytes
     * @param endOfInput whether this is the end of the byte sequence
     */
    public void push(ByteBuffer in, boolean endOfInput) {
        if (terminated) {
            handler.error(new IllegalStateException("Terminated"));
        }
        CoderResult cr = decoder.decode(in, out, false);
        while (cr.isOverflow()) {
            flush();
            cr = decoder.decode(in, out, false);
        }
        
        // If endOfInput, invoke one last time with eoi = true:
        if (endOfInput) cr = decoder.decode(in, out, true);
        
        if (cr.isUnderflow()) {
            if (endOfInput) {
                cr = decoder.flush(out);
                flush();
                while (cr.isOverflow()) {
                    flush();
                    cr = decoder.flush(out);
                }
                terminated = true;
                handler.done();
            }
            else in.compact();
        }
        if (cr.isError()) {
            terminated = true;
            try { cr.throwException(); } 
            catch (Throwable t) { handler.error(t); }
        }
    }
}
