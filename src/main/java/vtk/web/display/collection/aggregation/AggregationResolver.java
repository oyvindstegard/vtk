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
package vtk.web.display.collection.aggregation;

import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Path;
import vtk.repository.PropertySet;

public interface AggregationResolver {

    /**
     * Returns locations from where a given resource is to aggregate from.
     * Contains two separate sets, one for automatic and one for manually
     * approved aggregated resources. The sets may contain references to
     * locations/resources on local or other hosts.
     * 
     * @return Collection of locations to aggregate from for a given resource.
     * 
     */
    public CollectionListingAggregatedResources getAggregatedResources(HttpServletRequest request,
            PropertySet collection);

    /**
     * Returns a list containing all local paths a given resource is set to
     * aggregate from.
     * 
     * @return List of local aggregation-paths for the given resource, or
     *         <code>null</code> if resource not found.
     */
    @Deprecated
    public Set<Path> getAggregationPaths(HttpServletRequest request,
            Path pathToResource);

}
