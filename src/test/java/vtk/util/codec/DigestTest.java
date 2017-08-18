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
package vtk.util.codec;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
import vtk.util.io.IO;
import vtk.util.text.TextUtils;

/**
 *
 */
public class DigestTest {

    private final byte[] testBytes = {0x61,0x62,0x63};
    private final String testBytesSha1Hex = "a9993e364706816aba3e25717850c26c9cd0d89d";
    private final String testBytesSha256Hex = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private final String testBytesSha256Base64 = "ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=";
    private final String testBytesMd5Hex = "900150983cd24fb0d6963f7d28e17f72";

    @Test
    public void sha1HexEmpty() {

        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", Digest.create("SHA-1").compute());

    }

    @Test
    public void sha1Hex() {

        assertEquals(testBytesSha1Hex, Digest.create("SHA-1").data(testBytes).compute());

    }

    @Test
    public void sha256Base64() {
        String b64 = Digest.create("SHA-256").data(testBytes)
                                        .format(Digest.Format.BASE64)
                                        .compute();

        assertEquals(testBytesSha256Base64, b64);

        byte[] shasumBytes = new org.apache.commons.codec.binary.Base64().decode(b64);

        assertEquals(new String(TextUtils.toHex(shasumBytes)), testBytesSha256Hex);

    }

    @Test
    public void sha256Hex() throws Exception {

        String hex = Digest.create("SHA-256").data(testBytes)
                                        .format(Digest.Format.ASCII_HEX)
                                        .compute();

        assertEquals(testBytesSha256Hex, hex);

        byte[] shasumBytes = new org.apache.commons.codec.binary.Hex()
                .decode(hex.getBytes(StandardCharsets.US_ASCII));

        assertEquals(256/8, shasumBytes.length);

        assertEquals(new org.apache.commons.codec.binary.Base64().encodeAsString(shasumBytes), testBytesSha256Base64);

    }

    @Test
    public void md5Hex() {
        assertEquals(testBytesMd5Hex, Digest.create("MD5").data(testBytes).compute());
    }

    @Test
    public void verify() {
        assertTrue(Digest.create("MD5").data(testBytes).verify(testBytesMd5Hex));
    }

    @Test
    public void streamWrapper() throws Exception {
        InputStream dataStream = new ByteArrayInputStream(testBytes);
        Digest.StreamWrapper wrapper = Digest.create("SHA-1").data(dataStream).wrapper();
        byte[] read = IO.read(wrapper).perform();
        assertTrue(Arrays.equals(read, testBytes));
        assertEquals(testBytesSha1Hex, wrapper.compute());
    }

    @Test(expected = IllegalArgumentException.class)
    public void indigestion() {
        Digest.create("FROBNICATOR-1").compute();
    }

}
