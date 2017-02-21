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
package vtk.repository.search;

import java.util.EnumSet;

import vtk.repository.PropertySet;
import vtk.repository.search.query.DumpQueryTreeVisitor;
import vtk.repository.search.query.Query;

/**
 * Specifies a search on repository resources with a hard limit on how many
 * results that should be returned, in addition to a cursor.
 * 
 * At any given time, the <code>Query</code> alone will produce a complete
 * result set. The <code>cursor</code> and <code>maxResults</code> parameters
 * can be used to fetch subsets of this result set. Useful for implementing
 * paging when browsing large result sets.
 * 
 * The implementation must take into consideration what happens when the
 * complete result set changes between queries with cursor/maxResults.
 * 
 * @see Query
 * @see ResultSet
 * @see Sorting
 * @see PropertySelect
 */
public final class Search {

    public final static int DEFAULT_LIMIT = 40000;

    public enum FilterFlag {
        UNPUBLISHED,
        UNPUBLISHED_COLLECTIONS,
    }
    
    private PropertySelect propertySelect = PropertySelect.ALL_PROPERTIES;
    private Query query;
    private Sorting sorting;
    private int limit = DEFAULT_LIMIT;
    private int cursor = 0;
    private final EnumSet<FilterFlag> filterFlags;

    public Search() {
        Sorting defaultSorting = new Sorting();
        defaultSorting.addSortField(new TypedSortField(PropertySet.URI_IDENTIFIER));
        this.sorting = defaultSorting;
        this.filterFlags = EnumSet.allOf(FilterFlag.class);
    }

    public int getCursor() {
        return this.cursor;
    }

    public Search setCursor(int cursor) {
        if (cursor < 0) {
            throw new IllegalArgumentException("Cursor cannot be negative");
        }
        this.cursor = cursor;
        return this;
    }

    public int getLimit() {
        return this.limit;
    }

    public Search setLimit(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Limit cannot be negative");
        }
        this.limit = limit;
        return this;
    }

    /**
     * 
     * @return the configured <code>PropertySelect</code>. May also return <code>null</code>,
     * which in general means no particular selection.
     * @see #setPropertySelect(vtk.repository.search.PropertySelect) 
     */
    public PropertySelect getPropertySelect() {
        return propertySelect;
    }

    /**
     * Set a custom property selection. This decides what index fields to load
     * for search results, and thus which properties will be available for
     * retrieval in property sets.
     * 
     * <p>Default is {@link PropertySelect#ALL_PROPERTIES}, which gives you
     * all resources properties, but not the resource ACL.
     * @param propertySelect the property selection to use in search result mapping
     * @return this search instance
     * @see PropertySelect#ALL_PROPERTIES
     * @see PropertySelect#ALL
     * @see PropertySelect#NONE
     * @see ConfigurablePropertySelect
     */
    public Search setPropertySelect(PropertySelect propertySelect) {
        this.propertySelect = propertySelect;
        return this;
    }

    public Query getQuery() {
        return query;
    }

    public Search setQuery(Query query) {
        this.query = query;
        return this;
    }

    public Sorting getSorting() {
        return sorting;
    }

    public Search setSorting(Sorting sorting) {
        this.sorting = sorting;
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getName()).append("[");
        if (query != null) {
            sb.append("query=").append(query.accept(new DumpQueryTreeVisitor(), null));
        } else {
            sb.append("query=null");
        }
        sb.append(", propertySelect=").append(this.propertySelect);
        sb.append(", sorting=").append(this.sorting);
        sb.append(", limit=").append(this.limit);
        sb.append(", cursor=").append(this.cursor).append("]");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Search other = (Search) obj;
        if (this.propertySelect != other.propertySelect
                && (this.propertySelect == null || !this.propertySelect.equals(other.propertySelect))) {
            return false;
        }
        if (this.query != other.query && (this.query == null || !this.query.equals(other.query))) {
            return false;
        }
        if (this.sorting != other.sorting && (this.sorting == null || !this.sorting.equals(other.sorting))) {
            return false;
        }
        if (!this.filterFlags.equals(other.filterFlags)) {
            return false;
        }
        if (this.limit != other.limit) {
            return false;
        }
        if (this.cursor != other.cursor) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + (this.propertySelect != null ? this.propertySelect.hashCode() : 0);
        hash = 47 * hash + (this.query != null ? this.query.hashCode() : 0);
        hash = 47 * hash + (this.sorting != null ? this.sorting.hashCode() : 0);
        hash = 47 * hash + (this.filterFlags.contains(FilterFlag.UNPUBLISHED) ? 1 : 0);
        hash = 47 * hash + (this.filterFlags.contains(FilterFlag.UNPUBLISHED_COLLECTIONS) ? 1 : 0);
        hash = 47 * hash + this.limit;
        hash = 47 * hash + this.cursor;
        return hash;
    }

    /*
     * Checks if a filter flag is set.
     */
    public boolean hasFilterFlag(FilterFlag flag) {
        return filterFlags.contains(flag);
    }

    /*
     * Remove one or more filter flags.
     */
    public Search removeFilterFlag(FilterFlag... flags) {
        for (FilterFlag flag : flags) {
            filterFlags.remove(flag);
        }
        return this;
    }

    /* Add filter flags to search.
    */
    public Search addFilterFlag(FilterFlag... flags) {
        for (FilterFlag flag: flags) {
            filterFlags.add(flag);
        }
        return this;
    }
    
    /* Removes all filter flags set on search.
     */
    public Search clearAllFilterFlags() {
        filterFlags.clear();
        return this;
    }


}
