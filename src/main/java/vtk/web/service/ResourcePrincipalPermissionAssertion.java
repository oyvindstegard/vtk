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
package vtk.web.service;


import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.Repository;
import vtk.repository.RepositoryAction;
import vtk.repository.Resource;
import vtk.security.AuthenticationException;
import vtk.security.Principal;
import vtk.security.PrincipalManager;
import vtk.security.roles.RoleManager;


/**
 * <p>Assert that the current principal has the permission 'permission'
 * on the current resource.
 *
 * <p>Principals having the {@link RoleManager.Role#ROOT root role} are
 * treated especially. In general, these principals will produce a
 * match, unless the permission in question is one of <code>(write,
 * write-acl)</code> and the resource is locked by another principal.
 *
 * <p>Configurable properties:
 * <ul>
 *   <li><code>permission</code> - what permission to check, one of
 *     <ul>
 *       <li><code>read</code>
 *       <li><code>read-processed</code> (includes <code>read</code>)
 *       <li><code>write</code>
 *       <li><code>write-acl</code>
 *       <li><code>parent-write</code> - write permission on the resource's parent
 *       <li><code>delete</code> - delete permission on the resource
 *           (same as <code>parent-write</code> with the additional
 *           condition that the resource is not locked by another
 *           principal
 *       <li><code>unlock</code> - will be true only if a resource is locked and
 *           lock owner is the current principal
 *       <li><code>lock</code> - <code>true</code> if the resource is not locked and the current
 *           principal has write permission
 *       <li><code>delegate-ownership</code> - <code>true</code> if
 *       the current principal is the  owner of the resource
 *     </ul>
 *   <li><code>requiresAuthentication</code> - whether authentication is explicitly
 *       required. An {@link AuthenticationException} will be thrown on matching
 *       if there is no principal.
 *   <li><code>principalManager</code> (required) - a {@link PrincipalManager}
 *   <li><code>roleManager</code> (required) - a {@link RoleManager}
 *   <li><code>repository</code> (required) - a {@link Repository}
 *   <li><code>trustedToken</code> (required) - a string used as token
 *   for resource retrieval from the repository
 * </ul>
 */
public class ResourcePrincipalPermissionAssertion extends AbstractAssertion 
    implements InitializingBean {

    private static Logger logger = LoggerFactory.getLogger(
            ResourcePrincipalPermissionAssertion.class);
    
    private RepositoryAction permission = null;
    private RepositoryAction unpublishedPermission = null;
    private boolean requiresAuthentication = false;
    private PrincipalManager principalManager = null;
    private RoleManager roleManager = null;
    private Repository repository = null;
    private String trustedToken = null;
    private boolean anonymous = false;
    private boolean considerLocks = true;
    private boolean parent = false;
    
    Set<String> rootPrincipals;
    Set<String> readPrincipals;
    
    public void setRequiresAuthentication(boolean requiresAuthentication) {
        this.requiresAuthentication = requiresAuthentication;
    }

    public void setPermission(RepositoryAction permission) {
        if (this.permission != null) {
            throw new IllegalStateException("Cannot set privilege: permission already set");
        }
        this.permission = permission;
    }

    public void setPrivilege(Privilege privilege) {
        if (this.permission != null) {
            throw new IllegalStateException("Cannot set privilege: permission already set");
        }
        this.permission = privilege.getAction();
    }

    public void setUnpublishedPermission(RepositoryAction permission) {
        if (this.unpublishedPermission != null) {
            throw new IllegalStateException("Cannot set privilege: unpublishedPermission already set");
        }
        this.unpublishedPermission = permission;
    }

    public void setUnpublishedPrivilege(Privilege privilege) {
        if (this.unpublishedPermission != null) {
            throw new IllegalStateException("Cannot set privilege: unpublishedPermission already set");
        }
        this.unpublishedPermission = privilege.getAction();
    }
    
    public void setPrincipalManager(PrincipalManager principalManager) {
        this.principalManager = principalManager;
    }
    
    public void setRoleManager(RoleManager roleManager) {
        this.roleManager = roleManager;
    }
    
    public void setRepository(Repository repository) {
        this.repository = repository;
    }
    
    public void setTrustedToken(String trustedToken) {
        this.trustedToken = trustedToken;
    }
    
    public void setAnonymous(boolean anonymous) {
        this.anonymous = anonymous;
    }
    
    public void setConsiderLocks(boolean considerLocks) {
        this.considerLocks = considerLocks;
    }

    public void setParent(boolean parent) {
        this.parent = parent;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.principalManager == null) {
            throw new BeanInitializationException(
                "Property 'principalManager' must be set");
        }
        if (this.roleManager == null) {
            throw new BeanInitializationException(
                "Property 'roleManager' must be set");
        }
        if (this.repository == null) {
            throw new BeanInitializationException(
                "Property 'repository' must be set");
        }
        if (this.trustedToken == null) {
            throw new BeanInitializationException(
                "Property 'trustedToken' must be set");
        }
        
        
        this.rootPrincipals = this.roleManager.getPrincipals(RoleManager.Role.ROOT);
        this.readPrincipals = this.roleManager.getPrincipals(RoleManager.Role.READ_EVERYTHING);
    }


    @Override
    public boolean conflicts(WebAssertion assertion) {
        return false;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(
                "principal.permissionsOn(currentResource).includes(" + this.permission + ")");
        return sb.toString();
    }

    @Override
    public Optional<URL> processURL(URL url, Resource resource,
            Principal principal) {
        if (!matches(resource, principal)) {
            return Optional.empty();
        }
        return Optional.of(url);
    }

    @Override
    public URL processURL(URL url) {
        return url;
    }

    @Override
    public boolean matches(HttpServletRequest request, Resource resource,
            Principal principal) {
        return matches(resource, principal);
    }

    private boolean matches(Resource resource, Principal principal) {
        if (resource == null) {
            return false;
        }

        if (this.requiresAuthentication && principal == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Principal is null, authentication required");
            }
            throw new AuthenticationException();
        }
        
        RepositoryAction action = this.permission;
        if (this.unpublishedPermission != null && !resource.hasPublishDate()) {
            action = this.unpublishedPermission;
        }
        
        try {
            if (this.parent) {
                Path parent = resource.getURI().getParent();
                Resource resourceParent = this.repository.retrieve(this.trustedToken, parent, false);
                if (resourceParent == null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Resource parent is null [match = false]");
                    }
                    return false;
                }
                if (this.anonymous) {
                    return this.repository.isAuthorized(resourceParent, action, null, this.considerLocks);
                }
                return this.repository.isAuthorized(resourceParent, action, principal, this.considerLocks);
            }
            else {
                if (this.anonymous) {
                    return this.repository.isAuthorized(resource, action, null, this.considerLocks);
                }
                return this.repository.isAuthorized(resource, action, principal, this.considerLocks);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    
}
