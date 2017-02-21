/* Copyright (c) 2012–2017 University of Oslo, Norway
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
package vtk.repository.index.mapping;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;

import vtk.repository.Acl;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.PropertySetImpl;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.PropertySelect;

/**
 * Lazyily mapped
 * <code>PropertySet</code>. Does minimal work in constructor, and maps
 * on-demand requested props and attributes from internal Lucene fields.
 * 
 * <p>Implements the {@link PropertySet} interface, and has additional methods
 * for retrieving the ACL without requiring a full {@link Resource} instance.
 */
public final class LazyMappedPropertySet implements PropertySet {

    private Path uri;
    private int id = -1;
    private String resourceType;
    private List<IndexableField> propFields;
    private List<IndexableField> aclFields;
    private final DocumentMapper mapper;

    LazyMappedPropertySet(Document doc, DocumentMapper mapper) throws DocumentMappingException {
        for (IndexableField f : doc) {
            if (ResourceFields.ID_FIELD_NAME.equals(f.name())) {
                id = Integer.parseInt(f.stringValue());
                continue;
            }
            
            if (ResourceFields.URI_FIELD_NAME.equals(f.name())) {
                uri = Path.fromString(f.stringValue());
                continue;
            }

            if (ResourceFields.RESOURCETYPE_PATH_FIELD_NAME.equals(f.name())) {
                resourceType = mapper.getResourceFields().resolveResourceType(f.stringValue());
                continue;
            }
            
            if (AclFields.isAclField(f.name())) {
                if (aclFields == null) {
                    aclFields = new ArrayList<>();
                }
                aclFields.add(f);
                continue;
            }

            if (!PropertyFields.isPropertyField(f.name())) {
                continue;
            }

            if (propFields == null) {
                propFields = new ArrayList<>();
            }

            propFields.add(f);
        }

        if (uri == null) {
            throw new DocumentMappingException("Document missing required field "
                    + ResourceFields.URI_FIELD_NAME + ": " + doc);
        }
        if (resourceType == null) {
            throw new DocumentMappingException("Document missing required field "
                    + ResourceFields.RESOURCETYPE_PATH_FIELD_NAME + ": " + doc);
        }
        this.mapper = mapper;
    }

    @Override
    public Path getURI() {
        return this.uri;
    }
    
    @Override
    public int getID() {
        return this.id;
    }

    @Override
    public String getName() {
        return this.uri.getName();
    }

    @Override
    public String getResourceType() {
        return this.resourceType;
    }

    @Override
    public List<Property> getProperties() {
        if (propFields == null) {
            return new ArrayList<>(0);
        }

        // Lucene guarantees stored field order to be same as when document was indexed
        final List<Property> props = new ArrayList<>(propFields.size());
        for (int i = 0; i < propFields.size(); i++) {
            IndexableField f = propFields.get(i);
            List<IndexableField> values = new ArrayList<>();
            values.add(f);
            while (i < propFields.size() - 1 && f.name().equals(propFields.get(i + 1).name())) {
                values.add(propFields.get(++i));
            }
            props.add(mapper.propertyFromFields(f.name(), values));
        }
        return props;
    }

    @Override
    public List<Property> getProperties(Namespace namespace) {
        if (propFields == null) {
            return new ArrayList<>(0);
        }

        // Lucene guarantees stored field order to be same as when document was indexed
        final List<Property> props = new ArrayList<>();
        for (int i = 0; i < propFields.size(); i++) {
            final IndexableField f = propFields.get(i);
            List<IndexableField> values = null;
            if (PropertyFields.isPropertyFieldInNamespace(f.name(), namespace)) {
                values = new ArrayList<>();
                values.add(f);
            }
            while (i < propFields.size() - 1 && f.name().equals(propFields.get(i + 1).name())) {
                ++i;
                if (values != null) {
                    values.add(propFields.get(i));
                }
            }
            if (values != null) {
                props.add(mapper.propertyFromFields(f.name(), values));
            }
        }

        return props;
    }

    @Override
    public Property getProperty(PropertyTypeDefinition def) {
        return getProperty(def.getNamespace(), def.getName());
    }

    @Override
    public Property getProperty(Namespace ns, String name) {
        if (propFields == null) {
            return null;
        }

        // Lucene guarantees stored field order to be same as when document was indexed
        final String fieldName = PropertyFields.propertyFieldName(name, ns.getPrefix(), false, false);
        List<IndexableField> values = null;
        for (IndexableField f : propFields) {
            if (fieldName.equals(f.name())) {
                if (values == null) {
                    values = new ArrayList<>();
                }
                values.add(f);
            } else if (values != null) break; // All fields for property collected.
        }

        return values != null ? mapper.propertyFromFields(fieldName, values) : null;
    }

    @Override
    public Property getPropertyByPrefix(String prefix, String name) {
        if (propFields == null) {
            return null;
        }

        // Lucene guarantees stored field order to be same as when document was indexed
        final String fieldName = PropertyFields.propertyFieldName(name, prefix, false, false);
        List<IndexableField> values = null;
        for (IndexableField f : propFields) {
            if (fieldName.equals(f.name())) {
                if (values == null) {
                    values = new ArrayList<>();
                }
                values.add(f);
            } else if (values != null) break; // All fields for property collected.
        }

        return values != null ? mapper.propertyFromFields(fieldName, values) : null;
    }

    @Override
    public Iterator<Property> iterator() {
        return getProperties().iterator();
    }

    /**
     * Return resource ACL associated with the property set. Only available
     * if ACL has been selected for loading.
     * 
     * @see PropertySelect#isIncludeAcl() 
     * 
     * @return an <code>Acl</code> instance.
     */
    public Acl getAcl() {
        if (aclFields == null) {
            return null;
        }
        
        return mapper.getAclFields().fromFields(aclFields);
    }

    /**
     * @return <code>true</code> if the ACL present for this property set is
     * inherited from some ancestor property set.
     */
    public boolean isInheritedAcl() {
        return getAclInheritedFrom() != PropertySetImpl.NULL_RESOURCE_ID;
    }
    
    /**
     * @return Returns id of resource that this property set inherits its ACL from.
     */
    public int getAclInheritedFrom() {
        if (aclFields == null) {
            return PropertySetImpl.NULL_RESOURCE_ID;
        }

        return AclFields.aclInheritedFrom(aclFields);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PropertySet");
        sb.append(" [").append(uri).append(", resourcetype = ").append(resourceType).append("]");
        return sb.toString();
    }
}
