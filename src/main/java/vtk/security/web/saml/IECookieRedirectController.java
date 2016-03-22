package vtk.security.web.saml;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

/**
 * Used to set cookies in the other cookie store in IE.
 * 
 * IE browser working in the UiO domain have separate cookie stores for view and manage.
 * 
 * When cookies are set (login) for one store, this controller sets them for the other store.
 */

public class IECookieRedirectController implements Controller {

    private String ieCookieTicket;

    private String vrtxAuthSP;
    private String uioAuthIDP;
    private String uioAuthSSO;
    private String ieReturnURL;

    private Map<String, String> staticHeaders = new HashMap<String, String>();

    private static Logger authLogger = LoggerFactory.getLogger("vtk.security.web.AuthLog");

    private IECookieStore iECookieStore;

    private String spCookieDomain = null;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        String returnURL = URLDecoder.decode(request.getParameter(ieReturnURL), "UTF-8");

        if (request.getParameter("vrtxPreviewForceRefresh") == null) {
            String cookieTicket = request.getParameter(ieCookieTicket);

            Map<String, String> cookieMap = iECookieStore.getToken(request, UUID.fromString(cookieTicket));
            if (cookieMap != null) {
                String spCookie = cookieMap.get(vrtxAuthSP);
                String idpCookie = cookieMap.get(uioAuthIDP);
                String ssoCookie = cookieMap.get(uioAuthSSO);
                returnURL = cookieMap.get(ieReturnURL);

                if (spCookie != null) {
                    if (authLogger.isDebugEnabled()) {
                        authLogger.debug(request.getRemoteAddr() + " - request-URI: " + request.getRequestURI() + " - "
                                + "IE cookie setter setting: " + vrtxAuthSP + " : " + spCookie);
                    }

                    Cookie c = new Cookie(vrtxAuthSP, cookieMap.get(vrtxAuthSP));
                    c.setSecure(true);
                    c.setPath("/");
                    if (this.spCookieDomain != null) {
                        c.setDomain(this.spCookieDomain);
                    }
                    response.addCookie(c);
                }

                if (idpCookie != null) {
                    if (authLogger.isDebugEnabled()) {
                        authLogger.debug(request.getRemoteAddr() + " - request-URI: " + request.getRequestURI() + " - "
                                + "IE cookie setter setting: " + uioAuthIDP + " : " + idpCookie);
                    }

                    Cookie c = new Cookie(uioAuthIDP, cookieMap.get(uioAuthIDP));
                    c.setSecure(true);
                    c.setPath("/");
                    if (this.spCookieDomain != null) {
                        c.setDomain(this.spCookieDomain);
                    }
                    response.addCookie(c);
                }

                if (ssoCookie != null) {
                    if (authLogger.isDebugEnabled()) {
                        authLogger.debug(request.getRemoteAddr() + " - request-URI: " + request.getRequestURI() + " - "
                                + "IE cookie setter setting: " + uioAuthSSO + " : " + ssoCookie);
                    }

                    Cookie c = new Cookie(uioAuthSSO, cookieMap.get(uioAuthSSO));
                    c.setPath("/");
                    if (this.spCookieDomain != null) {
                        c.setDomain(this.spCookieDomain);
                    }
                    response.addCookie(c);
                }
                iECookieStore.dropToken(request, UUID.fromString(cookieTicket));
            }
        }

        // setHeaders(response);
        response.sendRedirect(returnURL);
        return null;
    }

    public void setIeCookieTicket(String ieCookieTicket) {
        this.ieCookieTicket = ieCookieTicket;
    }

    public void setVrtxAuthSP(String vrtxAuthSP) {
        this.vrtxAuthSP = vrtxAuthSP;
    }

    public void setUioAuthIDP(String uioAuthIDP) {
        this.uioAuthIDP = uioAuthIDP;
    }

    public void setUioAuthSSO(String uioAuthSSO) {
        this.uioAuthSSO = uioAuthSSO;
    }

    public void setIeReturnURL(String ieReturnURL) {
        this.ieReturnURL = ieReturnURL;
    }

    public void setStaticHeaders(Map<String, String> staticHeaders) {
        this.staticHeaders = staticHeaders;
    }

    private void setHeaders(HttpServletResponse response) {
        for (String header : this.staticHeaders.keySet()) {
            response.setHeader(header, this.staticHeaders.get(header));
        }
    }

    public void setiECookieStore(IECookieStore iECookieStore) {
        this.iECookieStore = iECookieStore;
    }

    public void setSpCookieDomain(String spCookieDomain) {
        if (spCookieDomain != null && !"".equals(spCookieDomain.trim())) {
            this.spCookieDomain = spCookieDomain;
        }
    }
}
