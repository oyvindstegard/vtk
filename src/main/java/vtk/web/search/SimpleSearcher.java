/* Copyright (c) 2017, University of Oslo, Norway
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
package vtk.web.search;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.ConfigurablePropertySelect;
import vtk.repository.search.PropertySelect;
import vtk.repository.search.QueryParserFactory;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.Searcher;
import vtk.repository.search.Sorting;
import vtk.repository.search.SortingParserFactory;

/**
 * Simple index searcher (built on top of the 
 * {@link vtk.repository.search} interfaces). 
 * 
 * <p>Handles textual parsing of queries, sorting and property 
 * selects, using a {@link QueryBuilder builder} pattern.
 * 
 * <p>Basic usage:
 * <pre>
 *  SimpleSearcher searcher s = ...;
 *  SimpleSearcher.Query query = searcher.queryBuilder()
 *      .query("type IN resource")
 *      .sorting("lastModified desc")
 *      .select("title,lastModified")
 *      .limit(10)
 *      .offset(20)
 *      .build();
 *  ResultSet rs = searcher.search(token, query);
 * </pre>
 */
public final class SimpleSearcher {
    private QueryParserFactory parserFactory;
    private SortingParserFactory sortingFactory;
    private Searcher searcher;
    private ResourceTypeTree resourceTypeTree;
    
    public static final Set<String> SPECIAL_FIELDS;
    static {
        Set<String> fields = new HashSet<>();
        fields.add("uri");
        fields.add("type");
        fields.add("name");
        fields.add("acl");
        SPECIAL_FIELDS = Collections.unmodifiableSet(fields);
    }
    
    public SimpleSearcher(QueryParserFactory parserFactory, 
            SortingParserFactory sortingFactory, 
            Searcher searcher, 
            ResourceTypeTree resourceTypeTree) {
        this.parserFactory = Objects.requireNonNull(parserFactory);
        this.sortingFactory = Objects.requireNonNull(sortingFactory);
        this.searcher = Objects.requireNonNull(searcher);
        this.resourceTypeTree = Objects.requireNonNull(resourceTypeTree);
    }
    
    /**
     * Creates a new query builder
     */
    public QueryBuilder builder() {
        return new QueryBuilder(parserFactory, sortingFactory, resourceTypeTree);
    }

    /**
     * Performs a search with a given query and transformer
     * @param token the security token
     * @param query the query
     * @param transformer function that transforms the result set
     * @return the transformed result set
     */
    public <T> T search(String token, Query query, Function<ResultSet, T> transformer) {
        Search search = new Search();
        search.setQuery(query.query);
        if (query.sorting.isPresent()) {
            search.setSorting(query.sorting.get());
        }
        search.setLimit(query.limit);
        search.setCursor(query.offset);
        search.setPropertySelect(query.select);
        if (query.unpublished) {
            search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED_COLLECTIONS, 
                    Search.FilterFlag.UNPUBLISHED);
        }
        return transformer.apply(searcher.execute(token, search));
    }
    
    /**
     * Performs a search for a given query
     * @param token the security token
     * @param query the query
     * @return the result set
     */
    public ResultSet search(String token, Query query) {
        return search(token, query, rs -> rs);
    }

    /**
     * Performs an asynchronous search for a given query and result transformer. 
     * The search is executed on the {@link java.util.concurrent.ForkJoinPool#commonPool}
     * @param token the security token
     * @param query the query
     * @return the future transformed result set
     */
    public <T> CompletableFuture<T> searchAsync(String token, Query query, Function<ResultSet,T> transformer) {
        CompletableFuture<ResultSet> future = CompletableFuture.supplyAsync(() -> search(token, query));
        return future.thenApply(transformer);
    }
    
    /**
     * Performs an asynchronous search for a given query. 
     * The search is executed on the {@link java.util.concurrent.ForkJoinPool#commonPool}
     * @param token the security token
     * @param query the query
     * @return the future result set
     */
    public CompletableFuture<ResultSet> searchAsync(String token, Query query) {
        return searchAsync(token, query, rs -> rs);
    }

    public static final class Query {
        public final vtk.repository.search.query.Query query;
        public final int limit;
        public final int offset;
        public final Optional<Sorting> sorting;
        public final List<String> fields;
        public final PropertySelect select;
        public final boolean unpublished;
        
        private Query(vtk.repository.search.query.Query query, int limit, int offset, 
                Optional<Sorting> sorting, List<String> fields, PropertySelect select,
                boolean unpublished) {
            this.query = query;
            this.limit = limit;
            this.offset = offset;
            this.sorting = sorting;
            this.fields = Collections.unmodifiableList(fields);
            this.select = select;
            this.unpublished = unpublished;
        }
        
        @Override
        public String toString() {
            return getClass().getSimpleName() + "("
                    + query + ", " + limit + ", " + offset + ", "
                    + sorting + "," + fields + ", " + select + ","
                    + unpublished + ")";
        }
        
    }
    
    public static final class QueryBuilder {
        private vtk.repository.search.query.Query query;
        private int limit = 100;
        private int offset = 0;
        private Sorting sorting;
        private List<String> fields = Collections.emptyList();
        private PropertySelect select = PropertySelect.NONE;
        private boolean unpublished;
        
        private QueryParserFactory parserFactory;
        private SortingParserFactory sortingFactory;
        private ResourceTypeTree resourceTypeTree;
        
        private QueryBuilder(QueryParserFactory parserFactory, 
                SortingParserFactory sortingFactory,
                ResourceTypeTree resourceTypeTree) {
            this.parserFactory = parserFactory;
            this.sortingFactory = sortingFactory;
            this.resourceTypeTree = resourceTypeTree;
        }
        
        public QueryBuilder query(String query) {
            Objects.requireNonNull(query);
            this.query = parserFactory.getParser().parse(query);
            return this;
        }
        
        public QueryBuilder limit(int limit) {
            if (limit <= 0 || limit > 1000) {
                throw new IllegalArgumentException("Limit must be an integer between 0 and 1000");
            }
            this.limit = limit;
            return this;
        }
        
        public QueryBuilder offset(int offset) {
            if (offset < 0) {
                throw new IllegalArgumentException("Offset must be an integer >= 0");
            }
            this.offset = offset;
            return this;
        }
        
        public QueryBuilder sorting(String sorting) {
            Objects.requireNonNull(sorting);
            this.sorting = sortingFactory.parser().parse(sorting);
            return this;
        }
        
        public QueryBuilder select(String select) {
            Objects.requireNonNull(select);
            this.fields = splitFields(select);
            this.select = parseSelect(fields);
            return this;
        }
        
        public QueryBuilder unpublished(boolean unpublished) {
            this.unpublished = unpublished;
            return this;
        }
        
        public Query build() {
            Objects.requireNonNull(query, "Field 'query' is NULL");
            Objects.requireNonNull(query, "Field 'select' is NULL");
            
            return new Query(query, limit, offset, 
                    Optional.ofNullable(sorting), fields, select,
                    unpublished);
        }
        
        private List<String> splitFields(String fields) {
            if (fields == null || fields.trim().equals("")) {
                return Collections.emptyList();
            }
            return Arrays.asList(fields.split(","));
        }

        private PropertySelect parseSelect(List<String> fields) {
            if (fields.isEmpty()) {
                return PropertySelect.NONE;
            }
            
            boolean allProps = false;
            boolean acl = false;

            ConfigurablePropertySelect propertySelect = 
                    new ConfigurablePropertySelect();
            
            for (String propName: fields) {
                if ("acl".equals(propName)) {
                    acl = true;
                    continue;
                }
                if ("*".equals(propName)) {
                    allProps = true;
                    continue;
                }
                if (SPECIAL_FIELDS.contains(propName)) {
                    continue;
                }
                String p = propName;
                String prefix = null;

                if (p.contains(":")) {
                    prefix = p.substring(0, p.indexOf(":"));
                    p = p.substring(prefix.length() + 1);
                }

                PropertyTypeDefinition def = resourceTypeTree
                        .getPropertyDefinitionByPrefix(prefix, p);
                if (!resourceTypeTree.isManagedProperty(def)) {
                    throw new IllegalArgumentException("Unknown field '" + propName + "'");
                }
                propertySelect.addPropertyDefinition(def);
            }
            if (allProps && acl) {
                return PropertySelect.ALL;
            }
            else if (allProps) {
                return PropertySelect.ALL_PROPERTIES;
            }
            propertySelect.setIncludeAcl(acl);
            return propertySelect;
        }
    }
    
}
