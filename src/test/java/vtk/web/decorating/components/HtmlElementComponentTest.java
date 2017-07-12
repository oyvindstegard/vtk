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
package vtk.web.decorating.components;


import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import vtk.text.html.HtmlElement;


public class HtmlElementComponentTest {

    private static final String SIMPLE_PAGE =
        "<html>\n"
        + "  <head>\n"
        + "    <meta name=\"keywords\" content=\"My keywords\"/>\n"
        + "    <title>My title</title>\n"
        + "  </head>\n"
        + "  <body>The body</body>\n"
        + "</html>\n";


    @Test
    public void simpleHeadExcludeTitle() throws Exception {
        HtmlElementComponent component = new HtmlElementComponent();
        component.setSelect("html.head");
        component.setExclude("title");
        component.setEnclosed(false);
        MockHttpServletRequest mockServletRequest = new MockHttpServletRequest("GET", "/");

        MockStringDecoratorRequest req = new MockStringDecoratorRequest(mockServletRequest, SIMPLE_PAGE);
        MockDecoratorResponse resp = new MockDecoratorResponse();
        component.render(req, resp);
        HtmlElement[] result = resp.getParsedResult();
        assertEquals("meta", result[0].getName());
    }

    @Test
    public void simpleBody() throws Exception {
        HtmlElementComponent component = new HtmlElementComponent();
        component.setSelect("html.body");
        component.setEnclosed(false);
        MockHttpServletRequest mockServletRequest = new MockHttpServletRequest("GET", "/");

        MockStringDecoratorRequest req = new MockStringDecoratorRequest(mockServletRequest, SIMPLE_PAGE);
        MockDecoratorResponse resp = new MockDecoratorResponse();
        component.render(req, resp);
        assertEquals("The body", resp.getResult());
    }

    @Test
    public void simpleBodyParameters() throws Exception {
        HtmlElementComponent component = new HtmlElementComponent();
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("select", "html.head");
        parameters.put("enclosed", "false");
        
        MockHttpServletRequest mockServletRequest = new MockHttpServletRequest("GET", "/");

        MockStringDecoratorRequest req = new MockStringDecoratorRequest(
                mockServletRequest, SIMPLE_PAGE, parameters);
        MockDecoratorResponse resp = new MockDecoratorResponse();
        component.render(req, resp);
        HtmlElement[] result = resp.getParsedResult();
        assertEquals("meta", result[0].getName());
    }

}

