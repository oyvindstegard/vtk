/* Copyright (c) 2014–2015 University of Oslo, Norway
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

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author oyvind
 */
public class JsonStreamerTest {

    private StringWriter sw;
    private JsonStreamer js;
    
    @Before
    public void setUp() {
        sw = new StringWriter();
        js = new JsonStreamer(sw);
    }
    
    @Test
    public void empty() throws IOException {
        js.endJson();
        assertTrue(sw.toString().isEmpty());
    }
    
    @Test
    public void emptyObject() throws IOException {
        js.beginObject().endObject();
        assertEquals("{}", sw.toString());
    }

    @Test
    public void emptyArray() throws IOException {
        js.beginArray().endArray();
        assertEquals("[]", sw.toString());
    }

    @Test
    public void basicObject() throws IOException {
        js.beginObject().member("a", "b").member("c", 3).endObject();
        assertEquals("{\"a\":\"b\",\"c\":3}", sw.toString());
    }
    
    @Test
    public void key() throws IOException {
        js.beginObject();
        js.key("key").value("value");
        js.endObject();
        assertEquals("{\"key\":\"value\"}", sw.toString());
    }
    
    @Test
    public void key_objectValue() throws IOException {
        js.beginObject()
          .key("some-object")
          .beginObject()
            .member("a-boolean", false)
          .endObject()
        .endObject();
        
        assertEquals("{\"some-object\":{\"a-boolean\":false}}", sw.toString());
    }
    
    @Test
    public void key_arrayValue() throws IOException {
        js.beginObject()
          .key("some-array")
          .beginArray()
            .value(1).value(2).value(3)
          .endArray()
        .endObject();
        
        assertEquals("{\"some-array\":[1,2,3]}", sw.toString());
    }
    
    @Test
    public void object() throws IOException {
        String expect = "{\"a\":\"b\",\"x\":\"y\",\"z\":{}}";
        Map<String,Object> object = new LinkedHashMap<>();
        object.put("a", "b");
        object.put("x", "y");
        object.put("z", new HashMap<String,Object>());
        
        js.object(object);
        
        assertEquals(expect, sw.toString());
    }
    
    @Test
    public void objectValue() throws IOException {
        String expect = "{\"a\":\"b\",\"x\":\"y\",\"z\":{},\"l\":[1,2,3],\"emptyList\":[]}";
        Map<String,Object> object = new LinkedHashMap<>();
        object.put("a", "b");
        object.put("x", "y");
        object.put("z", new HashMap<String,Object>());
        
        List<Object> l = new ArrayList<Object>();
        l.add(1); l.add(2); l.add(3);
        object.put("l", l);
        object.put("emptyList", Collections.EMPTY_LIST);
        
        js.value(object);
        
        assertEquals(expect, sw.toString());
    }
    
    @Test
    public void booleanValues() throws IOException {
        js.beginArray().value(true).value(false).endArray();
        assertEquals("[true,false]", sw.toString());
    }
    
    @Test
    public void stringEscapes() throws IOException {
        js.beginObject();
        js.member("\nlines\nin\nkey", "a\tb\tc, Ø. \u0000\u0001\u2001 100\u2126");
        js.endJson();
        assertEquals("{\"\\nlines\\nin\\nkey\":\"a\\tb\\tc, Ø. \\u0000\\u0001\\u2001 100\u2126\"}", 
                sw.toString());
    }
    
    @Test
    public void numericValues() throws IOException {
        js.beginArray()
          .value(0)
          .value(-1)
          .value(Math.PI)
          .value(Double.NaN)
          .value(1/0d)
          .value(Double.MAX_VALUE)
          .value(Double.MIN_VALUE)
          .value(Float.MAX_VALUE)
          .value(Float.MIN_VALUE)
          .value(Integer.MIN_VALUE)
          .value(Integer.MAX_VALUE)
          .value(Long.MIN_VALUE)
          .value(Long.MAX_VALUE)
        .endArray();
        
        assertEquals(
                "[0,"
                + "-1,"
                + "3.141592653589793,"
                + "null," // NaN not representable in JSON
                + "null," // Inf not representable in JSON
                + "1.7976931348623157E308," 
                + "4.9E-324,"
                + "3.4028235E38,"
                + "1.4E-45,"
                + "-2147483648,"
                + "2147483647,"
                + "-9223372036854775808,"
                + "9223372036854775807]",
                sw.toString());
    }
    
    @Test
    public void memberIfNotNull() throws IOException {
        js.beginObject()
                .memberIfNotNull("a", "b")
                .memberIfNotNull(null, "c")
                .memberIfNotNull("d", null)
                .memberIfNotNull(null, null)
         .endObject();
        
        assertEquals("{\"a\":\"b\"}", sw.toString());
        
    }
    
    @Test
    public void membersOf() throws IOException {
        Map<Object,Object> m = new LinkedHashMap<>();
        m.put("x", "y");
        m.put(null, "null");
        m.put(1, 1);
        
        js.beginObject().member("a", "b").membersOf(m).endObject();
        assertEquals("{\"a\":\"b\",\"x\":\"y\",\"null\":\"null\",\"1\":1}", sw.toString());
    }
    
    @Test
    public void objectWithNullValues() throws IOException {
        js.beginObject().member("a", null).key("b").value(null).endObject();
        assertEquals("{\"a\":null,\"b\":null}", sw.toString());
    }
    
    @Test
    public void arrayWithNullValues() throws IOException {
        js.beginArray().value(null).value(null).endArray();
        assertEquals("[null,null]", sw.toString());
    }
    
    @Test
    public void arrayOfObjects() throws IOException {
        js.beginArray()
                .beginObject().member("id", 1).endObject()
                .beginObject().member("id", 2).endObject()
                .beginObject().member("id", 3).endObject()
          .endArray();
        
        assertEquals("[{\"id\":1},{\"id\":2},{\"id\":3}]", sw.toString());
    }
    
    @Test
    public void stringValue() throws IOException {
        js.value("foo");
        assertEquals("\"foo\"", sw.toString());
    }
    
    @Test
    public void nullValue() throws IOException {
        js.value(null);
        assertEquals("null", sw.toString());
    }
    
    @Test
    public void nestedArrays() throws IOException {
        js.beginArray()
                 .beginArray().value(1)
                      .beginArray().value(2).endArray()
                 .endArray()
                 .beginArray().endArray()
                 .beginArray().beginArray().value(3).value(4).endArray().endArray()
          .endArray();
        
        assertEquals("[[1,[2]],[],[[3,4]]]", sw.toString());
    }
    
    @Test
    public void nullKeyMap() throws IOException {
        Map<Object,Object> mapWithNullKey = new HashMap<>();
        mapWithNullKey.put(null, "value of the null key");
        js.value(mapWithNullKey);
        assertEquals("{\"null\":\"value of the null key\"}", sw.toString());
    }

    @Test
    public void primitiveArrayEmpty() throws IOException {
        int[] array = new int[0];
        js.value(array);
        assertEquals("[]", sw.toString());

    }

    @Test
    public void primitiveArray_byte() throws IOException {
        byte[] array = {0};
        js.value(array);
        assertEquals("[0]", sw.toString());
        
        array = new byte[]{1,-2,Byte.MAX_VALUE};
        js.value(array);
        assertEquals("[0],[1,-2," + Byte.MAX_VALUE + "]", sw.toString());
    }
    
    @Test
    public void primitiveArray_short() throws IOException {
        short[] array = {0};
        js.value(array);
        assertEquals("[0]", sw.toString());
        
        array = new short[]{1,-2,Short.MAX_VALUE};
        js.value(array);
        assertEquals("[0],[1,-2," + Short.MAX_VALUE + "]", sw.toString());
    }
    
    @Test
    public void primitiveArray_int() throws IOException {
        int[] array = {0};
        js.value(array);
        assertEquals("[0]", sw.toString());
        
        array = new int[]{1,-2,Integer.MAX_VALUE};
        js.value(array);
        assertEquals("[0],[1,-2," + Integer.MAX_VALUE + "]", sw.toString());
    }
    
    @Test
    public void primitiveArray_long() throws IOException {
        long[] array = {0};
        js.value(array);
        assertEquals("[0]", sw.toString());
        
        array = new long[]{1,-2,Long.MAX_VALUE};
        js.value(array);
        assertEquals("[0],[1,-2," + Long.MAX_VALUE + "]", sw.toString());
    }
    
    @Test
    public void primitiveArray_boolean() throws IOException {
        boolean[] array = {false};
        js.value(array);
        assertEquals("[false]", sw.toString());
        
        array = new boolean[]{true,false,true};
        js.value(array);
        assertEquals("[false],[true,false,true]", sw.toString());
    }
    
    @Test
    public void primitiveArray_float() throws IOException {
        float[] array = {0.1f};
        js.value(array);
        assertEquals("[0.1]", sw.toString());
        
        array = new float[]{0.1f,-0.2f,Float.MAX_VALUE};
        js.value(array);
        assertEquals("[0.1],[0.1,-0.2," + Float.MAX_VALUE + "]", sw.toString());
    }
    
    @Test
    public void primitiveArray_double() throws IOException {
        double[] array = {0.1};
        js.value(array);
        assertEquals("[0.1]", sw.toString());
        
        array = new double[]{0.1,-0.2,Double.MAX_VALUE};
        js.value(array);
        assertEquals("[0.1],[0.1,-0.2," + Double.MAX_VALUE + "]", sw.toString());
    }

    @Test
    public void primitiveArray_char() throws IOException {
        char[] array = "\"test\"".toCharArray();
        js.value(array);
        assertEquals("[\"\\\"\",\"t\",\"e\",\"s\",\"t\",\"\\\"\"]", sw.toString());
    }
    
    @Test
    public void primitiveMultidimArray_int() throws IOException {
        int[][] array = {{1,2},{3,4},{}};
        js.value(array);
        assertEquals("[[1,2],[3,4],[]]", sw.toString());
        
        js.value(new int[][][]{{{}}});
        assertEquals("[[1,2],[3,4],[]],[[[]]]", sw.toString());
    }
    
    @Test
    public void primitiveArrayOfLists() throws IOException {
        List<Object> objectList = new ArrayList<>();
        List<boolean[]> boolArrayList = new ArrayList<>();
        
        objectList.add("x");
        objectList.add(1);
        objectList.add('y');
        
        boolArrayList.add(new boolean[]{true,true});
        boolArrayList.add(new boolean[]{false,false});
                
        Object[] array = new Object[]{objectList, boolArrayList};
        
        js.value(array);
        assertEquals("[[\"x\",1,\"y\"],[[true,true],[false,false]]]", sw.toString());
    }
    
    @Test
    public void primitiveArrayOfMaps() throws IOException {
        Map<String,Object> jsonMap = new LinkedHashMap<>();
        jsonMap.put("foo", "bar");
        jsonMap.put("baz", 1);
        
        Map<Integer,Integer> intMap = new LinkedHashMap<>();
        intMap.put(1, -1);
        intMap.put(2, -2);
        intMap.put(3, -3);
        
        Object[] array = new Object[]{jsonMap, intMap};
        
        js.value(array);
        
        assertEquals("[{\"foo\":\"bar\",\"baz\":1},{\"1\":-1,\"2\":-2,\"3\":-3}]", sw.toString());
    }
    
    @Test
    public void primitiveArrayOfObjects() throws IOException {
        Object[] array = new Object[] {
            new Object() {
                @Override
                public String toString() {
                    return "type-a-as-string";
                }
            },
            new Object() {
                @Override
                public String toString() {
                    return "type-b-as-string";
                }
            },
            new int[]{1,2,3},
            new HashMap()
        };
        js.value(array);
        
        assertEquals("[\"type-a-as-string\",\"type-b-as-string\",[1,2,3],{}]", sw.toString());
    }

    @Test
    public void useToStringForUnrecognizedType() throws IOException {
        Object obj = new Object() {
            @Override
            public String toString() {
                return "some object";
            }
        };
        
        js.beginObject().member("object", obj).endObject();
        assertEquals("{\"object\":\"some object\"}", sw.toString());
    }
    
    @Test
    public void primitiveArrayPrettyPrint() throws IOException {
        Object[] array = new Object[]{new int[]{1,2}, 
            new boolean[]{true,false}};
        String json = JsonStreamer.toJson(array, 1);
        assertEquals("[\n"
                + " [\n"
                + "  1,\n"
                + "  2\n"
                + " ],\n"
                + " [\n"
                + "  true,\n"
                + "  false\n"
                + " ]\n"
                + "]\n", json);
    }
    
    @Test
    public void toJson() {
        assertEquals("true", JsonStreamer.toJson(true));
        assertEquals("false", JsonStreamer.toJson(false));
        assertEquals("1", JsonStreamer.toJson(1));
        
        Map<String,Object> m = new HashMap<>();
        m.put("a", "b");
        assertEquals("{\"a\":\"b\"}", JsonStreamer.toJson(m));
    }
    
    
    @Test
    public void escapeSlashesOn() throws IOException {
        js = new JsonStreamer(sw, true);
        js.beginArray().value("/").endJson();
        assertEquals("[\"\\/\"]", sw.toString());
    }
    
    @Test
    public void escapeSlashesOff() throws IOException {
        js = new JsonStreamer(sw, false);
        js.beginArray().value("/").endJson();
        assertEquals("[\"/\"]", sw.toString());
    }
    
    @Test
    public void endJson() throws IOException {
        js.beginArray()
            .beginObject()
              .key("foo").beginObject()
                .key("array").beginArray().value(1)
                  .endJson();
        assertEquals("[{\"foo\":{\"array\":[1]}}]", sw.toString());
    }
    
    @Test
    public void continueNextJsonObjectAfterEnd() throws IOException {
        js.beginObject().endJson();
        js.beginObject().member("a", "b").endJson();
        
        assertEquals("{}{\"a\":\"b\"}", sw.toString());
    }
    
    // This test will fail until support for cycle detection is implemented.
    // Keep it disabled.
    @Test
    @Ignore
    public void noInfiniteRecursionOnSelfReferencingStructure() throws IOException {
        Map<String,Object> m = new HashMap<>();
        m.put("self", m);
        js.object(m);
        assertEquals("{\"self\":null}", sw.toString());
    }
    
    @Test(expected = IllegalStateException.class)
    public void beginObjectWithoutKey() throws IOException {
        js.beginObject().beginObject();
    }

    @Test(expected = IllegalStateException.class)
    public void beginArrayWithoutKey() throws IOException {
        js.beginObject().beginArray();
    }
    
    @Test (expected = IllegalStateException.class)
    public void invalidNesting() throws IOException {
        js.beginObject().beginObject().value(3).endArray();
    }
    
    @Test (expected = IllegalStateException.class)
    public void invalidNesting_array() throws IOException {
        js.beginArray().value(1).endObject();
    }
    
    @Test (expected = IllegalStateException.class)
    public void valueWithoutKeyOrArray() throws IOException {
        js.beginObject().value(3);
    }
    
    @Test (expected = IllegalStateException.class)
    public void endInIllegalState() throws IOException {
        js.beginObject().key("foo").endJson();
    }
    
    @Test (expected = IllegalStateException.class)
    public void doubleKey() throws IOException {
        js.beginObject().key("foo").key("bar");
    }
}
