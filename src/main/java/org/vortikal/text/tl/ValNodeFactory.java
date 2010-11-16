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
package org.vortikal.text.tl;

import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.vortikal.text.tl.expr.Expression;
import org.vortikal.text.tl.expr.Function;

public class ValNodeFactory implements DirectiveNodeFactory {

    private Map<Class<?>, ValueFormatHandler> valueFormatHandlers = 
        new HashMap<Class<?>, ValueFormatHandler>();
    private Set<Function> functions = new HashSet<Function>();
    
    private static final Symbol FLAG_SEPARATOR = new Symbol("#");
    private static final Symbol UNESCAPED = new Symbol("unescaped");
    
    public ValNodeFactory() {
    }
    
    public ValNodeFactory(Map<Class<?>, ValueFormatHandler> valueFormatHandlers) {
        this.valueFormatHandlers = valueFormatHandlers;
    }
    
    public void setFunctions(Set<Function> functions) {
        if (functions != null) {
            for (Function function: functions) {
                this.functions.add(function);
            }
        }
    }
    
    public Node create(DirectiveParseContext ctx) throws Exception {

        // [val <expression> (# flags)? ]
        List<Argument> args = ctx.getArguments();
        if (args.size() < 1) {
            throw new RuntimeException("Wrong number of arguments: " + ctx.getNodeText());
        }
        List<Argument> expression = new ArrayList<Argument>();
        List<Argument> flags = new ArrayList<Argument>();
        List<Argument> cur = expression;
        for (Argument arg: args) {
            if (FLAG_SEPARATOR.equals(arg)) {
                cur = flags;
                continue;
            }
            cur.add(arg);
        }
        
        if (expression.isEmpty()) {
            throw new RuntimeException("Empty expression :" + ctx.getNodeText());
        }
        
        boolean escape = true;
        if (!flags.isEmpty() && flags.get(0).equals(UNESCAPED)) {
           escape = false;
           flags.remove(0);
        }
        Argument format = null;
        if (!flags.isEmpty()) {
           format = flags.remove(0);
        }
        return new ValNode(expression, escape, format);
    }

    public void addValueFormatHandler(Class<?> clazz, ValueFormatHandler handler) {
        this.valueFormatHandlers.put(clazz, handler);
    }
    
    public interface ValueFormatHandler {
        public Object handleValue(Object val, String format, Context ctx);
    }

    private class ValNode extends Node {
        private Expression expression;
        boolean escape;
        private Argument format;

        public ValNode(List<Argument> expression, boolean escape, Argument format) {
            this.expression = new Expression(functions, expression);
            this.escape = escape;
            this.format = format;
        }

        public boolean render(Context ctx, Writer out) throws Exception {
            Object val = this.expression.evaluate(ctx);
            String format = null;
            if (this.format != null) {
                Object o = this.format.getValue(ctx);
                format = o.toString();
            }

            if (val == null) {
                out.write("null");
            } else {
                if (valueFormatHandlers.containsKey(val.getClass())) {
                    ValueFormatHandler handler = valueFormatHandlers.get(val.getClass());
                    val = handler.handleValue(val, format, ctx);
                }
                if (val != null) {
                    if (this.escape) {
                        out.write(ctx.htmlEscape(val.toString()));
                    } else { 
                        out.write(val.toString());
                    }
                }
            }
            return true;
        }

        public String toString() {
            return "[val-node: " + this.expression + "]";
        }
    }
}