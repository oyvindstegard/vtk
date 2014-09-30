/* Copyright (c) 2011, University of Oslo, Norway
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
package vtk.repository.index.mapping;

import java.io.IOException;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.collation.ICUCollationAttributeFactory;


/**
 * {@link TokenStream} implementation with a <code>String</code> array
 * as input and support for encoding terms as collation keys. (For locale-specific
 * sorting).
 *
 * TODO Does not support token offset attributes, but that can be easily added if
 * necessary.
 * 
 */
public final class StringArrayTokenStream extends TokenStream {

    private final String[] values;
    private int currentValueIndex;
    private final CharTermAttribute termAttr;

    /**
     * Create a token stream from the provided string values, with no particular
     * encoding. Each string becomes an index term as-is.
     * 
     * @param values sequence of term values/tokens
     */
    public StringArrayTokenStream(String... values) {
        super();
        this.values = values;
        this.currentValueIndex = 0;
        this.termAttr = addAttribute(CharTermAttribute.class);
    }

    /**
     * Create a token stream from the provided string values, and encode the terms
     * as collation keys using the provided {@link ICUCollationAttributeFactory}.
     * 
     * @param caFactory the collated term attribute factory to use
     * @param values sequence of term values/tokens
     */
    public StringArrayTokenStream(ICUCollationAttributeFactory caFactory, String... values) {
        super(caFactory);
        this.values = values;
        this.currentValueIndex = 0;
        this.termAttr = addAttribute(CharTermAttribute.class);
    }
    
    @Override
    public void reset() throws IOException {
        this.currentValueIndex = 0;
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (this.currentValueIndex == this.values.length) {
            return false; // Signals EOS
        }

        clearAttributes();
        
        this.termAttr.setEmpty();
        this.termAttr.append(this.values[this.currentValueIndex++]);

        return true;
    }
    
}
