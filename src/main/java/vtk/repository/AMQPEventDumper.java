/* Copyright (c) 2014, University of Oslo, Norway
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
package vtk.repository;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.context.ApplicationListener;

import vtk.repository.event.RepositoryEvent;
import vtk.repository.resourcetype.BinaryValue;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.security.Principal;
import vtk.util.text.Json;
import vtk.util.text.JsonStreamer;
import vtk.web.service.Service;
import vtk.web.service.URL;


public class AMQPEventDumper extends AbstractRepositoryEventDumper 
    implements ApplicationListener<RepositoryEvent> {
    
    private static Log logger = LogFactory.getLog(AMQPEventDumper.class);

    private AmqpTemplate template;
    private Service urlGenerator;
    private Map<String, PropertySerializer> serializers = null;
    
    public interface PropertySerializer {
        /**
         * Serializes a single property
         * @param property the property to serialize
         * @param dest the JSON object containing the properties
         */
        public void serialize(Property property, JsonStreamer streamer) throws Exception;
    }

    public static class SimplifiedLinksSerializer implements PropertySerializer {
        
        @Override
        public void serialize(Property property, JsonStreamer streamer) throws Exception {
            
            PropertyTypeDefinition def = property.getDefinition();
            if (!"hrefs".equals(def.getName())) return;

            Json.MapContainer data = property.getValue().getJSONValue();
            List<?> elements = (List<?>) data.get("links");
            streamer.key("links");
            streamer.beginArray();
            for (Object o: elements) {
                if (!(o instanceof Map<?, ?>)) continue;
                Map<?, ?> record = (Map<?, ?>) o;
                if (!record.containsKey("url")) continue;
                Object urlObj = record.get("url");
                if (urlObj == null) continue;
                String url = urlObj.toString();
                streamer.value(url);
            }
            streamer.endArray();
        }
    }
    
    /**
     * Default property serializer: produces <code>name = value</code> pairs in the destination object
     */
    public static PropertySerializer DEFAULT_PROPERTY_SERIALIZER = new PropertySerializer() {

        @Override
        public void serialize(Property property, JsonStreamer streamer) throws IOException {
            final PropertyTypeDefinition def = property.getDefinition();
            streamer.key(def.getName());
            if (def.isMultiple()) {
                streamer.beginArray();
                for (Value val: property.getValues()) {
                    Object mapped = mapToBasicValue(val);
                    streamer.value(mapped);
                }
                streamer.endArray();
            }
            else {
                Object mapped = mapToBasicValue(property.getValue());
                streamer.value(mapped);
            }
        }
        
        private Object mapToBasicValue(Value value) throws IOException {
            switch (value.getType()) {
            case BOOLEAN:
                return value.getBooleanValue();
            case DATE:
            case TIMESTAMP:
                return value.getDateValue().getTime();
            case JSON:
                return value.getJSONValue();
            case INT:
                return value.getIntValue();
            case LONG:
                return value.getLongValue();
            case PRINCIPAL:
                return value.getPrincipalValue().getQualifiedName();
            case BINARY:
                return mapBinaryValue(value.getBinaryValue());
            default:
                return value.getNativeStringRepresentation();
            }
        }
        
        private Object mapBinaryValue(BinaryValue value) throws IOException {
            if ("application/json".equals(value.getContentType())) {
                return Json.parse(new InputStreamReader(value.getContentStream().getStream()));
            }
           return null;
        }
        
    };
    
    public AMQPEventDumper(AmqpTemplate template, Service urlGenerator)  {
        this(template, urlGenerator, null);
    }
    
    public AMQPEventDumper(AmqpTemplate template, Service urlGenerator, Map<String, PropertySerializer> serializers) {
        this.template = template;
        this.urlGenerator = urlGenerator;
        this.serializers = serializers;
    }
    
    @Override
    public void created(Resource resource) {
        try {
            template.convertAndSend(updateMsg(resource, "created"));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleted(Resource resource) {
        try {
            template.convertAndSend(deletedMsg(resource.getURI()));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void moved(Resource resource, Resource from) {
        try {
            template.convertAndSend(movedMsg(from.getURI(), resource.getURI()));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public void modified(Resource resource, Resource originalResource) {
        try {
            template.convertAndSend(updateMsg(resource, "props_modified"));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void modifiedInheritableProperties(Resource resource,
            Resource originalResource) {
        try {
            template.convertAndSend(updateMsg(resource, "props_modified"));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void contentModified(Resource resource, Resource original) {
        try {
            template.convertAndSend(updateMsg(resource, "content_modified"));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void aclModified(Resource resource, Resource originalResource) {
        try {
            template.convertAndSend(updateMsg(resource, "acl_modified"));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private static final String VERSION = "0.1";
    

    private String updateMsg(Resource resource, String type) throws IOException {
        StringWriter writer = new StringWriter();
        JsonStreamer streamer = new JsonStreamer(writer);
        URL url = urlGenerator.constructURL(resource, 
                new vtk.security.PrincipalImpl("root@localhost", Principal.Type.USER));
        streamer.beginObject()
                .key("version").value(VERSION)
                .key("uri").value(url.toString())
                .key("type").value(type)
                .key("data").beginObject();
        
        streamer.key("properties").beginObject();
        properties(resource, streamer);
        streamer.endObject();
        
        streamer.key("acl").beginObject();
        acl(resource, streamer);
        streamer.endObject();
        
        streamer.endObject().endJson();
        return writer.toString();
    }
    
    private String deletedMsg(Path uri) throws IOException {
        StringWriter writer = new StringWriter();
        JsonStreamer streamer = new JsonStreamer(writer);
        URL url = urlGenerator.constructURL(uri);
        
        streamer.beginObject()
                .key("version").value(VERSION)
                .key("uri").value(url.toString())
                .key("type").value("deleted")
                .endObject()
                .endJson();
        return writer.toString();
    }

    private String movedMsg(Path from, Path to) throws IOException {
        StringWriter writer = new StringWriter();
        JsonStreamer streamer = new JsonStreamer(writer);
        streamer.beginObject()
                .key("version").value(VERSION)
                .key("type").value("moved")
                .key("from").value(urlGenerator.constructURL(from).toString())
                .key("to").value(urlGenerator.constructURL(to).toString())
                .endObject()
                .endJson();
        return writer.toString();
    }

    
    private void properties(Resource resource, JsonStreamer streamer) {
        
        for (Property prop: resource) {
            PropertyTypeDefinition def = prop.getDefinition();
            PropertySerializer serializer = serializers == null ? 
                    DEFAULT_PROPERTY_SERIALIZER : serializers.get(def.getName());
            if (serializer == null) continue;
            try {
                serializer.serialize(prop, streamer);
            }
            catch (Exception e) {
                logger.error("Failed to serialize property " + 
                        prop + " of resource " + resource, e);
            }
        }
    }

    private void acl(Resource resource, JsonStreamer streamer) throws IOException {
        Acl acl = resource.getAcl();
        Set<Privilege> actions = acl.getActions();
        for (Privilege action: actions) {
            streamer.key(action.getName()).beginArray();
            for (Principal p: acl.getPrincipalSet(action)) {
                streamer.value(p.getQualifiedName());
            }
            streamer.endArray();
        }
    }
    
}
