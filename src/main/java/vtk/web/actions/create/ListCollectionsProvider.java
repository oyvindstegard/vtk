/* Copyright (c) 2011, University of Oslo, Norway
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
package vtk.web.actions.create;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.Searcher;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.TypeTermQuery;
import vtk.repository.search.query.UriDepthQuery;
import vtk.repository.search.query.UriPrefixQuery;

public class ListCollectionsProvider {

    private Searcher searcher;
    private Repository repository;
    private final int maxLimit = 500;

    private static Logger logger = LoggerFactory.getLogger(ListCollectionsProvider.class);

    public List<Resource> buildSearchAndPopulateResources(Path uri, String token) {
        AndQuery mainQuery = new AndQuery();
        if (uri.equals("")) {
            mainQuery.add(new UriDepthQuery(0));
        } else {
            mainQuery.add(new UriPrefixQuery(uri.toString()));
            mainQuery.add(new UriDepthQuery(uri.getDepth() + 1));
        }
        mainQuery.add(new TypeTermQuery("collection", TermOperator.IN));
        Search search = new Search();
        search.clearAllFilterFlags();
        search.setQuery(mainQuery);
        search.setLimit(maxLimit);
        ResultSet rs = searcher.execute(token, search);

        List<PropertySet> results = rs.getAllResults();
        List<Resource> items = new ArrayList<>();

        for (PropertySet result : results) {
            try {
                Resource r = this.repository.retrieve(token, result.getURI(), true);
                items.add(r);
            } catch (Exception e) {
                logger.error("Exception " + e.getMessage());
            }
        }
        return items;
    }

    public boolean hasChildren(Resource r, String token) {
        if (r.getChildURIs() != null) {
            if (!r.getChildURIs().isEmpty()) {
                AndQuery mainQuery = new AndQuery();
                mainQuery.add(new UriPrefixQuery(r.getURI().toString()));
                mainQuery.add(new UriDepthQuery(r.getURI().getDepth() + 1));
                mainQuery.add(new TypeTermQuery("collection", TermOperator.IN));
                Search search = new Search();
                search.clearAllFilterFlags();
                search.setQuery(mainQuery);
                search.setLimit(1);
                if (searcher.execute(token, search).getTotalHits() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getLocalizedTitle(HttpServletRequest request, String key, Object[] params) {
        org.springframework.web.servlet.support.RequestContext springRequestContext = new org.springframework.web.servlet.support.RequestContext(
                request);
        if (params != null) {
            return springRequestContext.getMessage(key, params);
        }
        return springRequestContext.getMessage(key);
    }

    @Required
    public void setSearcher(Searcher searcher) {
        this.searcher = searcher;
    }

    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }
}
