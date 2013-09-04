/* Copyright (c) 2010, University of Oslo, Norway
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
package org.vortikal.text.tl;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class StripNodeFactory implements DirectiveNodeFactory {

    private static final Set<String> STRIP_TERM = new HashSet<String>(Arrays.asList("endstrip"));
    
    @Override
    public Node create(DirectiveParseContext ctx) throws Exception {
        
        ParseResult block = ctx.getParser().parse(STRIP_TERM);

        DirectiveParseContext terminator = block.getTerminator();
        if (terminator == null) {
            throw new RuntimeException("Unterminated directive: " + ctx.getNodeText());
        }
        NodeList nodeList = block.getNodeList();
        return new StripNode(nodeList);
    }
    
    private static class StripNode extends Node {
        private NodeList nodeList;
        
        public StripNode(NodeList nodeList) {
            this.nodeList = nodeList;
        }
        
        public boolean render(Context ctx, Writer out) throws Exception {
            StringWriter buffer = new StringWriter();
            this.nodeList.render(ctx, buffer);
            out.write(buffer.getBuffer().toString().trim());
            return true;
        }
    }

}
