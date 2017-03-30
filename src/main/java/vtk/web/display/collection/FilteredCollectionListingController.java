/* Copyright (c) 2012, University of Oslo, Norway
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
package vtk.web.display.collection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.ConfigurablePropertySelect;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.Searcher;
import vtk.repository.search.Sorting;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.Query;
import vtk.repository.search.query.UriPrefixQuery;
import vtk.security.SecurityContext;
import vtk.web.RequestContext;
import vtk.web.decorating.components.menu.SubFolderMenuProvider;
import vtk.web.display.listing.ListingPager;
import vtk.web.display.listing.ListingPagingLink;
import vtk.web.search.SearchSorting;
import vtk.web.service.Service;
import vtk.web.service.URL;

public abstract class FilteredCollectionListingController implements Controller {

    private final Logger logger = LoggerFactory.getLogger(FilteredCollectionListingController.class);

    protected static final String filterNamespace = "filter.";

    private String viewName;
    private String customSearch;
    private String preResult;
    private String customFilters;
    private String customListing;
    private Map<String, List<String>> filters;
    private int pageLimit = 25;
    protected ResourceTypeTree resourceTypeTree;

    private SearchSorting defaultSearchSorting;
    private List<String> configurablePropertySelectPointers;
    protected Service viewService;
    private List<String> filterWhitelistExceptions;
    protected Searcher searcher;
    private SubFolderMenuProvider subFolderMenuProvider;
    private PropertyTypeDefinition showSubfolderMenuPropDef;
    private PropertyTypeDefinition showSubfolderTitlePropDef;

    /* Override if other searcher is needed. (Example: multihostSearcher) */
    protected ResultSet search(Resource collection, Query query, int offset) {

        Search search = new Search();

        if (RequestContext.getRequestContext().isPreviewUnpublished()) {
            search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
        }

        search.setQuery(query);
        search.setSorting(getSearchSorting(collection));

        ConfigurablePropertySelect propertySelect = getPropertySelect();
        if (propertySelect != null) {
            search.setPropertySelect(propertySelect);
        }

        search.setCursor(offset);
        search.setLimit(pageLimit);

        String token = SecurityContext.getSecurityContext().getToken();
        return RequestContext.getRequestContext().getRepository().search(token, search);
    }

    /* Override if other locations are needed. (Examples: Aggregation or prefix) */
    protected Query getLocationQuery(Resource collection) {
        return new UriPrefixQuery(collection.getURI().toString(), false);
    }

    abstract protected Query buildBaseQuery(HttpServletRequest request, Map<String, Object> collectionSpecificValues,
            Resource collection);

    abstract protected Query buildFilterQuery(HttpServletRequest request, Map<String, Object> collectionSpecificValues,
            Resource collection, Map<String, List<String>> filters);

    abstract protected Map<String, List<String>> runFacetSearch(HttpServletRequest request,
            Map<String, Object> collectionSpecificValues, Resource collection, Query query,
            Map<String, List<String>> filters);

    private Query combineQueries(Query one, Query two) {
        AndQuery andQuery = new AndQuery();
        andQuery.add(one);
        andQuery.add(two);
        return andQuery;
    }

    /* Override if special filter handling is needed. */
    protected Map<String, List<String>> getFilters() {
        return filters;
    }

    /* Override if collection requires specific values for view. */
    protected Map<String, Object> getCollectionSpecificValues(Resource collection) {
        return new HashMap<>();
    }

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        final RequestContext rc = RequestContext.getRequestContext();
        Repository repository = rc.getRepository();
        String token = rc.getSecurityToken();
        Path uri = rc.getResourceURI();
        Resource collection = repository.retrieve(token, uri, false);

        Map<String, Object> model = new HashMap<>();
        Map<String, Object> collectionSpecificValues = getCollectionSpecificValues(collection);

        int page = ListingPager.getPage(request, ListingPager.UPCOMING_PAGE_PARAM);
        int offset = (page - 1) * pageLimit;

        Query locationQuery = getLocationQuery(collection);
        Query baseQuery = buildBaseQuery(request, collectionSpecificValues, collection);
        Query facetQuery = baseQuery != null ? combineQueries(locationQuery, baseQuery) : locationQuery;

        Map<String, List<String>> facets = runFacetSearch(request, collectionSpecificValues, collection, facetQuery,
                getFilters());

        Query filterQuery = buildFilterQuery(request, collectionSpecificValues, collection, facets);

        Query fullQuery = filterQuery != null ? combineQueries(facetQuery, filterQuery) : facetQuery;

        ResultSet rs = search(collection, fullQuery, offset);

        Map<String, Map<String, FilterURL>> urlFilters = new LinkedHashMap<>();
        if (facets != null) {
            Map<String, FilterURL> urlList;
            FilterURL filterUrl;

            String parameterKey;
            for (String filter : facets.keySet()) {
                List<String> parameterValues = facets.get(filter);
                urlList = new LinkedHashMap<>();
                parameterKey = filterNamespace + filter;

                URL url = ListingPager.removePagerParms(URL.create(request));
                filterUrl = new FilterURL(!request.getParameterMap().containsKey(parameterKey),
                        url.removeParameter(parameterKey));
                urlList.put("all", filterUrl);

                for (String parameter : parameterValues) {
                    url = ListingPager.removePagerParms(URL.create(request));
                    List<String> oldParameterValues = url.getParameters(parameterKey);
                    if (oldParameterValues != null && oldParameterValues.contains(parameter)) {
                        url.removeParameter(parameterKey);

                        for (String value : oldParameterValues) {
                            if (!value.equals(parameter)) {
                                url.addParameter(parameterKey, value);
                            }
                        }

                        filterUrl = new FilterURL(true, url);
                    } else {
                        url.addParameter(parameterKey, parameter);

                        filterUrl = new FilterURL(false, url);
                    }
                    urlList.put(parameter, filterUrl);
                }

                urlFilters.put(filter, urlList);
            }
        }

        Optional<ListingPager.Pagination> pagination
                = ListingPager.pagination(rs.getTotalHits(), getPageLimit(), URL.create(request), page);
        if (pagination.isPresent()) {
            List<ListingPagingLink> urls = pagination.get().pageThroughLinks();
            model.put("pageThroughUrls", urls);
        }

        Property showSubfolderMenu = collection.getProperty(showSubfolderMenuPropDef);
        if (showSubfolderMenu != null && showSubfolderMenu.getBooleanValue()) {
            model.put("showSubfolderMenu",
                    subFolderMenuProvider.getSubfolderMenuWithThreeGeneratedResultSets(collection, request));
            Property showSubfolderTitle = collection.getProperty(showSubfolderTitlePropDef);
            if (showSubfolderTitle != null) {
                model.put("showSubfolderTitle", showSubfolderTitle.getStringValue());
            }
        }

        if (customSearch != null) {
            model.put("customSearch", customSearch);
        }

        if (preResult != null) {
            model.put("preResult", preResult);
        }

        if (customFilters != null) {
            model.put("customFilters", customFilters);
        }

        if (customListing != null) {
            model.put("customListing", customListing);
        }

        model.put("filters", urlFilters);
        model.put("result", rs.getAllResults());
        model.put("page", page);
        model.put("from", offset + 1);
        model.put("to", offset + Math.min(pageLimit, rs.getSize()));
        model.put("total", rs.getTotalHits());
        model.put("collection", collection);
        model.put("collectionSpecificValues", collectionSpecificValues);

        return new ModelAndView(viewName, model);
    }

    protected class FilterResult {

        ResultSet rs;
        List<String> facets;
    }

    public class FilterURL {

        private final boolean marked;
        private final URL url;

        public FilterURL(boolean marked, URL url) {
            this.marked = marked;
            this.url = url;
        }

        public boolean getMarked() {
            return marked;
        }

        public URL getUrl() {
            return url;
        }
    }

    protected Map<String, List<String>> getRequestFilters(HttpServletRequest request, Map<String, List<String>> filters) {

        Map<String, List<String>> requestFilters = new HashMap<>();

        String[] parameterValues;
        for (String parameterKey : filters.keySet()) {
            parameterValues = request.getParameterValues(filterNamespace + parameterKey);
            if (parameterValues != null) {
                List<String> requestParameterValues = new ArrayList<>();
                for (String parameterValue : parameterValues) {
                    if (parameterValue != null && valueExistsInFilters(parameterKey, parameterValue, filters)) {
                        requestParameterValues.add(parameterValue);
                    }
                }
                if (!requestParameterValues.isEmpty()) {
                    requestFilters.put(parameterKey, requestParameterValues);
                }
            }
        }

        return requestFilters;
    }

    private boolean valueExistsInFilters(String parameterKey, String parameterValue, Map<String, List<String>> filters) {

        if (parameterKey != null && parameterKey.startsWith(filterNamespace)) {
            parameterKey = parameterKey.substring(filterNamespace.length());
        }

        if (filterWhitelistExceptions != null && filterWhitelistExceptions.contains(parameterKey)) {
            return true;
        }

        List<String> filter = filters.get(parameterKey);
        if (filter != null) {
            return filter.contains(parameterValue);
        }

        return false;
    }

    protected PropertyTypeDefinition getPropDef(String propertyName) {
        if (propertyName != null && propertyName.startsWith(filterNamespace)) {
            propertyName = propertyName.substring(filterNamespace.length());
        }

        Namespace ns = Namespace.STRUCTURED_RESOURCE_NAMESPACE;
        return resourceTypeTree.getPropertyTypeDefinition(ns, propertyName);
    }

    protected ConfigurablePropertySelect getPropertySelect() {
        ConfigurablePropertySelect propertySelect = null;
        if (configurablePropertySelectPointers != null && resourceTypeTree != null) {
            for (String propPointer : configurablePropertySelectPointers) {
                PropertyTypeDefinition ptd = resourceTypeTree.getPropertyDefinitionByName(propPointer);
                if (ptd != null) {
                    if (propertySelect == null) {
                        propertySelect = new ConfigurablePropertySelect();
                    }
                    propertySelect.addPropertyDefinition(ptd);
                }
            }
        }
        return propertySelect;
    }

    protected int getPageLimit() {
        return pageLimit;
    }

    protected Sorting getSearchSorting(Resource collection) {
        return new Sorting(defaultSearchSorting.getSortFields(collection));
    }

    @Required
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public void setCustomSearch(String customSearch) {
        this.customSearch = customSearch;
    }

    public void setPreResult(String preResult) {
        this.preResult = preResult;
    }

    public void setCustomFilters(String customFilters) {
        this.customFilters = customFilters;
    }

    public void setCustomListing(String customListing) {
        this.customListing = customListing;
    }

    public void setFilters(Map<String, List<String>> filters) {
        this.filters = Collections.unmodifiableMap(filters);
    }

    public void setPageLimit(int pageLimit) {
        if (pageLimit <= 0) {
            throw new IllegalArgumentException("Limit must be a positive integer");
        }
        this.pageLimit = pageLimit;
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    @Required
    public void setDefaultSearchSorting(SearchSorting defaultSearchSorting) {
        this.defaultSearchSorting = defaultSearchSorting;
    }

    @Required
    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }

    public void setConfigurablePropertySelectPointers(List<String> configurablePropertySelectPointers) {
        this.configurablePropertySelectPointers = configurablePropertySelectPointers;
    }

    public void setFilterWhitelistExceptions(List<String> filterWhitelistExceptions) {
        this.filterWhitelistExceptions = filterWhitelistExceptions;
    }

    @Required
    public void setSearcher(Searcher searcher) {
        this.searcher = searcher;
    }

    @Required
    public void setSubFolderMenuProvider(SubFolderMenuProvider subFolderMenuProvider) {
        this.subFolderMenuProvider = subFolderMenuProvider;
    }

    @Required
    public void setShowSubfolderMenuPropDef(PropertyTypeDefinition showSubfolderMenuPropDef) {
        this.showSubfolderMenuPropDef = showSubfolderMenuPropDef;
    }

    @Required
    public void setShowSubfolderTitlePropDef(PropertyTypeDefinition showSubfolderTitlePropDef) {
        this.showSubfolderTitlePropDef = showSubfolderTitlePropDef;
    }

}
