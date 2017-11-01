/* Copyright (c) 2012, 2014, University of Oslo, Norway
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
package vtk.web.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.ConfigurablePropertySelect;
import vtk.repository.search.PropertySortField;
import vtk.repository.search.ResourceSortField;
import vtk.repository.search.Search;
import vtk.repository.search.Searcher;
import vtk.repository.search.SortField;
import vtk.repository.search.Sorting;
import vtk.repository.search.query.AclReadForAllQuery;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.PropertyExistsQuery;
import vtk.repository.search.query.PropertyTermQuery;
import vtk.repository.search.query.Query;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.UriPrefixQuery;
import vtk.util.text.Json;
import vtk.web.RequestContext;
import vtk.web.search.SearchParser;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class BrokenLinksReport extends DocumentReporter {
    private PropertyTypeDefinition linkStatusPropDef;
    private PropertyTypeDefinition linkCheckPropDef;
    private PropertyTypeDefinition brokenLinksCountPropDef;
    private PropertyTypeDefinition sortPropDef;
    private PropertyTypeDefinition publishedPropDef;
    private PropertyTypeDefinition indexFilePropDef;
    private PropertyTypeDefinition unpublishedCollectionPropDef;

    public void setUnpublishedCollectionPropDef(PropertyTypeDefinition unpublishedCollectionPropDef) {
        this.unpublishedCollectionPropDef = unpublishedCollectionPropDef;
    }

    private SortField.Direction sortOrder;
    private SearchParser parser;
    private String queryFilterExpression;

    private final static String FILTER_READ_RESTRICTION_PARAM_NAME = "read-restriction";
    private final static String FILTER_READ_RESTRICTION_PARAM_DEFAULT_VALUE = "all";
    private final static String[] FILTER_READ_RESTRICTION_PARAM_VALUES = 
        { FILTER_READ_RESTRICTION_PARAM_DEFAULT_VALUE, "false", "true" };

    private final static String FILTER_LINK_TYPE_PARAM_NAME = "link-type";
    private final static String FILTER_LINK_TYPE_PARAM_DEFAULT_VALUE = "anchor-img";

    private final static String[] FILTER_LINK_TYPE_PARAM_VALUES = 
        { FILTER_LINK_TYPE_PARAM_DEFAULT_VALUE, "img", "anchor", "other" };

    private final static String FILTER_PUBLISHED_PARAM_NAME = "published";
    private final static String FILTER_PUBLISHED_PARAM_DEFAULT_VALUE = "true";
    private final static String[] FILTER_PUBLISHED_PARAM_VALUES = 
        { FILTER_PUBLISHED_PARAM_DEFAULT_VALUE, "false" };

    private final static String INCLUDE_PATH_PARAM_NAME = "include-path";
    private final static String EXCLUDE_PATH_PARAM_NAME = "exclude-path";
    
    private Service brokenLinksToTsvReportService;
    private int pageSize = 25;
   
    private void populateMap(String token, Resource resource, 
            Map<String, Object> result, HttpServletRequest request, boolean isCollectionView) {
        
        RequestContext requestContext = RequestContext.getRequestContext(request);
        URL reportURL = super.getReportService().urlConstructor(requestContext.getRequestURL())
                .withURI(resource.getURI())
                .withParameter(REPORT_TYPE_PARAM, getName())
                .constructURL();

        if (isCollectionView) {
            reportURL.addParameter(getAlternativeName(), "");
        }
        
        Map<String, List<FilterOption>> filters = new LinkedHashMap<>();

        String linkType = request.getParameter(FILTER_LINK_TYPE_PARAM_NAME);
        String published = request.getParameter(FILTER_PUBLISHED_PARAM_NAME);
        String readRestriction = request.getParameter(FILTER_READ_RESTRICTION_PARAM_NAME);

        if (linkType == null)
            linkType = FILTER_LINK_TYPE_PARAM_DEFAULT_VALUE;
        if (published == null)
            published = FILTER_PUBLISHED_PARAM_DEFAULT_VALUE;
        if (readRestriction == null)
            readRestriction = FILTER_READ_RESTRICTION_PARAM_DEFAULT_VALUE;

        result.put("linkType", linkType);

        // TODO: Refactor method and generalize for 1..infinity filters

        // Generate read restriction filter
        List<FilterOption> filterReadRestrictionOptions = new ArrayList<>();
        for (String param : FILTER_READ_RESTRICTION_PARAM_VALUES) {
            URL filterOptionURL = new URL(reportURL);
            filterOptionURL.addParameter(FILTER_READ_RESTRICTION_PARAM_NAME, param);
            filterOptionURL.addParameter(FILTER_PUBLISHED_PARAM_NAME, published);
            filterOptionURL.addParameter(FILTER_LINK_TYPE_PARAM_NAME, linkType);
            filterReadRestrictionOptions.add(new FilterOption(param, filterOptionURL,
                    param.equals(readRestriction) ? true : false));
        }

        // Generate link type filter
        List<FilterOption> filterLinkTypeOptions = new ArrayList<>();
        for (String param : FILTER_LINK_TYPE_PARAM_VALUES) {
            URL filterOptionURL = new URL(reportURL);
            filterOptionURL.addParameter(FILTER_LINK_TYPE_PARAM_NAME, param);
            filterOptionURL.addParameter(FILTER_PUBLISHED_PARAM_NAME, published);
            filterOptionURL.addParameter(FILTER_READ_RESTRICTION_PARAM_NAME, readRestriction);
            filterLinkTypeOptions.add(new FilterOption(param, filterOptionURL, param.equals(linkType) ? true : false));
        }

        // Generate published filter
        List<FilterOption> filterPublishedOptions = new ArrayList<>();
        for (String param : FILTER_PUBLISHED_PARAM_VALUES) {
            URL filterOptionURL = new URL(reportURL);
            filterOptionURL.addParameter(FILTER_PUBLISHED_PARAM_NAME, param);
            filterOptionURL.addParameter(FILTER_READ_RESTRICTION_PARAM_NAME, readRestriction);
            filterOptionURL.addParameter(FILTER_LINK_TYPE_PARAM_NAME, linkType);
            filterPublishedOptions
                    .add(new FilterOption(param, filterOptionURL, param.equals(published) ? true : false));
        }

        filters.put(FILTER_PUBLISHED_PARAM_NAME, filterPublishedOptions);
        filters.put(FILTER_LINK_TYPE_PARAM_NAME, filterLinkTypeOptions);
        filters.put(FILTER_READ_RESTRICTION_PARAM_NAME, filterReadRestrictionOptions);

        result.put("filters", filters);
    }
    
    @Override
    public Map<String, Object> getReportContent(HttpServletRequest request, String token, Resource resource) {
        
        Map<String, Object> result = new HashMap<>();
        
        if (request.getParameter(getAlternativeName()) == null) {
            /* Regular view */
            result = super.getReportContent(request, token, resource);
            
            populateMap(token, resource, result, request, false);

            result.put("brokenLinkCount", getBrokenLinkCount(
                    token, resource, request, (String) result.get("linkType")));
            
        }
        else {
            /* Collection view */
            result.put(REPORT_NAME, getAlternativeName());

            populateMap(token, resource, result, request, true);

            Accumulator accumulator = getBrokenLinkAccumulator(
                    token, resource, request, (String) result.get("linkType"));

            int page = 1;
            try {
                page = Integer.parseInt(request.getParameter("page"));
            }
            catch (Exception e) { }

            Map<String, CollectionStats> map = new LinkedHashMap<>();
            if ((pageSize * page) - pageSize < accumulator.map.size()) {
                URL currentPage = URL.create(request).removeParameter("page");
                if (page > 1) {
                    result.put("prev", new URL(currentPage).addParameter(
                            "page", String.valueOf(page - 1)));
                }
                if (pageSize * page < accumulator.map.size()) {
                    result.put("next", new URL(currentPage).addParameter(
                            "page", String.valueOf(page + 1)));
                }

                Iterator<Entry<String, CollectionStats>> it = accumulator.map.entrySet().iterator();
                int count = 0;
                Resource r;
                Path uri;
                CollectionStats cs;
                while (it.hasNext() && ++count <= pageSize * page) {
                    Entry<String, CollectionStats> pairs = it.next();
                    if (count > (pageSize * page) - pageSize && count <= pageSize * page) {
                        cs = pairs.getValue();
                        uri = Path.fromString(pairs.getKey());
                        
                        cs.url = getReportService().urlConstructor(URL.create(request))
                                .withURI(uri)
                                .constructURL()
                                .addParameter(REPORT_TYPE_PARAM, "broken-links");
                        
                        try {
                            r = repository.retrieve(token, uri, false);
                            cs.title = r.getTitle();
                        }
                        catch (Exception e) {
                            cs.title = uri.getName();
                        }
                        map.put(pairs.getKey(), cs);
                    }
                }
                result.put("map", map);
            }

            result.put("sum", accumulator.sum);
            result.put("documentSum", accumulator.documentSum);

            if (accumulator.sum > 0) {
                String linkType = request.getParameter(FILTER_LINK_TYPE_PARAM_NAME);
                String published = request.getParameter(FILTER_PUBLISHED_PARAM_NAME);
                String readRestriction = request.getParameter(FILTER_READ_RESTRICTION_PARAM_NAME);

                if (linkType == null)
                    linkType = FILTER_LINK_TYPE_PARAM_DEFAULT_VALUE;
                if (published == null)
                    published = FILTER_PUBLISHED_PARAM_DEFAULT_VALUE;
                if (readRestriction == null)
                    readRestriction = FILTER_READ_RESTRICTION_PARAM_DEFAULT_VALUE;

                Map<String, List<String>> usedFilters = new LinkedHashMap<>();
                usedFilters.computeIfAbsent(FILTER_LINK_TYPE_PARAM_NAME, k -> new ArrayList<>()).add(linkType);
                usedFilters.computeIfAbsent(FILTER_PUBLISHED_PARAM_NAME, k -> new ArrayList<>()).add(published);
                usedFilters.computeIfAbsent(FILTER_READ_RESTRICTION_PARAM_NAME, k -> new ArrayList<>()).add(readRestriction);

                URL exportURL = brokenLinksToTsvReportService.urlConstructor(URL.create(request))
                        .withURI(resource.getURI())
                        .withParameters(usedFilters)
                        .constructURL();

                String[] exclude = request.getParameterValues(EXCLUDE_PATH_PARAM_NAME);
                if (exclude != null)
                    for (String value : exclude)
                        exportURL.addParameter(EXCLUDE_PATH_PARAM_NAME, value);

                String[] include = request.getParameterValues(INCLUDE_PATH_PARAM_NAME);
                if (include != null)
                    for (String value : include)
                        exportURL.addParameter(INCLUDE_PATH_PARAM_NAME, value);

                result.put("brokenLinksToTsvReportService", exportURL);
            }
        }

        return result;
    }

    private int getBrokenLinkCount(String token, Resource currentResource, HttpServletRequest request,
            final String linkType) {
        // Set up search
        Search search = getSearch(token, currentResource, request);
        search.setLimit(Integer.MAX_VALUE);
        ConfigurablePropertySelect cfg = new ConfigurablePropertySelect();
        cfg.addPropertyDefinition(brokenLinksCountPropDef);
        cfg.setIncludeAcl(true);
        search.setPropertySelect(cfg);
        search.setSorting(null);

        // Set up include/exclude link types sum of broken links
        String[] includeTypes;
        String[] excludeTypes = new String[0];
        if (FILTER_LINK_TYPE_PARAM_DEFAULT_VALUE.equals(linkType) || linkType == null) {
            includeTypes = new String[] { "BROKEN_LINKS_ANCHOR", "BROKEN_LINKS_IMG" };
        }
        else if ("anchor".equals(linkType)) {
            includeTypes = new String[] { "BROKEN_LINKS_ANCHOR" };
        }
        else if ("img".equals(linkType)) {
            includeTypes = new String[] { "BROKEN_LINKS_IMG" };
        }
        else {
            includeTypes = new String[] { "BROKEN_LINKS" };
            excludeTypes = new String[] { "BROKEN_LINKS_IMG", "BROKEN_LINKS_ANCHOR" };
        }

        // Search callback which sums up broken link counts
        @SuppressWarnings("hiding")
        final class Accumulator implements Searcher.MatchCallback {
            int sum = 0;
            final String[] includeTypes;
            final String[] excludeTypes;

            Accumulator(String[] includeTypes, String[] excludeTypes) {
                this.includeTypes = includeTypes;
                this.excludeTypes = excludeTypes;
            }

            @Override
            public boolean matching(PropertySet propertySet) throws Exception {
                Property prop = propertySet.getProperty(brokenLinksCountPropDef);
                if (prop == null) {
                    //logger.info("Broken links count: " + propertySet.getURI() + ": null");
                    return true;
                }
                Json.MapContainer obj = prop.getJSONValue();
                for (String includeType : includeTypes) {
                    sum += obj.optIntValue(includeType).orElse(0);
                }
                for (String excludeType : excludeTypes) {
                    sum -= obj.optIntValue(excludeType).orElse(0);
                }
                return true;
            }
        }

        Accumulator accumulator = new Accumulator(includeTypes, excludeTypes);
        searcher.iterateMatching(token, search, accumulator);
        return accumulator.sum;
    }
    
    public Map<String, CollectionStats> getAccumulatorMap(String token, Resource currentResource,
            HttpServletRequest request) {
        String linkType = request.getParameter(FILTER_LINK_TYPE_PARAM_NAME);
        if (linkType == null)
            linkType = FILTER_LINK_TYPE_PARAM_DEFAULT_VALUE;

        return getBrokenLinkAccumulator(token, currentResource, request, linkType).map;
    }

    private Accumulator getBrokenLinkAccumulator(String token, Resource currentResource, HttpServletRequest request,
            final String linkType) {
        // Set up search
        Search search = getSearch(token, currentResource, request);
        search.setLimit(Integer.MAX_VALUE);
        ConfigurablePropertySelect cfg = new ConfigurablePropertySelect();
        cfg.addPropertyDefinition(brokenLinksCountPropDef);
        search.setPropertySelect(cfg);
        search.setSorting(null);

        // Set up include/exclude link types sum of broken links
        String[] includeTypes;
        String[] excludeTypes = new String[0];
        if (FILTER_LINK_TYPE_PARAM_DEFAULT_VALUE.equals(linkType) || linkType == null) {
            includeTypes = new String[] { "BROKEN_LINKS_ANCHOR", "BROKEN_LINKS_IMG" };
        }
        else if ("anchor".equals(linkType)) {
            includeTypes = new String[] { "BROKEN_LINKS_ANCHOR" };
        }
        else if ("img".equals(linkType)) {
            includeTypes = new String[] { "BROKEN_LINKS_IMG" };
        }
        else {
            includeTypes = new String[] { "BROKEN_LINKS" };
            excludeTypes = new String[] { "BROKEN_LINKS_IMG", "BROKEN_LINKS_ANCHOR" };
        }

        Map<String, CollectionStats> map = new TreeMap<>();
        for (Path uri : currentResource.getChildURIs()) {
            map.put(uri.toString(), new CollectionStats());
        }

        Accumulator accumulator = new Accumulator(includeTypes, excludeTypes, map,
                currentResource.getURI().getDepth() + 1);

        // No need to do search if no children
        if (map.isEmpty()) {
            return accumulator;
        }

        searcher.iterateMatching(token, search, accumulator);

        // Remove entries with value of 0 or less
        Iterator<Map.Entry<String, CollectionStats>> iter = accumulator.map.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, CollectionStats> entry = iter.next();
            if (entry.getValue().linkCount <= 0) {
                iter.remove();
            }
        }

        return accumulator;
    }

    public class CollectionStats {
        int documentCount;
        int linkCount;
        String title;
        URL url;

        public CollectionStats() {
            this(0, 0);
        }

        public CollectionStats(int documentCount, int linkCount) {
            this.documentCount = documentCount;
            this.linkCount = linkCount;
        }

        public int getLinkCount() {
            return linkCount;
        }

        public int getDocumentCount() {
            return documentCount;
        }

        public String getTitle() {
            return title;
        }

        public URL getUrl() {
            return url;
        }
    }

    // Search callback which sums up broken link counts
    final class Accumulator implements Searcher.MatchCallback {
        int sum = 0, documentSum = 0;
        int depth, count, optInt;
        final String[] includeTypes;
        final String[] excludeTypes;
        Map<String, CollectionStats> map;

        Accumulator(String[] includeTypes, String[] excludeTypes, 
                Map<String, CollectionStats> map, int depth) {
            this.depth = depth;
            this.includeTypes = includeTypes;
            this.excludeTypes = excludeTypes;
            this.map = map;
        }

        @Override
        public boolean matching(PropertySet propertySet) throws Exception {
            Property prop = propertySet.getProperty(brokenLinksCountPropDef);
            if (prop == null) {
                return true;
            }

            if (propertySet.getURI().getDepth() == depth) {
                return true;
            }

            CollectionStats cs = map.get(propertySet.getURI().getPath(depth).toString());
            if (cs == null) {
                return true;
            }

            count = 0;
            Json.MapContainer obj = prop.getJSONValue();
            for (String includeType : includeTypes) {
                optInt = obj.optIntValue(includeType).orElse(0);
                sum += optInt;
                cs.linkCount += optInt;
                count += optInt;
            }
            for (String excludeType : excludeTypes) {
                optInt = obj.optIntValue(excludeType).orElse(0);
                sum -= optInt;
                cs.linkCount -= optInt;
                count -= optInt;
            }
            if (count > 0) {
                cs.documentCount++;
                documentSum++;
            }

            return true;
        }
    }

    @Override
    protected Search getSearch(String token, Resource currentResource, HttpServletRequest request) {
        OrQuery linkStatusCriteria = new OrQuery();
        String linkType = request.getParameter(FILTER_LINK_TYPE_PARAM_NAME);

        if (FILTER_LINK_TYPE_PARAM_DEFAULT_VALUE.equals(linkType) || linkType == null) {
            linkStatusCriteria
                    .add(new PropertyTermQuery(
                            linkStatusPropDef, "BROKEN_LINKS_ANCHOR", TermOperator.EQ));
            linkStatusCriteria.add(new PropertyTermQuery(
                    linkStatusPropDef, "BROKEN_LINKS_IMG", TermOperator.EQ));
        }
        else if ("anchor".equals(linkType)) {
            linkStatusCriteria
                    .add(new PropertyTermQuery(
                            linkStatusPropDef, "BROKEN_LINKS_ANCHOR", TermOperator.EQ));
        }
        else if ("img".equals(linkType)) {
            linkStatusCriteria.add(new PropertyTermQuery(
                    linkStatusPropDef, "BROKEN_LINKS_IMG", TermOperator.EQ));
        }
        else {
            AndQuery and = new AndQuery();
            and.add(new PropertyTermQuery(
                    linkStatusPropDef, "BROKEN_LINKS", TermOperator.EQ));
            and.add(new PropertyTermQuery(
                    linkStatusPropDef, "BROKEN_LINKS_ANCHOR", TermOperator.NE));
            and.add(new PropertyTermQuery(
                    linkStatusPropDef, "BROKEN_LINKS_IMG", TermOperator.NE));
            linkStatusCriteria.add(and);
        }
        linkStatusCriteria.add(new PropertyTermQuery(
                linkStatusPropDef, "AWAITING_LINKCHECK", TermOperator.EQ));

        AndQuery topLevelQ = new AndQuery();

        // Read restriction (all|true|false)
        String readRestriction = request.getParameter(FILTER_READ_RESTRICTION_PARAM_NAME);

        if (readRestriction != null) {
            if ("true".equals(readRestriction)) {
                AclReadForAllQuery aclReadForAllQ = new AclReadForAllQuery(true);
                topLevelQ.add(aclReadForAllQ);
            }
            else if ("false".equals(readRestriction)) {
                AclReadForAllQuery aclReadForAllQ = new AclReadForAllQuery();
                topLevelQ.add(aclReadForAllQ);
            }
        }

        OrQuery uriQuery = new OrQuery();
        uriQuery.add(new UriPrefixQuery(currentResource.getURI().toString()));

        String[] includes = request.getParameterValues(INCLUDE_PATH_PARAM_NAME);
        if (includes != null) {
            for (String s : includes) {
                uriQuery.add(new UriPrefixQuery(s));
            }
        }
        topLevelQ.add(uriQuery).add(linkStatusCriteria);

        String[] excludes = request.getParameterValues(EXCLUDE_PATH_PARAM_NAME);
        if (excludes != null) {
            for (String s : excludes) {
                try {
                    topLevelQ.add(new UriPrefixQuery(s, true));
                }
                catch (Throwable t) { }
            }
        }

        // Add clauses for any configured default filter query
        Query filterQ = getFilterQuery(request);
        if (filterQ != null) {
            topLevelQ.add(filterQ);
        }

        // Don't include collections with index files:
        topLevelQ.add(new PropertyExistsQuery(indexFilePropDef, true));

        Search search = new Search();
        search.setQuery(topLevelQ);
        Sorting sorting = new Sorting();

        if (sortPropDef == null) {
            sorting.addSortField(new ResourceSortField(PropertySet.URI_IDENTIFIER, sortOrder));
        }
        else {
            sorting.addSortField(new PropertySortField(sortPropDef, sortOrder));
        }
        search.setSorting(sorting);

        // Published (true|false)
        String published = request.getParameter(FILTER_PUBLISHED_PARAM_NAME);
        if (currentResource.getProperty(unpublishedCollectionPropDef) != null) {
            search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED);
            PropertyTermQuery ptq = null;
            if ("false".equals(published)) {
                ptq = new PropertyTermQuery(
                        publishedPropDef, "true", TermOperator.NE);
            }
            else {
                ptq = new PropertyTermQuery(
                        publishedPropDef, "true", TermOperator.EQ);
            }
            topLevelQ.add(ptq);
        }
        else if (published != null && "false".equals(published)) {
            // ONLY those NOT published
            search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED);
            PropertyTermQuery ptq = new PropertyTermQuery(
                    publishedPropDef, "true", TermOperator.NE);
            topLevelQ.add(ptq);
        }

        return search;
    }

    @Override
    protected void handleResult(PropertySet resource, Map<String, Object> model) {
        Property linkCheck = resource.getProperty(linkCheckPropDef);
        if (linkCheck == null) return;
        
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) model.get("linkCheck");
        if (map == null) {
            map = new HashMap<>();
            model.put("linkCheck", map);
        }

        map.put(resource.getURI().toString(), linkCheck.getJSONValue());
    }

    public static class FilterOption {
        private String name;
        private URL url;
        private boolean active;

        public FilterOption(String name, URL url, boolean active) {
            this.name = name;
            this.url = url;
            this.active = active;
        }

        public String getName() {
            return name;
        }

        public URL getURL() {
            return url;
        }

        public boolean isActive() {
            return this.active;
        }
    }

    private Query getFilterQuery(HttpServletRequest request) {
        if (queryFilterExpression != null) {
            if (parser == null) {
                throw new IllegalStateException(
                        "parser must be configured when using queryFilterExpression");
            }
            return parser.parser(request).parse(queryFilterExpression);
        }
        return null;
    }

    @Required
    public void setLinkStatusPropDef(PropertyTypeDefinition linkStatusPropDef) {
        this.linkStatusPropDef = linkStatusPropDef;
    }

    @Required
    public void setLinkCheckPropDef(PropertyTypeDefinition linkCheckPropDef) {
        this.linkCheckPropDef = linkCheckPropDef;
    }

    @Required
    public void setBrokenLinksCountPropDef(PropertyTypeDefinition def) {
        this.brokenLinksCountPropDef = def;
    }

    @Required
    public void setPublishedPropDef(PropertyTypeDefinition publishedPropDef) {
        this.publishedPropDef = publishedPropDef;
    }

    @Required
    public void setIndexFilePropDef(PropertyTypeDefinition indexFilePropDef) {
        this.indexFilePropDef = indexFilePropDef;
    }

    public void setSortPropDef(PropertyTypeDefinition sortPropDef) {
        this.sortPropDef = sortPropDef;
    }

    @Required
    public void setSortOrder(SortField.Direction sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setParser(SearchParser parser) {
        this.parser = parser;
    }

    public void setQueryFilterExpression(String exp) {
        this.queryFilterExpression = exp;
    }
    
    @Required
    public void setBrokenLinksToTsvReportService(Service brokenLinksToTsvReportService) {
        this.brokenLinksToTsvReportService = brokenLinksToTsvReportService;
    }
    
    @Override
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
