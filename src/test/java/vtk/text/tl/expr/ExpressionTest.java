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
package vtk.text.tl.expr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import vtk.text.tl.Context;
import vtk.text.tl.Literal;
import vtk.text.tl.Symbol;
import vtk.text.tl.Token;
import vtk.text.tl.expr.Expression.FunctionResolver;

public class ExpressionTest {

    private FunctionResolver functions = new FunctionResolver();

    @Before
    public void setUp() throws Exception {

        this.functions.addFunction(new Concat(new Symbol("concat")));
        this.functions.addFunction(new Length(new Symbol("length")));
        this.functions.addFunction(new Get(new Symbol("get")));

        Symbol s = new Symbol("emptyfunc");
        this.functions.addFunction(new Function(s, 0) {
            @Override
            public Object eval(Context ctx, Object... args) {
                return "result";
            }
        });
        s = new Symbol("map");
        this.functions.addFunction(new Function(s) {
            @Override
            public Object eval(Context ctx, Object... args) {
                if (args == null || args.length % 2 != 0) {
                    throw new IllegalArgumentException(getSymbol() + " takes an even number of arguments");
                }
                Map<Object, Object> result = new HashMap<Object, Object>();
                for (int i = 0; i < args.length; i+=2) {
                    result.put(args[i], args[i+1]);
                }
                return result;
            }
        });
    }

    
    @Test
    public void basicExpressions() {
        Object result;
        result = eval(
                new Literal("\"literal-string\"")	
        );
        assertEquals("literal-string", result);

        result = eval(
                new Symbol("null")
        );
        assertNull(result);
        
        result = eval(
                new Symbol("null"),
                new Symbol("="),
                new Symbol("null")
        );
        assertEquals(true, result);

        result = eval(
                new Literal("true")
        );
        assertEquals(true, result);

        result = eval(
                new Symbol("!"),
                new Literal("true")
        );
        assertEquals(false, result);

        result = eval(
                new Symbol("!"),
                new Symbol("!"),
                new Literal("true")
        );
        assertEquals(true, result);
        
        result = eval(
                new Literal("true"),
                new Symbol("&&"),
                new Literal("false"),
                new Symbol("||"),
                new Literal("true")
        );
        assertEquals(true, result);
        
        result = eval(
                new Literal("true"),
                new Symbol("||"),
                new Literal("false"),
                new Symbol("&&"),
                new Literal("true")
        );
        assertEquals(true, result);
        
        
        result = eval(
                new Literal("774")	
        );
        assertEquals(774, result);

        result = eval(
                new Literal("-774")	
        );
        assertEquals(-774, result);

        result = eval(
                new Literal("2"),
                new Symbol("+"),
                new Literal("2")
        );
        assertEquals(4, result);

        result = eval(
                new Literal("2"),
                new Symbol("-"),
                new Literal("2")
        );
        assertEquals(0, result);

        result = eval(
                new Literal("2"),
                new Symbol("-"),
                new Literal("-2")
        );
        assertEquals(4, result);

        result = eval(
                new Literal("2"),
                new Symbol("*"),
                new Literal("2")
        );
        assertEquals(4, result);

        result = eval(
                new Literal("2"),
                new Symbol("/"),
                new Literal("2")
        );
        assertEquals(1, result);
        
        result = eval(
                new Literal("200"),
                new Symbol("/"),
                new Literal("4"),
                new Symbol("/"),
                new Literal("10")
        );
        assertEquals(5, result);

        result = eval(
                new Literal("200"),
                new Symbol("/"),
                new Literal("10"),
                new Symbol("/"),
                new Literal("4")
        );
        assertEquals(5, result);

        result = eval(
                new Literal("2.2"),
                new Symbol("/"),
                new Literal("2")
        );
        assertEquals(1.1F, result);
        
        result = eval(
                new Literal("2"),
                new Symbol("+"),
                new Literal("'2'"),
                new Symbol("+"),
                new Literal("'2'")
        );
        assertEquals("222", result);

        result = eval(
                new Literal("2"),
                new Symbol("+"),
                new Literal("2"),
                new Symbol("+"),
                new Literal("'2'")
        );
        assertEquals("42", result);
    }

    
    @Test
    public void complexExpressions() {
        Object result;
        result = eval(
                new Literal("2"),
                new Symbol("+"),
                new Literal("2"),
                new Symbol("*"),
                new Literal("2")
        );
        assertEquals(6, result);

        result = eval(
                new Literal("2"),
                new Symbol("+"),
                new Literal("3"),
                new Symbol("+"),
                new Literal("4"),
                new Symbol("/"),
                new Literal("2")
        );
        assertEquals(7, result);

        result = eval(
                new Symbol("("),
                new Literal("2"),
                new Symbol("+"),
                new Literal("4"),
                new Symbol(")"),
                new Symbol("*"),
                new Literal("2")
        );
        assertEquals(12, result);

        result = eval(
                new Symbol("("),
                new Literal("2"),
                new Symbol("+"),
                new Literal("4"),
                new Symbol(")"),
                new Symbol("="),
                new Literal("6")
        );
        assertEquals(true, result);

        result = eval(
                new Literal("2"),
                new Symbol("+"),
                new Literal("4"),
                new Symbol("="),
                new Literal("3"),
                new Symbol("||"),
                new Literal("2"),
                new Symbol("*"),
                new Literal("2"),
                new Symbol("="),
                new Literal("4")
        );
        assertEquals(true, result);

        result = eval(
                new Literal("2"),
                new Symbol("+"),
                new Literal("4"),
                new Symbol("="),
                new Literal("3"),
                new Symbol("&&"),
                new Literal("2"),
                new Symbol("*"),
                new Literal("2"),
                new Symbol("="),
                new Literal("4")
        );
        assertEquals(false, result);
        
        result = eval(
                new Symbol("("),
                new Literal("2"),
                new Symbol("+"),
                new Literal("4"),
                new Symbol("="),
                new Literal("3"),
                new Symbol(")"),
                new Symbol("||"),
                new Symbol("("),
                new Literal("2"),
                new Symbol("*"),
                new Literal("2"),
                new Symbol("="),
                new Literal("4"),
                new Symbol(")")
        );
        assertEquals(true, result);

        Context ctx = new Context(Locale.getDefault());        
        
        ctx.define("x", "y", true);
        ctx.define("a", null, true);
        
        result = eval(ctx, 
                new Symbol("x"),
                new Symbol("="),
                new Literal("'y'"),
                new Symbol("||"),
                new Symbol("("),
                new Symbol("x"),
                new Symbol("="),
                new Literal("'z'"),
                new Symbol("&&"),
                new Symbol("a"),
                new Symbol("="),
                new Literal("'value'"),
                new Symbol(")")
        );
        assertEquals(true, result);

        ctx.define("x", "z", true);
        ctx.define("a", "value", true);
        
        result = eval(ctx,
                new Symbol("x"),
                new Symbol("="),
                new Literal("'y'"),
                new Symbol("||"),
                new Symbol("("),
                new Symbol("x"),
                new Symbol("="),
                new Literal("'z'"),
                new Symbol("&&"),
                new Symbol("a"),
                new Symbol("="),
                new Literal("'value'"),
                new Symbol(")")
        );
        assertEquals(true, result);
        
        ctx.define("a", true, true);
        ctx.define("b", false, true);
        ctx.define("c", true, true);
        
        result = eval(ctx,
                new Symbol("!"),
                new Symbol("!"),
                new Symbol("("),
                new Symbol("a"),
                new Symbol("&&"),
                new Symbol("("),
                new Symbol("b"),
                new Symbol("||"),
                new Symbol("c"),
                new Symbol(")"),
                new Symbol(")")
        );
        assertEquals(true, result);
    }

    
    @Test
    public void functions() {
        Context ctx = new Context(Locale.getDefault());
        ctx.define("a", "a", true);
        ctx.define("b", "b", true);
        ctx.define("c", "c", true);

        Object result;
        result = eval(
                new Symbol("concat"),
                new Symbol("("),
                new Literal("'a'"),
                new Symbol(","),
                new Symbol("concat"),
                new Symbol("("),
                new Literal("'b'"),
                new Symbol(","),
                new Literal("'c'"),
                new Symbol(")"),
                new Symbol(")")
        );
        assertEquals("abc", result);

        result = eval(ctx, 
                new Symbol("concat"),
                new Symbol("("),
                new Symbol("a"),
                new Symbol(","),
                new Symbol("concat"),
                new Symbol("("),
                new Symbol("b"),
                new Symbol(","),
                new Symbol("c"),
                new Symbol(")"),
                new Symbol(")")
        );
        assertEquals("abc", result);

        result = eval(
                new Symbol("concat"),
                new Symbol("("),
                new Symbol("concat"),
                new Symbol("("),
                new Literal("'a'"),
                new Symbol(","),
                new Literal("'b'"),
                new Symbol(")"),
                new Symbol(","),
                new Literal("'c'"),
                new Symbol(")")
        );
        assertEquals("abc", result);

        result = eval(ctx,
                new Symbol("concat"),
                new Symbol("("),
                new Symbol("a"),
                new Symbol(","),
                new Symbol("concat"),
                new Symbol("("),
                new Symbol("concat"),
                new Symbol("("),
                new Symbol("b"),
                new Symbol(","),
                new Symbol("c"),
                new Symbol(")"),
                new Symbol(","),
                new Symbol("a"),
                new Symbol(")"),
                new Symbol(")")
        );
        assertEquals("abca", result);

        result = eval(ctx,
                new Symbol("emptyfunc"),
                new Symbol("("),
                new Symbol(")")
        );

        result = eval(ctx,
                new Symbol("concat"),
                new Symbol("("),
                new Symbol("emptyfunc"),
                new Symbol("("),
                new Symbol(")"),
                new Symbol(","),
                new Literal("'ing'"),
                new Symbol(")")
        );
        assertEquals("resulting", result);

        result = eval(ctx, 
                new Symbol("!"),
                new Symbol("("),
                new Literal("'abc'"),
                new Symbol("="),
                new Symbol("concat"),
                new Symbol("("),
                new Literal("'a'"),
                new Symbol(","),
                new Literal("'b'"),
                new Symbol(")"),
                new Symbol(")")
        );
        assertEquals(true, result);
        
    }
    
    @Test
    public void typeOf() throws Exception {
        Context ctx = new Context(Locale.getDefault());
        ctx.define("a", "a", true);

        Object result = eval(ctx,
                new Symbol("typeof"),
                new Symbol("("),
                new Literal("'abc'"),
                new Symbol(")")
        );
        assertEquals("string", result);
        
        result = eval(ctx, 
                new Symbol("typeof"),
                new Symbol("("),
                new Literal("2"),
                new Symbol("+"),
                new Literal("2"),
                new Symbol(")")
        );
        assertEquals("number", result);

        result = eval(ctx, 
                new Symbol("typeof"),
                new Symbol("("),
                new Literal("true"),
                new Symbol("&&"),
                new Literal("false"),
                new Symbol(")")
        );
        assertEquals("boolean", result);
        
        result = eval(ctx, 
                new Symbol("typeof"),
                new Symbol("("),
                new Symbol("{"),
                new Literal("'a'"),
                new Symbol(":"),
                new Literal("'b'"),
                new Symbol("}"),
                new Symbol(")")
        );
        assertEquals("object", result);
        
        result = eval(ctx,
                new Symbol("typeof"),
                new Symbol("("),
                new Symbol("a"),
                new Symbol(")")
        );
        assertEquals("string", result);

        result = eval(ctx, 
                new Symbol("typeof"),
                new Symbol("("),
                new Symbol("concat"),
                new Symbol(")")
        );
        assertEquals("function", result);
    
        result = eval(ctx, 
                new Symbol("typeof"),
                new Symbol("("),
                new Symbol("undefined_symbol"),
                new Symbol(")")
        );
        assertEquals("undefined", result);
    }

    @Test
    public void undefinedFunction() throws Exception {
        try {
            eval(
                    new Symbol("bazza"),
                    new Symbol("("),
                    new Symbol("a"),
                    new Symbol(","),
                    new Symbol("b"),
                    new Symbol(","),
                    new Symbol("c"),
                    new Symbol(")")
            );
            fail("Should not succeed");
        } catch (RuntimeException e) {
            // Expected
        }
    }
    

    @Test
    public void lists() {
        Object result;
        List<?> list;
        result = eval(
                new Symbol("#"),
                new Symbol("("),
                new Symbol(")")
        );
        list = (List<?>) result;
        assertTrue(list.isEmpty());
        
        result = eval(
                new Symbol("#"),
                new Symbol("("),
                new Literal("'a'"),
                new Symbol(")")
        );
        list = (List<?>) result;
        assertEquals(1, list.size());
        assertEquals("a", list.get(0));
        
        result = eval(
                new Symbol("#"),
                new Symbol("("),
                new Literal("'a'"),
                new Symbol(","),
                new Literal("'b'"),
                new Symbol(")")
        );
        list = (List<?>) result;
        assertEquals(2, list.size());
        assertEquals("a", list.get(0));
        assertEquals("b", list.get(1));
    }
    
    @Test
    public void malformedLists() {
        try {
            eval(
                    new Symbol("{{"),
                    new Literal("'a'"),
                    new Literal("'b'"),
                    new Literal("'c'"),
                    new Symbol("}}")
            );
            fail("Should not succeed");
        } catch (RuntimeException e) {
            // Expected
        }
    }
    
    @Test
    public void malformedMaps() {
        try {
            eval(
                    new Symbol("{"),
                    new Literal("'a'"),
                    new Symbol(":"),
                    new Symbol("}")
            );
            fail("Should not succeed");
            eval(
                    new Symbol("{"),
                    new Literal("'a'"),
                    new Symbol(":"),
                    new Literal("'b'"),
                    new Literal("'c'"),
                    new Symbol(":"),
                    new Literal("'d'"),
                    new Symbol("}")
            );
            fail("Should not succeed");
        } catch (RuntimeException e) {
            // Expected
        }
    }
    
    @Test
    public void basicMaps() {
        Object result;
        Map<?,?> map;
        result = eval(
                new Symbol("{"),
                new Symbol(":"),
                new Symbol("}")
        );
        map = (Map<?,?>) result;
        assertTrue(map.isEmpty());

        result = eval(
                new Symbol("{"),
                new Literal("'a'"),
                new Symbol(":"),
                new Literal("'b'"),
                new Symbol("}")
        );
        map = (Map<?,?>) result;
        assertEquals(1, map.size());
        assertEquals("b", map.get("a"));
        
        result = eval(
                new Symbol("{"),
                new Literal("'a'"),
                new Symbol(":"),
                new Literal("'b'"),
                new Symbol(","),
                new Literal("'c'"),
                new Symbol(":"),
                new Symbol("null"),
                new Symbol(","),
                new Literal("'d'"),
                new Symbol(":"),
                new Symbol("{"),
                new Literal("'e'"),
                new Symbol(":"),
                new Literal("'f'"),
                new Symbol("}"),
                new Symbol("}")
        );
        map = (Map<?,?>) result;
        assertEquals(3, map.size());
        assertEquals("b", map.get("a"));
        assertNull(map.get("c"));
        Map<?,?> submap = (Map<?,?>) map.get("d");
        assertNotNull(submap);
        assertEquals(1, submap.size());
        assertEquals("f", submap.get("e"));
        
        result = eval(
                new Symbol("{"),
                new Literal("'a'"),
                new Symbol(":"),
                new Literal("2"),
                new Symbol("+"),
                new Literal("2"),
                new Symbol(","),
                new Literal("3"),
                new Symbol("*"),
                new Literal("3"),
                new Symbol(":"),
                new Literal("'d'"),
                new Symbol("}")
        );
        map = (Map<?,?>) result;
        assertEquals(4, map.get("a"));
        assertEquals("d", map.get(9));
        

        result = eval(
                new Symbol("{"),
                new Literal("'a'"),
                new Symbol(":"),
                new Symbol("{"),
                new Literal("'b'"),
                new Symbol(":"),
                new Literal("'c'"),
                new Symbol("}"),
                new Symbol(","),
                new Symbol("{"),
                new Literal("'b'"),
                new Symbol(":"),
                new Literal("'c'"),
                new Symbol("}"),
                new Symbol(":"),
                new Literal("'a'"),
                new Symbol("}")
        );
        assertTrue(result instanceof Map<?,?>);
    }
    
    @Test
    public void mapsAndFunctions() {
        Object result;

        result = eval(
                new Symbol("{"),
                new Literal("'a'"),
                new Symbol(":"),
                new Symbol("concat"),
                new Symbol("("),
                new Literal("'b'"),
                new Symbol(","),
                new Symbol("get"),
                new Symbol("("),
                new Symbol("{"),
                new Literal("'c'"),
                new Symbol(":"),
                new Literal("'d'"),
                new Symbol("}"),
                new Symbol(","),
                new Literal("'c'"),
                new Symbol(")"),
                new Symbol(")"),
                new Symbol("}")
        );
        assertTrue(result instanceof Map<?,?>);
        Map<?,?> m = (Map<?,?>) result;
        assertEquals("bd", m.get("a"));

        Context ctx = new Context(Locale.getDefault());
        Map<Object, Object> a = new HashMap<Object, Object>();
        a.put("b", "c");
        ctx.define("a", a, true);
        result = eval(ctx,
                new Symbol("a"),
                new Symbol("!="),
                new Symbol("null"),
                new Symbol("&&"),
                new Symbol("a"),
                new Symbol("."),
                new Symbol("b"),
                new Symbol("="),
                new Literal("'c'")
        );
        assertEquals(Boolean.TRUE, result);

        Map<Object, Object> b = new HashMap<Object, Object>();
        b.put("c", new HashMap<Object, Object>());
        a = new HashMap<Object, Object>();
        a.put("b", b);
        ctx.define("a", a, true);
        result = eval(ctx,
                new Symbol("a"),
                new Symbol("."),
                new Symbol("b"),
                new Symbol("."),
                new Symbol("c"),
                new Symbol("."),
                new Symbol("length"),
                new Symbol("("),
                new Symbol(")"),
                new Symbol("="),
                new Literal("0")
        );
        assertEquals(Boolean.TRUE, result);

        
        ctx.define("a", null, true);
        result = eval(ctx,
                new Symbol("a"),
                new Symbol("!="),
                new Symbol("null"),
                new Symbol("&&"),
                new Symbol("a"),
                new Symbol("."),
                new Symbol("b"),
                new Symbol("="),
                new Literal("'c'")
        );
        assertEquals(Boolean.FALSE, result);
        
        ctx = new Context(Locale.getDefault());
        a = new HashMap<Object, Object>();
        a.put("b", "c");
        ctx.define("a", a, true);

        // length(a)
        result = eval(ctx,
                new Symbol("length"),
                new Symbol("("),
                new Symbol("a"),
                new Symbol(")")
        );
        assertEquals(1, result);
        
        // a.length()
        result = eval(ctx,
                new Symbol("a"),
                new Symbol("."),
                new Symbol("length"),
                new Symbol("("),
                new Symbol(")")
        );
        assertEquals(1, result);
        
        result = eval(ctx,
                new Symbol("a"),
                new Symbol("."),
                new Symbol("get"),
                new Symbol("("),
                new Literal("'b'"),
                new Symbol(")")
        );
        assertEquals("c", result);
        
        result = eval(ctx,
                new Symbol("a"),
                new Symbol("."),
                new Symbol("get"),
                new Symbol("("),
                new Literal("'xyz'"),
                new Symbol(")")
        );
        assertNull(result);

        result = eval(ctx,
                new Symbol("map"),
                new Symbol("("),
                new Literal("'a'"),
                new Symbol(","),
                new Literal("'b'"),
                new Symbol(","),
                new Literal("'c'"),
                new Symbol(","),
                new Literal("'d'"),
                new Symbol(")"),
                new Symbol("."),
                new Symbol("length"),
                new Symbol("("),
                new Symbol(")")
        );
        assertEquals(2, result);
        
    }
    
    @Test
    public void accessors() {
        
        Object result;

        result = eval(
                new Symbol("{"),
                new Literal("'a'"),
                new Symbol(":"),
                new Literal("'b'"),
                new Symbol("}"),
                new Symbol("."),
                new Symbol("a")
        );
        assertEquals("b", result);
        
        result = eval(
                new Symbol("{"),
                new Literal("'a'"),
                new Symbol(":"),
                new Symbol("#"),
                new Symbol("("),
                new Literal("'b'"),
                new Symbol(","),
                new Literal("'c'"),
                new Symbol(")"),
                new Symbol("}"),
                new Symbol("."),
                new Symbol("a"),
                new Symbol("."),
                new Literal("1")
        );
        assertEquals("c", result);
        
        Context ctx = new Context(Locale.getDefault());
        Map<Object, Object> m1 = new HashMap<Object, Object>();
        Map<Object, Object> m2 = new HashMap<Object, Object>();
        m1.put("b", m2);
        m2.put("c", new Object[]{"d", 2});
        ctx.define("a", m1, true);
        result = eval(ctx,
                new Symbol("a"),
                new Symbol("."),
                new Symbol("b"),
                new Symbol("."),
                new Symbol("c"),
                new Symbol("."),
                new Literal("1"),
                new Symbol("+"),
                new Literal("20")
        );
        assertEquals(22, result);

        result = eval(ctx,
                new Symbol("a"),
                new Symbol("."),
                new Symbol("b"),
                new Symbol("."),
                new Symbol("c"),
                new Symbol("."),
                new Symbol("get"),
                new Symbol("("),
                new Literal("1"),
                new Symbol(")"),
                new Symbol("+"),
                new Literal("20")
        );
        assertEquals(22, result);
    }

    @Test
    public void tooManyArguments() throws Exception {
        try {
            eval(
                    new Symbol("emptyfunc"),
                    new Symbol("("),
                    new Literal("'a'"),
                    new Symbol(","),
                    new Literal("'b'"),
                    new Symbol(","),
                    new Literal("'c'"),
                    new Symbol(")")
            );
            fail("Should not succeed");
        } catch (RuntimeException e) {
            // Expected
        }
    }

    @Test
    public void varArgs() throws Exception {
        Object result;
        result = eval(
                new Symbol("("),
                new Literal("'a'"),
                new Symbol(")")
        );
        assertEquals("a", result);
        
        result = eval(
                new Symbol("concat"),
                new Symbol("("),
                new Symbol(")")
        );
        assertEquals("", result);
        
        result = eval(
                new Symbol("concat"),
                new Symbol("("),
                new Literal("'a'"),
                new Symbol(")")
        );
        assertEquals("a", result);

        result = eval(
                new Symbol("concat"),
                new Symbol("("),
                new Literal("'a'"),
                new Symbol(","),
                new Literal("'b'"),
                new Symbol(","),
                new Literal("'c'"),
                new Symbol(")")
        );
        assertEquals("abc", result);

        result = eval(
                new Symbol("concat"),
                new Symbol("("),
                new Literal("'a'"),
                new Symbol(","),
                new Literal("'b'"),
                new Symbol(")")
        );
        assertEquals("ab", result);

        result = eval(
                new Symbol("concat"),
                new Symbol("("),
                new Literal("'a'"),
                new Symbol(","),
                new Literal("'b'"),
                new Symbol(","),
                new Symbol("concat"),
                new Symbol("("),
                new Symbol("concat"),
                new Symbol("("),
                new Literal("'x'"),
                new Symbol(","),
                new Literal("'y'"),
                new Symbol(")"),
                new Symbol(","),
                new Literal("'c'"),
                new Symbol(","),
                new Literal("'d'"),
                new Symbol(")"),
                new Symbol(","),
                new Literal("'e'"),
                new Symbol(")")
        );
        assertEquals("abxycde", result);
    }

    @Test
    public void malformedExpressions() throws Exception {
        try {
            eval(
                    new Symbol(","),
                    new Symbol("("),
                    new Literal("2"),
                    new Symbol("+")
            );
            fail("Should not succeed");
        } catch (RuntimeException e) {
            // Expected
        }

        try {
            eval(
                    new Symbol("concat"),
                    new Literal("2"),
                    new Symbol(","),
                    new Literal("2")
            );
            fail("Should not succeed");
        } catch (RuntimeException e) {
            // Expected
        }

        try {
            eval(
                    new Symbol("concat"),
                    new Symbol("("),
                    new Literal("'a'"),
                    new Symbol(","),
                    new Symbol("concat"),
                    new Symbol("("),
                    new Literal("'b'"),
                    new Symbol(","),
                    new Literal("'c'"),
                    new Symbol(")")
            );
            fail("Should not succeed");
        } catch (RuntimeException e) {
            // Expected
        }
    }

    private Object eval(Token... args) {
        Context ctx = new Context(Locale.getDefault());
        return eval(ctx, args);
    }

    private Object eval(Context ctx, Token... args) {
        Expression expr = new Expression(this.functions, Arrays.asList(args));
        return expr.evaluate(ctx);
    }
}
