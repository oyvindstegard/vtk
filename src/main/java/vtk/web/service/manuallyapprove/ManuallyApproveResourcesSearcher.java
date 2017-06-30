/* Copyright (c) 2012???2015 University of Oslo, Norway
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
package vtk.web.service.manuallyapprove;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.MultiHostSearcher;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.ConfigurablePropertySelect;
import vtk.repository.search.PropertySortField;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.SortField;
import vtk.repository.search.Sorting;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.Query;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.TypeTermQuery;
import vtk.repository.search.query.UriPrefixQuery;
import vtk.repository.search.query.UriSetQuery;
import vtk.security.SecurityContext;
import vtk.web.RequestContext;
import vtk.web.display.collection.aggregation.AggregationResolver;
import vtk.web.display.collection.aggregation.CollectionListingAggregatedResources;
import vtk.web.search.MultiHostUtil;
import vtk.web.search.VHostScopeQueryRestricter;
import vtk.web.service.Service;
import vtk.web.service.URL;


/**
 * TODO searcher makes policy decisions on age and general limits. These should
 * probably all be parameterizable, either by method or config, and this class
 * should only be considered a slave DAO.
 */
public class ManuallyApproveResourcesSearcher {

    /**
     * Hard limit used for searches executed per manually approved location, which
     * collects candidate docs for the final aggregated list.
     */
    public static final int LOCATION_SEARCH_LIMIT = 1000;
    
    /**
     * The default maximum total number of manually approve resources returned. This
     * includes both approved and unapproved docs from all sources.
     */
    public static final int DEFAULT_MAX_MANUALLY_APPROVE_RESOURCES = 300;
    
    /**
     * Time limit on old unapproved documents in months.
     * <p>
     * Unapproved docs older than this limit (on publishing date) may be removed
     * if the total result is larger than the maximum allowed manually approved
     * resources.
     */
    public static final int UNAPPROVED_TIME_LIMIT_MONTHS = 12;

    private Service viewService;
    private AggregationResolver aggregationResolver;
    private MultiHostSearcher multiHostSearcher;
    private Map<String, String> listingResourceTypeMappingPointers;
    private List<String> configurablePropertySelectPointers;
    private ResourceTypeTree resourceTypeTree;
    private int maxManuallyApproveResources = DEFAULT_MAX_MANUALLY_APPROVE_RESOURCES;

    private PropertyTypeDefinition titlePropDef;
    private PropertyTypeDefinition publishDatePropDef;
    private PropertyTypeDefinition creationTimePropDef;
    
    private final Logger logger = LoggerFactory.getLogger(ManuallyApproveResourcesSearcher.class.getName());

    /**
     * <p>
     * @param collection
     * @param locations sources of docs for manual approval (URLs)
     * @param alreadyApproved set of already approved resources
     * @return
     * @throws Exception 
     */
    public List<ManuallyApproveResource> getManuallyApproveResources(Resource collection, Set<String> locations,
            Set<String> alreadyApproved) throws Exception {

        // The final product. Will be populated with search results.
        List<ManuallyApproveResource> result = new ArrayList<>();
        
        RequestContext requestContext = RequestContext.getRequestContext();

        Repository repository = requestContext.getRepository();
        String token = SecurityContext.getSecurityContext().getToken();
        URL localHostURL = viewService.urlConstructor(requestContext.getRequestURL())
                .withURI(Path.ROOT)
                .constructURL();
        
        ConfigurablePropertySelect propertySelect = null;
        if (this.configurablePropertySelectPointers != null && this.resourceTypeTree != null) {
            for (String propPointer : this.configurablePropertySelectPointers) {
                PropertyTypeDefinition ptd = this.resourceTypeTree.getPropertyDefinitionByName(propPointer);
                if (ptd != null) {
                    if (propertySelect == null) {
                        propertySelect = new ConfigurablePropertySelect();
                    }
                    propertySelect.addPropertyDefinition(ptd);
                }
            }
        }

        // Sort on publish date
        Sorting sorting = new Sorting();
        sorting.addSortField(new PropertySortField(this.publishDatePropDef, SortField.Direction.DESC));

        Query resourceTypeQuery = new TypeTermQuery("file", TermOperator.IN);
        String resourceTypePointer = this.listingResourceTypeMappingPointers.get(collection.getResourceType());
        if (resourceTypePointer != null) {
            resourceTypeQuery = new TypeTermQuery(resourceTypePointer, TermOperator.IN);
        }

        // Get all resources that are eligible for manual approval, all
        // separated on origin (location)
        Map<String, List<PropertySet>> resourceSet = new HashMap<>();

        // Get resources to manually approve
        for (String location : locations) {

            Optional<URL> locationURL = getLocaltionAsURL(location, localHostURL);
            if (!locationURL.isPresent()) continue;
            PropertySet resource = getResource(repository, token, 
                    locationURL.get(), localHostURL);
            if (resource == null) {
                // Nothing found
                continue;
            }

            CollectionListingAggregatedResources clar = aggregationResolver.getAggregatedResources(resource);

            boolean isOtherHostLocation = isOtherHostLocation(location, localHostURL);
            boolean isMultiHostSearch = multiHostSearcher.isMultiHostSearchEnabled()
                    && (clar.includesResourcesFromOtherHosts(localHostURL) || isOtherHostLocation);
            
            Query query = generateQuery(locationURL.get(), resourceTypeQuery, clar, localHostURL, isMultiHostSearch);

            Search search = new Search();
            if (RequestContext.getRequestContext().isPreviewUnpublished()) {
                search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
            }
            search.setQuery(query);
            search.setLimit(LOCATION_SEARCH_LIMIT);
            search.setSorting(sorting);
            if (propertySelect != null) {
                search.setPropertySelect(propertySelect);
            }
            ResultSet searchResults = null;
            if (isMultiHostSearch) {
                searchResults = multiHostSearcher.search(token, search);
                if (searchResults != null) {
                    searchResults = MultiHostUtil.resolveResultSetImageRefProperties(searchResults);
                }
            } else {
                searchResults = repository.search(token, search);
            }
            List<PropertySet> allResults = searchResults != null ? 
                    searchResults.getAllResults() : Collections.emptyList();
            resourceSet.put(location, allResults);

        }

        // Map search results to objects for view
        for (Entry<String, List<PropertySet>> entry : resourceSet.entrySet()) {
            String source = entry.getKey();
            List<PropertySet> resources = entry.getValue();
            for (PropertySet ps : resources) {
                URL url = getPropertySetURL(ps, localHostURL);
                boolean approved = alreadyApproved.contains(url.toString());
                ManuallyApproveResource m = mapPropertySetToManuallyApprovedResource(ps, localHostURL, source, approved);
                if (!result.contains(m)) {
                    result.add(m);
                }
            }
        }

        // Get any already approved resource where the source might be gone
        // (e.g. removed)
        Set<PropertySet> alreadyApprovedMissingSource = getAlreadyApprovedMissingSource(alreadyApproved, result,
                repository, token, localHostURL);
        for (PropertySet ps : alreadyApprovedMissingSource) {
            URL url = getPropertySetURL(ps, localHostURL);
            String source = url.relativeURL(url.getPath().getParent().toString()).toString();
            ManuallyApproveResource m = mapPropertySetToManuallyApprovedResource(ps, localHostURL, source, true);
            result.add(m);
        }

        // Sort total result by "publish-date DESC, title ASC"
        Collections.sort(result, new ManuallyApproveResourceComparator());
        
        if (logger.isDebugEnabled()) {
            logger.debug("Result list size before filtering: " + result.size());
        }
        // Filter out docs which are not already approved and older than limit, down to max size.
        filterOldUnapproved(result, this.maxManuallyApproveResources);
        if (logger.isDebugEnabled()) {
            logger.debug("Result list size after filtering: " + result.size());
        }

        // Enforce hard limit lastly
        if (result.size() > this.maxManuallyApproveResources) {
            result = new ArrayList<>(result.subList(0, this.maxManuallyApproveResources));
        }
        return result;
    }
    
    /**
     * Removes old and unapproved items from list, down to a target size.
     * 
     * <p>Starts filtering from the back of the list, and list is expected to
     * be pre-sorted by publish-date in descending order (oldest last).
     *
     * <p>
     * If there are no unapproved items older than
     * {@link #UNAPPROVED_TIME_LIMIT_MONTHS} in list, then this method will not
     * removing anything.
     * <p>
     * This method will unconditionally stop filtering if list becomes equal to
     * or smaller than parameter <code>targetSize</code> in size.
     *
     * @param resources list of manually approve resources that must be sorted
     * in descending order by {@link ManuallyApproveResource#getPublishDate() }.
     * @param targetSize target size of list. Filtering will stop when this target
     * size is reached, or if size is already equal to or less than target size.
     */
    private void filterOldUnapproved(List<ManuallyApproveResource> resources, int targetSize) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1*UNAPPROVED_TIME_LIMIT_MONTHS);
        final Date earliestDate = cal.getTime();
        ListIterator<ManuallyApproveResource> revIt = resources.listIterator(resources.size());
        while (revIt.hasPrevious() && resources.size() > targetSize) {
            ManuallyApproveResource m = revIt.previous();
            if (!m.isApproved()) {
                Date pubDate = m.getPublishDate();
                if (pubDate != null && pubDate.before(earliestDate)) {
                    revIt.remove();
                }
            }
        }
    }

    private Optional<URL> getLocaltionAsURL(String location, URL localHostURL) {
        Optional<URL> url = getAsURL(location);
        if (url.isPresent()) return url;
        try {
            Path localPath = Path.fromStringWithTrailingSlash(location);
            URL parsed = URL.parse(localHostURL.toString());
            parsed.setPath(localPath);
            return Optional.of(parsed);
        }
        catch (IllegalArgumentException iae) {
                return Optional.empty();
        }
    }

    private Query generateQuery(URL locationURL, Query resourceTypeQuery, CollectionListingAggregatedResources clar,
            URL localHostBaseURL, boolean isMultiHostSearch) {

        AndQuery and = new AndQuery();
        and.add(resourceTypeQuery);

        Query uriQuery = new UriPrefixQuery(locationURL.getPath().toString());
        if (isMultiHostSearch) {
            uriQuery = VHostScopeQueryRestricter.vhostRestrictedQuery(uriQuery, locationURL);
        }

        Query aggregationQuery = clar.getAggregationQuery(localHostBaseURL, isMultiHostSearch);
        if (aggregationQuery == null) {
            and.add(uriQuery);
        } else {
            OrQuery uriOr = new OrQuery();
            uriOr.add(uriQuery);
            uriOr.add(aggregationQuery);
            and.add(uriOr);
        }
        return and;
    }

    private PropertySet getResource(Repository repository, String token, URL url, URL localHostURL) {
        try {

            PropertySet resource = null;
            Path path = null;
            if (localHostURL.getHost().equals(url.getHost())) {
                path = url.getPath();
            }
            if (path != null) {
                resource = repository.retrieve(token, path, true);
            } else if (this.multiHostSearcher.isMultiHostSearchEnabled()) {
                resource = multiHostSearcher.retrieve(token, url);
                if (resource != null) {
                    resource = MultiHostUtil.resolveImageRefProperties(resource);
                }
            }

            if (resource == null) {
                return null;
            }
            return resource;
        } catch (ResourceNotFoundException rnfe) {
            // resource doesn'n exist, ignore
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private Set<PropertySet> getAlreadyApprovedMissingSource(Set<String> alreadyApproved,
            List<ManuallyApproveResource> result, Repository repository, String token, URL localHostURL) {

        Set<String> missingAlreadyApproved = new HashSet<>();
        for (String s : alreadyApproved) {
            Optional<URL> url = getAsURL(s);
            boolean found = false;
            for (ManuallyApproveResource m : result) {
                if (!url.isPresent()) continue;
                if (m.getUrl().equals(url.get())) {
                    found = true;
                }
            }
            if (!found) {
                missingAlreadyApproved.add(s);
            }
        }

        Set<PropertySet> alreadyApprovedResources = new HashSet<>();
        if (missingAlreadyApproved.size() > 0) {

            Set<String> localPathsAsStringSet = new HashSet<>();
            Set<URL> urls = new HashSet<>();

            for (String approved : missingAlreadyApproved) {

                Path localPath = getLocalPath(approved, localHostURL);
                if (localPath != null) {
                    localPathsAsStringSet.add(localPath.toString());
                } else {
                    Optional<URL> url = getAsURL(approved);
                    if (url.isPresent()) {
                        urls.add(url.get());
                    }
                }

            }

            if (!localPathsAsStringSet.isEmpty()) {
                UriSetQuery uriSetQuery = new UriSetQuery(localPathsAsStringSet, TermOperator.IN);
                Search search = new Search();
                if (RequestContext.getRequestContext().isPreviewUnpublished()) {
                    search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
                }
                search.setQuery(uriSetQuery);
                search.setSorting(null);
                ResultSet rs = repository.search(token, search);
                alreadyApprovedResources.addAll(rs.getAllResults());
            }

            if (!urls.isEmpty() && multiHostSearcher.isMultiHostSearchEnabled()) {

                Set<PropertySet> rs = multiHostSearcher.retrieve(token, urls);
                if (rs != null && rs.size() > 0) {
                    rs = MultiHostUtil.resolveSetImageRefProperties(rs);
                    alreadyApprovedResources.addAll(rs);
                }
            }

        }
        return alreadyApprovedResources;
    }

    private boolean isOtherHostLocation(String location, URL localHostURL) {
        Optional<URL> url = getAsURL(location);
        return !url.isPresent() || !url.get().getHost().equals(localHostURL.getHost());
    }

    private Path getLocalPath(String location, URL localHostURL) {

        try {
            URL url = URL.parse(location);
            if (url.getHost().equals(localHostURL.getHost())) {
                // Is an url ref to a resource on local host
                return url.getPath();
            }
            // Is an url to resource on some other host
            return null;
        } catch (Exception e) {
            // Not an url, continue and assume a path
        }

        try {
            return Path.fromStringWithTrailingSlash(location);
        } catch (IllegalArgumentException iae) {
            return null;
        }
    }

    private Optional<URL> getAsURL(String location) {
        try {
            return Optional.of(URL.parse(location));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private ManuallyApproveResource mapPropertySetToManuallyApprovedResource(PropertySet ps, URL localURL,
            String source, boolean approved) {
        String title = ps.getName();
        Property titleProp = ps.getProperty(this.titlePropDef);
        if (titleProp != null) {
            title = titleProp.getStringValue();
        }
        URL url = this.getPropertySetURL(ps, localURL);
        Property dateProp = ps.getProperty(this.publishDatePropDef);
        if (dateProp == null) {
            dateProp = ps.getProperty(this.creationTimePropDef);
        }
        Date publishDate = dateProp != null ? dateProp.getDateValue() : Calendar.getInstance().getTime();
        ManuallyApproveResource m = new ManuallyApproveResource(title, url, source, publishDate, approved);
        return m;
    }

    private URL getPropertySetURL(PropertySet ps, URL localURL) {
        URL url = null;
        Property urlProp = ps.getProperty(Namespace.DEFAULT_NAMESPACE, MultiHostSearcher.URL_PROP_NAME);
        if (urlProp != null) {
            url = URL.parse(urlProp.getStringValue());
        } else {
            url = new URL(localURL).relativeURL("/");
            url.setPath(ps.getURI());
        }
        return url;
    }

    @Required
    public void setListingResourceTypeMappingPointers(Map<String, String> listingResourceTypeMappingPointers) {
        this.listingResourceTypeMappingPointers = listingResourceTypeMappingPointers;
    }

    @Required
    public void setTitlePropDef(PropertyTypeDefinition titlePropDef) {
        this.titlePropDef = titlePropDef;
    }

    @Required
    public void setPublishDatePropDef(PropertyTypeDefinition publishDatePropDef) {
        this.publishDatePropDef = publishDatePropDef;
    }

    @Required
    public void setCreationTimePropDef(PropertyTypeDefinition creationTimePropDef) {
        this.creationTimePropDef = creationTimePropDef;
    }

    @Required
    public void setMultiHostSearcher(MultiHostSearcher multiHostSearcher) {
        this.multiHostSearcher = multiHostSearcher;
    }

    @Required
    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }

    @Required
    public void setAggregationResolver(AggregationResolver aggregationResolver) {
        this.aggregationResolver = aggregationResolver;
    }

    public void setConfigurablePropertySelectPointers(List<String> configurablePropertySelectPointers) {
        this.configurablePropertySelectPointers = configurablePropertySelectPointers;
    }

    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }
    
    /**
     * Set maximum number of manually approvable resources returned by this DAO.
     * This includes both already approved and unapproved URLs from all sources combined.
     * <p>Default value is {@link #DEFAULT_MAX_MANUALLY_APPROVE_RESOURCES}.
     * @param maxManuallyApproveResources 
     */
    public void setMaxManuallyApproveResources(int maxManuallyApproveResources) {
        if (maxManuallyApproveResources < 1) {
            throw new IllegalArgumentException("maxManuallyApproveResources must be > 0");
        }
        this.maxManuallyApproveResources = maxManuallyApproveResources;
    }

}
