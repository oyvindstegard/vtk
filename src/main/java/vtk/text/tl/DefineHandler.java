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
import java.util.List;

import vtk.text.tl.Parser.Directive;
import vtk.text.tl.expr.Expression;
import vtk.text.tl.expr.Expression.FunctionResolver;

public class DefineHandler implements DirectiveHandler {

    private FunctionResolver functionResolver;

    public DefineHandler(FunctionResolver functionResolver) {
        this.functionResolver = functionResolver;
    }

    public String[] tokens() {
        return new String[] { "def" };
    }

    @Override
    public void directive(Directive directive, TemplateContext context) {
        List<Token> args = directive.args();
        if (args.size() < 2) {
            throw new RuntimeException("Too few arguments: " + directive);
        }
        Token arg1 = args.get(0);
        if (!(arg1 instanceof Symbol)) {
            throw new RuntimeException("Expected symbol: " + arg1.getRawValue());
        }
        final String variable = ((Symbol) arg1).getSymbol();

        final Expression expression =
                new Expression(this.functionResolver, args.subList(1, args.size()));
        context.add(new Node() {
            @Override
            public boolean render(Context ctx, Writer out) throws Exception {
                Object val = expression.evaluate(ctx);
                ctx.define(variable, val, true);
                return true;
            }
            @Override
            public String toString() {
                return "[def " + variable + " " + expression + "]";
            }
        });
    }
}
