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
package vtk.web.api;

import java.util.Date;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.AuthorizationException;
import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.Resource;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.util.Result;
import vtk.web.RequestContext;

public class PublishApiHandler implements Controller {

    @Override
    public ModelAndView handleRequest(HttpServletRequest request,
            HttpServletResponse response) {
        RequestContext requestContext = RequestContext.getRequestContext();
        
        Result<PublishRequest> publishRequest = publishRequest(requestContext);
        
        Result<ApiResponseBuilder> builder = publishRequest.flatMap(req -> {
            return req.action == PublishAction.PUBLISH ? 
                    publish(req.resource, requestContext) : 
                        unpublish(req.resource, requestContext);
        })
        .recover(ex -> {
            if (ex instanceof ResourceNotFoundException) {
                return new ApiResponseBuilder(HttpServletResponse.SC_NOT_FOUND)
                    .header("Content-Type", "text/plain")
                    .message("Resource " + requestContext.getResourceURI() 
                    + " does not exist");
            }
            if (ex instanceof InvalidRequestException) {
                return new ApiResponseBuilder(HttpServletResponse.SC_BAD_REQUEST)
                        .message(ex.getMessage());
            }
            if (ex instanceof AuthorizationException) {
                return new ApiResponseBuilder(HttpServletResponse.SC_FORBIDDEN)
                        .message(ex.getMessage());
            }
            return new ApiResponseBuilder(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                    .message("An unexpected error occurred: " + ex.getMessage());
        });
        
        try {
            builder.get().writeTo(response);
            return null;
        }
        catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
    
    
    private static enum PublishAction {
        PUBLISH,
        UNPUBLISH;
    }
    
    private static class PublishRequest {
        public final PublishAction action;
        public final Resource resource;
        public PublishRequest(PublishAction action, Resource resource) {
            this.action = action;
            this.resource = resource;
        } 
    }
    
    private Result<PublishRequest> publishRequest(RequestContext requestContext) {
        HttpServletRequest request = requestContext.getServletRequest();
        
        Result<PublishAction> pubAction = Result.attempt(() -> {
            if (!"POST".equals(request.getMethod()))
                throw new InvalidRequestException("POST method is required");
            return Objects.requireNonNull(request.getParameter("action"),  
                "Missing request parameter 'action'");
        })
         .flatMap(action -> Result.attempt(() -> {
          if ("publish".equals(action)) return PublishAction.PUBLISH;
          else if ("unpublish".equals(action)) return PublishAction.UNPUBLISH;
          else throw new InvalidRequestException("Unknown action: " + action 
                  + ": must be one of (publish, unpublish)");
        }));
        
        Result<PublishRequest> result = pubAction.flatMap(action -> 
            Result.attempt(() -> {
                try {
                    Resource resource = requestContext.getRepository()
                            .retrieve(requestContext.getSecurityToken(), 
                                    requestContext.getResourceURI(), false);
                    return new PublishRequest(action, resource);
                }
                catch (AuthorizationException | ResourceNotFoundException e) {
                    throw e;
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
        );
        return result;
    }
    
    
    private Result<ApiResponseBuilder> publish(Resource resource, 
            RequestContext requestContext) {
        
        Result<Resource> result = Result.attempt(() -> {
            try {
                Property publishDate = requestContext.getRepository().getTypeInfo(resource)
                        .createProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.PUBLISH_DATE_PROP_NAME);
                publishDate.setDateValue(new Date());
                resource.addProperty(publishDate);
                return requestContext.getRepository()
                        .store(requestContext.getSecurityToken(), resource);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return result.map(r -> 
            new ApiResponseBuilder(HttpServletResponse.SC_OK)
            .header("Content-Type", "text/plain;charset=utf-8")
            .message("Resource " + r.getURI() + " published"));
    }
    
    private Result<ApiResponseBuilder> unpublish(Resource resource, RequestContext requestContext) {
        
        Result<Resource> result = Result.attempt(() -> {
            try {
                PropertyTypeDefinition publishDatePropDef = 
                        requestContext.getRepository().getTypeInfo(resource)
                        .getPropertyTypeDefinition(
                        Namespace.DEFAULT_NAMESPACE, PropertyType.PUBLISH_DATE_PROP_NAME);
                resource.removeProperty(publishDatePropDef);

                return requestContext.getRepository()
                        .store(requestContext.getSecurityToken(), resource);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return result.map(r -> 
            new ApiResponseBuilder(HttpServletResponse.SC_OK)
            .header("Content-Type", "text/plain;charset=utf-8")
            .message("Resource " + r.getURI() + " unpublished"));
    }
}
