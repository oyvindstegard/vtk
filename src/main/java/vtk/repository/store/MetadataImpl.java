/* Copyright (c) 2010, University of Oslo, Norway
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
package vtk.repository.store;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Default implementation of container for metadata
 */
public class MetadataImpl implements Metadata, Serializable {

    private static final long serialVersionUID = -8461011811966268434L;
    private Map<String, List<Object>> attributes = new HashMap<String, List<Object>>();

    public void addAttributeValue(String attribute, Object value) {
        List<Object> values = this.attributes.get(attribute);
        if (values == null) {
            values = new ArrayList<Object>();
            this.attributes.put(attribute, values);
        }
        values.add(value);
    }

    public void setAttributeValues(String name, List<Object> values) {
        if (values == null) {
            this.attributes.remove(name);
        } else {
            this.attributes.put(name, values);
        }
    }

    @Override
    public Object getValue(String attributeName) {
        List<Object> values = this.attributes.get(attributeName);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    @Override
    public List<Object> getValues(String attributeName) {
        List<Object> values = this.attributes.get(attributeName);
        if (values != null) {
            return Collections.unmodifiableList(values);
        }

        return null;
    }

    @Override
    public Set<String> getAttributeNames() {
        return Collections.unmodifiableSet(this.attributes.keySet());
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + attributes + ")";
    }

}
