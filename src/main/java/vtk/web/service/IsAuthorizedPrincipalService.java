package vtk.web.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Required;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import vtk.repository.AuthorizationManager;
import vtk.repository.Privilege;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.security.PrincipalImpl;
import vtk.util.text.Json;
import vtk.util.text.JsonStreamer;
import vtk.web.RequestContext;

public class IsAuthorizedPrincipalService implements Controller {

    AuthorizationManager authorizationManager;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        RequestContext rc = RequestContext.getRequestContext();
        Repository repository = rc.getRepository();
        Resource resource = repository.retrieve(rc.getSecurityToken(), rc.getResourceURI(), true);

        Json.MapContainer jsonResponse = new Json.MapContainer();
        response.setContentType("application/json;charset=utf-8");

        String principalParam = request.getParameter("principal");
        String privilegeParam = request.getParameter("privilege");
        if (principalParam == null || privilegeParam == null) {
            jsonResponse.put("errorMsg", "Both 'principal' and 'privilege' must be given as arguments.");

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeResponse(JsonStreamer.toJson(jsonResponse), response);
            return null;
        }

        PrincipalImpl principal = new PrincipalImpl(principalParam, Principal.Type.USER);

        Privilege privilege;
        try {
            privilege = Privilege.valueOf(privilegeParam);
        } catch (IllegalArgumentException iae) {
            List<String> privilegeNames = new ArrayList<>();
            for (Privilege p : Privilege.values()) {
                privilegeNames.add(p.name());
            }
            Collections.sort(privilegeNames);

            jsonResponse.put("errorMsg", "Privilege '" + privilegeParam + "' does not exist.");
            Json.ListContainer validPrivilegeOptions = new Json.ListContainer();
            for (String privilegeName : privilegeNames) {
                validPrivilegeOptions.add(privilegeName);
            }
            jsonResponse.put("validOptions", validPrivilegeOptions);

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeResponse(JsonStreamer.toJson(jsonResponse), response);
            return null;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        jsonResponse.put("isAuthorized", authorizationManager.authorize(principal, resource.getAcl(), privilege));
        writeResponse(JsonStreamer.toJson(jsonResponse), response);
        return null;
    }

    private void writeResponse(String responseText, HttpServletResponse response) throws IOException {
        try (PrintWriter writer = response.getWriter()) {
            writer.write(responseText);
        }
    }

    @Required
    public void setAuthorizationManager(AuthorizationManager authorizationManager) {
        this.authorizationManager = authorizationManager;
    }

}
