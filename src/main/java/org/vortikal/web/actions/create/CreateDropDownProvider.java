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
package org.vortikal.web.actions.create;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.Path;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.search.ResultSet;
import org.vortikal.repository.search.Search;
import org.vortikal.repository.search.Searcher;
import org.vortikal.repository.search.query.AndQuery;
import org.vortikal.repository.search.query.TermOperator;
import org.vortikal.repository.search.query.TypeTermQuery;
import org.vortikal.repository.search.query.UriDepthQuery;
import org.vortikal.repository.search.query.UriPrefixQuery;

public class CreateDropDownProvider {

    private Searcher searcher;
    private Repository repository;
    private final int maxLimit = 500;

    private static Log logger = LogFactory.getLog(CreateDropDownProvider.class);

    public List<Resource> buildSearchAndPopulateResources(String uri, String token) {
        AndQuery mainQuery = new AndQuery();
        if (uri.equals("")) {
            mainQuery.add(new UriDepthQuery(0));
        } else {
            mainQuery.add(new UriPrefixQuery(uri));
            mainQuery.add(new UriDepthQuery(Path.fromString(uri).getDepth() + 1));
        }
        mainQuery.add(new TypeTermQuery("collection", TermOperator.IN));
        Search search = new Search();
        search.removeAllFilterFlags();
        search.setQuery(mainQuery);
        search.setLimit(maxLimit);
        ResultSet rs = searcher.execute(token, search);

        List<PropertySet> results = rs.getAllResults();
        List<Resource> items = new ArrayList<Resource>();

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
                search.removeAllFilterFlags();
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
