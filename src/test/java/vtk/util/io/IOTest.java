/* Copyright (c) 2016 University of Oslo, Norway
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

import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * Thorough test-case for IO class.
 * 
 * TODO clean up test names and consolidate some test code
 */
public class IOTest {

    public IOTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testCopy_InputStream_OutputStream() throws Exception {
        String s = "data to copy";
        int len = s.getBytes().length;
        InputStream in = new ByteArrayInputStream(s.getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long copied = IO.copy(in, out).perform();
        String result = new String(out.toByteArray());
        assertEquals(s, result);
        assertEquals(len, copied);

        byte[] data = generateRandomDataBuffer(30000);
        in = new ByteArrayInputStream(data);
        out = new ByteArrayOutputStream();

        copied = IO.copy(in, out).perform();
        
        byte[] resultData = out.toByteArray();
        assertTrue(buffersEqual(data, resultData));
        assertEquals(data.length, copied);
        
    }
    
    // Don't run this one normally, since it requires a lot of memory for the
    // JVM executing it.
    @Ignore
    @Test(expected = IOException.class)
    public void streamLimit() throws Exception {
        IO.read(new DevZeroInputStream()).perform();
    }

    @Test
    public void testWrite() throws Exception {
        CheckForCloseByteArrayOutputStream out = new CheckForCloseByteArrayOutputStream();
        byte[] data = generateRandomDataBuffer(10000);

        IO.write(data, out).perform();

        assertTrue(buffersEqual(data, out.toByteArray()));
        assertTrue(out.isClosed());

        out = new CheckForCloseByteArrayOutputStream();
        data = "foobar".getBytes();

        IO.write(data, out).closeOut(false).perform();
        
        assertTrue(buffersEqual(data, out.toByteArray()));
        assertFalse(out.isClosed());
    }

    @Test
    public void testCopy_3args() throws Exception {
        String s = "data to copy";
        int len = s.getBytes().length;
        InputStream in = new ByteArrayInputStream(s.getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        long piped = IO.copy(in, out).bufferSize(1).perform();
        
        String result = new String(out.toByteArray());
        assertEquals(s, result);
        assertEquals(len, piped);

        byte[] data = generateRandomDataBuffer(10000);
        in = new ByteArrayInputStream(data);
        out = new ByteArrayOutputStream();
        
        piped = IO.copy(in, out).bufferSize(10).perform();

        byte[] resultData = out.toByteArray();
        assertTrue(buffersEqual(data, resultData));
        assertEquals(data.length, piped);

        data = generateRandomDataBuffer(5000);
        in = new SmallChunkInputStream(data);
        out = new CheckForCloseByteArrayOutputStream();

        piped = IO.copy(in, out).bufferSize(64).perform();

        resultData = out.toByteArray();
        assertTrue(buffersEqual(data, resultData));
        assertTrue(((SmallChunkInputStream)in).isClosed());
        assertTrue(((CheckForCloseByteArrayOutputStream)out).isClosed());
        assertEquals(data.length, piped);

        data = generateRandomDataBuffer(5000);
        in = new SmallChunkInputStream(data);
        out = new CheckForCloseByteArrayOutputStream();

        piped = IO.copy(in, out).bufferSize(64).closeOut(false).perform();

        resultData = out.toByteArray();
        assertTrue(buffersEqual(data, resultData));
        assertTrue(((SmallChunkInputStream)in).isClosed());
        assertFalse(((CheckForCloseByteArrayOutputStream)out).isClosed());
        assertEquals(data.length, piped);
        
        data = generateRandomDataBuffer(5000);
        in = new SmallChunkInputStream(data);
        out = new CheckForCloseByteArrayOutputStream();

        piped = IO.copy(in, out).bufferSize(64)
                                .closeOut(false)
                                .closeIn(false).perform();

        resultData = out.toByteArray();
        assertTrue(buffersEqual(data, resultData));
        assertFalse(((SmallChunkInputStream)in).isClosed());
        assertFalse(((CheckForCloseByteArrayOutputStream)out).isClosed());
        assertEquals(data.length, piped);
    }

    @Test
    public void testCopy_offset_limit() throws Exception {
        for (final int bufferSize : new int[]{1, 2, 3, 4, 8, 1024, 8192, 16384}) {
            {
                String s = "data to copy";
                InputStream in = new ByteArrayInputStream(s.getBytes());
                ByteArrayOutputStream out = new ByteArrayOutputStream();

                long copied = IO.copy(in, out).offset(2).limit(5).bufferSize(bufferSize).perform();

                String result = new String(out.toByteArray());
                assertEquals("Failed with buffer size: " + bufferSize, "ta to", result);
                assertEquals("Failed with buffer size: " + bufferSize, 5L, copied);
            }
            {
                // Test offset bigger than available bytes
                String s = "data to copy";
                InputStream in = new ByteArrayInputStream(s.getBytes());
                ByteArrayOutputStream out = new ByteArrayOutputStream();

                long copied = IO.copy(in, out).offset(100).bufferSize(bufferSize).perform();

                String result = new String(out.toByteArray());
                assertEquals("Failed too big offset with buffer size: " + bufferSize, "", result);
                assertEquals("Failed too big offset with buffer size: " + bufferSize, 0L, copied);
            }
        }
    }
    
    @Test
    public void testFileCopy_offset_limit() throws Exception {
        byte[] data = "one two three".getBytes();
        IO.TempFile source = null, dest = null;
        try {
            source = IO.tempFile(new ByteArrayInputStream(data)).perform();
            dest = IO.tempFile();

            IO.copy(source.inputStream(), dest.outputStream()).offset(4).limit(3).perform();
            String destString = IO.readString(dest.inputStream(), "utf-8").perform();
            assertEquals("two", destString);

            IO.copy(source.inputStream(), dest.outputStream()).offset(0).limit(3).perform();
            destString = IO.readString(dest.inputStream(), "utf-8").perform();
            assertEquals("one", destString);

            IO.copy(source.inputStream(), dest.outputStream()).offset(8).limit(5).perform();
            destString = IO.readString(dest.inputStream(), "utf-8").perform();
            assertEquals("three", destString);

            IO.copy(source.inputStream(), dest.outputStream()).offset(8).limit(-1).perform();
            destString = IO.readString(dest.inputStream(), "utf-8").perform();
            assertEquals("three", destString);

            IO.copy(source.inputStream(), dest.outputStream()).offset(8).perform();
            destString = IO.readString(dest.inputStream(), "utf-8").perform();
            assertEquals("three", destString);

            long count = IO.copy(source.inputStream(), dest.outputStream()).offset(100).perform();
            assertEquals(0, count);
        } finally {
            if (source != null) source.delete();
            if (dest != null) dest.delete();
        }

    }

    @Test
    public void test_read_InputStream() throws Exception {
        byte[] data = new byte[0];
        ByteArrayInputStream content = new ByteArrayInputStream(data);
        
        byte[] result = IO.read(content).perform();
        assertEquals(-1, content.read());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(1);
        content = new ByteArrayInputStream(data);
        
        result = IO.read(content).perform();
        assertEquals(-1, content.read());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(2);
        content = new ByteArrayInputStream(data);
        
        result = IO.read(content).perform();
        assertEquals(-1, content.read());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(1000);
        content = new ByteArrayInputStream(data);

        result = IO.read(content).perform();
        assertEquals(-1, content.read());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(8191);
        content = new ByteArrayInputStream(data);
        
        result = IO.read(content).perform();
        assertEquals(-1, content.read());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(12288);
        content = new ByteArrayInputStream(data);
        
        result = IO.read(content).perform();
        assertEquals(-1, content.read());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(100000);
        content = new ByteArrayInputStream(data);
        
        result = IO.read(content).perform();
        assertEquals(-1, content.read());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(30001);
        content = new SmallChunkInputStream(data);
        
        result = IO.read(content).perform();
        assertEquals(-1, content.read());
        assertTrue(((SmallChunkInputStream)content).isClosed());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(1000);
        content = new SmallChunkInputStream(data);
        
        result = IO.read(content).perform();
        assertTrue(((SmallChunkInputStream)content).isClosed());
        assertEquals(-1, content.read());
        assertTrue(buffersEqual(data, result));
    }

    @Test
    public void testReadInputStream_InputStream_int() throws Exception {
        byte[] data = new byte[0];
        ByteArrayInputStream content = new ByteArrayInputStream(data);
        byte[] result = IO.read(content).limit(0).perform();
        assertEquals(-1, content.read());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(1);
        content = new ByteArrayInputStream(data);
        result = IO.read(content).limit(100).perform();
        assertEquals(-1, content.read());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(1);
        content = new ByteArrayInputStream(data);
        result = IO.read(content).limit(-1).perform();
        assertEquals(-1, content.read());
        assertEquals(1, result.length);

        data = generateRandomDataBuffer(2);
        content = new ByteArrayInputStream(data);
        result = IO.read(content).limit(100).perform();
        assertEquals(-1, content.read());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(1000);
        content = new ByteArrayInputStream(data);
        result = IO.read(content).limit(8192).perform();
        assertEquals(-1, content.read());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(8191);
        content = new ByteArrayInputStream(data);
        result = IO.read(content).limit(8192).perform();
        assertEquals(-1, content.read());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(8192);
        content = new ByteArrayInputStream(data);
        result = IO.read(content).limit(8192).perform();
        assertEquals(-1, content.read());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(100000);
        content = new ByteArrayInputStream(data);
        result = IO.read(content).limit(1224).perform();
        assertEquals(1224, result.length);
        assertEquals(100000 - 1224, content.available());
        assertTrue(buffersEqual(Arrays.copyOf(data, 1224), result));

        data = generateRandomDataBuffer(1024);
        content = new ByteArrayInputStream(data);
        result = IO.read(content).limit(1).perform();
        assertEquals(1, result.length);
        assertEquals(1023, content.available());
        assertTrue(buffersEqual(Arrays.copyOf(data, 1), result));

        data = generateRandomDataBuffer(100000);
        content = new ByteArrayInputStream(data);
        result = IO.read(content).limit(0).perform();
        assertEquals(0, result.length);
        assertTrue(content.read() != -1);

        data = generateRandomDataBuffer(100000);
        content = new ByteArrayInputStream(data);
        result = IO.read(content).limit(Long.MAX_VALUE).perform();
        assertEquals(-1, content.read());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(9999);
        content = new ByteArrayInputStream(data);
        result = IO.read(content).limit(9999).perform();
        assertEquals(result.length, 9999);
        assertEquals(-1, content.read());
        assertTrue(buffersEqual(data, result));

        // Do some tests with slow input stream
        data = generateRandomDataBuffer(1000);
        content = new SmallChunkInputStream(data);
        result = IO.read(content).limit(9999).perform();
        assertTrue(((SmallChunkInputStream)content).isClosed());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(1024);
        content = new SmallChunkInputStream(data);
        result = IO.read(content).limit(1024).perform();
        assertTrue(((SmallChunkInputStream)content).isClosed());
        assertTrue(buffersEqual(data, result));

        data = generateRandomDataBuffer(100000);
        content = new SmallChunkInputStream(data);
        result = IO.read(content).limit(50000).perform();
        assertTrue(((SmallChunkInputStream)content).isClosed());
        assertEquals(50000, result.length);
        assertEquals(50000, content.available());
        assertTrue(buffersEqual(Arrays.copyOf(data, 50000), result));
    }

    @Test
    public void testStringStream_String_String() throws Exception {
        String s = "foobar æøå";
        String encoding = "UTF-8";
        byte[] data = IO.read(IO.stringStream(s, encoding)).perform();
        assertTrue(buffersEqual(data, s.getBytes(encoding)));
    }

    @Test
    public void testStreamString_InputStream_String() throws Exception {
        String s = "foobar ææå";
        ByteArrayInputStream in = new ByteArrayInputStream(s.getBytes("utf-8"));
        String result = IO.readString(in, "utf-8").perform();
        assertEquals(s, result);
    }
    
    @Test
    public void test_stream_to_TempFile() throws IOException {
        byte[] data = generateRandomDataBuffer(10*1024);

        IO.TempFile tmp = IO.tempFile(new ByteArrayInputStream(data)).perform();
        assertFalse(tmp.isTruncatedToSizeLimit());
        
        byte[] tmpData = IO.read(tmp.inputStream()).perform();
        assertTrue(buffersEqual(data, tmpData));
        
        tmp.delete();
        assertFalse(tmp.file().exists());
        try {
            tmp.inputStream();
            fail("Expected IOException on trying to get input stream of deleted file");
        } catch (IOException io) {
            // ok
        }
    }

    @Test
    public void test_stream_to_TempFile_with_limit() throws IOException {
        byte[] data = generateRandomDataBuffer(1000);
        IO.TempFile tmp = IO.tempFile(new ByteArrayInputStream(data)).limit(100).perform();
        assertTrue(tmp.isTruncatedToSizeLimit());
        assertEquals(100, tmp.file().length());
        
        byte[] tmpData = IO.read(tmp.inputStream()).perform();
        
        byte[] cappedData = new byte[100];
        System.arraycopy(tmpData, 0, cappedData, 0, 100);
        assertTrue(buffersEqual(cappedData, tmpData));
        tmp.delete();
        assertFalse(tmp.file().exists());
        
        data = new byte[2*1024*1024 + 100];
        tmp = IO.tempFile(new ByteArrayInputStream(data)).limit(data.length).perform();

        assertFalse(tmp.isTruncatedToSizeLimit());
        tmpData = IO.read(tmp.inputStream()).perform();
        assertTrue(buffersEqual(data, tmpData));
        assertEquals(2*1024*1024 + 100, tmp.file().length());
        tmp.delete();
        assertFalse(tmp.file().exists());

        data = new byte[1024];
        tmp = IO.tempFile(new ByteArrayInputStream(data)).limit(0).perform();
        assertTrue(tmp.isTruncatedToSizeLimit());
        assertEquals(0, tmp.file().length());
        tmp.delete();
        assertFalse(tmp.file().exists());

        data = new byte[1024];
        tmp = IO.tempFile(new SmallChunkInputStream(data)).limit(1024+1).perform();

        assertFalse(tmp.isTruncatedToSizeLimit());
        assertEquals(1024, tmp.file().length());
        tmpData = IO.read(tmp.inputStream()).perform();
        assertTrue(buffersEqual(data, tmpData));
        tmp.delete();
        assertFalse(tmp.file().exists());

        data = new byte[1024];
        tmp = IO.tempFile(new SmallChunkInputStream(data)).limit(1024-1).perform();
        assertTrue(tmp.isTruncatedToSizeLimit());
        assertEquals(1024 - 1, tmp.file().length());
        tmp.delete();
        assertFalse(tmp.file().exists());

        data = new byte[0];
        tmp = IO.tempFile(new SmallChunkInputStream(data)).limit(-1).perform();
        assertFalse(tmp.isTruncatedToSizeLimit());
        assertEquals(0, tmp.file().length());
        tmp.delete();
        assertFalse(tmp.file().exists());

        data = new byte[1];

        tmp = IO.tempFile(new SmallChunkInputStream(data)).limit(-1).perform();
        assertFalse(tmp.isTruncatedToSizeLimit());
        assertEquals(1, tmp.file().length());
        tmp.delete();
        assertFalse(tmp.file().exists());
    }
    
    // Triggers and tests use of NIO copy path
    @Test
    public void file_stream_copy() throws Exception {
        byte[] data = generateRandomDataBuffer(1000);
        IO.TempFile tempFile = IO.tempFile(new ByteArrayInputStream(data)).perform();
        File destFile = File.createTempFile("IOTest", null);
        
        long count = IO.copy(tempFile.inputStream(), new FileOutputStream(destFile)).perform();
        assertEquals(1000, count);

        byte[] destFileData = IO.read(new FileInputStream(destFile)).perform();
        assertTrue(buffersEqual(data, destFileData));
        tempFile.delete();
        destFile.delete();
        
        data = new byte[2*1024*1024 + 100];
        data[2*1024*1024] = 'X';

        tempFile = IO.tempFile(new ByteArrayInputStream(data)).perform();
        destFile = File.createTempFile("IOTest", null);
        count = IO.copy(tempFile.inputStream(), new FileOutputStream(destFile)).perform();
        assertEquals(2*1024*1024 + 100, count);

        destFileData = IO.read(new FileInputStream(destFile)).perform();
        assertTrue(buffersEqual(data, destFileData));
        tempFile.delete();
        destFile.delete();
    }
    
    @Test
    public void test_create_empty_TempFile() throws Exception {
        IO.TempFile tmp = IO.tempFile();
        assertTrue(tmp.file().getName().startsWith(IO.TEMP_FILE_PREFIX));
        tmp.delete();
        
        tmp = IO.tempFile(null, "foo");
        assertTrue(tmp.file().getName().startsWith(IO.TEMP_FILE_PREFIX));
        assertTrue(tmp.file().getName().endsWith("foo"));
        tmp.delete();
    }
    
    @Test
    public void test_progress_read() throws Exception {
        InputStream in = new ByteArrayInputStream(generateRandomDataBuffer(1000*1000));
        final List<Long> progressCallbacks = new ArrayList<>();
        IO.read(in).progress(p -> progressCallbacks.add(p)).progressInterval(256*1024).perform();
        
        assertTrue(progressCallbacks.size() > 0);
        assertEquals(new Long(1000*1000), progressCallbacks.get(progressCallbacks.size()-1));
    }
    
    @Test
    public void test_progress_copy() throws Exception {
        InputStream in = new ByteArrayInputStream(generateRandomDataBuffer(1000*1000));
        OutputStream out = new ByteArrayOutputStream();
        final List<Long> progressCallbacks = new ArrayList<>();
        IO.copy(in, out).progress(p -> progressCallbacks.add(p)).progressInterval(256*1024).perform();

        assertTrue(progressCallbacks.size() > 0);
        assertEquals(new Long(1000*1000), progressCallbacks.get(progressCallbacks.size()-1));
    }

    @Test
    public void test_progress_file_copy() throws Exception {
        IO.TempFile inTmp = IO.tempFile(new DevZeroInputStream()).limit(512*1024).perform();
        assertEquals(512*1024, inTmp.file().length());
        IO.TempFile outTmp = IO.tempFile();
        
        final List<Long> progressCallbacks = new ArrayList<>();
        IO.copy(inTmp.inputStream(), outTmp.outputStream())
                .progress(p -> progressCallbacks.add(p))
                .progressInterval(128*1024).perform();

        assertTrue(progressCallbacks.size() > 0);
        assertEquals(new Long(512*1024), progressCallbacks.get(progressCallbacks.size()-1));
        inTmp.delete();
        outTmp.delete();
    }
    
    @Test
    public void test_progress_write() throws Exception {
        byte[] data = generateRandomDataBuffer(512*1024);
        OutputStream out = new ByteArrayOutputStream();
        final List<Long> progressCallbacks = new ArrayList<>();
        IO.write(data, out).progress(p -> progressCallbacks.add(p)).progressInterval(128*1024).perform();

        assertTrue(progressCallbacks.size() > 0);
        assertEquals(new Long(512*1024), progressCallbacks.get(progressCallbacks.size()-1));
    }
    
    private boolean buffersEqual(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }

        for (int i=0; i<a.length; i++) {
            if (a[i] != b[i])  return false;
        }

        return true;
    }

    private byte[] generateRandomDataBuffer(int size) {
        byte[] data = new byte[size];

        for (int i=0; i<size; i++) {
            data[i] = (byte)((int)(Math.random()*0xFF));
        }

        return data;
    }
    
    // Input stream which provides an unlimited amount of zero bytes.
    private static class DevZeroInputStream extends InputStream {

        @Override
        public int read() throws IOException {
            return 0;
        }

    }
    
    // Simulate an input stream that only reads small chunks of random size at a time.
    // Also record if close() has been called.
    private static class SmallChunkInputStream extends ByteArrayInputStream {

        boolean closed = false;

        public SmallChunkInputStream(byte[] buffer) {
            super(buffer);
        }

        @Override
        public int read(byte[] buffer, int offset, int len) {
            return super.read(buffer, offset, Math.min((int) (Math.random() * 5 + 1), len));
        }

        @Override
        public void close() throws IOException {
            this.closed = true;
        }

        public boolean isClosed() {
            return this.closed;
        }
    }

    // Just record close() call
    class CheckForCloseByteArrayOutputStream extends ByteArrayOutputStream {

        private boolean closed = false;

        public CheckForCloseByteArrayOutputStream() {
            super();
        }

        @Override
        public void close() throws IOException {
            this.closed = true;
        }

        public boolean isClosed() {
            return this.closed;
        }
    }

}
