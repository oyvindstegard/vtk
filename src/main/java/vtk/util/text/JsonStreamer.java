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

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;

/**
 * Serialize generic data structures to JSON with a stream-friendly API.
 * 
 * The stream generator is stateful with regard to validation and should prevent client
 * code from creating invalid JSON. It handles comma-separators, value
 * serializing, proper closing of JSON structure and optional pretty printing.
 * 
 * <p>Client code invokes methods to add data forming a logical JSON structure. The
 * serialized data is immediately written to a stream writer instance, but state
 * is kept wrt. to JSON syntax. If client invokes generator methods in wrong
 * order, etc, typically <code>IllegalStateException</code> will be thrown to
 * prevent writing invalid JSON. This condition generally indicates an error in
 * the calling code.
 * 
 * <p>
 * Java type serialization support:
 * <ul>
 * <li>Collections of objects will be serialized to JSON arrays recursively.
 * <li>Java arrays of objects or primitive types will be serialized to JSON
 * arrays recursively.
 * <li>Maps are serialized to JSON objects recursively.
 * <li>Primitive objects of types <code>Boolean</code>, <code>Number</code>,
 * <code>null</code> and <code>String</code> are serialized to the corresponding
 * JSON data types.
 * <li>Objects of type {@link Character} are serialized to JSON strings of length 1.
 * <li>Objects of other kinds are serialized using their {@link Object#toString()}
 * representation.
 * </ul>
 *
 * <p>
 * Note: There is currently no built-in protection against stack overflow
 * error if you try to serialize self-referencing container structures
 * (maps/lists/arrays containing direct or indirect reference cycles)
 * 
 * TODO add simple checking to detect ref-cycles between lists/maps on recursive calls.
 * TODO add support for custom object serialization through "JSONAware" interface
 * or similar.
 * 
 * <p>
 * This class is not thread safe. You should not share instances of this class
 * without external synchronization.
 * 
 * @author oyvind
 */
public class JsonStreamer {
    
    private static final int INITIAL=0, ARRAY=1, OBJECT=2, KEY_VALUE=3;
    
    private static final class Context {
        int state;
        boolean empty;
        int level;
        Context(int state, int level) {
            this.empty = true;
            this.state = state;
            this.level = level;
        }
    }
    
    private final Deque<Context> contexts = new LinkedList<>();
    private final Formatter formatter;

    /**
     * Construct instance writing JSON data to the provided writer.
     * 
     * <p>No pretty printing.
     * 
     * @param writer writer stream to write data to
     */
    public JsonStreamer(Writer writer) {
        this(writer, -1, false);
    }
    
    /**
     * Construct instance writing JSON data to the provided writer.
     * Control escaping of slash chars.
     * 
     * <p>No pretty printing.
     * 
     * @param writer
     * @param escapeSlashes whether to escape forward-slashes with back-slashes in output.
     * The need for this depends on the context of consumer (JavaScript may require this.)
     */
    public JsonStreamer(Writer writer, boolean escapeSlashes) {
        this(writer, -1, escapeSlashes);
    }

    /**
     * Construct instance writing JSON data to the provided writer with
     * pretty printing support.
     * 
     * @param writer the writer stream to write data to
     * @param indent number of spaces to indent data per level of JSON nesting.
     * if less then zero, then pretty printing is disabled
     */
    public JsonStreamer(Writer writer, int indent) {
        this(writer, indent, false);
    }
    
    /**
     * Construct instance writing JSON data to the provided writer with
     * pretty printing support.
     * 
     * @param writer the writer stream to write data to
     * @param indent number of spaces to indent data per level of JSON nesting.
     * if less then zero, then pretty printing is disabled
     * @param escapeSlashes whether to escape forward-slashes with back-slashes in output.
     * The need for this depends on the context of usage (JavaScript requires this.)
     */
    public JsonStreamer(Writer writer, int indent, boolean escapeSlashes) {
        if (indent >= 0) {
            this.formatter = new PrettyFormatter(writer, escapeSlashes, indent, ' ');
        } else {
            this.formatter = new Formatter(writer, escapeSlashes);
        }
        this.contexts.offerFirst(new Context(INITIAL, 0));
    }
    
    /**
     * Utility method for non-streaming usage. Serialize an object value to a JSON
     * string value and return it. No pretty printing.
     * 
     * <p>Object may be a container type ({@code Map<String,Object>, List<Object>})
     * or primitive value.
     * 
     * @param value The value to serialize.
     * @return a string with JSON data.
     */
    public static String toJson(Object value) {
        return toJson(value, -1, false);
    }
    
    /**
     * Utility method for non-streaming usage. Serialize an object value to a JSON
     * string value and return it.
     * 
     * <p>Object may be a container type ({@code Map<String,Object>, List<Object>})
     * or primitive value.
     * 
     * @param value The value to serialize.
     * @param indent number of spaces to indent data per level of JSON nesting (pretty printing).
     * if less then zero, then pretty printing is disabled
     * @return a string with JSON data.
     */
    public static String toJson(Object value, int indent) {
        return toJson(value, indent, false);
    }

    /**
     * Utility method for non-streaming usage. Serialize an object value to a JSON
     * string value and return it.
     * 
     * <p>Object may be a container type ({@code Map<String,Object>, List<Object>})
     * or primitive value.
     * 
     * @param value The value to serialize.
     * @return a string with JSON data.
     * @param escapeSlashes whether to escape forward-slashes with back-slashes in output.
     * The need for this depends on the context of usage (JavaScript requires this.)
     */
    public static String toJson(Object value, boolean escapeSlashes) {
        return toJson(value, -1, escapeSlashes);
    }
    
    /**
     * Utility method for non-streaming usage. Serialize an object value to a JSON
     * string value and return it.
     * 
     * <p>Object may be a container type ({@code Map<String,Object>, List<Object>})
     * or primitive value.
     * 
     * @param value the value to serialize
     * @param escapeSlashes whether to escape forward-slashes with back-slashes in output.
     * The need for this depends on the context of usage (JavaScript requires this.)
     * @param indent number of spaces to indent data per level of JSON nesting.
     * if less then zero, then pretty printing is disabled
     * @return a string with JSON data.
     */
    public static String toJson(Object value, int indent, boolean escapeSlashes) {
        StringBuilderWriter w = new StringBuilderWriter(); // Thin StringBuilder adapter for Writer API
        try {
            new JsonStreamer(w, indent, escapeSlashes).value(value).endJson();
        } catch (IOException io) {} // Won't happen.
        return w.writtenCharsToString();
    }

    /**
     * Start a new array.
     * @return this <code>JsonStreamer</code> instance.
     * @throws IOException if writing to stream fails
     * @throws IllegalStateException if current state is object with no specified key.
     */
    public JsonStreamer beginArray() throws IOException {
        Context ctx = contexts.peek();
        if (ctx.state == OBJECT) {
            throw new IllegalStateException("Cannot begin array without key in object state");
        }
        if (!ctx.empty && ctx.state != KEY_VALUE) {
            formatter.writeValueSeparator(ctx.level);
        }
        
        formatter.writeBeginArray(ctx.state == KEY_VALUE, ctx.level);
        
        contexts.offerFirst(new Context(ARRAY, ctx.level+1));
        return this;
    }

    /**
     * End current array, forming a complete array value.
     * @return this <code>JsonStreamer</code> instance.
     * @throws IOException if writing to stream fails
     * @throws IllegalStateException if current state is not an array.
     */
    public JsonStreamer endArray() throws IOException {
        Context ctx = contexts.poll();
        if (ctx.state != ARRAY) {
            throw new IllegalStateException("Cannot end array when not in array state");
        }
        
        Context enclosing = contexts.peek();
        enclosing.empty = false;
        if (enclosing.state == KEY_VALUE) {
            enclosing.state = OBJECT;
        }

        formatter.writeEndArray(ctx.empty, enclosing.level);
        
        return this;
    }

    /**
     * Begin a new object.
     * @return this <code>JsonStreamer</code> instance.
     * @throws IOException if writing to stream fails
     * @throws IllegalStateException if not outermost object and current state
     * is object with no key specified.
     */
    public JsonStreamer beginObject() throws IOException {
        Context ctx = contexts.peek();
        if (ctx.state == OBJECT) {
            throw new IllegalStateException("Cannot begin object without key in object state");
        }
        
        if (!ctx.empty && ctx.state != KEY_VALUE) {
            formatter.writeValueSeparator(ctx.level);
        }
        formatter.writeBeginObject(ctx.state == KEY_VALUE, ctx.level);
        
        contexts.offerFirst(new Context(OBJECT, ctx.level+1));
        return this;
    }

    /**
     * End current object, forming a complete object value.
     * @return this <code>JsonStreamer</code> instance.
     * @throws IOException if writing to stream fails
     * @throws IllegalStateException if current state is not an object.
     */
    public JsonStreamer endObject() throws IOException {
        Context ctx = contexts.poll();
        if (ctx.state != OBJECT) {
            throw new IllegalStateException("Cannot end object when not in object state");
        }
        
        Context enclosing = contexts.peek();
        enclosing.empty = false;
        if (enclosing.state == KEY_VALUE) {
            enclosing.state = OBJECT;
        }

        formatter.writeEndObject(ctx.empty, enclosing.level);

        return this;
    }

    /**
     * Begin a member by specifying the key.
     * @param key key as string
     * @return this <code>JsonStreamer</code> instance.
     * @throws IOException if writing to stream fails
     * @throws IllegalStateException if current state is not an object.
     */
    public JsonStreamer key(String key) throws IOException {
        if (key == null) {
            throw new IllegalArgumentException("key cannot be null");
        }
        Context ctx = contexts.peek();
        if (ctx.state != OBJECT) {
            throw new IllegalStateException("Cannot start key when not in object state");
        }
        
        if (!ctx.empty) {
            formatter.writeValueSeparator(ctx.level);
        }
        formatter.writeMemberKeyAndColon(key, ctx.level);
        
        ctx.state = KEY_VALUE;
        return this;
    }

    /**
     * Write a value, which can be anything from primitives to full maps forming
     * complete objects.
     * 
     * @param object the value
     * @return this <code>JsonStreamer</code> instance.
     * @throws IOException if writing to stream fails
     * @throws IllegalStateException if current state is object with no specified key.
     * @throws StackOverflowError if object is a structure with reference cycles, which is not supported.
     */
    public JsonStreamer value(Object object) throws IOException {
        Context ctx = contexts.peek();
        if (ctx.state == OBJECT) {
            throw new IllegalStateException("Cannot insert value without key when in object state");
        }
        
        if (object instanceof Map) {
            return object((Map<?,?>)object);
        }
        if (object instanceof Iterable) {
            return array((Iterable<?>)object);
        }
        if (object != null) {
            Class clazz = object.getClass();
            if (clazz.isArray()) {
                javaArrayValue(object);
                return this;
            }
        }
        
        if (!ctx.empty && ctx.state != KEY_VALUE) {
            formatter.writeValueSeparator(ctx.level);
        }

        if (ctx.state == KEY_VALUE) {
            ctx.state = OBJECT;
            formatter.writeValue(object, ctx.level, false);
        } else {
            formatter.writeValue(object, ctx.level, true);
        }
        ctx.empty = false;
        return this;
    }

    /**
     * Write a collection of values, which becomes a JSON array.
     * @param values
     * @return
     * @throws IOException 
     * @throws StackOverflowError if object is a structure with reference cycles, which is not supported.
     */
    public JsonStreamer array(Iterable<?> values) throws IOException {
        beginArray();
        for (Object v: values) {
            value(v);
        }
        endArray();
        return this;
    }

    /**
     * Write a complete object from a map. Map keys are converted to JSON object
     * keys via {@link Object#toString() }.
     * 
     * @param map the object as a map.
     * @return this <code>JsonStreamer</code> instance.
     * @throws IOException if writing to stream fails
     * @throws IllegalStateException if current state is object with no specified key.
     * @throws StackOverflowError if object is a structure with reference cycles, which is not supported.
     */
    public JsonStreamer object(Map<?, ?> map) throws IOException {
        beginObject();
        for (Map.Entry<?,?> entry: map.entrySet()) {
            Object key = entry.getKey();
            member(key == null ? "null" : key.toString(), entry.getValue());
        }
        endObject();
        return this;
    }
    
    /**
     * Write an object member consisting of a key and a value.
     * @param key the key, cannot be <code>null</code>
     * @param value the value, which can be of any supported type.
     * @return this <code>JsonStreamer</code> instance.
     * @throws IOException if writing to stream fails
     * @throws IllegalStateException if current state is not an object
     * @throws StackOverflowError if object is a structure with reference cycles, which is not supported.
     */
    public JsonStreamer member(String key, Object value) throws IOException {
        key(key);
        value(value);
        return this;
    }
    
    /**
     * Write all members of provided map to current object.
     * @param map the map from which to write members to current object stream.
     * @return this <code>JsonStreamer</code> instance.
     * @throws IOException if writing to stream fails
     * @throws IllegalStateException if current state is not an object
     * @throws StackOverflowError if object is a structure with reference cycles, which is not supported.
     */
    public JsonStreamer membersOf(Map<?, ?> map) throws IOException {
        for (Map.Entry<?, ?> entry: map.entrySet()) {
            Object key = entry.getKey();
            member(key == null ? "null" : key.toString(), entry.getValue());
        }
        return this;
    }

    /**
     * Like {@link #member(java.lang.String, java.lang.Object) }, but only write
     * the member if both key and value are non-null.
     * @param key the key, may be <code>null</code>.
     * @param value the value, which can be of any supported kind.
     * @return this <code>JsonStreamer</code> instance.
     * @throws IOException if writing to stream fails
     * @throws IllegalStateException if current state is not an object
     * @throws StackOverflowError if object is a structure with reference cycles, which is not supported.
     */
    public JsonStreamer memberIfNotNull(String key, Object value) throws IOException {
        if (key == null || value == null) {
            return this;
        }
        
        return member(key, value);
    }

    /**
     * Gracefully ends current JSON data by syntactically closing all unclosed scopes
     * and verifying that object or array can be ended in current state without violating
     * JSON syntax.
     * 
     * <p>Note that this method does NOT close the underlying {@link Writer} instance.
     * 
     * @throws IOException 
     */
    public void endJson() throws IOException {
        Context ctx;
        Context previous = null;
        while ((ctx = contexts.poll()) != null) {
            switch (ctx.state) {
                case ARRAY:
                    formatter.writeEndArray(ctx.empty, ctx.level-1);
                    break;
                case OBJECT:
                    formatter.writeEndObject(ctx.empty, ctx.level-1);
                    break;
                case KEY_VALUE:
                    if (previous == null) {
                        throw new IllegalStateException("Cannot end stream in key-value state without a value");
                    } else {
                        formatter.writeEndObject(false, ctx.level-1);
                    }
                    break;
                case INITIAL:
                    contexts.offerFirst(ctx);
                    return;
            }
            previous = ctx;
        }
    }
    
    // Unwrap and serialize any primitive Java array
    private void javaArrayValue(Object javaArray) throws IOException {
        final Class componentType = javaArray.getClass().getComponentType();
        final int len = Array.getLength(javaArray);
        
        beginArray();
        final Context ctx = contexts.peek();
        if (componentType.isArray()) {
            for (int i=0; i<len; i++) {
                Object subArray = Array.get(javaArray, i);
                javaArrayValue(subArray);
                ctx.empty = false;
            }
        } else if (componentType == byte.class) {
            for (int i=0; i<len; i++) {
                if (!ctx.empty) formatter.writeValueSeparator(ctx.level);
                formatter.writeValue(Array.getByte(javaArray, i), ctx.level, true);
                ctx.empty = false;
            }
        } else if (componentType == short.class) {
            for (int i=0; i<len; i++) {
                if (!ctx.empty) formatter.writeValueSeparator(ctx.level);
                formatter.writeValue(Array.getShort(javaArray, i), ctx.level, true);
                ctx.empty = false;
            }
        } else if (componentType == int.class) {
            for (int i=0; i<len; i++) {
                if (!ctx.empty) formatter.writeValueSeparator(ctx.level);
                formatter.writeValue(Array.getInt(javaArray, i), ctx.level, true);
                ctx.empty = false;
            }
        } else if (componentType == long.class) {
            for (int i=0; i<len; i++) {
                if (!ctx.empty) formatter.writeValueSeparator(ctx.level);
                formatter.writeValue(Array.getLong(javaArray, i), ctx.level, true);
                ctx.empty = false;
            }
        } else if (componentType == float.class) {
            for (int i=0; i<len; i++) {
                if (!ctx.empty) formatter.writeValueSeparator(ctx.level);
                formatter.writeValue(Array.getFloat(javaArray, i), ctx.level, true);
                ctx.empty = false;
            }
        } else if (componentType == double.class) {
            for (int i=0; i<len; i++) {
                if (!ctx.empty) formatter.writeValueSeparator(ctx.level);
                formatter.writeValue(Array.getDouble(javaArray, i), ctx.level, true);
                ctx.empty = false;
            }
        } else if (componentType == boolean.class) {
            for (int i=0; i<len; i++) {
                if (!ctx.empty) formatter.writeValueSeparator(ctx.level);
                formatter.writeValue(Array.getBoolean(javaArray, i), ctx.level, true);
                ctx.empty = false;
            }
        } else if (componentType == char.class) {
            for (int i=0; i<len; i++) {
                if (!ctx.empty) formatter.writeValueSeparator(ctx.level);
                formatter.writeValue(Array.getChar(javaArray, i), ctx.level, true);
                ctx.empty = false;
            }
        } else {
            for (int i=0; i<len; i++) {
                value(Array.get(javaArray, i));
            }
        }
        
        endArray();
    }
    
    /**
     * Default serializer/formatter for JSON output
     * Responsibility of formatting syntactical elements and values
     * to a writer. Stateless wrt. syntax.
     */
    private static class Formatter {
        
        private final boolean escapeSlashes;
        protected final Writer w;
        
        Formatter(Writer writer, boolean escapeSlashes) {
            this.escapeSlashes = escapeSlashes;
            this.w = writer;
        }
        
        void writeBeginObject(boolean asKeyValue, int level) throws IOException {
            w.write('{');
        }
        
        void writeEndObject(boolean empty, int level) throws IOException {
            w.write('}');
        }
        
        void writeBeginArray(boolean asKeyValue, int level) throws IOException {
            w.write('[');
        }
        
        void writeEndArray(boolean empty, int level) throws IOException {
            w.write(']');
        }
        
        void writeValueSeparator(int level) throws IOException {
            w.write(',');
        }
        
        void writeMemberKeyAndColon(String key, int level) throws IOException {
            writeEscapedStringValue(key);
            w.write(':');
        }

        /**
         * Write an object value (a non-collection/non-array type).
         * @param value the value to write
         * @param level object/array nesting level
         * @param arrayValue if <code>true</code>, the value is part of a JSON array,
         * otherwise the value is an object member value.
         * @throws IOException in case of errors writing to stream
         */
        void writeValue(Object value, int level, boolean arrayValue) throws IOException {
            writeObjectValue(value);
        }

        /**
         * See {@link #writeValue(java.lang.Object, int, boolean) }.
         */
        void writeValue(char value, int level, boolean arrayValue) throws IOException {
            writeEscapedStringValue(Character.toString(value));
        }
        
        /**
         * See {@link #writeValue(java.lang.Object, int, boolean) }.
         */
        void writeValue(boolean value, int level, boolean arrayValue) throws IOException {
            w.write(value ? "true" : "false");
        }
        
        /**
         * See {@link #writeValue(java.lang.Object, int, boolean) }.
         */
        void writeValue(byte value, int level, boolean arrayValue) throws IOException {
            w.write(Byte.toString(value));
        }
        
        /**
         * See {@link #writeValue(java.lang.Object, int, boolean) }.
         */
        void writeValue(short value, int level, boolean arrayValue) throws IOException {
            w.write(Short.toString(value));
        }
        
        /**
         * See {@link #writeValue(java.lang.Object, int, boolean) }.
         */
        void writeValue(int value, int level, boolean arrayValue) throws IOException {
            w.write(Integer.toString(value));
        }
        
        /**
         * See {@link #writeValue(java.lang.Object, int, boolean) }.
         */
        void writeValue(long value, int level, boolean arrayValue) throws IOException {
            w.write(Long.toString(value));
        }
        
        /**
         * See {@link #writeValue(java.lang.Object, int, boolean) }.
         */
        void writeValue(float value, int level, boolean arrayValue) throws IOException {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                w.write("null");
            } else {
                w.write(Float.toString(value));
            }
        }
        
        /**
         * See {@link #writeValue(java.lang.Object, int, boolean) }.
         */
        void writeValue(double value, int level, boolean arrayValue) throws IOException {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                w.write("null");
            } else {
                w.write(Double.toString(value));
            }
        }
        
        private void writeObjectValue(Object value) throws IOException {
            if (value == null) {
                w.write("null");
            } else if (value instanceof String) {
                writeEscapedStringValue((String)value);
            } else if (value instanceof Number) {
                if (value instanceof Double) {
                    Double dbl = (Double)value;
                    if (dbl.isInfinite() || dbl.isNaN()) {
                        w.write("null");
                    } else {
                        w.write(dbl.toString());
                    }
                } else if (value instanceof Float) {
                    Float flt = (Float)value;
                    if (flt.isInfinite() || flt.isNaN()) {
                        w.write("null");
                    } else {
                        w.write(flt.toString());
                    }
                } else {
                    w.write(value.toString());
                }
            } else if (value instanceof Boolean) {
                w.write(value.toString());
            } else {
                writeEscapedStringValue(value.toString());
            }
        }

        private void writeEscapedStringValue(final String value) throws IOException {
            w.write('\"');
            final int len = value.length();
            for (int i=0; i<len; i++) {
                final char c = value.charAt(i);
                switch (c) {
                    case '\"': w.write("\\\""); break;
                    case '\\': w.write("\\\\"); break;
                    case '\b': w.write("\\b"); break;
                    case '\f': w.write("\\f"); break;
                    case '\n': w.write("\\n"); break;
                    case '\r': w.write("\\r"); break;
                    case '\t': w.write("\\t"); break;
                        
                    case '/': 
                        w.write(escapeSlashes ? "\\/" : "/");
                        break;
                    
                    default:
                        if ((c >= 0 && c <= 0x1F)
                                || (c >= 0x7F && c <= 0x9F)
                                || (c >= 0x2000 && c <= 0x20FF)) { // Some unicode control stuff
                            w.write(TextUtils.toUnicodeEscape(c));
                        } else {
                            w.write(c);
                        }
                }
            }
            w.write('\"');
        }
    }

    // Pretty printing serializer/formatter for JSON output
    private static final class PrettyFormatter extends Formatter {

        private char[] indentLevel;
        
        PrettyFormatter(Writer w, boolean escapeSlashes, int indent, char indentChar) {
            super(w, escapeSlashes);
            if (indent >= 0){
                indentLevel = new char[indent];
                for (int i=0; i<indent; i++) {
                    indentLevel[i] = indentChar;
                }
            } 
        }
        
        private void writeNewLine(int level) throws IOException {
            w.write('\n');
            for (int i=0; i<level; i++) {
                w.write(indentLevel);
            }
        }

        @Override
        void writeValue(Object value, int level, boolean arrayValue) throws IOException {
            if (arrayValue) writeNewLine(level);
            super.writeValue(value, level, arrayValue);
        }

        @Override
        void writeValue(double value, int level, boolean arrayValue) throws IOException {
            if (arrayValue) writeNewLine(level);
            super.writeValue(value, level, arrayValue);
        }

        @Override
        void writeValue(float value, int level, boolean arrayValue) throws IOException {
            if (arrayValue) writeNewLine(level);
            super.writeValue(value, level, arrayValue);
        }

        @Override
        void writeValue(long value, int level, boolean arrayValue) throws IOException {
            if (arrayValue) writeNewLine(level);
            super.writeValue(value, level, arrayValue);
        }

        @Override
        void writeValue(int value, int level, boolean arrayValue) throws IOException {
            if (arrayValue) writeNewLine(level);
            super.writeValue(value, level, arrayValue);
        }

        @Override
        void writeValue(short value, int level, boolean arrayValue) throws IOException {
            if (arrayValue) writeNewLine(level);
            super.writeValue(value, level, arrayValue);
        }

        @Override
        void writeValue(byte value, int level, boolean arrayValue) throws IOException {
            if (arrayValue) writeNewLine(level);
            super.writeValue(value, level, arrayValue);
        }

        @Override
        void writeValue(boolean value, int level, boolean arrayValue) throws IOException {
            if (arrayValue) writeNewLine(level);
            super.writeValue(value, level, arrayValue);
        }

        @Override
        void writeValue(char value, int level, boolean arrayValue) throws IOException {
            if (arrayValue) writeNewLine(level);
            super.writeValue(value, level, arrayValue);
        }
        
        @Override
        void writeMemberKeyAndColon(String key, int level) throws IOException {
            writeNewLine(level);
            super.writeMemberKeyAndColon(key, level);
            w.write(' ');
        }

        @Override
        void writeBeginArray(boolean asKeyValue, int level) throws IOException {
            if (!asKeyValue && level > 0) {
                writeNewLine(level);
            }
            super.writeBeginArray(asKeyValue, level);
        }

        @Override
        void writeBeginObject(boolean asKeyValue, int level) throws IOException {
            if (!asKeyValue && level > 0) {
                writeNewLine(level);
            }
            super.writeBeginObject(asKeyValue, level);
        }
        
        @Override
        void writeEndArray(boolean empty, int level) throws IOException {
            if (!empty)  {
                writeNewLine(level);
            }
            super.writeEndArray(empty, level);
            if (level == 0) {
                writeNewLine(0);
            }
        }

        @Override
        void writeEndObject(boolean empty, int level) throws IOException {
            if (!empty) {
                writeNewLine(level);
            }
            super.writeEndObject(empty, level);
            if (level == 0) {
                writeNewLine(0);
            }
        }
    }
    
    /**
     * String writer using a <code>StringBuilder</code> internally. Can be used
     * to build JSON strings using streaming API methods, by passing an instance
     * of this class to <code>JsonStreamer</code>. Essentially the same as {@link java.io.StringWriter
     * }, but doesn't use <code>StringBuffer</code> internally.
     */
    static final class StringBuilderWriter extends Writer {

        private final StringBuilder buffer = new StringBuilder(); 
        
        @Override
        public void write(String str, int off, int len) {
            buffer.append(str, off, off + len);
        }

        @Override
        public void write(String str) {
            buffer.append(str);
        }

        @Override
        public void write(char[] cbuf) {
            buffer.append(cbuf);
        }

        @Override
        public void write(int c) {
            buffer.append((char) c);
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            buffer.append(cbuf, off, len);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
        
        /**
         * Get internal <code>StringBuilder</code> buffer.
         * @return 
         */
        public StringBuilder buffer() {
            return buffer;
        }
        
        /**
         * Get string of chars written so far.
         * @return 
         */
        public String writtenCharsToString() {
            return buffer.toString();
        }
        
        /**
         * Clear all data written to internal string builder.
         */
        public void clear() {
            buffer.setLength(0);
        }

        @Override
        public String toString() {
            return writtenCharsToString();
        }
    }
}
