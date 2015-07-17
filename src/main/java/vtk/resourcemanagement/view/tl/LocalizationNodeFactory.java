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
package vtk.resourcemanagement.view.tl;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import vtk.resourcemanagement.StructuredResource;
import vtk.text.tl.Context;
import vtk.text.tl.DirectiveHandler;
import vtk.text.tl.Node;
import vtk.text.tl.Parser.Directive;
import vtk.text.tl.TemplateContext;
import vtk.text.tl.Token;
import vtk.web.ModelProvider;
import vtk.web.decorating.DynamicDecoratorTemplate;

public class LocalizationNodeFactory implements DirectiveHandler {

    private String resourceModelKey;
    
    public LocalizationNodeFactory(String resourceModelKey) {
        this.resourceModelKey = resourceModelKey;
    }
    
    @Override
    public String[] tokens() {
        return new String[] { "localized" };
    }

    @Override
    public void directive(Directive directive, TemplateContext context) {
        List<Token> args = directive.args();
        if (args.size() == 0) {
            context.error("At least one argument required");
            return;
        }
        final Token code = args.get(0);
        final List<Token> rest = new ArrayList<Token>(args.subList(1, args.size()));

        context.add(new Node() {
            public boolean render(Context ctx, Writer out) throws Exception {
                String key = code.getValue(ctx).toString();
                //RequestContext requestContext = RequestContext.getRequestContext();
                //HttpServletRequest request = requestContext.getServletRequest();
                HttpServletRequest request = (HttpServletRequest) ctx.getAttribute(DynamicDecoratorTemplate.SERVLET_REQUEST_CONTEXT_ATTR);
                Map<String, Object> model = ModelProvider.getModel(request);
                Object[] localizationArgs = new Object[rest.size()];
                for (int i = 0; i < rest.size(); i++) {
                    Token a = rest.get(i);
                    localizationArgs[i] = a.getValue(ctx);
                }

                StructuredResource resource = (StructuredResource) model.get(resourceModelKey);
                if (resource == null) {
                    throw new RuntimeException("Unable to localize string: " + key + ": no resource found in model");
                }
                String localizedMsg = resource.getType().getLocalizedMsg(key, ctx.getLocale(), localizationArgs);
                out.write(ctx.htmlEscape(localizedMsg));
                return true;
            }
        });
    }

}
