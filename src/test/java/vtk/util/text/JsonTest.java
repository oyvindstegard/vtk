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
package vtk.util.text;

import java.io.ByteArrayInputStream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jmock.Expectations;
import static org.jmock.Expectations.returnValue;
import org.jmock.Sequence;
import org.jmock.auto.Auto;
import org.jmock.auto.Mock;
import org.jmock.integration.junit4.JUnitRuleMockery;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import vtk.util.io.IO;

import vtk.util.text.Json.Container;
import vtk.util.text.Json.ListContainer;
import vtk.util.text.Json.MapContainer;

/**
 * Test {@link vtk.util.text.Json}.
 */
@SuppressWarnings("unchecked")
public class JsonTest {
 
    // Mock JSON data
    public static final String JSON_TEST_RESOURCE = "JsonTest.txt";
    
    private String jsonTestText;
    private InputStream jsonInputStream;
    
    @Rule
    public JUnitRuleMockery context = new JUnitRuleMockery();
    
    @Mock
    private Json.Handler jsonHandler;
    @Auto
    private Sequence jsonHandlerSeq; 
    
    
    @Before
    public void setUp() throws IOException {
        InputStream is = getClass().getResourceAsStream(JSON_TEST_RESOURCE);
        jsonTestText = IO.readString(is, "utf-8").perform();
        jsonInputStream = getClass().getResourceAsStream(JSON_TEST_RESOURCE);
    }
    
    @Test
    public void testParseFromString() {
        List<Object> result = (List<Object>) Json.parse(jsonTestText);
        assertEquals(3500, result.size());
    }

    @Test
    public void testParseFromStream() throws IOException {
        List<Object> result = (List<Object>) Json.parse(jsonInputStream);
        assertEquals(3500, result.size());
    }

    @Test
    public void testParseFromReader() throws IOException {
        List<Object> result = (List<Object>) Json.parse(new InputStreamReader(jsonInputStream));
        assertEquals(3500, result.size());
    }
    
    @Test
    public void testParseData() {
        List<Object> result = (List<Object>) Json.parse(jsonTestText);
        assertEquals(3500, result.size());
        
        Map<String,Object> first = (Map<String,Object>)result.get(0);
        assertEquals(1L, first.get("id"));
        assertEquals("Brazil", first.get("country"));
        assertEquals(10, ((List<Object>)first.get("numbers")).size());
        
        Map<String,Object> last = (Map<String,Object>)result.get(3499);
        assertEquals(4000L, last.get("id"));
        assertEquals("Indonesia", last.get("country"));
        assertEquals(10, ((List<Object>)last.get("numbers")).size());
    }
    
    @Test
    public void parseObjectWithDuplicateKeys() {
        // We want value of latest key occurence to be final value in this parser.
        // (No conversion to array with value accumulation, like net.sf.json.JSONObject does
        // when parsing.)
        Container c = Json.parseToContainer("{\"a\":1, \"b\":2, \"a\":3}");
        assertFalse(c.isArray());
        assertEquals(2, c.size());
        assertEquals((Integer)3, c.asObject().intValue("a"));
    }
    
    @Test
    public void testSelect() {
        Json.Container c = Json.parseToContainer(jsonTestText);
        Json.MapContainer o = c.asArray().objectValue(120);
        
        assertEquals((Long)121l, o.select("id"));
        assertEquals("Judith", o.select("name.first_name"));
        assertEquals("Alexander", o.select("name.last_name"));
        assertEquals("jalexander3c", o.select("name.username"));
        assertEquals("Russia", o.select("country"));
        assertEquals("#95472d", o.select("colors[0]"));
        assertEquals(25.08, o.select("numbers[1]"));
    }
    
    @Test(expected = Json.JsonParseException.class)
    public void emptyStream() throws IOException {
        Json.Container c = Json.parseToContainer(new ByteArrayInputStream(new byte[0]));
        
    }
    
    @Test(expected = Json.JsonParseException.class)
    public void emptyString() {
        Json.Container c = Json.parseToContainer("");
    }
    
    @Test(expected = Json.JsonParseException.class)
    public void whitespaceOnly() {
        Json.Container c = Json.parseToContainer("    \t  ");
    }
    
    @Test
    public void whitespaceBeforeStart() {
        Json.Container c = Json.parseToContainer("    [0]");
        assertTrue(c.isArray());
        assertFalse(c.isEmpty());
        assertEquals(1, c.size());
    }

    @Test
    public void testParseToContainer() {
        Json.Container c = Json.parseToContainer(jsonTestText);
        assertEquals(3500, c.size());
        
        assertTrue(c.isArray());
        Json.MapContainer first = c.asArray().objectValue(0);
        assertEquals((Long)1l, first.longValue("id"));
        assertEquals("Brazil", first.stringValue("country"));
        assertEquals(10, first.arrayValue("numbers").size());

        Json.MapContainer last = c.asArray().objectValue(3499);
        assertEquals((Long)4000l, last.longValue("id"));
        assertEquals("Indonesia", last.stringValue("country"));
        assertEquals(10, last.arrayValue("numbers").size());
    }
    
    @Test
    public void illegalType() {
        Json.Container c = Json.parseToContainer("{\"a\":\"value\", \"b\":true, " 
                + "\"n\":133.33, \"x\":null, \"y\":{}, \"z\":[]}");
        Json.MapContainer json = c.asObject();

        // String
        assertNotNull(json.stringValue("a"));
        try {
            json.stringValue("b");
            fail("Expected ValueException");
        } catch (Json.ValueException ve) {}
        try {
            json.stringValue("x");
            fail("Expected ValueException");
        } catch (Json.ValueException ve) {}
        
        // Boolean
        assertNotNull(json.booleanValue("b"));
        try {
            json.booleanValue("a");
            fail("Expected ValueException");
        } catch (Json.ValueException ve) {}
        try {
            json.booleanValue("x");
            fail("Expected ValueException");
        } catch (Json.ValueException ve) {}
        
        // Long
        assertNotNull(json.longValue("n"));
        try {
            json.longValue("a");
            fail("Expected ValueException");
        } catch (Json.ValueException ve) {}
        try {
            json.longValue("x");
            fail("Expected ValueException");
        } catch (Json.ValueException ve) {}
        
        // Integer
        assertNotNull(json.intValue("n"));
        try {
            json.intValue("a");
            fail("Expected ValueException");
        } catch (Json.ValueException ve) {}
        try {
            json.intValue("x");
            fail("Expected ValueException");
        } catch (Json.ValueException ve) {}

        // Double
        assertNotNull(json.doubleValue("n"));
        try {
            json.doubleValue("a");
            fail("Expected ValueException");
        } catch (Json.ValueException ve) {}
        try {
            json.doubleValue("x");
            fail("Expected ValueException");
        } catch (Json.ValueException ve) {}
        
        // JSON object
        assertNotNull(json.objectValue("y"));
        try {
            json.objectValue("a");
            fail("Expected ValueException");
        } catch (Json.ValueException ve) {}
        try {
            json.objectValue("x");
            fail("Expected ValueException");
        } catch (Json.ValueException ve) {}
        
        // JSON array
        assertNotNull(json.arrayValue("z"));
        try {
            json.arrayValue("a");
            fail("Expected ValueException");
        } catch (Json.ValueException ve) {}
        try {
            json.arrayValue("x");
            fail("Expected ValueException");
        } catch (Json.ValueException ve) {}
        
    }
    
    @Test
    public void mapContainerOptMethods() {
        Json.Container c = Json.parseToContainer("{\"a\":\"value\", \"b\":null, \"n\":133.33, \"obj\":{\"n\":1}, \"arr\":[]}");
        Json.MapContainer json = c.asObject();
        
        // Null value as default value

        // Long
        assertNull(json.optLongValue("non-existing", null));
        assertEquals((Long)1L, json.optLongValue("a", 1L));
        assertEquals((Long)1L, json.optLongValue("b", 1L));
        assertEquals((Long)1L, json.optLongValue("non-existing", 1L));
        assertEquals((Long)133L, json.optLongValue("n", 0L));
        
        // Integer
        assertNull(json.optIntValue("non-existing", null));
        assertEquals((Integer)1, json.optIntValue("a", 1));
        assertEquals((Integer)1, json.optIntValue("b", 1));
        assertEquals((Integer)1, json.optIntValue("non-existing", 1));
        assertEquals((Integer)133, json.optIntValue("n", 0));
        
        // Double
        assertNull(json.optDoubleValue("non-existing", null));
        assertEquals((Double)1d, json.optDoubleValue("a", 1d));
        assertEquals((Double)1d, json.optDoubleValue("b", 1d));
        assertEquals((Double)1d, json.optDoubleValue("non-existing", 1d));
        assertEquals((Double)133.33d, json.optDoubleValue("n", 0d));
        
        // Boolean
        assertNull(json.optBooleanValue("non-existing", null));
        assertTrue(json.optBooleanValue("a", true));
        assertTrue(json.optBooleanValue("b", true));
        assertTrue(json.optBooleanValue("non-existing", true));
        assertTrue(json.optBooleanValue("n", true));
        
        // String
        assertNull(json.optStringValue("non-existing", null));
        assertEquals("value", json.optStringValue("a", "default"));
        assertEquals("default", json.optStringValue("b", "default"));
        assertEquals("default", json.optStringValue("non-existing", "default"));
        assertEquals("default", json.optStringValue("n", "default"));
        
        // Object value (map)
        MapContainer expectedObj = new MapContainer();
        expectedObj.put("n", 1L);
        
        assertNull(json.optObjectValue("non-existing", null));
        assertEquals(Collections.EMPTY_MAP, json.optObjectValue("non-existing", Collections.EMPTY_MAP));
        assertEquals(Collections.EMPTY_MAP, json.optObjectValue("a", Collections.EMPTY_MAP));
        assertEquals(expectedObj, json.optObjectValue("obj", Collections.EMPTY_MAP));
        
        // Array value (list)
        ListContainer expectedArr = new ListContainer();
        
        assertNull(json.optArrayValue("non-existing", null));
        assertEquals(Collections.EMPTY_LIST, json.optArrayValue("non-existing", Collections.EMPTY_LIST));
        assertEquals(Collections.EMPTY_LIST, json.optArrayValue("a", Collections.EMPTY_LIST));
        assertEquals(expectedArr, json.optArrayValue("arr", Collections.EMPTY_LIST));
    }
    
    @Test
    public void toContainerConversionMethods() {
        Map<String,Object> map1 = new LinkedHashMap<>();
        Map<String,Object> map2 = new LinkedHashMap<>();
        List<Object> l1 = new ArrayList<>();
        l1.add(1);
        l1.add(map2);
        
        map1.put("a", "b");
        map2.put("c", "d");
        map2.put("e", 0.3);
        map1.put("map", map2);
        map1.put("list", l1);
        
        MapContainer wrapped = MapContainer.toContainer(map1);
        assertEquals(map1, wrapped);
        assertTrue(wrapped.get("map") instanceof MapContainer);
        assertTrue(wrapped.get("list") instanceof ListContainer);
        assertTrue(((List<?>)wrapped.get("list")).get(1) instanceof MapContainer);
    }

    @Test
    public void uncontainedPrimitiveValue() {
        assertEquals(true, Json.parse("true"));
    }

    @Test(expected = ClassCastException.class)
    public void uncontainedPrimitiveValueToContainer() {
        Json.parseToContainer("true");
    }

    @Test
    public void uncontainedPrimitiveValueAsEvents() throws IOException {
        Json.ParseEvents p = Json.parseAsEvents("\"an uncontained value\"");

        context.checking(new Expectations(){{
            oneOf(jsonHandler).beginJson(); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).primitive("an uncontained value"); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endJson(); inSequence(jsonHandlerSeq);
        }});

        p.begin(jsonHandler);
    }

    @Test
    public void parseAsEvents() throws IOException {
        
        String jsonData = "{\"a\":{\"b\":[1,2.1,false]},\"c\":\"s\"}";
        Json.ParseEvents p = Json.parseAsEvents(new StringReader(jsonData));

        context.checking(new Expectations(){{
            oneOf(jsonHandler).beginJson(); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).beginObject(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).beginMember("a"); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).beginObject(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).beginMember("b"); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).beginArray(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).primitive(1L); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).primitive(2.1d); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).primitive(false); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endArray(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endMember(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endObject(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endMember(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).beginMember("c"); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).primitive("s"); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endMember(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endObject(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endJson(); inSequence(jsonHandlerSeq);
        }});
        
        p.begin(jsonHandler);

    }

    @Test
    public void parseAsEventsResume() throws IOException {
        
        String jsonData = "{\"a\":1,\"b\":2}";
        Json.ParseEvents p = Json.parseAsEvents(new StringReader(jsonData));

        context.checking(new Expectations(){{
            oneOf(jsonHandler).beginJson(); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).beginObject(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).beginMember("a"); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).primitive(1L); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endMember(); will(returnValue(false)); inSequence(jsonHandlerSeq);

            oneOf(jsonHandler).beginMember("b"); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).primitive(2L); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endMember(); will(returnValue(false)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endObject(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endJson(); inSequence(jsonHandlerSeq);
        }});
        
        p.begin(jsonHandler);
        p.resume(jsonHandler);
        p.resume(jsonHandler);
    }
    
    @Test
    public void parseAsEventsArrayOuter() throws IOException {
        String jsonData = "[1,2]";
        Json.ParseEvents p = Json.parseAsEvents(new StringReader(jsonData));

        context.checking(new Expectations(){{
            oneOf(jsonHandler).beginJson(); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).beginArray(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).primitive(1L); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).primitive(2L); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endArray(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endJson(); inSequence(jsonHandlerSeq);
        }});
        
        p.begin(jsonHandler);
    }    

    @Test
    public void parseAsEventsInputStream() throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream("{}".getBytes("utf-8"));
        Json.ParseEvents p = Json.parseAsEvents(bis);

        context.checking(new Expectations(){{
            oneOf(jsonHandler).beginJson(); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).beginObject(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endObject(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endJson(); inSequence(jsonHandlerSeq);
        }});
        
        p.begin(jsonHandler);
    }    

    @Test
    public void parseAsEventsString() throws IOException {
        Json.ParseEvents p = Json.parseAsEvents("[{}]");

        context.checking(new Expectations(){{
            oneOf(jsonHandler).beginJson(); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).beginArray(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).beginObject(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endObject(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endArray(); will(returnValue(true)); inSequence(jsonHandlerSeq);
            oneOf(jsonHandler).endJson(); inSequence(jsonHandlerSeq);
        }});

        p.begin(jsonHandler);
    }
}
