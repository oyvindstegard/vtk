/* Copyright (c) 2011, University of Oslo, Norway
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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.vortikal.repository.Path;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.security.Principal;
import org.vortikal.web.RequestContext;
import org.vortikal.web.referencedata.ReferenceDataProvider;
import org.vortikal.web.service.Service;
import org.vortikal.web.service.ServiceUnlinkableException;
import org.vortikal.web.service.URL;


/**
 * Generate a URL to Service (if exists) reference data provider.
 * 
 * <p>Configurable JavaBean properties:
 * <ul>
 *  <li><code>modelName</code> - the name to use for the submodel generated
 *  <li><code>service</code> - the service name used to construct the URL
 * </ul>
 */
public class ResourceServiceExistsURLProvider implements ReferenceDataProvider, BeanFactoryAware {

    private String urlName;
    private String serviceName;
    
    public void setUrlName(String urlName) {
        this.urlName = urlName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    private BeanFactory beanFactory;

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request)
        throws Exception {
        
        if (!beanFactory.containsBean(serviceName)) { 
          return;
        }
        
        Service service = (Service) beanFactory.getBean(serviceName);

        RequestContext requestContext = RequestContext.getRequestContext();
        Principal principal = requestContext.getPrincipal();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Resource resource = null;
        Path uri = requestContext.getResourceURI();
        
        try {
            if (uri != null) {
                resource = repository.retrieve(token, uri, true);
            }
        } catch (Throwable t) { }

        URL url = null;
        try {
            if (resource != null) {
                url = service.constructURL(resource, principal, true);
            }
        } catch (ServiceUnlinkableException ex) { }

        model.put(this.urlName, url);
    }
    
    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}
