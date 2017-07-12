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
package vtk.web;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Acl;
import vtk.repository.Privilege;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;
import vtk.text.html.HtmlUtil;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class ACLTooltipHelper {
   
    private Service permissionsService;
    private Repository repository;
    private org.springframework.web.servlet.support.RequestContext springRequestContext;
    
    public String generateTitle(PropertySet r, Acl acl, boolean isInheritedAcl, HttpServletRequest request) {
        return generateTitle(r, acl, isInheritedAcl, null, request);
    }

    public String generateTitle(PropertySet r, Acl acl, boolean isInheritedAcl, String name, HttpServletRequest request) {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        
        boolean authorizedToRead = authorizedTo(acl, requestContext.getPrincipal(), Privilege.READ);
        boolean authorizedToAdmin = authorizedTo(acl, requestContext.getPrincipal(), Privilege.ALL);
        
        StringBuilder title = new StringBuilder();
        this.springRequestContext = new org.springframework.web.servlet.support.RequestContext(request);

        title.append("<span id=&quot;title-wrapper&quot;>");
        if(name != null) {
            title.append("<strong id=&quot;title&quot;>" + HtmlUtil.encodeBasicEntities(name) + "</strong>");
        }
        if (isInheritedAcl) {
            String inheritedPermissionTitle = getLocalizedTitle(request, "report.list-resources.inherited-permissions", null);
            if(name == null) {
                inheritedPermissionTitle = inheritedPermissionTitle.substring(0, 1).toUpperCase() + inheritedPermissionTitle.substring(1);
            } else {
                inheritedPermissionTitle = " " + inheritedPermissionTitle;
            }
            title.append(inheritedPermissionTitle);
            genEditOrViewButton(request, r, authorizedToAdmin, authorizedToRead, title);
            title.append("</span><span class=&quot;inherited-permissions&quot;>");
        } else {
            String inheritedPermissionTitle = getLocalizedTitle(request, "report.list-resources.own-permissions", null);
            if(name == null) {
                inheritedPermissionTitle = inheritedPermissionTitle.substring(0, 1).toUpperCase() + inheritedPermissionTitle.substring(1);
            } else {
                inheritedPermissionTitle = " " + inheritedPermissionTitle;
            }
            title.append(inheritedPermissionTitle);
            genEditOrViewButton(request, r, authorizedToAdmin, authorizedToRead, title);
            title.append("</span>");
        }

        // Generate table with permissions
        String notAssigned = getLocalizedTitle(request, "permissions.not.assigned", null).toLowerCase();
        title.append("<table><tbody>");

        String[] aclFormatted = getAclFormatted(acl, request);

        String read = aclFormatted[0].isEmpty() ? notAssigned : HtmlUtil.encodeBasicEntities(aclFormatted[0]);
        title.append("<tr><th scope=&quot;row&quot;>" + getLocalizedTitle(request, "permissions.privilege.read", null)
                + ":</th><td>" + read + "</td></tr>");

        String write = aclFormatted[1].isEmpty() ? notAssigned : HtmlUtil.encodeBasicEntities(aclFormatted[1]);
        title.append("<tr><th scope=&quot;row&quot;>" + getLocalizedTitle(request, "permissions.privilege.read-write", null)
                + ":</th><td>" + write + "</td></tr>");

        String admin = aclFormatted[2].isEmpty() ? notAssigned : HtmlUtil.encodeBasicEntities(aclFormatted[2]);
        title.append("<tr><th scope=&quot;row&quot;>"
                + getLocalizedTitle(request, "report.list-resources.admin-permission", null)
                + ":</th><td>" + admin + "</td></tr>");

        title.append("</tbody></table>");

        if (isInheritedAcl) {
            title.append("</span>");
        }
        
        return title.toString();
    }
    
    public boolean authorizedTo(Acl acl, Principal principal, Privilege privilege) {
        return repository.authorize(principal, acl, privilege);
    }

    private void genEditOrViewButton(HttpServletRequest request, PropertySet r, boolean authorizedToAdmin,
            boolean authorizedToRead, StringBuilder title) {
        String uriService = permissionsService.urlConstructor(URL.create(request))
                .withURI(r.getURI())
                .constructURL()
                .getPathRepresentation();
        if (authorizedToAdmin) {
            title.append("&nbsp;&nbsp;<a class=&quot;vrtx-button-small&quot; href=&quot;" + uriService
                    + "&quot;><span>" + getLocalizedTitle(request, "report.list-resources.edit", null)
                    + "</span></a>");
        } else if (authorizedToRead) {
            title.append("&nbsp;&nbsp;<a class=&quot;vrtx-button-small&quot; href=&quot;" + uriService
                    + "&quot;><span>" + getLocalizedTitle(request, "report.list-resources.view", null)
                    + "</span></a>");
        }
    }

    private String[] getAclFormatted(Acl acl, HttpServletRequest request) {
        String[] aclFormatted = { "", "", "" }; // READ, READ_WRITE, ALL

        for (Privilege action : Privilege.values()) {
            String actionName = action.getName();
            Principal[] privilegedUsers = acl.listPrivilegedUsers(action);
            Principal[] privilegedGroups = acl.listPrivilegedGroups(action);
            Principal[] privilegedPseudoPrincipals = acl.listPrivilegedPseudoPrincipals(action);
            StringBuilder combined = new StringBuilder();
            int i = 0;
            int len = privilegedPseudoPrincipals.length + privilegedUsers.length + privilegedGroups.length;
            boolean all = false;

            for (Principal p : privilegedPseudoPrincipals) {
                String pseudo = this.getLocalizedTitle(request, "pseudoPrincipal." + p.getName(), null);
                if (p.getName() == PrincipalFactory.NAME_ALL) {
                    all = true;
                    combined.append(pseudo);
                }
                if ((len == 1 || i == len - 1) && !all) {
                    combined.append(pseudo);
                } else if (!all) {
                    combined.append(pseudo + ", ");
                }
                i++;
            }
            if (!all) {
                for (Principal p : privilegedUsers) {
                    if (len == 1 || i == len - 1) {
                        combined.append(p.getDescription());
                    } else {
                        combined.append(p.getDescription() + ", ");
                    }
                    i++;
                }
                for (Principal p : privilegedGroups) {
                    // Avoid "description" for groups (not useful data), use unqualified name.
                    if (len == 1 || i == len - 1) {
                        combined.append(p.getName());
                    } else {
                        combined.append(p.getName() + ", ");
                    }
                    i++;
                }
            }
            if (actionName == "read") {
                aclFormatted[0] = combined.toString();
            } else if (actionName == "read-write") {
                aclFormatted[1] = combined.toString();
            } else if (actionName == "all") {
                aclFormatted[2] = combined.toString();
            }
        }
        return aclFormatted;
    }

    private String getLocalizedTitle(HttpServletRequest request, String key, Object[] params) {
        if (params != null) {
            return this.springRequestContext.getMessage(key, params);
        }
        return this.springRequestContext.getMessage(key);
    }
    

    @Required
    public void setPermissionsService(Service permissionsService) {
        this.permissionsService = permissionsService;
    }
    
    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }
    
}
