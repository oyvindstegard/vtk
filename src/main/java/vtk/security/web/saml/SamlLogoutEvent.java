/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vtk.security.web.saml;

import javax.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEvent;
import vtk.security.Principal;

/**
 * SAML logout application event.
 */
public class SamlLogoutEvent extends ApplicationEvent {

    private static final long serialVersionUID = -3165802907172157767L;

    private final HttpServletRequest request;
    private final Principal principal;

    /**
     * A SAML logout event may have information of the user id logging or not.
     * @param source source of event, not null
     * @param request the request which caused logout and invaldation of user session
     * @param principal that logged out
     */
    SamlLogoutEvent(Object source, HttpServletRequest request, Principal principal) {
        super(source);
        this.request = request;
        this.principal = principal;
    }

    public Principal getPrincipal() {
        return principal;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

}
