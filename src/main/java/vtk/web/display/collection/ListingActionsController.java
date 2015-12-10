/* Copyright (c) 2014, University of Oslo, Norway
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
package vtk.web.display.collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Resource;
import vtk.repository.TypeInfo;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;


public class ListingActionsController implements Controller {
    
    private String viewName;
    private Map<String, Service> actions = new LinkedHashMap<>();
    private Map<String, Service> globalActions = new LinkedHashMap<>();
    
    public ListingActionsController(String viewName, Map<String, Service> actions, 
            Map<String, Service> globalActions) {
        if (viewName == null) {
            throw new IllegalArgumentException("viewName is null");
        }
        this.viewName = viewName;
        if (actions != null) {
            for (String key: actions.keySet()) {
                Service val = actions.get(key);
                if (val == null) {
                    throw new IllegalArgumentException(
                            "actions: service is null for key: " + key);
                }
                this.actions.put(key, val);
            }
        }
        if (globalActions != null) {
            for (String key: globalActions.keySet()) {
                Service val = globalActions.get(key);
                if (val == null) {
                    throw new IllegalArgumentException(
                            "globalActions: service is null for key: " + key);
                }
                this.globalActions.put(key, val);
            }
        }
    }
    
    @Override
    public ModelAndView handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext();

        Resource collection = requestContext.getRepository().retrieve(
                requestContext.getSecurityToken(), 
                requestContext.getCurrentCollection(), true);
        
        Resource[] children = requestContext.getRepository().listChildren(
                requestContext.getSecurityToken(), 
                requestContext.getCurrentCollection(), true);
        
        List<String> paramList = csvParameter(request, "types");

        List<String> types = new ArrayList<>();
        for (String s: paramList)
            types.add(s.trim());

        children = filter(requestContext, children, types);

        paramList = csvParameter(request, "actions");
        List<String> actions = new LinkedList<String>();
        for (String s: paramList) {
            if (!this.actions.containsKey(s))
                throw new IllegalArgumentException("Invalid action: '" + s 
                        + "', not in: " + this.actions.keySet());
                actions.add(s);
        }
        paramList = csvParameter(request, "global-actions");
        List<String> globalActions = new LinkedList<>();
        for (String s: paramList) {
            if (!this.globalActions.containsKey(s))
                throw new IllegalArgumentException("Invalid global-action: '" + s 
                        + "', not in: " + this.globalActions.keySet());;
                globalActions.add(s);
        }

        List<Object> entries = new LinkedList<>();
        for (Resource r: children) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("resource", r);
            entry.put("actions", genActions(
                    requestContext, r, this.actions, actions));
            entries.add(entry);
        }

        Map<String, Object> model = new HashMap<>();
        model.put("collection", collection);
        model.put("entries", entries);
        model.put("globalActions", genActions(
                requestContext, collection, this.globalActions, globalActions));
        return new ModelAndView(viewName, model);
    }
    
    
    private List<String> csvParameter(HttpServletRequest request, String parameter) {
        List<String> result = new LinkedList<String>();
        String param = request.getParameter(parameter);
        if (param != null) {
            String[] values = param.split(",");
            for (String s: values) result.add(s);
        }
        return result;
    }

    private Resource[] filter(RequestContext requestContext, Resource[] list, List<String> types) {
        List<Resource> result = new LinkedList<>();
        for (Resource r: list) {
            String resourceType = r.getResourceType();
            TypeInfo typeInfo = requestContext.getRepository().getTypeInfo(resourceType);
            for (String type: types) {
                if (typeInfo.isOfType(type)) {
                    result.add(r);
                    break;
                }
            }
        }
        return result.toArray(new Resource[result.size()]);
    }

    private Map<String, URL> genActions(RequestContext requestContext, Resource resource, 
            Map<String, Service> actions, Collection<String> selection) {
        Map<String, URL> result = new LinkedHashMap<>();

        for (String action: selection) {
            Service service = actions.get(action);
            try {
                URL url = service.constructURL(resource, requestContext.getPrincipal());
                result.put(action, url);
            } catch (Throwable t) { }
        }
        return result;
    }
    
}
