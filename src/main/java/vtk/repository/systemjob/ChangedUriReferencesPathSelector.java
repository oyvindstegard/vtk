/* Copyright (c) 2015, University of Oslo, Norway
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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.SystemChangeContext;
import vtk.repository.event.RepositoryEvent;
import vtk.repository.event.ResourceCreationEvent;
import vtk.repository.event.ResourceDeletionEvent;
import vtk.repository.event.ResourceModificationEvent;
import vtk.repository.event.ResourceMovedEvent;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.PropertySelect;
import vtk.repository.search.PropertySortField;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.Searcher;
import vtk.repository.search.Sorting;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.PropertyWildcardQuery;
import vtk.repository.search.query.TermOperator;
import vtk.security.SecurityContext;
import vtk.util.repository.AbstractRepositoryEventHandler;

/**
 * Repository event handler that captures URI name space operations 
 * and stores these events in an in-memory queue, such that they 
 * are made available to the periodic link checking system job 
 * (by implementing the {@link  PathSelector} interface).
 */
public class ChangedUriReferencesPathSelector 
    extends AbstractRepositoryEventHandler implements PathSelector {
    
    private PropertyTypeDefinition hrefsPropDef;
    protected Searcher searcher;
    private int mainQueryLimit = 100;
    private UriQueue queue = new UriQueue(100);
    
    public ChangedUriReferencesPathSelector(PropertyTypeDefinition hrefsPropDef,
            Searcher searcher) {
        super();
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
                queue.add(modEvent.getOriginal().getURI());
            }
        }
        else if (event instanceof ResourceDeletionEvent) {
            queue.add(((ResourceDeletionEvent) event).getResource().getURI());
        }
        else if (event instanceof ResourceCreationEvent) {
            queue.add(((ResourceCreationEvent) event).getResource().getURI());
        }
        else if (event instanceof ResourceMovedEvent) {
            queue.add(((ResourceMovedEvent) event).getFrom().getURI());
            queue.add(((ResourceMovedEvent) event).getResource().getURI());
        }
    }
    
    @Override
    public synchronized void selectWithCallback(Repository repository,
            SystemChangeContext context, PathSelectCallback callback)
                    throws Exception {
        
        List<Path> uris = queue.dequeueAll();
        
        Search mainSearch = referencingSearch(context, uris);
        final String token = SecurityContext.exists() ? 
                SecurityContext.getSecurityContext().getToken() : null;

        ResultSet results = searcher.execute(token, mainSearch);
        callback.beginBatch(results.getSize());
        for (PropertySet p: results) {
            callback.select(p.getURI());
        }
    }
    

    private Search referencingSearch(SystemChangeContext context, List<Path> uris) {
        OrQuery query = new OrQuery();
        
        for (Path uri: uris) {
            String term = "*" + uri.toString().replaceAll(" ", "\\") + "*";
            PropertyWildcardQuery hrefQuery = new PropertyWildcardQuery(hrefsPropDef, term, TermOperator.EQ);
            hrefQuery.setComplexValueAttributeSpecifier("links.url");
            query.add(hrefQuery);
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


    private static class UriQueue {
        private int limit;
        private LinkedList<Path> elements = new LinkedList<>();
        public UriQueue(int limit) {
            this.limit = limit;
        }

        public boolean add(Path path) {
            synchronized(elements) {
                boolean result = elements.add(path);
                if (result) while (elements.size() > limit) elements.remove();
                return result;
            }
        }
        
        public List<Path> dequeueAll() {
            synchronized(elements) {
                if (elements.size() == 0) return Collections.emptyList();
                List<Path> result = elements;
                elements = new LinkedList<>();
                return result;
            }
        }
    }
    
}
