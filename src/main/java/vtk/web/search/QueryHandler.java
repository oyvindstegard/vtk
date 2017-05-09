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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.springframework.web.HttpRequestHandler;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.search.ResultSet;
import vtk.repository.search.query.QueryBuilderException;
import vtk.util.Result;
import vtk.util.text.JsonStreamer;
import vtk.web.RequestContext;

public final class QueryHandler implements HttpRequestHandler {
    private Map<String, ResponseHandler> formats;
    private SimpleSearcher searcher;

    public QueryHandler(SimpleSearcher searcher, Map<String, ResponseHandler> formats) {
        this.searcher = Objects.requireNonNull(searcher);
        this.formats = new HashMap<>(Objects.requireNonNull(formats));
    }

    public QueryHandler(SimpleSearcher searcher) {
        this.searcher = Objects.requireNonNull(searcher);
        Map<String, ResponseHandler> defaultFormats = new HashMap<>();
        defaultFormats.put("json-compact", compactJsonResponseHandler);
        defaultFormats.put("json", completeJsonResponseHandler);
        defaultFormats.put("xml", completeXmlResponseHandler);
        this.formats = defaultFormats;
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
        Path uri = requestContext.getResourceURI();
        Repository repository = requestContext.getRepository();
        repository.retrieve(token, uri, true);

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
            return builder.query(Objects.requireNonNull(request.getParameter("q"), 
                    "Request parameter 'q' is required"));
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
            PrintWriter writer = response.getWriter();
            writer.write(t.getMessage());
            writer.close();
        };
    }

    @FunctionalInterface
    private static interface SuccessfulResponseHandler {
        public void accept(SimpleSearcher.Query query, ResultSet result) throws IOException;
    }

    private static ResponseHandler errorHandler(SuccessfulResponseHandler successHandler) {
        return (query, result, requestContext, response) -> {
            if (query.failure.isPresent()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                PrintWriter writer = response.getWriter();
                writer.write(query.failure.get().getMessage());
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
                PrintWriter writer = response.getWriter();
                writer.write(result.failure.get().getMessage());
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
        return def.getValueFormatter().valueToString(v, null, locale);
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
                .member("type", propset.getResourceType())
                .key("properties")
                .beginObject();

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
                streamer.endObject().endObject();
            }
            streamer.endArray().endJson();
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
                    .member("type", propset.getResourceType())
                    .key("properties")
                    .beginArray();
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
                streamer.endArray().endObject();
            }
            streamer.endArray().endJson();
        };
        errorHandler(successHandler)
        .accept(query, result, requestContext, response);
    };

    
    private static String xmlFormatValue(PropertyTypeDefinition def, Value v, Locale locale) {
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
                                            xmlFormatValue(def, v, requestContext.getLocale()));
                                    xml.writeEndElement();
                                }
                            }
                            xml.writeEndElement();
                        }
                        else {
                            xml.writeStartElement("value");
                            xml.writeCharacters(
                                    xmlFormatValue(def, p.getValue(), requestContext.getLocale()));
                            xml.writeEndElement();
                        }
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
}
