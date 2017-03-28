/* Copyright (c) 2017, University of Oslo, Norway
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
package vtk.web.servlet;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import vtk.web.servlet.TranslateURLFilter;

public class TranslateURLFilterTest {
    
    private Map<String, String> translations = new HashMap<>();

    @Before
    public void setUp() throws Exception {
        translations.put("(https?)://www.example.com/foo/(.*)", "$1://www.example.info/bar/$2");
    }

    @Test
    public void test() throws IOException, ServletException {
        
        MockServlet servlet = new MockServlet();
        
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/foo/abc/?x=y");
        request.setServerName("www.example.com");
        request.setScheme("https");
        request.setServerPort(443);
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        TranslateURLFilter filter = new TranslateURLFilter(translations);
        MockFilterChain chain = new MockFilterChain(servlet, filter);
        chain.doFilter(request, response);
        assertEquals("https://www.example.info/bar/abc/?x=y", servlet.lastURL);

        request.setScheme("http");
        request.setServerPort(80);
        chain = new MockFilterChain(servlet, filter);
        chain.doFilter(request, response);
        assertEquals("http://www.example.info/bar/abc/?x=y", servlet.lastURL);
    }
    
    @SuppressWarnings("serial")
    private class MockServlet extends HttpServlet {
        public String lastURL = null;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            this.lastURL = req.getRequestURL().toString();
        }
        
    }
}
