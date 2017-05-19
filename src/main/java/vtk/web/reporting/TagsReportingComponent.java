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
package vtk.web.reporting;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;


import org.springframework.beans.factory.annotation.Required;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.ResourceTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.search.ConfigurablePropertySelect;
import vtk.repository.search.QueryException;
import vtk.repository.search.Search;
import vtk.repository.search.Searcher;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.PropertyExistsQuery;
import vtk.repository.search.query.Query;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.TypeTermQuery;
import vtk.repository.search.query.UriPrefixQuery;
import vtk.util.cache.SimpleCache;
import vtk.web.RequestContext;
import vtk.web.display.collection.aggregation.AggregationResolver;
import vtk.web.search.SearchComponentQueryBuilder;

public class TagsReportingComponent {

    private Searcher searcher;
    private PropertyTypeDefinition tagsPropDef = null;
    private AggregationResolver aggregationResolver;
    private boolean caseInsensitive = true;
    private Optional<SimpleCache<String, List<TagFrequency>>> cache = Optional.empty();
    private String staticToken = null;
    private boolean useStaticToken = false;
    private Map<String, List<SearchComponentQueryBuilder>> resourceTypeQueries;

    public static final class TagFrequency implements Serializable {

        private static final long serialVersionUID = -8618894865163460399L;
        private final String tag;
        private int frequency;

        private TagFrequency(String tag, int frequency) {
            this.tag = tag;
            this.frequency = frequency;
        }

        public String getTag() {
            return this.tag;
        }

        public int getFrequency() {
            return this.frequency;
        }

        @Override
        public String toString() {
            return tag + ":" + frequency;
        }

        private void increment() {
            ++this.frequency;
        }
    }

    /**
     * Get list of TagFrequency instances for the given report criteria. The
     * list will always be sorted by frequency in descending order.
     * 
     * @param scopeUri
     * @param resourceTypeDefs
     * @param limit
     * @param tagOccurenceMin
     * @param requestSecurityToken 
     * @return a list of {@link TagFrequency} objects.
     * @throws java.io.IOException
     * @throws QueryException
     */
    public List<TagFrequency> getTags(Path scopeUri, List<ResourceTypeDefinition> resourceTypeDefs, int limit,
            int tagOccurenceMin, String requestSecurityToken) throws IOException  {
        return getTags(scopeUri, resourceTypeDefs, limit, tagOccurenceMin, requestSecurityToken, null);
    }

    /**
     * Get list of TagFrequency instances for the given report criteria. The
     * list will always be sorted by frequency in descending order.
     * 
     * @param scopeUri
     * @param resourceTypeDefs
     * @param limit
     * @param tagOccurenceMin
     * @param requestSecurityToken 
     * @param whiteList 
     * @return a list of {@link TagFrequency} objects.
     * @throws java.io.IOException
     * @throws QueryException
     */
    @SuppressWarnings("unchecked")
    public List<TagFrequency> getTags(Path scopeUri, List<ResourceTypeDefinition> resourceTypeDefs, int limit,
            int tagOccurenceMin, String requestSecurityToken, Set<String> whiteList) throws IOException  {

        final String token = useStaticToken ? this.staticToken : requestSecurityToken;

        Set<String> rtNames = resourceTypeDefs == null ? Collections.emptySet()
                : resourceTypeDefs.stream().map(d -> d.getName()).collect(Collectors.toSet());

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repo = requestContext.getRepository();
        Resource scopeResource = repo.retrieve(token, scopeUri, true);

        Query pathScopeQuery = null;
        if (scopeUri != null && !scopeUri.isRoot()) {

            Set<Path> aggregationPaths = null;
            if (aggregationResolver != null) {
                aggregationPaths = aggregationResolver.getAggregationPaths(scopeUri);
            }

            if (aggregationPaths == null) {
                pathScopeQuery = new UriPrefixQuery(scopeUri.toString());
            } else {
                OrQuery or = new OrQuery();
                or.add(new UriPrefixQuery(scopeUri.toString()));
                for (Path p : aggregationPaths) {
                    or.add(new UriPrefixQuery(p.toString()));
                }
                pathScopeQuery = or;
            }

        }

        Query typeScopeQuery = null;
        if (rtNames != null && !rtNames.isEmpty()) {

            if (rtNames.size() == 1) {
                typeScopeQuery = getTypeScopeQuery(rtNames.iterator().next(), scopeResource,
                        requestContext.getServletRequest());
            } else {
                OrQuery or = new OrQuery();
                for (String rtName : rtNames) {
                    // Consider TermOperator.IN to get hierarchical type support
                    or.add(getTypeScopeQuery(rtName, scopeResource, requestContext.getServletRequest()));
                }
                typeScopeQuery = or;
            }

        }

        // Build complete query tree
        final Query topLevel;
        if (typeScopeQuery != null || pathScopeQuery != null) {
            AndQuery and = new AndQuery();
            and.add(new PropertyExistsQuery(tagsPropDef, false));
            if (pathScopeQuery != null) {
                and.add(pathScopeQuery);
            }
            if (typeScopeQuery != null) {
                and.add(typeScopeQuery);
            }
            topLevel = and;
        } else {
            topLevel = new PropertyExistsQuery(tagsPropDef, false);
        }

        final String cacheKey = makeCacheKey(token, topLevel, limit, tagOccurenceMin, whiteList);
        List<TagFrequency> result = lookupCached(cacheKey);
        if (result != null) {
            return result;
        }

        // Set up index search
        Search search = new Search();
        if (RequestContext.getRequestContext().isPreviewUnpublished()) {
            search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
        }
        search.setQuery(topLevel);
        search.setSorting(null);
        search.setLimit(Integer.MAX_VALUE);
        search.setPropertySelect(new ConfigurablePropertySelect(tagsPropDef));

        final Map<String, TagFrequency> tagFreqMap = new HashMap<>();
        // Execute index iteration and collect/aggregate tag frequencies
        searcher.iterateMatching(token, search, p -> {
            Property tags = p.getProperty(tagsPropDef);
            if (tags != null) {
                if (tagsPropDef.isMultiple()) {
                    for (Value value : tags.getValues()) {
                        if (whiteList == null || whiteList.contains(value.getStringValue().toLowerCase())) {
                            tagFreqMap.computeIfAbsent(value.getStringValue(), t -> new TagFrequency(t, 0)).increment();
                        }
                    }
                } else {
                    Value value = tags.getValue();
                    if (whiteList == null || whiteList.contains(value.getStringValue().toLowerCase())) {
                        tagFreqMap.computeIfAbsent(value.getStringValue(), t -> new TagFrequency(t, 0)).increment();
                    }
                }
            }
            return true;
        });

        result = (caseInsensitive ?
                consolidateCaseVariations(tagFreqMap.values().stream()) : tagFreqMap.values().stream())
                .filter(tf -> tf.frequency >= tagOccurenceMin)
                .sorted((tf1,tf2) -> -1*Integer.compare(tf1.frequency, tf2.frequency))
                .limit(limit > -1 ? limit : Long.MAX_VALUE)
                .collect(Collectors.toList());

        return cacheResult(cacheKey, result);
    }

    private Query getTypeScopeQuery(String rtName, Resource scopeResource, HttpServletRequest servletRequest) {
        Query typeScopeQuery = new TypeTermQuery(rtName, TermOperator.EQ);
        List<SearchComponentQueryBuilder> rtNameQueryBuilders = resourceTypeQueries.get(rtName);
        if (rtNameQueryBuilders != null) {
            AndQuery and = new AndQuery();
            for (SearchComponentQueryBuilder queryBuilder : rtNameQueryBuilders) {
                and.add(queryBuilder.build(scopeResource, servletRequest));
            }
            and.add(typeScopeQuery);
            typeScopeQuery = and;
        }
        return typeScopeQuery;
    }

    private Stream<TagFrequency> consolidateCaseVariations(Stream<TagFrequency> tagFreqs) {
        return tagFreqs.collect(
                Collectors.groupingBy(tf -> tf.tag.toLowerCase(), Collectors.toList()))
                .values().stream().map(variants -> {
                    int sum = 0, max = 0;
                    String mostCommon = "";
                    for (TagFrequency v : variants) {
                        sum += v.frequency;
                        if (v.frequency > max) {
                            mostCommon = v.tag;
                            max = v.frequency;
                        }
                    }
                    return new TagFrequency(mostCommon, sum);
                });
    }
    
    private List<TagFrequency> cacheResult(String cacheKey, List<TagFrequency> result) {
        if (cache.isPresent()) {
            cache.get().put(cacheKey, result);
        }
        return result;
    }

    private List<TagFrequency> lookupCached(String cacheKey) {
        if (!cache.isPresent()) {
            return null;
        }
        return cache.get().get(cacheKey);
    }

    private String makeCacheKey(String token, Query q, int limit, int minFreq, Set<String> whiteList) {
        return "TagsReportingCacheKey{" + "limit=" + limit + ", minFreq=" + minFreq + ", token=" + token + ", q=" + q
                + (whiteList != null ? ", whitelist=" + whiteList.toString() : "") + "}";
    }

    @Required
    public void setTagsPropDef(PropertyTypeDefinition tagsPropDef) {
        this.tagsPropDef = tagsPropDef;
    }

    @Required
    public void setSearcher(Searcher searcher) {
        this.searcher = searcher;
    }

    public void setAggregationResolver(AggregationResolver aggregationResolver) {
        this.aggregationResolver = aggregationResolver;
    }

    /**
     * Set an optional cache instance for tags reporting to use.
     * @param cache 
     */
    public void setCache(SimpleCache<String,List<TagFrequency>> cache) {
        this.cache = Optional.of(cache);
    }

    /**
     * Optionally set value of static security token.
     *
     * <p>Default value is {@code null}.
     *
     * <p>Note that for the configured static token to actually be used, one
     * must enable the setting by calling {@link #setUseStaticToken(boolean) }.
     *
     * @param token the token, which may also be {@code null}.
     */
    public void setStaticToken(String token) {
        this.staticToken = token;
    }

    /**
     * Set whether configured static security token should be preferred for all
     * repository queries, instead of per-request tokens reflecting individual users.
     *
     * <p>The advantage of using a static token is that the same set of tags
     * will be visible to all users, regardless of repository permissions,
     * and the caching mechanism, if configured, will be more effective.
     *
     * <p>A possible disadvantage is that any user may see tag values set on resources
     * which they may not have permission to read, depending on which principal
     * the static security token maps to.
     *
     * <p>Default value is {@code false}.
     *
     * @param use {@code true} to enable use of static security token
     */
    public void setUseStaticToken(boolean use) {
        this.useStaticToken = use;
    }

    public void setCaseInsensitive(boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
    }

    public void setResourceTypeQueries(Map<String, List<SearchComponentQueryBuilder>> resourceTypeQueries) {
        this.resourceTypeQueries = resourceTypeQueries;
    }

}
