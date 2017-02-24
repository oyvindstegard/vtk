/* Copyright (c) 2007-2017, University of Oslo, Norway
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

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

import vtk.repository.PropertySet;
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

    /**
     * Specifies duration and timeout when waiting for pending updates
     * is specified for the search.
     */
    public static final class WaitSpec {
        private final Instant timestamp;
        private final Duration timeout;
        private WaitSpec(Instant timestamp, Duration timeout) {
            this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
            this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        }
        public Instant timestamp() {
            return timestamp;
        }
        public Duration timeout() {
            return timeout;
        }
        @Override
        public int hashCode() {
            int hash = 7;
            hash = 89 * hash + Objects.hashCode(this.timestamp);
            hash = 89 * hash + Objects.hashCode(this.timeout);
            return hash;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final WaitSpec other = (WaitSpec) obj;
            if (!Objects.equals(this.timestamp, other.timestamp)) {
                return false;
            }
            if (!Objects.equals(this.timeout, other.timeout)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "WaitSpec{" + "timestamp=" + timestamp + ", timeout=" + timeout + '}';
        }
        
    }

    private PropertySelect propertySelect = PropertySelect.ALL_PROPERTIES;
    private Query query;
    private Sorting sorting;
    private int limit = DEFAULT_LIMIT;
    private int cursor = 0;
    private final EnumSet<FilterFlag> filterFlags;
    private WaitSpec waitForPendingUpdatesSpec = null;

    public Search() {
        Sorting defaultSorting = new Sorting();
        defaultSorting.addSortField(new ResourceSortField(PropertySet.URI_IDENTIFIER));
        this.sorting = defaultSorting;
        this.filterFlags = EnumSet.allOf(FilterFlag.class);
    }

    public int getCursor() {
        return this.cursor;
    }

    /**
     * Requests that the thread which will execute this search get search
     * results which are at least as recently updated as the provided timestamp,
     * with regard to modifications to the resource repository.
     *
     * <p>The repository index is asynchronously updated after repository write
     * operations, and the time it takes for the index to reflect actual repository
     * state depends on the general write load and other factors.
     * This search setting can be used to attempt a wait for updated results
     * if it is important for client code to not be served search results
     * reflecting earlier and stale repository resource states.
     *
     * <p>Be aware that setting this will cause searching threads to be blocked if
     * there are pending updates older than the provided timestamp present in
     * the repository change event log. If so, an attempt will be made to wait
     * for the pending updates to be indexed before executing search, thus
     * ensuring up to date results (as of at least the timestamp).
     *
     * <p>A timeout must also be provided, which when reached, will cause the
     * search to proceed normally, but possibly with not as up to date results
     * as requested. The search result set can be inspected to check for this
     * condition using {@link ResultSet#recency() }, and action taken depends on client code needs (retry, accept
     * whatever for best effort, etc.).
     *
     * @param timestamp a freshness timestamp, which must be provided
     * @param timeout a timeout
     * @return this search instance for easy setter-chaining.
     */
    public Search setWaitForPendingUpdates(Instant timestamp, Duration timeout) {
        this.waitForPendingUpdatesSpec = new WaitSpec(timestamp, timeout);
        return this;
    }

    /**
     * Get wait spec for pending updates. Optional and only present if
     * {@link #waitForPendingUpdatesSpec} has been set.
     * @return
     */
    public Optional<WaitSpec> getWaitForPendingUpdates() {
        return Optional.ofNullable(waitForPendingUpdatesSpec);
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
        return "Search{" + "propertySelect=" + propertySelect + ", query=" + query
                + ", sorting=" + sorting + ", limit=" + limit + ", cursor=" + cursor
                + ", filterFlags=" + filterFlags + ", waitForPendingUpdatesSpec=" + waitForPendingUpdatesSpec + '}';
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 73 * hash + Objects.hashCode(this.propertySelect);
        hash = 73 * hash + Objects.hashCode(this.query);
        hash = 73 * hash + Objects.hashCode(this.sorting);
        hash = 73 * hash + this.limit;
        hash = 73 * hash + this.cursor;
        hash = 73 * hash + Objects.hashCode(this.filterFlags);
        hash = 73 * hash + Objects.hashCode(this.waitForPendingUpdatesSpec);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Search other = (Search) obj;
        if (this.limit != other.limit) {
            return false;
        }
        if (this.cursor != other.cursor) {
            return false;
        }
        if (!Objects.equals(this.propertySelect, other.propertySelect)) {
            return false;
        }
        if (!Objects.equals(this.query, other.query)) {
            return false;
        }
        if (!Objects.equals(this.sorting, other.sorting)) {
            return false;
        }
        if (!Objects.equals(this.filterFlags, other.filterFlags)) {
            return false;
        }
        if (!Objects.equals(this.waitForPendingUpdatesSpec, other.waitForPendingUpdatesSpec)) {
            return false;
        }
        return true;
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
