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
 *      * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
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
package vtk.web.search.collectionlisting;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import vtk.repository.MultiHostSearcher;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.search.ConfigurablePropertySelect;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.Sorting;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.Query;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.UriPrefixQuery;
import vtk.repository.search.query.UriSetQuery;
import vtk.web.RequestContext;
import vtk.web.display.collection.aggregation.AggregationResolver;
import vtk.web.display.collection.aggregation.CollectionListingAggregatedResources;
import vtk.web.search.ListingUriQueryBuilder;
import vtk.web.search.QueryPartsSearchComponent;
import vtk.web.search.SearchComponentQueryBuilder;
import vtk.web.search.VHostScopeQueryRestricter;
import vtk.web.service.URL;

/**
 * 
 * Resolves and includes aggregation in search.
 * 
 * If resolved aggregation set requires search across hosts, and mulit host
 * search is enabled (necessary solr extensions are included in config), the
 * resolved aggregation set is cached for 10mins, using a cache key comprised of
 * [name] of search component performing search, [path] to resource requesting
 * search, [last modified date] of resource and [security token].
 * 
 */
public class CollectionListingSearchComponent extends QueryPartsSearchComponent {

    private static Log logger = LogFactory.getLog(CollectionListingSearchComponent.class);

    private AggregationResolver aggregationResolver;
    private MultiHostSearcher multiHostSearcher;
    private ListingUriQueryBuilder listingUriQueryBuilder;
    private Ehcache cache;

    @Override
    protected ResultSet getResultSet(HttpServletRequest request, Resource collection, String token, Sorting sorting,
            int searchLimit, int offset, ConfigurablePropertySelect propertySelect) {

        // Check cache for aggregation set containing ref to other hosts
        String lastModified = collection.getPropertiesLastModified().toString();
        CollectionListingCacheKey cacheKey = new CollectionListingCacheKey(getName(), collection.getURI(),
                lastModified, token);
        Element cached = cache.get(cacheKey);
        Object cachedObj = cached != null ? cached.getObjectValue() : null;

        URL localHostBaseURL = viewService.constructURL(Path.ROOT);

        boolean isMultiHostSearch = false; // Mark if multi host search required
        boolean isCached = false; // Mark if retrieved from cache
        CollectionListingAggregatedResources clar = null;
        if (cachedObj != null) {
            clar = (CollectionListingAggregatedResources) cachedObj;

            logger.info("Retrieved aggregation for " + collection.getURI() + " from cache:\n" + clar);

            isMultiHostSearch = true; // Aggregation set is only cached if multi
                                      // host search is required
            isCached = true;
        } else {
            clar = aggregationResolver.getAggregatedResources(collection);
            isMultiHostSearch = multiHostSearcher.isMultiHostSearchEnabled()
                    && (clar != null && clar.includesResourcesFromOtherHosts(localHostBaseURL));
        }

        ResultSet result = null;

        Query query = generateQuery(request, collection, clar, localHostBaseURL, isMultiHostSearch);

        Search search = new Search();
        if (RequestContext.getRequestContext().isPreviewUnpublished()) {
            search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
        }
        search.setQuery(query);
        search.setLimit(searchLimit);
        search.setCursor(offset);
        search.setSorting(sorting);

        if (propertySelect != null) {
            search.setPropertySelect(propertySelect);
        }

        if (isMultiHostSearch) {

            if (!isCached) {
                // Cache aggergation set
                logger.info("Caching aggreagtion for " + collection.getURI() + ", cacheKey: " + cacheKey);

                cache.put(new Element(cacheKey, clar));
            }
            try {
                result = multiHostSearcher.search(token, search);
            } catch (Exception e) {
                logger.error("An error occured while searching multiple hosts", e);
            }
        } else {
            Repository repository = RequestContext.getRequestContext().getRepository();
            result = repository.search(token, search);
        }

        return result;
    }

    /**
     * 
     * XXX This is where the trouble starts. We need to reconstruct the query
     * from all the different query generation mechanisms we have. We then need
     * to check if multi host search is required, and if so, restrict any
     * locally generated uri type query to the current host. This is just a
     * pain... Contact rezam if you honstly feel like you need to alter this
     * (which you don't).
     * 
     */
    private Query generateQuery(HttpServletRequest request, Resource collection,
            CollectionListingAggregatedResources clar, URL localHostBaseURL, boolean isMultiHostSearch) {

        // Basic listing uri query
        Query uriQuery = listingUriQueryBuilder.build(collection);
        if (uriQuery == null) {
            throw new IllegalArgumentException("An uri query must be supplied");
        }

        // Other queries that might be configured
        List<Query> additionalQueries = getAdditionalQueries(collection, request);
        Query aggregationQuery = clar == null ? null : clar.getAggregationQuery(localHostBaseURL, isMultiHostSearch);

        // No more to do, just return the listing uri query
        if (additionalQueries.isEmpty() && aggregationQuery == null) {
            if (isMultiHostSearch) {
                return VHostScopeQueryRestricter.vhostRestrictedQuery(uriQuery, localHostBaseURL);
            }
            return uriQuery;
        }

        // Different categories of additional queries
        List<Query> localOtherQueries = new ArrayList<Query>();
        List<Query> localIncludeUriQueries = new ArrayList<Query>();
        List<Query> localExcludeUriQueries = new ArrayList<Query>();
        for (Query localQuery : additionalQueries) {
            if (localQuery instanceof UriPrefixQuery) {
                if (((UriPrefixQuery) localQuery).isInverted()) {
                    localExcludeUriQueries.add(localQuery);
                } else {
                    localIncludeUriQueries.add(localQuery);
                }
            } else if (localQuery instanceof UriSetQuery) {
                if (((UriSetQuery) localQuery).getOperator().equals(TermOperator.NI)) {
                    localExcludeUriQueries.add(localQuery);
                } else {
                    localIncludeUriQueries.add(localQuery);
                }
            } else {
                localOtherQueries.add(localQuery);
            }
        }

        if (!localIncludeUriQueries.isEmpty()) {
            OrQuery includeUriOr = new OrQuery();
            includeUriOr.add(uriQuery);
            for (Query incQ : localIncludeUriQueries) {
                includeUriOr.add(incQ);
            }
            uriQuery = includeUriOr;
        }

        if (!localExcludeUriQueries.isEmpty()) {
            Query excludeQuery = null;
            if (localExcludeUriQueries.size() == 1) {
                excludeQuery = localExcludeUriQueries.get(0);
            } else {
                OrQuery excludeOr = new OrQuery();
                for (Query exQ : localExcludeUriQueries) {
                    excludeOr.add(exQ);
                }
                excludeQuery = excludeOr;
            }
            AndQuery excludeUriAnd = new AndQuery();
            excludeUriAnd.add(uriQuery);
            excludeUriAnd.add(excludeQuery);
            uriQuery = excludeUriAnd;
        }

        // Restrict the base listing uri query to the current (if multi host
        // search is required)
        if (isMultiHostSearch) {
            uriQuery = VHostScopeQueryRestricter.vhostRestrictedQuery(uriQuery, localHostBaseURL);
        }

        if (localOtherQueries.isEmpty() && aggregationQuery == null) {
            return uriQuery;
        }

        if (aggregationQuery != null) {
            OrQuery uriOr = new OrQuery();
            uriOr.add(uriQuery);
            uriOr.add(aggregationQuery);
            uriQuery = uriOr;
        }

        if (localOtherQueries.isEmpty()) {
            return uriQuery;
        }

        AndQuery and = new AndQuery();
        for (Query q : localOtherQueries) {
            and.add(q);
        }
        and.add(uriQuery);
        return and;
    }

    private List<Query> getAdditionalQueries(Resource collection, HttpServletRequest request) {
        List<Query> result = new ArrayList<Query>();
        if (queryBuilders != null) {
            for (SearchComponentQueryBuilder queryBuilder : queryBuilders) {
                Query q = queryBuilder.build(collection, request);
                if (q != null) {
                    result.add(q);
                }
            }
        }
        return result;
    }

    @Required
    public void setAggregationResolver(AggregationResolver aggregationResolver) {
        this.aggregationResolver = aggregationResolver;
    }

    @Required
    public void setMultiHostSearcher(MultiHostSearcher multiHostSearcher) {
        this.multiHostSearcher = multiHostSearcher;
    }

    @Required
    public void setListingUriQueryBuilder(ListingUriQueryBuilder listingUriQueryBuilder) {
        this.listingUriQueryBuilder = listingUriQueryBuilder;
    }

    @Required
    public void setCache(Ehcache cache) {
        this.cache = cache;
    }

}
