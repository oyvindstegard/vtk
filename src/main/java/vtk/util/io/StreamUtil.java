/* Copyright (c) 2004,2010,2014 University of Oslo, Norway
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;

/**
 * @deprecated Use {@link vtk.util.io.IO} instead.
 */
@Deprecated
public abstract class StreamUtil {

    public static final String TEMP_FILE_PREFIX = "StreamUtil-tmpfile";
    
    public static final int NIO_CHANNEL_CHUNK_SIZE = 4*1024*1024;
    
    /**
     * Reads an input stream into a byte array. Closes the stream
     * after the data has been read.
     * 
     * <p>Buffer allocations are kept to a minimum by using multiple read buffers as
     * needed and avoiding buffer copy+throw-away when growing. This provides better
     * performance mainly because of reduced copying and allocations. It creates
     * less garbage than the traditional ByteArrayOutputStream approach.
     *
     * <p>This method may require a lot of memory.
     * Care should be taken when the input stream can potentially be very large.
     * In any case, the limit to number of bytes this method can return is
     * {@link Integer#MAX_VALUE}, and during processing it may require up to
     * double this amount of JVM heap space.
     *
     * @param content an <code>InputStream</code> value
     * @return a <code>byte[]</code> containg the read data.
     *         Length is exactly the number of bytes read from the input stream.
     * @exception IOException if an error occurs or stream exceeds {@link Integer#MAX_VALUE max
     * number of bytes}
     */
    public static byte[] readInputStream(InputStream content)
        throws IOException {
        try {
            byte[][] buffers = new byte[10][];
            byte[] currentbuf = new byte[8192];
            buffers[0] = currentbuf;
            int n, pos = 0, total = 0, bufcount = 1;
            while ((n = content.read(currentbuf, pos, currentbuf.length - pos)) > 0) {
                if (total + n < total) {
                    throw new IOException("Stream exceeded maximum length of " + Integer.MAX_VALUE + " bytes.");
                }
                
                pos += n;
                total += n;
                if (pos == currentbuf.length) {
                    if (bufcount == buffers.length) {
                        // Allocate for more buffers
                        buffers = Arrays.copyOf(buffers, buffers.length << 1);
                    }
                    
                    // Double size of new buffer, but keep roof at 64MiB
                    byte[] newbuffer = new byte[Math.min(0x4000000, currentbuf.length << 1)];
                    buffers[bufcount++] = newbuffer;
                    currentbuf = newbuffer;
                    pos = 0;
                }
            }

            // Assemble allocated buffers to single properly sized return value
            final byte[] returnbuf = new byte[total];
            int remaining = total;
            for (int i=0; i<bufcount; i++) {
                byte[] buf = buffers[i];
                int copycount = Math.min(buf.length, remaining);
                System.arraycopy(buf, 0, returnbuf, total - remaining, copycount);
                remaining -= copycount;
                buffers[i] = null; // GC as soon as possible
            }

            return returnbuf;
        } finally {
            content.close();
        }
    }

    /**
     * Reads the first N bytes of an input stream into a byte
     * array. Closes the stream after the data has been read. No more than
     * <code>maxLength</code> bytes will be read from the input stream.
     *
     * @param content an <code>InputStream</code> value
     * @param maxLength the maximum number of bytes to read from the
     * stream.
     * @return a <code>byte[]</code> array. The length of the byte
     * array will be less than or equal to the <code>maxLength</code>
     * argument.
     * @exception IOException if an error occurs
     */
    public static byte[] readInputStream(InputStream content, int maxLength)
        throws IOException {
        try {
            if (maxLength <= 0) return new byte[0];

            byte[][] buffers = new byte[10][];
            byte[] currentbuf = new byte[8192];
            buffers[0] = currentbuf;
            int n, pos = 0, total = 0, bufcount = 1;
            int chunksize = Math.min(currentbuf.length, maxLength);
            while ((n = content.read(currentbuf, pos, chunksize)) > 0) {
                pos += n;
                total += n;

                if (total >= maxLength) {
                    total = maxLength;
                    break;
                }

                if (pos == currentbuf.length) {
                    if (bufcount == buffers.length) {
                        // Allocate for more buffers
                        buffers = Arrays.copyOf(buffers, buffers.length << 1);
                    }
                    // Double size of new buffer, but keep roof at 64MiB
                    byte[] newbuffer = new byte[Math.min(0x4000000, currentbuf.length << 1)];
                    buffers[bufcount++] = newbuffer;
                    currentbuf = newbuffer;
                    pos = 0;
                    chunksize = currentbuf.length;
                }

                if (chunksize + pos > currentbuf.length) {
                    // Truncate next read chunk to fit current buffer
                    chunksize = currentbuf.length - pos;
                }

                // Truncate next read chunk to remaining bytes if we're nearing maxLength
                chunksize = Math.min(chunksize, maxLength - total);
            }

            // Assemble allocated buffers to single properly sized return value
            final byte[] returnbuf = new byte[total];
            int remaining = total;
            for (int i=0; i<bufcount; i++) {
                byte[] buf = buffers[i];
                int copycount = Math.min(buf.length, remaining);
                System.arraycopy(buf, 0, returnbuf, total - remaining, copycount);
                remaining -= copycount;
                buffers[i] = null; // GC as soon as possible
            }

            return returnbuf;
        } finally {
            content.close();
        }
    }

    /**
     * Buffered copy using a default buffer size. The input stream is closed,
     * while the output stream remains open after completion.
     * 
     * @see #pipe(java.io.InputStream, java.io.OutputStream, int)
     */
    public static long pipe(InputStream in, OutputStream out)
        throws IOException {
        return pipe(in, out, 4096, false);
    }

    /**
     * Like {@link #pipe(InputStream, OutputStream, int, boolean)}, but start copying 
     * at an offset and limit to a provided number of bytes.
     * @param in the stream to copy
     * @param out the destination
     * @param offset the position to start copying from
     * @param limit the maximum number of bytes to copy
     * @param bufferSize the buffer size
     * @param closeOutput whether to close output stream after copying
     * @return
     * @throws IOException
     */
    public static long pipe(InputStream in, OutputStream out, long offset, long limit,
            final int bufferSize, final boolean closeOutput) throws IOException {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be > 0");
        }
        if (offset < 0 ) {
            throw new IllegalArgumentException("Offset must be >= 0");
        }
        if (limit <= 0 ) {
            throw new IllegalArgumentException("Limit must be > 0");
        }

        try {
            byte[] buffer = new byte[bufferSize];
            if (offset > 0) {
                long skipped = in.skip(offset);
                if (skipped != offset) {
                    throw new IOException("Unable to skip to offset: " + offset);
                }
            }
            long count = 0;
            int n;
            while ((n = in.read(buffer, 0, buffer.length)) > 0) {
                if (count + n >= limit) {
                    n = (int) (limit - count);
                    out.write(buffer, 0, n);
                    count += n;
                    break;
                }
                out.write(buffer, 0, n);
                count += n;
            }
            return count;
        } finally {
            in.close();
            if (closeOutput) {
                out.close();
            }
        }
        
    }
    
    /**
     * Buffered copy of <em>all</em> available data from an input stream to an output stream.
     * The operation stops when either of the following conditions occur:
     * 1. No more data can be read from the input stream (EOF).
     * 2. An exception occurs, either when reading the input stream or writing to the
     *    output stream.
     *
     * The input stream is always closed upon completion or exception.
     * The output stream can be optionally closed under the same conditions
     * if argument closeOutput is true.
     *
     * @param in
     * @param bufferSize
     * @param out
     * @return The total number of bytes piped from the input stream to the output stream.
     * @throws IOException
     */
    public static long pipe(InputStream in, OutputStream out, final int bufferSize, final boolean closeOutput)
        throws IOException {

        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be > 0");
        }
        
        try {
            byte[] buffer = new byte[bufferSize];
            long count = 0;
            int n;
            while ((n = in.read(buffer, 0, buffer.length)) > 0) {
                out.write(buffer, 0, n);
                count += n;
            }
            return count;
        } finally {
            in.close();
            if (closeOutput) {
                out.close();
            }
        }
    }
    
    /**
     * Optimized copy for file streams using java.nio channels. If both
     * input stream and output stream are actually file streams, then this
     * method should be faster than {@link #pipe(java.io.InputStream, java.io.OutputStream, int, boolean) }.
     * 
     * @return number of bytes transferred.
     * 
     * @see #pipe(java.io.InputStream, java.io.OutputStream, int, boolean) 
     */
    public static long fileStreamCopy(FileInputStream in, FileOutputStream out, boolean closeOutput) throws IOException {
        FileChannel src = in.getChannel();
        FileChannel dst = out.getChannel();
        long pos = 0;
        long n;
        try {
            while ((n = dst.transferFrom(src, pos, NIO_CHANNEL_CHUNK_SIZE)) > 0) {
                pos += n;
            }
        } finally {
            src.close();
            if (closeOutput) {
                dst.close();
            }
        }
        return pos;
    }

    /**
     * Dump all data in buffer to output stream. Optionally close output stream
     * when all data has been written.
     *
     * @param data
     * @param out
     * @param closeOutput
     * @throws IOException
     */
    public static void dump(byte[] data, OutputStream out, boolean closeOutput)
        throws IOException {
        try {
            out.write(data, 0, data.length);
        } finally {
            if (closeOutput) {
                out.close();
            }
        }
    }


    /**
     * Make an input stream from the given string using the given
     * character encoding.
     * @param s
     * @param encoding
     * @return
     * @throws IOException
     */
    public static InputStream stringToStream(String s, String encoding) throws IOException {
        return new ByteArrayInputStream(s.getBytes(encoding));
    }

    /**
     * Make in input stream from the given string using the system defeault
     * character encoding.
     * @param s
     * @return
     * @throws IOException
     */
    public static InputStream stringToStream(String s) throws IOException {
        return stringToStream(s, System.getProperty("file.encoding"));
    }

    /**
     * Read input stream and return a <code>String</code> using the given
     * character encoding.
     *
     * The input stream is closed.
     * 
     * @param in
     * @param encoding
     * @return
     * @throws IOException
     */
    public static String streamToString(InputStream in, String encoding) throws IOException {
        return new String(readInputStream(in), encoding);
    }
    
    /**
     * Read input stream and return a <code>String</code> using the system
     * default character encoding.
     *
     * The input stream is closed.
     *
     * @param in
     * @param encoding
     * @return
     * @throws IOException
     */
    public static String streamToString(InputStream in) throws IOException {
        return streamToString(in, System.getProperty("file.encoding"));
    }
    
    /**
     * Creates a temporary file from an input stream. The input stream
     * is read to a temporary file in its entirety, and is then closed. An instance of
     * {@link TempFile} is returned if no I/O error occurs. Generally, the temporary
     * file should only be used for reading, and it should be deleted when
     * no longer needed.
     * 
     * Temporary file is created in the system default location.
     * 
     * @param stream the input stream to write to a temporary file.
     * @return An instance of {@link TempFile} which can be used to get access to the
     *         underlying {@link File}. Note that you cannot rely on persistence of
     *         this file, since it will be deleted when the <code>TempFile</code> instance
     *         is garbage collected.
     * 
     * @throws IOException if an I/O error occurs
     */
    public static TempFile streamToTempFile(InputStream stream) throws IOException {
        return streamToTempFile(stream, null);
    }
    
    /**
     * Like {@link #streamToTempFile(java.io.InputStream), but with possibilty
     * to explicity set directory where temporary file should be created,
     * instead of using the system default.
     * 
     * @param stream the input stream to write to a temporary file.
     * @param tempDir temporary directory to store the file in. May be <code>null</code> for system default
     *                directory (value of system property <code>"java.io.tmpdir"</code>).
     * @return An instance of {@link TempFile} which can be used to get access to the
     *         underlying {@link File}. Note that you cannot rely on persistence of
     *         this file, since it will be deleted when the <code>TempFile</code> instance
     *         is garbage collected.
     * 
     * @throws IOException if an I/O error occurs
     */
    public static TempFile streamToTempFile(InputStream stream, File tempDir) throws IOException {
        return streamToTempFile(stream, -1, tempDir, null);
    }

    /**
     * Like {@link #streamToTempFile(java.io.InputStream,java.io.File)}, but with
     * added option of setting a maximum limit on size.
     */
    public static TempFile streamToTempFile(InputStream stream, final long sizeLimit, File tempDir) throws IOException {
        return streamToTempFile(stream, sizeLimit, tempDir, null);
    }
    
    /**
     * Like {@link #streamToTempFile(java.io.InputStream,long,java.io.File) }, but
     * with an additional option of controlling the suffix of the generated file name.
     * 
     * @param stream the input stream to write to a temporary file.
     * @param tempDir temporary directory to store the file in. May be <code>null</code> for system default
     *                directory (value of system property <code>"java.io.tmpdir"</code>).
     * @param sizeLimit limit on number of bytes to store in temporary file. If
     *                  the input stream contains <em>more</em> bytes than limit, the temporary
     *                  file will be truncated to limit. Reading input stream stops
     *                  when limit is reached. To check for this condition from client code, you
     *                  can use {@link TempFile#isTruncatedToSizeLimit() }.
     * 
     *                  Note that the input stream is closed both when there is nothing
     *                  more to read and if the size limit is reached.
     * 
     *                  A limit of &lt;= -1 means no limit and is equivalent to calling
     *                  {@link #streamToTempFile(java.io.InputStream) }.
     * @param suffix    File name suffix for created temporary file.
     * 
     * @see #streamToTempFile(java.io.InputStream) 
     */
    public static TempFile streamToTempFile(InputStream stream, final long sizeLimit, File tempDir, String suffix) throws IOException {
        if (tempDir == null) {
            tempDir = new File(System.getProperty("java.io.tmpdir"));
        }
        ReadableByteChannel src = Channels.newChannel(stream);
        File tempFile = File.createTempFile(TEMP_FILE_PREFIX, suffix, tempDir);
        FileChannel dest = new FileOutputStream(tempFile).getChannel();
        long pos = 0;
        try {
            while (true) {
                long n = dest.transferFrom(src, pos, NIO_CHANNEL_CHUNK_SIZE);
                if (n == 0) {
                    break;
                }
                pos += n;

                if (sizeLimit > -1 && pos > sizeLimit) {
                    dest.truncate(sizeLimit);
                    return new TempFile(tempFile, true);
                }
            }
        } finally {
            src.close();
            dest.close();
        }
        
        return new TempFile(tempFile, false);
    }
    
    /**
     * A thin holder class for a temporary file on local file system. Note that temporary
     * files will be deleted when instances of this class are finalized, so
     * <em>do not rely on persistence</em>.
     */
    public static final class TempFile {
        private File file;
        private boolean truncated = false;
        
        private TempFile(File file, boolean truncated) {
            this.file = file;
            this.truncated = truncated;
        }
        
        /**
         * Get the {@link File} object for the temporary file.
         * @return 
         */
        public File getFile() {
            return this.file;
        }
        
        /**
         * Returns <code>true</code> if the temporary file is truncated to a size limit.
         * A temporary file may be truncated if it was created with a size limit, and the
         * input stream source contained more bytes than that limit.
         */
        public boolean isTruncatedToSizeLimit() {
            return this.truncated;
        }
        
        /**
         * Get the input stream of this temporary file.
         * @return an input stream backed by the temporary file.
         */
        public FileInputStream getFileInputStream() throws IOException {
            return new FileInputStream(this.file);
        }
        
        /**
         * Calls method {@link File#delete() } on underlying file object if
         * file still exists.
         */
        public void delete() {
            if (this.file.exists()) {
                this.file.delete();
            }
        }
        
        @Override
        protected void finalize() {
            delete();
        }
        
        @Override
        public String toString() {
            return StreamUtil.TempFile.class.getSimpleName() + "[file = " + this.file + ", truncated = " + this.truncated + "]";
        }
    }

}
