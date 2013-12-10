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
package org.vortikal.web.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.vortikal.repository.Acl;
import org.vortikal.repository.Path;
import org.vortikal.repository.Privilege;
import org.vortikal.repository.Resource;
import org.vortikal.text.html.HtmlUtil;
import org.vortikal.web.JSONTreeHelper;
import org.vortikal.web.RequestContext;
import org.vortikal.web.service.provider.ListResourcesProvider;

public class ListResourcesService implements Controller {

    private ListResourcesProvider provider;
    private Service permissionsService;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String uri = null;
        try {
            uri = (String) request.getParameter("uri");
        } catch (Exception e) {
            badRequest(e, response);
            return null;
        }
        if (uri == null) {
            return null;
        }
        
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        List<Resource> resources = this.provider.buildSearchAndPopulateResources(Path.fromString(uri), token, request);
        writeResults(resources, request, response);
        return null;
    }

    private void okRequest(JSONArray arr, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/plain;charset=utf-8"); /* XXX: Should be application/json? */
        writeResponse(arr.toString(1), response);
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
    
    private void writeResults(List<Resource> resources, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();

        JSONArray list = new JSONArray();
        for (Resource r : resources) {
            JSONObject o = new JSONObject();

            Acl acl = r.getAcl();
            boolean authorizedToRead = this.provider.authorizedTo(acl, requestContext.getPrincipal(), Privilege.READ);
            boolean authorizedToAdmin = this.provider.authorizedTo(acl, requestContext.getPrincipal(), Privilege.ALL);

            String listClasses = "";
            String spanClasses = "";

            // Add classes
            if (r.isCollection()) {
                spanClasses = JSONTreeHelper.CLASSES_FOLDER;
                o.put(JSONTreeHelper.HAS_CHILDREN, (r.getChildURIs() != null && authorizedToRead) ? !r.getChildURIs().isEmpty()
                        : false);
            } else {
                spanClasses = JSONTreeHelper.CLASSES_FILE;
            }
            if (r.isReadRestricted()) {
                spanClasses += " " + JSONTreeHelper.CLASSES_RESTRICTED;
            } else {
                spanClasses += " " + JSONTreeHelper.CLASSES_ALLOWED_FOR_ALL;
            }

            // Generate title
            StringBuilder title = new StringBuilder();
            String name = HtmlUtil.encodeBasicEntities(r.getName());

            title.append("<span id=&quot;title-wrapper&quot;><strong id=&quot;title&quot;>" + name + "</strong>");
            if (r.isInheritedAcl()) {
                title.append(" "
                        + provider.getLocalizedTitle(request, "report.list-resources.inherited-permissions", null));
                this.genEditOrViewButton(request, r, authorizedToAdmin, authorizedToRead, title);
                title.append("</span><span class=&quot;inherited-permissions&quot;>");
            } else {
                title.append(" " + provider.getLocalizedTitle(request, "report.list-resources.own-permissions", null));
                this.genEditOrViewButton(request, r, authorizedToAdmin, authorizedToRead, title);
                title.append("</span>");
                listClasses = JSONTreeHelper.CLASSES_NOT_INHERITED;
            }

            // Generate table with permissions
            String notAssigned = provider.getLocalizedTitle(request, "permissions.not.assigned", null).toLowerCase();
            title.append("<table><tbody>");

            String[] aclFormatted = provider.getAclFormatted(acl, request);

            String read = aclFormatted[0].isEmpty() ? notAssigned : aclFormatted[0];
            title.append("<tr><td>" + provider.getLocalizedTitle(request, "permissions.privilege.read", null)
                    + ":</td><td>" + read + "</td></tr>");

            String write = aclFormatted[1].isEmpty() ? notAssigned : aclFormatted[1];
            title.append("<tr><td>" + provider.getLocalizedTitle(request, "permissions.privilege.read-write", null)
                    + ":</td><td>" + write + "</td></tr>");

            String admin = aclFormatted[2].isEmpty() ? notAssigned : aclFormatted[2];
            title.append("<tr><td>"
                    + provider.getLocalizedTitle(request, "report.list-resources.admin-permission", null)
                    + ":</td><td>" + admin + "</td></tr>");

            title.append("</tbody></table>");

            if (r.isInheritedAcl()) {
                title.append("</span>");
            }

            // Add to JSON-object
            o.put(JSONTreeHelper.TEXT, name);
            o.put(JSONTreeHelper.URI, r.getURI().toString());
            o.put(JSONTreeHelper.TITLE, title.toString());
            o.put(JSONTreeHelper.CLASSES_LIST, listClasses);
            o.put(JSONTreeHelper.CLASSES_SPAN, spanClasses);

            // Add object to JSON-array
            list.add(o);
        }
        
        okRequest(list, response);
    }

    private void genEditOrViewButton(HttpServletRequest request, Resource r, boolean authorizedToAdmin,
            boolean authorizedToRead, StringBuilder title) {
        String uriService = this.permissionsService.constructURL(r.getURI()).getPathRepresentation();
        if (authorizedToAdmin) {
            title.append("&nbsp;&nbsp;<a class=&quot;vrtx-button-small&quot; href=&quot;" + uriService
                    + "&quot;><span>" + provider.getLocalizedTitle(request, "report.list-resources.edit", null)
                    + "</span></a>");
        } else if (authorizedToRead) {
            title.append("&nbsp;&nbsp;<a class=&quot;vrtx-button-small&quot; href=&quot;" + uriService
                    + "&quot;><span>" + provider.getLocalizedTitle(request, "report.list-resources.view", null)
                    + "</span></a>");
        }
    }

    @Required
    public void setProvider(ListResourcesProvider provider) {
        this.provider = provider;
    }

    @Required
    public void setPermissionsService(Service permissionsService) {
        this.permissionsService = permissionsService;
    }

}
