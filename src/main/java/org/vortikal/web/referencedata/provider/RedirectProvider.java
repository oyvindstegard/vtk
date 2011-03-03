/* Copyright (c) 2004, 2007, University of Oslo, Norway
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

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.security.Principal;
import org.vortikal.web.RequestContext;
import org.vortikal.web.referencedata.ReferenceDataProvider;
import org.vortikal.web.service.Service;
import org.vortikal.web.service.URL;

/**
 * Provides a redirect URL to the current resource for a given
 * service. The model is first searched for an object of type {@link
 * Resource} and key <code>resource</code>. If no such object exists
 * in the model, the resource is retrieved from the repository.
 *
 * <p>Configurable properties:
 * <ul>
 *   <li><code>redirectToService</code> - the {@link Service service}
 *   for which to construct the redirect URL
 *   <li><code>urlAnchor</code> - anchor to append to redirect URL (optional)
 * </ul>
 *
 * <p>Model data provided:
 * <ul>
 *   <li><code>redirectURL</code> the redirect URL
 * </ul>
 */
public class RedirectProvider implements InitializingBean, ReferenceDataProvider {

    private Service redirectToService;
    private String urlAnchor;
    

    public void setRedirectToService(Service redirectToService) {
        this.redirectToService = redirectToService;
    }

    public void setUrlAnchor(String urlAnchor) {
        this.urlAnchor = urlAnchor;
    }


    public void afterPropertiesSet() throws Exception {
        if (this.redirectToService == null) {
            throw new BeanInitializationException(
                "Bean property 'redirectToService' must be set");
        }
    }


    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void referenceData(Map model, HttpServletRequest request)
        throws Exception {
        
        RequestContext requestContext = RequestContext.getRequestContext();
        Principal principal = requestContext.getPrincipal();

        Resource resource = null;
        if (model != null) {
            resource = (Resource) model.get("resource");
        }

        if (resource == null) {
            Repository repository = requestContext.getRepository();
            resource = repository.retrieve(
                requestContext.getSecurityToken(), requestContext.getResourceURI(), false);
        }
        
        URL redirectURL = this.redirectToService.constructURL(resource, principal);
        if (this.urlAnchor != null) {
            redirectURL.setRef(this.urlAnchor);
        }
        model.put("redirectURL", redirectURL.toString());
    }

}
