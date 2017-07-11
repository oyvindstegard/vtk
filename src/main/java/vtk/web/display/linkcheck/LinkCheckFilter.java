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
package vtk.web.display.linkcheck;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.text.html.HtmlAttribute;
import vtk.text.html.HtmlContent;
import vtk.text.html.HtmlElement;
import vtk.text.html.HtmlNodeFilter;
import vtk.text.html.HtmlPage;
import vtk.text.html.HtmlPageFilter;
import vtk.web.RequestContext;
import vtk.web.decorating.HtmlNodeFilterFactory;
import vtk.web.decorating.HtmlPageFilterFactory;

public class LinkCheckFilter implements HtmlPageFilterFactory, HtmlNodeFilterFactory {

    private String elementClass = null;

    @Required
    public void setElementClass(String elementClass) {
        this.elementClass = elementClass;
    }

    @Override
    public HtmlNodeFilter nodeFilter(HttpServletRequest request) {
        return new Filter(request, elementClass);
    }

    @Override
    public HtmlPageFilter pageFilter(HttpServletRequest request) {
        return new Filter(request, elementClass);
    }

    private static class Filter implements HtmlNodeFilter, HtmlPageFilter {
        private HttpServletRequest request;
        private String elementClass;

        public Filter(HttpServletRequest request, String elementClass) {
            this.request = request;
            this.elementClass = elementClass;
        }

        @Override
        public boolean match(HtmlPage page) {
            return match();
        }

        @Override
        public NodeResult filter(HtmlContent node) {
            if (!(node instanceof HtmlElement)) {
                return NodeResult.keep;
            }
            HtmlElement element = (HtmlElement) node;
            String name = element.getName().toLowerCase();
            if (!"a".equals(name)) {
                return NodeResult.keep;
            }
            HtmlAttribute href = element.getAttribute("href");
            if (href == null) {
                return NodeResult.keep;
            }

            if (!match()) {
                return NodeResult.keep;
            }

            HtmlAttribute clazz = element.getAttribute("class");
            if (clazz == null) {
                clazz = new SimpleAttr("class", this.elementClass);
            } else {
                clazz = new SimpleAttr("class", clazz.getValue() + " " + this.elementClass);
            }
            element.setAttribute(clazz);
            return NodeResult.keep;
        }


        @Override
        public HtmlContent filterNode(HtmlContent content) {
            filter(content);
            return content;
        }

        private boolean match() {
            if (!RequestContext.exists(request)) return false;
            RequestContext requestContext = RequestContext.getRequestContext(request);
            if (requestContext.getPrincipal() == null) {
                return false;
            }
            return "true".equals(request.getParameter("link-check"));
        }

        private static class SimpleAttr implements HtmlAttribute {
            private String name, value;
            private boolean singleQuotes = false;
            public SimpleAttr(String name, String value) {
                this.name = name;
                this.value = value;
            }
            public String getName() {
                return this.name;
            }
            public String getValue() {
                return this.value;
            }
            public void setName(String name) {
                this.name = name;
            }
            public void setValue(String value) {
                this.value = value;
            }
            public boolean hasValue() {
                return true;
            }
            public boolean isSingleQuotes() {
                return this.singleQuotes;
            }
            public void setSingleQuotes(boolean singleQuotes) {
                this.singleQuotes = singleQuotes;
            }
        }
    }
}
