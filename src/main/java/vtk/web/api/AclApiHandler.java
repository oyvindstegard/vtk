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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.HttpRequestHandler;

import vtk.repository.Acl;
import vtk.repository.AuthorizationException;
import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.Resource;
import vtk.repository.ResourceNotFoundException;
import vtk.security.InvalidPrincipalException;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;
import vtk.util.Result;
import vtk.util.text.Json;
import vtk.util.text.Json.ListContainer;
import vtk.util.text.Json.MapContainer;
import vtk.util.text.JsonStreamer;
import vtk.web.RequestContext;
import vtk.web.service.URL;

public class AclApiHandler implements HttpRequestHandler {
    private PrincipalFactory principalFactory;
    
    public AclApiHandler(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        
        RequestContext requestContext = RequestContext.getRequestContext();
        Result<Optional<Resource>> res = retrieve(requestContext, requestContext.getResourceURI());
        
        Result<ApiResponseBuilder> bldr = res.flatMap(opt -> {
           if (!opt.isPresent()) {
               return Result.success(new ApiResponseBuilder(HttpServletResponse.SC_NOT_FOUND)
                       .message("404 Not Found: " + requestContext.getResourceURI()));
           }
           else {
               Resource resource = opt.get();
               switch (request.getMethod()) {
               case "GET": 
                   return getAcl(resource, requestContext);
               case "PUT":
               case "POST": 
                   return updateAcl(resource, requestContext);
               case "DELETE": 
                   return deleteAcl(resource, requestContext);
               default: 
                   return unknownMethod(resource, requestContext);
               }
           }
        });
        
        bldr = bldr.recover(ex -> {
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
        
        bldr.forEach(resp -> {
            resp.writeTo(response);
        });
    }
    
    
    private Result<ApiResponseBuilder> getAcl(Resource resource, 
            RequestContext requestContext) {

        if (resource.isInheritedAcl()) {
            Result<Optional<Resource>> ancestor = 
                    nearestAcl(requestContext, resource.getURI().getParent());
            return ancestor.map(opt -> {
                Resource ans = opt.get();
                URL url = new URL(requestContext.getRequestURL()).setPath(ans.getURI());
                return new ApiResponseBuilder(HttpServletResponse.SC_NOT_FOUND)
                    .header("Content-Type", "text/plain;charset=utf-8")
                    .header("Link", "<" + url + ">; rel=\"inherited-acl\"")
                    .message("ACL Not Found on " + resource.getURI() + "\n"
                            + "Nearest ACL: " + ans.getURI() + "\n");
            });
        }
        return Result.success(new ApiResponseBuilder(HttpServletResponse.SC_OK)
            .header("Content-Type", "application/json")
            .message(JsonStreamer.toJson(aclToJson(resource.getAcl()), 2, true)));
    }
    

    private Result<ApiResponseBuilder> updateAcl(Resource resource, 
            RequestContext requestContext) {

        HttpServletRequest request = requestContext.getServletRequest();
        
        if (!"application/json".equals(request.getContentType())) {
            return Result.success(new ApiResponseBuilder(HttpServletResponse.SC_NOT_ACCEPTABLE)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message("Content-Type application/json required for " 
                    + request.getMethod() + " method\n"));
        }
        boolean existed = !resource.isInheritedAcl();
        Result<InputStream> stream = Result.attempt(() -> {
           try {
               return request.getInputStream();
           }
           catch (IOException e) {
               throw new RuntimeException(e);
           }
        });
        Result<Json.MapContainer> json = stream.flatMap(is -> parseJson(is))
                .recoverWith(t -> Result.failure(new InvalidRequestException(t.getMessage())));

        Result<Acl> newAcl = json.flatMap(aclMapper)
                .recoverWith(t -> Result.failure(new InvalidRequestException(t.getMessage())));
        
        Result<Resource> updated = newAcl.flatMap(acl -> {
            return Result.attempt(() -> {
                try {
                    return requestContext.getRepository()
                            .storeACL(requestContext.getSecurityToken(), 
                                    requestContext.getResourceURI(), acl);
                }
                catch (InvalidPrincipalException e) {
                    throw new InvalidRequestException(
                            "Invalid principal: " + e.getMessage(), e);
                }
                catch (AuthorizationException e) {
                    throw e;
                }
                catch (Exception e) {
                    throw new RuntimeException(e); 
                }
            });
        });
        Result<ApiResponseBuilder> result = updated.map(res -> 
                new ApiResponseBuilder(existed ? HttpServletResponse.SC_OK : HttpServletResponse.SC_CREATED)
                    .header("Content-Type", "text/plain;charset=utf-8")
                    .message("ACL updated on resource " + resource.getURI() + "\n"));
        return result;
    }
    
    private Result<ApiResponseBuilder> deleteAcl(Resource resource, 
            RequestContext requestContext) {
        
        if (resource.isInheritedAcl()) {
            return Result.success(new ApiResponseBuilder(HttpServletResponse.SC_NOT_FOUND)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message("404 Not Found\n"));
        }
        
        Result<Resource> updated = Result.attempt(() -> {
            try {
                return requestContext.getRepository()
                        .deleteACL(requestContext.getSecurityToken(), resource.getURI());
            }
            catch (Exception e) {
                    throw new RuntimeException(e);
            }            
        });
        
        return updated.map(r -> {
            return new ApiResponseBuilder(HttpServletResponse.SC_OK)
                    .header("Content-Type", "text/plain;charset=utf-8")
                    .message("Deleted ACL on resource " + r.getURI() + "\n");
        });
    }
    
    private Result<ApiResponseBuilder> unknownMethod(Resource resource, 
            RequestContext requestContext) {
        return Result.success(new ApiResponseBuilder(HttpServletResponse.SC_BAD_REQUEST)
            .header("Content-Type", "text/plain;charset=utf-8")
            .message("Request method " + requestContext.getServletRequest().getMethod() 
                    + " not supported: on " + resource.getURI()));
    }
    
    private Result<Optional<Resource>> retrieve(RequestContext requestContext, Path uri) {
        return Result.attempt(() -> {
            try {
                return Optional.of(requestContext.getRepository()
                        .retrieve(requestContext.getSecurityToken(), uri, false));
            }
            catch (ResourceNotFoundException e) {
                return Optional.empty();
            }
            catch (AuthorizationException e) {
                throw e;
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Result<Optional<Resource>> nearestAcl(RequestContext requestContext, Path uri) {
        Result<Optional<Resource>> retrieval = retrieve(requestContext, uri);
        return retrieval.flatMap(option -> {
           if (!option.isPresent())  return Result.success(option);
           Resource r = option.get();
           if (!r.isInheritedAcl()) return Result.success(option);
           return nearestAcl(requestContext, r.getURI().getParent());
        });
    }
    
    private Result<Json.MapContainer> parseJson(InputStream input) {
        return Result.attempt(() -> {
            try {
                return Json.parseToContainer(input).asObject();
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Function<Json.MapContainer, Result<Acl>> aclMapper = map -> {
        Result<Acl> result = Result.attempt(() -> {
            Acl acl = Acl.EMPTY_ACL;
            
            for (Privilege action: Privilege.values()) {
                if (map.containsKey(action.getName())) {
                    MapContainer entry = map.objectValue(action.getName());
                    
                    if (entry.containsKey("users")) {
                        ListContainer list = entry.arrayValue("users");
                        for (Object user: list) {
                            acl = acl.addEntry(action, 
                                    principalFactory.getPrincipal(
                                            user.toString(), Principal.Type.USER));
                        }
                    }
                    if (entry.containsKey("groups")) {
                        ListContainer list = entry.arrayValue("groups");
                        for (Object group: list) {
                            acl = acl.addEntry(action, 
                                    principalFactory.getPrincipal(
                                            group.toString(), Principal.Type.GROUP));
                        }
                    }
                    if (entry.containsKey("pseudo")) {
                        ListContainer list = entry.arrayValue("pseudo");
                        for (Object pseudo: list) {
                            acl = acl.addEntry(action, 
                                    principalFactory.getPrincipal(
                                            pseudo.toString(), Principal.Type.PSEUDO));
                        }
                    }
                }
            }
            if (acl.isEmpty()) {
                throw new IllegalStateException("Resulting ACL has no entries");
            }
            return acl;
        });
        return result;
    };
    
    
    
    private Json.MapContainer aclToJson(Acl acl) {
        Json.MapContainer json = new Json.MapContainer();
        Set<Privilege> actions = acl.getActions();
        
        actions.forEach(action -> {
            List<Principal> users = new ArrayList<>();
            List<Principal> groups = new ArrayList<>();
            List<Principal> pseudo = new ArrayList<>();
            
            acl.getPrincipalSet(action).forEach(p -> {
                if (p.getType() == Principal.Type.USER) {
                    users.add(p);
                }
                else if (p.getType() == Principal.Type.GROUP) {
                    groups.add(p);
                }
                else {
                    pseudo.add(p);
                }
            });
            
            Json.MapContainer entry = new Json.MapContainer();
            if (!users.isEmpty()) {
                Json.ListContainer list = new Json.ListContainer();
                users.forEach(u -> list.add(u.getQualifiedName()));
                entry.put("users", list);
            }
            
            if (!groups.isEmpty()) {
                Json.ListContainer list = new Json.ListContainer();
                groups.forEach(g -> list.add(g.getQualifiedName()));
                entry.put("groups", list);
            }
            
            if (!pseudo.isEmpty()) {
                Json.ListContainer list = new Json.ListContainer();
                pseudo.forEach(p -> list.add(p.getQualifiedName()));
                entry.put("pseudo", list);
            }
            json.put(action.getName(), entry);
        });
        return json;
    }
    
}
