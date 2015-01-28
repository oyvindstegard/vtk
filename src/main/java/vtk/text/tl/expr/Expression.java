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
package vtk.text.tl.expr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import vtk.text.tl.Context;
import vtk.text.tl.Literal;
import vtk.text.tl.Symbol;
import vtk.text.tl.Token;

/**
 * Expression parser and evaluator. 
 * 
 * Takes a list of {@link Token tokens} as constructor argument and 
 * returns a "compiled" expression that can be later evaluated 
 * against a given {@link Context context}.
 * 
 * The expression grammar is defined as follows:
 * <pre>
 * expression ::= logical-expression ;
 * 
 * logical-expression ::= relational-expression { logical-operator relational-expression } ;
 *     
 * relational-expression ::= 
 *    inv-expression 
 *    | simple-expression { relational-operator simple-expression } ;
 * 
 * inv-expression ::= "!" relational-expression ;
 * 
 * simple-expression ::= operand { additive-operator operand } ;
 * 
 * operand ::= term { accessor }  ;
 * 
 * accessor ::=
 *    "." function-call
 *    | "." field-accessor ;
 * 
 * field-accessor = symbol | literal ;
 * 
 * term ::= factor { multiplicative-operator factor } ;
 * 
 * factor ::= 
 *    "(" logical-expression ")"
 *    | list
 *    | function-call 
 *    | map
 *    | variable
 *    | literal ;
 * 
 * list ::= "#" "(" [ list-body ] ")" ;
 * 
 * list-body ::= logical-expression { "," logical-expression } ;
 * 
 * function-call ::=  symbol "(" [ arg-list ] ")" ;
 * 
 * method-call ::= symbol "." symbol "(" [ arg-list ] ")" ;
 * 
 * arg-list ::= logical-expression { "," logical-expression } ;
 * 
 * accessor ::= symbol "." ;
 * 
 * map ::= 
 *    "{" ":" "}"
 *    | "{" map-entry { "," map-entry } "}";
 *  
 * map-entry ::= logical-expression ":" logical-expression ;
 *  
 * logical-operator ::= "&&" | "||" ;
 * 
 * relational-operator ::=  "=" | "!=" | "<" | ">" | "<=" | ">=" "~" ;
 * 
 * additive-operator ::=  "+" | "-" ;
 * 
 * multiplicative-operator ::=  "*" | "/" ;
 * </pre>
 */
public class Expression {

    private static final Symbol LP = new Symbol("(");
    private static final Symbol RP = new Symbol(")");
    private static final Symbol LCB = new Symbol("{");
    private static final Symbol RCB = new Symbol("}");
    private static final Symbol COMMA = new Symbol(",");
    private static final Symbol HASH = new Symbol("#");
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
    private static final Symbol MAPPING = new Symbol(":");
    private static final Symbol MATCH = new Symbol("~");
    private static final Symbol ACCESSOR = new Symbol(".");

    private static final Symbol[] LOGICAL_OPERATORS = 
        new Symbol[] { AND, OR };
    
    private static final Symbol[] RELATIONAL_OPERATORS = 
        new Symbol[] { EQ, NEQ, LT, GT, MATCH  };

    private static final Symbol[] ADDITIVE_OPERATORS = 
        new Symbol[] { PLUS, MINUS };

    private static final Symbol[] MULTIPLICATIVE_OPERATORS = 
        new Symbol[] { MULTIPLY, DIVIDE };

    private enum Wildcard {
        ANY_SYMBOL,
        LITERAL;
    }

    public static final Map<Symbol, Operator> OPERATORS;
    static {
        Map<Symbol, Operator> ops = new HashMap<Symbol, Operator>();
        ops.put(NOT, new Not(NOT));
        ops.put(DIVIDE, new Divide(DIVIDE));
        ops.put(MULTIPLY, new Multiply(MULTIPLY));
        ops.put(PLUS, new Plus(PLUS));
        ops.put(MINUS, new Minus(MINUS));
        ops.put(GT, new Gt(GT));
        ops.put(LT, new Lt(LT));
        ops.put(EQ, new Eq(EQ));
        ops.put(NEQ, new Neq(NEQ));
        ops.put(MATCH, new Match(MATCH));
        ops.put(AND, new And(AND));
        ops.put(OR, new Or(OR));
        OPERATORS = Collections.unmodifiableMap(new HashMap<Symbol, Operator>(ops));
    }
    
    private FunctionResolver functionResolver;
    private List<Token> tokens;
    private int pos = 0;
    private ExpressionNode exp;

    public Expression(List<Token> tokens) {
        this(null, tokens);
    }

    public Expression(Set<Function> functions, List<Token> tokens) {        
       this.tokens = tokens;
       this.functionResolver = new FunctionResolver();
       if (functions != null) {
           for (Operator f : functions) {
               this.functionResolver.addFunction(f);
           }
       }
       this.exp = logicalExpression();
       if (cur() != null) {
           throw new IllegalArgumentException("Extra tokens at position " 
                   + pos + " in expression: " + this.tokens + ": " + cur());
       }
    }

    public Object evaluate(Context ctx) {
        return this.exp.eval(ctx);
    }
    
    
    private ExpressionNode logicalExpression() {
        ExpressionNode node = relationalExpression();
        while (lookingAt(LOGICAL_OPERATORS)) {
            Symbol s = readSymbol();
            ExpressionNode rel = relationalExpression();
            node = new InfixOperation(node, s, rel);
        }
        return node;
    }
    
    private ExpressionNode relationalExpression() {
        if (lookingAt(NOT)) {
            ExpressionNode inv = invExpression();
            return inv;
        }
        ExpressionNode node = simpleExpression();
        while (lookingAt(RELATIONAL_OPERATORS)) {
            Symbol s = readSymbol();
            ExpressionNode simple = simpleExpression();
            node = new InfixOperation(node, s, simple);
        }
        return node;
    }

    private ExpressionNode simpleExpression() {
        ExpressionNode node = operand();
        while (lookingAt(ADDITIVE_OPERATORS)) {
            Symbol s = readSymbol();
            ExpressionNode operand = operand();
            node = new InfixOperation(node, s, operand);
        }
        return node;
    }

    private ExpressionNode operand() {
        ExpressionNode node = term();
        
        while (lookingAt(ACCESSOR)) {
            readSymbol();
            if (lookingAt(Wildcard.LITERAL)) {
                Literal literal = readLiteral();
                return new FieldAccessNode(node, literal);
            }
            expect(Wildcard.ANY_SYMBOL);
            Symbol symbol = readSymbol();
            if (lookingAt(LP)) {
                // Method call
                List<ExpressionNode> args = new ArrayList<ExpressionNode>();
                args.add(node);
                readSymbol();
                if (lookingAt(RP)) {
                    readSymbol();
                    node = new FunctionCall(symbol, args, this.functionResolver);
                    continue;
                }
                args.addAll(argList());
                expect(RP);
                readSymbol();
                node = new FunctionCall(symbol, args, this.functionResolver);
                continue;
            } 
            // Field accessor:
            node = new FieldAccessNode(node, symbol);
        }
        return node;
    }
    
    private ExpressionNode term() {
        ExpressionNode node = factor();
        while (lookingAt(MULTIPLICATIVE_OPERATORS)) {
            Symbol s = readSymbol();
            ExpressionNode factor = factor();
            node = new InfixOperation(node, s, factor);
        }
        return node;
    }

    private ExpressionNode factor() {
       if (lookingAt(LP)) {
           readSymbol();
           ExpressionNode n = logicalExpression();
           expect(RP);
           readSymbol();
           return n;
       }
       if (lookingAt(HASH)) {
           ExpressionNode list = list();
           return list;
       }
       if (lookingAt(Wildcard.ANY_SYMBOL) && LP.equals(lookahead(1))) {
           ExpressionNode fun = functionCall();
           return fun;
       }
       if (lookingAt(LCB)) {
           ExpressionNode map = map();
           return map;
       }
       if (lookingAt(Wildcard.ANY_SYMBOL)) {
          Symbol s = readSymbol();
          ExpressionNode var = new VariableNode(s);
          return var;
       }
       if (lookingAt(Wildcard.LITERAL)) {
          Literal literal = readLiteral();
          ExpressionNode node = new LiteralNode(literal);
          return node;
       }
       throw new IllegalArgumentException("Illegal token: " + cur());
    }

    private ExpressionNode functionCall() {
        Symbol symbol = readSymbol();
        expect(LP);
        readSymbol();
        if (lookingAt(RP)) {
            readSymbol();
            return new FunctionCall(symbol, this.functionResolver);
        }
        List<ExpressionNode> args = argList();
        expect(RP);
        readSymbol();
        return new FunctionCall(symbol, args, this.functionResolver);
    }
    
    private List<ExpressionNode> argList() {
        List<ExpressionNode> list = new ArrayList<ExpressionNode>();
        list.add(logicalExpression());
        while (lookingAt(COMMA)) {
            readSymbol();
            list.add(logicalExpression());
        }
        return list;
    }
    
    private ExpressionNode list() {
        readSymbol();
        expect(LP);
        readSymbol();
        if (lookingAt(RP)) {
            readSymbol();
            return new ListNode(new ArrayList<ExpressionNode>());
        }
        List<ExpressionNode> list = new ArrayList<ExpressionNode>();
        list.add(logicalExpression());
        while (lookingAt(COMMA)) {
            readSymbol();
            list.add(logicalExpression());
        }
        expect(RP);
        readSymbol();
        return new ListNode(list);
    }

    private ExpressionNode map() {
        readSymbol();
        if (lookingAt(MAPPING)) {
            readSymbol();
            expect(RCB);
            readSymbol();
            ExpressionNode emptymap = new MapNode(new HashMap<ExpressionNode, ExpressionNode>());
            return emptymap;
        }
        Map<ExpressionNode, ExpressionNode> map = new LinkedHashMap<ExpressionNode, ExpressionNode>();
        ExpressionNode key = logicalExpression();
        expect(MAPPING);
        readSymbol();
        ExpressionNode value = logicalExpression();
        map.put(key, value);
        while (lookingAt(COMMA)) {
            readSymbol();
            key = logicalExpression();
            expect(MAPPING);
            readSymbol();
            value = logicalExpression();
            map.put(key, value);
        }
        expect(RCB);
        readSymbol();
        return new MapNode(map);
    }
    
    private ExpressionNode invExpression() {
        Symbol s = readSymbol();
        ExpressionNode rel = logicalExpression();
        return new UnaryOperation(s, rel);
    }
    
    private Token cur() {
        if (this.pos < this.tokens.size()) {
            return this.tokens.get(this.pos);
        }
        return null;
    }
    
    private void expect(Symbol s) {
        Token cur = cur();
        if (cur == null) {
            throw new IllegalArgumentException("Expected: " + s + ", found: EOF");
        }
        if (!s.equals(cur)) {
            throw new IllegalArgumentException("Expected: " + s + ", found: " + cur 
                    + " at position " + this.pos + " in " + this.tokens);
        }
    }
    
    private void expect(Wildcard wildcard) {
        Token cur = cur();
        String expected = wildcard == Wildcard.ANY_SYMBOL ? "<symbol>" : "<literal>";
        if (cur == null) {
            throw new IllegalArgumentException("Expected: " + expected + ", found: EOF");
        }
        switch (wildcard) {
        case ANY_SYMBOL:
            if (!(cur instanceof Symbol)) {
                throw new IllegalArgumentException("Expected: " + expected + ", found: " + cur);
            }
            break;
        case LITERAL:
            if (!(cur instanceof Literal)) {
                throw new IllegalArgumentException("Expected: " + expected + ", found: " + cur);
            }
            break;
        }
    }
    
    private Symbol readSymbol() {
        Token cur = cur();
        if (cur == null) {
            throw new IllegalArgumentException("Encountered EOF");
        }
        if (!(cur instanceof Symbol)) {
            throw new IllegalArgumentException("Not a symbol: " + cur);
        }
        this.pos++;
        return (Symbol) cur;
    }
    
    private Literal readLiteral() {
        Token cur = cur();
        if (cur == null) {
            throw new IllegalArgumentException("Encountered EOF");
        }
        if (!(cur instanceof Literal)) {
            throw new IllegalArgumentException("Not a literal: " + cur);
        }
        this.pos++;
        return (Literal) cur;
    }

    private boolean lookingAt(Wildcard wildcard) {
        Token cur = cur();
        if (cur == null) {
            return false;
        }
        switch (wildcard) {
        case ANY_SYMBOL:
            return cur instanceof Symbol;
        case LITERAL:
            return cur instanceof Literal;
        default:
            return false;
        }
    }
    
    private boolean lookingAt(Symbol... symbols) {
        Token cur = cur();
        for (Symbol symbol: symbols) {
            if (symbol.equals(cur)) {
                return true;
            }
        }
        return false;
    }
    
    private Token lookahead(int offset) {
        int idx = this.pos + offset;
        if (idx >= this.tokens.size()) {
            return null;
        }
        return this.tokens.get(idx);
    }    

    private static class InfixOperation implements ExpressionNode {
        private ExpressionNode left;
        private Operator operator;
        private ExpressionNode right;
        public InfixOperation(ExpressionNode left, Symbol symbol, ExpressionNode right) {
            this.left = left;
            this.operator = OPERATORS.get(symbol);
            this.right = right;
        }

        @Override
        public Object eval(Context ctx) {
            return this.operator.eval(ctx, new ExpressionNode[]{left, right});
        }
        
        @Override
        public String toString() {
            return "(" + this.left + " " + this.operator + " " + this.right + ")";
        }
    }
    
    private static class UnaryOperation implements ExpressionNode {
        private Operator operator;
        private ExpressionNode node;
        public UnaryOperation(Symbol symbol, ExpressionNode node) {
            this.operator = OPERATORS.get(symbol);
            this.node = node;
        }

        @Override
        public Object eval(Context ctx) {
            return this.operator.eval(ctx, new ExpressionNode[]{node});
        }
        @Override
        public String toString() {
            return this.operator.toString() + " " + this.node.toString();
        }
    }

    private static class ListNode implements ExpressionNode {
        private List<ExpressionNode> list;
        public ListNode(List<ExpressionNode> list) {
            this.list = list;
        }
        @Override
        public Object eval(Context ctx) {
            List<Object> result = new ArrayList<Object>();
            for (ExpressionNode n: this.list) {
                result.add(n.eval(ctx));
            }
            return result;
        }
        @Override
        public String toString() {
            return "#(" + this.list + ")";
        }
    }

    private static class MapNode implements ExpressionNode {
        private Map<ExpressionNode, ExpressionNode> map;
        public MapNode(Map<ExpressionNode, ExpressionNode> map) {
            this.map = map;
        }
        @Override
        public Object eval(Context ctx) {
            Map<Object, Object> result = new LinkedHashMap<Object, Object>();
            for (ExpressionNode key: this.map.keySet()) {
                result.put(key.eval(ctx), this.map.get(key).eval(ctx));
            }
            return result;
        }
        @Override
        public String toString() {
            return this.map.toString();
        }
    }

    
    private static class VariableNode implements ExpressionNode {
        private Symbol symbol;
        public VariableNode(Symbol symbol) {
            this.symbol = symbol;
        }
        @Override
        public Object eval(Context ctx) {
            return this.symbol.getValue(ctx);
        }
        @Override
        public String toString() {
            return this.symbol.getRawValue();
        }
    }

    private static class LiteralNode implements ExpressionNode {
        private Literal literal;
        public LiteralNode(Literal literal) {
            this.literal= literal;
        }
        @Override
        public Object eval(Context ctx) {
            return this.literal.getValue(ctx);
        }
        @Override
        public String toString() {
            return this.literal.getRawValue();
        }
    }
    
    private static class FieldAccessNode implements ExpressionNode {
        private ExpressionNode object;
        private String field;
        private Accessor accessor;

        public FieldAccessNode(ExpressionNode object, Token accessor) {
            this.object = object;
            this.field = accessor.getRawValue();
            this.accessor = new Accessor(ACCESSOR, accessor);
        }
        @Override
        public Object eval(Context ctx) {
            return this.accessor.eval(ctx, this.object);
        }
        
        @Override
        public String toString() {
            return this.object + "." + this.field;
        }
    }

    private static class FunctionResolver {
        private static Symbol TYPEOF = new Symbol("typeof");
        private Map<Symbol, Operator> functions = new HashMap<Symbol, Operator>();
        
        public FunctionResolver() {
            functions.put(TYPEOF,  new Operator(TYPEOF) {
                @Override
                public Object eval(Context ctx, ExpressionNode... args) {
                    if (args.length != 1) {
                        throw new IllegalStateException(TYPEOF + ": takes 1 argument");
                    }
                    ExpressionNode arg = args[0];
                    
                    if (arg instanceof VariableNode) {
                        VariableNode node = (VariableNode) arg;
                        if (functions.containsKey(node.symbol)) {
                            return "function";
                        }
                        if (!ctx.isDefined(node.symbol.getSymbol())) {
                            return "undefined";
                        }
                    }
                    Object object = arg.eval(ctx);
                    if (object == null) {
                        //return "null";
                        return "object";
                    }
                    if (object instanceof String) {
                        return "string";
                    }
                    if (object instanceof Number) {
                        return "number";
                    }
                    if (object instanceof Boolean) {
                        return "boolean";
                    }
                    return "object";
                }
            });
        }
        
        public Operator get(Symbol name) {
            //final Map<Symbol, Operator> functions = this.functions;
            return this.functions.get(name);
        }
        
        public void addFunction(Operator function) {
            if (function == null) {
                throw new IllegalArgumentException("Function is NULL");
            }
            Symbol symbol = function.getSymbol();
            if (symbol == null) {
                throw new IllegalArgumentException("Function's symbol is NULL");
            }
            if (this.functions.containsKey(symbol)) {
                throw new IllegalArgumentException("Cannot re-define " + symbol.getSymbol());
            }
            this.functions.put(symbol, function);
        }
    }
    
    private static class FunctionCall implements ExpressionNode {
        private Symbol name;
        private List<ExpressionNode> args = null;
        private FunctionResolver resolver;
        
        public FunctionCall(Symbol name, FunctionResolver resolver) {
            this.name = name;
            this.resolver = resolver;
        }

        public FunctionCall(Symbol name, List<ExpressionNode> args, FunctionResolver resolver) {
            this.name = name;
            this.args = args;
            this.resolver = resolver;
        }
        
        @Override
        public Object eval(Context ctx) {
            Operator fun = this.resolver.get(this.name);
            if (fun == null) {
                throw new IllegalStateException("Undefined function: " + this.name);
            }
            List<ExpressionNode> args = this.args;
            if (args == null) {
                args = Collections.emptyList();
            }
            return fun.eval(ctx, args.toArray(new ExpressionNode[args.size()]));
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(this.name.getRawValue());
            sb.append("(");
            if (this.args != null) {
                sb.append(this.args);
            }
            sb.append(")");
            return sb.toString();
        }
    }
    
    
    @Override
    public String toString() {
        return this.exp.toString();
    }
}
