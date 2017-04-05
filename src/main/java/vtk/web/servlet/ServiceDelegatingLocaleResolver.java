/* Copyright (c) 2007, University of Oslo, Norway
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
package vtk.web.servlet;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.LocaleResolver;

import vtk.web.RequestContext;
import vtk.web.service.Service;


/**
 * Looks up locale resolvers in the service tree up towards the root,
 * and delegates to the locale resolver found. Locale resolvers are
 * associated with a service using {@link Service#getAttribute(java.lang.String)  service attributes}.
 */
public class ServiceDelegatingLocaleResolver implements LocaleResolver {

    private String localeResolverAttribute = "localeResolver";
    private LocaleResolver defaultLocaleResolver;

    private Map<String, Locale> localeTranslationMap = new HashMap<>();
    
    @Required public void setDefaultLocaleResolver(LocaleResolver defaultLocaleResolver) {
        this.defaultLocaleResolver = defaultLocaleResolver;
    }
    public void setLocaleResolverAttribute(String localeResolverAttribute) {
        this.localeResolverAttribute = localeResolverAttribute;
    }
    public void setLocaleTranslationMap(Map<String, Locale> localeTranslationMap) {
        this.localeTranslationMap = localeTranslationMap;
    }

    public Locale resolveLocale(HttpServletRequest request) {
        LocaleResolver resolver = mapLocaleResolver();
        Locale locale = resolver.resolveLocale(request); 
        if (this.localeTranslationMap.containsKey(locale.toString())) {
            locale = this.localeTranslationMap.get(locale.toString());
        }
        return locale;
    }

    public void setLocale(HttpServletRequest request,
                          HttpServletResponse response, Locale locale) {
        LocaleResolver resolver = mapLocaleResolver();
        resolver.setLocale(request, response, locale);
    }

    private LocaleResolver mapLocaleResolver() {
        Service currentService = null;
        if (RequestContext.exists()) {
            currentService = RequestContext.getRequestContext().getService();
        }
        
        while (currentService != null) {
            Object resolver = currentService.getAttribute(this.localeResolverAttribute);
            if (resolver != null && (resolver instanceof LocaleResolver)) {
                return (LocaleResolver) resolver;
            }
            currentService = currentService.getParent();
        }
        return this.defaultLocaleResolver;
    }
}
