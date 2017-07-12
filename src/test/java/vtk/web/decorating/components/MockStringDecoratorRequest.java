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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import vtk.text.html.HtmlPage;
import vtk.text.html.HtmlPageParser;
import vtk.web.decorating.DecoratorRequest;


public class MockStringDecoratorRequest implements DecoratorRequest {
    private HttpServletRequest servletRequest;
    private HtmlPage page;
    private Map<String, Object> parameters = new HashMap<>();

    public MockStringDecoratorRequest(HttpServletRequest servletRequest, 
            String content) throws Exception {
        this(servletRequest, content, null);
    }
    

    public MockStringDecoratorRequest(HttpServletRequest servletRequest,
            String content, Map<String, Object> parameters) throws Exception {
        this.servletRequest = servletRequest;
        if (parameters != null) {
            this.parameters = parameters;
        }
        HtmlPageParser parser = new HtmlPageParser();
        HtmlPage page = parser.parse(new java.io.ByteArrayInputStream(
                                         content.getBytes("utf-8")), "utf-8");
        this.page = page;
    }

    @Override
    public Locale getLocale() {
        return Locale.getDefault();
    }
        
    @Override
    public HtmlPage getHtmlPage() {
        return page;
    }
        
    @Override
    public String getDoctype() {
        return page.getDoctype();
    }

    @Override
    public HttpServletRequest getServletRequest() {
        return servletRequest;
    }
    
    @Override
    public Map<String, Object> getMvcModel() {
        return new HashMap<>();
    }
        
    @Override
    public Object getRawParameter(String name) {
        return parameters.get(name);
    }

    @Override
    public String getStringParameter(String name) {
        return (String) parameters.get(name);
    }
        
    @Override
    public Iterator<String> getRequestParameterNames() {
        List<String> l = new ArrayList<>();
        for (Object o: parameters.keySet()) {
            l.add((String) o);
        }
        return l.iterator();
    }
}
