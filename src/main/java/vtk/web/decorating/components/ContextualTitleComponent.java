/* Copyright (c) 2011, University of Oslo, Norway
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
import java.util.Collections;
import java.util.Map;

import org.springframework.beans.factory.annotation.Required;
import vtk.repository.Path;
import vtk.repository.Resource;
import vtk.web.RequestContext;
import vtk.web.TitleResolver;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;

public class ContextualTitleComponent extends AbstractDecoratorComponent {

    private TitleResolver resolver;
    
    @Override
    public void render(DecoratorRequest request, DecoratorResponse response)
            throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Path path = getUriParameter(request);
        if (path == null) {
            path = requestContext.getResourceURI();
        }
        
        Resource resource = requestContext.getRepository().retrieve(
                requestContext.getSecurityToken(), path, true);
        Writer writer = response.getWriter();
        writer.write(this.resolver.resolve(resource));
        writer.flush();
        writer.close();
    }
    
    // For debugging
    private Path getUriParameter(DecoratorRequest request) {
        try {
            return Path.fromString(request.getStringParameter("uri"));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected String getDescriptionInternal() {
        return "Renders a contextualized resource title";
    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        return Collections.emptyMap();
    }
    
    @Required
    public void setTitleResolver(TitleResolver resolver) {
        this.resolver = resolver;
    }

}
