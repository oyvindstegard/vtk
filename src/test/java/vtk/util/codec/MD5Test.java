/* Copyright (c) 2008, University of Oslo, Norway
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

import java.io.ByteArrayInputStream;

import static org.junit.Assert.*;
import org.junit.Test;
import vtk.util.io.IO;
import vtk.util.io.NullOutputStream;


public class MD5Test {

    @Test
    public void md5sum() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", MD5.md5sum("abc"));
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", MD5.md5sum(""));
        assertEquals("37b51d194a7513e45b56f6524f2d51f2", MD5.md5sum("bar"));
        assertEquals("5db1de9f01225e0c9b89df9e1e96d240", MD5.md5sum("FOO bar"));
        assertEquals("69a8f8b65843545e07e264782a632e2b", 
                MD5.md5sum("\\%$powjegpokjewgi39i310ut31+iujas0gh+2r1+ut139gtuwpjgo"));
    }

    @Test
    public void streams() throws Exception {
        String s = "bar";
        byte[] buf = s.getBytes();
        MD5.MD5InputStream is = MD5.wrap(new ByteArrayInputStream(buf));
        IO.copy(is, NullOutputStream.INSTANCE).perform();
        
        String sum1 = MD5.md5sum(new ByteArrayInputStream(buf));
        String sum2 = MD5.md5sum(buf);
        String sum3 = MD5.md5sum(s);
        String sum4 = is.md5sum();
        
        assertEquals("37b51d194a7513e45b56f6524f2d51f2", sum1);
        assertEquals(sum1, sum2);
        assertEquals(sum2, sum3);
        assertEquals(sum3, sum4);
    }

}
