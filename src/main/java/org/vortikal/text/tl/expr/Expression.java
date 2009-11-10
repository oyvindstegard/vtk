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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.vortikal.text.tl.Argument;
import org.vortikal.text.tl.Context;
import org.vortikal.text.tl.Literal;
import org.vortikal.text.tl.Symbol;
import org.vortikal.text.tl.expr.Operator.Notation;
import org.vortikal.text.tl.expr.Operator.Precedence;

/**
 * Utility class for parsing and evaluating infix expressions (e.g.
 * <code>(x = y) || (x = z)</code>).
 */
public class Expression {

    private static final Symbol LP = new Symbol("(");
    private static final Symbol RP = new Symbol(")");
    private static final Symbol COMMA = new Symbol(",");

    private static final Symbol EQ = new Symbol("=");
    private static final Symbol NEQ = new Symbol("!=");
    private static final Symbol GT = new Symbol(">");
    private static final Symbol LT = new Symbol("<");
    private static final Symbol AND = new Symbol("&&");
    private static final Symbol OR = new Symbol("||");
    private static final Symbol NOT = new Symbol("!");
    private static final Symbol PLUS = new Symbol("+");
    private static final Symbol MINUS = new Symbol("-");
    private static final Symbol MULTIPLY = new Symbol("*");
    private static final Symbol DIVIDE = new Symbol("/");

    /**
     * The default set of operators
     */
    public static final Map<Symbol, Operator> DEFAULT_OPERATORS;
    static {
        Map<Symbol, Operator> ops = new HashMap<Symbol, Operator>();

        // Unary
        ops.put(NOT, new Not(NOT, Notation.PREFIX, Precedence.TEN));

        // Multiplicative
        ops.put(DIVIDE, new Divide(DIVIDE, Notation.INFIX, Precedence.NINE));
        ops.put(MULTIPLY, new Multiply(MULTIPLY, Notation.INFIX, Precedence.EIGHT));

        // Additive
        ops.put(PLUS, new Plus(PLUS, Notation.INFIX, Precedence.SEVEN));
        ops.put(MINUS, new Minus(MINUS, Notation.INFIX, Precedence.SIX));

        // Greater/less than
        ops.put(GT, new Gt(GT, Notation.INFIX, Precedence.FIVE));
        ops.put(LT, new Lt(LT, Notation.INFIX, Precedence.FOUR));

        // Equality
        ops.put(EQ, new Eq(EQ, Notation.INFIX, Precedence.THREE));
        ops.put(NEQ, new Neq(NEQ, Notation.INFIX, Precedence.TWO));

        // Logical AND/OR
        ops.put(AND, new And(AND, Notation.INFIX, Precedence.ONE));
        ops.put(OR, new Or(OR, Notation.INFIX, Precedence.ZERO));

        DEFAULT_OPERATORS = Collections.unmodifiableMap(new HashMap<Symbol, Operator>(ops));
    }

    /**
     * The set of defined operators for this expression
     */
    private Map<Symbol, Operator> operators = new HashMap<Symbol, Operator>(DEFAULT_OPERATORS);

    /**
     * The expression in its original infix notation
     */
    private List<Argument> infix;

    /**
     * The expression converted to postfix notation
     */
    private List<Argument> postfix;

    public Expression(List<Argument> args) {
        this(null, args);
    }

    /**
     * Constructs an expression using a supplied set of functions
     */
    public Expression(Set<Function> functions, List<Argument> args) {
        if (functions != null) {
            for (Function f : functions) {
                addFunction(f);
            }
        }
        if (args == null || args.isEmpty()) {
            throw new IllegalArgumentException("Empty expression");
        }
        this.infix = new ArrayList<Argument>(args);

        Stack<Symbol> stack = new Stack<Symbol>();
        List<Argument> postfix = new ArrayList<Argument>();

        Argument prev = null, next = null;

        for (int i = 0; i < args.size(); i++) {
            Argument arg = args.get(i);
            if (i > 0) {
                prev = args.get(i - 1);
            }
            if (i < args.size() - 1) {
                next = args.get(i + 1);
            }
            if ((arg instanceof Literal) && (prev instanceof Literal)) {
                throw new RuntimeException("Malformed expression: " + this);
            }
            if (arg instanceof Literal) {
                postfix.add(arg);
                continue;
            }
            Symbol symbol = (Symbol) arg;

            if (LP.equals(symbol)) {
                stack.push(symbol);
                continue;
            }

            if (RP.equals(symbol)) {
                int commas = 0;
                while (true) {
                    if (stack.isEmpty()) {
                        throw new RuntimeException("Unbalanced parentheses in expression " + this);
                    }
                    Symbol top = stack.pop();
                    if (LP.equals(top)) {
                        break;
                    }
                    if (COMMA.equals(top)) {
                        commas++;
                    } else {
                        postfix.add(top);
                    }
                }
                if (!stack.isEmpty()) {
                    Symbol top = stack.pop();
                    Operator op = this.operators.get(top);
                    if (op instanceof Function) {
                        Function f = (Function) op;
                        int expected = f.getArgumentCount();
                        if (expected == 0 && commas != 0 || expected > 0 && expected != commas + 1) {
                            throw new RuntimeException(this + ": wrong number of arguments for function "
                                    + f.getSymbol().getSymbol() + " (expected " + expected + ")");
                        }
                    }
                    postfix.add(top);
                }
                continue;
            }

            if (COMMA.equals(symbol)) {
                stack.push(symbol);
                continue;
            }

            Operator op = this.operators.get(symbol);
            if (op == null) {
                postfix.add(symbol);
                continue;
            }
            if (op instanceof Function && !LP.equals(next)) {
                throw new RuntimeException("Expected '(' after function name: " + op.getSymbol().getSymbol());
            }
            if (stack.isEmpty()) {
                stack.push(symbol);
                continue;
            }
            Operator top = this.operators.get(stack.peek());
            int n = op.getprecedence().value();
            while (top != null && top.getprecedence().value() > n) {
                postfix.add(top.getSymbol());
                stack.pop();
                if (stack.isEmpty()) {
                    top = null;
                } else {
                    top = this.operators.get(stack.peek());
                }
            }
            stack.push(symbol);
        }
        while (!stack.isEmpty()) {
            Symbol top = stack.pop();
            if (LP.equals(top) || RP.equals(top) || COMMA.equals(top)) {
                throw new RuntimeException("Invalid expression: " + this);
            }
            postfix.add(top);
        }
        this.postfix = postfix;
    }

    /**
     * Evaluates the expression. Returns a single object value as the result.
     */
    public Object evaluate(Context ctx) {
        Stack<Object> stack = new Stack<Object>();
        try {
            for (Argument arg : this.postfix) {
                if (arg instanceof Literal) {
                    stack.push(arg.getValue(ctx));
                } else {
                    Symbol s = (Symbol) arg;
                    Operator op = operators.get(s);
                    if (op == null) {
                        // Variable:
                        stack.push(s.getValue(ctx));
                    } else {
                        // Function/operator:
                        Object val = op.eval(ctx, stack);
                        stack.push(val);
                    }
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException("Unable to evaluate expression '" + this + "': " + t.getMessage(), t);
        }
        if (stack.size() != 1) {
            throw new RuntimeException("Unable to evaluate expression " + this);
        }
        return stack.peek();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        Iterator<Argument> iter = this.infix.iterator();
        while (iter.hasNext()) {
            Argument arg = iter.next();
            sb.append(arg.getRawValue());
            if (iter.hasNext()) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private void addFunction(Function function) {
        if (function == null) {
            throw new IllegalArgumentException("Function is NULL");
        }
        Symbol symbol = function.getSymbol();
        if (symbol == null) {
            throw new IllegalArgumentException("Function's symbol is NULL");
        }
        if (this.operators.containsKey(symbol)) {
            throw new IllegalArgumentException("Cannot re-define " + symbol.getSymbol());
        }
        this.operators.put(symbol, function);
    }
}
