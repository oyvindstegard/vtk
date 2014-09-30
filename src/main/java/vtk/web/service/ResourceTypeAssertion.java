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
package vtk.web.service;

import org.springframework.beans.factory.annotation.Required;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.TypeInfo;
import vtk.repository.resourcetype.ResourceTypeDefinition;
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
public class ResourceTypeAssertion extends AbstractRepositoryAssertion {

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
    
    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    @Override
    public boolean conflicts(Assertion assertion) {
        // XXX: implement
        return false;
    }

    @Override
    public boolean matches(Resource resource, Principal principal) {

        if (resource == null) {
            return this.invert;
        }

        try {

            TypeInfo typeInfo = this.repository.getTypeInfo(resource);
            boolean match = false;

            if (this.exactMatch) {
                if (this.resourceTypeDefinition != null) {
                    match = (typeInfo.getResourceType().equals(this.resourceTypeDefinition));
                } else {
                    typeInfo.getResourceType().getName().equals(this.resourceType);
                }
            } else {
                if (this.resourceTypeDefinition != null) {
                    match = typeInfo.isOfType(this.resourceTypeDefinition);
                } else {
                    match = typeInfo.isOfType(this.resourceType);
                }
            }

            if (this.invert)
                return !match;

            return match;

        } catch (RuntimeException e) {
            // XXX Hmm. Don't wrap runtime-exceptions, because we then hide
            //     the real exception type information, which is needed
            //     in higher level error handling.
            //     For instance, handling of AuthenticationException in VTKServlet.
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("resourcetype ");
        if (this.invert) sb.append("not ");
        sb.append(this.exactMatch ? "is " : "in ");
        sb.append(this.resourceTypeDefinition != null ? this.resourceTypeDefinition.getName() : this.resourceType);
        return sb.toString();
    }

    public void setExactMatch(boolean exactMatch) {
        this.exactMatch = exactMatch;
    }

}
