/* Copyright (c) 2013, University of Oslo, Norway
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

package vtk.web.view;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import vtk.util.text.Json;

/**
 *
 */
public class JsonViewTest {

    private Map<String,Object> model;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private JsonView view;
    
    @Before
    public void setUp() {
        model = new HashMap<String,Object>();
        view = new JsonView();
        view.setBeanName("jsonView");
        request = new MockHttpServletRequest("GET", "/json");
        response = new MockHttpServletResponse();
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void renderJsonObject() throws Exception {
        Json.MapContainer body = new Json.MapContainer();
        body.put("a", "b");
        body.put("n", 3);
        
        model.put("jsonObject", body);
        view.render(model, request, response);
        
        String result = response.getContentAsString();
        assertEquals("{\"a\":\"b\",\"n\":3}", result);
    }

    @Test
    public void renderMap() throws Exception {
        Map<String,Object> body = new LinkedHashMap<String,Object>();
        body.put("a", "b");
        body.put("n", 3);
        
        view.setModelKey("map");
        model.put("map", body);
        view.render(model, request, response);
        
        String result = response.getContentAsString();
        assertEquals("{\"a\":\"b\",\"n\":3}", result);
        
    }
    
    @Test
    public void renderArray() throws Exception {
        int[] numbers = new int[3];
        numbers[0] = 2;
        numbers[1] = 1;
        numbers[2] = 0;
        
        view.setModelKey("array");
        model.put("array", numbers);
        view.render(model, request, response);
        
        String result = response.getContentAsString();
        assertEquals("[2,1,0]", result);
    }
    
    @Test
    public void httpStatus() throws Exception {
        Json.MapContainer body = new Json.MapContainer();
        body.put("error", "Not found");
        model.put("jsonObject", body);
        model.put("httpStatus", 404);
        view.render(model, request, response);
        assertEquals("{\"error\":\"Not found\"}", response.getContentAsString());
        assertEquals(404, response.getStatus());
    }
    
    @Test
    public void renderNull() throws Exception {
        model.put("jsonObject", null);
        view.render(model, request, response);
        String result = response.getContentAsString();
        assertEquals("null", result);
    }
    
}
