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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.SystemChangeContext;
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

public class ReferencingResourcesPathSelector implements PathSelector {
    private PropertyTypeDefinition hrefsPropDef;
    protected Searcher searcher;
    private PathSelector selector;
    private int mainQueryLimit = 100;
    
    public ReferencingResourcesPathSelector(PropertyTypeDefinition hrefsPropDef,
            Searcher searcher, PathSelector selector) {
        super();
        this.hrefsPropDef = hrefsPropDef;
        this.searcher = searcher;
        this.selector = selector;
    }

    
    @Override
    public void selectWithCallback(Repository repository,
            SystemChangeContext context, PathSelectCallback callback)
                    throws Exception {
        
        List<Path> uris = new ArrayList<>();
        
        selector.selectWithCallback(repository, context, new PathSelectCallback() {
            @Override
            public void beginBatch(int total) throws Exception { }
            @Override
            public void select(Path path) throws Exception { uris.add(path); }
        });

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
    
}
