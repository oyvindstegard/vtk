/* Copyright (c) 2007, University of Oslo, Norway
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
package org.vortikal.repository.search;

import org.vortikal.repository.PropertySet;
import org.vortikal.repository.search.query.Query;

/**
 * Executes a query on repository resources with a hard limit on how
 * many results that should be returned, in addition to a cursor. 
 * 
 * At any given time, the <code>Query</code> alone will produce a complete result
 * set. The <code>cursor</code> and <code>maxResults</code> parameters
 * can be used to fetch subsets of this result set. Useful for implementing
 * paging when browsing large result sets.
 * 
 * The implementation must take into consideration what happens
 * when the complete result set changes between queries with 
 * cursor/maxResults. 
 * 
 * @param token The security token associated with the principal 
 *              executing the query.
 * @param query The <code>Query</code> object, containing the query
 *              conditions.
 * @param maxResults Number of results to include from (and including) cursor position.
 * @param cursor     Positition to start in the query result set (zero-based).
 * 
 * @param sorting the {@link Sorting} to use
 * @param selectedProperties the {@link PropertySelecy properties} queried for
 * 
 * @return      A <code>ResultSet</code> containing a subset of the results.
 * 
 * @throws org.vortikal.repository.search.QueryException If the query could not be executed.
 * 
 * @see Query, ResultSet
 */
public class Search {

    public final static int MAX_LIMIT = 40000; 
    
    private PropertySelect propertySelect = new WildcardPropertySelect();
    private Query query;
    private Sorting sorting;
    private int limit = MAX_LIMIT;
    private int cursor = 0;
    
    public Search() {
        SortingImpl defaultSorting = new SortingImpl();
        defaultSorting.addSortField(new TypedSortField(PropertySet.URI_IDENTIFIER));
        this.sorting = defaultSorting;
    }

    public int getCursor() {
        return cursor;
    }
    
    public void setCursor(int cursor) {
        if (cursor < 0) {
            throw new IllegalArgumentException("Cursor cannot be negative");
        }
        this.cursor = cursor;
    }
    
    public int getLimit() {
        return limit;
    }
    
    public void setLimit(int limit) {
        if (limit > MAX_LIMIT) {
            this.limit = MAX_LIMIT;
        } else if (limit < 0) {
            throw new IllegalArgumentException("Limit cannot be negative");
        } else {
            this.limit = limit;
        }
    }
    
    public PropertySelect getPropertySelect() {
        return propertySelect;
    }
    
    public void setPropertySelect(PropertySelect propertySelect) {
        this.propertySelect = propertySelect;
    }
    
    public Query getQuery() {
        return query;
    }
    
    public void setQuery(Query query) {
        this.query = query;
    }
    
    public Sorting getSorting() {
        return sorting;
    }
    
    public void setSorting(Sorting sorting) {
        this.sorting = sorting;
    }
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(this.getClass().getName()).append(": [");
        sb.append("query=").append(this.query.dump(""));
        sb.append(", propertySelect=").append(this.propertySelect);
        sb.append(", sorting=").append(this.sorting);
        sb.append(", limit=").append(this.limit);
        sb.append(", cursor=").append(this.cursor).append("]");
        return sb.toString();
    }
    
}
