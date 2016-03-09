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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import vtk.text.tl.Parser.Directive;
import vtk.text.tl.expr.Expression;
import vtk.text.tl.expr.Expression.FunctionResolver;

public class ListHandler implements DirectiveHandler {

    private FunctionResolver functionResolver = null;

    public ListHandler(FunctionResolver functionResolver) {
        this.functionResolver = functionResolver;
    }

    public String[] tokens() {
        return new String[] { "list", "endlist" };
    }

    /**
     * [list x varname]
     *    .. do stuff with varname: [val varname]
     *    [if _first] first element [endif]
     *    [if _last] last element [endif]
     *    index: [val _index]
     *    size: [val _size]
     * [endlist]
     *
     */
    @Override
    public void directive(Directive directive, TemplateContext context) {
        String name = directive.name();

        if ("list".equals(name)) {
            context.push(new DirectiveState(directive));
            return;
        }
        if ("endlist".equals(name)) {
            DirectiveState state = context.pop();
            if (state == null || !"list".equals(state.directive().name())) {
                context.error("Misplaced directive: endlist");
                return;
            }
            List<Token> args = state.directive().args();
            if (args.size() < 2) {
                context.error("List directive: too few arguments");
                return;
            }
            Token last = args.get(args.size() - 1);
            if (!(last instanceof Symbol)) {
                context.error("List directive: expected symbol: " + last.getRawValue());
                return;
            }

            Expression expression = new Expression(functionResolver, args.subList(0, args.size() - 1));
            context.add(new ListNode(expression, (Symbol) last, state.nodes()));
        }
    }

    private static class ListNode extends Node {
        private Expression expression;
        private Symbol defVar;
        private NodeList nodeList;

        public ListNode(Expression expression, Symbol defVar, NodeList nodeList) {
            this.expression = expression;
            this.defVar = defVar;
            this.nodeList = nodeList;
        }

        @Override
        public boolean render(Context ctx, Writer out) throws Exception {
            Object evaluated = this.expression.evaluate(ctx);
            List<Object> elements = new ArrayList<Object>();
            if (evaluated instanceof Iterable<?>) {
                Iterable<?> iterable = (Iterable<?>) evaluated;
                for (Object o : iterable) {
                    elements.add(o);
                }
            } else if (evaluated instanceof Iterator<?>) {
                Iterator<?> iter = (Iterator<?>) evaluated;
                while (iter.hasNext()) {
                    elements.add(iter.next());
                }
            } else if (evaluated instanceof Object[]) {
                for (Object o : (Object[]) evaluated) {
                    elements.add(o);
                }
            } else if (evaluated instanceof Map<?,?>) {
                Map<?,?> map = (Map<?,?>) evaluated;
                for (Map.Entry<?, ?> entry: map.entrySet()) {
                    elements.add(new Object[] { entry.getKey(), entry.getValue() });
                }
            } else {
                throw new RuntimeException(
                        "List: Cannot iterate expression: "
                                + this.expression + ": result is not a list: "
                                + evaluated);
            }
            execute(elements, ctx, out);
            return true;
        }

        private void execute(List<Object> elements, Context ctx, Writer out) throws Exception {
            int size = elements.size();

            for (int i = 0; i < size; i++) {
                Object object = elements.get(i);
                ctx.push();
                ctx.define(this.defVar.getSymbol(), object, false);
                ctx.define("_size", size, false);
                ctx.define("_index", i, false);
                ctx.define("_first", (i == 0), false);
                ctx.define("_last", (i == size - 1), false);
                this.nodeList.render(ctx, out);
                ctx.pop();
            }
        }
    }
}
