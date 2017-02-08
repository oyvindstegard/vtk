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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.PropertyImpl;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinitionImpl;
import vtk.repository.resourcetype.Value;
import vtk.resourcemanagement.StructuredResource;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.property.JSONPropertyAttributeDescription;
import vtk.resourcemanagement.property.JSONPropertyDescription;
import vtk.resourcemanagement.property.PropertyDescription;
import vtk.resourcemanagement.property.SimplePropertyDescription;
import vtk.text.html.HtmlPageParser;
import vtk.util.io.IO;
import vtk.util.text.Json;
import vtk.util.text.JsonStreamer;

public class LinkReplacerTest {

    @Before
    public void setUp() throws Exception {
    }
    
    @Test
    public void testLinkProperties() {
        
        Resource r = mockResource("text/plain", 
                singleProperty("prop1", Type.IMAGE_REF, "/link1"),
                singleProperty("prop2", Type.HTML, "<a href=\"/link2\">link</a>"),
                singleProperty("prop3", Type.IMAGE_REF, "/unmapped/link"),
                multiProperty("prop4", Type.IMAGE_REF,
                        "/link3", "/link4", "/link5"));
        
        LinkReplacer.Context ctx = new AbstractContext() {
            
            @Override
            public Resource resource() {
                return r;
            }
            
            @Override
            public Optional<String> mapURL(String input) {
                switch(input) {
                case "/link1": 
                case "/link2": 
                case "/link3": 
                case "/link4": 
                case "/link5": 
                    return Optional.of(input + "/mapped");
                default: 
                    return Optional.empty();
                }
            }
        };
        
        LinkReplacer.process(ctx);
        assertEquals("/link1/mapped", r.getProperties().get(0).getStringValue());
        assertEquals("<a href=\"/link2/mapped\">link</a>", 
                r.getProperties().get(1).getStringValue());
        assertEquals("/unmapped/link", 
                r.getProperties().get(2).getStringValue());
        
        Property multi = r.getProperties().get(3);
        assertTrue(multi.getDefinition().isMultiple());
        Value[] values = multi.getValues();
        assertEquals(multi.getValues().length, 3);
        assertEquals("/link3/mapped", values[0].getStringValue());
        assertEquals("/link4/mapped", values[1].getStringValue());
        assertEquals("/link5/mapped", values[2].getStringValue());
    }
    
    @Test
    public void testStructuredResource() {
        StructuredResourceDescription desc = 
                jsonResourceDescription("test",
                jsonField("picture", "image_ref"),
                jsonField("intro", "simple_html"),
                jsonField("json-simple", "json", 
                        jsonField("field1", "resource_ref"),
                        jsonField("field2", "html")),
                jsonField("json-multi", "json[]",
                        jsonField("field1", "resource_ref"),
                        jsonField("field2", "html")));
        
        Resource r = mockResource("application/json");
        
        String inputContent = 
                "{ \"resourcetype\":\"test\",\n"
                + "  \"properties\":{\n"
                + "    \"picture\":\"/pictures/img01.png\",\n"  
                + "    \"intro\":\"<p><a href=\\\"/my/link1\\\">link</a></p>\",\n"
                + "    \"json-simple\":{\n"
                + "       \"field1\":\"/resource/ref1\"\n"
                + "       \"field2\":\"<p><a href=\\\"/my/link2\\\">link</a></p>\",\n"
                + "     }\n"
                + "    \"json-multi\":[{\n"
                + "       \"field1\":\"/resource/ref1\"\n"
                + "       \"field2\":\"<p><a href=\\\"/my/link2\\\">link</a></p>\",\n"
                + "     }]\n"
                + "  }\n"
                + "}\n";
        
        Map<String, String> replacements = new HashMap<>();
        replacements.put("/pictures/img01.png", "/pictures/img02.png");
        replacements.put("/resource/ref1", "/resource/ref2");
        replacements.put("/my/link1", "/moved/link1");
        replacements.put("/my/link2", "/moved/link2");
        
        final StringBuilder resultContent = new StringBuilder();
        
        LinkReplacer.Context ctx = new AbstractContext() {
            
            @Override
            public Resource resource() {
                return r;
            }
            
            @Override
            public Optional<String> mapURL(String input) {
                return Optional.ofNullable(replacements.get(input));
            }
            
            @Override
            public void writeRevision(StructuredResource res) {
                resultContent.append(JsonStreamer.toJson(res.toJSON()));
            }
            
            @Override
            public Optional<InputStream> content() {
                InputStream content = new ByteArrayInputStream(
                        inputContent.getBytes(StandardCharsets.UTF_8));
                return Optional.of(content);
            }
            
            @Override
            public Optional<StructuredResourceDescription> resourceDescription() {
                return Optional.of(desc);
            }
        };
        LinkReplacer.process(ctx);
        String content = inputContent;
        for (String key: replacements.keySet()) {
            content = content.replace(key, replacements.get(key));
        }
        assertEquals(resultContent.toString(), JsonStreamer.toJson(Json.parse(content)));
    }
    
    @Test
    public void testHtmlContent() {
        String content = 
                "<!DOCTYPE html>\n"
                + "<html>\n"
                + "  <head>\n"
                + "    <link rel=\"shortcut icon\" href=\"/resources/favicon1.ico\">\n"
                + "    <script src=\"/resources/script1.js\"></script>\n"
                + "    <title>My title</title>\n"
                + "  </head>\n" 
                + "  <body>\n"
                + "    <p>Some text</p>\n"
                + "    <a href=\"/articles/article1.html\"><img src=\"/images/picture1.png\" /></a>\n"
                + "    <p>Some more text</p>\n"
                + "    <iframe title=\"My iframe\" src=\"/apps/iframe1\"></iframe>\n"
                + " </body>\n" 
                + "</html>\n";
        
        Resource r = mockResource("text/html");
        
        Map<String, String> replacements = new HashMap<>();
        replacements.put("/resources/favicon1.ico", "/resources/favicon2.ico");
        replacements.put("/resources/script1.js", "/resources/script2.js");
        replacements.put("/articles/article1.html", "/articles/article2.html");
        replacements.put("/images/picture1.png", "/images/picture2.png");
        replacements.put("/apps/iframe1", "/apps/iframe2");
        

        StringBuilder result = new StringBuilder();
        
        LinkReplacer.Context ctx = new AbstractContext() {
            
            @Override
            public Resource resource() {
                return r;
            }
            
            @Override
            public Optional<String> mapURL(String input) {
                return Optional.ofNullable(replacements.get(input));
            }
            
            @Override
            public Optional<InputStream> content() {
                InputStream stream = new ByteArrayInputStream(
                        content.getBytes(StandardCharsets.UTF_8));
                return Optional.of(stream);
            }
            
            @Override
            public void writeContent(InputStream content) {
                try {
                    String str = IO.readString(content, "utf-8").perform();
                    result.append(str);
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        
        LinkReplacer.process(ctx);
        String s = content;
        for (String key: replacements.keySet()) {
            s = s.replace(key, replacements.get(key));
        }
        
        HtmlPageParser parser = new HtmlPageParser();
        try {
            String s1 = parser.parse(IO.stringStream(s, "utf-8"), "utf-8")
                    .getStringRepresentation();
            String s2 = parser.parse(IO.stringStream(result.toString(), "utf-8"), "utf-8")
                    .getStringRepresentation();
            assertEquals(s1, s2);
            
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private static abstract class AbstractContext implements LinkReplacer.Context {
        
        @Override
        public abstract Resource resource();

        @Override
        public Optional<String> mapURL(String input) {
            return Optional.empty();
        }

        @Override
        public Optional<StructuredResourceDescription> resourceDescription() {
            return Optional.empty();
        }

        @Override
        public void storeProperties() {
        }

        @Override
        public Optional<InputStream> content() {
            return Optional.empty();
        }

        @Override
        public void writeContent(InputStream content) {
        }

        @Override
        public void writeRevision(StructuredResource res) {
        }

        @Override
        public void log(String from, String to, String label) {
        }
    }

    private Resource mockResource(String contentType, Property... properties) {
        List<Property> propertyList = Arrays.asList(properties);

        Resource mock = Mockito.mock(Resource.class);
        Mockito.when(mock.getContentType()).thenReturn(contentType);
        Mockito.when(mock.getProperties()).thenReturn(propertyList);
        Mockito.when(mock.iterator()).thenReturn(propertyList.iterator());
        return mock;
    }
    
    private Property singleProperty(String name, Type type, String value) {
        PropertyTypeDefinitionImpl def = new PropertyTypeDefinitionImpl();
        def.setNamespace(Namespace.DEFAULT_NAMESPACE);
        def.setName(name);
        def.setType(type);
        PropertyImpl p = new PropertyImpl(def);
        p.setStringValue(value);
        return p;
    }
    
    private Property multiProperty(String name, Type type, String... values) {
        PropertyTypeDefinitionImpl def = new PropertyTypeDefinitionImpl();
        def.setNamespace(Namespace.DEFAULT_NAMESPACE);
        def.setName(name);
        def.setType(type);
        def.setMultiple(true);
        PropertyImpl p = new PropertyImpl(def);
        List<Value> list = new ArrayList<>();
        for (String v: values) {
            list.add(new Value(v, type));
        }
        p.setValues(list.toArray(new Value[list.size()]));
        return p;
    }

    private StructuredResourceDescription jsonResourceDescription(
            String name, PropertyDescription... props) {

        StructuredResourceDescription desc = new StructuredResourceDescription();
        desc.setName(name);
        desc.setPropertyDescriptions(Arrays.asList(props));
        return desc;
    }

    private PropertyDescription jsonField(String name, String type, 
            PropertyDescription... subfields) {
        PropertyDescription desc;
        String t = type;
        boolean multiple = false;
        if (type.endsWith("[]")) {
            t = type.substring(0, type.length() - 2);
            multiple = true;
        }
        switch (t) {
        case "image_ref":
        case "media_ref":
        case "resource_ref":
            desc = new SimplePropertyDescription();
            break;
        case "html":
        case "simple_html":
            desc = new SimplePropertyDescription();
            break;
        case "json":
            desc = new JSONPropertyDescription();
            if (subfields.length > 0) {
                for (PropertyDescription field: subfields) {
                    JSONPropertyAttributeDescription attr = 
                            new JSONPropertyAttributeDescription();
                    attr.setName(field.getName());
                    attr.setType(field.getType());
                    ((JSONPropertyDescription) desc).addAttribute(attr);
                }
            }
            break;
        default:
            throw new IllegalArgumentException("Unsupported property type: " + type);
        }
        desc.setMultiple(multiple);
        desc.setName(name);
        desc.setType(t);
        return desc;
    }

}
