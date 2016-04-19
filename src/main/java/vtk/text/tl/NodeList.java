/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.text.tl;

import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class NodeList implements Iterable<Node> {
    
    private List<Node> nodes = new ArrayList<Node>();
    
    public NodeList() {
        
    }
    
    public NodeList(Node... nodes) {
        for (Node node: nodes) add(node);
    }

    public void add(Node node) {
        this.nodes.add(node);
    }
    
    public Iterator<Node> iterator() {
        return Collections.unmodifiableList(this.nodes).iterator();
    }
    
    public List<Node> getNodes() {
        return Collections.unmodifiableList(this.nodes);
    }

    /**
     * Renders the node list.
     * @param ctx the execution state (variables)
     * @param out output writer
     * @return <code>true</code> if the execution continued across 
     * all nodes, <code>false</code> if one of the nodes aborted execution
     * @throws Exception
     */
    public boolean render(Context ctx, Writer out) throws Exception {
        for (Node node: this.nodes) {
            try {
                if (!node.render(ctx, out)) {
                    return false;
                }
            }
            catch (Throwable t) {
                String msg = t.getMessage();
                out.append("Error: " + node);
                if (msg != null) out.append(": ").append(t.getMessage());
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return this.nodes.toString();
    }
}
