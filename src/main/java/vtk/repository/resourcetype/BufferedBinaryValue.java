/* Copyright (c) 2012, University of Oslo, Norway
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

package vtk.repository.resourcetype;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import vtk.util.io.InputStreamWithLength;

/**
 * Binary value buffer.
 *
 * <p>Objects of this class are immutable.
 */
public final class BufferedBinaryValue implements BinaryValue {

    private final byte[] buffer;
    private final String contentType;

    /**
     * Construct a buffered binary value.
     * @param value byte array containing the octets of this value
     * @param contentType the media type of the data in this value
     */
    public BufferedBinaryValue(byte[] value, String contentType) {
        if (value == null) throw new IllegalArgumentException("value cannot be null");
        this.buffer = copy(value);
        this.contentType = contentType;
    }
    
    /**
     * Convenience constructor for a buffered binary value from a <code>String</code>. The
     * binary value will be the string encoded as UTF-8.
     * @param value a string
     * @param contentType the content type
     */
    public BufferedBinaryValue(String value, String contentType) {
        if (value == null) throw new IllegalArgumentException("value cannot be null");
        this.buffer = value.getBytes(StandardCharsets.UTF_8);
        this.contentType = contentType;
    }

    private byte[] copy(byte[] buffer) {
        byte[] copy = new byte[buffer.length];
        System.arraycopy(buffer, 0, copy, 0, copy.length);
        return copy;
    }
    
    @Override
    public String getContentType() {
        return this.contentType;
    }

    @Override
    public InputStreamWithLength stream() {
        ByteArrayInputStream bis = new ByteArrayInputStream(buffer);
        return new InputStreamWithLength(bis, buffer.length);
    }
    
    @Override
    public byte[] getBytes() {
        return copy(buffer);
    }

    @Override
    public String stringValue(Charset charset) {
        return new String(buffer, charset);
    }

    @Override
    public String stringValue() {
        return new String(buffer, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("BufferedBinaryValue{");
        b.append("type = ").append(this.contentType);
        b.append(", length = ").append(this.buffer.length);
        b.append("}");
        return b.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final BufferedBinaryValue other = (BufferedBinaryValue) obj;
        if (this.buffer != other.buffer) {
            return false;
        }
        if ((this.contentType == null) ? (other.contentType != null) : !this.contentType.equals(other.contentType)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + System.identityHashCode(this.buffer);
        hash = 97 * hash + (this.contentType != null ? this.contentType.hashCode() : 0);
        return hash;
    }

}
