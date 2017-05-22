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
package vtk.web.referencedata.provider;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.NoSuchMessageException;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Service;
import vtk.web.service.ServiceUnlinkableException;
import vtk.web.service.URL;


/**
 * URL (link) reference data provider. Puts a URL to the requested
 * resource that is constructed using a configured service in a
 * submodel.
 * 
 * <p>Configurable JavaBean properties:
 * <ul>
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
 *   <li><code>title</code> - a localized string that can be used as
 *   the URL title. It is looked up based on the current resource's
 *   type as follows:
 *   <code>url.&lt;serviceName&gt;.&lt;resourceType&gt;</code> (e.g.
 *   <code>url.manageService.collection = Manage this folder</code>).
 * </ul>
 * 
 */
public class ResourceServiceURLProvider implements ReferenceDataProvider {

    private String modelName = null;
    private Service service = null;
    private boolean matchAssertions = false;
    private boolean linkToParent = false;
    private String urlName = "url";
    private String titleName = "title";
    
    // URL "hints":
    private String staticURLHost;
    private String staticURLProtocol;
    private Map<String, String> staticURLParameters;

    @Required public void setModelName(String modelName) {
        this.modelName = modelName;
    }
    
    @Required public void setService(Service service) {
        this.service = service;
    }
    
    public void setLinkToParent(boolean linkToParent) {
        this.linkToParent = linkToParent;
    }
    
    public void setMatchAssertions(boolean matchAssertions) {
        this.matchAssertions = matchAssertions;
    }

    public void setUrlName (String urlName) {
        this.urlName = urlName;
    }
    
    public void setTitleName (String titleName) {
        this.titleName = titleName;
    }

    public void setStaticURLHost(String staticURLHost) {
        this.staticURLHost = staticURLHost;
    }

    public void setStaticURLProtocol(String staticURLProtocol) {
        if ("*".equals(staticURLProtocol)) {
            staticURLProtocol = null;
        }
        this.staticURLProtocol = staticURLProtocol;
    }

    public void setStaticURLParameters(Map<String, String> staticURLParameters) {
        this.staticURLParameters = staticURLParameters;
    }

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {

        RequestContext requestContext = RequestContext.getRequestContext();
        Principal principal = requestContext.getPrincipal();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Resource resource = null;
        Path uri = requestContext.getResourceURI();

        if (this.linkToParent) {
            uri = uri.getParent();
        }

        try {
            if (uri != null) { // Will be null for root resource if this.linkToParent is true
                resource = repository.retrieve(token, uri, true);
            }
        } catch (Throwable t) { }

        @SuppressWarnings("unchecked")
        Map<String, Object> urlMap = (Map<String, Object>) model.get(this.modelName);
        if (urlMap == null) {
            urlMap = new HashMap<>();
        }

        URL url = null;
        try {
            if (resource != null) {
                url = this.service.constructURL(resource, principal,
                                                 this.matchAssertions);
            }
        } catch (ServiceUnlinkableException ex) { }
        
        urlMap.put(this.urlName, url);
        if (url != null) {
            if (this.staticURLHost != null) {
                url.setHost(this.staticURLHost);
            }
            if (this.staticURLProtocol != null) {
                url.setProtocol(this.staticURLProtocol);
            }
            if (this.staticURLParameters != null) {
                for (String param : this.staticURLParameters.keySet()) {
                    url.addParameter(param, this.staticURLParameters.get(param));
                }
            }
            urlMap.put(this.titleName, getLocalizedTitle(request, resource, this.service));
        }
        model.put(this.modelName, urlMap);
    }

    private String getLocalizedTitle(HttpServletRequest request, Resource resource, Service service) {
        String localizationKey = "url." + service.getName() + "." + resource.getResourceType();
        org.springframework.web.servlet.support.RequestContext springContext =
            new org.springframework.web.servlet.support.RequestContext(request);
            
        try {
            return springContext.getMessage(localizationKey);
        } catch (NoSuchMessageException e) {
            localizationKey = "url." + service.getName();
            return springContext.getMessage(localizationKey, service.getName());
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append(" [ ");
        sb.append("modelName = ").append(this.modelName);
        sb.append(", service = ").append(this.service.getName());
        sb.append(" ]");
        return sb.toString();
    }
}
