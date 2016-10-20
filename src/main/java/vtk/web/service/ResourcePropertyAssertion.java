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

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.RequestContext.RepositoryTraversal;
import vtk.web.RequestContext.TraversalCallback;

/**
 * Assertion for matching on whether the current resource has a
 * property with a given name, namespace and value.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>namespace</code> - the {@link PropertyTypeDefinition#getNamespace() 
 *   namespace} of the property to match
 *   <li><code>name</code> - the {@link PropertyTypeDefinition#getName()  name} of
 *   the property to match
 *   <li><code>value</code> - the {@link Property#getValue()  value}
 *   of the property to match
 *   <li><code>checkExistenceOnly</code> - whether to only check if
 *   the property exists on the resource.
 * </ul>
 */
public class ResourcePropertyAssertion extends AbstractRepositoryAssertion {

    private Namespace namespace;
    private String name;
    private String value;
    private boolean checkExistenceOnly = false;
    private boolean invert = false;
    private boolean checkInherited = false; // Can delete ?
    private String token = null;
    
    public void setName(String name) {
        this.name = name;
    }
    
    public void setNamespace(Namespace namespace) {
        this.namespace = namespace;
    }
    
    public String getName() {
        return this.name;
    }

    public Namespace getNamespace() {
        return this.namespace;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setCheckExistenceOnly(boolean checkExistenceOnly) {
        this.checkExistenceOnly = checkExistenceOnly;
    }
    
    public void setCheckInherited(boolean checkInherited) {
        this.checkInherited = checkInherited;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    @Override
    public boolean conflicts(Assertion assertion) {
        if (assertion instanceof ResourcePropertyAssertion) {
            ResourcePropertyAssertion other = (ResourcePropertyAssertion) assertion;
			
            if (this.namespace.equals(other.getNamespace()) && 
                this.name.equals(other.getName())) {
				
                boolean same = false;

                if (this.checkExistenceOnly) {
                    same = this.checkExistenceOnly == other.checkExistenceOnly;
                } else {
                    same = (this.value == null && other.getValue() == null)
                        || (this.value != null && this.value.equals(other.getValue()));
                    
                }
                if (!this.invert && !other.invert)
                    return  !same;
                else if (this.invert != other.invert)
                    return same;
            }
        }
        return false;
    }

    public void setInvert(boolean invert) {
        this.invert = invert;
    }

    @Override
    public boolean matches(Resource resource, Principal principal) {
        try {
            if (resource == null) {
                return this.invert;
            }
            if (this.checkInherited) {
                return matchInherited(resource);
            }
            return matchResource(resource);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("property.").append(this.name);
        if (this.checkExistenceOnly) {
            sb.append(" exists");
        } else {
            sb.append(" = ").append(this.value);
        }
        return sb.toString();
    }

    private boolean matchResource(Resource resource) {
        Property property = resource.getProperty(this.namespace, this.name);
        if (this.checkExistenceOnly) {
            if (property != null) return !this.invert;
        } else {
            if (property != null && this.value.equals(property.getStringValue())) return !this.invert;
        }
        return this.invert;
    }
    
    private boolean matchInherited(Resource resource) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        final String token = this.token != null ? this.token : requestContext.getSecurityToken();
        
        RepositoryTraversal traversal = requestContext.rootTraversal(token, resource.getURI());
        Callback callback = new Callback(this.name);
        traversal.traverse(callback);
        if (callback.result == null) {
            return this.invert;
        }
        return matchResource(callback.result);
    }
    
    private static class Callback implements TraversalCallback {
        public Resource result;
        private String propName;
        public Callback(String propName) {
            this.propName = propName;
        }
        @Override
        public boolean callback(Resource r) {
            for (Property p: r) {
                if (p.getDefinition().getName().equals(propName)) {
                    this.result = r;
                    return false;
                }
            }
            return true;
        }
        @Override
        public boolean error(Path uri, Throwable error) {
            return false;
        }
    }

}
