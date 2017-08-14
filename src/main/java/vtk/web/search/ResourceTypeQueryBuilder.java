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
package vtk.web.search;

import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Resource;
import vtk.repository.ResourceTypeTree;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.Query;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.TypeTermQuery;
import vtk.web.tags.TagsHelper;

public class ResourceTypeQueryBuilder implements SearchComponentQueryBuilder {

    private ResourceTypeTree resourceTypeTree;

    public Optional<Query> build(Resource base, HttpServletRequest request) {
        String[] resourceTypes = request.getParameterValues(TagsHelper.RESOURCE_TYPE_PARAMETER);
        if (resourceTypes != null) {
            if (resourceTypes.length == 1) {
                if (isValidResourceType(resourceTypes[0])) {
                    return Optional.of(new TypeTermQuery(resourceTypes[0], TermOperator.IN));
                }
            }
            OrQuery resourceTypesQuery = new OrQuery();
            for (String resourceType : resourceTypes) {
                if (isValidResourceType(resourceType)) {
                    resourceTypesQuery.add(new TypeTermQuery(resourceType, TermOperator.IN));
                }
            }
            if (resourceTypesQuery.getQueries().size() > 0) {
                return Optional.of(resourceTypesQuery);
            }
        }
        return Optional.empty();
    }

    private boolean isValidResourceType(String resourceTypeName) {
        try {
            this.resourceTypeTree.getResourceTypeDefinitionByName(resourceTypeName);
            return true;
        } catch (IllegalArgumentException iae) {
            // resourceTypeName is bogus, ignore it
        }
        return false;
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

}
