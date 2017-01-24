/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.resourcemanagement;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.RepositoryContentEvaluationAssertion;
import vtk.repository.Resource;
import vtk.repository.resourcetype.Content;
import vtk.repository.resourcetype.property.PropertyEvaluationException;
import vtk.security.Principal;
import vtk.util.text.Json;

/**
 * XXX Not usable as web service assertion.
 *
 */
public class ValidDocumentAssertion implements RepositoryContentEvaluationAssertion {

    private StructuredResourceManager resourceManager;
	
    @Override
    public boolean matches(Resource resource, Principal principal) {
        return matches(resource, principal, null);
    }

    @Override
    public boolean matches(Resource resource, Principal principal, Content content) {
        // Could fallback to Resource.getInputStream instead,but that will not work in all cases when
        // this assertion is called from evaluation framework.
        if (content == null) return false;
        if (resource.isCollection()) return false;
        
        try {
            Json.MapContainer object = content.getContentRepresentation(Json.MapContainer.class);
            Object o = Json.select(object, "resourcetype");
            if (o == null) {
                return false;
            }
            StructuredResourceDescription description = resourceManager.get(o.toString());
            if (description == null) {
                return false;
            }
            try {
                description.buildFromMap(object);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
        catch (Exception e) {
            // XXX: return false and log warning instead ? We probably should allow invalid JSON
            //      to be uploaded to repository (just evaluate type to '/file/text').
            throw new PropertyEvaluationException(
                    "Unable to get JSON representation of content: " + resource, e);
        }
    }

    @Required
    public void setResourceManager(StructuredResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }
    
    @Override
    public String toString() {
        return "resource.content = <Valid JSON>";
    }
}
