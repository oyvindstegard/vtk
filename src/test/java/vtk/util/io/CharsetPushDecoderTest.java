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

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.Channels;
import java.nio.channels.CompletionHandler;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;

public class CharsetPushDecoderTest {

    private File UTF8_DEMO;
    private File UTF16_BE_DEMO;
    
    @Before
    public void setup() throws IOException {
        UTF8_DEMO = new File(getClass().getResource("UTF-8-demo.txt").getFile());
        UTF16_BE_DEMO = new File(getClass().getResource("UTF-16-BE-demo.txt").getFile());
    }
    
    @Test
    public void testEncodeDecode() throws IOException {
        String str1 = "abcdefghijklmnopqrstuvwxyz";
        assertEquals(str1, decode(str1, 256));
        
        String str2 = "😎";
        assertEquals(str2, decode(str2, 32));
    }
    
    
    @Test
    public void testFile() throws FileNotFoundException, IOException {
        byte[] bytes = StreamUtil.readInputStream(new FileInputStream(UTF16_BE_DEMO));
        String str = new String(bytes, "utf-16be");
        String decoded = decode(str, 2048);
        assertEquals(str, decoded);
    }
    
    private String decode(String source, int bufsize) throws IOException {
        Charset cs = Charset.forName("utf-8");
        final StringBuilder sb = new StringBuilder();
        CharsetPushDecoder decoder = new CharsetPushDecoder(cs, bufsize, new CharsetPushDecoder.Handler() {
            public void chars(CharBuffer buffer) {
                sb.append(buffer.toString());
            }
            public void done() { }
            public void error(Throwable t) { throw new RuntimeException(t); }
        });

        InputStream is = new ByteArrayInputStream(source.getBytes(cs));
        
        ReadableByteChannel chan = Channels.newChannel(is);
        ByteBuffer in = ByteBuffer.allocate(200);
        
        while (true) {
            int read = chan.read(in);
            in.flip();
            decoder.push(in, read == -1);
            if (read == -1) break;
        }
        chan.close();
        return sb.toString();
    }

    @Test
    public void testAsyncFile() throws IOException {
        final CompletableFuture<String> future = new CompletableFuture<String>() { };
        
        CharsetPushDecoder.Handler decodeHandler = new CharsetPushDecoder.Handler() {
            StringBuilder sb = new StringBuilder();
            public void chars(CharBuffer buffer) {
                sb.append(buffer.toString());
            }
            public void error(Throwable err) {
                future.completeExceptionally(err);
            }
            public void done() { 
                future.complete(sb.toString());
            }
        };
        
        CharsetPushDecoder decoder = new CharsetPushDecoder(
                Charset.forName("utf-8"), 32, decodeHandler);
        
        AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(
                Paths.get(UTF8_DEMO.getPath()),
                EnumSet.of(StandardOpenOption.READ),
                null);
        
        final ByteBuffer byteBuffer = ByteBuffer.allocate(256);
        
        CompletionHandler<Integer, ByteBuffer> handler = new CompletionHandler<Integer, ByteBuffer>() {
            private long pos = 0;
            @Override
            public synchronized void completed(Integer result, ByteBuffer attachment) {
                try {
                    attachment.flip();
                    decoder.push(attachment, result == -1);
                    
                    if (result == -1) {
                        fileChannel.close();
                        fileChannel.close();
                    }
                    else {
                        pos += result;
                        fileChannel.read(byteBuffer, pos, byteBuffer, this);
                    }
                } catch (IOException e) {
                    decodeHandler.error(e);
                }
            }
            @Override
            public void failed(Throwable e, ByteBuffer attachment) {
                decodeHandler.error(e);
            }
          };
       fileChannel.read(byteBuffer, 0, byteBuffer, handler);
       try {
           String content = future.get();
           String original = StreamUtil.streamToString(new FileInputStream(UTF8_DEMO));
           assertEquals(original, content);
       } 
       catch (Exception e) {
           throw new RuntimeException(e);
       }
    }
}
