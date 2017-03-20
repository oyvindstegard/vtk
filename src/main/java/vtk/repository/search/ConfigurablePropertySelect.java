/* Copyright (c) 2007-2017, University of Oslo, Norway
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
package vtk.repository.search;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import vtk.repository.Namespace;

import vtk.repository.resourcetype.PropertyTypeDefinition;

/**
 * {@link PropertySelect} selecting based on a configured set of
 * added {@link PropertyTypeDefinition property type definitions}.
 */
public class ConfigurablePropertySelect implements PropertySelect {

    // XXX Compare property type definitions by namespace and name due to missing
    // equals/hashcode in property type definition classes and use of overriding definitions, which
    // causes issues with canonical instance when only using object compare
    private final Map<Namespace, Set<String>> properties = new HashMap<>();
    
    private boolean includeAcl = false;

    /**
     * Construct an empty select instance which initially selects no properties and no ACLs.
     */
    public ConfigurablePropertySelect() {
    }

    /**
     * Construct a select instance which initially selects the single provided property
     * and no ACLs.
     * @param def
     */
    public ConfigurablePropertySelect(PropertyTypeDefinition def) {
        addPropertyDefinition(def);
    }

    /**
     * Construct a select instance which initially selects all properties in the provided collection
     * and no ACLs.
     * @param properties
     */
    public ConfigurablePropertySelect(Collection<PropertyTypeDefinition> properties) {
        for (PropertyTypeDefinition p: properties) {
            addPropertyDefinition(p);
        }
    }
    
    public final void addPropertyDefinition(PropertyTypeDefinition pd) {
        properties.computeIfAbsent(pd.getNamespace(), ns -> new HashSet<>()).add(pd.getName());
    }

    public boolean isEmpty() {
        return properties.isEmpty();
    }

    @Override
    public boolean isIncludedProperty(PropertyTypeDefinition def) {
        return properties.getOrDefault(def.getNamespace(), Collections.emptySet()).contains(def.getName());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getName()).append("{");
        sb.append("properties: ").append(this.properties);
        sb.append(", includeAcl: ").append(this.includeAcl);
        sb.append("}");
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 71 * hash + Objects.hashCode(this.properties);
        hash = 71 * hash + (this.includeAcl ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ConfigurablePropertySelect other = (ConfigurablePropertySelect) obj;
        if (this.includeAcl != other.includeAcl) {
            return false;
        }
        if (!Objects.equals(this.properties, other.properties)) {
            return false;
        }
        return true;
    }


    /**
     * Set if ACLs objects should be included for search results. Default value
     * is <code>false</code>.
     * 
     * @param includeAcl <code>true</code> if ACLs should be loaded and included
     * in search results.
     */
    public void setIncludeAcl(boolean includeAcl) {
        this.includeAcl = includeAcl;
    }

    /**
     * 
     * @return <code>true</code> if ACLs will be included for search results.
     */
    @Override
    public boolean isIncludeAcl() {
        return includeAcl;
    }
    
}
