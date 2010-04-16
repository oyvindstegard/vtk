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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.vortikal.repository.Path;
import org.vortikal.repository.Repository;
import org.vortikal.repository.RepositoryAction;
import org.vortikal.repository.Resource;
import org.vortikal.security.AuthenticationException;
import org.vortikal.security.Principal;
import org.vortikal.security.SecurityContext;
import org.vortikal.web.RequestContext;
import org.vortikal.web.referencedata.ReferenceDataProvider;
import org.vortikal.web.service.ResourcePrincipalPermissionAssertion;

/**
 * Model builder that checks if the current principal has the permision 'permission' on
 * the current resource, and puts this in the model as a sub-model. 
 * 
 * Configurable properties:
 * <ul>
 *  <li><code>repository</code> - the {@link Repository} is required
 *  <li><code>modelName</code> - name of sub model (default resourcePrincipalPermissions)
 *  <li><code>permission</code> - what permission to check, see the {@link ResourcePrincipalPermissionAssertion}
 *  <li><code>requiresAuthentication</code> - whether authentication is explicitly
 *       required. An {@link AuthenticationException} will be thrown on matching
 *       if there is no principal.
 *  <li><code>anonymous</code> - boolean, true to check anonymous instead of current principal
 * </ul>
 * 
 * Model data provided in the sub-model:
 * <ul>
 *  <li><code>permissionsQueryResult</code> - string ('true' or 'false')
 *  <li><code>requestScheme</code> - string, generally 'http' or 'https')
 * </ul>
 */
public class ResourcePrincipalPermissionsProvider implements ReferenceDataProvider, InitializingBean {

    private static Log logger = LogFactory.getLog(
            ResourcePrincipalPermissionsProvider.class);

    private Repository repository = null;
    private String modelName = "resourcePrincipalPermissions";
    private RepositoryAction permission = null;
    private boolean requiresAuthentication = false;
    private boolean anonymous = false;
        
    public void setRepository(Repository repository) {
        this.repository = repository;
    }
    
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
    
    public void setPermission(RepositoryAction permission) {
        this.permission = permission;
    }

    public void setRequiresAuthentication(boolean requiresAuthentication) {
        this.requiresAuthentication = requiresAuthentication;
    }

    public void setAnonymous(boolean anonymous) {
        this.anonymous = anonymous;
    }
    
    public void afterPropertiesSet() {
        if (this.repository == null) {
            throw new BeanInitializationException(
                "JavaBean property 'repository' must be set");
        }
        if (this.modelName == null) {
            throw new BeanInitializationException(
                "JavaBean property 'modelName' must be set");
        }
    }


    @SuppressWarnings("unchecked")
    public void referenceData(Map model, HttpServletRequest request)
        throws Exception {

        boolean result = false;
        
        Map<String, Object> permissionsModel = new HashMap<String, Object>();

        SecurityContext securityContext = SecurityContext.getSecurityContext();
        RequestContext requestContext = RequestContext.getRequestContext();
        
        String scheme = request.getScheme();
        
        Principal principal = securityContext.getPrincipal();
        Path uri = requestContext.getResourceURI();
        String token = securityContext.getToken();        

        Resource resource = this.repository.retrieve(token, uri, false);
        if (resource == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Resource is null [match = false]");
            }
            result = false;
        } else {
    
            if (this.requiresAuthentication && principal == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Principal is null, authentication required");
                }
                throw new AuthenticationException();
            }
            
            try {
                if (this.anonymous) {
                    result =  this.repository.isAuthorized(resource, this.permission, null);
                } else {
                    result = this.repository.isAuthorized(resource, this.permission, principal);
                }
                
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (result) {
            permissionsModel.put("permissionsQueryResult", "true");
        } else {
            permissionsModel.put("permissionsQueryResult", "false");            
        }
        permissionsModel.put("requestScheme", scheme);            
        
        model.put(modelName, permissionsModel);
    }

}
