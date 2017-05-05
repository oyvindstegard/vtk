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
package vtk.repository.search.query;

import java.util.Objects;

/**
 * Prefix query on resource URI. Only matches on complete URIs and descendants, not prefix
 * on URI names (last segment of an URI).
 *
 * <p>Instances of this class are immutable.
 */
public class UriPrefixQuery implements UriQuery {

    private static final long serialVersionUID = -2798356085088768567L;

    private final String uri;
    private final boolean inverted;
    private final boolean includeSelf;

    /**
     * Construct default URI prefix query which includes matching of self and all descendants.
     *
     * <p>For compatibility reasons, match does not include self if URI ends with a trailing slash or is the root URI.
     * Otherwise include self is default.
     *
     * @param uri
     */
    public UriPrefixQuery(String uri) {
        this(uri, false);
    }

    /**
     * Construct URI prefix query with possible negation of matching.
     *
     * <p>For compatibility reasons, it does not include self if uri ends with a trailing slash,
     * and is not the root URI, otherwise
     *
     * <p>For better control over whether self shall be included or not, use {@link #UriPrefixQuery(java.lang.String, boolean, boolean) }.
     * @param uri
     * @param inverted
     */
    public UriPrefixQuery(String uri, boolean inverted) {
        // Be backwards compatible with older behaviour on significance of trailing slash.
        // XXX: Note that the root URI '/' is a special case, it will not be included
        //      as part of URI prefix query results (only the children), unless
        //      this is explicitly set using #setIncludeSelf(boolean)
        if ("/".equals(uri)) {
            this.includeSelf = false;
        } else if (uri.endsWith("/")) {
            this.includeSelf = false;
            uri = uri.substring(0, uri.length()-1);
        } else {
            this.includeSelf = true;
        }

        this.uri = uri;
        this.inverted = inverted;
    }

    /**
     * Construct URI prefix query with possible negation of matching and control
     * over whether the URI itself shall be included, or just its descendants.
     * @param uri
     * @param inverted
     * @param includeSelf
     */
    public UriPrefixQuery(String uri, boolean inverted, boolean includeSelf) {
        if (!"/".equals(uri) && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length()-1);
        }
        this.uri = uri;
        this.inverted = inverted;
        this.includeSelf = includeSelf;
    }
    
    public String getUri() {
        return this.uri;
    }

    public boolean isInverted() {
        return inverted;
    }

    /**
     * @return boolean telling whether the URI itself should be matched,
     * in addition to all its descendants, or not.
     */
    public boolean isIncludeSelf() {
        return this.includeSelf;
    }
    
    @Override
    public Object accept(QueryVisitor visitor, Object data) {
        return visitor.visit(this, data);
    }

    @Override
    public String toString() {
        return "UriPrefixQuery{" + "uri=" + uri + ", inverted=" + inverted + ", includeSelf=" + includeSelf + '}';
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + Objects.hashCode(this.uri);
        hash = 47 * hash + (this.inverted ? 1 : 0);
        hash = 47 * hash + (this.includeSelf ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final UriPrefixQuery other = (UriPrefixQuery) obj;
        if (this.inverted != other.inverted) {
            return false;
        }
        if (this.includeSelf != other.includeSelf) {
            return false;
        }
        if (!Objects.equals(this.uri, other.uri)) {
            return false;
        }
        return true;
    }


}
