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

import vtk.repository.ContentStream;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.ConfigurablePropertySelect;
import vtk.repository.search.Parser;
import vtk.repository.search.PropertySortField;
import vtk.repository.search.Search;
import vtk.repository.search.Searcher;
import vtk.repository.search.Searcher.MatchingResult;
import vtk.repository.search.SortFieldDirection;
import vtk.repository.search.Sorting;
import vtk.repository.search.TypedSortField;
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

    private SortFieldDirection sortOrder;
    private Parser parser;
    private String queryFilterExpression;

    private final static String FILTER_READ_RESTRICTION_PARAM_NAME = "read-restriction";
    private final static String FILTER_READ_RESTRICTION_PARAM_DEFAULT_VALUE = "all";
    private final static String[] FILTER_READ_RESTRICTION_PARAM_VALUES = { FILTER_READ_RESTRICTION_PARAM_DEFAULT_VALUE,
            "false", "true" };

    private final static String FILTER_LINK_TYPE_PARAM_NAME = "link-type";
    private final static String FILTER_LINK_TYPE_PARAM_DEFAULT_VALUE = "anchor-img";

    private final static String[] FILTER_LINK_TYPE_PARAM_VALUES = { FILTER_LINK_TYPE_PARAM_DEFAULT_VALUE, "img",
            "anchor", "other" };

    private final static String FILTER_PUBLISHED_PARAM_NAME = "published";
    private final static String FILTER_PUBLISHED_PARAM_DEFAULT_VALUE = "true";
    private final static String[] FILTER_PUBLISHED_PARAM_VALUES = { FILTER_PUBLISHED_PARAM_DEFAULT_VALUE, "false" };

    private final static String INCLUDE_PATH_PARAM_NAME = "include-path";
    private final static String EXCLUDE_PATH_PARAM_NAME = "exclude-path";
    
    private Service brokenLinksToTsvReportService;
    private int pageSize = 25;
   
    private void populateMap(String token, Resource resource, Map<String, Object> result, HttpServletRequest request, boolean isCollectionView) {
        URL reportURL = super.getReportService().constructURL(resource).addParameter(REPORT_TYPE_PARAM, getName());

        if(isCollectionView) {
            reportURL.addParameter(getAlternativeName(), "");
        }
        
        Map<String, List<FilterOption>> filters = new LinkedHashMap<String, List<FilterOption>>();

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
        List<FilterOption> filterReadRestrictionOptions = new ArrayList<FilterOption>();
        for (String param : FILTER_READ_RESTRICTION_PARAM_VALUES) {
            URL filterOptionURL = new URL(reportURL);
            filterOptionURL.addParameter(FILTER_READ_RESTRICTION_PARAM_NAME, param);
            filterOptionURL.addParameter(FILTER_PUBLISHED_PARAM_NAME, published);
            filterOptionURL.addParameter(FILTER_LINK_TYPE_PARAM_NAME, linkType);
            filterReadRestrictionOptions.add(new FilterOption(param, filterOptionURL,
                    param.equals(readRestriction) ? true : false));
        }

        // Generate link type filter
        List<FilterOption> filterLinkTypeOptions = new ArrayList<FilterOption>();
        for (String param : FILTER_LINK_TYPE_PARAM_VALUES) {
            URL filterOptionURL = new URL(reportURL);
            filterOptionURL.addParameter(FILTER_LINK_TYPE_PARAM_NAME, param);
            filterOptionURL.addParameter(FILTER_PUBLISHED_PARAM_NAME, published);
            filterOptionURL.addParameter(FILTER_READ_RESTRICTION_PARAM_NAME, readRestriction);
            filterLinkTypeOptions.add(new FilterOption(param, filterOptionURL, param.equals(linkType) ? true : false));
        }

        // Generate published filter
        List<FilterOption> filterPublishedOptions = new ArrayList<FilterOption>();
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
    public Map<String, Object> getReportContent(String token, Resource resource, HttpServletRequest request) {
        
        Map<String, Object> result = new HashMap<String, Object>();
        
        /* Regular view */
        if(request.getParameter(getAlternativeName()) == null) {
            result = super.getReportContent(token, resource, request);
            
            populateMap(token, resource, result, request, false);

            result.put("brokenLinkCount", getBrokenLinkCount(token, resource, request, (String) result.get("linkType")));
            
        /* Collection view */
        } else {
            result.put(REPORT_NAME, getAlternativeName());

            populateMap(token, resource, result, request, true);

            Accumulator accumulator = getBrokenLinkAccumulator(token, resource, request, (String) result.get("linkType"));

            int page = 1;
            try {
                page = Integer.parseInt(request.getParameter("page"));
            } catch (Exception e) {
            }

            Map<String, CollectionStats> map = new LinkedHashMap<String, CollectionStats>();
            if ((pageSize * page) - pageSize < accumulator.map.size()) {
                URL currentPage = URL.create(request).removeParameter("page");
                if (page > 1) {
                    result.put("prev", new URL(currentPage).addParameter("page", String.valueOf(page - 1)));
                }
                if (pageSize * page < accumulator.map.size()) {
                    result.put("next", new URL(currentPage).addParameter("page", String.valueOf(page + 1)));
                }

                Iterator<Entry<String, CollectionStats>> it = accumulator.map.entrySet().iterator();
                int count = 0;
                Resource r;
                Path uri;
                CollectionStats cs;
                while (it.hasNext() && ++count <= pageSize * page) {
                    Entry<String, CollectionStats> pairs = (Entry<String, CollectionStats>) it.next();
                    if (count > (pageSize * page) - pageSize && count <= pageSize * page) {
                        cs = pairs.getValue();
                        uri = Path.fromString(pairs.getKey());
                        cs.url = getReportService().constructURL(uri).addParameter(REPORT_TYPE_PARAM, "broken-links");
                        try {
                            r = repository.retrieve(token, uri, false);
                            cs.title = r.getTitle();
                        } catch (Exception e) {
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

                Map<String, String> usedFilters = new LinkedHashMap<String, String>();
                usedFilters.put(FILTER_LINK_TYPE_PARAM_NAME, linkType);
                usedFilters.put(FILTER_PUBLISHED_PARAM_NAME, published);
                usedFilters.put(FILTER_READ_RESTRICTION_PARAM_NAME, readRestriction);

                URL exportURL = this.brokenLinksToTsvReportService.constructURL(resource, null, usedFilters, false);

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
        cfg.addPropertyDefinition(this.brokenLinksCountPropDef);
        cfg.setIncludeAcl(true);
        search.setPropertySelect(cfg);
        search.setSorting(null);

        // Set up include/exclude link types sum of broken links
        String[] includeTypes;
        String[] excludeTypes = new String[0];
        if (FILTER_LINK_TYPE_PARAM_DEFAULT_VALUE.equals(linkType) || linkType == null) {
            includeTypes = new String[] { "BROKEN_LINKS_ANCHOR", "BROKEN_LINKS_IMG" };
        } else if ("anchor".equals(linkType)) {
            includeTypes = new String[] { "BROKEN_LINKS_ANCHOR" };
        } else if ("img".equals(linkType)) {
            includeTypes = new String[] { "BROKEN_LINKS_IMG" };
        } else {
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
            public boolean matching(MatchingResult result) throws Exception {
                PropertySet propertySet = result.propertySet();
                Property prop = propertySet.getProperty(brokenLinksCountPropDef);
                if (prop == null)
                    return true;
                Json.MapContainer obj = prop.getJSONValue();
                for (String includeType : this.includeTypes) {
                    sum += obj.optIntValue(includeType, 0);
                }
                for (String excludeType : this.excludeTypes) {
                    sum -= obj.optIntValue(excludeType, 0);
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
        cfg.addPropertyDefinition(this.brokenLinksCountPropDef);
        search.setPropertySelect(cfg);
        search.setSorting(null);

        // Set up include/exclude link types sum of broken links
        String[] includeTypes;
        String[] excludeTypes = new String[0];
        if (FILTER_LINK_TYPE_PARAM_DEFAULT_VALUE.equals(linkType) || linkType == null) {
            includeTypes = new String[] { "BROKEN_LINKS_ANCHOR", "BROKEN_LINKS_IMG" };
        } else if ("anchor".equals(linkType)) {
            includeTypes = new String[] { "BROKEN_LINKS_ANCHOR" };
        } else if ("img".equals(linkType)) {
            includeTypes = new String[] { "BROKEN_LINKS_IMG" };
        } else {
            includeTypes = new String[] { "BROKEN_LINKS" };
            excludeTypes = new String[] { "BROKEN_LINKS_IMG", "BROKEN_LINKS_ANCHOR" };
        }

        Map<String, CollectionStats> map = new TreeMap<String, CollectionStats>();
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

        Accumulator(String[] includeTypes, String[] excludeTypes, Map<String, CollectionStats> map, int depth) {
            this.depth = depth;
            this.includeTypes = includeTypes;
            this.excludeTypes = excludeTypes;
            this.map = map;
        }

        @Override
        public boolean matching(MatchingResult result) throws Exception {
            PropertySet propertySet = result.propertySet();
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
            for (String includeType : this.includeTypes) {
                optInt = obj.optIntValue(includeType, 0);
                sum += optInt;
                cs.linkCount += optInt;
                count += optInt;
            }
            for (String excludeType : this.excludeTypes) {
                optInt = obj.optIntValue(excludeType, 0);
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
                    .add(new PropertyTermQuery(this.linkStatusPropDef, "BROKEN_LINKS_ANCHOR", TermOperator.EQ));
            linkStatusCriteria.add(new PropertyTermQuery(this.linkStatusPropDef, "BROKEN_LINKS_IMG", TermOperator.EQ));
        } else if ("anchor".equals(linkType)) {
            linkStatusCriteria
                    .add(new PropertyTermQuery(this.linkStatusPropDef, "BROKEN_LINKS_ANCHOR", TermOperator.EQ));
        } else if ("img".equals(linkType)) {
            linkStatusCriteria.add(new PropertyTermQuery(this.linkStatusPropDef, "BROKEN_LINKS_IMG", TermOperator.EQ));
        } else {
            AndQuery and = new AndQuery();
            and.add(new PropertyTermQuery(this.linkStatusPropDef, "BROKEN_LINKS", TermOperator.EQ));
            and.add(new PropertyTermQuery(this.linkStatusPropDef, "BROKEN_LINKS_ANCHOR", TermOperator.NE));
            and.add(new PropertyTermQuery(this.linkStatusPropDef, "BROKEN_LINKS_IMG", TermOperator.NE));
            linkStatusCriteria.add(and);
        }
        linkStatusCriteria.add(new PropertyTermQuery(this.linkStatusPropDef, "AWAITING_LINKCHECK", TermOperator.EQ));

        AndQuery topLevelQ = new AndQuery();

        // Read restriction (all|true|false)
        String readRestriction = request.getParameter(FILTER_READ_RESTRICTION_PARAM_NAME);

        if (readRestriction != null) {
            if ("true".equals(readRestriction)) {
                AclReadForAllQuery aclReadForAllQ = new AclReadForAllQuery(true);
                topLevelQ.add(aclReadForAllQ);
            } else if ("false".equals(readRestriction)) {
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
                } catch (Throwable t) {
                }
            }
        }

        // Add clauses for any configured default filter query
        Query filterQ = getFilterQuery();
        if (filterQ != null) {
            topLevelQ.add(filterQ);
        }

        // Don't include collections with index files:
        topLevelQ.add(new PropertyExistsQuery(this.indexFilePropDef, true));

        Search search = new Search();
        search.setQuery(topLevelQ);
        Sorting sorting = new Sorting();

        if (this.sortPropDef == null) {
            sorting.addSortField(new TypedSortField(PropertySet.URI_IDENTIFIER, this.sortOrder));
        } else {
            sorting.addSortField(new PropertySortField(this.sortPropDef, this.sortOrder));
        }
        search.setSorting(sorting);

        // Published (true|false)
        String published = request.getParameter(FILTER_PUBLISHED_PARAM_NAME);
        if (currentResource.getProperty(unpublishedCollectionPropDef) != null) {
            search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED);
            PropertyTermQuery ptq = null;
            if ("false".equals(published)) {
                ptq = new PropertyTermQuery(this.publishedPropDef, "true", TermOperator.NE);
            } else {
                ptq = new PropertyTermQuery(this.publishedPropDef, "true", TermOperator.EQ);
            }
            topLevelQ.add(ptq);
        } else if (published != null && "false".equals(published)) {
            // ONLY those NOT published
            search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED);
            PropertyTermQuery ptq = new PropertyTermQuery(this.publishedPropDef, "true", TermOperator.NE);
            topLevelQ.add(ptq);
        }

        return search;
    }

    @Override
    protected void handleResult(PropertySet resource, Map<String, Object> model) {
        Property linkCheck = resource.getProperty(this.linkCheckPropDef);
        if (linkCheck == null) {
            // Try re-load from database to get binary prop (binary props not available in search results).
            try {
                String token = RequestContext.getRequestContext().getSecurityToken();
                resource = repository.retrieve(token, resource.getURI(), true);
                linkCheck = resource.getProperty(this.linkCheckPropDef);
            } catch (Exception e) {
            }
            if (linkCheck == null) {
                return;
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) model.get("linkCheck");
        if (map == null) {
            map = new HashMap<String, Object>();
            model.put("linkCheck", map);
        }

        ContentStream binaryStream = linkCheck.getBinaryStream();

        try {
            Json.MapContainer obj = Json.parseToContainer(binaryStream.getStream()).asObject();
            map.put(resource.getURI().toString(), obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
            return this.name;
        }

        public URL getURL() {
            return this.url;
        }

        public boolean isActive() {
            return this.active;
        }
    }

    private Query getFilterQuery() {
        if (this.queryFilterExpression != null) {
            if (this.parser == null) {
                throw new IllegalStateException("parser must be configured when using queryFilterExpression");
            }
            return this.parser.parse(this.queryFilterExpression);
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
    public void setSortOrder(SortFieldDirection sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setParser(Parser parser) {
        this.parser = parser;
    }

    public void setQueryFilterExpression(String exp) {
        this.queryFilterExpression = exp;
    }
    
    @Required
    public void setBrokenLinksToTsvReportService(Service brokenLinksToTsvReportService) {
        this.brokenLinksToTsvReportService = brokenLinksToTsvReportService;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
