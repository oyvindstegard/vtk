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
import org.vortikal.repository.RepositoryException;
import org.vortikal.repository.Resource;
import org.vortikal.security.Principal;
import org.vortikal.security.SecurityContext;
import org.vortikal.web.RequestContext;
import org.vortikal.web.referencedata.ReferenceDataProvider;
import org.vortikal.web.service.Service;

/**
 * Standard resource context model builder. Creates a model map with
 *  "standard" model data for the current resource.
 *
 * <p>Configurable properties:
 * <ul>
 *   <li><code>repository</code> - the {@link Repository content
 *   repository}
 *   <li><code>retrieveForProcessing</code> - boolean indicating
 *   whether to retrieve resources using the
 *   <code>forProcessing</code> flag set to <code>false</code> or
 *   false. The default is <code>true</code>.
 *   <li><code>getResourceFromModel</code> - boolean indicating if the
 *   provider should use the resource from the model provided
 *   (<code>true</code>), or get it from the repository from the URI
 *   specified in the RequestContext. Default is <code>false</code>.
 *   <li><code>resourceFromModelKey</code> (only applicable when
 *   <code>getResourceFromModel</code> is <code>true</code>) - the key
 *   to use when looking up the resource from the model. Default is
 *   <code>resource</code>.
 *   <li><code>modelName</code> - the name to use for the submodel
 *   (default is <code>resourceContext</code>).
 * </ul>
 * 
 * <p>Model data provided:
 * <ul>
 *   <li><code>principal</code> - the current principal
 *   <li><code>currentServiceName</code> - the name of the current
 *   service
 *   <li><code>currentURI</code> - the URI of the requested resource
 *   <li><code>currentResource</code> - the requested resource
 * </ul>
 */
public class ResourceContextProvider implements InitializingBean, ReferenceDataProvider {

    private Repository repository = null;
    private boolean retrieveForProcessing = false;
    private boolean getResourceFromModel = false;
    private String resourceFromModelKey = "resource";
    private String modelName = "resourceContext";
    
    
    public void setRepository(Repository repository) {
        this.repository = repository;
    }


    public void setRetrieveForProcessing(boolean retrieveForProcessing) {
        this.retrieveForProcessing = retrieveForProcessing;
    }
    
    
    public void setGetResourceFromModel(boolean getResourceFromModel) {
        this.getResourceFromModel = getResourceFromModel;
    }
	
	
    public void setResourceFromModelKey(String resourceFromModelKey) {
        this.resourceFromModelKey = resourceFromModelKey;
    }
    

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
    

    public void afterPropertiesSet() throws Exception {
        if (this.repository == null) {
            throw new BeanInitializationException(
                "Bean property 'repository' must be set");
        }
        if (this.modelName == null) {
            throw new BeanInitializationException(
                "Bean property 'modelName' must be set");
        }
        if (this.getResourceFromModel && null == this.resourceFromModelKey) {
            throw new BeanInitializationException(
                "Bean property 'resourceFromModelKey' cannot be null when "
                + "'getResourceFromModel' is set");
        }
    }

    
    public void referenceData(Map model, HttpServletRequest request)
        throws Exception {

        Map resourceContextModel = new HashMap();

        SecurityContext securityContext = SecurityContext.getSecurityContext();
        RequestContext requestContext = RequestContext.getRequestContext();
        Service currentService = requestContext.getService();
        
        Principal principal = securityContext.getPrincipal();

        Resource resource = null;
        
        if (model != null && this.getResourceFromModel) {
            resource = (Resource) model.get(this.resourceFromModelKey);
        }

        if (resource == null) {
            try {
                resource = repository.retrieve(
                    securityContext.getToken(), requestContext.getResourceURI(),
                    this.retrieveForProcessing);
       
            } catch (RepositoryException e) { }
        }
        	   
        resourceContextModel.put("principal", principal);
        resourceContextModel.put("currentResource", resource);
        resourceContextModel.put("currentURI", requestContext.getResourceURI());
        resourceContextModel.put("currentServiceName", currentService.getName());
        
        model.put(this.modelName, resourceContextModel);
    }

    public String toString() {
        StringBuffer sb = new StringBuffer(this.getClass().getName());
        sb.append(" [ ");
        sb.append("modelName = ").append(this.modelName);
        sb.append(" ]");
        return sb.toString();
    }
}
