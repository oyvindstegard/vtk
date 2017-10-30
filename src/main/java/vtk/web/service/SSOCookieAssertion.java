package vtk.web.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.security.web.SecurityInitializer;
import vtk.util.web.HttpUtil;

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
    private Long ssoTimeout;
    private List<Pattern> urlPatternWhitelist = Collections.emptyList();

    @Override
    public boolean conflicts(WebAssertion assertion) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean matches(HttpServletRequest request, Resource resource, Principal principal) {
        boolean doRedirect = false;

        // No check for action=refresh-lock on the assumption that java refreshes don't trigger
        // a redirect in the browser
        final Optional<Cookie> ssoCookie = HttpUtil.getCookie(request, uioAuthSSO);
        if (ssoCookie.isPresent()
                && !HttpUtil.getCookie(request, SecurityInitializer.VRTXLINK_COOKIE).isPresent()
                && request.getParameter("authTarget") == null && !request.getRequestURI().contains(serviceProviderURI)) {

            String url = request.getRequestURL().toString();

            for (Pattern p: urlPatternWhitelist) {
                if (p.matcher(url).matches()) {
                    doRedirect = true;
                    break;
                }
            }

            Long cookieTimestamp = new Long(0);
            try {
                cookieTimestamp = Long.valueOf(ssoCookie.get().getValue());
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

    /**
     * Sets initial list of URL patterns to whitelist for SSO assertion.
     * @param urlPatternWhitelist
     */
    public void setUrlPatternWhitelist(List<Pattern> urlPatternWhitelist) {
        this.urlPatternWhitelist = Objects.requireNonNull(urlPatternWhitelist);
    }

    /**
     * Adds a URL pattern to whitelist for SSO assertion.
     *
     * <p>Use of this method is not thread safe at runtime and is meant to be
     * used for configuration extensibility at init time (which is single threaded).
     *
     * @param pattern a pattern matching URLs for which the SSO service should kick in.
     */
    public void addUrlPatternToWhitelist(Pattern pattern) {
        List<Pattern> newList = new ArrayList<>(this.urlPatternWhitelist);
        newList.add(pattern);
        this.urlPatternWhitelist = newList;
    }

    public void setSsoTimeout(Long ssoTimeout) {
        this.ssoTimeout = ssoTimeout;
    }

}
