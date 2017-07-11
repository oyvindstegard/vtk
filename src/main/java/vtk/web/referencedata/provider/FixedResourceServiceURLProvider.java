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
package vtk.web.referencedata.provider;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

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
 * URL (link) reference data provider. Puts a URL to a fixed resource
 * resource that is constructed using a configured service in a
 * submodel.
 * 
 * <p>Configurable JavaBean properties:
 * <ul>
 *  <li><code>modelName</code> - the name to use for the submodel generated
 *  <li><code>service</code> - the {@link Service} used to construct the URL
 *  <li><code>uri</code> - the URI (path) of the resource in the repository
 *  <li><code>appendPath</code> - a path (starting with <code>/</code>
 *  to append to the URL after construction. This will only work if
 *  the resource is a collection. Default is <code>null</code> (no
 *  path appended).
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
public class FixedResourceServiceURLProvider
  implements ReferenceDataProvider, InitializingBean {

    private String modelName;
    private Service service;
    private boolean matchAssertions;
    private Path uri;
    private String appendPath;
    private String urlName = "url";

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
    
    public void setService(Service service) {
        this.service = service;
    }
    
    public void setUri(String uri) {
        this.uri = Path.fromString(uri);
    }

    public void setAppendPath(String appendPath) {
        this.appendPath = appendPath;
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
        if (this.uri == null) {
            throw new BeanInitializationException(
                "Bean property 'uri' must be set");
        }
        if (this.appendPath != null && !this.appendPath.startsWith("/")) {
            throw new BeanInitializationException(
                "Bean property 'appendPath' must start with a '/' character");
        }
    }
    

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Principal principal = requestContext.getPrincipal();
        Repository repository = requestContext.getRepository();

        Resource resource = null;
        try {
            resource = repository.retrieve(requestContext.getSecurityToken(), this.uri, true);
        } catch (Throwable t) { }

        @SuppressWarnings("unchecked")
        Map<String, String> urlMap = (Map<String, String>) model.get(this.modelName);
        if (urlMap == null) {
            urlMap = new HashMap<>();
        }

        String url = null;
        try {
            if (resource != null) {
                url = service.urlConstructor(URL.create(request))
                        .withURI(resource.getURI())
                        .constructURL()
                        .toString();
            }
        } catch (ServiceUnlinkableException ex) { }

        if (this.appendPath != null && url != null && resource.isCollection()) {
            url = appendPath(url);
        }

        urlMap.put(this.urlName, url);
        model.put(this.modelName, urlMap);
    }

    private String appendPath(String url) {
        String queryString = "";
        int queryStringIndex = url.indexOf("?");
        if (queryStringIndex > 0) {
            queryString = url.substring(queryStringIndex);
            url = url.substring(0, queryStringIndex);
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        url = url + this.appendPath + queryString;
        return url;
    }
    

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer(this.getClass().getName());
        sb.append(" [ ");
        sb.append("modelName = ").append(this.modelName);
        sb.append(", service = ").append(this.service.getName());
        sb.append(", uri = ").append(this.uri);
        sb.append(" ]");
        return sb.toString();
    }
}
