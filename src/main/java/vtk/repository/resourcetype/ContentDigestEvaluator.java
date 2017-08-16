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
package vtk.repository.resourcetype;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.resourcetype.property.PropertyEvaluationException;
import vtk.util.text.TextUtils;

/**
 * Property evaluator which writes a string value containging a message digest of
 * the resource content.
 *
 * <p>
 * Values are encoded as hexadecimal strings.
 *
 * <p>
 * Default algorithm is SHA-256.
 */
public class ContentDigestEvaluator implements PropertyEvaluator {

    private final String algorithm;

    /**
     * Construct evaluator with default algorithm.
     */
    public ContentDigestEvaluator() {
        this("SHA-256");
    }

    /**
     * Construct evaluator with a specific algorithm, by SPI name
     * @param algorithm
     */
    public ContentDigestEvaluator(String algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public boolean evaluate(Property property, PropertyEvaluationContext ctx) throws PropertyEvaluationException {

        if (ctx.isCollection()) {
            throw new IllegalStateException("This evaluator should not be configured for properties of collection types");
        }

        if (!property.isValueInitialized() || ctx.getEvaluationType() == PropertyEvaluationContext.Type.ContentChange) {
            try {
                property.setStringValue(digest(ctx.getContent()));
            } catch (IOException | NoSuchAlgorithmException | ValueFormatException e) {
                throw new PropertyEvaluationException("Content digestion failed", e);
            }
        }

        return true;
    }

    private String digest(Content content) throws IOException, NoSuchAlgorithmException {
        final MessageDigest md = MessageDigest.getInstance(algorithm);
        try (InputStream is = content.getContentInputStream()) {
            int read;
            final byte[] buf = new byte[4096];
            while ((read = is.read(buf)) != -1) {
                md.update(buf, 0, read);
            }
        }
        return new String(TextUtils.toHex(md.digest()));
    }

}
