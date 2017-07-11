/* Copyright (c) 2006, University of Oslo, Norway
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
package vtk.repository.resourcetype;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.TypeInfo;
import vtk.security.Principal;

/**
 * Assertion for matching on whether the current resource has a given resource
 * type.
 * 
 * <p>
 * Configurable JavaBean properties:
 * <ul>
 * <li><code>resourceTypeDefinition</code> - the {@link ResourceTypeDefinition
 * resource type} to match
 * <li><code>invert</code> - whether to invert the assertion
 * <li><code>exactMatch</code> - set to true for equals instead of ofType
 * asserting
 * </ul>
 */
public class ResourceTypeAssertion implements RepositoryAssertion {
    private Repository repository;
    private ResourceTypeDefinition resourceTypeDefinition;
    private boolean invert = false;
    private boolean exactMatch = false;
    private String resourceType;

    public void setResourceTypeDefinition(ResourceTypeDefinition resourceTypeDefinition) {
        this.resourceTypeDefinition = resourceTypeDefinition;
    }

    public void setInvert(boolean invert) {
        this.invert = invert;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }
    
    public void setExactMatch(boolean exactMatch) {
        this.exactMatch = exactMatch;
    }

    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    @Override
    public boolean matches(Optional<Resource> resource,
            Optional<Principal> principal) {

        if (resource.isPresent()) {
            return matches(resource.get());
        }
        return invert;
    }

    private boolean matches(Resource resource) {
        TypeInfo typeInfo = repository.getTypeInfo(resource);
        boolean match = false;

        if (exactMatch) {
            if (resourceTypeDefinition != null) {
                match = (typeInfo.getResourceType().equals(resourceTypeDefinition));
            }
            else {
                typeInfo.getResourceType().getName().equals(resourceType);
            }
        }
        else {
            if (resourceTypeDefinition != null) {
                match = typeInfo.isOfType(resourceTypeDefinition);
            }
            else {
                match = typeInfo.isOfType(resourceType);
            }
        }
        if (invert) {
            return !match;
        }
        return match;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("resourcetype ");
        if (invert) sb.append("not ");
        sb.append(exactMatch ? "is " : "in ");
        sb.append(resourceTypeDefinition != null ? resourceTypeDefinition.getName() : resourceType);
        return sb.toString();
    }
}
