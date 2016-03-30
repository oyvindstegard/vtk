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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Input/output tool.
 */
public abstract class IO {

    public static final String TEMP_FILE_PREFIX = "VTKIO-";
    
    public static final int DEFAULT_BUFFER_SIZE = 8192;
    
    public static final int DEFAULT_PROGRESS_INTERVAL = 1024*1024;
    
    public static final int NIO_CHANNEL_CHUNK_SIZE = 4*1024*1024;
    
    /**
     * An I/O operation which reads from a source and returns a result, typically
     * representing the source data in memory.
     * 
     * <p>Attributes of the operation may be set using the provided methods, before
     * a call to {@link Read#perform() }, which starts the operation.
     * 
     * @param <I> The source type
     * @param <R> The result type
     */
    public interface Read<I,R> {
        /**
         * Access the source directly.
         * @return the source
         */
        I in();
        
        /**
         * Attach a progress callback to the I/O operation, which should
         * accept a single parameter of type <code>Long</code> and return no value.
         * 
         * <p>In general, the callback will be called one or more times during the I/O operation
         * with number of bytes read thus far. You should not do any heavy work in
         * this callback, as it can slow down the thread performing the I/O operation.
         * How often it is called depends on {@link #progressInterval(int) } and
         * internal buffering, <em>but the only guarantee is that it will be called at least once</em>.
         * 
         * @param progressCallback a callback following the functional {@link Consumer} interface.
         * @return the <code>Read</code> instance
         */
        Read<I,R> progress(Consumer<Long> progressCallback);
        
        /**
         * Set minimum number of bytes read between each progress callback call.
         * <p>
         * The set value will not guarantee a specific number of calls based on
         * total number of bytes read, but it can be used to control the rate of
         * calls so it does not occur more often than desireable.
         * 
         * <p>The default value is {@link IO#DEFAULT_PROGRESS_INTERVAL}.
         *
         * @param interval minimum number of bytes between progress callback calls, must be 1 or greater
         * @return the <code>Read</code> instance
         */
        Read<I,R> progressInterval(int interval);
        
        /**
         * Limit how many bytes to read from the source.
         * 
         * @param limit the limit in number of bytes, negative means no limit.
         * @return the <code>Read</code> instance
         */
        Read<I,R> limit(long limit);
        
        /**
         * Set whether source should be closed after completion of I/O operation.
         * 
         * <p>Default is <code>true</code> &ndash; close source after completion.
         * 
         * <p>Only applicable to sources that are actually of closeable types.
         * 
         * @param closeIn
         * @return the <code>Read</code> instance
         */
        Read<I,R> closeIn(boolean closeIn);

        /**
         * Actually perform the I/O operation, returning the result.
         * 
         * <p>Calling this method multiple times leads to undefined results
         * and will likely result in an <code>IOException</code>, depending on the types involved.
         * 
         * @return An instance of the result type
         * @throws IOException If error occurs
         */
        R perform() throws IOException;
    }
    
    /**
     * An I/O operation which reads from a source and writes to a destination, possibly
     * using intermediate buffering.
     * 
     * <p>Attributes of the operation may be set using the provided methods, before
     * a call to {@link Copy#perform() }, which starts the operation.
     * 
     * @param <I> The source type from which bytes will be read
     * @param <O> The destinatinon type to which bytes will be written
     */
    public interface Copy<I,O> {
        /**
         * Access the source directly.
         * @return the source
         */
        I in();
        
        /**
         * Access the destination directly.
         * @return the destination
         */
        O out();
        
        /**
         * Attach a progress callback to the I/O operation, which should
         * accept a single parameter of type <code>Long</code> and return no value.
         * 
         * <p>
         * In general, the callback will be called one or more times during the
         * I/O operation with number of bytes copied thus far. You should not do
         * any heavy work in this callback, as it can slow down the thread
         * performing the I/O operation. How often it is called depends on {@link #progressInterval(int)
         * } and internal buffering, <em>but the only guarantee is that it will be
         * called at least once.</em>
         *
* 
         * @param progressCallback a callback following the functional {@link Consumer} interface.
         * @return the <code>Copy</code> operation instance
         */
        Copy<I,O> progress(Consumer<Long> progressCallback);
        
        /**
         * Set minimum number of bytes copied between each progress callback call.
         * <p>
         * The set value will not guarantee a specific number of calls based on
         * total number of bytes copied, but it can be used to control the rate of
         * calls so it does not occur more often than desireable.
         * 
         * <p>The default value is {@link IO#DEFAULT_PROGRESS_INTERVAL}.
         *
         * @param interval minimum number of bytes between progress callback calls, must be 1 or greater
         * @return the <code>Copy</code> operation instance
         */
        Copy<I,O> progressInterval(int interval);
        
        /**
         * Limit how many bytes to copy.
         * 
         * <p>Default is no limit.
         * 
         * @param limit the limit in number of bytes, negative or 0 means no limit.
         * @return the <code>Copy</code> operation instance
         */
        Copy<I,O> limit(long limit);
        
        /**
         * Set where to start copying from the source, as a zero based byte offset.
         * 
         * <p>If amount of source bytes is less than provided offset, then nothing (zero bytes)
         * will be copied to the destination.
         * 
         * <p>Default is 0
         * 
         * @param offset an offset value which must be greater than or equal to zero.
         * @return the <code>Copy</code> operation instance
         */
        Copy<I,O> offset(long offset);

        /**
         * Suggest a custom buffering size for the operation.
         * 
         * <p>Default value is {@link IO#DEFAULT_BUFFER_SIZE}
         * 
         * <p>Depending on the actual types involved, this may or may not make
         * a difference, and some operation implementations will ignore this value.
         * @param size the buffering size in number of bytes
         * @return the <code>Copy</code> operation instance
         */
        Copy<I,O> bufferSize(int size);

        /**
         * Set whether source should be closed after completion of I/O operation.
         * 
         * <p>Default is <code>true</code> &ndash; close source after completion.
         * 
         * <p>Only applicable to sources that are actually of closeable types.
         * @param closeIn <code>true</code> to close input
         * @return the <code>Copy</code> operation instance
         */
        Copy<I,O> closeIn(boolean closeIn);

        /**
         * Set whether destination should be closed after completion of I/O operation.
         * 
         * <p>Default is <code>true</code> &ndash; close destination after completion.
         * 
         * <p>Only applicable to destinations that are actually of closeable types.
         * 
         * @param closeOut <code>true</code> to close output
         * @return the <code>Copy</code> operation instance
         */
        Copy<I,O> closeOut(boolean closeOut);

        /**
         * Actually perform the I/O operation, returning the number of bytes transferred.
         * 
         * <p>Calling this method multiple times leads to undefined results
         * and will likely result in an <code>IOException</code>, depending on
         * the types involved.
         * 
         * @return number of bytes transferred
         * @throws IOException If error occurs
         */
        long perform() throws IOException;
    }
    
    private static abstract class ReadBase<I,R> implements Read<I,R> {
        
        protected long limit = -1;
        protected boolean closeIn = true;
        protected Consumer<Long> progress = p -> {};
        protected final I input;
        protected int progressInterval = DEFAULT_PROGRESS_INTERVAL;
        
        protected ReadBase(I input) {
            this.input = input;
        }

        @Override
        public I in() {
            return input;
        }

        @Override
        public Read<I,R> progress(Consumer<Long> progressCallback) {
            if (progressCallback == null) {
                throw new IllegalArgumentException("progressCallback cannot be null");
            }
            
            this.progress = progressCallback;
            return this;
        }
        
        @Override
        public Read<I,R> progressInterval(int interval) {
            if (interval <= 0) {
                throw new IllegalArgumentException("progress callback interval must be >= 1");
            }
            this.progressInterval = interval;
            return this;
        }
        
        @Override
        public Read<I,R> limit(long limit) {
            this.limit = limit;
            return this;
        }

        @Override
        public Read<I,R> closeIn(boolean closeIn) {
            this.closeIn = closeIn;
            return this;
        }
        
        protected byte[] readInputStream(InputStream in) throws IOException {
            // External API is long-based, translate safely to int here
            int limit = this.limit > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)this.limit;
            
            try {
                byte[][] buffers = new byte[10][];
                byte[] currentbuf = new byte[8192];
                buffers[0] = currentbuf;
                int n, pos = 0, total = 0, bufcount = 1;
                long lastProgressCallback = 0;
                int chunksize = limit >= 0 ? Math.min(limit, currentbuf.length) : currentbuf.length;
                while ((n = in.read(currentbuf, pos, chunksize)) > 0) {
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
                    
                    chunksize = limit >= 0 ? Math.min(limit-total, currentbuf.length - pos) : currentbuf.length - pos;
                    
                    if (total - lastProgressCallback >= progressInterval) {
                        progress.accept((long)total);
                        lastProgressCallback = total;
                    }
                }

                // Assemble allocated buffers to single properly sized return value
                final byte[] returnbuf = new byte[total];
                int remaining = total;
                for (int i = 0; i < bufcount; i++) {
                    byte[] buf = buffers[i];
                    int copycount = Math.min(buf.length, remaining);
                    System.arraycopy(buf, 0, returnbuf, total - remaining, copycount);
                    remaining -= copycount;
                    buffers[i] = null; // GC as soon as possible
                }

                progress.accept((long)total);
                return returnbuf;
            } finally {
                if (closeIn) in.close();
            }
        }
    }
    
    private static abstract class CopyBase<I,O> implements Copy<I,O> {
        protected I input;
        protected O output;
        protected int bufferSize = DEFAULT_BUFFER_SIZE;
        protected long offset = 0, limit = -1;
        protected boolean closeIn = true, closeOut = true;
        protected Consumer<Long> progress = p -> {};
        protected int progressInterval = DEFAULT_PROGRESS_INTERVAL;
        
        protected CopyBase(I input, O output) {
            this.input = input;
            this.output = output;
        }

        @Override
        public I in() {
            return this.input;
        }

        @Override
        public O out() {
            return this.output;
        }
        
        @Override
        public Copy<I, O> progress(Consumer<Long> progressCallback) {
            this.progress = progressCallback;
            return this;
        }

        @Override
        public Copy<I, O> progressInterval(int interval) {
            if (interval <= 0) {
                throw new IllegalArgumentException("progress callback interval must be >= 1");
            }
            this.progressInterval = interval;
            return this;
        }

        @Override
        public Copy<I, O> limit(long limit) {
            this.limit = limit;
            return this;
        }

        @Override
        public Copy<I, O> offset(long offset) {
            if (offset < 0) {
                throw new IllegalArgumentException("Offset must be >= 0");
            }
            this.offset = offset;
            return this;
        }

        @Override
        public Copy<I, O> bufferSize(int size) {
            if (bufferSize <= 0) {
                throw new IllegalArgumentException("Buffer size must be > 0");
            }
            this.bufferSize = size;
            return this;
        }

        @Override
        public Copy<I, O> closeOut(boolean closeOut) {
            this.closeOut = closeOut;
            return this;
        }

        @Override
        public Copy<I, O> closeIn(boolean closeIn) {
            this.closeIn = closeIn;
            return this;
        }
        
        protected long copy(InputStream in, OutputStream out) throws IOException {
            if (in instanceof FileInputStream
                    && in.getClass().equals(FileInputStream.class)
                    && out instanceof FileOutputStream
                    && out.getClass().equals(FileOutputStream.class)) {
                
                return nioFileCopy(((FileInputStream)in).getChannel(), ((FileOutputStream)out).getChannel());
            }
            
            try {
                byte[] buffer = new byte[bufferSize];
                if (offset > 0) {
                    in.skip(offset);
                }

                Function<Long,Integer> chunkSize = c -> limit >= 0 ?
                            (int)Math.min((long)bufferSize, limit-c) : bufferSize;
                long count = 0, lastProgressCallback = 0;
                int n;
                while ((n = in.read(buffer, 0, chunkSize.apply(count))) > 0) {
                    out.write(buffer, 0, n);
                    count += n;
                    
                    if (count-lastProgressCallback >= progressInterval) {
                        progress.accept(count);
                        lastProgressCallback = count;
                    }
                }
                progress.accept(count);
                return count;
            } finally {
                if (closeIn) {
                    in.close();
                }
                if (closeOut) {
                    out.close();
                }
            }
        }
        
        private long nioFileCopy(FileChannel src, FileChannel dst) throws IOException {
            long pos = offset;
            long written = 0, lastProgressCallback = 0;
            long n;
            Function<Long,Long> chunkSize = w -> Math.min(
                            limit >= 0 ? Math.max(0, limit-w) : NIO_CHANNEL_CHUNK_SIZE,
                            NIO_CHANNEL_CHUNK_SIZE);

            try {
                while ((n = src.transferTo(pos, chunkSize.apply(written), dst)) > 0) {
                    pos += n;
                    written += n;
                    if (written - lastProgressCallback >= progressInterval) {
                        progress.accept(written);
                        lastProgressCallback = written;
                    }
                }
                progress.accept(written);
                return written;
            } finally {
                if (closeIn) {
                    src.close();
                }
                if (closeOut) {
                    dst.close();
                }
            }
        }
    }

    /**
     * Read data from an input stream into memory.
     * 
     * <p>This method may require a lot of memory.
     * Care should be taken when the input stream can potentially be very large, by
     * using the {@link Read#limit(int) } attribute.
     * 
     * @param is the stream to read from
     * @return an instance of <code>Read&lt;InputStream,byte[]&gt;</code>
     * which can be used to adjust additional settings and
     * perform the operation.
     */
    public static Read<InputStream,byte[]> read(InputStream is) {
        return new ReadBase<InputStream,byte[]>(is) {
            @Override
            public byte[] perform() throws IOException {
                return super.readInputStream(super.input);
            }
        };
    }

    /**
     * Copy data from an input stream to an output stream with buffering.
     * 
     * @param in the input stream to read from
     * @param out the output stream to write to
     * @return an instance of Copy&lt;InputStream,OutputStream&gt; which
     * can be used to adjust additional settings and perform the operation.
     */
    public static Copy<InputStream,OutputStream> copy(InputStream in, OutputStream out) {
        return new CopyBase<InputStream,OutputStream>(in, out) {
            @Override
            public long perform() throws IOException {
                return copy(in, out);
            }
        };
    }
    
    /**
     * Write data from memory to an output stream.
     * 
     * <p>This operation does not buffer any data, because the data is already
     * in memory. Additional buffering depends on the provided output stream.
     * 
     * @param data the bytes to write
     * @param out the output stream to write to
     * @return an instance of Copy&lt;byte[], OutputStream&gt; which can be used
     * to adjust additional settings and perform the operation.
     */
    public static Copy<byte[],OutputStream> write(byte[] data, OutputStream out) {
        return new CopyBase<byte[],OutputStream>(data, out) {
            @Override
            public Copy<byte[], OutputStream> offset(long offset) {
                if (offset >= data.length) {
                    throw new IllegalArgumentException("Offset cannot be larger than size of input");
                }
                return super.offset(offset);
            }

            @Override
            public long perform() throws IOException {
                try {
                    int total = 0;
                    int offset = (int) super.offset;
                    int limit = super.limit > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)super.limit;
                    int length = limit >= 0 ? Math.min(input.length-offset, limit) : input.length-offset;
                    long lastProgressCallback = 0;
                    while (total < length) {
                        // We want chunk size as big as possible here, but it should not be bigger than
                        // requested progressInterval
                        int chunksize = Math.min(progressInterval, length - total);
                        output.write(input, offset+total, chunksize);
                        total += chunksize;
                        
                        if (total - lastProgressCallback >= progressInterval) {
                            progress.accept((long)total);
                            lastProgressCallback = total;
                        }
                    }
                    progress.accept((long)total);
                    return total;
                } finally {
                    if (closeOut) {
                        output.close();
                    }
                }
            }
        };
    }

    /**
     * Make an input stream from the given string using the given
     * character encoding.
     * @param s the string
     * @param encoding a string describing the encoding system
     * @return an input stream from which the encoded string can be read
     * @throws IOException
     */
    public static InputStream stringStream(String s, String encoding) throws IOException {
        return new ByteArrayInputStream(s.getBytes(encoding));
    }

    /**
     * Make in input stream from the given string using the system defeault
     * character encoding.
     * @param s the string
     * @return an input stream from which the encoded string can be read
     * @throws IOException
     */
    public static InputStream stringStream(String s) throws IOException {
        return stringStream(s, System.getProperty("file.encoding"));
    }

    /**
     * Read input stream and make a <code>String</code> using the given
     * character encoding.
     *
     * @param in the input stream
     * @param encoding the encoding
     * @return an instance of Read&lt;InputStream,String&gt; which can be used
     * to adjust additional settings and perform the operation.
     */
    public static Read<InputStream,String> readString(InputStream in, String encoding) {
        return new ReadBase<InputStream,String>(in) {
            @Override
            public String perform() throws IOException {
                byte[] bytes = super.readInputStream(input);
                return new String(bytes, encoding);
            }
        };
    }
    
    /**
     * Read input stream and return a <code>String</code> using the system
     * default character encoding.
     *
     * The input stream is closed.
     *
     * @param in
     * @return an instance of Read&lt;InputStream,String&gt; which can be used
     * to adjust additional settings and perform the operation.
     */
    public static Read<InputStream,String> readString(InputStream in) {
        return readString(in, System.getProperty("file.encoding"));
    }
    
    /**
     * Create an empty {@link TempFile} in the system default temporary file directory.
     * @return a {@link TempFile} instance, which can be used to store temporary data
     * @throws java.io.IOException in case of error creating the temporary file
     */
    public static TempFile tempFile() throws IOException {
        return tempFile((File)null,(String)null);
    }
    
    /**
     * Create an empty {@link TempFile}.
     * @param tempDir directory in which to create the file, or <code>null</code> for system default
     * @param suffix file name suffix, or <code>null</code> for default
     * @return a {@link TempFile} instance, which can be used to store temporary data
     * @throws java.io.IOException in case of error creating the temporary file
     */
    public static TempFile tempFile(File tempDir, String suffix) throws IOException {
        File tempFile = File.createTempFile(TEMP_FILE_PREFIX, suffix, tempDir);
        return new TempFile(tempFile, false);
    }
    
    /**
     * Read input stream to a {@link TempFile}.
     * 
     * <p>Uses system default temporary directory.
     *
     * @param in the input stream to read from
     * @return an instance of Read&lt;InputStream,TempFile&gt; which can be used
     * to adjust additional settings and perform the operation.
     */
    public static Read<InputStream,TempFile> tempFile(InputStream in) {
        return tempFile(in, null, null);
    }
    
    /**
     * Read input stream to a {@link TempFile}.
     * 
     * <p>Uses the provided temporary directory.
     *
     * @param in the input stream to read from
     * @param tempDir the temporary directory to use for the temp file
     * @return an instance of Read&lt;InputStream,TempFile&gt; which can be used
     * to adjust additional settings and perform the operation.
     */
    public static Read<InputStream,TempFile> tempFile(InputStream in, File tempDir) {
        return tempFile(in, tempDir, null);
    }
    
    /**
     * Read input stream to a {@link TempFile}.
     * 
     * <p>Uses the provided temporary directory and suffix.
     *
     * @param in the input stream to read from
     * @param tempDir the temporary directory to use for the temp file
     * @param suffix the suffix to use for the temporary file name
     * @return an instance of Read&lt;InputStream,TempFile&gt; which can be used
     * to adjust additional settings and perform the operation.
     */
    public static Read<InputStream,TempFile> tempFile(InputStream in, final File tempDir, final String suffix) {
        return new ReadBase<InputStream,TempFile>(in) {
            @Override
            public TempFile perform() throws IOException {
                File backingFile = File.createTempFile(TEMP_FILE_PREFIX, suffix, tempDir);
                OutputStream out = new FileOutputStream(backingFile);
                boolean truncated = false;
                if (limit >= 0) {
                    long written = copy(in, out).closeIn(false).limit(limit).progress(progress).perform();
                    if (written == limit && in.markSupported()) {
                        in.mark(1);
                        if (in.read() > -1) {
                            truncated = true;
                            in.reset();
                        }
                    }
                } else {
                    copy(in, out).closeIn(false).progress(progress).perform();
                }
                if (closeIn) {
                    in.close();
                }
                return new TempFile(backingFile, truncated);
            }
        };
    }
    
    /**
     * A holder class for a temporary file on local file system. Note that temporary
     * files will be deleted when instances of this class are finalized, so
     * <em>do not rely on persistence</em> after you have discarded the reference
     * to the temporary file.
     */
    public static final class TempFile {
        private final File file;
        private final boolean truncated;
        
        private TempFile(File file, boolean truncated) {
            this.file = file;
            this.truncated = truncated;
        }
        
        /**
         * Get the {@link File} object for the temporary file.
         * @return 
         */
        public File file() {
            return file;
        }
        
        /**
         * <p>A temporary file may be truncated if it was created with a size limit, and the
         * source contained more bytes than that limit.
         * @return <code>true</code> if the temporary file was truncated to a size limit when it was created.
         */
        public boolean isTruncatedToSizeLimit() {
            return truncated;
        }
        
        /**
         * Get the input stream of this temporary file.
         * @return an input stream backed by the temporary file.
         * @throws java.io.IOException in case of error opening input stream for file
         */
        public FileInputStream inputStream() throws IOException {
            return new FileInputStream(file);
        }

        /**
         * Get an output stream for this temporary file.
         * @return
         * @throws IOException in case of error opening output stream for file
         */
        public FileOutputStream outputStream() throws IOException {
            return new FileOutputStream(file);
        }
        
        /**
         * Calls method {@link File#delete() } on underlying file object if
         * file still exists.
         */
        public void delete() {
            if (file.exists()) {
                file.delete();
            }
        }
        
        @Override
        protected void finalize() {
            delete();
        }
        
        @Override
        public String toString() {
            return IO.TempFile.class.getSimpleName() + "{file : " + this.file 
                                                    + ", truncated : " + this.truncated + "}";
        }

    }

}
