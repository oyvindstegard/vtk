/* Copyright (c) 2012, University of Oslo, Norway
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
 *      * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
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
package vtk.web.search.messagelisting;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.MultiHostSearcher;
import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.search.ConfigurablePropertySelect;
import vtk.repository.search.ResultSet;
import vtk.repository.search.ResultSetImpl;
import vtk.repository.search.Sorting;
import vtk.web.RequestContext;
import vtk.web.search.collectionlisting.CollectionListingSearchComponent;

/**
 * A "hack" implementation of the default collection listing search component
 * for message listing. After initial search has been run, do explicit lookup in
 * local repository for current changes that might yet not be visible in index
 * due to write delay. Lookup is performed depending on a given action (request
 * parameter).
 * 
 * XXX: Move to Vortex
 */
public class MessageListingLocalRepositoryLookupSearchComponent extends CollectionListingSearchComponent {

    private final static String URI_PARAM = "uri";
    private final static String ACTION_PARAM = "action";

    @Override
    protected ResultSet getResultSet(HttpServletRequest request, Resource collection, String token, Sorting sorting,
            int searchLimit, int offset, ConfigurablePropertySelect propertySelect) {

        ResultSet result = super.getResultSet(request, collection, token, sorting, searchLimit, offset, propertySelect);

        String actionParameter = request.getParameter(ACTION_PARAM);
        if (actionParameter == null) {
            // No specific action was requested, return
            return result;
        }

        String uriParameter = request.getParameter(URI_PARAM);
        Path resourcePath = null;
        if (uriParameter != null) {
            try {
                resourcePath = Path.fromString(uriParameter);
            } catch (IllegalArgumentException iae) {
                // Ignore, invalid path reference
            }
        }

        if (resourcePath == null) {
            // No altered resource path, return
            return result;
        }

        // Check for action performed and manipulate result set accordingly

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        Resource resource = null;
        try {
            resource = repository.retrieve(token, resourcePath, true);
        } catch (Exception e) {
            // Ignore, resource is unavailable
        }

        // A resource has been either altered or newly added. Manipulate result
        // set due to delayed update of index -> specifically update/add
        // altered/new resource to result set.
        if (resource != null) {
            if ("new".equals(actionParameter)) {
                this.addToResultSet(result, resource);
            } else if ("update".equals(actionParameter)) {
                this.updateResultSet(result, resource);
            }
        }

        // Check for deleted resources
        this.removeDeleted(result, repository, token);

        return result;
    }

    // Add a newly created message to the result set. Check for existence first,
    // In case of multiple refresh with same param values.
    private void addToResultSet(ResultSet result, Resource resource) {
        List<PropertySet> resources = result.getAllResults();
        int resourceIndex = this.getResourceIndex(result, resource);
        if (resourceIndex == -1) {
            resources.add(0, resource);
            ((ResultSetImpl) result).setTotalHits(result.getTotalHits() + 1);
        }
    }

    // Replace an altered message in result set.
    private void updateResultSet(ResultSet result, Resource resource) {
        int resourceIndex = this.getResourceIndex(result, resource);
        if (resourceIndex != -1) {
            List<PropertySet> resources = result.getAllResults();
            resources.remove(resourceIndex);
            resources.add(resourceIndex, resource);
        }
    }

    private int getResourceIndex(ResultSet result, Resource resource) {
        int index = 0;
        for (PropertySet ps : result.getAllResults()) {
            // Only check local resources
            if (ps.getPropertyByPrefix(null, MultiHostSearcher.MULTIHOST_RESOURCE_PROP_NAME) == null) {
                if (ps.getURI().equals(resource.getURI())) {
                    return index;
                }
            }
            index++;
        }
        return -1;
    }

    private void removeDeleted(ResultSet result, Repository repository, String token) {

        List<PropertySet> deletedSet = new ArrayList<PropertySet>();
        List<PropertySet> resources = result.getAllResults();
        for (PropertySet ps : resources) {
            // Only check local resource for existence
            if (ps.getPropertyByPrefix(null, MultiHostSearcher.MULTIHOST_RESOURCE_PROP_NAME) == null) {
                boolean deleted = false;
                try {
                    deleted = !repository.exists(token, ps.getURI());
                } catch (Exception e) {
                    // Ignore
                }
                if (deleted) {
                    deletedSet.add(ps);
                }
            }
        }

        int deletedCount = deletedSet.size();
        if (deletedCount > 0) {
            resources.removeAll(deletedSet);
            ((ResultSetImpl) result).setTotalHits(result.getTotalHits() - deletedCount);
        }

    }

}
