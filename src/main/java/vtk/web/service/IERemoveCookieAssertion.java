package vtk.web.service;

import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.security.web.saml.SamlAuthenticationHandler;

public class IERemoveCookieAssertion implements WebAssertion {

    private String ieCookieLogoutTicket;

    @Override
    public boolean conflicts(WebAssertion assertion) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean matches(HttpServletRequest request, Resource resource, Principal principal) {
        if (request.getParameter(ieCookieLogoutTicket) != null && SamlAuthenticationHandler.browserIsIE(request)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Optional<URL> processURL(URL url, Resource resource, Principal principal) {
        return Optional.empty();
    }

    @Override
    public URL processURL(URL url) {
        return url;
    }
    
    public void setIeCookieLogoutTicket(String ieCookieLogoutTicket) {
        this.ieCookieLogoutTicket = ieCookieLogoutTicket;
    }

}
