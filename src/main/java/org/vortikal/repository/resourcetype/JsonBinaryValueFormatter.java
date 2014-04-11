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
package org.vortikal.repository.resourcetype;

import java.io.UnsupportedEncodingException;
import java.util.Locale;
import net.sf.json.JSONSerializer;

/**
 *
 */
public class JsonBinaryValueFormatter implements ValueFormatter {

    @Override
    public String valueToString(Value value, String format, Locale locale) throws IllegalValueTypeException {
        if (value.getType() != PropertyType.Type.JSON_BINARY) {
            throw new IllegalValueTypeException(PropertyType.Type.JSON_BINARY, value.getType());
        }
        try {
            return new String(value.getBinaryValue().getBytes(), "UTF-8");
        } catch (UnsupportedEncodingException ue) {
            throw new IllegalStateException("UTF-8 encoding not available");
        }
    }

    @Override
    public Value stringToValue(String string, String format, Locale locale) {

        try {
            JSONSerializer.toJSON(string);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unparseable JSON string", e);
        }
        
        try {
            return new Value(string.getBytes("UTF-8"), "application/json", PropertyType.Type.JSON_BINARY);
        } catch (UnsupportedEncodingException ue) {
            throw new IllegalStateException("UTF-8 encoding not available");
        }
    }
    
}
