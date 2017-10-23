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
 * SAML login application event.
 */
public class SamlLoginEvent extends ApplicationEvent {

    private static final long serialVersionUID = 4865320495417194222L;

    private final HttpServletRequest request;
    private final Principal principal;
    private final UserData userData;

    SamlLoginEvent(Object source, HttpServletRequest request, Principal principal, UserData data) {
        super(source);
        this.request = request;
        this.principal = principal;
        this.userData = data;
    }

    public Principal getPrincipal() {
        return principal;
    }

    public UserData getUserData() {
        return userData;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

}
