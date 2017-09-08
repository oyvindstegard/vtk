/* Copyright (c) 2014, University of Oslo, Norway
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

package vtk.repository.index;

import java.io.IOException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.PostingsEnum;

import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.Bits;
import vtk.repository.Path;
import vtk.repository.index.mapping.ResourceFields;

/**
 * Ordered iteration on all URIs of property sets present in index
 */
public class UriIterator implements CloseableIterator<Path> {

    private final IndexManager index;
    private final IndexSearcher searcher;
    
    private final Terms terms;
    private final Bits liveDocs;
    private TermsEnum te = null;
    private PostingsEnum pe = null;
    private Path next;
    
    public UriIterator(IndexManager index) throws IOException {
        this.index = index;
        this.searcher = index.getIndexSearcher();

        IndexReader reader = searcher.getIndexReader();
        this.terms = MultiFields.getTerms(reader, ResourceFields.URI_FIELD_NAME);
        this.liveDocs = MultiFields.getLiveDocs(reader);

        this.next = nextUri();
    }
    
    private Path nextUri() throws IOException {
        if (te == null) {
            if (terms == null) {
                return null;
            }
            te = terms.iterator();
        }
        if (te.next() == null) {
            return null;
        }

        pe = te.postings(pe, PostingsEnum.NONE);
        while (!hasLiveDoc(pe, liveDocs)) {
            if (te.next() == null) {
                return null;
            }
            pe = te.postings(pe, PostingsEnum.NONE);
        }
        
        return Path.fromString(te.term().utf8ToString());
    }

    private boolean hasLiveDoc(PostingsEnum pe, Bits liveDocs) throws IOException {
        int docId;
        while ((docId = pe.nextDoc()) != PostingsEnum.NO_MORE_DOCS) {
            if (liveDocs == null || liveDocs.get(docId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() throws Exception {
        index.releaseIndexSearcher(searcher);
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public Path next() {
        if (next == null) {
            throw new IllegalStateException("No more elements");
        }

        Path retVal = next;

        // Advance to next
        try {
            next = nextUri();
        } catch (IOException io) {
            next = null;
        }

        // Return current
        return retVal;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Not supported");
    }
    
}
