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
package vtk.web.decorating;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import vtk.text.tl.DefineHandler;
import vtk.text.tl.DirectiveHandler;
import vtk.text.tl.IfHandler;
import vtk.text.tl.ValHandler;
import vtk.text.tl.expr.Expression.FunctionResolver;
import vtk.util.io.InputSource;
import vtk.web.decorating.components.MockDecoratorResponse;
import vtk.web.decorating.components.MockStringDecoratorRequest;

public class DynamicComponentParserTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void test() throws Exception {

        List<DirectiveHandler> handlers = Arrays.asList(
                new DirectiveHandler[] {
                        new DefineHandler(new FunctionResolver()),
                        new ValHandler(null, new FunctionResolver()),
                        new IfHandler(new FunctionResolver())
                });

        final String template =
            "[component foo]" +
            "[description]My description[/description]" +
            "[parameter param1 \"my first parameter\"]" +
            "[parameter param2 \"my second parameter\"]" +
            "[if request.parameters.param1][val request.parameters.param1]" +
            "[elseif request.parameters.param2][val request.parameters.param2]" +
            "[else][error \"no parameters\"]content after error[endif]" +
            "[/component]";

        InputSource inputSource = new InputSource() {
            @Override
            public InputStream getInputStream() throws IOException {
                return new java.io.ByteArrayInputStream(template.getBytes("utf-8"));
            }
            @Override
            public String getID() {
                return "test-template";
                
            }
            @Override
            public long getLastModified() throws IOException {
                return -1L;
            }
            @Override
            public String getCharacterEncoding() throws IOException {
                return "utf-8";
            }
        };
        
        DynamicComponentParser parser = new DynamicComponentParser(handlers);
        DecoratorComponent component = parser.compile("ns", "name", inputSource);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("param1", "foo");
        MockStringDecoratorRequest req = new MockStringDecoratorRequest("<html></html>", params);
        MockDecoratorResponse resp = new MockDecoratorResponse();
        component.render(req, resp);
        assertEquals("foo", resp.getResult());
        
        params = new HashMap<String, Object>();
        params.put("param2", "bar");
        req = new MockStringDecoratorRequest("<html></html>", params);
        resp = new MockDecoratorResponse();
        component.render(req, resp);
        assertEquals("bar", resp.getResult());

        params = new HashMap<String, Object>();
        req = new MockStringDecoratorRequest("<html></html>", params);
        resp = new MockDecoratorResponse();
        component.render(req, resp);
        assertEquals("no parameters", resp.getResult());
    }

}
