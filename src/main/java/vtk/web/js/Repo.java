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
package vtk.web.js;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import vtk.js.JavascriptEngine;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.util.Result;
import vtk.util.repository.ResourceMappers;
import vtk.util.repository.ResourceMappers.PropertySetMapper;
import vtk.util.text.Json.MapContainer;
import vtk.web.search.SimpleSearcher;
import vtk.web.search.SimpleSearcher.Query;

public class Repo implements Module {
    private Repository repository;
    private SimpleSearcher searcher;
    private JavascriptEngine engine;
    
    private PropertySetMapper<MapContainer> jsonMapper(Locale locale) {
        return ResourceMappers
                .jsonObject(locale)
                .acls(true)
                .types(true)
                .uris(true)
                .build();
    }

    
    public Repo(Repository repository, SimpleSearcher searcher, JavascriptEngine engine) {
        this.repository = repository;
        this.searcher = searcher;
        this.engine = engine;
    }
    
    @Override
    public Object instance(JSContext context, JavascriptEngine engine) {
        Map<String, Object> module = new HashMap<>();
        module.put("load", load(context));
        module.put("search", search(context));
        return module;
    }
    
    private static class RetrieveParams {
        public final Path uri;
        public final String token;
        public RetrieveParams(Path uri, String token) {
            this.uri = uri;
            this.token = token;
        }
    }

    @SuppressWarnings("restriction")
    private BiConsumer<ScriptObjectMirror, ScriptObjectMirror> load(JSContext context) {
        PropertySetMapper<MapContainer> mapper = jsonMapper(context.locale());

        return (params, callback) -> {
            Result<RetrieveParams> retrieveParams = Result.attempt(() -> {
                Path uri = Path.fromString(
                        Objects.requireNonNull(params.get("uri"), "Field 'uri' is required")
                        .toString());
                
                String token = Objects
                        .toString(params.containsKey("token") ? params.get("token") 
                                : context.securityToken(), null);
                return new RetrieveParams(uri, token);
            });

            CompletableFuture.supplyAsync(() -> {
                return retrieveParams.flatMap(retrieve -> {
                    return Result.attempt(() -> {
                        try {
                            Resource r = repository
                                    .retrieve(retrieve.token, retrieve.uri, true);
                            return mapper.apply(r);
                        }
                        catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                });
            })
            
            .thenAccept(result -> {
                engine.execute(() -> {
                    // Invoke callback from engine thread
                    if (callback != null && callback.isFunction()) {
                        try {
                            if (result.failure.isPresent()) {
                                callback.call(null, result.failure.get(), null);
                            }
                            else {
                                callback.call(null, null, result.result.get());
                            }
                        }
                        catch (Throwable t) {
                            context.end(engine.jsError(t));
                        }
                    }
                    return null;
                });
            });
        };
    }

    
    
    @FunctionalInterface
    public static interface Search {
        @SuppressWarnings("restriction")
        public void search(ScriptObjectMirror params, ScriptObjectMirror callback);
    }
    private static class SearchParams {
        public final String query;
        public final int limit;
        public final int offset;
        public final String sorting;
        public final String select;
        public SearchParams(String query, int limit, int offset, String sorting, String select) {
            this.query = query; this.limit = limit; 
            this.offset = offset; this.sorting = sorting; 
            this.select = select;
        }
    }
    
    @SuppressWarnings("restriction")
    public Search search(JSContext context) {
        return (params, callback) -> {
            Result<SearchParams> searchParams = Result.attempt(() -> {
                String query = Objects
                        .requireNonNull(params.get("query"), "Field 'query' is required")
                        .toString();
                int limit = Integer.valueOf(params.getOrDefault("limit", "100").toString());
                int offset = Integer.valueOf(params.getOrDefault("offset", "0").toString());
                String sorting = params.getOrDefault("sorting", "uri").toString();
                String select = params.getOrDefault("select", "*").toString();
                return new SearchParams(query, limit, offset, sorting, select);
            });
            
            CompletableFuture.supplyAsync(() -> {
                return searchParams.flatMap(sp -> {
                    return Result.attempt(() -> {
                        Query q = searcher.builder()
                            .query(sp.query)
                            .limit(sp.limit)
                            .offset(sp.offset)
                            .sorting(sp.sorting)
                            .select(sp.select)
                            .build();
                        
                        return searcher.search(context.securityToken(), q);
                    });
                });
            })
            .thenApply(res -> { 
            
                PropertySetMapper<MapContainer> mapper = jsonMapper(context.locale());
                Result<MapContainer> result = res.flatMap(rs -> Result.attempt(() -> { 
                    MapContainer rmap = new MapContainer();
                    List<MapContainer> entries = rs.getAllResults().stream()
                            .map(propset -> mapper.apply(propset))
                            .collect(Collectors.toList());

                    rmap.put("entries", entries);
                    rmap.put("list", entries);
                    rmap.put("size", rs.getSize());
                    rmap.put("hits", rs.getTotalHits());
                    return rmap;
                }));
                
                return result;
            })
            
            .thenCompose(result -> {
                try {
                    MapContainer rmap = result.get();
                    return engine.toJsObject(rmap);
                }
                catch (Throwable t) {
                    //return CompletableFuture.failedFuture(result.failure.get());
                    CompletableFuture<Object> fail = new CompletableFuture<>();
                    fail.completeExceptionally(t);
                    return fail;
                }
            })
            
            .whenComplete((jsObj, err) -> {
                engine.execute(() -> {
                    // Invoke callback from engine thread
                    if (callback != null && callback.isFunction()) {
                        try {
                            if (err != null) {
                                callback.call(null, err, null);
                            }
                            else {
                                callback.call(null, null, jsObj);
                            }
                        }
                        catch (Throwable t) {
                            context.end(engine.jsError(t));
                        }
                    }
                    return null;
                });
            });
         };
    }
}
