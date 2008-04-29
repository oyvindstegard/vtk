package org.vortikal.security.web;

import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.core.Ordered;
import org.vortikal.security.AuthenticationException;
import org.vortikal.security.AuthenticationProcessingException;
import org.vortikal.security.InvalidPrincipalException;
import org.vortikal.security.Principal;
import org.vortikal.security.PrincipalFactory;
import org.vortikal.util.cache.SimpleCache;
import org.vortikal.util.codec.MD5;
import org.vortikal.web.service.Assertion;

/**
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>recognizedDomains</code> - a {@link Set} specifying the
 *     recognized principal {@link Principal#getDomain domains}. If
 *     this property is not specified, all domains are matched.
 *   <li><code>cache</code> - simple {@link SimpleCache cache} to
 *   allow for clients that don't send cookies (optional)
 *   <li><code>recognizedPrincipals</code> - a {@link Set} of principal
 *     names to recognize when matching authentication
 *     requests. Takes precedence over recognizedDomains. If the principal in an 
 *     authentication request is not present in this set, the authentication 
 *     request is treated as if it were not recognized.
 *   <li><code>order</code> - the bean order returned in {@link
 *   Ordered#getOrder}
 * </ul>
 */
public abstract class AbstractAuthenticationHandler implements
        AuthenticationHandler, Ordered {

    protected Log logger = LogFactory.getLog(this.getClass());

    private PrincipalFactory principalFactory;
    
    /* Simple cache to allow for clients that don't send cookies */
    private SimpleCache<String, Principal> cache;

    private List<Assertion> requestAssertions; 
    
    private Set<String> recognizedDomains = null;

    private int order = Integer.MAX_VALUE;

    public void setCache(SimpleCache<String, Principal> cache) {
        this.cache = cache;
    }

    public void setRecognizedDomains(Set<String> recognizedDomains) {
        this.recognizedDomains = recognizedDomains;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getOrder() {
        return this.order;
    }

    public boolean isRecognizedAuthenticationRequest(HttpServletRequest req)
            throws AuthenticationProcessingException {

        String username = null;

        try {
            username = getUserName(req);
        } catch (IllegalArgumentException e) {
            throw new InvalidAuthenticationRequestException(e);
        }

        Principal principal = null;
        try {
            principal = principalFactory.getPrincipal(username, Principal.Type.USER);
        } catch (InvalidPrincipalException e) {
            return false;
        }

        if (this.requestAssertions != null) {
            for (Assertion assertion : this.requestAssertions) {
                if (!assertion.matches(req, null, principal)) {
                    return false;
                }
            }
        }
        
        if (this.recognizedDomains == null
                || this.recognizedDomains.contains(principal.getDomain()))
            return true;

        return false;
    }

    public Principal authenticate(HttpServletRequest request)
            throws AuthenticationProcessingException, AuthenticationException {

        String username = null;
        String password = null;
        try {

            username = getUserName(request);
            password = getPassword(request);

        } catch (IllegalArgumentException e) {
            throw new InvalidAuthenticationRequestException(e);
        }

        Principal principal = null;

        try {
            principal = principalFactory.getPrincipal(username, Principal.Type.USER);
        } catch (InvalidPrincipalException e) {
            throw new AuthenticationException("Invalid principal '" + username
                    + "'", e);
        }

        String md5sum = null;
        if (this.cache != null) {
            // Only calculate if useful
            md5sum = MD5.md5sum(principal.getQualifiedName() + password);
            
            Principal cachedPrincipal = this.cache.get(md5sum);
            if (cachedPrincipal != null) {
                if (this.logger.isDebugEnabled())
                    this.logger.debug("Found authenticated principal '"
                            + username + "' in cache.");
                return cachedPrincipal;
            }
        }

        authenticateInternal(principal, password);

        if (this.cache != null)
            /* add to cache */
            this.cache.put(md5sum, principal);

        return principal;
    }

    public abstract void authenticateInternal(Principal principal,
            String password) throws AuthenticationProcessingException,
            AuthenticationException;

    protected abstract String getUserName(HttpServletRequest request);

    protected abstract String getPassword(HttpServletRequest request);

    public void setRequestAssertions(List<Assertion> requestAssertions) {
        this.requestAssertions = requestAssertions;
    }

    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }

}