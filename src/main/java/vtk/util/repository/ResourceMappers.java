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
package vtk.util.repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import vtk.repository.Acl;
import vtk.repository.Namespace;
import vtk.repository.Privilege;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.security.Principal;
import vtk.util.text.Json;
import vtk.util.text.JsonStreamer;
import vtk.web.service.URL;

/**
 * This class consists of utility functions for mapping 
 * {@link PropertySet property sets} to other formats, 
 * such as {@link #xmlStreamer(Locale) XML} and {@link #jsonStreamer(Locale) JSON}.
 * 
 * Typical usage is to instantiate a {@link MapperFactory builder} and use it to create
 * the actual {@link PropertySetMapper mapper}
 */
public final class ResourceMappers {
    
    @FunctionalInterface
    public static interface PropertySetMapper<T> extends Function<PropertySet, T> {};

    @FunctionalInterface
    public static interface ValueMapper<T> extends BiFunction<PropertyTypeDefinition, Value, T> {};
    
    
    @FunctionalInterface
    public static interface MapperFactory<T> {
        public PropertySetMapper<T> build();
    }

    /**
     * Creates a builder for a JSON streaming resource mapper
     * @param locale the locale to use when formatting properties
     * @return a builder object
     */
    public static JsonStreamerBuilder jsonStreamer(Locale locale) {
        return new JsonStreamerBuilder(locale);
    }
    
    /**
     * Creates a builder for a JSON object resource mapper
     * @param locale the locale to use when formatting properties
     * @return a builder object
     */
    public static JsonObjectBuilder jsonObject(Locale locale) {
        return new JsonObjectBuilder(locale);
    }
    
    /**
     * Creates a builder for an XML streaming resource mapper
     * @param locale the locale to use when formatting properties
     * @return a builder object
     */
    public static XmlStreamerBuilder xmlStreamer(Locale locale) {
        return new XmlStreamerBuilder(locale);
    }
    
    public static class JsonStreamerBuilder implements MapperFactory<Consumer<JsonStreamer>> {
        private boolean compact = false;
        private ValueMapper<Object> valueMapper = null;
        private boolean includeAcls = false;
        private Locale locale;
        
        private JsonStreamerBuilder(Locale locale) {
            this.locale = Objects.requireNonNull(locale);
        }
        
        public JsonStreamerBuilder compact(boolean compact) {
            this.compact = compact;
            return this;
        }
        
        public JsonStreamerBuilder acls(boolean includeAcls) {
            this.includeAcls = includeAcls;
            return this;
        }
        
        public JsonStreamerBuilder valueMapper(ValueMapper<Object> valueMapper) {
            this.valueMapper = valueMapper;
            return this;
        }
        
        public PropertySetMapper<Consumer<JsonStreamer>> build() {
            ValueMapper<Object> mapper = valueMapper != null ?
                    valueMapper : jsonValueFormatter(() -> locale);
            if (compact) {
                return jsonStreamerCompact(mapper, includeAcls);
            }
            return jsonStreamerComplete(mapper, includeAcls);
        }
    }
    
    public static class JsonObjectBuilder implements MapperFactory<Json.MapContainer> {
        private Locale locale;
        private ValueMapper<Object> valueMapper = null;
        private boolean includeAcls = false;
        
        public JsonObjectBuilder valueMapper(ValueMapper<Object> valueMapper) {
            this.valueMapper = valueMapper;
            return this;
        }
        private JsonObjectBuilder(Locale locale) {
            this.locale = Objects.requireNonNull(locale);
        }
        public JsonObjectBuilder acls(boolean includeAcls) {
            this.includeAcls = includeAcls;
            return this;
        }
        @Override
        public PropertySetMapper<Json.MapContainer> build() {
            ValueMapper<Object> mapper = valueMapper != null ? 
                    valueMapper : jsonValueFormatter(() -> locale);
            return jsonMapContainerCompact(mapper, includeAcls);
        }
    }
    
    public static class XmlStreamerBuilder implements MapperFactory<Consumer<XMLStreamWriter>> {
        private boolean includeAcls = false;
        private Function<PropertySet, Optional<URL>> linkConstructor = null;
        private Locale locale;

        private XmlStreamerBuilder(Locale locale) {
            this.locale = Objects.requireNonNull(locale);
        }
        
        public XmlStreamerBuilder linkConstructor(Function<PropertySet, Optional<URL>> linkConstructor) {
            this.linkConstructor = linkConstructor;
            return this;
        }
        
        public XmlStreamerBuilder acls(boolean includeAcls) {
            this.includeAcls = includeAcls;
            return this;
        }

        @Override
        public PropertySetMapper<Consumer<XMLStreamWriter>> build() {
            Function<PropertySet, Optional<URL>> linkConstructor = 
                    this.linkConstructor != null ? this.linkConstructor : 
                        propset -> Optional.empty();
            return xmlStreamingMapper(defaultValueFormatter(locale), 
                    includeAcls, linkConstructor);
        }
    }
    
    private static PropertySetMapper<Json.MapContainer> 
        jsonMapContainerCompact(ValueMapper<Object> valueFormatter, boolean includeAcls) {
        return propset -> {
            Json.MapContainer map = new Json.MapContainer();
            map.put("uri", propset.getURI());
            map.put("type", propset.getResourceType());
            if (!propset.getProperties().isEmpty()) {
                Json.MapContainer props = new Json.MapContainer();
                for (Property p: propset) {
                    PropertyTypeDefinition def = p.getDefinition();

                    String name = def.getName();
                    if (def.getNamespace() != Namespace.DEFAULT_NAMESPACE) {
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
            if (includeAcls && propset.acl().isPresent()) {
                Json.MapContainer aclMap = new Json.MapContainer();
                Acl acl = propset.acl().get();
                writeAcl(acl, jsonObjectAclWriter(aclMap));
                map.put("acl", aclMap);
            }
            return map;
        };
    }
    
    /**
     * A property set mapper that writes to a {@link JsonStreamer} in a compact format
     * @param locale the locale used to format values
     * @return a property set mapper
     */
    private static PropertySetMapper<Consumer<JsonStreamer>> 
        jsonStreamerCompact(ValueMapper<Object> valueFormatter, boolean includeAcls) {
        return propset -> streamer -> {
            try {
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
                                streamer.value(valueFormatter.apply(def, v));
                            }
                            streamer.endArray();
                        }
                        else {
                            streamer.value(valueFormatter.apply(def, p.getValue()));
                        }
                    }
                    streamer.endObject();
                }
                if (includeAcls && propset.acl().isPresent()) {
                    Acl acl = propset.acl().get();
                    streamer.key("acl").beginObject();
                    writeAcl(acl, jsonStreamingAclWriter(streamer));
                    streamer.endObject();
                }
                streamer.endObject();
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
    
    
    private static PropertySetMapper<Consumer<JsonStreamer>> 
        jsonStreamerComplete(ValueMapper<Object> valueFormatter, boolean includeAcls) {
        return propset -> streamer -> {
            try {
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
                            .memberIfNotNull("ns", def.getNamespace().getUri())
                            .member("name", name)
                            .member("type", def.getType());

                        if (def.isMultiple()) {
                            streamer.key("values").beginArray();
                            if (def.getType() == PropertyType.Type.BINARY) {
                                streamer.value("<binary>");
                            }
                            else {
                                for (Value v: p.getValues()) {
                                    streamer.value(valueFormatter.apply(def, v));
                                }
                            }
                            streamer.endArray();
                        }
                        else {
                            streamer.member("value", valueFormatter.apply(def, p.getValue()));
                        }
                        streamer.endObject();
                    }
                    streamer.endArray();
                }
                
                if (includeAcls && propset.acl().isPresent()) {
                    Acl acl = propset.acl().get();
                    streamer.key("acl").beginObject();
                    writeAcl(acl, jsonStreamingAclWriter(streamer));
                    streamer.endObject();
                }
                streamer.endObject();
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
    
    private static PropertySetMapper<Consumer<XMLStreamWriter>> xmlStreamingMapper(ValueMapper<String> valueFormatter, 
            boolean includeAcls, Function<PropertySet, Optional<URL>> linkConstructor) {
        return propset -> xml -> {
            try {
                xml.writeStartElement("resource");
                xml.writeAttribute("name", propset.getName());
                xml.writeAttribute("type", propset.getResourceType().toString());
                xml.writeAttribute("uri", propset.getURI().toString());

                Optional<URL> url = linkConstructor.apply(propset);
                if (url.isPresent()) {
                    xml.writeAttribute("url", url.get().toString());
                }

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
                                xml.writeCharacters(valueFormatter.apply(def, v).toString());
                                xml.writeEndElement();
                            }
                        }
                        xml.writeEndElement();
                    }
                    else {
                        xml.writeStartElement("value");
                        xml.writeCharacters(valueFormatter.apply(def, p.getValue()).toString());
                        xml.writeEndElement();
                    }
                    xml.writeEndElement();
                }
                if (includeAcls && propset.acl().isPresent()) {
                    Acl acl = propset.acl().get();
                    xml.writeStartElement("acl");
                    writeAcl(acl, xmlStreamingAclWriter(xml));
                    xml.writeEndElement();
                }
                xml.writeEndElement();
            }
            catch (XMLStreamException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static void writeAcl(Acl acl, BiConsumer<Privilege, Map<String, List<Principal>>> writer) {
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
                entry.put("groups", groups);
            }
            if (!pseudo.isEmpty()) {
                entry.put("pseudo", pseudo);
            }
            if (!entry.isEmpty()) {
                writer.accept(action, entry);
            }
        });
    }
    
    
    private static BiConsumer<Privilege, Map<String, List<Principal>>> jsonObjectAclWriter(Json.MapContainer aclMap) {
        return (privilege, entry) -> {
            Json.MapContainer privilegeMap = new Json.MapContainer();
            for (String type: entry.keySet()) {
                List<String> principals = new ArrayList<>();
                for (Principal p: entry.get(type)) {
                    principals.add(p.getQualifiedName());
                }
                privilegeMap.put(type, principals);
            }
            aclMap.put(privilege.getName(), privilegeMap);
        };
    }
    
    private static BiConsumer<Privilege, Map<String, List<Principal>>> jsonStreamingAclWriter(JsonStreamer streamer) {
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
    
    
    private static ValueMapper<Object> jsonValueFormatter(Supplier<Locale> locale) {
        return (def, value) -> { 
            if (def.getType() == Type.DATE) {
                return def.getValueFormatter().valueToString(value, "iso-8601-short", locale.get());
            }
            else if (def.getType() == Type.TIMESTAMP) {
                return def.getValueFormatter().valueToString(value, "iso-8601", locale.get());
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
            return def.getValueFormatter().valueToString(value, null, locale.get());
        };
    }
    
    private static ValueMapper<String> defaultValueFormatter(Locale locale) {
        return (def, value) -> {
            if (def.getType() == Type.DATE) {
                return def.getValueFormatter().valueToString(value, "iso-8601-short", locale);
            }
            else if (def.getType() == Type.TIMESTAMP) {
                return def.getValueFormatter().valueToString(value, "iso-8601", locale);
            }
            return def.getValueFormatter().valueToString(value, null, locale);
        };
    }
    
    
    private static BiConsumer<Privilege, Map<String, List<Principal>>> xmlStreamingAclWriter(XMLStreamWriter xml) {
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
}
