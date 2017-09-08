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
 * Query for URI depth.
 * Only equals is supported and therefore the assumed operator.
 */
public class UriDepthQuery implements UriQuery {

    private static final long serialVersionUID = 6017693254620277196L;

    private final int depth;
    private final TermOperator op;
    
    public UriDepthQuery(int depth) {
        this(depth, TermOperator.EQ);
    }

    public UriDepthQuery(int depth, TermOperator op) {
        this.depth = depth;
        this.op = op;
    }
    
    public int getDepth() {
        return this.depth;
    }

    public TermOperator getOperator() {
        return this.op;
    }
    
    @Override
    public Object accept(QueryVisitor visitor, Object data) {
        return visitor.visit(this, data);
    }

    @Override
    public String toString() {
        return "UriDepthQuery{" + "depth " + op.name() + " " + depth + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + this.depth;
        hash = 47 * hash + Objects.hashCode(this.op);
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
        final UriDepthQuery other = (UriDepthQuery) obj;
        if (this.depth != other.depth) {
            return false;
        }
        if (this.op != other.op) {
            return false;
        }
        return true;
    }


    
}
