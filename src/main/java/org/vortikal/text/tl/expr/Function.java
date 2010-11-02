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
package org.vortikal.text.tl.expr;

import org.vortikal.text.tl.Context;
import org.vortikal.text.tl.Symbol;


public abstract class Function extends Operator {
    private Integer argumentCount;
    
    public Function(Symbol symbol, int argumentCount) {
        super(symbol, Notation.PREFIX, Precedence.FUNCTION_PRECEDENCE);
        this.argumentCount = argumentCount;
    }

    public Function(Symbol symbol) {
        super(symbol, Notation.PREFIX, Precedence.FUNCTION_PRECEDENCE);
        this.argumentCount = null;
    }

    
    @Override
    public final Object eval(Context ctx, EvalStack stack) throws Exception {
        Object o = stack.pop();
        if (o == null || !(o instanceof Integer)) {
            throw new IllegalStateException("Expected argument count at top of stack");
        }
        int argCount = (Integer) o;
        if (this.argumentCount != null && argCount != this.argumentCount) {
            throw new IllegalArgumentException("Function " + getSymbol().getSymbol() 
                    + " takes " + this.argumentCount + " arguments");
        }
        Object[] args = new Object[argCount];

        for (int i = argCount - 1; i >= 0; i--) {
            args[i] = stack.pop();
        }
        return eval(ctx, args);
    }
    
    public abstract Object eval(Context ctx, Object...args) throws Exception;
}
