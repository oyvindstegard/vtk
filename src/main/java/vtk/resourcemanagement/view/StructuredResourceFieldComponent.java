/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.resourcemanagement.view;

import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Required;

import vtk.resourcemanagement.StructuredResource;
import vtk.text.html.HtmlContent;
import vtk.text.html.HtmlText;
import vtk.web.ModelProvider;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.decorating.HtmlDecoratorComponent;
import vtk.web.decorating.components.AbstractDecoratorComponent;
import vtk.web.decorating.components.DecoratorComponentException;

public class StructuredResourceFieldComponent extends
        AbstractDecoratorComponent implements HtmlDecoratorComponent {

    private static final String DESCRIPTION
        = "Outputs the contents of the element(s) identified by select";

    private static final String PARAMETER_SELECT = "select";
    private static final String PARAMETER_SELECT_DESC = 
        "Selects the field to output";
    
    private String resourceModelKey;
    
    @Override
    protected String getDescriptionInternal() {
        return DESCRIPTION;
    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new HashMap<String, String>();
        map.put(PARAMETER_SELECT, PARAMETER_SELECT_DESC);
        return map;
    }

    @Override
    public List<HtmlContent> render(DecoratorRequest request) throws Exception {
        String select = request.getStringParameter(PARAMETER_SELECT);
        if (select == null || "".equals(select.trim())) {
            throw new DecoratorComponentException("Required parameter '" 
                    + PARAMETER_SELECT + "' not specified");
        }
        
        Map<String, Object> mvcModel = ModelProvider.getModel(request.getServletRequest());
        
        if (mvcModel == null) return null;
        Object o = mvcModel.get(this.resourceModelKey);
        if (o == null) {
            return null;
        }
        if (!(o instanceof StructuredResource)) {
            return null;
        }
        StructuredResource r = (StructuredResource) o;
        final Object prop = r.getProperty(select);
        if (prop == null) {
            return null;
        }
        HtmlContent c = new HtmlText() {
            @Override
            public String getContent() {
                return prop.toString();
            }
        };
        return Collections.singletonList(c);
    }

    @Override
    public void render(DecoratorRequest request, DecoratorResponse response)
            throws Exception {
        List<HtmlContent> l = render(request);
        if (l == null) {
            return;
        }
        Writer writer = response.getWriter();
        for (HtmlContent c : l) {
            writer.write(c.getContent());
        }
        writer.flush();
        writer.close();
    }

    @Required
    public void setResourceModelKey(String resourceModelKey) {
        this.resourceModelKey = resourceModelKey;
    }
}
