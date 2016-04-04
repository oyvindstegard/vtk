package vtk.security.web.saml;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import vtk.web.service.URL;

/**
 * Used to remove cookies in the other cookie store in IE.
 * 
 * IE browser working in the UiO domain have separate cookie stores for view and manage.
 * 
 * When cookies are removed (logout) for one store, this controller removes them for the other store.
 */

public class IERemoveCookieController implements Controller {

    private String spCookieDomain = null;
    private String ieCookieLogoutTicket;
    private IECookieStore iECookieStore;

    private static Logger authLogger = LoggerFactory.getLogger("vtk.security.web.AuthLog");

    private String uioAuthSSO;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        URL currentURL = URL.create(request);

        String cookieTicket = request.getParameter(ieCookieLogoutTicket);
        List<String> cookiesToDelete = new ArrayList<String>();

        if (SamlAuthenticationHandler.getCookie(request, uioAuthSSO) != null) {
            cookiesToDelete.add(uioAuthSSO);
        }

        if (iECookieStore.getToken(request, UUID.fromString(cookieTicket)) != null) {
            for (String key : cookiesToDelete) {
                if (authLogger.isDebugEnabled()) {
                    authLogger.debug(request.getRemoteAddr() + " - request-URI: " + request.getRequestURI() + " - "
                            + "DELETING cookie: " + key);
                }
                Cookie c = new Cookie(key, key);
                c.setPath("/");
                if (this.spCookieDomain != null) {
                    c.setDomain(this.spCookieDomain);
                }
                c.setMaxAge(0);
                response.addCookie(c);
            }
            iECookieStore.dropToken(request, UUID.fromString(cookieTicket));

        }
        currentURL.removeParameter(ieCookieLogoutTicket);
        response.sendRedirect(currentURL.toString());

        return null;
    }

    public void setSpCookieDomain(String spCookieDomain) {
        if (spCookieDomain != null && !"".equals(spCookieDomain.trim())) {
            this.spCookieDomain = spCookieDomain;
        }
    }

    public void setIeCookieLogoutTicket(String ieCookieLogoutTicket) {
        this.ieCookieLogoutTicket = ieCookieLogoutTicket;
    }

    public void setiECookieStore(IECookieStore iECookieStore) {
        this.iECookieStore = iECookieStore;
    }

    public void setUioAuthSSO(String uioAuthSSO) {
        this.uioAuthSSO = uioAuthSSO;
    }
}
