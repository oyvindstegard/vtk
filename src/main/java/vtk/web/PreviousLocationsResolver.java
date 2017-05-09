/* Copyright (c) 2016, University of Oslo, Norway
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
package vtk.web;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.ConfigurablePropertySelect;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.query.PropertyTermQuery;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.UriSetQuery;
import vtk.util.text.Json.MapContainer;

public class PreviousLocationsResolver {
    // Resolve latest time stamps on the set of ancestor paths, 
    // or only on the resources found? (Requires an extra index search):
    private boolean pathTimestamps = true;
    private PropertyTypeDefinition locationHistoryPropDef;
    private PropertyTypeDefinition unpublishedCollectionPropDef;
    private Supplier<Repository> repo;
    private Supplier<String> token;

    public static class RelocatedResource {
        public final PropertySet resource;
        public final Optional<LocalDateTime> time;
        
        public RelocatedResource(PropertySet resource, Optional<LocalDateTime> time) {
            this.resource = resource;
            this.time = time;
        }
        
        public PropertySet getResource() { return resource; }
        public Optional<LocalDateTime> getTime() { return time; }
        
        @Override
        public String toString() {
            return getClass().getSimpleName() 
                    + "(" + resource + "," + time + ")";
        }        
    }
    
    public PreviousLocationsResolver(PropertyTypeDefinition locationHistoryPropDef,
            PropertyTypeDefinition unpublishedCollectionPropDef, 
            Supplier<Repository> repo, Supplier<String> token) {
        this.locationHistoryPropDef = locationHistoryPropDef;
        this.unpublishedCollectionPropDef = unpublishedCollectionPropDef;
        this.repo = repo;
        this.token = token;
    }

    public Collection<RelocatedResource> resolve(Path uri) throws Exception {
        Set<RelocatedResource> relocated = resolve(uri, new HashSet<>(), 5);
        if (pathTimestamps) {
            return resolveAncestorTimes(relocated);
        }
        return relocated;
    }
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
            DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");

    private Set<RelocatedResource> resolve(Path uri, Set<Path> seen, 
            int recursion) throws Exception {
        Set<RelocatedResource> result = new HashSet<>();
        if (recursion == 0) return result;
        if (uri.getDepth() > 10) return result;
        seen.add(uri);
        try {
            Resource resource = repo.get()
                    .retrieve(token.get(), uri, true);

            Property unpubCollection = resource
                    .getProperty(unpublishedCollectionPropDef);
            boolean published = resource.isPublished() && 
                    (unpubCollection == null || unpubCollection
                        .getBooleanValue() == false);
            
            if (published) {
                result.add(new RelocatedResource(resource, latestRelocation(resource)));
            }
        }
        catch (Throwable t) { }
        
        for (int i = uri.getDepth(); i > 0; i--) {
            Path left = uri.getPath(i);
            Path right = uri.right(left);

            List<RelocatedResource> prev = searchPreviousLocations(left);
            for (RelocatedResource item: prev) {
                Path next = item.resource.getURI().append(right);
                if (!seen.contains(next)) {
                    result.addAll(resolve(next, seen, recursion - 1));
                }
            }
        }
        return result;
    }

    private List<RelocatedResource> searchPreviousLocations(Path uri) {
        PropertyTermQuery termQuery = new PropertyTermQuery(
                locationHistoryPropDef, "locations.from_uri", uri.toString(), TermOperator.EQ);

        Search search = new Search();
        search.setQuery(termQuery);
        search.setSorting(null);
        search.setPropertySelect(new ConfigurablePropertySelect(
                Collections.singletonList(locationHistoryPropDef)));
        search.clearAllFilterFlags();
        search.setLimit(10);

        ResultSet results = repo.get()
                .search(token.get(), search);
        return results.getAllResults().stream()
                .map(resource -> new RelocatedResource(resource, latestRelocation(resource)))
                .collect(Collectors.toList());
    }

    private Optional<LocalDateTime> latestRelocation(PropertySet resource) {
        Property history = resource.getProperty(locationHistoryPropDef);
        if (history == null) {
            return Optional.empty();
        }
        MapContainer jsonValue = history.getJSONValue();
        List<Object> list = jsonValue.optArrayValue("locations", null);
        if (list == null || list.size() == 0) {
            return Optional.empty();
        }
        
        Map<?, ?> object = (Map<?, ?>) list.get(list.size() - 1);
        if (!object.containsKey("time")) {
            return Optional.empty();
        }
        try {
            LocalDateTime time = LocalDateTime.parse(
                    object.get("time").toString(), TIMESTAMP_FORMAT);
            return Optional.of(time);
        }
        catch (Exception e) { 
            return Optional.empty();
        }
    }

    
    private Set<RelocatedResource> resolveAncestorTimes(Set<RelocatedResource> resources) {
        if (resources.size() == 0) return resources;
        Set<String> uris = resources.stream().map(r -> r.resource.getURI())
                .flatMap(uri -> uri.getPaths().stream())
                .map(uri -> uri.toString())
                .collect(Collectors.toSet());
        UriSetQuery query = new UriSetQuery(uris, TermOperator.IN);
        Search search = new Search();
        search.setQuery(query);
        search.setPropertySelect(new ConfigurablePropertySelect(
                Collections.singletonList(locationHistoryPropDef)));

        search.clearAllFilterFlags();
        search.setLimit(30);
        
        ResultSet results = repo.get()
                .search(token.get(), search);
        Map<Path, Optional<LocalDateTime>> ancestorTimes = new HashMap<>();
        results.getAllResults()
            .forEach(r -> ancestorTimes.put(r.getURI(), latestRelocation(r)));

        Set<RelocatedResource> result = new HashSet<>();
        
        for (RelocatedResource r: resources) {
            RelocatedResource cur = r;
            for (Path path: r.resource.getURI().getAncestors()) {
                Optional<LocalDateTime> ancestorTime = ancestorTimes.get(path);
                if (cur.time.isPresent() && ancestorTime.isPresent()
                        && ancestorTime.get().isAfter(cur.time.get())) {
                    cur = new RelocatedResource(cur.resource, ancestorTime);
                }
            }
            result.add(cur);
        }
        return result;
    }
}

