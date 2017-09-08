/* Copyright (c) 2014-2017, University of Oslo, Norway
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;

import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.resourcetype.IllegalValueTypeException;
import vtk.repository.resourcetype.PropertyTypeDefinitionImpl;
import vtk.repository.resourcetype.Value;
import vtk.repository.resourcetype.ValueFormatter;

import static vtk.repository.resourcetype.PropertyType.Type;

import static org.junit.Assert.*;
import org.junit.Test;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.ValueFactory;
import vtk.repository.resourcetype.ValueFactoryImpl;
import vtk.testing.mocktypes.MockPrincipalFactory;
import vtk.util.text.Json;

/**
 *
 */
public class PropertyFieldsTest {

    private final PropertyFields pf;

    private final ValueFactory valueFactory;

    public PropertyFieldsTest() {
        ValueFactoryImpl vf = new ValueFactoryImpl();
        vf.setPrincipalFactory(new MockPrincipalFactory());
        valueFactory = vf;
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
    public void dateValueIndexFieldEncoding() {

        String[] dateFormats = new String[]{"Long-format",
            "yyyy-MM-dd HH:mm:ss Z", "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm", "yyyy-MM-dd HH", "yyyy-MM-dd"};

        Date now = new Date();
        String[] dateStrings = new String[]{Long.toString(now.getTime()),
            "2005-10-10 14:22:00 +0100", "2005-10-10 14:22:00",
            "2005-10-10 14:22", "2005-10-10 14", "2005-10-10"};

        for (int i = 0; i < dateStrings.length; i++) {
            try {
                pf.propertyFieldQuery("someDate", dateStrings[i],
                        PropertyType.Type.TIMESTAMP, false);
            } catch (Exception e) {
                fail("Failed to encode index field value for date format '" + dateFormats[i]
                        + "', date string '" + dateStrings[i] + "':"
                        + e.getMessage());
            }
        }
    }

    @Test
    public void storedFields() {

        IndexableField stringField = pf.makeFields("string", "bâr", Fields.FieldSpec.STORED).get(0);
        IndexableField intField = pf.makeFields("int", 1024, Fields.FieldSpec.STORED).get(0);
        IndexableField longField = pf.makeFields("long", Long.MAX_VALUE, Fields.FieldSpec.STORED).get(0);

        assertEquals("string", stringField.name());
        assertEquals("int", intField.name());
        assertEquals("long", longField.name());

        assertEquals("bâr", stringField.stringValue());
        assertNull(stringField.binaryValue());
        assertNull(stringField.numericValue());

        assertEquals(1024, intField.numericValue().intValue());
        assertEquals("1024", intField.stringValue());
        assertNull(intField.binaryValue());

        assertEquals(Long.MAX_VALUE, longField.numericValue().longValue());
        assertEquals(Long.toString(Long.MAX_VALUE), longField.stringValue());
        assertNull(longField.binaryValue());

        Value stringValue = pf.valueFromField(Type.STRING, stringField);
        assertEquals("bâr", stringValue.getStringValue());

        Value intValue = pf.valueFromField(Type.INT, intField);
        assertEquals(1024, intValue.getIntValue());

        Value longValue = pf.valueFromField(Type.LONG, longField);
        assertEquals(Long.MAX_VALUE, longValue.getLongValue());
    }


    @Test
    public void dateFields() {
        final long nowTime = new Date().getTime();
        List<IndexableField> fields = pf.makeFields("date", new Date(nowTime), Fields.FieldSpec.INDEXED_STORED);

        assertEquals(2, fields.size());
        boolean haveStored = false;
        boolean haveIndexed = false;
        for (IndexableField f : fields) {
            if (f.fieldType().stored()) {
                long fieldValue = f.numericValue().longValue();
                assertEquals(nowTime, fieldValue);
                haveStored = true;
            }
            if (!f.fieldType().stored()) {
                long fieldValue = f.numericValue().longValue();
                assertEquals(DateTools.round(nowTime, DateTools.Resolution.SECOND), fieldValue);
                haveIndexed = true;
            }
        }
        if (! (haveIndexed && haveStored)) {
            fail("Expected one indexed and one stored field");
        }
    }

    @Test
    public void isLowercaseField() {
        assertFalse(PropertyFields.isLowercaseField("p_foo"));
        assertFalse(PropertyFields.isLowercaseField("p_bar:foo"));
        assertTrue(PropertyFields.isLowercaseField("p_l_bar:foo"));
        assertFalse(PropertyFields.isLowercaseField(ResourceFields.NAME_FIELD_NAME));
        assertTrue(PropertyFields.isLowercaseField(ResourceFields.NAME_LC_FIELD_NAME));
        assertFalse(PropertyFields.isLowercaseField("what:ever"));
    }

    @Test
    public void multithreadedDateValueIndexFieldEncoding() {

        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    dateValueIndexFieldEncoding();
                }
            });
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException ie) {
                fail("Interrupted while waiting for test threads to finish.");
            }
        }
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

        Document doc = new Document();
        List<IndexableField> fields = doc.getFields();
        pf.addJsonPropertyFields(doc, prop);
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

        Document doc = new Document();
        List<IndexableField> fields = doc.getFields();
        pf.addJsonPropertyFields(doc, prop);

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

        Document doc = new Document();
        List<IndexableField> fields = doc.getFields();
        pf.addJsonPropertyFields(doc, prop);

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

    @Test
    public void propertyFieldQuery_exactMatch_stringLowercase() {
        Query q;
        q = pf.propertyFieldQuery("p_l_a", "B", PropertyType.Type.STRING, true);
        assertEquals("p_l_a:b", q.toString());
        q = pf.propertyFieldQuery("p_l_a", "B@UIO.NO", PropertyType.Type.PRINCIPAL, true);
        assertEquals("p_l_a:b@uio.no", q.toString());
    }

    @Test
    public void propertyFieldQuery_exactMatch_string() {
        Query q;
        q = pf.propertyFieldQuery("p_a", "b", PropertyType.Type.STRING, false);
        assertEquals("p_a:b", q.toString());
        q = pf.propertyFieldQuery("p_a", "b@uio.no", PropertyType.Type.PRINCIPAL, false);
        assertEquals("p_a:b@uio.no", q.toString());
    }

    @Test
    public void propertyFieldQuery_exactMatch_int() {
        Query q;
        q = pf.propertyFieldQuery("p_a", "1", PropertyType.Type.INT, false);
        assertEquals("p_a:[1 TO 1]", q.toString());
        q = pf.propertyFieldQuery("p_a", 1, PropertyType.Type.INT, false);
        assertEquals("p_a:[1 TO 1]", q.toString());
    }

    @Test
    public void propertyFieldQuery_exactMatch_long() {
        Query q;
        q = pf.propertyFieldQuery("p_a", "1", PropertyType.Type.LONG, false);
        assertEquals("p_a:[1 TO 1]", q.toString());
        q = pf.propertyFieldQuery("p_a", 1L, PropertyType.Type.LONG, false);
        assertEquals("p_a:[1 TO 1]", q.toString());
    }

    @Test
    public void propertyFieldQuery_exactMatch_boolean() {
        Query q;
        q = pf.propertyFieldQuery("p_a", "true", PropertyType.Type.BOOLEAN, false);
        assertEquals("p_a:true", q.toString());
        q = pf.propertyFieldQuery("p_a", "false", PropertyType.Type.BOOLEAN, false);
        assertEquals("p_a:false", q.toString());
    }

    @Test
    public void propertyFieldQuery_exactMatch_date() {
        Query q;
        Date date = Date.from(Instant.parse("2017-12-05T00:00:00.00Z"));
        long millisEpoch = date.getTime();

        q = pf.propertyFieldQuery("p_a", date, PropertyType.Type.DATE, false);
        assertEquals("p_a:[" + millisEpoch + " TO " + millisEpoch + "]", q.toString());
        q = pf.propertyFieldQuery("p_a", date, PropertyType.Type.TIMESTAMP, false);
        assertEquals("p_a:[" + millisEpoch + " TO " + millisEpoch + "]", q.toString());

        q = pf.propertyFieldQuery("p_a", "2017-12-05 00:00:00 +0000", PropertyType.Type.DATE, false);
        assertEquals("p_a:[" + millisEpoch + " TO " + millisEpoch + "]", q.toString());
        q = pf.propertyFieldQuery("p_a", "2017-12-05 00:00:00 +0000", PropertyType.Type.TIMESTAMP, false);
        assertEquals("p_a:[" + millisEpoch + " TO " + millisEpoch + "]", q.toString());

        q = pf.propertyFieldQuery("p_a", millisEpoch, PropertyType.Type.TIMESTAMP, false);
        assertEquals("p_a:[" + millisEpoch + " TO " + millisEpoch + "]", q.toString());
        q = pf.propertyFieldQuery("p_a", Long.toString(millisEpoch), PropertyType.Type.TIMESTAMP, false);
        assertEquals("p_a:[" + millisEpoch + " TO " + millisEpoch + "]", q.toString());
    }


    @Test
    public void propertyFieldQuery_range_string() {
        Query q;

        q = pf.propertyFieldRangeQuery("p_a", "b", "c", true, true, PropertyType.Type.STRING, false);
        assertEquals("p_a:[b TO c]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", "b", "c", false, false, PropertyType.Type.STRING, false);
        assertEquals("p_a:{b TO c}", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", "b", "c", true, false, PropertyType.Type.STRING, false);
        assertEquals("p_a:[b TO c}", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", "b", "c", false, true, PropertyType.Type.STRING, false);
        assertEquals("p_a:{b TO c]", q.toString());
    }

    @Test
    public void propertyFieldQuery_range_string_unbounded() {
        Query q;

        q = pf.propertyFieldRangeQuery("p_a", null, null, true, true, PropertyType.Type.STRING, false);
        assertEquals("p_a:[* TO *]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", "b", null, false, true, PropertyType.Type.STRING, false);
        assertEquals("p_a:{b TO *]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", null, "c", true, false, PropertyType.Type.STRING, false);
        assertEquals("p_a:[* TO c}", q.toString());

    }

    @Test
    public void propertyFieldQuery_range_int() {
        Query q;

        q = pf.propertyFieldRangeQuery("p_a", 0, 1000, true, true, PropertyType.Type.INT, false);
        assertEquals("p_a:[0 TO 1000]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", 0, 1000, false, false, PropertyType.Type.INT, false);
        assertEquals("p_a:[1 TO 999]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", 0, 1000, true, false, PropertyType.Type.INT, false);
        assertEquals("p_a:[0 TO 999]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", 0, 1000, false, true, PropertyType.Type.INT, false);
        assertEquals("p_a:[1 TO 1000]", q.toString());

        // Test parseing when bounds are string objects
        q = pf.propertyFieldRangeQuery("p_a", "0", "1000", false, false, PropertyType.Type.INT, false);
        assertEquals("p_a:[1 TO 999]", q.toString());

    }

    @Test
    public void propertyFieldQuery_range_int_unbounded() {
        Query q;

        q = pf.propertyFieldRangeQuery("p_a", 0, null, true, true, PropertyType.Type.INT, false);
        assertEquals("p_a:[0 TO "  + Integer.MAX_VALUE + "]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", null, 1000, true, true, PropertyType.Type.INT, false);
        assertEquals("p_a:[" + Integer.MIN_VALUE + " TO 1000]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", null, null, true, true, PropertyType.Type.INT, false);
        assertEquals("p_a:[" + Integer.MIN_VALUE + " TO " + Integer.MAX_VALUE + "]", q.toString());

    }

    @Test
    public void propertyFieldQuery_range_long() {
        Query q;

        q = pf.propertyFieldRangeQuery("p_a", 0L, 1000L, true, true, PropertyType.Type.LONG, false);
        assertEquals("p_a:[0 TO 1000]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", 0L, 1000L, false, false, PropertyType.Type.LONG, false);
        assertEquals("p_a:[1 TO 999]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", 0L, 1000L, true, false, PropertyType.Type.LONG, false);
        assertEquals("p_a:[0 TO 999]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", 0L, 1000L, false, true, PropertyType.Type.LONG, false);
        assertEquals("p_a:[1 TO 1000]", q.toString());

        // Test parseing when bounds are string objects
        q = pf.propertyFieldRangeQuery("p_a", "0", Long.toString(Long.MAX_VALUE), false, false, PropertyType.Type.LONG, false);
        assertEquals("p_a:[1 TO "+ (Long.MAX_VALUE-1)+"]", q.toString());

    }

    @Test
    public void propertyFieldQuery_range_long_unbounded() {
        Query q;

        q = pf.propertyFieldRangeQuery("p_a", 0, null, true, true, PropertyType.Type.LONG, false);
        assertEquals("p_a:[0 TO "  + Long.MAX_VALUE + "]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", null, 1000, true, true, PropertyType.Type.LONG, false);
        assertEquals("p_a:[" + Long.MIN_VALUE + " TO 1000]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", null, null, true, true, PropertyType.Type.LONG, false);
        assertEquals("p_a:[" + Long.MIN_VALUE + " TO " + Long.MAX_VALUE + "]", q.toString());

    }

    @Test
    public void propertyFieldQuery_range_dateTimestamp() {
        Date from = Date.from(Instant.parse("2017-12-05T00:00:00.00Z"));
        Date to = Date.from(Instant.parse("2017-12-31T22:59:59.00Z"));

        Query q;
        q = pf.propertyFieldRangeQuery("p_a", from, to, true, true, PropertyType.Type.DATE, false);
        assertEquals("p_a:["+from.getTime()+" TO "+to.getTime()+"]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", from, to, false, true, PropertyType.Type.DATE, false);
        assertEquals("p_a:["+(from.getTime()+1000) +" TO "+to.getTime()+"]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", from, to, true, false, PropertyType.Type.DATE, false);
        assertEquals("p_a:["+from.getTime()+" TO "+(to.getTime()-1000)+"]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", from, to, false, false, PropertyType.Type.DATE, false);
        assertEquals("p_a:["+(from.getTime()+1000)+" TO "+(to.getTime()-1000)+"]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", from, to, true, true, PropertyType.Type.TIMESTAMP, false);
        assertEquals("p_a:["+from.getTime()+" TO "+to.getTime()+"]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", from, to, false, true, PropertyType.Type.TIMESTAMP, false);
        assertEquals("p_a:["+(from.getTime()+1000) +" TO "+to.getTime()+"]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", from, to, true, false, PropertyType.Type.TIMESTAMP, false);
        assertEquals("p_a:["+from.getTime()+" TO "+(to.getTime()-1000)+"]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", from, to, false, false, PropertyType.Type.TIMESTAMP, false);
        assertEquals("p_a:["+(from.getTime()+1000)+" TO "+(to.getTime()-1000)+"]", q.toString());
    }

    @Test
    public void propertyFieldQuery_range_dateTimestamp_unbounded() {
        Date from = Date.from(Instant.parse("2017-12-05T00:00:00.00Z"));
        Date to = Date.from(Instant.parse("2017-12-31T22:59:59.00Z"));
        Query q;

        q = pf.propertyFieldRangeQuery("p_a", from, null, true, true, PropertyType.Type.DATE, false);
        assertEquals("p_a:[" + from.getTime() + " TO "  + Long.MAX_VALUE + "]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", null, to, true, true, PropertyType.Type.DATE, false);
        assertEquals("p_a:[" + Long.MIN_VALUE + " TO "+to.getTime()+"]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", null, null, true, true, PropertyType.Type.DATE, false);
        assertEquals("p_a:[" + Long.MIN_VALUE + " TO " + Long.MAX_VALUE + "]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", from, null, true, true, PropertyType.Type.TIMESTAMP, false);
        assertEquals("p_a:[" + from.getTime() + " TO "  + Long.MAX_VALUE + "]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", null, to, true, true, PropertyType.Type.TIMESTAMP, false);
        assertEquals("p_a:[" + Long.MIN_VALUE + " TO "+to.getTime()+"]", q.toString());

        q = pf.propertyFieldRangeQuery("p_a", null, null, true, true, PropertyType.Type.TIMESTAMP, false);
        assertEquals("p_a:[" + Long.MIN_VALUE + " TO " + Long.MAX_VALUE + "]", q.toString());
    }
}
