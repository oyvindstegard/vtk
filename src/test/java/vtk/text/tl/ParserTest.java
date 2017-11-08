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
package vtk.text.tl;

import static org.junit.Assert.assertEquals;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import vtk.text.tl.Parser.Directive;
import vtk.text.tl.Parser.Handler;
import vtk.text.tl.expr.Concat;
import vtk.text.tl.expr.Expression.FunctionResolver;

public class ParserTest {

    private List<DirectiveHandler> directiveHandlers;

    @Before
    public void setUp() throws Exception {
        FunctionResolver functions = new FunctionResolver();
        functions.addFunction(new Concat(new Symbol("concat")));

        directiveHandlers = Arrays.asList(new DirectiveHandler[] {
                new IfHandler(functions),
                new ValHandler(null, functions),
                new ListHandler(functions),
                new DefineHandler(functions),
                new DirectiveHandler() {
                    @Override
                    public String[] tokens() {
                        return new String[] { "abc", "/abc" };
                    }
                    @Override
                    public void directive(Directive directive,
                            TemplateContext context) {
                        if ("abc".equals(directive.name())) {
                            context.push(new DirectiveState(directive));
                        } else {
                            final DirectiveState state = context.pop();
                            context.add(new Node() {
                                @Override
                                public boolean render(Context ctx, Writer out)
                                        throws Exception {
                                    return state.nodes().render(ctx, out);
                                }
                            });
                            
                        }
                    }
                }
        });
    }

    @Test
    public void basicSyntax() throws Exception {
        
        Context ctx = new Context(Locale.getDefault());

        String result = parseAndRender("[def x 22][val x]", ctx);
        assertEquals("22", result);
        
        result = parseAndRender("a [abc]wrapper around [abc]inner [/abc]text[/abc]", ctx);
        assertEquals("a wrapper around inner text", result);
        
        result = parseAndRender("[]", ctx);
        assertEquals("[]", result);
        
        try {
            result = parseAndRender("[def x", ctx);
            throw new RuntimeException("Error: parser allows unterminated directive");
        } catch (Exception e) {
            // Expected
        }

        result = parseAndRender("[def x \"22\"][val x]", ctx);
        assertEquals("22", result);
        
        result = parseAndRender(" [def x 2][val x]", ctx);
        assertEquals(" 2", result);

        result = parseAndRender("\r\n[def x 2]\r[val x]", ctx);
        assertEquals("\n\n2", result);
        
        result = parseAndRender("\r\n[!--comment--]", ctx);
        assertEquals("\n", result);

        result = parseAndRender("\r\n[#--<!-- HTML comment --> --]", ctx);
        assertEquals("\n<!-- HTML comment --> ", result);

        
   
        result = parseAndRender("\r\n[def x-x \"22\"]\r\n[def x-y x-x]\r\n[val x-y]", ctx);
        assertEquals("\n\n\n22", result);

        result = parseAndRender("[def x \"[ab\\\"c\\\"]\"][val x # unescaped]", ctx);
        assertEquals("[ab\"c\"]", result);
        
        String template = 
            "[def foo \"bar]\"]"
            + "\n"
            + "[!-- comment\n [] --]" 
            + "\nsome text[ mixed #--with\n brackets and quotes ' '\n"
            + "[#-- [raw node] \n [[[[[[]]]]]] \n -- \n -- [this is not a directive]-->--]" 
            + "\n";

        NodeList nodeList = parse(template);
        List<Node> nodes = nodeList.getNodes();
        assertEquals(6, nodes.size());

        StringWriter writer = new StringWriter();
        nodes.get(0).render(ctx, writer);
        assertEquals("", writer.toString());
        
        writer = new StringWriter();
        nodes.get(1).render(ctx, writer);
        assertEquals("\n", writer.toString());
        
        writer = new StringWriter();
        nodes.get(2).render(ctx, writer);
        assertEquals("", writer.toString());

        writer = new StringWriter();
        nodes.get(3).render(ctx, writer);
        assertEquals("\nsome text[ mixed #--with\n brackets and quotes ' '\n", writer.toString());
        
        writer = new StringWriter();
        nodes.get(4).render(ctx, writer);
        assertEquals(" [raw node] \n [[[[[[]]]]]] \n -- \n -- [this is not a directive]-->", writer.toString());
        
        writer = new StringWriter();
        nodes.get(5).render(ctx, writer);
        assertEquals("\n", writer.toString());

        nodeList = parse("[#--<!--[if IE]>conditional comment<![endif]-->--]");
        nodes = nodeList.getNodes();
        assertEquals(1, nodes.size());
        writer = new StringWriter();
        nodes.get(0).render(ctx, writer);
        assertEquals("<!--[if IE]>conditional comment<![endif]-->", writer.toString());
        
        template = "<!--[if IE 7]>\n"
            + "<link type=\"text/css\" rel=\"stylesheet\" media=\"all\" href=\"...style_ie7.css\" />\n"
            + "<![endif]-->";
        try {
            parseAndRender(template, ctx);
            throw new Exception("Should fail");
        } catch (RuntimeException e) { }
  
        template = "[#--" + template + "--]";
        parse(template);
    }
    
    @Test
    public void directiveArgs() {
        List<Token> args = parseDirective("[test arg1 \"arg2\" 100]");
        assertEquals(args.size(), 3);
        assertEquals(new Symbol("arg1"), args.get(0));
        assertEquals(new Literal("\"arg2\""), args.get(1));
        assertEquals(new Literal("100"), args.get(2));

        args = parseDirective("[test concat('foo', 'bar')]");
        assertEquals(6, args.size());
        assertEquals(new Symbol("concat"), args.get(0));
        assertEquals(new Symbol("("), args.get(1));
        assertEquals(new Literal("\"foo\""), args.get(2));
        assertEquals(new Symbol(","), args.get(3));
        assertEquals(new Literal("'bar'"), args.get(4));
        assertEquals(new Symbol(")"), args.get(5));

        args = parseDirective("[test {'foo', 'bar'}.1]");
        assertEquals(7, args.size());
        assertEquals(new Symbol("{"), args.get(0));
        assertEquals(new Literal("'foo'"), args.get(1));
        assertEquals(new Symbol(","), args.get(2));
        assertEquals(new Literal("'bar'"), args.get(3));
        assertEquals(new Symbol("}"), args.get(4));
        assertEquals(new Symbol("."), args.get(5));
        assertEquals(new Literal("1"), args.get(6));
    }
    
    @Test
    public void testOpenClose() throws Exception {
        Context ctx = new Context(Locale.getDefault());
        String result = parseAndRender("[abc]foo[/abc]", ctx);
        assertEquals("foo", result);
    }
    
    @Test
    public void valNode() throws Exception {
        Context ctx = new Context(Locale.getDefault());
        String result = parseAndRender("[val concat('a', 'b')]", ctx);
        assertEquals("ab", result);
        
        String html = "<p>a.</p> <p>b.</p>";
        result = parseAndRender("[val '" + html + "']", ctx);
        assertEquals("&lt;p&gt;a.&lt;/p&gt; &lt;p&gt;b.&lt;/p&gt;", result);
        
        result = parseAndRender("[val '" + html + "' # unescaped]", ctx);
        assertEquals(html, result);
        
        result = parseAndRender("[val '" + html + "' # flatten]", ctx);
        assertEquals("a. b.", result);
    }
    
    @Test
    public void ifElse() throws Exception {
        Context ctx = new Context(Locale.getDefault());
        String result = parseAndRender("[if true]yes[else]no[endif]", ctx);
        assertEquals("yes", result);

        result = parseAndRender("[if 4 / 2 = 2]yes[else]no[endif]", ctx);
        assertEquals("yes", result);
        
        ctx.define("var1", true, true);
        result = parseAndRender("[if var1]yes[else]no[endif]", ctx);
        assertEquals("yes", result);

        ctx.define("var1", false, true);
        result = parseAndRender("[if var1]yes[else]no[endif]", ctx);
        assertEquals("no", result);

        ctx.define("var1", false, true);
        ctx.define("var2", true, true);
        result = parseAndRender("[if var1]var1[elseif var2]var2[endif]", ctx);
        assertEquals("var2", result);
        
        ctx.define("var1", false, true);
        ctx.define("var2", false, true);
        result = parseAndRender("[if var1]var1[elseif var2]var2[else]none[endif]", ctx);
        assertEquals("none", result);

        ctx.define("var1", false, true);
        ctx.define("var2", true, true);
        result = parseAndRender("[if var1]var1[elseif var2][if var1]var1[elseif var2]var2[endif][else]none[endif]", ctx);
        assertEquals("var2", result);
        
        Map<Object, Object> map = new HashMap<Object, Object>();
        map.put("a", 22);
        ctx.define("map", map, true);
        result = parseAndRender("[if map.a = 22]yes[else]no[endif]", ctx);
        assertEquals("yes", result);

    }

    @Test
    public void list() throws Exception {
        Context ctx = new Context(Locale.getDefault());
        ctx.define("lst", Arrays.asList(new Integer[] {2, 2}), true);
        String result = parseAndRender("[def sum 0][list lst elem][def sum sum + elem][endlist][val sum]", ctx);
        assertEquals("4", result);
        assertEquals(4, ctx.get("sum"));
        
    }
    
    private String parseAndRender(String template, Context ctx) throws Exception {
        NodeList result = parse(template);
        StringWriter out = new StringWriter();
        result.render(ctx, out);
        return out.toString();
    }
    
    private NodeList parse(String template) throws RuntimeException {
        Reader reader = new StringReader(template);
        final NodeList result = new NodeList();
        TemplateParser parser = new TemplateParser(reader, directiveHandlers, new TemplateHandler() {
            @Override
            public void success(NodeList nodeList) {
                for (Node node: nodeList) result.add(node);
            }
            @Override
            public void error(String message, int line) {
                throw new RuntimeException("Error at line " + line + ": " + message);
            }
        });
        parser.parse();
        return result;
    }

    private List<Token> parseDirective(String template) {
        Reader reader = new StringReader(template);
        final List<Token> tokens = new ArrayList<Token>();
        
        new Parser(reader, new Handler() {
            @Override
            public void start() {
            }
            @Override
            public void directive(Directive directive) {
                tokens.addAll(directive.args());
            }
            @Override
            public void text(String text) {
            }
            @Override
            public void raw(String text) {
            }
            @Override
            public void comment(String text) {
            }
            @Override
            public void error(String message, int line) {
            }
            @Override
            public void end() {
            }
        }).parse();
        return tokens;
    }

}
