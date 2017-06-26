/* Copyright (c) 2015, University of Oslo, Norway
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
package vtk.web.decorating.tl;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable map from String to Object with vararg 
 * constructor for instantiating with multiple entries:
 * <p>
 * <code>
 *   DomainMap map = new DomainMap(
 *     "key1", value1,
 *     "key2", value2,
 *     ...,
 *     "keyN", valueN);
 * </code>
 */
public class DomainMap extends AbstractMap<String, Object> {

    private Set<Map.Entry<String, Object>> entrySet;
    
    public DomainMap(Object... mappings) {
        super();
        Set<Map.Entry<String, Object>> entrySet = new HashSet<>();
        if (mappings.length % 2 != 0) 
            throw new IllegalArgumentException(
                    "Argument length must be an even number");
        for (int i = 0; i < mappings.length; i+=2) {
            final String key = mappings[i].toString();
            final Object value = mappings[i+1];
            entrySet.add(new Map.Entry<String, Object>() {
                @Override
                public String getKey() { 
                    return key; 
                }
                @Override
                public Object getValue() { 
                    return value; 
                }
                @Override
                public Object setValue(Object value) {
                    throw new UnsupportedOperationException();
                }
            });
        }
        this.entrySet = Collections.unmodifiableSet(entrySet);
    }
    
    @Override
    public Set<java.util.Map.Entry<String, Object>> entrySet() {
        return entrySet;
    }
}
