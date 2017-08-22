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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import vtk.util.text.TextUtils;

/**
 * Obtain message digests of data as strings.
 */
public class Digest {

    /**
     * Digest serialization formats
     */
    public enum Format {
        ASCII_HEX(b -> new String(TextUtils.toHex(b))),
        BASE64(b -> new String(Base64.encode(b), StandardCharsets.US_ASCII));

        private final Function<byte[],String> formatter;

        private Format(Function<byte[], String> formatter) {
            this.formatter = formatter;
        }

        public String format(byte[] digest) {
            return formatter.apply(digest);
        }
    }

    private final MessageDigest md;
    private Format format = Format.ASCII_HEX;
    private Optional<InputStream> data = Optional.empty();

    /**
     * Create new digester with default options.
     *
     * @param algorithm the algorithm, for instance "SHA-1" or "MD5".
     * @return a new {@code Digest } instance
     */
    public static Digest create(String algorithm) {
        return new Digest(algorithm);
    }

    /**
     * Same as calling {@link #create(java.lang.String) } with "MD5" as argument.
     * @return
     */
    public static Digest md5() {
        return create("MD5");
    }

    /**
     * Same as calling {@link #create(java.lang.String) } with "SHA-1" as argument.
     * @return
     */
    public static Digest sha1() {
        return create("SHA-1");
    }

    /**
     * Same as calling {@link #create(java.lang.String) } with "SHA-256" as argument.
     * @return
     */
    public static Digest sha256() {
        return create("SHA-256");
    }

    /**
     * Same as calling {@link #create(java.lang.String) } with "SHA-512" as argument.
     * @return
     */
    public static Digest sha512() {
        return create("SHA-512");
    }

    private Digest(String algorithm) {
        try {
            md = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("No such algorithm available", e);
        }
    }

    /**
     * Choose string representation format.
     * @param format
     * @return
     */
    public Digest format(Format format) {
        this.format = Objects.requireNonNull(format);
        return this;
    }

    /**
     * Provide data as byte array.
     * @param data
     * @return
     */
    public Digest data(byte[] data) {
        this.data = Optional.of(new ByteArrayInputStream(data));
        return this;
    }

    /**
     * Provide data as input stream.
     *
     * <p>Use this setting to create a stream wrapper using {@link #wrapper() }.
     * @param data
     * @return
     */
    public Digest data(InputStream data) {
        this.data = Optional.of(data);
        return this;
    }

    /**
     * Provide data as string. String will be encoded as UTF-8 bytes before
     * computation of message digest.
     *
     * @param data
     * @return
     */
    public Digest data(String data) {
        this.data = Optional.of(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
        return this;
    }

    /**
     * Obtain wrapper which will compute digest as data is read through stream
     * provided with {@link #data(java.io.InputStream) }.
     *
     * @return a digest stream wrapper, which allows on-the-fly calculation of
     * digest while stream is being read for other purposes
     */
    public StreamWrapper wrapper() {
        return new StreamWrapper(data.orElse(new ByteArrayInputStream(new byte[0])), md, format);
    }

    /**
     * Consumes all input data and computes digest.
     *
     * <p>
     * This resets digest state back to initial state (zero bytes).
     *
     * @return a string representing the digest, formatted according to
     * configured formatter.
     */
    public String compute() {
        try (InputStream input = data.orElse(new ByteArrayInputStream(new byte[0]))) {
            int read;
            final byte[] buf = new byte[4096];
            while ((read = input.read(buf)) != -1) {
                md.update(buf, 0, read);
            }
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
        return format.format(md.digest());
    }

    /**
     * Computes digest of input data and verifies that result is equal to provided
     * argument, which must be formatted in the same way.
     * 
     * @param digest
     * @return
     */
    public boolean verify(String digest) {
        return compute().equals(digest);
    }

    /**
     * Digest stream which wraps an input stream and computes digest as the
     * stream is read.
     */
    public static class StreamWrapper extends DigestInputStream {

        private final Format format;

        private StreamWrapper(InputStream stream, MessageDigest digest, Format f) {
            super(stream, digest);
            this.format = f;
        }

        /**
         * Obtain digest of data read through the wrapped input stream so far.
         *
         * <p>
         * This resets digest state back to initial state (zero bytes).
         *
         * @return digest formatted as a string
         */
        public String compute() {
            return format.format(super.digest.digest());
        }
    }

}
