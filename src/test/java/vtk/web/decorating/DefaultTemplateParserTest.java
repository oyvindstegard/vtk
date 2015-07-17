/* Copyright (c) 2007, University of Oslo, Norway
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
package vtk.web.decorating;

import static org.junit.Assert.assertEquals;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Map;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;


public class DefaultTemplateParserTest {

    private static final String EMPTY_TEMPLATE = "";
    
    private Mockery context = new JUnit4Mockery();
    private final DecoratorRequest mockRequest = context.mock(DecoratorRequest.class);
    private final DecoratorResponse mockResponse = context.mock(DecoratorResponse.class);

    @Test
    public void empty() throws Exception {
        DollarSyntaxComponentParser parser = createParser();
        Reader reader = new StringReader(EMPTY_TEMPLATE);
        ComponentInvocation[] parsedTemplate = parser.parse(reader);
        assertEquals(1, parsedTemplate.length);
    }
    

    private static final String SIMPLE_TEMPLATE_WITH_PARAMS =
        "<html>${namespace:name var1=[20] var2=[30]}</html>";

    @Test
    public void simple() throws Exception {
        DollarSyntaxComponentParser parser = createParser();
        Reader reader = new StringReader(SIMPLE_TEMPLATE_WITH_PARAMS);
        ComponentInvocation[] parsedTemplate = parser.parse(reader);
        assertEquals(3, parsedTemplate.length);
        
        String begin = renderComponent(parsedTemplate[0]);
        String end = renderComponent(parsedTemplate[2]);
        assertEquals("<html>", begin);
        assertEquals("</html>", end);

        ComponentInvocation c = parsedTemplate[1];
        assertEquals("20", c.getParameters().get("var1"));
        assertEquals("30", c.getParameters().get("var2"));

    }


    private static final String MALFORMED_TEMPLATE =
        "<html>${namespace:name var1=[20}] var2=[30]]}</html>";

    @Test
    public void malformed() throws Exception {
        DollarSyntaxComponentParser parser = createParser();
        Reader reader = new StringReader(MALFORMED_TEMPLATE);
        ComponentInvocation[] parsedTemplate = parser.parse(reader);
        assertEquals(1, parsedTemplate.length);
        String result = renderComponent(parsedTemplate[0]);
        assertEquals(MALFORMED_TEMPLATE, result);
    }

    private static final String NESTED_DIRECTIVES =
        "${${component:ref}}";

    @Test
    public void malformedNestedDirectives() throws Exception {
        DollarSyntaxComponentParser parser = createParser();
        Reader reader = new StringReader(NESTED_DIRECTIVES);
        ComponentInvocation[] parsedTemplate = parser.parse(reader);
        assertEquals(3, parsedTemplate.length);
        String begin = renderComponent(parsedTemplate[0]);
        String end = renderComponent(parsedTemplate[2]);
        assertEquals("${", begin);
        assertEquals("}", end);
    }

    private static final String COMPLEX_TEMPLATE =
        "${<html>${namespace:name\nvar1 = [20] \r\nvar2=\r\n[30\\]] \rvar3=[400]}</html>}";

    @Test
    public void testComplexTemplate() throws Exception {
        DollarSyntaxComponentParser parser = createParser();
        Reader reader = new StringReader(COMPLEX_TEMPLATE);
        ComponentInvocation[] parsedTemplate = parser.parse(reader);
        assertEquals(3, parsedTemplate.length);
        ComponentInvocation c = parsedTemplate[1];
        assertEquals("20", c.getParameters().get("var1"));
        assertEquals("30]", c.getParameters().get("var2"));
        assertEquals("400", c.getParameters().get("var3"));
    }


    private DollarSyntaxComponentParser createParser() {
        DollarSyntaxComponentParser parser = new DollarSyntaxComponentParser();
        //parser.setComponentResolver(new DummyComponentResolver());
        return parser;
    }
    
    private String renderComponent(ComponentInvocation inv) throws Exception {
        final Writer writer = new StringWriter();
        context.checking(new Expectations() {{ oneOf(mockResponse).getWriter(); will(returnValue(writer)); }});
        if (inv instanceof StaticTextFragment) {
            StringBuilder sb = new StringBuilder();
            ((StaticTextFragment) inv).write(sb);
            return sb.toString();
        }
        DecoratorComponent c = new DummyComponent(inv.getNamespace(), inv.getName());
        c.render(mockRequest, mockResponse);
        return writer.toString();
    }
    

    private class DummyComponent implements DecoratorComponent {
        private String namespace, name;
        
        public DummyComponent(String namespace, String name) {
            this.namespace = namespace;
            this.name = name;
        }

        @Override
        public String getNamespace() {
            return this.namespace;
        }
        
        @Override
        public String getName() {
            return this.name;
        }
        
        @Override
        public String getDescription() {
            return "Dummy component " + this.namespace + ":" + this.name;
        }
        
        @Override
        public Map<String, String> getParameterDescriptions() {
            return null;
        }
        
        @Override
        public Collection<UsageExample> getUsageExamples() {
            return null;
        }
        
        @Override
        public void render(DecoratorRequest request, DecoratorResponse response)
            throws Exception {
            response.getWriter().write("component: " + this.namespace + ":" + this.name);
        }
    }

}

