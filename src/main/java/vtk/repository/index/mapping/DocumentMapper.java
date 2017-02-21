/* Copyright (c) 2006-2017 University of Oslo, Norway
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
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.document.Document;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.PropertySetImpl;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.ResourceTypeDefinition;
import vtk.repository.search.PropertySelect;
import vtk.security.PrincipalFactory;


import org.apache.lucene.document.DocumentStoredFieldVisitor;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.StoredFieldVisitor;
import org.springframework.context.ApplicationListener;
import vtk.repository.Acl;
import vtk.repository.resourcetype.event.TypeConfigurationEvent;


import vtk.repository.resourcetype.ValueFactory;
import vtk.repository.search.ConfigurablePropertySelect;

/**
 * Mapping from Lucene {@link org.apache.lucene.document.Document} to
 * {@link vtk.repository.PropertySet} objects and vice-versa.
 * 
 */
public class DocumentMapper implements InitializingBean, ApplicationListener<TypeConfigurationEvent> {

    private final Logger logger = LoggerFactory.getLogger(DocumentMapper.class);

    private ResourceTypeTree resourceTypeTree;
    private PrincipalFactory principalFactory;
    private ValueFactory valueFactory;
    private Locale locale;
    
    // Fields impls for indexed resource aspects
    private ResourceFields resourceFields;
    private PropertyFields propertyFields;
    private AclFields aclFields;
    
    @Override
    public void afterPropertiesSet() {
        aclFields = new AclFields(locale, principalFactory);
        propertyFields = new PropertyFields(locale, valueFactory);
        resourceFields = new ResourceFields(locale, resourceTypeTree);
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

    // Lazily built lookup map for index property field to property definition
    // Used by query threads for mapping resources from index resoults and must support highly concurrent access
    private final Map<String, PropertyTypeDefinition> propertyFieldToDef = new ConcurrentHashMap<>();
    // Lazily built lookup map for resource type to property selector for property definitions.
    // Typically used only by an indexing thread.
    private final Map<ResourceTypeDefinition, PropertySelect> typeToPropertySelect = new HashMap<>();

    private PropertyTypeDefinition propertyDefinitionFromField(String fieldName) {
        return propertyFieldToDef.computeIfAbsent(fieldName, f -> {
            String prefix = PropertyFields.propertyNamespacePrefix(f);
            String name = PropertyFields.propertyName(f);
            return resourceTypeTree.getPropertyDefinitionByPrefix(prefix, name);
        });
    }

    private boolean propertyBelongsToType(ResourceTypeDefinition def, PropertyTypeDefinition propDef) {
        // Called only by indexing threads, so there will never be much contention here.
        // Still we'd like getDocument to be thread safe, so synchronize.
        PropertySelect pSelect;
        synchronized (this) {
            pSelect = typeToPropertySelect.computeIfAbsent(def, d -> {
                List<PropertyTypeDefinition> typePropDefs = resourceTypeTree.getPropertyTypeDefinitionsIncludingAncestors(d);
                return new ConfigurablePropertySelect(typePropDefs);
            });
        }
        return pSelect.isIncludedProperty(propDef);
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
        
        final ResourceTypeDefinition def =
                this.resourceTypeTree.getResourceTypeDefinitionByName(propSet.getResourceType());
        if (def == null) {
            logger.warn("Missing type information for resource type '" + propSet.getResourceType()
                    + "', cannot create complete index document.");
            return doc;
        }

        // Property fields
        for (Property property: propSet) {
            // Completely ignore any property without a definition
            if (property.getDefinition() == null) continue;

            if (!propertyBelongsToType(def, property.getDefinition()) && !property.isInherited()) {
                // SKip all (dead/zombie) props which are not inherited
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
                if (!property.getDefinition().isMultiple()) {
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
                boolean haveUri = false, haveResourceTypePath = false;
                @Override
                public StoredFieldVisitor.Status needsField(FieldInfo fieldInfo) throws IOException {
                    if (ResourceFields.URI_FIELD_NAME.equals(fieldInfo.name)) {
                        haveUri = true;
                        return StoredFieldVisitor.Status.YES;
                    }
                    if (ResourceFields.RESOURCETYPE_PATH_FIELD_NAME.equals(fieldInfo.name)) {
                        haveResourceTypePath = true;
                        return StoredFieldVisitor.Status.YES;
                    }
                    
                    if (haveResourceTypePath && haveUri) {
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
                        PropertyTypeDefinition def = propertyDefinitionFromField(fieldInfo.name);
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
                            || ResourceFields.RESOURCETYPE_PATH_FIELD_NAME.equals(fieldInfo.name)) {
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
        PropertyTypeDefinition def = propertyDefinitionFromField(fieldName);
        if (def == null) {
            String name = PropertyFields.propertyName(fieldName);
            String nsPrefix = PropertyFields.propertyNamespacePrefix(fieldName);
            def = resourceTypeTree.getPropertyTypeDefinition(Namespace.getNamespaceFromPrefix(nsPrefix), name);
            logger.warn("Definition for property '" + nsPrefix + PropertyFields.NAMESPACEPREFIX_NAME_SEPARATOR + name + "' not found");
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

    @Override
    public void onApplicationEvent(TypeConfigurationEvent event) {
        // Clear all cached state from type configuration, regardless of type config change !
        propertyFieldToDef.clear();
        synchronized(this) {
            typeToPropertySelect.clear();
        }
    }

}
