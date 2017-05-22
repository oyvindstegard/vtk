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

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import vtk.text.html.EnclosingHtmlContent;
import vtk.text.html.HtmlContent;
import vtk.text.html.HtmlElement;
import vtk.text.html.HtmlPage;
import vtk.web.decorating.DecoratorComponent;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;


public abstract class AbstractHtmlSelectComponent 
    extends AbstractDecoratorComponent implements DecoratorComponent {

    protected static final String PARAMETER_SELECT = "select";
    protected String elementPath;

    public void setSelect(String select) {
        this.elementPath = select;
    }
    
    public void render(DecoratorRequest request, DecoratorResponse response) throws Exception {
        List<HtmlContent> result = render(request);
        outputContent(result, request, response);
    } 
    
    public List<HtmlContent> render(DecoratorRequest request) throws Exception {
        String expression = (this.elementPath != null) ?
                this.elementPath : request.getStringParameter(PARAMETER_SELECT);
        if (expression == null) {
            throw new DecoratorComponentException("Missing parameter 'select'");
        }

        HtmlPage page = request.getHtmlPage();
        List<HtmlElement> elements = page.select(expression);
        List<HtmlContent> filtered = filterElements(elements, request);
        return filtered;
    }
    
    /**
     * Output rendered content to the response.
     * @param content the rendered HTML content  
     * @param request the decorator request
     * @param response the decorator response
     */
    protected void outputContent(List<HtmlContent> content,
            DecoratorRequest request, DecoratorResponse response) throws Exception {
        Writer out = response.getWriter();
        for (HtmlContent c: content) {
            if (c instanceof EnclosingHtmlContent) {
                out.write(((EnclosingHtmlContent) c).getEnclosedContent());
            } else {
                out.write(c.getContent());
            }
        }
        out.flush();
        out.close();
    }

    /**
     * Filter the list of selected HTML elements. 
     * The default implementation is to keep all elements.
     * @param elements the HTML content list
     * @param request the decorator request
     * @return the filtered HTML content list
     */
    protected List<HtmlContent> filterElements(List<HtmlElement> elements, DecoratorRequest request) {
        List<HtmlContent> result = new ArrayList<>();
        result.addAll(elements);
        return result;
    }
}
