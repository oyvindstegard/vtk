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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.HttpRequestHandler;

import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.search.ResultSet;
import vtk.repository.search.query.QueryBuilderException;
import vtk.util.Result;
import vtk.util.repository.ResourceMappers;
import vtk.util.repository.ResourceMappers.PropertySetMapper;
import vtk.util.text.JsonStreamer;
import vtk.util.text.TextUtils;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * A search handler that accepts queries as request parameters and returns 
 * results in a number of formats.
 * 
 * <p>Description of query parameters
 * <ul>
 * <li>{@code q} - the query string, which is parsed using 
 *      {@link vtk.web.search.SearchParser}
 * <li>{@code fq} - Additional filter query clause, which will be logically required (AND), 
 * 		in addition to the criteria specified in the main query. Can occur zero or more times.
 * <li>{@code properties} or {@code fields} - a comma-separated list of property 
 *      names to include in the result set. If the string {@code *} appears in the list, 
 *      all properties are included. In addition, the special 
 *      name {@code acl} causes ACLs to be included.
 * <li>{@code sort} - a comma-separated list of property names, each potentially
 *     suffixed by either {@code asc} or {@code desc}, specifying the sorting
 *     of the result set
 * <li>{@code limit} - maximum number of resources to include in the result set
 * <li>{@code offset} - where to start in the global result set
 * <li>{@code t} - if specified, this parameter refers to a pre-configured 
 *     <em>template</em>. Templates are strings that may contain the special
 *     token <code>{q}</code>, which is substituted by the query parameter ({@code q})
 *     at search-time. This can be useful for certain types of queries where a single input
 *     string is repeated a number of times, in order to reduce the length of the query string. 
 *     For example, the query <code>?q=field1=xxx OR field2=xxx OR field3=xxx ...</code> 
 *     can be shortened to <code>?q=xxx&t=my-template</code>, given that the template 
 *     {@code my-template} is defined as <code>field1={q} OR field2={q} OR field3={q}...</code>.
 *     Whitespace in the request parameter {@code q} is normally escaped (using {@code \}) 
 *     before the template is applied. In cases where non-escaped input is wanted, 
 *     the token <code>{'q}</code> may be used in the template.
 * <li>{@code tokenize} (boolean, used with the {@code t} parameter) - whether or not 
 *      to tokenize the query parameter. If {@code true}, the template is expanded for each token
 *      in the {@code q} parameter, and the resulting queries are joined using the {@code AND} operator.
 * <li>{@code format} - refers to one of a number of pre-configured formats. The default formats
 *     are {@code json}, {@code json-compact}, {@code xml}, {@code tab-separated}, {@code comma-separated} 
 *     and {@code semicolon-separated}.
 * </ul>
 * @see SimpleSearcher
 */
public final class QueryHandler implements HttpRequestHandler {
    private static Logger logger = LoggerFactory.getLogger(QueryHandler.class);
    private SimpleSearcher searcher;
    private Map<String, ResponseHandler> formats;
    private Map<String, String> templates;
    private Map<String, String> defaultParameters;

    public QueryHandler(SimpleSearcher searcher) {
        this.searcher = Objects.requireNonNull(searcher);
        this.formats = Collections.unmodifiableMap(defaultFormats());
        this.templates = new HashMap<>();
        this.defaultParameters = new HashMap<>();
    }
    
    public QueryHandler(SimpleSearcher searcher, Map<String, ResponseHandler> formats, 
            Map<String, String> templates, Map<String, String> defaultParameters) {
        this.searcher = Objects.requireNonNull(searcher);
        Map<String, ResponseHandler> fmts = defaultFormats();
        for (String key: Objects.requireNonNull(formats).keySet()) {
            fmts.put(key, formats.get(key));
        }
        this.formats = Collections.unmodifiableMap(fmts);
        this.templates = new HashMap<>(Objects.requireNonNull(templates));
        this.defaultParameters = new HashMap<>(Objects.requireNonNull(defaultParameters));
    }

    public static Map<String, ResponseHandler> defaultFormats() {
        Map<String, ResponseHandler> defaultFormats = new HashMap<>();
        defaultFormats.put("json-compact", compactJsonResponseHandler);
        defaultFormats.put("json", completeJsonResponseHandler);
        defaultFormats.put("xml", completeXmlResponseHandler);
        defaultFormats.put("tab-separated", fieldSeparatedResponseHandler('\t'));
        defaultFormats.put("comma-separated", fieldSeparatedResponseHandler(','));
        defaultFormats.put("semicolon-separated", fieldSeparatedResponseHandler(';'));
        return defaultFormats;
    }

    @FunctionalInterface
    public static interface ResponseHandler {
        public void accept(Result<SimpleSearcher.Query> query, Result<ResultSet> result, 
                RequestContext requestContext, HttpServletRequest request, 
                HttpServletResponse response) throws IOException; 
    }

    @Override
    public void handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        HttpServletRequest wrappedRequest 
            = new ParameterRequestWrapper(request, defaultParameters);
        RequestContext requestContext = RequestContext.getRequestContext(request);
        String token = requestContext.getSecurityToken();

        Result<ResponseHandler> format = outputFormat(wrappedRequest);
        Result<SimpleSearcher.Query> query = format.flatMap(f -> buildQuery(wrappedRequest));
        Result<ResultSet> resultSet = query.flatMap(q -> Result.attempt(() -> searcher.search(token, q)));

        logger.debug("Request: query={}, result={}, handler={}", query, resultSet, format);
        
        format.forEach(handler -> {
            try {
                handler.accept(query, resultSet, requestContext, wrappedRequest, response);
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    
    private Result<ResponseHandler> outputFormat(HttpServletRequest request) {
        Result<ResponseHandler> format = Result.attempt(() -> {
            String f = Objects
                    .requireNonNull(request.getParameter("format"), 
                            "Missing parameter 'format'. Must be one of " 
                                    + formats.keySet());
            ResponseHandler handler = formats.get(f);
            return Objects.requireNonNull(handler, "Unknown format '" + f 
                    + "'. Must be one of " + formats.keySet());
        });
        format = format.recover(err -> badRequest(err));
        return format;
    }
    
    private Result<SimpleSearcher.Query> buildQuery(HttpServletRequest request) {

        Result<SimpleSearcher.QueryBuilder> qry = Result.success(searcher.builder());

        qry = qry.flatMap(builder -> Result.attempt(() -> {
            String q = Objects.requireNonNull(request.getParameter("q"),
                    "Request parameter 'q' is required");
            if ("".equals(q.trim())) {
                throw new IllegalArgumentException("Parameter 'q' cannot be empty");
            }
            if (request.getParameter("t") != null) {
                String name = request.getParameter("t");
                String template = Objects.requireNonNull(
                        templates.get(name), "No such template: '" + name + "'");

                if ("true".equals(request.getParameter("tokenize"))) {
                    q = TextUtils.tokenizeWithPhrases(q).stream()
                            .limit(10)
                            .map(token -> {
                                String query = template.replaceAll("\\{'q\\}", token);
                                query = query.replaceAll("\\{q\\}", 
                                        escape(escape(token, ' ', '\\'), ' ', '\\'));
                                return query;
                            })
                            .collect(Collectors.joining(" AND ", "(", ")"));
                }
                else {
                    String query = template.replaceAll("\\{'q\\}", q);
                    q = escape(escape(q, ' ', '\\'), ' ', '\\');
                    query = query.replaceAll("\\{q\\}", q);
					q = query;
                }
			}
			if (request.getParameterValues("fq") != null) {
				for (String fq : request.getParameterValues("fq")) {
					fq = escape(fq, ' ', '\\');
					builder.addFilterQuery(fq);
				}
			}
			return builder.query(q);
        }));

        qry = qry.flatMap(builder -> Result.attempt(() -> {
            if (request.getParameter("limit") != null) {
                Integer limit = Integer
                        .parseInt(request.getParameter("limit"));
                return builder.limit(limit);
            }
            return builder;
        }));

        qry = qry.flatMap(builder -> Result.attempt(() -> {
            if (request.getParameter("offset") != null) {
                Integer offset = Integer
                        .parseInt(request.getParameter("offset"));
                return builder.offset(offset);
            }
            return builder;
        }));

        qry = qry.flatMap(builder -> Result.attempt(() -> {
            if (request.getParameter("properties") != null) {
                String fields = request.getParameter("properties");
                return builder.select(fields);
            }
            else if (request.getParameter("fields") != null) {
                String fields = request.getParameter("fields");
                return builder.select(fields);
            }
            return builder;
        }));

        qry = qry.flatMap(builder -> Result.attempt(() -> {
            if (request.getParameter("sort") != null) {
                return builder.sorting(request.getParameter("sort"));
            }
            return builder;
        }));

        return qry.flatMap(builder -> Result.attempt(() -> builder.build()));
    }

    private static ResponseHandler badRequest(Throwable t) {
        return (query, result, requestContext, request, response) -> {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain;charset=utf-8");
            PrintWriter writer = response.getWriter();
            writer.write(t.getMessage());
            writer.write("\n" + usage + "\n");
            writer.close();
        };
    }
    
    private static String usage = "Usage: ?q=<query>&format=<format>"
            + "[&sort=<sorting>][&fields=<fields>][&limit=<limit>][&offset=<offset>][&t=<template>]";

    @FunctionalInterface
    private static interface SuccessfulResponseHandler {
        public void accept(SimpleSearcher.Query query, ResultSet result) throws IOException;
    }

    private static ResponseHandler errorHandler(SuccessfulResponseHandler successHandler) {
        return (query, result, requestContext, request, response) -> {
            if (query.failure.isPresent()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("text/plain;charset=utf-8");
                PrintWriter writer = response.getWriter();
                writer.write(query.failure.get().getMessage());
                writer.write("\n" + usage + "\n");
                writer.close();
                return;
            }

            if (result.failure.isPresent()) {
                Throwable t = result.failure.get();
                if (t instanceof QueryBuilderException) {
                    // Some query-related exceptions are not  
                    // thrown until search() is called:
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
                else {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
                response.setContentType("text/plain;charset=utf-8");
                PrintWriter writer = response.getWriter();
                writer.write(result.failure.get().getMessage());
                writer.write("\n" + usage + "\n");
                writer.close();
                return;
            }
            query.forEach(q -> result.forEach(r -> { 
                try {
                    successHandler.accept(q, r);
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }));
        };
    }


    public static ResponseHandler compactJsonResponseHandler = 
        (query, result, requestContext, request, response) -> {

        SuccessfulResponseHandler successHandler = (q, rs) -> {

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");            

            PropertySetMapper<Consumer<JsonStreamer>> mapper = ResourceMappers
                    .jsonStreamer(requestContext.getLocale())
                    .compact(true)
                    .uris(q.fields.contains("uri") || q.fields.contains("*"))
                    .types(q.fields.contains("type") || q.fields.contains("*"))
                    .names(q.fields.contains("name") || q.fields.contains("*"))
                    .acls(false)
                    .build();
            
            PrintWriter writer = response.getWriter();
            JsonStreamer streamer = new JsonStreamer(writer, 2, false)
                    .beginObject()
                    .member("size", rs.getSize())
                    .member("offset", q.offset)
                    .member("total", rs.getTotalHits())
                    .key("results")
                    .beginArray();
            
            
            for (PropertySet propset: rs) {
                mapper.apply(propset).accept(streamer);
            }
            streamer.endArray().endJson();
            writer.flush();
            writer.close();
        };
        errorHandler(successHandler)
            .accept(query, result, requestContext, request, response);
    };

    public static ResponseHandler completeJsonResponseHandler = 
        (query, result, requestContext, request, response) -> {
        SuccessfulResponseHandler successHandler = (q, rs) -> {

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json;charset=utf-8");
            
            PropertySetMapper<Consumer<JsonStreamer>> mapper = ResourceMappers
                    .jsonStreamer(requestContext.getLocale())
                    .compact(false)
                    .uris(true)
                    .types(true)
                    .names(true)
                    .acls(true)
                    .build();
            
            PrintWriter writer = response.getWriter();
            JsonStreamer streamer = new JsonStreamer(writer, 2, false)
                    .beginObject()
                    .member("size", rs.getSize())
                    .member("offset", q.offset)
                    .member("total", rs.getTotalHits())
                    .key("results")
                    .beginArray();
            
            for (PropertySet propset: rs) {
                mapper.apply(propset).accept(streamer);
            }
            streamer.endArray().endJson();
            writer.flush();
            writer.close();
        };
        errorHandler(successHandler)
            .accept(query, result, requestContext, request, response);
    };


    public static ResponseHandler completeXmlResponseHandler = 
        (query, result, requestContext, request, response) -> {

        SuccessfulResponseHandler successHandler = (q, rs) -> {
            try {

                Function<PropertySet, Optional<vtk.web.service.URL>> linkConstructor = propset -> {
                    Optional<Service> service = requestContext.service("viewService");
                    if (!service.isPresent()) {
                        return Optional.empty();
                    }
                    else {
                        try {
                            URL url = service.get().urlConstructor(requestContext.getRequestURL())
                                    .withURI(propset.getURI())
                                    .constructURL();
                            return Optional.of(url);
                        }
                        catch (Throwable t) {
                            return Optional.empty();
                        }
                    }
                };
    
                PropertySetMapper<Consumer<XMLStreamWriter>> mapper = 
                        ResourceMappers.xmlStreamer(requestContext.getLocale())
                            .acls(true)
                            .linkConstructor(linkConstructor)
                            .build();
                
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("application/xml;charset=utf-8");
                
                OutputStream stream = response.getOutputStream();
                XMLStreamWriter xml = XMLOutputFactory.newInstance()
                        .createXMLStreamWriter(stream, "UTF-8");
                xml.writeStartDocument();

                xml.writeStartElement("results");
                xml.writeAttribute("size", String.valueOf(rs.getSize()));
                xml.writeAttribute("totalHits", String.valueOf(rs.getTotalHits()));
                xml.writeAttribute("offset", String.valueOf(q.offset));
                
                for (PropertySet propset: rs) {
                    mapper.apply(propset).accept(xml);
                }
                xml.writeEndElement();
                xml.writeEndDocument();
                xml.flush();
                xml.close();
            }
            catch (XMLStreamException | FactoryConfigurationError e) {
                throw new RuntimeException(e);
            }
        };
        errorHandler(successHandler).accept(query, result, requestContext, request, response);
    };
    
    
    
    public static ResponseHandler fieldSeparatedResponseHandler(Character separator) {
        if (separator == '\\') throw new IllegalArgumentException(
                "Character '\\' not a valid separator");
        
        return (query, result, requestContext, request, response) -> {
            
            query = query.flatMap(q -> Result.attempt(() -> {
                if (q.fields.contains("*")) {
                    throw new IllegalArgumentException(
                            "This output format does not support wildcard fields (*)");
                }
                else if (q.fields.isEmpty()) {
                    throw new IllegalArgumentException(
                            "This output format requires at least one field to be specified");
                }
                return q;
            }));

            SuccessfulResponseHandler successHandler = (q, rs) -> {

                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/plain;charset=utf-8");
                
                PrintWriter writer = response.getWriter();
                // Handy uniqueness filter:
                boolean unique = "true".equals(request.getParameter("filter-unique"));
                Set<String> seen = null;
                if (unique) seen = new HashSet<>();

                for (PropertySet propset: rs) {
                    boolean first = true;
                    StringBuilder line = new StringBuilder();
                    boolean blank = true;
                    
                    for (String field: q.fields) {
                        if (!first) line.append(separator);
                        else first = false;
                        
                        String prefix = null;
                        String name = field;
                        if (field.contains(":")) {
                            prefix = field.substring(0, field.indexOf(":"));
                            name = field.substring(prefix.length() + 1);
                        }
                        Property p = propset.getPropertyByPrefix(prefix, name);
                        if (p != null) {
                            PropertyTypeDefinition def = p.getDefinition();

                            if (def.getType() == PropertyType.Type.BINARY) {
                                line.append(escape("<binary>", separator, '\\'));
                                blank = false;
                            }
                            else if (def.isMultiple()) {
                                boolean firstVal = true;
                                StringBuilder multi = new StringBuilder();
                                for (Value v: p.getValues()) {
                                    if (!firstVal) {
                                        multi.append(",");
                                    }
                                    firstVal = false;
                                    multi.append(escape(defaultFormatValue(def, v, 
                                            requestContext.getLocale()), separator, '\\'));
                                    blank = false;
                                }
                                line.append(escape(multi.toString(), separator, '\\'));
                            }
                            else {
                                line.append(escape(defaultFormatValue(def, p.getValue(), 
                                        requestContext.getLocale()), separator, '\\'));
                                blank = false;
                            }
                        }
                        else if ("uri".equals(field)) {
                            line.append(escape(propset.getURI().toString(), separator, '\\'));
                            blank = false;
                        }
                        else if ("type".equals(field)) {
                            line.append(escape(propset.getResourceType(), separator, '\\'));
                            blank = false;
                        }
                        else if ("name".equals(field)) {
                            line.append(escape(propset.getName(), separator, '\\'));
                            blank = false;
                        }
                    }
                    if (!blank) {
                        String output = line.append('\n').toString();
                        if (unique) {
                            if (!seen.contains(output)) {
                                seen.add(output);
                                writer.write(output);
                            }
                        }
                        else {
                            writer.write(output);
                        }
                    }
                }
                writer.flush();
                writer.close();
            };
            errorHandler(successHandler)
                .accept(query, result, requestContext, request, response);
        };
    }
    
    private static String defaultFormatValue(PropertyTypeDefinition def, Value v, Locale locale) {
        if (def.getType() == Type.DATE) {
            return def.getValueFormatter().valueToString(v, "iso-8601-short", locale);
        }
        else if (def.getType() == Type.TIMESTAMP) {
            return def.getValueFormatter().valueToString(v, "iso-8601", locale);
        }
        return def.getValueFormatter().valueToString(v, null, locale);
    }


    private static String escape(String value, char separator, char escape) {
        StringBuilder escapedValue = new StringBuilder(value.length());
        
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == separator || c == escape) {
                escapedValue.append(escape).append(c);
            }
            else if (c == '\n') {
                escapedValue.append('\\').append('n');
            }
            else {
                escapedValue.append(c);
            }
        }

        return escapedValue.toString();
    }
    
    private static class ParameterRequestWrapper extends HttpServletRequestWrapper {
        private Map<String, String> defaultParameters;
        
        public ParameterRequestWrapper(HttpServletRequest request, Map<String, String> defaultParameters) {
            super(request);
            this.defaultParameters = defaultParameters;
        }
        
        @Override
        public String getParameter(String name) {
            String param = super.getParameter(name);
            if (param != null) {
                return param;
            }
            return defaultParameters.get(name);
        }
    }
}
