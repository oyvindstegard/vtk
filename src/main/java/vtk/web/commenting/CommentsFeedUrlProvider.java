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
package vtk.web.commenting;

import java.util.Map;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.RepositoryAction;
import vtk.repository.Resource;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.decorating.components.ViewRenderingDecoratorComponent;

/**
 * XXX class is named "an URL provider", which is easy to mix up with typical
 * reference data provider classes.
 * (Actual URL comes from another provider configured for the view.)
 */
public class CommentsFeedUrlProvider extends ViewRenderingDecoratorComponent {
    
    @Override
    protected void processModel(final Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {
        super.processModel(model, request, response);
        RequestContext requestContext = RequestContext.getRequestContext(request.getServletRequest());
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = RequestContext.getRequestContext(request.getServletRequest()).getResourceURI();
        
        boolean commentsAllowed = false;
        boolean commentsEnabled = false;

        Resource resource = repository.retrieve(token, uri, true);
        commentsAllowed = repository.isAuthorized(resource, RepositoryAction.ADD_COMMENT, 
                                            requestContext.getPrincipal(), false);

        Property commentsEnabledProp = resource.getProperty(Namespace.DEFAULT_NAMESPACE, "commentsEnabled");
        if (commentsEnabledProp != null && commentsEnabledProp.getBooleanValue()) {
            commentsEnabled = true;
        }
        
        model.put("commentsAllowed", commentsAllowed);
        model.put("commentsEnabled", commentsEnabled);
        
    }

}
