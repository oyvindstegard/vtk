/* Copyright (c) 2004, University of Oslo, Norway
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 
 *  * Neither the name of the University of Oslo nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *      
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.vortikal.web.servlet;

import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.springframework.web.servlet.LocaleResolver;
import org.vortikal.repository.Path;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.security.SecurityContext;
import org.vortikal.util.repository.LocaleHelper;
import org.vortikal.web.RequestContext;



/**
 * Resolves locale for the current resource.
 * 
 * <p>The locale is set to the first match:
 * <ul>
 *   <li>The resource {@link Resource#getContentLanguage() contentLanguage}
 *   <li>The nearest parent with {@link Resource#getContentLanguage() contentLanguage} set
 *   <li>defaultLocale
 *   
 *   XXX: Needs to be fixed (will give wrong locale if read-processed is used?), 
 *   should probably use a trusted token?
 * 
 */
public class ResourceAwareLocaleResolver implements LocaleResolver {
    
    protected static final String LOCALE_REQUEST_ATTRIBUTE_NAME =
        ResourceAwareLocaleResolver.class.getName() + ".RequestAttribute";
    
    private Locale defaultLocale;
    private Repository repository;
    

    /**
     * Set the default locale that this resolver will return if
     * the request does not contain a cookie. If the default
     * locale is not set, the accept header locale of the client
     * is returned.
     */
    public void setDefaultLocale(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
    }


    public void setRepository(Repository repository) {
        this.repository = repository;
    }
    

    public Locale resolveLocale(HttpServletRequest request) {
		
    	if (request != null) {
			Locale locale = (Locale) request.getAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME);
    		if (locale != null) {
    			return locale;
    		}
    	}

    	SecurityContext securityContext = SecurityContext.getSecurityContext();
        String token = securityContext.getToken();
        RequestContext requestContext = RequestContext.getRequestContext();
        Path uri = requestContext.getResourceURI();
        
        try {
            Resource resource = this.repository.retrieve(token, uri, true);
            Locale locale = LocaleHelper.getLocale(resource.getContentLanguage());

            if (locale == null) {
                // Check for the nearest ancestor that has a locale set and use it
                locale = getNearestAncestorLocale(token, resource);
            }
            if (locale == null) {
                // If no ancestor has a locale set, use the default of the host
            	locale = this.defaultLocale;
            }
            
            return locale;
        } catch (Throwable t) {
            return this.defaultLocale;
        }
    }
    

    private Locale getNearestAncestorLocale(String token, Resource resource) throws Exception {
        Path parentURI = resource.getURI().getParent();
        while (parentURI != null) {
            Resource parent = this.repository.retrieve(token, parentURI, false);
            if (!StringUtils.isBlank(parent.getContentLanguage())) {
                return LocaleHelper.getLocale(parent.getContentLanguage());
            }
            parentURI = parentURI.getParent();
        }
        return null;
    }


    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        request.setAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME, locale);
    }
    
}
