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
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Acl;
import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.Resource;
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
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            URL url = new URL(requestContext.getRequestURL());
            url.setPath(ancestor.getURI());
            response.addHeader("Link", "<" + url + ">; rel=\"inherited-acl\"");
            response.setContentType("text/plain;charset=utf-8");
            PrintWriter writer = response.getWriter();
            writer.write("ACL Not Found on \n" + resource.getURI());
            writer.write("Nearest ACL: " + ancestor.getURI() + "\n");
            response.flushBuffer();
        }
        
        response.setContentType("application/json");
        PrintWriter writer = response.getWriter();
        writer.write(JsonStreamer.toJson(aclToJson(resource.getAcl()), 2, true));
        response.flushBuffer();
    }
    

    private void updateAcl(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        if (!"application/json".equals(request.getContentType())) {
            response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
            response.setContentType("text/plain;charset=utf-8");
            response.getWriter().write("Content-Type application/json required for " 
                    + request.getMethod() + " method");
            return;
        }
        
        Result<Json.MapContainer> json = parseJson(request.getInputStream());
        if (json.failure.isPresent()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain;charset=utf-8");
            response.getWriter().write("Failed to parse JSON from input: " 
                    + json.failure.get().getMessage() + "\n");
            response.flushBuffer();
            return;
        }
        
        Result<Acl> acl = json.flatMap(aclMapper);
        if (acl.failure.isPresent()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain;charset=utf-8");
            response.getWriter().write("Failed to map JSON input to a valid ACL: " 
                    + acl.failure.get().getMessage() + "\n");
            response.flushBuffer();
            return;
        }
        
        RequestContext requestContext = RequestContext.getRequestContext();
        try {
            Resource updated = requestContext.getRepository().storeACL(requestContext.getSecurityToken(), 
                            requestContext.getResourceURI(), acl.result.get());
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/plain;charset=utf-8");
            response.getWriter().write("ACL updated\n");
            response.flushBuffer();
        }
        catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
    
    
    private void deleteAcl(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Resource resource = requestContext.getRepository().retrieve(requestContext.getSecurityToken(), 
                requestContext.getResourceURI(), true);
        
        if (resource.isInheritedAcl()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            PrintWriter writer = response.getWriter();
            writer.write("404 Not Found\n");
            response.flushBuffer();
        }
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter writer = response.getWriter();
        writer.write("Deleted ACL\n");
        response.flushBuffer();
    }

    private void unknownMethod(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        throw new UnsupportedOperationException("Not implemented");
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
    
}
