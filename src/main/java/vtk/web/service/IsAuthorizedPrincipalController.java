package vtk.web.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import vtk.web.RequestContext;

public class IsAuthorizedPrincipalController implements Controller {

    private AuthorizationManager authorizationManager;
    private String viewName;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        Map<String, Object> model = new HashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();

        RequestContext rc = RequestContext.getRequestContext();
        Repository repository = rc.getRepository();
        Resource resource = repository.retrieve(rc.getSecurityToken(), rc.getResourceURI(), true);

        String principalParam = request.getParameter("principal");
        String privilegeParam = request.getParameter("privilege");
        if (privilegeParam == null) {
            body.put("errorMsg", "Parameter 'privilege' must be given as argument.");

            model.put("json", body);
            model.put("status", 400);
            return new ModelAndView(viewName, model);
        }

        Principal principal;
        if (principalParam != null) {
            principal = new PrincipalImpl(principalParam, Principal.Type.USER);
        } else {
            principal = rc.getPrincipal();
        }

        Privilege privilege;
        try {
            privilege = Privilege.forName(privilegeParam.toLowerCase());
        } catch (IllegalArgumentException iae) {
            List<String> privilegeNames = new ArrayList<>();
            for (Privilege p : Privilege.values()) {
                privilegeNames.add(p.getName());
            }
            Collections.sort(privilegeNames);

            body.put("errorMsg", "Privilege '" + privilegeParam + "' does not exist.");
            body.put("validOptions", privilegeNames);

            model.put("json", body);
            model.put("status", 400);
            return new ModelAndView(viewName, model);
        }

        body.put("isAuthorized", authorizationManager.authorize(principal, resource.getAcl(), privilege));
        body.put("principal", principal != null ? principal.getQualifiedName(): null);
        body.put("privilege", privilege.getName());

        model.put("json", body);
        model.put("status", 200);
        return new ModelAndView(viewName, model);
    }

    @Required
    public void setAuthorizationManager(AuthorizationManager authorizationManager) {
        this.authorizationManager = authorizationManager;
    }

    @Required
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

}
