/* Copyright (c) 2016, University of Oslo, Norway
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
package vtk.web;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.query.PropertyTermQuery;
import vtk.repository.search.query.TermOperator;

public class PreviousLocationsResolver {
    private RequestContext requestContext;
    private PropertyTypeDefinition locationHistoryPropDef;

    public PreviousLocationsResolver(PropertyTypeDefinition locationHistoryPropDef, 
            RequestContext requestContext) {
        this.locationHistoryPropDef = locationHistoryPropDef;
        this.requestContext = requestContext;
    }

    public Set<PropertySet> resolve(Path uri) throws Exception {
        return track(uri, new HashSet<>(), 5);
    }

    private Set<PropertySet> track(Path uri, Set<Path> seen, 
            int recursion) throws Exception {
        Set<PropertySet> result = new HashSet<>();
        if (recursion == 0) return result;
        seen.add(uri);
        if (requestContext.getRepository()
                .exists(requestContext.getSecurityToken(), uri)) {
            PropertySet resource = requestContext.getRepository()
                    .retrieve(requestContext.getSecurityToken(), uri, true);
            result.add(resource);
        }

        for (int i = uri.getDepth(); i > 0; i--) {
            Path left = uri.getPath(i);
            Path right = uri.right(left);

            List<PropertySet> prev = searchPreviousLocations(left);
            for (PropertySet r: prev) {
                Path next = r.getURI().append(right);
                if (!seen.contains(next)) {
                    result.addAll(track(next, seen, recursion - 1));
                }
            }
        }
        return result;
    }

    private List<PropertySet> searchPreviousLocations(Path uri) {

        PropertyTermQuery termQuery = new PropertyTermQuery(
                locationHistoryPropDef, uri.toString(), TermOperator.EQ);
        termQuery.setComplexValueAttributeSpecifier("locations.from_uri");
        Search search = new Search();
        search.setQuery(termQuery);
        search.setSorting(null);
        search.clearAllFilterFlags();
        search.setLimit(10);

        ResultSet results = requestContext.getRepository()
                .search(requestContext.getSecurityToken(), search);
        return results.getAllResults();
    }
}

