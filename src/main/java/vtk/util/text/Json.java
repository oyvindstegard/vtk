/* Copyright (c) 2014–2015, University of Oslo, Norway
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
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.parser.ContainerFactory;
import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * TODO class has become largeish, it may be time to refactor into separate source code files
 * and put it all in a separate package.
 * 
 * New JSON utility class using json-simple internally for core parsing.
 * 
 * <p>Supports the same features as the old {@code JSON} utility class, including stripping
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
    
    /**
     * Set up event based parsing of a JSON string.
     * @param input the string to parse JSON data from.
     * @return a {@link ParseEvents} instance which can be used for event based parsing
     * of the JSON data.
     */
    public static ParseEvents parseAsEvents(String input) {
        return parseAsEvents(new CommentStripFilter(input));
    }
    
    /**
     * Set up event based parsing of a JSON input stream.
     * @param input the input stream to parse JSON data from.
     * @return a {@link ParseEvents} instance which can be used for event based parsing
     * of the JSON stream.
     */
    public static ParseEvents parseAsEvents(InputStream input) {
        try {
            Reader reader = new BufferedReader(new InputStreamReader(input, "utf-8"));
            return Json.parseAsEvents(reader);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
    
    /**
     * Set up event based parsing of JSON data from a reader.
     * @param input the stream reader to parse JSON data from.
     * @return a {@link ParseEvents} instance which can be used for event based parsing
     * of the JSON stream.
     */
    public static ParseEvents parseAsEvents(Reader input) {
        if (!(input instanceof CommentStripFilter)) {
            input = new CommentStripFilter(input);
        }
        return new ParseEventsImpl(input);
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
            throw new JsonParseException("Failed to parse JSON data: " + pe.toString(), pe);
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

        public ListContainer() {
        }
        
        public ListContainer(List<Object> array) {
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

        /**
         * Conversion of a provided {@code List} to a {@code ListContainer}
         * structure.
         * 
         * <p>XXX Comitted for review; I'm not sure we need this for anything.
         * If we
         * decide this is not necessary, just remove it to keep things lean and
         * simple.
         * 
         * <p>All values in list that are lists or other maps will also be converted
         * recursively. All contained values that are {@code Map} instances must
         * have string keys, otherwise a {@code ClassCastException} will be
         * thrown.
         *
         * @param array any list
         * @return a ListContainer with the same values as input map, except all contained
         * lists/maps values are converted to containers.
         * @throws ClassCastException if maps occur as values that do not have
         * string keys.
         * @throws StackOverflowError if provided structure contains reference cycles
         * between maps and/or lists.
         */
        static ListContainer toContainer(List<?> array) {
            ListContainer outer = new ListContainer();
            for (Object o: array) {
                if (o instanceof Map) {
                    outer.add(MapContainer.toContainer((Map<String,Object>)o));
                } else if (o instanceof List) {
                    outer.add(toContainer((List<?>)o));
                } else {
                    outer.add(o);
                }
            }
            return outer;
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

        public MapContainer() {
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
         * Get a JSON object value for key as a {@code MapContainer}. But if
         * no appropriate value exists for the key, return a provided default
         * value.
         * 
         * TODO consider if default value should be converted to MapContainer if used.
         * 
         * @param key
         * @param defaultValue
         * @return a {@code MapContainer} instance if key with appropriate value was found, otherwise
         * the <code>defaultValue</code> instance
         * 
         */
        public Map<String,Object> optObjectValue(String key, Map<String,Object> defaultValue) {
            Object o = get(key);
            if (o instanceof MapContainer) return (MapContainer)o;
            
            return defaultValue;
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
         * Get a JSON array value for key as a {@code ListContainer}. But if
         * no appropriate value exists for the key, return a provided default
         * value.
         * 
         * TODO consider if default value should be converted to ListContainer if used.
         * 
         * @param key
         * @param defaultValue
         * @return 
         */
        public List<Object> optArrayValue(String key, List<Object> defaultValue) {
            Object o = get(key);
            if (o instanceof ListContainer) return (ListContainer)o;
            
            return defaultValue;
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
        
        
        /**
         * Conversion of a provided {@code Map} to a {@code Container}
         * structure.
         *
         * <p>
         * XXX Comitted for review; I'm not sure we need this for anything. But can
         * be used for {@link MapContainer#optObjectValue(java.lang.String, java.util.Map)  MapContainer, to convert default
         * arg to a MapContainer return value.
         * 
         * If we decide this is not necessary, just remove it to keep things lean and simple.
         *
         * <p>
         * All values in map that are lists or other maps will also be converted
         * recursively. All contained values that are {@code Map} instances must
         * have string keys, otherwise a {@code ClassCastException} will be
         * thrown.
         *
         * <p>
         * Note that no deep copy of other value types are done.
         *
         * @param map any input map
         * @return a MapContainer with the same keys/values as input map.
         * @throws ClassCastException if maps occur as values that do not have
         * string keys.
         * @throws StackOverflowError if provided map contains reference cycles
         * between maps and/or lists.
         */
        public static MapContainer toContainer(Map<String, Object> map) {
            MapContainer root = new MapContainer();
            for (Map.Entry<String,Object> entry: map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Map) {
                    root.put(key, toContainer((Map<String,Object>)value));
                } else if (value instanceof List) {
                    root.put(key, ListContainer.toContainer((List<?>)value));
                } else {
                    root.put(key, value);
                }
            }
            return root;
        }
    }
    
    /**
     * Cast object to {@link Number} and provide {@link Number#longValue() }, but
     * throw {@link ValueException} if object is not a {@code Number}.
     * @param o should be a {@code Number}.
     * @return an {@code Long} representation of a {@code Number} instance.
     */
    public static Long asLong(Object o) {
        if (!(o instanceof Number)) {
            throw new ValueException("Not a Number: " + o);
        }
        return ((Number)o).longValue();
    }
    
    /**
     * Cast object to {@link Number} and provide {@link Number#intValue() }, but
     * throw {@link ValueException} if object is not a {@code Number}.
     * @param o should be a {@code Number}.
     * @return an {@code Integer} representation of a {@code Number} instance.
     */
    public static Integer asInteger(Object o) {
        if (!(o instanceof Number)) {
            throw new ValueException("Not a Number: "+ o);
        }
        return ((Number)o).intValue();
    }

    /**
     * Cast object to {@link Number} and provide {@link Number#doubleValue() }, but
     * throw {@link ValueException} if object is not a {@code Number}.
     * @param o should be a {@code Number}
     * @return a {@code Double} representation of a {@code Number} instance.
     */
    public static Double asDouble(Object o) {
        if (!(o instanceof Number)) {
            throw new ValueException("Not a Number: " + o);
        }
        return ((Number)o).doubleValue();
    }
    
    /**
     * Cast object to {@link Boolean} and provide {@link Boolean#doubleValue() }, but
     * throw {@link ValueException} if object is not a {@code Boolean}.
     * @param o should be a {@code Boolean}
     * @return a {@code Boolean} value
     */
    public static Boolean asBoolean(Object o) {
        if (!(o instanceof Boolean)) {
            throw new ValueException("Not a Boolean: " + o);
        }
        return (Boolean)o;
    }

    /**
     * Cast object to {@code String} and return value, but throw
     * {@link ValueException} if object is not a {@code String}.
     * @param o should be a {@code String} instance
     * @return a {@code String}
     */
    public static String asString(Object o) {
        if (! (o instanceof String)) {
            throw new ValueException("Not a String: " + o);
        }
        return (String)o;
    }

    /**
     * Interface for a stateful event based JSON parser.
     */
    public interface ParseEvents {
        
        /**
         * Begin parsing process using the provided event handler.
         * @param handler an instace of {@link Handler} provided by client.
         * @throws IOException in case of errors reading data from input
         * @throws vtk.util.text.Json.JsonException in case of JSON parsing errors.
         */
        void begin(Handler handler) throws IOException, JsonException;
        
        /**
         * Resume parsing process using the provided event handler, which
         * may be different from the handler used in {@link #begin(vtk.util.text.Json.Handler) begin}.
         * 
         * <p>The method will resume processing from the place where an earlier {@link Handler}
         * stopped processing events.
         * 
         * @param handler an instace of {@link Handler} provided by client.
         * @throws IOException in case of errors reading data from input
         * @throws vtk.util.text.Json.JsonException in case of JSON parsing errors.
         */
        void resume(Handler handler) throws IOException, JsonException;

    }
    
    private static final class ParseEventsImpl implements ParseEvents {
        private final Reader input;
        private final JSONParser parser;
        
        ParseEventsImpl(Reader input) {
            this.input = input;
            this.parser = new JSONParser();
        }

        @Override
        public void begin(Handler handler) throws IOException, JsonParseException {
            try {
                parser.parse(input, new ContentHandlerAdapter(handler));
            } catch (ParseException pe) {
                throw new JsonParseException("Failed to parse JSON data: " + pe.toString(), pe);
            }
        }

        @Override
        public void resume(Handler handler) throws IOException, JsonParseException {
            try {
                parser.parse(input, new ContentHandlerAdapter(handler), true);
            } catch (ParseException pe) {
                throw new JsonParseException("Failed to parse JSON data: " + pe.toString(), pe);
            }
        }
    }
    
    /**
     * Callback interface for event driven JSON parsing.
     * 
     * <p>Exceptions thrown from callback code must be unchecked, unless
     * they are {@link IOException}. This may change in the future.. You can
     * use {@link JsonException} wrapping the real cause, for instance, and
     * that will propagate back through {@link ParseEvents} methods. (Or any other
     * {@code RuntimeException}.)
     */
    public interface Handler {

        /**
         * Called <em>once</em> at start of JSON stream parsing.
         * @throws IOException 
         */
        void beginJson() throws IOException;
        
        /**
         * Called once at end of JSON stream.
         * @throws IOException 
         */
        void endJson() throws IOException;
        
        /**
         * Called at start of every JSON object occuring in stream, including
         * an outer object.
         * @return {@code false} to stop processing, {@code true} to continue.
         * @throws IOException 
         */
        boolean beginObject() throws IOException;

        /**
         * Called at end of every JSON object occuring in stream, including
         * an outer object.
         * @return {@code false} to stop processing, {@code true} to continue.
         * @throws IOException 
         */
        boolean endObject() throws IOException;

        /**
         * Called at start of object member with key as argument.
         * @param key the member key
         * @return {@code false} to stop processing, {@code true} to continue.
         * @throws IOException 
         */
        boolean beginMember(String key) throws IOException;
        
        /**
         * Called at end of an object member.
         * @return {@code false} to stop processing, {@code true} to continue.
         * @throws IOException 
         */
        boolean endMember() throws IOException;
        
        /**
         * Called at start of every JSON array occuring in stream, including
         * an outer array.
         * @return {@code false} to stop processing, {@code true} to continue.
         * @throws IOException 
         */
        boolean beginArray() throws IOException;
        
        /**
         * Called at end of every JSON array occuring in stream, including
         * an outer array.
         * @return {@code false} to stop processing, {@code true} to continue.
         * @throws IOException 
         */
        boolean endArray() throws IOException;
        
        /**
         * Called for every primitive value occuring in stream, either as part
         * of a JSON array or a JSON object member value.
         * @param value the value, which may be a {@code String},
         * {@code Number}, {@code Boolean} or {@code null}.
         * @return {@code false} to stop processing, {@code true} to continue.
         * @throws IOException 
         */
        boolean primitive(Object value) throws IOException;
    }

    /**
     * Extendable {@link Handler} implementation with default methods which
     * do nothing and always signal to continue processing.
     * 
     * <p>By extending this class, client can choose to override only the
     * necessary methods, instead of implementing the entire {@code Handler}
     * interface.
     */
    public static class DefaultHandler implements Handler {
        @Override
        public void beginJson() throws IOException {}
        @Override
        public void endJson() throws IOException {}
        @Override
        public boolean beginObject() throws IOException { return true; }
        @Override
        public boolean endObject() throws IOException { return true; }
        @Override
        public boolean beginMember(String key) throws IOException { return true; }
        @Override
        public boolean endMember() throws IOException { return true; }
        @Override
        public boolean beginArray() throws IOException { return true; }
        @Override
        public boolean endArray() throws IOException { return true; }
        @Override
        public boolean primitive(Object value) throws IOException { return true; }
    }
    
    /**
     * Adapt json-simple callbacks through {@link ContentHandler} interface to
     * {@link Handler} interface, which in turn calls client code.
     */
    private static final class ContentHandlerAdapter implements ContentHandler {

        private Handler handler;
        
        ContentHandlerAdapter(Handler handler) {
            this.handler = handler;
        }
        
        @Override
        public void startJSON() throws ParseException, IOException {
            handler.beginJson();
        }

        @Override
        public void endJSON() throws ParseException, IOException {
            handler.endJson();
        }

        @Override
        public boolean startObject() throws ParseException, IOException {
            return handler.beginObject();
        }

        @Override
        public boolean endObject() throws ParseException, IOException {
            return handler.endObject();
        }

        @Override
        public boolean startObjectEntry(String key) throws ParseException, IOException {
            return handler.beginMember(key);
        }

        @Override
        public boolean endObjectEntry() throws ParseException, IOException {
            return handler.endMember();
        }

        @Override
        public boolean startArray() throws ParseException, IOException {
            return handler.beginArray();
        }

        @Override
        public boolean endArray() throws ParseException, IOException {
            return handler.endArray();
        }

        @Override
        public boolean primitive(Object value) throws ParseException, IOException {
            return handler.primitive(value);
        }
        
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
