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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Acl;
import vtk.repository.AuthorizationException;
import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.Resource;
import vtk.repository.ResourceNotFoundException;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;
import vtk.util.Result;
import vtk.util.text.Json;
import vtk.util.text.Json.ListContainer;
import vtk.util.text.Json.MapContainer;
import vtk.util.text.JsonStreamer;
import vtk.web.RequestContext;
import vtk.web.service.URL;

public class AclApiHandler implements Controller {
    private PrincipalFactory principalFactory;
    
    public AclApiHandler(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }

    @Override
    public ModelAndView handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        switch (request.getMethod()) {
            case "GET": 
                getAcl(request, response);
                break;
            case "PUT":
            case "POST": 
                updateAcl(request, response);
                break;
            case "DELETE": 
                deleteAcl(request, response);
                break;
            default: 
                unknownMethod(request, response);
        }
        return null;
    }
    
    
    private void getAcl(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Resource resource = requestContext.getRepository().retrieve(requestContext.getSecurityToken(), 
                requestContext.getResourceURI(), true);
        
        if (resource.isInheritedAcl()) {
            Resource ancestor = nearestAcl(requestContext, resource.getURI().getParent());
            URL url = new URL(requestContext.getRequestURL());
            url.setPath(ancestor.getURI());
            
            ResponseBuilder(HttpServletResponse.SC_NOT_FOUND)
                .header("Content-Type", "text/plain;charset=utf-8")
                .header("Link", "<" + url + ">; rel=\"inherited-acl\"")
                .message("ACL Not Found on " + resource.getURI() + "\n"
                        + "Nearest ACL: " + ancestor.getURI() + "\n")
                .writeTo(response);
            return;
        }
        ResponseBuilder(HttpServletResponse.SC_OK)
            .header("Content-Type", "application/json")
            .message(JsonStreamer.toJson(aclToJson(resource.getAcl()), 2, true))
            .writeTo(response);
    }
    

    private void updateAcl(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        if (!"application/json".equals(request.getContentType())) {
            ResponseBuilder(HttpServletResponse.SC_NOT_ACCEPTABLE)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message("Content-Type application/json required for " 
                    + request.getMethod() + " method\n")
                .writeTo(response);
            return;
        }
        
        Result<Json.MapContainer> json = parseJson(request.getInputStream());
        if (json.failure.isPresent()) {
            ResponseBuilder(HttpServletResponse.SC_BAD_REQUEST)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message("Failed to parse JSON from input: " 
                        + json.failure.get().getMessage() + "\n")
                .writeTo(response);
            return;
        }
        
        
        Result<Acl> acl = json.flatMap(aclMapper);
        if (acl.failure.isPresent()) {
            ResponseBuilder(HttpServletResponse.SC_BAD_REQUEST)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message("Failed to map JSON input to a valid ACL: " 
                        + acl.failure.get().getMessage() + "\n")
                .writeTo(response);
            return;
        }
        
        RequestContext requestContext = RequestContext.getRequestContext();
        try {
            Resource updated = requestContext.getRepository()
                    .storeACL(requestContext.getSecurityToken(), 
                            requestContext.getResourceURI(), acl.result.get());
            ResponseBuilder(HttpServletResponse.SC_OK)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message("ACL updated\n")
                .writeTo(response);
        }
        catch (AuthorizationException e) {
            ResponseBuilder(HttpServletResponse.SC_UNAUTHORIZED)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message("Principal " + requestContext.getPrincipal() 
                    + " not authorized to update ACL on resource " 
                    + requestContext.getResourceURI() + "\n")
                .writeTo(response);
        }
        catch (ResourceNotFoundException e) {
            ResponseBuilder(HttpServletResponse.SC_NOT_FOUND)
            .header("Content-Type", "text/plain;charset=utf-8")
            .message("Resource not found: " + requestContext.getResourceURI())
            .writeTo(response);
        }
        catch (Exception e) {
            ResponseBuilder(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message("An unexpected error occurred: " + e.getMessage() + "\n")
                .writeTo(response);
        }
    }
    
    private void deleteAcl(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Resource resource = requestContext.getRepository().retrieve(requestContext.getSecurityToken(), 
                requestContext.getResourceURI(), true);
        
        if (resource.isInheritedAcl()) {
            ResponseBuilder(HttpServletResponse.SC_NOT_FOUND)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message("404 Not Found\n")
                .writeTo(response);
            return;
        }
        ResponseBuilder(HttpServletResponse.SC_OK)
            .header("Content-Type", "text/plain;charset=utf-8")
            .message("Deleted ACL\n")
            .writeTo(response);
    }

    private void unknownMethod(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        ResponseBuilder(HttpServletResponse.SC_BAD_REQUEST)
            .header("Content-Type", "text/plain;charset=utf-8")
            .message("Request method not supported: " + request.getMethod())
            .writeTo(response);
    }
    
    private Resource nearestAcl(RequestContext requestContext, Path uri) throws Exception {
        String token = requestContext.getSecurityToken();
        Resource resource = requestContext.getRepository().retrieve(token, uri, false);
        if (resource.isInheritedAcl()) {
            return nearestAcl(requestContext, uri.getParent());
        }
        return resource;
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
    
    private Function<Json.MapContainer, Result<Acl>> aclMapper = (map) -> {
        Acl result = Arrays.stream(Privilege.values()).reduce(Acl.EMPTY_ACL, (acc, action) -> {
            Acl acl = acc;
            if (map.containsKey(action.getName())) {
                MapContainer entry = map.objectValue(action.getName());
                
                if (entry.containsKey("users")) {
                    ListContainer list = entry.arrayValue("users");
                    for (Object user: list) {
                        Principal.Type type = user.toString().startsWith("pseudo:") 
                                ? Principal.Type.PSEUDO : Principal.Type.USER;

                        acl = acl.addEntry(action, 
                                principalFactory.getPrincipal(
                                        user.toString(), type));
                        
                    }
                }
                if (entry.containsKey("group")) {
                    ListContainer list = entry.arrayValue("group");
                    for (Object group: list) {
                        Principal.Type type = group.toString().startsWith("pseudo:") 
                                ? Principal.Type.PSEUDO : Principal.Type.GROUP;

                        acl = acl.addEntry(action, 
                                principalFactory.getPrincipal(
                                        group.toString(), type));
                    }
                }
            }
            return acl;
        }, (a1, a2) -> a1);
        if (result.isEmpty()) {
            return Result.failure(new Throwable("Resulting ACL has no entries"));
        }
        return Result.success(result);
    };
    
    
    private Json.MapContainer aclToJson(Acl acl) {
        Json.MapContainer json = new Json.MapContainer();
        Set<Privilege> actions = acl.getActions();
        
        actions.forEach(action -> {
            List<Principal> users = new ArrayList<>();
            List<Principal> groups = new ArrayList<>();
            
            acl.getPrincipalSet(action).forEach(p -> {
                if (p.isUser()) users.add(p); else groups.add(p);
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
            json.put(action.getName(), entry);
        });
        return json;
    }
    
    private static ResponseBuilder ResponseBuilder(int status) { 
        return new ResponseBuilder(status); 
    }
    
    private static class ResponseBuilder {
        private int status;
        private Map<String, String> headers = new HashMap<>();
        private String message = null;
        
        public ResponseBuilder(int status) 
            { this.status = status; }
        public ResponseBuilder message(String message) 
            { this.message = message; return this; }
        public ResponseBuilder header(String name, String value) 
            { this.headers.put(name, value); return this; }
        
        public void writeTo(HttpServletResponse response) throws Exception {
            response.setStatus(status);
            for (String name: headers.keySet()) {
                response.setHeader(name, headers.get(name));
            }
            if (message != null) {
                PrintWriter writer = response.getWriter();
                writer.write(message);
                response.flushBuffer();
            }
        }
    }
    
}
