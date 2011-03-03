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
package org.vortikal.web.referencedata.provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.security.Principal;
import org.vortikal.web.RequestContext;
import org.vortikal.web.referencedata.ReferenceDataProvider;
import org.vortikal.web.service.Service;
import org.vortikal.web.service.ServiceUnlinkableException;

/**
 * Reference data that has a list of locales, providing URLs to switch
 * locale to each one (if they are not already the current
 * locale). The URLs are made available in the submodel of a
 * configurable name (<code>locales</code> by default).
 * 
 * Configurable properties:
 * <ul>
 *   <li><code>modelName</code> - the name to use for the submodel
 *       generated (default <code>locales</code>)
 *   <li><code>locales</code> - a map containing keys that are
 *       interpreted as locale names. The values are required to be
 *       <code>org.vortikal.web.service.Service</code> objects, used
 *       for constructing URLs for changing the locale.
 * </ul>
 * 
 * Model data provided:
 * <ul>
 *   <li><code>localeServiceURLs</code> -
 *       (<code>java.util.Map</code>): for each locale, a URL to the
 *       service that changes the locale to that of the key
 *   <li><code>localeServiceNames</code> - the name of the keys in the
 *   URL map
 *   <li><code>currentLocale</code> - the name of the current locale
 * </ul>
 * 
 */
public class SwitchLocaleProvider implements ReferenceDataProvider {
    private String modelName = "locales";
    private Map<String, Service> locales;

    @Required public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    @Required public void setLocales(Map<String, Service> locales) {
        this.locales = locales;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void referenceData(Map model, HttpServletRequest request)
        throws Exception {
        Map<String, Object> localeMap = new HashMap<String, Object>();

        RequestContext requestContext = RequestContext.getRequestContext();
        Principal principal = requestContext.getPrincipal();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Resource resource = repository.retrieve(
            token, requestContext.getResourceURI(), false);

        org.springframework.web.servlet.support.RequestContext springContext =
            new org.springframework.web.servlet.support.RequestContext(request);
        String currentLocale = springContext.getLocale().toString();

        Map<String, String> localeServiceURLs = new HashMap<String, String>();
        List<String> localeServiceNames = new ArrayList<String>();
        for (String key: this.locales.keySet()) {
            Service service = this.locales.get(key);
            if (!currentLocale.equals(key)) {
                try {
                    localeServiceNames.add(key);
                    String url = service.constructLink(resource, principal);
                    localeServiceURLs.put(key, url);
                } catch (ServiceUnlinkableException e) { }
            }
        }
        
        localeMap.put("currentLocale", currentLocale);
        localeMap.put("localeServiceNames", localeServiceNames);
        localeMap.put("localeServiceURLs", localeServiceURLs);
        model.put(this.modelName, localeMap);
    }

}
