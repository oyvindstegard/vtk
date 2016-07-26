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
package vtk.resourcemanagement.studies;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.security.SecurityContext;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.decorating.components.ViewRenderingDecoratorComponent;

public class SharedTextComponent extends ViewRenderingDecoratorComponent {

    private SharedTextResolver sharedTextResolver;

    @Override
    protected void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {

        String propName = request.getStringParameter("propName");

        if (StringUtils.isBlank(propName)) {
            return;
        }
        model.put("id", propName);
        
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = SecurityContext.getSecurityContext().getToken();
        Resource resource = repository.retrieve(token, requestContext.getResourceURI(), true);

        Property prop = resource.getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE, propName);

        if (prop == null) {
            model.put("nullProp", true);
            return;
        }

        String sharedText = sharedTextResolver.resolveSharedText(resource, prop);

        if (sharedText == null) {
            return;
        }

        model.put("sharedText", sharedText);
    }

    @Required
    public void setSharedTextResolver(SharedTextResolver sharedTextResolver) {
        this.sharedTextResolver = sharedTextResolver;
    }

}
