/* Copyright (c) 2006, 2007, University of Oslo, Norway
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
package org.vortikal.repository;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.util.repository.LocaleHelper;

/**
 * Implementation of {@link org.vortikal.repository.PropertySet}.
 * 
 *
 */
public class PropertySetImpl implements PropertySet, Cloneable, Serializable {

    private static final long serialVersionUID = 6482843397243107314L;

    /**
     * Numeric ID used to represent the NULL resource ("no resource").
     */
    public static final int NULL_RESOURCE_ID = -1;
    
    protected Path uri;
    protected String resourceType;
    protected Map<Namespace, Map<String, Property>> propertyMap =
        new HashMap<Namespace, Map<String, Property>>();
    
    // Numeric ID used by database 
    protected int id = NULL_RESOURCE_ID;
    
    protected boolean aclInherited = true;

    // Numeric ID of resource from which this resource inherits its ACL definition.
    private int aclInheritedFrom = NULL_RESOURCE_ID;
                               
    // Numeric ID of ancestors. Might be null. Used by indexing system.
    private int[] ancestorIds;  
   
    // Note: needs uri set explicitly
    public PropertySetImpl() {
    }

    public int getID() {
        return this.id;
    }

    public void setID(int id) {
        this.id = id;
    }
     
    @Override
    public Path getURI() {
        return this.uri;
    }

    @Override
    public String getName() {
        return this.uri.getName();
    }

    @Override
    public String getResourceType() {
        return this.resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public void setAclInheritedFrom(int aclInheritedFrom) {
        this.aclInheritedFrom = aclInheritedFrom;
        if (aclInheritedFrom == NULL_RESOURCE_ID) {
            this.aclInherited = false;
        } else {
            this.aclInherited = true;
        }
    }

    public void setInheritedAcl(boolean aclInherited) {
        this.aclInherited = aclInherited;
        if (!aclInherited) {
            this.aclInheritedFrom = NULL_RESOURCE_ID;
        }
    }
    
    public int getAclInheritedFrom() {
        return this.aclInheritedFrom;
    }
    

    public boolean isInheritedAcl() {
        return this.aclInherited;
    }


    public void addProperty(Property property) {
        PropertyTypeDefinition propDef = property.getDefinition();
        Map<String, Property> map = this.propertyMap.get(propDef.getNamespace());
        if (map == null) {
            map = new HashMap<String, Property>();
            this.propertyMap.put(propDef.getNamespace(), map);
        }
        map.put(propDef.getName(), property);
    }
 
    @Override
    public Property getProperty(PropertyTypeDefinition type) {
        return getProperty(type.getNamespace(), type.getName());
    }
    
    @Override
    public Property getPropertyByPrefix(String prefix, String name) {
        Namespace namespace = Namespace.getNamespaceFromPrefix(prefix);

        if (namespace.getPrefix() != null && 
                namespace.getPrefix().equals(namespace.getUri())) {
            // Namespace URI is unknown, look up by prefix-comparison.
            for (Namespace ns : this.propertyMap.keySet()) {
                if (prefix.equals(ns.getPrefix())) {
                    namespace = ns;
                    break;
                }
            }
        }
        
        Map<String, Property> map = this.propertyMap.get(namespace);
        if (map == null) return null;
        return map.get(name);
    }

    @Override
    public Property getProperty(Namespace namespace, String name) {
        Map<String, Property> map = this.propertyMap.get(namespace);
        if (map == null) return null;
        return map.get(name);
    }
    
    public Locale getContentLocale() {
        Property contentLanguage = getProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.CONTENTLOCALE_PROP_NAME);
        if (contentLanguage != null) {
            return LocaleHelper.getLocale(contentLanguage.getStringValue());
        }
        return null;
    }

    @Override
    public List<Property> getProperties(Namespace namespace) {
        Map<String, Property> map = this.propertyMap.get(namespace);
        if (map == null) return new ArrayList<Property>();
        return new ArrayList<Property>(map.values());
    }

    @Override
    public List<Property> getProperties() {
        List<Property> props = new ArrayList<Property>(20);
        for (Map<String, Property> map: this.propertyMap.values()) {
            props.addAll(map.values());
        }
        return props;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        
        PropertySetImpl clone = new PropertySetImpl();
        clone.resourceType = this.resourceType;
        clone.setUri(this.uri);
        clone.setAclInheritedFrom(this.aclInheritedFrom);
        clone.setInheritedAcl(this.aclInherited);

        // Clone all props:
        for (Map.Entry<Namespace, Map<String,Property>> entry: this.propertyMap.entrySet()) {
            Namespace ns = entry.getKey();
            Map<String,Property> propMap = entry.getValue();
            Map<String,Property> clonePropMap = new HashMap<String,Property>(propMap.size() + propMap.size()/2);
            for (Map.Entry<String,Property> propEntry: propMap.entrySet()) {
                clonePropMap.put(propEntry.getKey(), (Property)propEntry.getValue().clone());
            }
            clone.propertyMap.put(ns, clonePropMap);
        }
        
        return clone;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append(" [").append(this.uri).append("]");
        return sb.toString();
    }
    
    public int[] getAncestorIds() {
        return this.ancestorIds;
    }

    public void setAncestorIds(int[] ancestorIds) {
        this.ancestorIds = ancestorIds;
    }


    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PropertySetImpl))
            return false;
        PropertySetImpl other = (PropertySetImpl) obj;
        if (!this.uri.equals(other.uri)) return false;
        if (!this.resourceType.equals(other.resourceType)) return false;
        if (!this.propertyMap.equals(other.propertyMap)) return false;
        if (this.id != other.id) return false;
        if (this.aclInheritedFrom != other.aclInheritedFrom) return false;
        if (this.ancestorIds == null && other.ancestorIds != null) return false;
        if (this.ancestorIds != null && other.ancestorIds == null) return false;
        if (this.ancestorIds != null) {
            for (int i = 0; i < this.ancestorIds.length; i++) {
                if (this.ancestorIds[i] != other.ancestorIds[i]) return false;
            }
        }
        return true;
    }

    public void setUri(Path uri) {
        this.uri = uri;
    }
    
}
