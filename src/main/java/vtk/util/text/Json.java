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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.simple.parser.ContainerFactory;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * New JSON utility class using json-simple internally for core parsing.
 * 
 * <p>Supports the same features as the old {@link JSON} utility class, including stripping
 * of C++-style comments from input. But it is generally a lot faster at parsing, and
 * it exposes no direct binding to the json-simple API.
 */
public final class Json {

    private Json(){}
    
    /**
     * Parse JSON from a string.
     * 
     * <p>JSON data may contain C++-style comments, which will be stripped before
     * being handed to core parser.
     * 
     * @param input
     * @return a plain unwrapped data structure built on {@code HashMap<String,Object>}
     * and {@code ArrayList<Object>} as container types.
     */
    public static Object parse(String input) {
        try {
            return parseInternal(new CommentStripFilter(input), false);
        } catch (IOException io) {
            // Cannot happen
            throw new JsonParseException(io.getMessage(), io);
        }
    }
    
    /**
     * Parse JSON directly from an input stream.
     * 
     * <p>JSON data may contain C++-style comments, which will be stripped before
     * being handed to core parser.
     * 
     * <p>Character encoding is assumed to be UTF-8.
     *
     * @param input
     * @return a plain unwrapped data structure built on {@code HashMap<String,Object>}
     * and {@code ArrayList<Object>} as container types.
     * @throws IOException 
     */
    public static Object parse(InputStream input) throws IOException {
        Reader reader = new BufferedReader(new InputStreamReader(input, "utf-8"));
        return parseInternal(reader, false);
    }
    
    /**
     * Parse JSON directly from a reader.
     * 
     * <p>JSON data may contain C++-style comments, which will be stripped before
     * being handed to core parser.
     * 
     * @param input the reader which provides the characters forming the JSON data.
     * @return a plain unwrapped data structure built on {@code HashMap<String,Object>}
     * and {@code ArrayList<Object>} as container types.
     * @throws IOException 
     */
    public static Object parse(Reader input) throws IOException {
        return parseInternal(input, false);
    }
    
    /**
     * Like {@link #parse(java.lang.String) }, but with specilized container types.
     * @param input
     * @return a data structure built on {@link MapContainer} and {@link ListContainer}
     * as container types.
     */
    public static Container parseToContainer(String input) {
        try {
            return (Container)parseInternal(new CommentStripFilter(input), true);
        } catch (IOException io) {
            // Cannot happen
            throw new JsonParseException(io.getMessage(), io);
        }
    }
    
    /**
     * Like {@link #parse(java.io.Reader)  }, but with specilized container types.
     * @param input
     * @return a data structure built on {@link MapContainer} and {@link ListContainer}
     * as container types.
     * @throws IOException 
     */
    public static Container parseToContainer(Reader input) throws IOException {
        return (Container)parseInternal(input, true);
    }
    
    /**
     * Like {@link #parse(java.io.InputStream)  }, but with specilized container types.
     * @param input
     * @return a data structure built on {@link MapContainer} and {@link ListContainer}
     * as container types.
     * @throws IOException 
     */
    public static Container parseToContainer(InputStream input) throws IOException {
        Reader reader = new BufferedReader(new InputStreamReader(input, "utf-8"));
        return (Container)parseInternal(reader, true);
    }
    
    private static Object parseInternal(Reader input, boolean useContainer) throws IOException {
        try {
            if (! (input instanceof CommentStripFilter)) {
                input = new CommentStripFilter(input);
            }
            JSONParser parser = new JSONParser();
            if (useContainer) {
                return parser.parse(input, new JsonContainerFactory());
            } else {
                return parser.parse(input, new UnwrappedContainerFactory());
            }
        } catch (ParseException pe) {
            throw new JsonParseException("Failed to parse JSON string: " + pe.getMessage(), pe);
        }
    }

    /**
     * Container factory which provides standard {@link java.util.HashMap} for
     * JSON objects and {@link java.util.ArrayList} for JSON arrays. It therefore
     * presents and "unwrapped" view of JSON data with no special containers.
     */
    private static final class UnwrappedContainerFactory implements ContainerFactory {
        @Override
        public Map createObjectContainer() {
            return new HashMap<>();
        }
        @Override
        public List creatArrayContainer() {
            return new ArrayList<>();
        }
    }
    
    /**
     * Container factory which provides specialized container types for extra
     * functionality.
     */
    private static final class JsonContainerFactory implements ContainerFactory {
        @Override
        public Map createObjectContainer() {
            return new MapContainer();
        }
        @Override
        public List creatArrayContainer() {
            return new ListContainer();
        }
    }

    private static final Pattern ARRAY_SELECTOR = Pattern.compile("^([^\\[\\]]+)\\[([0-9]+)\\]$");

    /**
     * Select element by drill down expression in dot- and array-subscript notation.
     * Currently does not support bare array as outer structure.
     *
     * <p>
     * Examples:
     * <ul>
     * <li>{@code "foo"} - select element with key {@code "foo"} on the current
     * object.
     * <li>{@code "foo.bar"} - select object with key {@code "foo"}, then select
     * element of object with key {@code "bar"}.
     * <li>{@code "a.numbers[2]"} - select object with key {@code "a"}, and on
     * that object, select third element of array with key {@code "numbers"}.
     * <li>{@code "a.objects[2].b"} - select object with key {@code "a"}, and on
     * that object, select third element of array with key {@code "objects"},
     * and on that object select element with key {@code "b"}.
     * </ul>
     *
     * <p>
     * This method will always return <code>null</code> when something is not
     * found, and thus you cannot distinguish JSON <code>null</code> values from
     * a "not found" condition.
     *
     * @param object The object to select into.
     * @param expression the expression, which cannot be null. The empty string
     * will return the instance itself.
     * @return the selected value, which may be any kind of object that can
     * exist in the JSON data structure. If the value does not exist, <code>null</code> is
     * returned.
     */
    public static Object select(Map<String,Object> object, String expression) {
        if (expression.isEmpty()) {
            return object;
        }

        String[] selectors = expression.split("\\.");

        Map<String, Object> current = object;
        Object found = null;

        for (int i = 0; i < selectors.length; i++) {
            String selector = selectors[i];
            int idx = -1;
            Matcher m = ARRAY_SELECTOR.matcher(selector);
            if (m.matches()) {
                try {
                    idx = Integer.parseInt(m.group(2));
                    if (idx >= 0) {
                        selector = m.group(1);
                    }
                } catch (NumberFormatException nfe) {
                }
            }

            Object o = current.get(selector);
            if (o == null) {
                found = null;
                break;
            }
            if (idx >= 0) {
                // o must be list, otherwise not found.
                if (o instanceof List && idx < ((List) o).size()) {
                    o = ((List) o).get(idx);
                } else {
                    found = null;
                    break;
                }
            }
            if (i == selectors.length - 1) {
                found = o;
                break;
            }
            if (!(o instanceof Map)) {
                found = null;
                break;
            }
            current = (Map<String, Object>) o;
        }

        return found;
    }
        
    /**
     * Specialized container interface for JSON objects and arrays represented
     * as maps and lists in memory. Mainly focused on reading of data, rather
     * than something to be used to build JSON data.
     */
    public static interface Container {
        /**
         * 
         * @return <code>true</code> if this container instance is a list.
         */
        boolean isArray();
        
        /**
         * @return this container as a {@code ListContainer}.
         * @throws ValueException if this container is a map
         */
        ListContainer asArray();
        
        /**
         * @return this container as a {@code MapContainer}.
         * @throws ValueException if this container is a list
         */
        MapContainer asObject();
        
        /**
         * @return <code>true</code> if this container is empty, <code>false</code> otherwise.
         */
        boolean isEmpty();
        
        /**
         * @return number of elements in this container, which may be either number of keys
         * in a map container or number of elements in a list container.
         */
        int size();
    }
    
    /**
     * Container of JSON arrays based on list.
     */
    public static final class ListContainer extends ArrayList<Object> implements Container {

        private ListContainer() {
        }
        
        private ListContainer(List<Object> array) {
            super(array);
        }
        
        @Override
        public boolean isArray() {
            return true;
        }

        @Override
        public ListContainer asArray() {
            return this;
        }

        @Override
        public MapContainer asObject() {
            throw new ValueException("This is not a MapContainer");
        }
        
        /**
         * Get map representation of JSON object at index.
         * @param idx the index of the JSON object in this array.
         * @return a {@code MapContainer} 
         * @throws ValueException if object at index is not a JSON object/map.
         */
        public MapContainer objectValue(int idx) {
            Object o = get(idx);
            if (! (o instanceof MapContainer)) {
                throw new ValueException("Not a MapContainer");
            }
            return (MapContainer)o;
        }

        /**
         * Get sub-array at index idx
         * @param idx index of sub-array in this array
         * @return a {@code ListContainer} representing the JSON array.
         * @throws ValueException if object at idx is not a JSON array/list.
         */
        public ListContainer arrayValue(int idx) {
            Object o = get(idx);
            if (! (o instanceof ListContainer)) {
                throw new ValueException("Not a ListContainer");
            }
            return (ListContainer)o;
        }

        /**
         * Get long value at index.
         * @param idx index of long value in this array.
         * @return long value.
         * @throws ValueException if value is a JSON null or not a JSON number.
         */
        public Long longValue(int idx) {
            return Json.asLong(get(idx));
        }

        /**
         * Get int value at index.
         * @param idx
         * @return int value
         * @throws ValueException if value is a JSON null or not a JSON number.
         */
        public Integer intValue(int idx) {
            return Json.asInteger(get(idx));
        }

        /**
         * Get double value at index.
         * @param idx
         * @return double value
         * @throws ValueException if value is a JSON null or not a JSON number.
         */
        public Double doubleValue(int idx) {
            return Json.asDouble(get(idx));
        }

        /**
         * Get boolean value at index.
         * @param idx
         * @return boolean value
         * @throws ValueException if value is a JSON null or not a JSON boolean.
         */
        public Boolean booleanValue(int idx) {
            return Json.asBoolean(get(idx));
        }
        
        /**
         * Get string value at index.
         * @param idx
         * @return 
         * @throws ValueException if value is a JSON null or not a JSON string.
         */
        public String stringValue(int idx) {
            return Json.asString(get(idx));
        }

        /**
         * Check for JSON null value at index.
         * @param idx
         * @return <code>true</code> if value is a <em>JSON null</em> value.
         */
        public boolean isNull(int idx) {
            return exists(idx) && get(idx) == null;
        }

        /**
         * Check that array element at index exists (actual
         * @param idx
         * @return <code>true</code> if idx is not out of bounds.
         */
        public boolean exists(int idx) {
            return idx >= 0 && idx < size();
        }

    }

    /**
     * A <code>java.util.LinkedHashMap</code> extension with utility methods for easier
     * access to map values in a JSON-centric fashion.
     * 
     * <p>
     * Regarding <code>null</code> values: value-type-specific getter methods
     * like {@link #intValue(java.lang.String) } will fail with a
     * {@link ValueException} if the actual value is JSON <code>null</code> or the
     * key does not exist. For cases where lenient interpretation of parsed JSON
     * data is preferred, or where items in JSON objects are optional, one may use
     * the opt-methods with suitable default values provided. Or one can just use the
     * general methods inherited from {@code java.util.Map}, like
     * {@link java.util.Map#get(java.lang.Object) get}.
     *
     * <p>Regarding type conversion: the value-type-specific getter methods do no
     * type conversion. As an example, trying to get a numeric value from a string value
     * will fail, even though the string may be parseable as a number. Conversely,
     * non-string JSON types are <em>not</em> automatically converted to strings upon
     * request for string values.
     */
    public static final class MapContainer extends LinkedHashMap<String,Object> implements Container {

        private MapContainer() {
        }

        public MapContainer(Map<? extends String, ? extends Object> m) {
            super(m);
        }

        @Override
        public boolean isArray() {
            return false;
        }

        @Override
        public ListContainer asArray() {
            throw new ValueException("This is not a ListContainer");
        }

        @Override
        public MapContainer asObject() {
            return this;
        }

        /**
         * Get a JSON object value for key as a {@code MapContainer}.
         * @param key
         * @return 
         * @throws ValueException if not object value or key does not exist.
         */
        public MapContainer objectValue(String key) {
            Object o = get(key);
            if (! (o instanceof MapContainer)) {
                throw new ValueException("Key does not exist or is not an object value: '" + key + "'");
            }
            return (MapContainer)o;
        }
        
        /**
         * Return array value for key.
         * @param key
         * @return 
         * @throws ValueException if not array value or key does not exist.
         */
        public ListContainer arrayValue(String key) {
            Object o = get(key);
            if (! (o instanceof ListContainer)) {
                throw new ValueException("Key does not exist or is not an array value: '" + key + "'");
            }
            return (ListContainer)o;
        }
        
        /**
         * Optional {@code long} value with default.
         * @param key
         * @param defaultValue value to return in case of null-value, wrong data type or non-existing key.
         * invalid value type.
         * @return the value, but if key does not exist, has
         * a JSON null-value or has an incompatible value type, then the {@code defaultValue} is returned.
         */
        public Long optLongValue(String key, Long defaultValue) {
            try {
                Long value = longValue(key);
                return value != null ? value : defaultValue;
            } catch (ValueException ve) {
                return defaultValue;
            }
        }
        
        /**
         * Get Long value by key.
         * @param key
         * @return long value
         * @throws ValueException if key does not exist, value is a JSON null or value not a JSON number.
         */
        public Long longValue(String key) {
            return Json.asLong(get(key));
        }

        /**
         * Optional {@code int } value with default.
         * @param key
         * @param defaultValue value to return in case of null-value, wrong data type or non-existing key.
         * @return the value, but if key does not exist, has
         * a JSON null-value or has an incompatible value type, then the {@code defaultValue} is returned.
         */
        public Integer optIntValue(String key, Integer defaultValue) {
            try {
                Integer value = intValue(key);
                return value != null ? value : defaultValue;
            } catch (ValueException ve) {
                return defaultValue;
            }
        }

        /**
         * Get int value by key.
         * @param key
         * @return int value
         * @throws ValueException if key does not exist, value is a JSON null or value not a JSON number.
         */
        public Integer intValue(String key) {
            return Json.asInteger(get(key));
        }
        
        /**
         * Optional {@code double} value with default.
         * @param key
         * @param defaultValue value to return in case of null-value, wrong data type or non-existing key.
         * @return the value, but if key does not exist, has
         * a JSON null-value or has an incompatible value type, then the {@code defaultValue} is returned.
         */
        public Double optDoubleValue(String key, Double defaultValue) {
            try {
                Double value = doubleValue(key);
                return value != null ? value : defaultValue;
            } catch (ValueException ve) {
                return defaultValue;
            }
        }
        
        /**
         * Get double value by key.
         * @param key
         * @return double value
         * @throws ValueException if key does not exist, value is a JSON null or value not a JSON number.
         */
        public Double doubleValue(String key) {
            return Json.asDouble(get(key));
        }
        
        /**
         * Optional {@code boolean} value with default.
         * @param key
         * @param defaultValue value to return in case of null-value, wrong data type or non-existing key.
         * @return the value, but if key does not exist, has
         * a JSON null-value or has an incompatible value type, then the {@code defaultValue} is returned.
         */
        public Boolean optBooleanValue(String key, Boolean defaultValue) {
            try {
                Boolean value = booleanValue(key);
                return value != null ? value : defaultValue;
            } catch (ValueException ve) {
                return defaultValue;
            }
        }
        
        /**
         * Get boolean value by key.
         * @param key
         * @return JSON boolean value as a {@code Boolean}
         * @throws ValueException if key does not exist, value is a JSON null or value not a JSON boolean.
         */
        public Boolean booleanValue(String key) {
            return Json.asBoolean(get(key));
        }
        
        /**
         * Optional {@code String} value with default.
         * @param key
         * @param defaultValue value to return in case of null-value, wrong data type or non-existing key.
         * @return the value, but if key does not exist, has
         * a JSON null-value or has an incompatible value type, then the {@code defaultValue} is returned.
         * @see #stringValue(java.lang.String) 
         */
        public String optStringValue(String key, String defaultValue) {
            try {
                String value = stringValue(key);
                return value != null ? value : defaultValue;
            } catch (ValueException ve) {
                return defaultValue;
            }
        }

        /**
         * Get string value by key.
         * @param key
         * @return 
         * @throws ValueException if key does not exist, value is a JSON null or value not a JSON string.
         */
        public String stringValue(String key) {
            return Json.asString(get(key));
        }

        /**
         * Check if value is a JSON null value.
         * @param key
         * @return <code>true</code> if key <em>exists</em> and has a <em>JSON null</em> value
         */
        public boolean isNull(String key) {
            if (containsKey(key)) {
                return get(key) == null;
            }
            return false;
        }
        
        /**
         * Check if a key exists. This can be used to distinguish between
         * cases of JSON null values and key existence.
         * @param key
         * @return <code>true</code> if object has the key. Note that the value may
         * still be <code>null</code>, if it's a JSON null.
         */
        public boolean has(String key) {
            return containsKey(key);
        }

        /**
         * Select element by expression on this object.
         * 
         * <p>Works like {@link #select(java.util.Map, java.lang.String) } with first
         * parameter replaced by this object.
         * @param expression
         * @return 
         */
        public Object select(String expression) {
            return Json.select(this, expression);
        }
    }
    
    private static Long asLong(Object o) {
        if (!(o instanceof Number)) {
            throw new ValueException("Not a Number: " + o);
        }
        return ((Number)o).longValue();
    }
    
    private static Integer asInteger(Object o) {
        if (!(o instanceof Number)) {
            throw new ValueException("Not a Number: "+ o);
        }
        return ((Number)o).intValue();
    }

    private static Double asDouble(Object o) {
        if (!(o instanceof Number)) {
            throw new ValueException("Not a Number: " + o);
        }
        return ((Number)o).doubleValue();
    }
    
    private static Boolean asBoolean(Object o) {
        if (!(o instanceof Boolean)) {
            throw new ValueException("Not a Boolean: " + o);
        }
        return (Boolean)o;
    }
    
    private static String asString(Object o) {
        if (! (o instanceof String)) {
            throw new ValueException("Not a String: " + o);
        }
        return (String)o;
    }
    
    /**
     * General exception for Json util.
     */
    public static class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
        public JsonException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when input could not be successfully parsed.
     */
    public static final class JsonParseException extends JsonException {
        public JsonParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Exception thrown by {@link MapContainer} or {@link ListContainer} when
     * a value type conversion is not possible.
     */
    public static final class ValueException extends JsonException {
        public ValueException(String message) {
            super(message);
        }
        public ValueException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
