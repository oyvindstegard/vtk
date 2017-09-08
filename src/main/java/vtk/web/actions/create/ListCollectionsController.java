/* Copyright (c) 2011, 2013 University of Oslo, Norway
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
package vtk.web.actions.create;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.HttpRequestHandler;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.Principal;
import vtk.util.text.Json;
import vtk.util.text.JsonStreamer;
import vtk.web.JSONTreeHelper;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.ServiceUnlinkableException;
import vtk.web.service.URL;

public class ListCollectionsController implements HttpRequestHandler {

    private ListCollectionsProvider provider;
    private Service service;
    private String action;
    private Repository repository;
    private PropertyTypeDefinition unpublishedCollectionPropDef;
    
    private final static String PARAMETER_SERVICE = "service";
    private final static String PARAMETER_REPORT_TYPE = "report-type";

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Path uri = null;
        try {
            uri = Path.fromString(request.getParameter("uri"));
        }
        catch (Exception e) {
            badRequest(e, response);
            return;
        }
        if (uri == null) {
            return;
        }
        
        String token = RequestContext.getRequestContext(request).getSecurityToken();
        List<Resource> resources = provider.buildSearchAndPopulateResources(uri, token);
        writeResults(resources, request, response, token);
    }
    

    private void writeResults(List<Resource> resources, HttpServletRequest request, HttpServletResponse response,
            String token) throws IOException {

        String buttonText = mapServiceParamToButtonText(request.getParameter(PARAMETER_SERVICE));
        Map<String, List<String>> uriParameters = getReportTypeParam(request.getParameter(PARAMETER_REPORT_TYPE));
        
        Json.ListContainer listNodes = new Json.ListContainer();
        for (Resource resource : resources) {
            listNodes.add(generateJSONObjectNode(resource, token, request, uriParameters, buttonText));
        }
        okRequest(listNodes, response);
    }
    

    private void okRequest(Json.ListContainer arr, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/plain;charset=utf-8"); /* XXX: Should be application/json? */
        String str = JsonStreamer.toJson(arr, 1);
        writeResponse(str, response);
    }

    private void badRequest(Throwable e, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        writeResponse(e.getMessage(), response);
    }
    
    private void writeResponse(String responseText, HttpServletResponse response) throws IOException {
        PrintWriter writer = response.getWriter();
        try {
            writer.write(responseText);
        } finally {
            writer.close();
        }
    }
    
    private JSONObject generateJSONObjectNode(Resource resource, String token, HttpServletRequest request, Map<String, List<String>> uriParameters, String buttonText) {
        JSONObject o = new JSONObject();

        RequestContext requestContext = RequestContext.getRequestContext(request);
        Principal principal = requestContext.getPrincipal();

        String title;
        try {
            URL url = service.urlConstructor(requestContext.getRequestURL())
                    .withResource(resource)
                    .withPrincipal(principal)
                    .withParameters(uriParameters)
                    .constructURL();
            
            if (action != null) {
                url.setRef("action=" + action);
            }
            title = "<a target=&quot;_top&quot; class=&quot;vrtx-button-small&quot; href=&quot;"
                    + url.getPathRepresentation() + "&quot;>"
                    + "<span>" + provider.getLocalizedTitle(request, buttonText, null)
                    + "</span>" + "</a>";
        } catch (ServiceUnlinkableException e) {
            title = "<span class=&quot;no-create-permission&quot;>"
                    + provider.getLocalizedTitle(request, "manage.no-permission", null) + "</span>";
        } catch (Exception e) {
            return null;
        }
        Path uri = resource.getURI();
        
        boolean unpublished = resource.getProperty(unpublishedCollectionPropDef) != null 
                || !resource.isPublished();
        
        o.put(JSONTreeHelper.URI, uri.toString());
        o.put(JSONTreeHelper.TITLE, title);
        o.put(JSONTreeHelper.CLASSES_SPAN, JSONTreeHelper.CLASSES_FOLDER + (!unpublished ? "" : " " + JSONTreeHelper.CLASSES_UNPUBLISHED));
        o.put(JSONTreeHelper.HAS_CHILDREN, provider.hasChildren(resource, token));
        o.put(JSONTreeHelper.TEXT, uri.isRoot() ? repository.getId() : resource.getName());

        return o;
    }

    
    private String mapServiceParamToButtonText(String serviceParam) {
        if (serviceParam != null) {
            if (serviceParam.equals("upload-file-from-drop-down")) {
                return "manage.upload-here";
            } else if (serviceParam.equals("view-report-from-drop-down")) {
                return "manage.view-this";
            }
        }
        return "manage.place-here";
    }
    
    private Map<String, List<String>> getReportTypeParam(String reportType) {
        Map<String, List<String>> uriParameters = new HashMap<>();
        if (reportType != null) {
            uriParameters.computeIfAbsent(PARAMETER_REPORT_TYPE, k -> new ArrayList<>()).add(reportType);
        }
        return uriParameters;
    }

    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    @Required
    public void setProvider(ListCollectionsProvider provider) {
        this.provider = provider;
    }
    
    @Required
    public void setUnpublishedCollectionPropDef(PropertyTypeDefinition prop) {
        this.unpublishedCollectionPropDef = prop;
    }

    @Required
    public void setService(Service service) {
        this.service = service;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
