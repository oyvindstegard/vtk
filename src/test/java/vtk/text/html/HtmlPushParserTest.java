/* Copyright (c) 2015, University of Oslo, Norway
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
package vtk.text.html;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;

import org.junit.Test;

import vtk.text.html.HtmlPushParser.Attribute;
import vtk.text.html.HtmlPushParser.CommentNode;
import vtk.text.html.HtmlPushParser.DoctypeNode;
import vtk.text.html.HtmlPushParser.ElementNode;
import vtk.text.html.HtmlPushParser.Node;
import vtk.text.html.HtmlPushParser.NodeHandler;
import vtk.text.html.HtmlPushParser.TextNode;
import vtk.text.html.HtmlPushParser.Undefined;

public class HtmlPushParserTest {

    @Test
    public void doctypes() {
        List<String> doctypes = Arrays.asList(
                "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\"" + 
                        "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">",
                "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\"" +
                        "\"http://www.w3.org/TR/html4/strict.dtd\">",
                "<!DOCTYPE HTML>");
        
        assertTrue(doctypes.stream()
                .flatMap(d -> parseToList(d).stream())
                .allMatch(x -> x instanceof DoctypeNode));
        
    }
    

    @Test
    public void basicElements() {
        List<Node> nodes = parseToList("<element1><element2><element3>");
        assertEquals(3, nodes.size());
        assertEquals("<element1>", nodes.get(0).toHtml());
        assertEquals("<element2>", nodes.get(1).toHtml());
        assertEquals("<element3>", nodes.get(2).toHtml());        
    }
    
    @Test
    public void attributes() {
        List<Node> nodes = parseToList("<element attr1=\"value1\" attr2=value2>");
        assertEquals(1, nodes.size());
        assertTrue(nodes.get(0) instanceof ElementNode);
        ElementNode elem = (ElementNode) nodes.get(0);
        List<Attribute> attrs = elem.attributes();
        assertEquals(attrs.size(), 2);
        assertEquals("attr1=\"value1\"", attrs.get(0).toHtml());
        assertEquals("\"value1\"", attrs.get(0).quotedValue());
        
        String str = "<element1 attr1=\"value1\">";
        assertEquals(str, parseToString(str));
        str = "<element1 attr1='value1'>";
        assertEquals(str, parseToString(str));
        str = "<element1 attr1=value1 attr2>";
        assertEquals(str, parseToString(str));
        
        str = "<element1 attr1 = \"value1 with spaces in it\">";
        assertEquals(str, parseToString(str));
        
        str = "<element1 attr1 = value1>";
        assertEquals(str, parseToString(str));
    }
    
    private static final List<String> SIMPLE_XHTML_PAGE = Arrays.asList(
            "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">", "\n", 
            "<html>", "\n", 
            "<head attr1=\"foo\" attr2=\"bar\">", "\n", 
            "<object>", "foobar", "</object>", "\n", 
            "<object>", "foobbar", "</object>",
            "<object>", "syt", "</object>",
            "<object>", "<element>", "<under>", "foo", "</under>", "</element>", "</object>",
            "<meta name=\"keywords\" content=\"My keywords\"/>", "\n", 
            "<title>", "My title", "</title>", "\n", 
            "</head>", "\n", 
            "<body>", "The body", "</body>", "\n", 
            "</html>");

    @Test
    public void simpleHtmlPage() {
        List<String> result = toHtml(parseToList(SIMPLE_XHTML_PAGE));
        assertEquals(SIMPLE_XHTML_PAGE, result);
    }
    

    @Test
    public void nodeFiltering() {
        
        Attribute myAttr = Attribute.create("abc", "def");
        
        final List<Node> result = new ArrayList<>();
        
        parse(SIMPLE_XHTML_PAGE, new NodeHandler() {
            public boolean node(Node node) {
                
                if (node instanceof ElementNode) {
                    ElementNode el = ((ElementNode) node);
                    ElementNode el2 = el.withAttribute(myAttr).removeAttribute("attr2");
                    result.add(el2);
                }
                else if (node instanceof TextNode) {
                    String s = node.as(TextNode.class).toHtml();
                    if (!s.trim().isEmpty())
                        result.add(TextNode.create((s.replaceAll(".", "x"))));
                    return true;
                }
                else result.add(node);
                return true;
            }
            public void end() { }
            public void error(String msg) { }
        });
        
        assertFalse(result.stream().anyMatch(n -> n.toHtml().contains("attr2")));

        result.stream().filter(n -> n instanceof TextNode)
                .allMatch(n -> ((TextNode) n).toHtml().matches("x+"));
        
    }

    private static final String SIMPLE_XHTML_PAGE_WITH_DIRECTIVES = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n"
            + "<html>\n"
            + "  <head attr1=\"foo\" attr2=\"bar\">\n"
            + "    <object>foobar</object>\n"
            + "    <meta name=\"keywords\" content=\"My keywords\"/>\n"
            + "    <title>My title</title>\n"
            + "  </head>\n"
            + "  <body>"
            + "    <div>"
            + "      <span>Last modified"
            + "        <b>${resource:property id=[lastModified]}</b>"
            + "        <s><em>test</em></s>"
            + "      </span>"
            + "      ${resource:property id=[lastModified]}"
            + "    </div>"
            + "  </body>\n"
            + "</html>\n";

    @Test
    public void nestedTagFiltering() {
        final Map<String, ElementNode> map = new HashMap<>();
        final Stack<String> stack = new Stack<>();
        
        parse(SIMPLE_XHTML_PAGE_WITH_DIRECTIVES, new NodeHandler() {
            public boolean node(Node node) {
                if (!node.is(ElementNode.class)) return true;
                
                ElementNode element = node.as(ElementNode.class);
                if (element.closing() && !stack.isEmpty()) {
                    stack.pop();
                }
                if (!element.closing()) {
                    if (stack.empty()) stack.push(element.name());
                    else stack.push("." + element.name());
                    
                    String key = stack.stream().reduce("", (x, y) -> x + y);

                    map.put(key, element);
                    if (element.selfClosing() && !stack.isEmpty()) {
                        stack.pop();
                    }
                }
                return true;
            }
            public void end() { }
            public void error(String msg) { }
        });

        assertNotNull(map.get("html"));
        assertNotNull(map.get("html.head"));
        assertNotNull(map.get("html.head.object"));
        assertNotNull(map.get("html.head.meta"));
        assertNotNull(map.get("html.head.title"));
        assertNotNull(map.get("html.body"));
        assertNotNull(map.get("html.body.div"));
        assertNotNull(map.get("html.body.div.span"));
        assertNotNull(map.get("html.body.div.span.b"));
        assertNotNull(map.get("html.body.div.span.s"));
        assertNotNull(map.get("html.body.div.span.s.em"));
    }

    private static final String UNFORMATTED_STRING = "  body The div page div body";

    @Test
    public void unformattedString() {
        String s = parseToString(UNFORMATTED_STRING);
        assertEquals(UNFORMATTED_STRING, s);
    }

    private static final String PARTIAL_HTML_PAGE = "  <body>The <div>page</div></body>";

    @Test
    public void partialHtmlPage() {
        List<Node> list = parseToList(PARTIAL_HTML_PAGE);
        assertEquals(7, list.size());
        String result = list.stream().reduce("", (s, n) -> s + n.toHtml(), (s1, s2) -> s1 + s2);
        assertEquals(PARTIAL_HTML_PAGE, result);
    }

    private static final String SINGLE_DOUBLE_ATTR_QUOTES = "<body attr1=\"value'1\"><div attr2='value\"2'></div></body>";

    @Test
    public void attrQuotes() {
        List<Node> list = parseToList(SINGLE_DOUBLE_ATTR_QUOTES);
        assertTrue(list.stream().allMatch(n -> n instanceof ElementNode));
        List<ElementNode> elements = list.stream()
                .map(n -> n.as(ElementNode.class)).collect(Collectors.toList());

        assertEquals("value'1", elements.get(0).attribute("attr1").get().value());
        assertEquals("value\"2", elements.get(1).attribute("attr2").get().value());
    }

    private static final String VALID_HTML_401_TRANS = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">\n"
            + "<html>\n"
            + "  <head>\n"
            + "    <!-- My comment #1 -->\n"
            + "    <link REL=\"stylesheet\" HREF=\"/some/stylesheet.css\">\n"
            + "    <link REL=\"stylesheet\" HREF=\"/some/other/stylesheet.css\">\n"
            + "    <title>My title</title>\n"
            + "  </head>\n"
            + "<body>\n"
            + "  <div class=\"body\">\n"
            + "    <div id=\"titleContainer\">\n"
            + "      <div class=\"class1 class2\">\n"
            + "        <h1>Header 1</h1>\n"
            + "      </div>\n"
            + "    </div>\n"
            + "    <!-- My comment #2 -->\n"
            + "    <br>\n"
            + "    <form action=\"http://foo.bar/post.html\" method=\"POST\">\n"
            + "      <select name=val1>\n"
            + "      <select name=val2>\n"
            + "      <select name=val3 selected>\n"
            + "    </form>\n"
            + "    <hr>\n"
            + "    <table class=\"myListing\">\n"
            + "      <!-- My comment #3 -->\n"
            + "      <tr class=\"listingHeader\">\n"
            + "        <th class=\"sortColumn name\"><a href=\"http://foo.bar?sort=1\">Name</a></th>\n"
            + "        <th class=\"size\"><a href=\"http://foo.bar?sort=2\">Size</a></th>\n"
            + "        <th class=\"lastModified\"><a href=\"http://foo.bar?sort=3\">Last modified</a></th>\n"
            + "      </tr>\n"
            + "      <tr class=\"listingRow\">\n"
            + "        <td class=\"name\">Some name</td>\n"
            + "        <td class=\"size\">200</td>\n"
            + "        <td class=\"lastModified\">2007-01-30</td>\n"
            + "      </tr>\n"
            + "    </table>\n"
            + "  </div>\n"
            + "  <div class = \"contact\" >\n"
            + "    <a href=\"http://foo.bar/contact\">Contact</a>\n"
            + "  </div>\n"
            + "  </div>\n"
            + "</body>\n"
            + "</html>\n";

    @Test
    public void validHtml401Trans() {
        List<Node> nodes = parseToList(VALID_HTML_401_TRANS);
        
        List<CommentNode> comments = nodes.stream().filter(x -> x instanceof CommentNode)
                .map(x -> x.as(CommentNode.class)).collect(Collectors.toList());

        assertTrue(comments.stream().allMatch(c -> 
            c.toHtml().startsWith("<!--") && c.toHtml().endsWith("-->")));
        
        List<String> hrefs = nodes.stream()
                .filter(n -> n instanceof ElementNode)
                .map(n -> (ElementNode) n)
                .filter(el -> el.name().equalsIgnoreCase("a"))
                .map(a -> a.attribute("href"))
                .filter(attr -> attr.isPresent() && attr.get().value() != null)
                .map(attr -> attr.get().value())
                .collect(Collectors.toList());

        hrefs.stream().forEach(href -> assertTrue(href.startsWith("http://")));
    }

    private static final String SIMPLE_HTML_401_FRAMESET = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Frameset//EN\" \"http://www.w3.org/TR/html4/frameset.dtd\">\n"
            + "<html>\n"
            + "  <head>\n"
            + "    <title>Simple frameset document</title>\n"
            + "  </head>\n"
            + "<frameset cols=\"20%, 80%\">\n"
            + "  <frameset rows=\"100, 200\">\n"
            + "    <frame src=\"frame1.html\">\n"
            + "    <frame src=\"frame2.html\">\n"
            + "  </frameset>\n"
            + "</frameset>\n" + "</html>\n";

    @Test
    public void validHtml401Frameset() {
        List<Node> list = parseToList(SIMPLE_HTML_401_FRAMESET);
        Map<String, List<ElementNode>> grouped = list.stream()
            .filter(n -> n instanceof ElementNode)
            .map(n -> (ElementNode) n)
            .filter(n -> !n.closing() && n.name().matches("(frameset|frame)"))
            .collect(Collectors.groupingBy(ElementNode::name));

        assertEquals(2, grouped.get("frame").size());
        assertEquals(2, grouped.get("frameset").size());
    }

    @Test
    public void malformedFragments() {
        assertEquals("<html\n</html>", parseToList("<html\n</html>").stream()
            .filter(n -> (n instanceof Undefined))
            .map(n -> n.toHtml())
            .collect(Collectors.joining()));
        
        assertTrue(parseToList("<div attr=\" <span>foo</span></div>").stream()
            .allMatch(n -> n instanceof Undefined));
        
        List<Node> list = parseToList("<element <!-- with comment -->>");
        assertEquals(2, list.size());
        assertTrue(list.get(0) instanceof Undefined);
        assertTrue(list.get(1) instanceof TextNode);
        assertTrue(list.get(1).toHtml().equals(">"));
    }

    private static final String MALFORMED_XHTML_PAGE = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n"
            + "<html\n"
            + "  <head attr1=\"foo\" attr2=\"bar\"\n"
            + "    <object>foo</object\n"
            + "    <object>bar</object"
            + "    <title>My title/title>\n"
            + "  </head>\n"
            + "  <body>The body</body>\n"
            + "</html>\n";

    @Test
    public void malformedXHtml() {
        List<Node> list = parseToList(MALFORMED_XHTML_PAGE);
        assertTrue(list.stream().filter(n -> n instanceof Undefined).findAny().isPresent());
        assertTrue(list.stream().filter(n -> n instanceof ElementNode).findAny().isPresent());
        assertTrue(list.stream().filter(n -> n instanceof TextNode).findAny().isPresent());
    }

    @Test
    public void tables() {
        parseToList("test-tables.html", "utf-8");
    }

    @Test
    public void html401Strict() {
        parseToList("test-html401strict.html", "utf-8");
    }

    @Test
    public void htmlFile1() {
        parseToList("test-1.html", "utf-8");
    }

    
    @Test
    public void testConditionalComments() {
        String html = 
                "<html>" + 
                "<!--[if IE 6]>\n" + 
                "<link rel=\"stylesheet\" href=\"ie6.css\" type=\"text/css\"/>" +
                "Special instructions for IE 6 here\n" + 
                "<![endif]-->\n" + 
                "</html>";
        List<Node> list = parseToList(html);
        assertNotNull(list);
        assertEquals(list.size(), 4);
        assertTrue(list.get(0) instanceof ElementNode);
        assertTrue(list.get(1) instanceof CommentNode);
        assertTrue(list.get(2) instanceof TextNode);
        assertTrue(list.get(3) instanceof ElementNode);
    }
    
    private String parseToString(String html) {
        List<Node> nodes = parseToList(html);
        StringBuilder result = new StringBuilder();
        for (Node node: nodes) result.append(node.toHtml());
        return result.toString();
    }
    
    private List<String> toHtml(List<Node> nodes) {
        List<String> result = new ArrayList<>();
        for (Node node: nodes)
            result.add(node.toHtml());
        return result;
    }

    private List<Node> parseToList(List<String> html) {
        final List<Node> result = new ArrayList<>();
        HtmlPushParser parser = new HtmlPushParser(new NodeHandler() {
            public boolean node(Node node) {
                result.add(node);
                return true;
            }
            public void error(String msg) {
                throw new RuntimeException(msg);
            }
            public void end() {
            }
        });
        for (String s: html) parser.push(s);
        return result;
    }
    
    private void parse(String html, NodeHandler handler) {
        HtmlPushParser parser = new HtmlPushParser(handler);
        parser.push(html);
        parser.eof();
    }
    
    private void parse(List<String> html, NodeHandler handler) {
        HtmlPushParser parser = new HtmlPushParser(handler);
        for (String s: html) 
            parser.push(s);
        parser.eof();
    }
    
    private List<Node> parseToList(String html) {
        try {
            return parseToList(new ByteArrayInputStream(html.getBytes("utf-8")), "utf-8");
        } 
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private List<Node> parseToList(String fileName, String encoding) {
        InputStream in = getClass().getResourceAsStream(fileName);
        return parseToList(in, encoding);
    }
    
    private List<Node> parseToList(InputStream in, String encoding) {
        final List<Node> result = new ArrayList<>();
        HtmlPushParser parser = new HtmlPushParser(new NodeHandler() {
            public boolean node(Node node) {
                result.add(node);
                return true;
            }
            public void error(String msg) {
                throw new RuntimeException(msg);
            }
            public void end() {
            }
        });
        
        try {
            CharBuffer buf = CharBuffer.allocate(200);
            Reader reader = new InputStreamReader(in, encoding);

            int n = 0;
            while ((n = reader.read(buf)) > 0) {
                buf.flip();
                parser.push(buf.toString());
            }
            parser.eof();
            reader.close();
            return result;
        } 
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    
}
