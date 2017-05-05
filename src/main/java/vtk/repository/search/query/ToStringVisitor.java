/* Copyright (c) 2008,2014, University of Oslo, Norway
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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * Simple visitor which renders the complete query tree to a string, optionally
 * with indentation for human readability.
 */
public class ToStringVisitor implements QueryVisitor {

    private static final String PER_LEVEL_INDENT = "  ";
    
    private final boolean indent;

    private final Set<Query> seen = Collections.newSetFromMap(new IdentityHashMap<Query,Boolean>());

    /**
     * Default visitor with single line output (no indentation).
     */
    public ToStringVisitor() {
        this.indent = false;
    }

    /**
     * Visitor which indents output according to level of depth in query tree.
     *
     * <p>Visit methods return a string representation of the provided query.
     * @param indent if indentation should be done in output
     */
    public ToStringVisitor(boolean indent) {
        this.indent = indent;
    }

    private Object visitNode(AbstractMultipleQuery amq, Object prefix) {
        if (!seen.add(amq)) {
            throw new IllegalArgumentException("Query tree contains cyclic references through node type AbstractMultipleQuery");
        }

        if (prefix == null) prefix = "";

        StringBuilder buf = new StringBuilder(indent ? (String)prefix : "");
        buf.append(amq.getClass().getSimpleName()).append('[');

        if (amq.getQueries().isEmpty()) {
            buf.append(']');
            return buf.toString();
        }

        for (Iterator<Query> it = amq.getQueries().iterator(); it.hasNext();) {
            if (indent) buf.append('\n');
            
            buf.append(it.next().accept(this, prefix + PER_LEVEL_INDENT));

            if (it.hasNext()) {
                buf.append(',').append(indent ? "" : " ");
            } else {
                if (indent) buf.append('\n').append((String)prefix);
                buf.append(']');
                if (indent && ((String) prefix).isEmpty()) buf.append('\n');
            }
        }

        return buf.toString();
    }

    private Object visitLeafNode(Query q, Object prefix) {
        if (q instanceof AbstractMultipleQuery) {
            throw new IllegalArgumentException("Only leaf nodes accepted");
        }
        if (indent) {
            return (prefix != null ? prefix.toString() : "") + q.toString();
        } else {
            return q.toString();
        }
    }

    /**
     * @param andQuery The <code>AndQuery</code> instance.
     * @param prefix A <code>String</code> instance with the base output prefix or <code>null</code>.
     * 
     * @return A <code>String<code> representation of an AND query node and the complete
     *         subtree.
     */
    @Override
    public Object visit(AndQuery andQuery, Object prefix) {
        return visitNode((AbstractMultipleQuery)andQuery, prefix);
    }

    /**
     * @param orQuery The <code>OrQuery</code> instance.
     * @param prefix A <code>String</code> with the base output prefix or <code>null</code>.
     * 
     * @return A <code>String</code> representation of an OR query node and the complete
     *         subtree.
     */
    @Override
    public Object visit(OrQuery orQuery, Object prefix) {
        return visitNode((AbstractMultipleQuery)orQuery, prefix);
    }

    @Override
    public Object visit(UriSetQuery uriSetQuery, Object prefix) {
        return visitLeafNode(uriSetQuery, prefix);
    }
    
    /**
     * @param npQuery The <code>NamePrefixQuery</code> instance.
     * @param prefix A <code>String</code> with the base output prefix or <code>null</code>.
     * 
     * @return A <code>String</code> representation of a <code>NamePrefixQuery</code> node.
     */
    @Override
    public Object visit(NamePrefixQuery npQuery, Object prefix) {
        return visitLeafNode(npQuery, prefix);
    }

    /**
     * @param nrQuery The <code>NameRangeQuery</code> instance.
     * @param prefix A <code>String</code> with the base output prefix or <code>null</code>.
     * 
     * @return A <code>String</code> representation of a <code>NameRangeQuery</code> node.
     */
    @Override
    public Object visit(NameRangeQuery nrQuery, Object prefix) {
        return visitLeafNode(nrQuery, prefix);
    }
    
    /**
     * @param ntQuery The <code>NameTermQuery</code> instance.
     * @param prefix A <code>String</code> with the base output prefix or <code>null</code>.
     * 
     * @return A <code>String</code> representation of a <code>NameTermQuery</code> node.
     */
    @Override
    public Object visit(NameTermQuery ntQuery, Object prefix) {
        return visitLeafNode(ntQuery, prefix);
    }

    /**
     * @param nwQuery The <code>NameWildcardQuery</code> instance.
     * @param prefix A <code>String</code> with the base output prefix or <code>null</code>.
     * 
     * @return A <code>String</code> representation of a <code>NameWildcardQuery</code> node.
     */
    @Override
    public Object visit(NameWildcardQuery nwQuery, Object prefix) {
        return visitLeafNode(nwQuery, prefix);
    }

    /**
     * @param peQuery The <code>PropertyExistsQuery</code> instance.
     * @param prefix A <code>String</code> with the base output prefix or <code>null</code>.
     * 
     * @return A <code>String</code> representation of a <code>PropertyExistsQuery</code> node.
     */
    @Override
    public Object visit(PropertyExistsQuery peQuery, Object prefix) {
        return visitLeafNode(peQuery, prefix);
    }


    /**
     * @param ppQuery The <code>PropertyPrefixQuery</code> instance.
     * @param prefix A <code>String</code> with the base output prefix or <code>null</code>.
     * 
     * @return A <code>String</code> representation of a <code>PropertyPrefixQuery</code> node.
     */
    @Override
    public Object visit(PropertyPrefixQuery ppQuery, Object prefix) {
        return visitLeafNode(ppQuery, prefix);
    }

    /**
     * @param prQuery The <code>PropertyRangeQuery</code> instance.
     * @param prefix A <code>String</code> with the base output prefix or <code>null</code>.
     * 
     * @return A <code>String</code> representation of a <code>PropertyRangeQuery</code> node.
     */
    @Override
    public Object visit(PropertyRangeQuery prQuery, Object prefix) {
        return visitLeafNode(prQuery, prefix);
    }

    /**
     * @param ptQuery The <code>PropertyTermQuery</code> instance.
     * @param prefix A <code>String</code> with the base output prefix or <code>null</code>.
     * 
     * @return A <code>String</code> representation of a <code>PropertyTermQuery</code> node.
     */
    @Override
    public Object visit(PropertyTermQuery ptQuery, Object prefix) {
        return visitLeafNode(ptQuery, prefix);
    }

    /**
     * @param pwQuery The <code>PropertyWildcardQuery</code> instance.
     * @param prefix A <code>String</code> with the base output prefix or <code>null</code>.
     * 
     * @return A <code>String</code> representation of a <code>PropertyWildcardQuery</code> node.
     */
    @Override
    public Object visit(PropertyWildcardQuery pwQuery, Object prefix) {
        return visitLeafNode(pwQuery, prefix);
    }

    /**
     * @param ttQuery The <code>TypeTermQuery</code> instance.
     * @param prefix A <code>String</code> with the base output prefix or <code>null</code>.
     * 
     * @return A <code>String</code> representation of a <code>TypeTermQuery</code> node.
     */
    @Override
    public Object visit(TypeTermQuery ttQuery, Object prefix) {
        return visitLeafNode(ttQuery, prefix);
    }

    /**
     * @param udQuery The <code>UriDepthQuery</code> instance.
     * @param prefix A <code>String</code> with the base output prefix or <code>null</code>.
     * 
     * @return A <code>String</code> representation of a <code>UriDepthQuery</code> node.
     */
    @Override
    public Object visit(UriDepthQuery udQuery, Object prefix) {
        return visitLeafNode(udQuery, prefix);
    }

    /**
     * @param upQuery The <code>UriPrefixQuery</code> instance.
     * @param prefix A <code>String</code> with the base output prefix or <code>null</code>.
     * 
     * @return A <code>String</code> representation of a <code>UriPrefixQuery</code> node.
     */
    @Override
    public Object visit(UriPrefixQuery upQuery, Object prefix) {
        return visitLeafNode(upQuery, prefix);
    }

    /**
     * @param utQuery The <code>UriTermQuery</code> instance.
     * @param prefix A <code>String</code> with the base output prefix or <code>null</code>.
     * 
     * @return A <code>String</code> representation of a <code>UriTermQuery</code> node.
     */
    @Override
    public Object visit(UriTermQuery utQuery, Object prefix) {
        return visitLeafNode(utQuery, prefix);
    }

    @Override
    public Object visit(AclExistsQuery aclExistsQuery, Object prefix) {
        return visitLeafNode(aclExistsQuery, prefix);
    }

    @Override
    public Object visit(AclInheritedFromQuery aclIHFQuery, Object prefix) {
        return visitLeafNode(aclIHFQuery, prefix);
    }

    @Override
    public Object visit(AclReadForAllQuery query, Object prefix) {
        return visitLeafNode(query, prefix);
    }

    @Override
    public Object visit(AclPrivilegeQuery query, Object prefix) {
        return visitLeafNode(query, prefix);
    }
    
    @Override
    public Object visit(MatchAllQuery query, Object prefix) {
        return visitLeafNode(query, prefix);
    }
    
}
