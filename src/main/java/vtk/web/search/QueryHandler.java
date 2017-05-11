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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.springframework.web.HttpRequestHandler;

import vtk.repository.Acl;
import vtk.repository.Namespace;
import vtk.repository.Privilege;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.search.ResultSet;
import vtk.repository.search.query.QueryBuilderException;
import vtk.security.Principal;
import vtk.util.Result;
import vtk.util.text.JsonStreamer;
import vtk.web.RequestContext;

/**
 * A search handler that accepts queries as request parameters and returns 
 * results in a number of formats.
 * 
 * <p>Description of query parameters
 * <ul>
 * <li>{@code q} - the query string, which is parsed using 
 *      {@link vtk.repository.search.Parser}
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
 *     {@code my-template} is defined as <code>field1={q} OR field2={q} OR field3={q}...</code> 
 * <li>{@code format} - refers to one of a number of pre-configured formats. The default formats
 *     are {@code json}, {@code json-compact}, {@code xml}, {@code tab-separated}, {@code comma-separated} 
 *     and {@code semicolon-separated}.
 * </ul>
 * @see SimpleSearcher
 */
public final class QueryHandler implements HttpRequestHandler {
    private Map<String, ResponseHandler> formats;
    private Map<String, String> templates;
    private SimpleSearcher searcher;

    public QueryHandler(SimpleSearcher searcher, Map<String, ResponseHandler> formats, 
            Map<String, String> templates) {
        this.searcher = Objects.requireNonNull(searcher);
        Map<String, ResponseHandler> fmts = defaultFormats();
        for (String key: Objects.requireNonNull(formats).keySet()) {
            fmts.put(key, formats.get(key));
        }
        this.formats = Collections.unmodifiableMap(fmts);
        this.templates = Objects.requireNonNull(templates);
    }

    public QueryHandler(SimpleSearcher searcher) {
        this.searcher = Objects.requireNonNull(searcher);
        this.formats = Collections.unmodifiableMap(defaultFormats());
        this.templates = new HashMap<>();
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
                RequestContext requestContext, HttpServletResponse response) throws IOException; 
    }

    @Override
    public void handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();

        Result<ResponseHandler> format = outputFormat(request);
        Result<SimpleSearcher.Query> query = format.flatMap(f -> buildQuery(request));
        Result<ResultSet> resultSet = query.flatMap(q -> Result.attempt(() -> searcher.search(token, q)));

        format.forEach(handler -> {
            try {
                handler.accept(query, resultSet, requestContext, response);
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private Result<ResponseHandler> outputFormat(HttpServletRequest request) {
        Result<ResponseHandler> format = Result.attempt(() -> {
            String f = Objects.requireNonNull(
                    request.getParameter("format"), 
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
            if (request.getParameter("t") != null) {
                String name = request.getParameter("t");
                String template = Objects.requireNonNull(
                        templates.get(name), "No such template: ' " + name + "'");
                q = template.replaceAll("\\{q\\}", q);
            }
            return builder.query(q);
        }));

        qry = qry.flatMap(builder -> Result.attempt(() -> {
            if (request.getParameter("limit") != null) {
                Integer limit = Integer
                        .parseInt(Objects.requireNonNull(request.getParameter("limit"), 
                                "Request parameter 'limit' must be an integer > 0"));
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
                return builder.sorting(Objects.requireNonNull(request.getParameter("sort"), 
                        "Request parameter 'sorting' cannot be empty"));
            }
            return builder;
        }));

        return qry.flatMap(builder -> Result.attempt(() -> builder.build()));
    }

    private static ResponseHandler badRequest(Throwable t) {
        return (query, result, requestContext, response) -> {
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
        return (query, result, requestContext, response) -> {
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
        (query, result, requestContext, response) -> {

        SuccessfulResponseHandler successHandler = (q, rs) -> {
            PrintWriter writer = response.getWriter();

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");

            JsonStreamer streamer = new JsonStreamer(writer, 2, false)
                    .beginObject()
                    .member("size", rs.getSize())
                    .member("offset", q.offset)
                    .member("total", rs.getTotalHits())
                    .key("results")
                    .beginArray();
            
            for (PropertySet propset: rs) {
                streamer.beginObject()
                .member("uri", propset.getURI())
                .member("type", propset.getResourceType());
                
                if (!propset.getProperties().isEmpty()) {
                    streamer.key("properties").beginObject();

                    for (Property p: propset) {
                        PropertyTypeDefinition def = p.getDefinition();

                        String name = def.getName();
                        if (def.getNamespace() != Namespace.DEFAULT_NAMESPACE) {
                            name = def.getNamespace().getPrefix() + ":" + name;
                        }
                        streamer.key(name);
                        if (def.getType() == PropertyType.Type.BINARY) {
                            streamer.value("<binary>");
                        }
                        else if (def.isMultiple()) {
                            streamer.beginArray();
                            for (Value v: p.getValues()) {
                                streamer.value(jsonFormatValue(
                                        def, v, requestContext.getLocale()));
                            }
                            streamer.endArray();
                        }
                        else {
                            streamer.value(jsonFormatValue(
                                    def, p.getValue(), requestContext.getLocale()));
                        }
                    }
                    streamer.endObject();
                }
                if (q.select.isIncludeAcl() && propset.acl().isPresent()) {
                    Acl acl = propset.acl().get();
                    streamer.key("acl").beginObject();
                    formatAcl(acl, jsonAclWriter(streamer));
                    streamer.endObject();
                }
                streamer.endObject();
            }
            streamer.endArray().endJson();
            writer.flush();
            writer.close();
        };
        errorHandler(successHandler)
            .accept(query, result, requestContext, response);
    };

    public static ResponseHandler completeJsonResponseHandler = (query, result, requestContext, response) -> {
        SuccessfulResponseHandler successHandler = (q, rs) -> {
            PrintWriter writer = response.getWriter();

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");

            JsonStreamer streamer = new JsonStreamer(writer, 2, false)
                    .beginObject()
                    .member("size", rs.getSize())
                    .member("offset", q.offset)
                    .member("total", rs.getTotalHits())
                    .key("results")
                    .beginArray();

            for (PropertySet propset: rs) {
                streamer.beginObject()
                    .member("uri", propset.getURI())
                    .member("type", propset.getResourceType());
                
                if (!propset.getProperties().isEmpty()) {
                    streamer.key("properties").beginArray();

                    for (Property p: propset) {
                        PropertyTypeDefinition def = p.getDefinition();

                        String name = def.getName();
                        if (def.getNamespace() != Namespace.DEFAULT_NAMESPACE) {
                            name = def.getNamespace().getPrefix() + ":" + name;
                        }
                        streamer.beginObject()
                        .member("ns", def.getNamespace().getUri())
                        .member("name", name)
                        .member("type", def.getType());

                        if (def.isMultiple()) {
                            streamer.key("values").beginArray();
                            if (def.getType() == PropertyType.Type.BINARY) {
                                streamer.value("<binary>");
                            }
                            else {
                                for (Value v: p.getValues()) {
                                    streamer.value(jsonFormatValue(
                                            def, v, requestContext.getLocale()));
                                }
                            }
                            streamer.endArray();
                        }
                        else {
                            streamer.member("value", jsonFormatValue(
                                    def, p.getValue(), requestContext.getLocale()));
                        }
                        streamer.endObject();
                    }
                    streamer.endArray();
                }
                if (q.select.isIncludeAcl() && propset.acl().isPresent()) {
                    Acl acl = propset.acl().get();
                    streamer.key("acl").beginObject();
                    formatAcl(acl, jsonAclWriter(streamer));
                    streamer.endObject();
                }
                streamer.endObject();
            }
            streamer.endArray().endJson();
            writer.flush();
            writer.close();
        };
        errorHandler(successHandler)
        .accept(query, result, requestContext, response);
    };

    
    private static void formatAcl(Acl acl, BiConsumer<Privilege, Map<String, List<Principal>>> writer) {
        Set<Privilege> actions = acl.getActions();
        
        actions.forEach(action -> {
            List<Principal> users = new ArrayList<>();
            List<Principal> groups = new ArrayList<>();
            List<Principal> pseudo = new ArrayList<>();

            acl.getPrincipalSet(action).forEach(p -> {
                if (p.getType() == Principal.Type.USER) {
                    users.add(p);
                }
                else if (p.getType() == Principal.Type.GROUP) {
                    groups.add(p);
                }
                else {
                    pseudo.add(p);
                }
            });
            
            Map<String, List<Principal>> entry = new HashMap<>();
            if (!users.isEmpty()) {
                entry.put("users", users);
            }
            if (!groups.isEmpty()) {
                entry.put("groups", users);
            }
            if (!pseudo.isEmpty()) {
                entry.put("pseudo", users);
            }
            if (!entry.isEmpty()) {
                writer.accept(action, entry);
            }
        });
    }
    
    private static BiConsumer<Privilege, Map<String, List<Principal>>> jsonAclWriter(JsonStreamer streamer) {
        return (privilege, entry) -> {
            try {
                streamer.key(privilege.getName()).beginObject();
                for (String type: entry.keySet()) {
                    streamer.key(type).beginArray();
                    for (Principal p: entry.get(type)) {
                        streamer.value(p.getQualifiedName());
                    }
                    streamer.endArray();
                }
                streamer.endObject();
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };        
    }

    private static BiConsumer<Privilege, Map<String, List<Principal>>> xmlAclWriter(XMLStreamWriter xml) {
        return (privilege, entry) -> {
            try {
                xml.writeStartElement(privilege.getName());
                for (String type: entry.keySet()) {
                    xml.writeStartElement(type);
                    for (Principal p: entry.get(type)) {
                        xml.writeStartElement("principal");
                        xml.writeCharacters(p.getQualifiedName());
                        xml.writeEndElement();
                    }
                    xml.writeEndElement();
                }
                xml.writeEndElement();
            }
            catch (XMLStreamException e) {
                throw new RuntimeException(e);
            }
        };        
    }

    private static Object jsonFormatValue(PropertyTypeDefinition def, Value v, Locale locale) {
        if (def.getType() == Type.DATE) {
            return def.getValueFormatter().valueToString(v, "iso-8601-short", locale);
        }
        else if (def.getType() == Type.TIMESTAMP) {
            return def.getValueFormatter().valueToString(v, "iso-8601", locale);
        }
        else if (def.getType() == Type.JSON) {
            return v.getJSONValue();
        }
        else if (def.getType() == Type.INT) {
            return v.getIntValue();
        }
        else if (def.getType() == Type.LONG) {
            return v.getLongValue();
        }
        else if (def.getType() == Type.BOOLEAN) {
            return v.getBooleanValue();
        }
        return def.getValueFormatter().valueToString(v, null, locale);
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


    public static ResponseHandler completeXmlResponseHandler = 
        (query, result, requestContext, response) -> {

        SuccessfulResponseHandler successHandler = (q, rs) -> {
            try {
                OutputStream stream = response.getOutputStream();

                XMLStreamWriter xml = XMLOutputFactory.newInstance()
                        .createXMLStreamWriter(stream, "UTF-8");
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("application/xml;charset=utf-8");

                xml.writeStartDocument();

                xml.writeStartElement("results");
                xml.writeAttribute("size", String.valueOf(rs.getSize()));
                xml.writeAttribute("totalHits", String.valueOf(rs.getTotalHits()));
                xml.writeAttribute("offset", String.valueOf(q.offset));

                for (PropertySet propset: rs) {
                    xml.writeStartElement("resource");
                    xml.writeAttribute("name", propset.getName());
                    xml.writeAttribute("type", propset.getResourceType().toString());
                    xml.writeAttribute("uri", propset.getURI().toString());

                    requestContext.service("viewService").ifPresent(service -> {
                        try {
                            xml.writeAttribute("url", service.constructURL(propset.getURI()).toString());
                        }
                        catch (Throwable t) { }
                    });

                    for (Property p: propset) {
                        PropertyTypeDefinition def = p.getDefinition();
                        String name = def.getName();
                        if (def.getNamespace() != Namespace.DEFAULT_NAMESPACE) {
                            name = def.getNamespace().getPrefix() + ":" + name;
                        }
                        xml.writeStartElement("property");
                        xml.writeAttribute("name", name);
                        if (def.getNamespace() != Namespace.DEFAULT_NAMESPACE) {
                            xml.writeAttribute("namespace", def.getNamespace().getUri());
                        }

                        if (p.getDefinition().isMultiple()) {
                            xml.writeStartElement("values");
                            if (p.getDefinition().getType() == PropertyType.Type.BINARY) {
                                xml.writeEmptyElement("binary");
                            }
                            else {
                                for (Value v: p.getValues()) {
                                    xml.writeStartElement("value");
                                    xml.writeCharacters(
                                            defaultFormatValue(def, v, requestContext.getLocale()));
                                    xml.writeEndElement();
                                }
                            }
                            xml.writeEndElement();
                        }
                        else {
                            xml.writeStartElement("value");
                            xml.writeCharacters(
                                    defaultFormatValue(def, p.getValue(), requestContext.getLocale()));
                            xml.writeEndElement();
                        }
                        xml.writeEndElement();
                    }
                    if (q.select.isIncludeAcl() && propset.acl().isPresent()) {
                        Acl acl = propset.acl().get();
                        xml.writeStartElement("acl");
                        formatAcl(acl, xmlAclWriter(xml));
                        xml.writeEndElement();
                    }
                    xml.writeEndElement();
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
        errorHandler(successHandler).accept(query, result, requestContext, response);
    };
    
    
    
    public static ResponseHandler fieldSeparatedResponseHandler(Character separator) {
        if (separator == '\\') throw new IllegalArgumentException(
                "Character '\\' not a valid separator");
        
        return (query, result, requestContext, response) -> {
            
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
                boolean unique = "true".equals(requestContext
                        .getServletRequest().getParameter("unique-filter"));
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
                .accept(query, result, requestContext, response);
        };
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
    
}
