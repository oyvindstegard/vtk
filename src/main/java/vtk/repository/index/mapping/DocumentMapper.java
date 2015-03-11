/* Copyright (c) 2006,2012 University of Oslo, Norway
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

import java.io.IOException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Document;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.PropertySetImpl;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.PrimaryResourceTypeDefinition;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.ResourceTypeDefinition;
import vtk.repository.search.PropertySelect;
import vtk.security.PrincipalFactory;


import org.apache.lucene.document.DocumentStoredFieldVisitor;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.StoredFieldVisitor;
import vtk.repository.Acl;


import vtk.repository.resourcetype.ValueFactory;

/**
 * Mapping from Lucene {@link org.apache.lucene.document.Document} to
 * {@link vtk.repository.PropertySet} objects and vice-versa.
 * 
 */
public class DocumentMapper implements InitializingBean {

    private final Log logger = LogFactory.getLog(DocumentMapper.class);

    private ResourceTypeTree resourceTypeTree;
    private PrincipalFactory principalFactory;
    private ValueFactory valueFactory;
    private Locale locale;
    
    // Fields impls for indexed resource aspects
    private ResourceFields resourceFields;
    private PropertyFields propertyFields;
    private AclFields aclFields;
    

    // Fast lookup maps for flat list of resource type prop defs and
    // stored field-name to prop-def map
    private final Map<String, PropertyTypeDefinition> fieldNamePropDefMap 
                                  = new HashMap<String, PropertyTypeDefinition>();

    @Override
    public void afterPropertiesSet() {
        aclFields = new AclFields(locale, principalFactory);
        propertyFields = new PropertyFields(locale, valueFactory);
        resourceFields = new ResourceFields(locale);
        populateTypeInfoCacheMaps(fieldNamePropDefMap, resourceTypeTree.getRoot());
    }

    /**
     * Access {@link AclFields} instance.
     * @return the <code>ResourceFields</code> instance
     */
    public AclFields getAclFields() {
        return aclFields;
    }
    
    /**
     * Access {@link PropertyFields} instance.
     * @return the <code>ResourceFields</code> instance
     */
    public PropertyFields getPropertyFields() {
        return propertyFields;
    }
    
    /**
     * Access {@link ResourceFields} instance.
     * @return the <code>ResourceFields> instance.
     */
    public ResourceFields getResourceFields() {
        return resourceFields;
    }

    private void populateTypeInfoCacheMaps(Map<String, PropertyTypeDefinition> fieldPropDefMap, 
            PrimaryResourceTypeDefinition rtDef) {

        // Prop-defs from mixins are included here.
        List<PropertyTypeDefinition> propDefs
                = this.resourceTypeTree.getPropertyTypeDefinitionsIncludingAncestors(rtDef);

        for (PropertyTypeDefinition propDef : propDefs) {
            String fieldName = PropertyFields.propertyFieldName(propDef);
            if (!fieldPropDefMap.containsKey(fieldName)) {
                fieldPropDefMap.put(fieldName, propDef);
            }
        }

        for (PrimaryResourceTypeDefinition child : this.resourceTypeTree.getResourceTypeDefinitionChildren(rtDef)) {
            populateTypeInfoCacheMaps(fieldPropDefMap, child);
        }
    }

    /**
     * Map <code>PropertySetImpl</code> to Lucene <code>Document</code>. Used
     * at indexing time.
     * 
     * @param propSet the property set to create an index document of.
     * @param acl ACL of property set
     * 
     * @return a <code>Document</code> with index fields corresponding to the
     * property set metadata and properties.
     */
    public Document getDocument(PropertySetImpl propSet, Acl acl)
            throws DocumentMappingException {
        
        final Document doc = new Document();
        final List<IndexableField> fields = doc.getFields();

        // Resource meta fields
        resourceFields.addResourceFields(fields, propSet);
        
        // ACL fields
        aclFields.addAclFields(fields, propSet, acl);
        
        final ResourceTypeDefinition rtDef = 
                this.resourceTypeTree.getResourceTypeDefinitionByName(propSet.getResourceType());
        if (rtDef == null) {
            logger.warn("Missing type information for resource type '" + propSet.getResourceType()
                    + "', cannot create complete index document.");
            return doc;
        }

        // Property fields
        List<PropertyTypeDefinition> rtPropDefs =
                this.resourceTypeTree.getPropertyTypeDefinitionsIncludingAncestors(rtDef);
        for (Property property: propSet) {
            // Completely ignore any Property without a definition
            if (property.getDefinition() == null) continue;
            
            // Resolve canonical prop-def instance
            String propFieldName = PropertyFields.propertyFieldName(property.getDefinition());
            PropertyTypeDefinition canonicalDef = this.fieldNamePropDefMap.get(propFieldName);

            // Skip all props not part of type and that are not inheritable
            if (!rtPropDefs.contains(canonicalDef) && !property.getDefinition().isInheritable()) {
                continue;
            }

            // Create indexed fields
            switch (property.getType()) {
            case BINARY:
                break; // Don't store any binary property value types

            case JSON:
                // Add any indexable JSON value attributes (both as lowercase
                // and regular), sorting field(s) and stored field(s)
                propertyFields.addJsonPropertyFields(fields, property);
                break;

            case STRING:
                if (!canonicalDef.isMultiple()) {
                    propertyFields.addSortField(fields, property);
                }
            
            case HTML:
                // Add lowercase version of search field for STRING and HTML
                // types
                propertyFields.addPropertyFields(fields, property, true);

            default:
                // Create searchable and stored index fields of value(s)
                propertyFields.addPropertyFields(fields, property, false);
            }
        }

        return doc;
    }

    /**
     * Obtain a {@link StoredFieldVisitor} from a {@link PropertySelect}. Can
     * be used for selective field loading.
     * 
     * @param select a <code>PropertySelect</code> specifying the desired properties
     * to load from index documents.
     * 
     * @return a <code>StoredFieldVisitor</code> to be used when loading documents
     * from Lucene.
     */
    public DocumentStoredFieldVisitor newStoredFieldVisitor(final PropertySelect select) {
        if (select == PropertySelect.ALL || select == null) {
            return new DocumentStoredFieldVisitor();
        } else if (select == PropertySelect.NONE) {
            return new DocumentStoredFieldVisitor() {
                boolean haveUri = false, haveResourceType = false;
                @Override
                public StoredFieldVisitor.Status needsField(FieldInfo fieldInfo) throws IOException {
                    if (ResourceFields.URI_FIELD_NAME.equals(fieldInfo.name)) {
                        haveUri = true;
                        return StoredFieldVisitor.Status.YES;
                    }
                    if (ResourceFields.RESOURCETYPE_FIELD_NAME.equals(fieldInfo.name)) {
                        haveResourceType = true;
                        return StoredFieldVisitor.Status.YES;
                    }
                    
                    if (haveResourceType && haveUri) {
                        return StoredFieldVisitor.Status.STOP;
                    }
                    
                    return StoredFieldVisitor.Status.NO;
                }
            };
        } else {
            return new DocumentStoredFieldVisitor() {
                @Override
                public StoredFieldVisitor.Status needsField(FieldInfo fieldInfo) throws IOException {
                    
                    if (PropertyFields.isPropertyField(fieldInfo.name)) {
                        PropertyTypeDefinition def = fieldNamePropDefMap.get(fieldInfo.name);
                        if (def != null) {
                            if (select.isIncludedProperty(def)) {
                                return StoredFieldVisitor.Status.YES;
                            } else {
                                return StoredFieldVisitor.Status.NO;
                            }
                        }
                    }
                    
                    if (AclFields.isAclField(fieldInfo.name)) {
                        if (select.isIncludeAcl()) {
                            return StoredFieldVisitor.Status.YES;
                        } else {
                            return StoredFieldVisitor.Status.NO;
                        }
                    }
                    
                    // Check for required reserved fields
                    if (ResourceFields.URI_FIELD_NAME.equals(fieldInfo.name)
                            || ResourceFields.RESOURCETYPE_FIELD_NAME.equals(fieldInfo.name)) {
                        return StoredFieldVisitor.Status.YES;
                    }
                    
                    // Skip others
                    return StoredFieldVisitor.Status.NO;
                }
            };
        }
    }
    
    /**
     * Map from Lucene <code>Document</code> instance to a repository
     * <code>PropertySetImpl</code> instance.
     * 
     * @param doc The {@link org.apache.lucene.document.Document} to map from.
     * Only loaded fields will be mapped to available properties.
     * 
     * @return an instance of {@link LazyMappedPropertySet} containing properties for all
     * loaded property fields present in the document. This can be used together
     * with {@link ResultSetWithAcls} for making ACLs available.
     * 
     * @throws DocumentMappingException in case of errors mapping from document.
     */
    @SuppressWarnings("unchecked")
    public LazyMappedPropertySet getPropertySet(Document doc) throws DocumentMappingException {
        return new LazyMappedPropertySet(doc, this);
    }
    
    Property propertyFromFields(String fieldName, List<IndexableField> fields) throws DocumentMappingException {
        PropertyTypeDefinition def = fieldNamePropDefMap.get(fieldName);
        if (def == null) {
            String name = PropertyFields.propertyName(fieldName);
            String nsPrefix = PropertyFields.propertyNamespace(fieldName);
            def = resourceTypeTree.getPropertyTypeDefinition(Namespace.getNamespaceFromPrefix(nsPrefix), name);
            logger.warn("Definition for property '" + nsPrefix + PropertyFields.NAMESPACEPREFIX_NAME_SEPARATOR + name + "' not found by " + " property manager.");
        }
        
        return propertyFields.fromFields(def, fields);
    }
    
    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }
    
    @Required
    public void setPrincipalFactory(PrincipalFactory pf) {
        this.principalFactory = pf;
    }

     /**
     * Set locale to use for locale-specific sorting and lowercasing of
     * string values as index terms. When set, specialized sorting fields will
     * be encoded as collation keys at indexing time.
     * 
     * @param locale the locale instance to set.
     * 
     * Default value: <code>Locale.getDefault()</code>.
     */
    public void setLocale(Locale locale) {
        this.locale = locale;
    }
 
    @Required
    public void setValueFactory(ValueFactory valueFactory) {
        this.valueFactory = valueFactory;
    }

}
