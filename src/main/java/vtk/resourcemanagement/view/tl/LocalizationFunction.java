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
package vtk.resourcemanagement.view.tl;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import vtk.resourcemanagement.StructuredResource;
import vtk.text.tl.Context;
import vtk.text.tl.Symbol;
import vtk.text.tl.expr.Function;
import vtk.web.ModelProvider;
import vtk.web.decorating.DynamicDecoratorTemplate;

public class LocalizationFunction extends Function {

    private String resourceModelKey;
    
    public LocalizationFunction(Symbol symbol, String resourceModelKey) {
        super(symbol, 1);
        this.resourceModelKey = resourceModelKey;
    }

    @Override
    public Object eval(Context ctx, Object... args) {
        String key = args[0].toString();
        //RequestContext requestContext = RequestContext.getRequestContext();
        //HttpServletRequest request = requestContext.getServletRequest();
        HttpServletRequest request = (HttpServletRequest) ctx.getAttribute(DynamicDecoratorTemplate.SERVLET_REQUEST_CONTEXT_ATTR);

        Map<String, Object> model = ModelProvider.getModel(request);
        Object[] localizationArgs = new Object[0];
        if (args.length > 1) {
            localizationArgs = new Object[args.length - 1];
            for (int i = 1; i < args.length; i++) {
                localizationArgs[i - 1] = args[i];
            }
        }
        StructuredResource resource = (StructuredResource) model.get(this.resourceModelKey);
        if (resource == null) {
            throw new RuntimeException("Unable to localize string: " + key + ": no resource found in model");
        }
        String localizedMsg = resource.getType().getLocalizedMsg(key, ctx.getLocale(), localizationArgs);
        return localizedMsg;
    }
    
}
