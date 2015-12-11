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
package vtk.text.tl;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import vtk.text.tl.Parser.Directive;
import vtk.text.tl.expr.Expression;
import vtk.text.tl.expr.Expression.FunctionResolver;

public class IfHandler implements DirectiveHandler {

    private FunctionResolver functionResolver = null;

    public IfHandler(FunctionResolver functionResolver) {
        this.functionResolver = functionResolver;
    }

    public String[] tokens() {
        return new String[] { "if", "elseif", "else", "endif" };
    }

    public void directive(Directive directive, TemplateContext context) {
        String name = directive.name();
        String prev = context.top() == null ? null
                : context.top().directive().name();
        if ("if".equals(name)) {
            if (directive.args().isEmpty()) {
                context.error("[if]: missing expression");
                return;

            }
            context.push(new DirectiveState(directive));
        }
        else if ("elseif".equals(name)) {
            if (directive.args().isEmpty()) {
                context.error("[elseif]: missing expression");
                return;

            }
            if ("if".equals(prev) || "elseif".equals(prev))
                context.push(new DirectiveState(directive));
            else {
                context.error("[elseif] must follow [if] or [elseif], was: [" + prev + "]");
                return;
            }
        }
        else if ("else".equals(name)) {
            if ("if".equals(prev) || "elseif".equals(prev))
                context.push(new DirectiveState(directive));
            else {
                context.error("[else] must follow [if] or [elseif], was: [" + prev + "]");
                return;
            }
        }
        else if ("endif".equals(directive.name())) {
            if (!"if".equals(prev) && !"elseif".equals(prev) && !"else".equals(prev)) {
                context.error("[endif] must follow [if], [elseif] or [else], was: [" + prev + "]");
                return;
            }
            List<Branch> branches = new ArrayList<Branch>();
            while (true) {
                DirectiveState state = context.pop();
                if (state == null) {
                    context.error("internal error: missing if-node");
                    return;
                }
                Directive prevDir = state.directive();
                List<Token> args = new ArrayList<Token>(prevDir.args());
                if (args.isEmpty()) args.add(new Literal("true"));

                Expression expression = new Expression(functionResolver, args);
                Branch branch = new Branch(prevDir, expression, state.nodes());
                branches.add(0, branch);

                if ("if".equals(prevDir.name())) break;
            }
            context.add(new IfNode(branches));
        }
    }

    static class Branch {
        private Directive directive;
        private Expression expression;
        private NodeList nodeList;
        public Branch(Directive directive, Expression expression, NodeList nodeList) {
            this.directive = directive;
            this.expression = expression;
            this.nodeList = nodeList;
        }
        @Override
        public String toString() {
            return directive.toString();
        }
    }

    static class IfNode extends Node {
        private List<Branch> branches;

        public IfNode(List<Branch> branches) {
            this.branches = branches;
        }

        @Override
        public boolean render(Context ctx, Writer out) throws Exception {
            NodeList target = null;
            for (Branch branch: this.branches) {
                if (eval(branch.expression, ctx)) {
                    target = branch.nodeList;
                    break;
                }
            }
            boolean retval = true;
            if (target != null) {
                ctx.push();
                retval = target.render(ctx, out);
                ctx.pop();
            }
            return retval;
        }

        private boolean eval(Expression expression, Context ctx) {
            Object o = expression.evaluate(ctx);
            if (o == null) {
                return false;
            } else if (o instanceof Boolean) {
                return ((Boolean) o).booleanValue();
            } else {
                return true;
            }
        }

        @Override
        public String toString() {
            return branches.toString();
        }

    }

}
