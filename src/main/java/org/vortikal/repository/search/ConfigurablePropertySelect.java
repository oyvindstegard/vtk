/* Copyright (c) 2007, University of Oslo, Norway
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
package org.vortikal.repository.search;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.vortikal.repository.resourcetype.PropertyTypeDefinition;

/**
 * {@link PropertySelect} selecting based on a configured set of
 * added {@link PropertyTypeDefinition property type definitions}.
 */
public class ConfigurablePropertySelect implements PropertySelect {
    private Set<PropertyTypeDefinition> properties = new HashSet<PropertyTypeDefinition>();
    
    public ConfigurablePropertySelect() {
    }
    
    public ConfigurablePropertySelect(Collection<PropertyTypeDefinition> properties) {
        for (PropertyTypeDefinition p: properties) {
            this.properties.add(p);
        }
    }
    
    public void addPropertyDefinition(PropertyTypeDefinition def) {
        this.properties.add(def);
    }

    public boolean isEmpty() {
        return this.properties.isEmpty();
    }

    public boolean isIncludedProperty(PropertyTypeDefinition def) {
        return this.properties.contains(def);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getName()).append(":");
        sb.append("properties = ").append(this.properties);
        return sb.toString();
    }
    
}
