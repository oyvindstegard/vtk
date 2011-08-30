/* Copyright (c) 2011, University of Oslo, Norway
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
package org.vortikal.util.text;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.vortikal.util.io.StreamUtil;

public final class JSON {
    
    private static final int MAX_LENGTH = 500000;

    public static Object parse(String input) {
        Object o = JSONSerializer.toJSON(input);
        return unwrap(o);
    }
    
    public static Object parse(InputStream input) throws IOException {
        byte[] buf = StreamUtil.readInputStream(input, MAX_LENGTH);
        String str = new String(buf, "utf-8");
        Object o = JSONSerializer.toJSON(str);
        return unwrap(o);
    }
    
    private static Object unwrap(Object object) {
        if (! (object instanceof net.sf.json.JSON)) {
            return object;
        }
        net.sf.json.JSON json = (net.sf.json.JSON) object;
        if (json instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) json;
            Map<Object, Object> m = new HashMap<Object, Object>();
            for (Object o: jsonObject.keySet()) {
                Object value = jsonObject.get(o);
                o = unwrap(o);
                value = unwrap(value);
                m.put(o, value);
            }
            return m;
        } else if (json instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) json;
            List<Object> list = new ArrayList<Object>();
            for (Object o: jsonArray) {
                list.add(unwrap(o));
            }
            return list;
        } else if (json instanceof JSONNull) {
            return null;
        }
        return object;
    }
}
