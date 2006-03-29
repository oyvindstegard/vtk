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

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.security.Principal;
import org.vortikal.security.SecurityContext;
import org.vortikal.util.repository.URIUtil;
import org.vortikal.web.RequestContext;
import org.vortikal.web.referencedata.ReferenceDataProvider;
import org.vortikal.web.service.Service;
import org.vortikal.web.service.ServiceUnlinkableException;


/**
 * URL (link) reference data provider. Puts a URL to the requested
 * resource that is constructed using a configured service in a
 * submodel.
 * 
 * <p>Configurable JavaBean properties:
 * <ul>
 *  <li><code>repository</code> - the content {@link Repository repository}
 *  <li><code>modelName</code> - the name to use for the submodel generated
 *  <li><code>service</code> - the {@link Service} used to construct the URL
 *  <li><code>linkToParent</code> - if this property is set to
 *  <code>true</code>, the parent resource is used for URL
 *  construction instead of the requested resource. The default is
 *  <code>false</code>.
 *  <li><code>matchAssertions</code> - whether to require that all
 *  assertions must match when constructing links (default is
 *  <code>false</code>)
 *  <li><code>urlName</code> - the name to use for the url in the submodel
 * </ul>
 * 
 * <p>Model data provided:
 * <ul>
 *   <li><code>url</code> - the URL of the resource. If it could not
 *   be constructed for some reason, <code>null</code> is supplied
 *   as the value.
 * </ul>
 * 
 */
public class ResourceServiceURLProvider
  implements ReferenceDataProvider, InitializingBean {

    private String modelName = null;
    private Service service = null;
    private Repository repository = null;
    private boolean matchAssertions = false;
    private boolean linkToParent = false;
    private boolean linkToGrandparent = false;
    private String urlName = "url";

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
    
    public void setService(Service service) {
        this.service = service;
    }
    
    public void setRepository(Repository repository) {
        this.repository = repository;
    }
    
    public void setLinkToParent(boolean linkToParent) {
        this.linkToParent = linkToParent;
    }
    
    public void setLinkToGrandparent(boolean linkToGrandparent) {
        this.linkToGrandparent = linkToGrandparent;
    }

    public void setMatchAssertions(boolean matchAssertions) {
        this.matchAssertions = matchAssertions;
    }

    public void setUrlName (String urlName) {
        this.urlName = urlName;
    }
    
    public void afterPropertiesSet() {
        if (this.modelName == null) {
            throw new BeanInitializationException(
                "Bean property 'modelName' must be set");
        }
        if (this.service == null) {
            throw new BeanInitializationException(
                "Bean property 'service' must be set");
        }
        if (this.repository == null) {
            throw new BeanInitializationException(
                "Bean property 'repository' must be set");
        }
    }
    


    public void referenceData(Map model, HttpServletRequest request)
        throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext();
        SecurityContext securityContext = SecurityContext.getSecurityContext();
        
        Principal principal = securityContext.getPrincipal();

        Resource resource = null;
        String uri = requestContext.getResourceURI();
        
        if (this.linkToParent) {
            uri = URIUtil.getParentURI(uri);
        }
        if (this.linkToGrandparent) {
            uri = URIUtil.getGrandparentURI(uri);
        }

        try {
            if (uri != null) {
                resource = repository.retrieve(
                    securityContext.getToken(), uri, true);
            }
        } catch (Throwable t) { }

        Map urlMap = (Map) model.get(this.modelName);
        if (urlMap == null) {
            urlMap = new HashMap();
        }

        String url = null;
        try {
            if (resource != null) {
                url = this.service.constructLink(resource, principal,
                                                 this.matchAssertions);
            }
        } catch (ServiceUnlinkableException ex) { }

        urlMap.put(this.urlName, url);
        model.put(this.modelName, urlMap);
    }

    public String toString() {
        StringBuffer sb = new StringBuffer(this.getClass().getName());
        sb.append(" [ ");
        sb.append("modelName = ").append(this.modelName);
        sb.append(", service = ").append(this.service.getName());
        sb.append(" ]");
        return sb.toString();
    }
}
