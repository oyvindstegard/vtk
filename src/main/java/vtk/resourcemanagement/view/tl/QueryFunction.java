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
package vtk.resourcemanagement.view.tl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.text.tl.Context;
import vtk.text.tl.Symbol;
import vtk.text.tl.expr.Function;
import vtk.util.text.Json;
import vtk.util.text.Json.MapContainer;
import vtk.web.RequestContext;
import vtk.web.search.SimpleSearcher;
import vtk.web.search.SimpleSearcher.Query;
import vtk.web.search.SimpleSearcher.QueryBuilder;

public class QueryFunction extends Function {
    private SimpleSearcher searcher;

    public QueryFunction(Symbol symbol, SimpleSearcher searcher) {
        super(symbol, 1);
        this.searcher = Objects.requireNonNull(searcher);
    }

    @Override
    public Object eval(Context ctx, Object... args) {
        Object arg = args[0];
        Objects.requireNonNull(arg);
        if (!(arg instanceof Map<?,?>)) {
            throw new IllegalArgumentException("Argument must be a hash");
        }
        Map<?,?> input = (Map<?,?>) arg;

        QueryBuilder builder = searcher.builder()
            .query(Objects.requireNonNull(input.get("q")).toString());
        
        Optional.ofNullable(input.get("select"))
            .ifPresent(sel -> builder.select(String.valueOf(sel)));
        Optional.ofNullable(input.get("sorting"))
            .ifPresent(sort -> builder.sorting(String.valueOf(toString())));
        Optional.ofNullable(input.get("offset"))
            .ifPresent(offset -> builder.sorting(String.valueOf(offset)));
        Optional.ofNullable(input.get("limit"))
            .ifPresent(limit -> builder.sorting(String.valueOf(limit)));
        
        Query query = builder.build();
        
        boolean flattenNamespaces = Optional.ofNullable(input.get("flatten-namespaces"))
                .map(String::valueOf)
                .map(Boolean::valueOf)
                .orElse(false);
        
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        requestContext.getLocale();

        MapContainer result = searcher.search(token, query, rs -> {
            Json.MapContainer resultMap = new Json.MapContainer();
            List<Json.MapContainer> list = rs.getAllResults()
                    .stream().map(mapper(flattenNamespaces, requestContext.getLocale()))
                    .collect(Collectors.toList());
            resultMap.put("size", rs.getSize());
            resultMap.put("offset", query.offset);
            resultMap.put("total", rs.getTotalHits());
            resultMap.put("results", list);
            return resultMap;
        });
        
        return result;
    }

    // Should be factored out to a general {@code ResourceMapper} utility:
    private static java.util.function.Function<PropertySet, Json.MapContainer>  
        mapper(boolean flattenNamespaces, Locale locale) {
        BiFunction<PropertyTypeDefinition, Value, Object> valueFormatter = jsonValueFormatter(locale);
        
        return propset -> {
            Json.MapContainer map = new Json.MapContainer();
            map.put("uri", propset.getURI());
            map.put("type", propset.getResourceType());
            if (!propset.getProperties().isEmpty()) {
                Json.MapContainer props = new Json.MapContainer();
                for (Property p: propset) {
                    PropertyTypeDefinition def = p.getDefinition();

                    String name = def.getName();
                    if (!flattenNamespaces && def.getNamespace() != Namespace.DEFAULT_NAMESPACE) {
                        name = def.getNamespace().getPrefix() + ":" + name;
                    }
                    if (def.getType() == PropertyType.Type.BINARY) {
                        props.put(name, "<binary>");
                    }
                    else if (def.isMultiple()) {
                        List<Object> list = new ArrayList<>();
                        for (Value v: p.getValues()) {
                            list.add(valueFormatter.apply(def, v));
                        }
                        props.put(name, list);
                    }
                    else {
                        props.put(name, valueFormatter.apply(def, p.getValue()));
                    }
                }
                map.put("properties", props);
            }

            return map;
        };
    }
    
    private static BiFunction<PropertyTypeDefinition, Value, Object> jsonValueFormatter(Locale locale) {
        return (def, value) -> { 
            if (def.getType() == Type.DATE) {
                return def.getValueFormatter().valueToString(value, "iso-8601-short", locale);
            }
            else if (def.getType() == Type.TIMESTAMP) {
                return def.getValueFormatter().valueToString(value, "iso-8601", locale);
            }
            else if (def.getType() == Type.JSON) {
                return value.getJSONValue();
            }
            else if (def.getType() == Type.INT) {
                return value.getIntValue();
            }
            else if (def.getType() == Type.LONG) {
                return value.getLongValue();
            }
            else if (def.getType() == Type.BOOLEAN) {
                return value.getBooleanValue();
            }
            return def.getValueFormatter().valueToString(value, null, locale);
        };
    }
    
}
