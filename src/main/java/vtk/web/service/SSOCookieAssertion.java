package vtk.web.service;

import java.util.Date;
import java.util.Optional;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.security.web.SecurityInitializer;

/**
 * Assertion that checks for UIO_AUTH_SSO cookie and if several other conditions are met, appends the authTarget
 * parameter and does a redirect
 * 
 * <p>
 * Configurable properties:
 * <ul>
 * <li><code>serviceProviderURI</code> - the endpoint for the SP
 * <li><code>wordWhitelist</code> - lift of resources that should be SSO redirected.
 * <li><code>ssoTimeout</code> - timeout to do the redirect
 * </ul>
 * 
 */

public class SSOCookieAssertion implements WebAssertion {

    private String uioAuthSSO;
    private String serviceProviderURI;
    private String[] wordWhitelist;
    private Long ssoTimeout;

    @Override
    public boolean conflicts(WebAssertion assertion) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean matches(HttpServletRequest request, Resource resource, Principal principal) {
        Boolean doRedirect = false;

        // No check for action=refresh-lock on the assumption that java refreshes don't trigger
        // a redirect in the browser
        if (getCookie(request, uioAuthSSO) != null && getCookie(request, SecurityInitializer.VRTXLINK_COOKIE) == null
                && request.getParameter("authTarget") == null && !request.getRequestURI().contains(serviceProviderURI)) {

            StringBuffer url = request.getRequestURL();

            for (String word : wordWhitelist) {
                if (url.toString().endsWith(word.trim())) {
                    doRedirect = true;
                }
            }

            Long cookieTimestamp = new Long(0);
            try {
                cookieTimestamp = Long.valueOf(getCookie(request, uioAuthSSO).getValue());
            } catch (NumberFormatException e) {

            }
            Long currentTime = new Date().getTime();

            if (currentTime - cookieTimestamp > ssoTimeout) {
                doRedirect = false;
            }

        }
        return doRedirect;
    }

    @Override
    public Optional<URL> processURL(URL url, Resource resource, Principal principal) {
        return Optional.empty();
    }

    @Override
    public URL processURL(URL url) {
        return url;
    }

    public void setUioAuthSSO(String uioAuthSSO) {
        this.uioAuthSSO = uioAuthSSO;
    }

    public void setServiceProviderURI(String serviceProviderURI) {
        this.serviceProviderURI = serviceProviderURI;
    }

    public void setWordWhitelist(String[] wordWhitelist) {
        this.wordWhitelist = wordWhitelist;
    }

    public void setSsoTimeout(Long ssoTimeout) {
        this.ssoTimeout = ssoTimeout;
    }

    private static Cookie getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie;
            }
        }
        return null;
    }
}
