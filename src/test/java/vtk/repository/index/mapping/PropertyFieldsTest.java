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
package vtk.repository.index.mapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.index.IndexableField;
import org.jmock.auto.Mock;
import org.jmock.integration.junit4.JUnitRuleMockery;

import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.resourcetype.IllegalValueTypeException;
import vtk.repository.resourcetype.PropertyTypeDefinitionImpl;
import vtk.repository.resourcetype.Value;
import vtk.repository.resourcetype.ValueFormatter;

import static vtk.repository.resourcetype.PropertyType.Type;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.ValueFactory;
import vtk.util.text.Json;

/**
 *
 */
public class PropertyFieldsTest {

    private PropertyFields pf;

    @Mock
    private ValueFactory valueFactory;

    @Rule
    public JUnitRuleMockery context = new JUnitRuleMockery();

    @Before
    public void setUp() {
        // Currently no tests trigger internal use of ValueFactory in PropertFields, but
        // need non-null mocked value for construction.
        pf = new PropertyFields(Locale.getDefault(), valueFactory);
    }

    private Property getTypedProperty(Namespace namespace, String name, final Type type) {
        return getTypedProperty(namespace, name, type, null);
    }

    // Creates a typed property backed by a new prop def. Prop def instances are not
    // reused, so do not depend on that in code that uses this method.
    private Property getTypedProperty(Namespace namespace, String name, final Type type,
            Map<String, Object> metadata) {
        PropertyTypeDefinitionImpl propDef = new PropertyTypeDefinitionImpl();
        propDef.setNamespace(namespace);
        propDef.setName(name);
        if (metadata != null) {
            propDef.setMetadata(metadata);
        }
        propDef.setType(type);
        propDef.setValueFormatter(new ValueFormatter() {
            @Override
            public Value stringToValue(String string, String format,
                    Locale locale) {
                return new Value(string, type);
            }

            @Override
            public String valueToString(Value value, String format,
                    Locale locale) throws IllegalValueTypeException {
                return value.toString();
            }

        });
        propDef.afterPropertiesSet();
        return propDef.createProperty();
    }

    private Property getStringProperty(Namespace namespace, String name) {
        return getTypedProperty(namespace, name, Type.STRING);
    }

    @Test
    public void sortFieldName() {
        Property prop = getStringProperty(Namespace.getNamespaceFromPrefix("bar"), "foo");
        assertEquals("p_s_bar:foo", PropertyFields.sortFieldName(prop.getDefinition()));

        prop = getTypedProperty(Namespace.DEFAULT_NAMESPACE, "integer", Type.INT);
        assertEquals("p_integer", PropertyFields.sortFieldName(prop.getDefinition()));
    }

    @Test
    public void jsonSortFieldName() {
        Property prop = getTypedProperty(Namespace.getNamespaceFromPrefix("bar"), "foo", Type.JSON);
        assertEquals("p_s_bar:foo@baz", PropertyFields.jsonSortFieldName(prop.getDefinition(), "baz"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void combineLowercaseAndSortInSameField() {
        PropertyFields.propertyFieldName("foo", "bar", true, true);
    }

    @Test
    public void isPropertyField() {
        assertTrue(PropertyFields.isPropertyField("p_foo"));
        assertTrue(PropertyFields.isPropertyField("p_bar:foo"));
        assertTrue(PropertyFields.isPropertyField("p_l_bar:foo"));
        assertFalse(PropertyFields.isPropertyField(ResourceFields.NAME_FIELD_NAME));
        assertFalse(PropertyFields.isPropertyField(ResourceFields.NAME_LC_FIELD_NAME));
        assertFalse(PropertyFields.isPropertyField("what:ever"));
    }

    @Test
    public void getSearchFieldNameProperty() {

        Property prop = getStringProperty(Namespace.getNamespaceFromPrefix("bar"), "foo");

        assertEquals("p_bar:foo", PropertyFields.propertyFieldName(prop.getDefinition(), false));
        assertEquals("p_l_bar:foo", PropertyFields.propertyFieldName(prop.getDefinition(), true));

        prop = getStringProperty(Namespace.DEFAULT_NAMESPACE, "lastModified");

        assertEquals("p_lastModified", PropertyFields.propertyFieldName(prop.getDefinition(), false));

        assertEquals("p_l_lastModified", PropertyFields.propertyFieldName(prop.getDefinition(), true));

    }

    @Test
    public void getJsonSearchFieldName() {
        Property prop = getStringProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE, "complex");
        assertEquals("p_resource:complex@attr1", PropertyFields.jsonFieldName(prop.getDefinition(), "attr1", false));
        assertEquals("p_l_resource:complex@attr1", PropertyFields.jsonFieldName(prop.getDefinition(), "attr1", true));

        prop = getStringProperty(Namespace.DEFAULT_NAMESPACE, "system-job-status");
        assertEquals("p_system-job-status@attr1", PropertyFields.jsonFieldName(prop.getDefinition(), "attr1", false));
        assertEquals("p_l_system-job-status@attr1", PropertyFields.jsonFieldName(prop.getDefinition(), "attr1", true));
    }
    
    @Test
    public void addJsonPropertyFields_hrefs() {
        final String hrefsJson = "{\n" +
                    "  \"size\": 2,\n" +
                    "  \"links\": [\n" +
                    "    {\n" +
                    "      \"reltype\": \"RELATIVE\",\n" +
                    "      \"source\": \"PROPERTIES\",\n" +
                    "      \"type\": \"ANCHOR\",\n" +
                    "      \"url\": \"/\"\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"reltype\": \"RELATIVE\",\n" +
                    "      \"source\": \"PROPERTIES\",\n" +
                    "      \"type\": \"ANCHOR\",\n" +
                    "      \"url\": \"/vrtx\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";
        /*
            String hrefsJson pretty printed:
            {
              "size": 2,
              "links": [
                {
                  "reltype": "RELATIVE",
                  "source": "PROPERTIES",
                  "type": "ANCHOR",
                  "url": "/"
                },
                {
                  "reltype": "RELATIVE",
                  "source": "PROPERTIES",
                  "type": "ANCHOR",
                  "url": "/vrtx"
                }
              ]
            }
        */
        
        Map<String, Object> propDefMetadata = new HashMap<>();
        propDefMetadata.put(PropertyTypeDefinition.METADATA_INDEXABLE_JSON, true);
        propDefMetadata.put(PropertyTypeDefinition.METADATA_INDEXABLE_JSON_TYPEHINT_FIELD_PREFIX 
                + "size", "INT");
        
        Property prop = getTypedProperty(Namespace.DEFAULT_NAMESPACE, "hrefs", Type.JSON, propDefMetadata);
        prop.setJSONValue(Json.parseToContainer(hrefsJson).asObject());
        
        List<IndexableField> fields = new ArrayList<>();
        pf.addJsonPropertyFields(fields, prop);
        assertEquals(18, fields.size());
        
        assertHasIndexableFieldWithValue(fields, "p_hrefs@size", 2);
        assertHasIndexableFieldWithValue(fields, "p_hrefs@links.url", "/");
        assertHasIndexableFieldWithValue(fields, "p_l_hrefs@links.url", "/");
        assertHasIndexableFieldWithValue(fields, "p_hrefs@links.url", "/vrtx");
        assertHasIndexableFieldWithValue(fields, "p_l_hrefs@links.url", "/vrtx");

        assertHasIndexableFieldWithValue(fields, "p_hrefs@links.reltype", "RELATIVE");
        assertHasIndexableFieldWithValue(fields, "p_l_hrefs@links.reltype", "relative");
        
        assertHasIndexableFieldWithValue(fields, "p_hrefs@links.source", "PROPERTIES");
        assertHasIndexableFieldWithValue(fields, "p_l_hrefs@links.source", "properties");

        assertHasIndexableFieldWithValue(fields, "p_hrefs@links.type", "ANCHOR");
        assertHasIndexableFieldWithValue(fields, "p_l_hrefs@links.type", "anchor");
        
        // Note that no dedicated sorting fields should be present because all fields
        // are either multi valued or numeric.
        
        int countLinksUrl=0, countLinksReltype=0, countLinksSource=0, countLinksType=0;
        for (IndexableField f: fields) {
            if (f.name().endsWith("hrefs@links.url")) {
                ++countLinksUrl;
            } else if (f.name().endsWith("hrefs@links.reltype")) {
                ++countLinksReltype;
            } else if (f.name().endsWith("hrefs@links.type")) {
                ++countLinksType;
            } else if (f.name().endsWith("hrefs@links.source")) {
                ++countLinksSource;
            } else if (! (f.name().equals("p_hrefs") || f.name().equals("p_hrefs@size"))) {
                fail("Unexpected field: " + f);
            }
        }
        assertEquals(4, countLinksUrl);
        assertEquals(4, countLinksReltype);
        assertEquals(4, countLinksSource);
        assertEquals(4, countLinksType);
    }

    @Test
    public void addJsonPropertyFields_onlyStored() {
        Property prop = getTypedProperty(Namespace.DEFAULT_NAMESPACE, "jsonProp", Type.JSON);
        Json.MapContainer jsonData = new Json.MapContainer();
        jsonData.put("foo", "Value of foo");
        jsonData.put("bar", "Value of bar");
        jsonData.put("baz", Arrays.asList(new String[]{"a", "b", "c"}));

        prop.setJSONValue(jsonData);

        List<IndexableField> fields = new ArrayList<>();
        pf.addJsonPropertyFields(fields, prop);

        assertEquals(1, fields.size());
        IndexableField storedField = fields.get(0);
        assertEquals("p_jsonProp", storedField.name());
        assertEquals("{\"foo\":\"Value of foo\",\"bar\":\"Value of bar\",\"baz\":[\"a\",\"b\",\"c\"]}", storedField.stringValue());
    }

    @Test
    public void addJsonPropertyFields_storedIndexed() {
        Map<String, Object> propDefMetadata = new HashMap<>();
        propDefMetadata.put(PropertyTypeDefinition.METADATA_INDEXABLE_JSON, true);
        Property prop = getTypedProperty(Namespace.DEFAULT_NAMESPACE, "jsonProp", Type.JSON, propDefMetadata);
        Json.MapContainer jsonData = new Json.MapContainer();
        jsonData.put("foo", "Value of foo");
        jsonData.put("bar", "Value of bar");
        jsonData.put("baz", Arrays.asList(new String[]{"a", "b", "c"}));
        prop.setJSONValue(jsonData);

        List<IndexableField> fields = new ArrayList<>();
        pf.addJsonPropertyFields(fields, prop);

        assertEquals(13, fields.size());

        // Stored field for entire JSON value
        IndexableField f = assertHasIndexableFieldWithValue(fields, "p_jsonProp", 
         "{\"foo\":\"Value of foo\",\"bar\":\"Value of bar\",\"baz\":[\"a\",\"b\",\"c\"]}");
        assertTrue(f.fieldType().stored());

        assertHasIndexableFieldWithValue(fields, "p_jsonProp@bar", "Value of bar");
        assertHasIndexableFieldWithValue(fields, "p_l_jsonProp@bar", "value of bar");
        assertHasIndexableFieldWithValue(fields, "p_s_jsonProp@bar", null);
        
        assertHasIndexableFieldWithValue(fields, "p_jsonProp@foo", "Value of foo");
        assertHasIndexableFieldWithValue(fields, "p_l_jsonProp@foo", "value of foo");
        assertHasIndexableFieldWithValue(fields, "p_s_jsonProp@foo", null);
        
        assertHasIndexableFieldWithValue(fields, "p_jsonProp@baz", "a");
        assertHasIndexableFieldWithValue(fields, "p_jsonProp@baz", "b");
        assertHasIndexableFieldWithValue(fields, "p_jsonProp@baz", "c");
        assertHasIndexableFieldWithValue(fields, "p_l_jsonProp@baz", "a");
        assertHasIndexableFieldWithValue(fields, "p_l_jsonProp@baz", "b");
        assertHasIndexableFieldWithValue(fields, "p_l_jsonProp@baz", "c");
    }

    private IndexableField assertHasIndexableFieldWithValue(List<IndexableField> fields, String name, Object value) {
        for (IndexableField f : fields) {
            if (f.name().equals(name)) {
                if (value != null) {
                    if (f.numericValue() != null && value.equals(f.numericValue())) {
                        return f;
                    } else if (f.binaryValue() != null && value.equals(f.binaryValue())) {
                        return f;
                    } else if (f.stringValue() != null && value.equals(f.stringValue())) {
                        return f;
                    }
                } else {
                    return f;
                }
            }
        }
        fail("No IndexableField with name " + name + " found in list.");
        return null;
    }

    @Test
    public void isStoredFieldInNamespace() {
        assertTrue(PropertyFields.isPropertyFieldInNamespace("p_title", Namespace.DEFAULT_NAMESPACE));
        assertTrue(PropertyFields.isPropertyFieldInNamespace("p_owner", Namespace.DEFAULT_NAMESPACE));
        assertFalse(PropertyFields.isPropertyFieldInNamespace("p_resource:author", Namespace.DEFAULT_NAMESPACE));

        assertTrue(PropertyFields.isPropertyFieldInNamespace("p_resource:author", Namespace.STRUCTURED_RESOURCE_NAMESPACE));
        assertFalse(PropertyFields.isPropertyFieldInNamespace("p_resource:author", Namespace.DEFAULT_NAMESPACE));

        assertFalse(PropertyFields.isPropertyFieldInNamespace("p_content:keywords", Namespace.STRUCTURED_RESOURCE_NAMESPACE));
        assertFalse(PropertyFields.isPropertyFieldInNamespace("p_content:keywords", Namespace.DEFAULT_NAMESPACE));
    }

    @Test
    public void getSearchFieldNamePropertyTypeDefinition() {

        PropertyTypeDefinitionImpl def
                = new PropertyTypeDefinitionImpl();

        def.setName("foo");
        def.setNamespace(Namespace.getNamespaceFromPrefix("bar"));

        assertEquals("p_bar:foo", PropertyFields.propertyFieldName(def, false));
        assertEquals("p_l_bar:foo", PropertyFields.propertyFieldName(def, true));

        def = new PropertyTypeDefinitionImpl();
        def.setName("lastModified");
        def.setNamespace(Namespace.DEFAULT_NAMESPACE);

        assertEquals("p_lastModified", PropertyFields.propertyFieldName(def, false));
        assertEquals("p_l_lastModified", PropertyFields.propertyFieldName(def, true));

    }

    @Test
    public void getSearchFieldNameStringString() {

        String fieldName = PropertyFields.propertyFieldName("foo", null, false, false);
        assertEquals("p_foo", fieldName);

        fieldName = PropertyFields.propertyFieldName("foo", null, true, false);
        assertEquals("p_l_foo", fieldName);

        fieldName = PropertyFields.propertyFieldName("bar", "foo", false, false);
        assertEquals("p_foo:bar", fieldName);

        fieldName = PropertyFields.propertyFieldName("bar", "foo", true, false);
        assertEquals("p_l_foo:bar", fieldName);
    }

    @Test
    public void getStoredFieldNamePropertyTypeDefinition() {

        PropertyTypeDefinitionImpl def
                = new PropertyTypeDefinitionImpl();
        def.setName("foo");
        def.setNamespace(Namespace.getNamespaceFromPrefix("bar"));

        String fieldName = PropertyFields.propertyFieldName(def);

        assertEquals("p_bar:foo", fieldName);

        def = new PropertyTypeDefinitionImpl();
        def.setName("lastModified");
        def.setNamespace(Namespace.DEFAULT_NAMESPACE);

        fieldName = PropertyFields.propertyFieldName(def);

        assertEquals("p_lastModified", fieldName);

    }

    @Test
    public void getPropertyNamespacePrefixFromStoredFieldName() {

        String fieldName = "p_foo";

        String nsPrefix = PropertyFields.propertyNamespacePrefix(fieldName);

        assertNull(nsPrefix);

        fieldName = "p_bar:foo";

        nsPrefix = PropertyFields.propertyNamespacePrefix(fieldName);

        assertEquals("bar", nsPrefix);
    }

    @Test
    public void getPropertyNameFromStoredFieldName() {
        String fieldName = "p_foo";

        String name = PropertyFields.propertyName(fieldName);

        assertEquals("foo", name);

        fieldName = "p_bar:foo";

        name = PropertyFields.propertyName(fieldName);

        assertEquals("foo", name);
    }

}
