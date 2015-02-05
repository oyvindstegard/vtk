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

import java.io.IOException;
import java.util.Map;

/**
 * Like {@link JsonStreamer}, but with an API geared towards building JSON strings
 * in memory. Sometimes this is more convenient.
 * 
 * <p>Uses {@code JsonStreamer} internally, but no methods can throw {@code IOException}.
 */
public class JsonBuilder {
    
    private final JsonStreamer.StringBuilderWriter writer = new JsonStreamer.StringBuilderWriter();
    private final JsonStreamer js;

    /**
     * Like {@link JsonStreamer#JsonStreamer(java.io.Writer, int, boolean) }, but
     * no writer is necessary.
     */
    public JsonBuilder(int indent, boolean escapeSlashes) {
        js = new JsonStreamer(writer, indent, escapeSlashes);
    }

    /**
     * Like {@link JsonStreamer#JsonStreamer(java.io.Writer, boolean) , but no
     * writer is necessary.
     */
    public JsonBuilder(boolean escapeSlashes) {
        js = new JsonStreamer(writer, escapeSlashes);
    }

    /**
     * Like {@link JsonStreamer#JsonStreamer(java.io.Writer, int), but no
     * writer is necessary.
     */
    public JsonBuilder(int indent) {
        js = new JsonStreamer(writer, indent);
    }

    /**
     * Get the JSON string built so far. May not be a complete and closed JSON object
     * or array if the building is not in a "closed state".
     * 
     * <p>To make sure you get a complete object, call {@link #endJson() } first.
     * 
     * @return a string of JSON data built so far.
     */
    public String jsonString() {
        return writer.writtenCharsToString();
    }
    
    /**
     * Returns the same as {@link #jsonString() }.
     * @return a string of JSON data built so far.
     */
    @Override
    public String toString() {
        return jsonString();
    }
    
    /**
     * Clear all data in internal JSON string buffer and reset JSON state
     * (start from scratch).
     */
    public void clearJson() {
        endJson();
        writer.clear();
    }
    
    /**
     * Like {@link JsonStreamer#JsonStreamer(java.io.Writer) }, but no
     * writer is necessary.
     */
    public JsonBuilder() {
        js = new JsonStreamer(writer);
    }
    
    /**
     * Like {@link JsonStreamer#beginArray() }, but no {@link java.io.IOException}
     * can be thrown. 
     */
    public JsonBuilder beginArray() {
        try {
            js.beginArray();
        } catch (IOException io) {
        }
        return this;
    }

    /**
     * Like {@link JsonStreamer#endArray() }, but no {@link java.io.IOException}
     * can be thrown. 
     */
    public JsonBuilder endArray() {
        try {
            js.endArray();
        } catch (IOException io) {
        }
        return this;
    }

    /**
     * Like {@link JsonStreamer#beginObject() }, but no {@link java.io.IOException}
     * can be thrown. 
     */
    public JsonBuilder beginObject() {
        try {
            js.beginObject();
        } catch (IOException io) {
        }
        return this;
    }

    /**
     * Like {@link JsonStreamer#endObject() }, but no {@link java.io.IOException}
     * can be thrown. 
     */
    public JsonBuilder endObject() {
        try {
            js.endObject();
        } catch (IOException io) {}
        return this;
    }

    /**
     * Like {@link JsonStreamer#key(java.lang.String)   }, but no {@link java.io.IOException}
     * can be thrown. 
     */
    public JsonBuilder key(String key) {
        try {
            js.key(key);
        } catch (IOException io) {}
        return this;
    }

    /**
     * Like {@link JsonStreamer#value(java.lang.Object)  }, but no {@link java.io.IOException}
     * can be thrown. 
     */
    public JsonBuilder value(Object object) {
        try {
            js.value(object);
        } catch (IOException io) {}
        return this;
    }

    /**
     * Like {@link JsonStreamer#array(java.util.List) }, but no {@link java.io.IOException}
     * can be thrown. 
     */
    public JsonBuilder array(Iterable<?> values) {
        try {
            js.array(values);
        } catch (IOException io) {}
        return this;
    }

    /**
     * Like {@link JsonStreamer#object(java.util.Map) }, but no {@link java.io.IOException}
     * can be thrown.
     */
    public JsonBuilder object(Map<?, ?> map) {
        try {
            js.object(map);
        } catch (IOException io) {}
        return this;
    }

    /**
     * Like {@link JsonStreamer#member(java.lang.String, java.lang.Object) },
     * but no {@link java.io.IOException} can be thrown.
     */
    public JsonBuilder member(String key, Object value) {
        try {
            js.key(key);
            js.value(value);
        } catch (IOException io) {}
        return this;
    }

    /**
     * Like {@link JsonStreamer#membersOf(java.util.Map) }, but no {@link java.io.IOException}
     * can be thrown.
     */
    public JsonBuilder membersOf(Map<?, ?> map) {
        try {
            js.membersOf(map);
        } catch (IOException io) {}
        return this;
    }
    
    /**
     * Like {@link JsonStreamer#memberIfNotNull(java.lang.String, java.lang.Object) }, 
     * but no {@link java.io.IOException} can be thrown.
     */
    public JsonBuilder memberIfNotNull(String key, Object value) {
        try {
            js.memberIfNotNull(key, value);
        } catch (IOException io) {}
        return this;
    }

    /**
     * Like {@link JsonStreamer#endJson() }, 
     * but no {@link java.io.IOException} can be thrown.
     */
    public void endJson() {
        try {
            js.endJson();
        } catch (IOException io) {}
    }
    
}
