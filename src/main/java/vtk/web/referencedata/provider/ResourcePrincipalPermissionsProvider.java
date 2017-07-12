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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.RepositoryAction;
import vtk.repository.Resource;
import vtk.security.AuthenticationException;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.ResourcePrincipalPermissionAssertion;

/**
 * Model builder that checks if the current principal has the permision 'permission' on
 * the current resource, and puts this in the model as a sub-model. 
 * 
 * Configurable properties:
 * <ul>
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
 *  <li><code>requestPort</code> - port number
 * </ul>
 */
public class ResourcePrincipalPermissionsProvider implements ReferenceDataProvider, InitializingBean {

    private static Logger logger = LoggerFactory.getLogger(
            ResourcePrincipalPermissionsProvider.class);

    private String modelName = "resourcePrincipalPermissions";
    private RepositoryAction permission = null;
    private boolean requiresAuthentication = false;
    private boolean anonymous = false;
    private boolean considerLocks = true;
        
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
    
    public void setConsiderLocks(boolean considerLocks) {
        this.considerLocks = considerLocks;
    }
    
    public void afterPropertiesSet() {
        if (this.modelName == null) {
            throw new BeanInitializationException(
                "JavaBean property 'modelName' must be set");
        }
    }


    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {
        boolean result = false;
        Map<String, Object> permissionsModel = new HashMap<>();
        RequestContext requestContext = RequestContext.getRequestContext(request);
        String scheme = request.getScheme();
        Integer port = request.getServerPort();
        
        Principal principal = requestContext.getPrincipal();
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();

        try {
        Resource resource = repository.retrieve(token, uri, false);
        if (resource == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Resource is null [match = false]");
            }
            result = false;
        }
        else {
    
            if (this.requiresAuthentication && principal == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Principal is null, authentication required");
                }
                throw new AuthenticationException();
            }
            try {
                if (this.anonymous) {
                    result = repository.isAuthorized(resource, this.permission, 
                            null, this.considerLocks);
                } else {
                    result = repository.isAuthorized(resource, this.permission, 
                            principal, this.considerLocks);
                }
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (result) {
            permissionsModel.put("permissionsQueryResult", "true");
        }
        else {
            permissionsModel.put("permissionsQueryResult", "false");            
        }
        permissionsModel.put("requestScheme", scheme);            
        permissionsModel.put("requestPort", port);            
        model.put(modelName, permissionsModel);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
