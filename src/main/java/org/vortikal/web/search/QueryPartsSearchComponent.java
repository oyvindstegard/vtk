/* Copyright (c) 2008, University of Oslo, Norway
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
package org.vortikal.web.search;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.search.ResultSet;
import org.vortikal.repository.search.Search;
import org.vortikal.repository.search.Sorting;
import org.vortikal.repository.search.query.AndQuery;
import org.vortikal.repository.search.query.Query;
import org.vortikal.web.RequestContext;

public class QueryPartsSearchComponent extends QuerySearchComponent {

    protected List<QueryBuilder> queryBuilders;

    @Override
    protected ResultSet getResultSet(HttpServletRequest request, Resource collection, String token, Sorting sorting,
            int searchLimit, int offset) {

        Query query = this.getQuery(collection, request);

        Search search = new Search();
        search.setQuery(query);
        search.setLimit(searchLimit);
        search.setCursor(offset);
        search.setSorting(sorting);

        Repository repository = RequestContext.getRequestContext().getRepository();
        return repository.search(token, search);
    }

    private Query getQuery(Resource collection, HttpServletRequest request) {

        if (this.queryBuilders == null) {
            throw new IllegalArgumentException("Component need at least one query builder");
        }

        AndQuery query = new AndQuery();

        for (QueryBuilder builder : this.queryBuilders) {
            Query q = builder.build(collection, request);
            if (q != null) {
                query.add(q);
            }
        }

        return query;
    }

    public void setQueryBuilders(List<QueryBuilder> queryBuilders) {
        this.queryBuilders = queryBuilders;
    }

}
