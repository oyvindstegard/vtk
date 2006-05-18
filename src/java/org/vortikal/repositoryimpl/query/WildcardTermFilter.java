/* Copyright (c) 2006, University of Oslo, Norway
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
package org.vortikal.repositoryimpl.query;

import java.io.IOException;
import java.util.BitSet;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.WildcardTermEnum;

/**
 * A Lucene <code>Filter</code> that filters on the given wildcard term.
 * 
 * @see org.apache.lucene.search.WildcardTermEnum
 * 
 * NOTE: This filter is only meant as a short-lived filter for a single query.
 *       The results are cached once, and only once, and applies to the reader
 *       that was passed in after the first call to {@link #bits(IndexReader)}.
 *       Sub-sequent calls with different readers will not update the cached
 *       results ! So don't re-use instances of this class.
 *       
 *       Long-term filters which rarely change should be wrapped with
 *       {@link org.apache.lucene.search.CachingWrapperFilter} instead.
 * 
 * @author oyviste
 *
 */
public class WildcardTermFilter extends Filter {

    private BitSet bits = null;
    private Term wildcardTerm;
    
    public WildcardTermFilter(Term wildcardTerm) {
        this.wildcardTerm = wildcardTerm;
    }

    public BitSet bits(IndexReader reader) throws IOException {
        synchronized(this) {
            if (this.bits == null) {
                this.bits = getBits(reader);
            }
        }
        
        return this.bits;
    }

    private BitSet getBits(IndexReader reader) throws IOException {
        
        BitSet bits = new BitSet(reader.maxDoc());
        String fieldName = wildcardTerm.field();
        WildcardTermEnum tenum = new WildcardTermEnum(reader, this.wildcardTerm);
        TermDocs tdocs = reader.termDocs();
        try {
            do {
                Term term = tenum.term();
                if (term != null && term.field() == fieldName) {
                    tdocs.seek(tenum);
                    
                    while (tdocs.next()) {
                        bits.set(tdocs.doc());
                    }
                } else break;
            } while (tenum.next());
        } finally {
            tenum.close();
            tdocs.close();
        }
        
        return bits;
        
    }
}
