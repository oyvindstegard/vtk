/* Copyright (c) 2015,2016 University of Oslo, Norway
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
package vtk.repository.systemjob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.SystemChangeContext;
import vtk.repository.event.RepositoryEvent;
import vtk.repository.event.ResourceCreationEvent;
import vtk.repository.event.ResourceDeletionEvent;
import vtk.repository.event.ResourceModificationEvent;
import vtk.repository.event.ResourceMovedEvent;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.PropertySelect;
import vtk.repository.search.PropertySortField;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.Searcher;
import vtk.repository.search.Sorting;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.PropertyTermQuery;
import vtk.repository.search.query.PropertyWildcardQuery;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.UriDepthQuery;
import vtk.repository.search.query.UriPrefixQuery;
import vtk.security.SecurityContext;
import vtk.util.repository.AbstractRepositoryEventHandler;

/**
 * Repository event handler that captures URI name space operations 
 * and stores the URIs of these events in a limited in-memory queue.
 *
 * <p>This event handler is also a {@code PathSelector} for use by system jobs,
 * where it will select URIs of resources which have URLs that reference the
 * URIs stored in its internal queue.
 *
 * <p>Depending on repository write load, events will be discarded without
 * being processed if the queue limit is exceeded.
 */
public class ChangedUriReferencesPathSelector 
    extends AbstractRepositoryEventHandler implements PathSelector {
    
    private PropertyTypeDefinition hrefsPropDef;
    protected Searcher searcher;
    private int mainQueryLimit = 200;
    private final BlockingQueue<PropertySet> queue = new ArrayBlockingQueue<>(100);
    
    public ChangedUriReferencesPathSelector(PropertyTypeDefinition hrefsPropDef,
            Searcher searcher) {
        // No need for async because event handler only adds to a concurrent queue
        // in a non-blocking fashion
        super(false);
        this.hrefsPropDef = hrefsPropDef;
        this.searcher = searcher;
    }
    
    @Override
    public void handleEvent(RepositoryEvent event) {
        if (event instanceof ResourceModificationEvent) {
            ResourceModificationEvent modEvent = 
                    (ResourceModificationEvent) event;
            if (modEvent.getOriginal().isPublished() != 
                    modEvent.getResource().isPublished()) {
                queue.offer(modEvent.getOriginal());
            }
        }
        else if (event instanceof ResourceDeletionEvent) {
            queue.offer(((ResourceDeletionEvent) event).getResource());
        }
        else if (event instanceof ResourceCreationEvent) {
            queue.offer(((ResourceCreationEvent) event).getResource());
        }
        else if (event instanceof ResourceMovedEvent) {
            queue.offer(((ResourceMovedEvent) event).getFrom());
            queue.offer(((ResourceMovedEvent) event).getResource());
        }
    }

    /**
     * Select paths of resources that reference URIs recently subjected to namespace
     * changes or change in published-status.
     *
     * @param repository
     * @param context
     * @param callback
     * @throws Exception
     */
    @Override
    public void selectWithCallback(Repository repository,
            SystemChangeContext context, PathSelectCallback callback)
                    throws Exception {

        List<PropertySet> modified = new ArrayList<>(100);
        queue.drainTo(modified);
        
        Search mainSearch = referencingSearch(context, modified);
        final String token = SecurityContext.exists() ? 
                SecurityContext.getSecurityContext().getToken() : null;

        ResultSet results = searcher.execute(token, mainSearch);
        callback.beginBatch(results.getSize());
        for (PropertySet p: results) {
            callback.select(p.getURI());
        }
    }
    

    private Search referencingSearch(SystemChangeContext context, List<PropertySet> resources) {
        OrQuery query = new OrQuery();
        
        for (PropertySet resource: resources) {
            Path uri = resource.getURI();
            
            // Root-relative and full URLs:
            String term = "*" + uri.toString().replaceAll(" ", "\\ ") + "*";
            PropertyWildcardQuery hrefQuery = new PropertyWildcardQuery(hrefsPropDef, term, TermOperator.EQ);
            hrefQuery.setComplexValueAttributeSpecifier("links.url");
            query.add(hrefQuery);

            if (!uri.isRoot()) {
                // Relative references:
                term = "*" + uri.getName().replaceAll(" ", "\\ ") + "*";
                AndQuery andQuery = new AndQuery();
                hrefQuery = new PropertyWildcardQuery(hrefsPropDef, term, TermOperator.EQ);
                hrefQuery.setComplexValueAttributeSpecifier("links.url");
                andQuery.add(hrefQuery);
                andQuery.add(new UriPrefixQuery(uri.getParent().toString().replaceAll(" ", "\\ ")));
                andQuery.add(new UriDepthQuery(uri.getDepth()));
                query.add(andQuery);
                
            }
            
            // vrtxid fields in hrefs property:
            Property idProp = resource.getProperty(
                    Namespace.DEFAULT_NAMESPACE, 
                    PropertyType.EXTERNAL_ID_PROP_NAME);
            PropertyTermQuery idRefQuery = new PropertyTermQuery(
                    hrefsPropDef, idProp.getStringValue(), TermOperator.EQ);
            idRefQuery.setComplexValueAttributeSpecifier("links.vrtxid");
            query.add(idRefQuery);
        }

        Search search = new Search();
        search.setQuery(query);
        
        PropertySortField sortField = new PropertySortField(context.getSystemJobStatusPropDef());
        sortField.setComplexValueAttributeSpecifier("linkcheck-recent");
        search.setSorting(new Sorting(Collections.singletonList(sortField)));
        search.setLimit(mainQueryLimit);
        search.clearAllFilterFlags();
        search.setPropertySelect(PropertySelect.NONE);
        return search;
    }
    
}
