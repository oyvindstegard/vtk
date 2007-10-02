/* Copyright (c) 2006, University of Oslo, Norway
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
package org.vortikal.repositoryimpl.index.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.FieldSelectorResult;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Property;
import org.vortikal.repository.ResourceTypeTree;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.ResourceTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.repository.resourcetype.ValueFactory;
import org.vortikal.repository.search.PropertySelect;
import org.vortikal.repository.search.WildcardPropertySelect;
import org.vortikal.repositoryimpl.PropertyManager;
import org.vortikal.repositoryimpl.PropertySetImpl;
import org.vortikal.repositoryimpl.domain.ContextManager;
import org.vortikal.util.repository.URIUtil;

/**
 * Simple mapping from Lucene {@link org.apache.lucene.document.Document} to
 * {@link org.vortikal.repository.PropertySet} objects and vice-versa.
 * 
 * XXX: more error-checking
 * TODO: Javadoc
 * 
 * @author oyviste
 */
public class DocumentMapperImpl implements DocumentMapper, InitializingBean {

    private static Log logger = LogFactory.getLog(DocumentMapperImpl.class);
    
    /* Fast lookup from stored field name in index -> PropertyTypeDefinition */
    private Map<String, PropertyTypeDefinition> fieldNamePropDefMap; 

    private ResourceTypeTree resourceTypeTree;
    private PropertyManager propertyManager;
    private ValueFactory valueFactory;

    private ContextManager contextManager;
    
    public void afterPropertiesSet() {
        this.resourceTypeTree = this.propertyManager.getResourceTypeTree();
        
        this.fieldNamePropDefMap = new HashMap<String, PropertyTypeDefinition>();
        // Populate map for fast lookup of stored index field name -> PropertyTypeDefinition
        for (PropertyTypeDefinition def: this.resourceTypeTree.getPropertyTypeDefinitions()) {
            this.fieldNamePropDefMap.put(FieldNameMapping.getStoredFieldName(def), def);
        }
    }
    
    public Document getDocument(PropertySetImpl propSet) throws DocumentMappingException {
        Document doc = new Document();
        
        // Special fields
        // uri
        Field uriField = FieldValueMapper.getStoredKeywordField(FieldNameMapping.URI_FIELD_NAME, propSet.getURI());
        doc.add(uriField);
        
        // uriDepth (not stored, but indexed for use in searches)
        int uriDepth = URIUtil.getUriDepth(propSet.getURI());
        Field uriDepthField = FieldValueMapper.getKeywordField(FieldNameMapping.URI_DEPTH_FIELD_NAME, uriDepth);
        doc.add(uriDepthField);
        
        // name
        Field nameField = FieldValueMapper.getStoredKeywordField(FieldNameMapping.NAME_FIELD_NAME, propSet.getName());
        doc.add(nameField);
        
        // resourceType
        Field resourceTypeField =
            FieldValueMapper.getStoredKeywordField(FieldNameMapping.RESOURCETYPE_FIELD_NAME, propSet.getResourceType());
        doc.add(resourceTypeField);
        
        // ANCESTORIDS (index system field)
        Field ancestorIdsField = 
            FieldValueMapper.getUnencodedMultiValueFieldFromIntegers(FieldNameMapping.ANCESTORIDS_FIELD_NAME, 
                                                                    propSet.getAncestorIds());
        doc.add(ancestorIdsField);
        
        // ID (index system field)
        Field idField = FieldValueMapper.getKeywordField(FieldNameMapping.ID_FIELD_NAME, propSet.getID());
        doc.add(idField);
        Field storedIdField = BinaryFieldValueMapper.getStoredBinaryIntegerField(FieldNameMapping.STORED_ID_FIELD_NAME, 
                                                                    propSet.getID());
        doc.add(storedIdField);
        
        // ACL_INHERITED_FROM (index system field)
        Field aclField = BinaryFieldValueMapper.getStoredBinaryIntegerField(
                FieldNameMapping.ACL_INHERITED_FROM_FIELD_NAME, propSet.getAclInheritedFrom());
        doc.add(aclField);
        
        ResourceTypeDefinition resourceDef = 
                this.resourceTypeTree.getResourceTypeDefinitionByName(propSet.getResourceType());
        
        List<PropertyTypeDefinition> propDefs = 
            this.resourceTypeTree.getPropertyTypeDefinitionsForResourceTypeIncludingAncestors(resourceDef);
        
        // Index only properties that satisfy both of the following conditions:
        // 1) Belongs to the resource type's definition
        // 2) Exists in the property set.
        for (PropertyTypeDefinition propDef: propDefs) {
            Property property = propSet.getProperty(propDef);
            
            if (property == null) continue;
           
            // The field used for searching on the property (w/multi-values encoded for proper analysis)
            Field indexedField = getIndexedFieldFromProperty(property);
            doc.add(indexedField);
            
            // The field(s) used for storing the property value(s) (in binary form) 
            Field[] storedFields = getStoredFieldsFromProperty(property);
            for (int u=0; u < storedFields.length; u++) {
               doc.add(storedFields[u]);
            }
        }
        
        return doc;
    }
    
    public FieldSelector getDocumentFieldSelector(final PropertySelect select) {
        
        FieldSelector selector = null;
        if (select == WildcardPropertySelect.WILDCARD_PROPERTY_SELECT) {
            selector = new FieldSelector() {

                public FieldSelectorResult accept(String fieldName) {
                    return FieldSelectorResult.LOAD;
                }
                
            };
        } else {
            selector = new FieldSelector() {

                public FieldSelectorResult accept(String fieldName) {
                    PropertyTypeDefinition def = 
                        DocumentMapperImpl.this.fieldNamePropDefMap.get(fieldName);
                    
                    if (def == null) {
                        // Reserved field
                        return FieldSelectorResult.LOAD;
                    }
                    
                    if (!select.isIncludedProperty(def)
                        && !fieldName.equals(FieldNameMapping.OWNER_PROPERTY_STORED_FIELD_NAME)) {
                            
                            return FieldSelectorResult.NO_LOAD;
                    } 
                    
                    // Default policy is to load otherwise unknown fields
                    return FieldSelectorResult.LOAD;
                }
            };
        }
        
        return selector;
    }
    
    /**
     * Map from Lucene <code>Document</code> instance to a  repository 
     * <code>PropertySet</code> instance. 
     * 
     * This method is heavily used when generating query results and
     * is critical for general query performance. Emphasis should be placed
     * on optimizing it as much as possible. Preferably use 
     * {@link #loadDocumentWithFieldSelection(IndexReader, int, PropertySelect)}
     * to load the document before passing it to this method. Only loaded
     * fields will be mapped to properties.
     * 
     * @param doc The {@link org.apache.lucene.document.Document} to map from
     * @param select A {@link PropertySelect} instance determining which
     *               properties that should be mapped.
     *
     * @return
     * @throws DocumentMappingException
     */
    public PropertySetImpl getPropertySet(Document doc) 
        throws DocumentMappingException {
        
        PropertySetImpl propSet = new PropertySetImpl();
        propSet.setUri(doc.get(FieldNameMapping.URI_FIELD_NAME));
        propSet.setAclInheritedFrom(BinaryFieldValueMapper.getIntegerFromStoredBinaryField(
                doc.getField(FieldNameMapping.ACL_INHERITED_FROM_FIELD_NAME)));
        propSet.setID(BinaryFieldValueMapper.getIntegerFromStoredBinaryField(
                doc.getField(FieldNameMapping.STORED_ID_FIELD_NAME)));
        propSet.setResourceType(doc.get(FieldNameMapping.RESOURCETYPE_FIELD_NAME));
        
        // Loop through all stored binary fields and re-create properties with
        // values. Multi-valued properties are stored as a sequence of binary fields
        // (with the same name) in the index.
        // Note that the iteration will _only_ contain _stored_ fields.
        String currentName = null;
        List<Field> fields = null;
        
        for(Iterator<Field> iterator = doc.getFields().iterator(); iterator.hasNext();) {
            Field field = iterator.next();
            
            // Skip reserved fields
            if (FieldNameMapping.isReservedField(field.name())) {
                currentName = null;
                continue;
            }
            
            if (currentName == null) {
                currentName = field.name();
                fields = new ArrayList<Field>();
            }
            
            // Field.name() returns an internalized String instance. 
            // Optimize by only comparing reference instead of calling 
            // String.equals(Object o). This saves a method call, 
            // and a full string comparison in cases where the references differ.
            if (field.name() == currentName) {
                fields.add(field);
            } else {
                Property prop = getPropertyFromStoredFieldValues(currentName, 
                                                                 fields);
                propSet.addProperty(prop);
                
                fields = new ArrayList<Field>();
                currentName = field.name();
                fields.add(field);
            }
        }
        
        // Make sure we don't forget the last field
        if (currentName != null && fields != null) {
            Property prop = getPropertyFromStoredFieldValues(currentName,
                                                             fields);
            propSet.addProperty(prop);
        }
        
        addContext(propSet);

        return propSet;
    }

    private void addContext(PropertySetImpl propSet) {
        String uri = propSet.getURI();
        Map<PropertyTypeDefinition, String> context = this.contextManager.getContext(uri);

        if (context == null)
            return;
        
        for (PropertyTypeDefinition propDef : context.keySet()) {
            String stringValue = context.get(propDef);
            Property property = this.propertyManager.createProperty(propDef);

            Value value = this.valueFactory.createValue(stringValue, PropertyType.TYPE_STRING);
            property.setValue(value);
            propSet.addProperty(property);
        }
    }
    
    /**
     * Re-create a <code>Property</code> from index fields.
     * @param fields
     * @return
     * @throws FieldValueMappingException
     */
    private Property getPropertyFromStoredFieldValues(String fieldName, 
                                                  List<Field> storedValueFields) 
        throws FieldValueMappingException {
        
        PropertyTypeDefinition def = this.fieldNamePropDefMap.get(fieldName);
        
        Property property = null;
        if (def == null) {
            // No definition found, make it a String type and log a warning
            String name = 
                FieldNameMapping.getPropertyNameFromStoredFieldName(fieldName);
            String nsPrefix = 
                FieldNameMapping.getPropertyNamespacePrefixFromStoredFieldName(fieldName);

            property = this.propertyManager.createProperty(
                                Namespace.getNamespaceFromPrefix(nsPrefix), name);
            
            logger.warn("Definition for property '"
                            + nsPrefix
                            + FieldNameMapping.FIELD_NAMESPACEPREFIX_NAME_SEPARATOR
                            + name
                            + "' not found by "
                            + " property manager. Config might have been updated without "
                            + " updating index(es)");

            Value value = BinaryFieldValueMapper.getValueFromBinaryField(
                    storedValueFields.get(0), this.valueFactory,
                    PropertyType.TYPE_STRING);

            property.setValue(value);
        } else {
            property = this.propertyManager.createProperty(def);
            
            if (def.isMultiple()) { // and indexes haven't been updated to
                                    // reflect this.

                Value[] values = BinaryFieldValueMapper
                        .getValuesFromBinaryFields(storedValueFields,
                                this.valueFactory, def.getType());

                property.setValues(values);
            } else {
                if (storedValueFields.size() != 1) {
                    // Fail hard if multiple stored fields found for single
                    // value property
                    throw new FieldValueMappingException(
                            "Single value property '"
                                    + def.getNamespace().getPrefix()
                                    + FieldNameMapping.FIELD_NAMESPACEPREFIX_NAME_SEPARATOR
                                    + def.getName()
                                    + "' has an invalid number of stored values ("
                                    + storedValueFields.size() + ") in index.");
                }

                Value value = BinaryFieldValueMapper.getValueFromBinaryField(
                        storedValueFields.get(0), this.valueFactory, def.getType());

                property.setValue(value);
            }
        }

        return property;
    }    
    
   /**
     * Creates an indexable <code>Field</code> from a <code>Property</code>.
     *
     */
    private Field getIndexedFieldFromProperty(Property property) 
        throws FieldValueMappingException {
        
        String fieldName = FieldNameMapping.getSearchFieldName(property);

        if (FieldNameMapping.isReservedField(fieldName)) {
            throw new FieldValueMappingException("Property field name '" + fieldName 
                    + "' is a reserved index field.");
        }
        
        PropertyTypeDefinition def = property.getDefinition();
        if (def == null) {
            throw new FieldValueMappingException(
                    "Cannot create indexed field for property with null definition");
        }
        
        Field field = null;
        if (def.isMultiple()) {
            Value[] values = property.getValues();
            field = FieldValueMapper.getFieldFromValues(fieldName, values);
        } else {
            field = FieldValueMapper.getFieldFromValue(fieldName, property.getValue());
        }
        
        return field;
    }

    /**
     * Creates a stored (binary) <code>Field</code> from a <code>Property</code>.
     * 
     */
    private Field[] getStoredFieldsFromProperty(Property property)
        throws FieldValueMappingException {
        
        String fieldName 
            = FieldNameMapping.getStoredFieldName(property);

        if (FieldNameMapping.isReservedField(fieldName)) {
            throw new DocumentMappingException("Property field name '" + fieldName 
                    + "' is a reserved index field.");
        }
        
        PropertyTypeDefinition def = property.getDefinition();
        if (def == null) {
            throw new FieldValueMappingException(
                    "Cannot create stored field for a property with null definition");
        }
        
        if (def.isMultiple()) {
                Value[] values = property.getValues();
                return BinaryFieldValueMapper.getBinaryFieldsFromValues(fieldName, values);
        }
        Field[] singleField = new Field[1];
        singleField[0] = BinaryFieldValueMapper.getBinaryFieldFromValue(fieldName, 
                property.getValue());
        return singleField;
    }

    @Required
    public void setPropertyManager(PropertyManager propertyManager) {
        this.propertyManager = propertyManager;
    }

    @Required
    public void setValueFactory(ValueFactory valueFactory) {
        this.valueFactory = valueFactory;
    }

    @Required
    public void setContextManager(ContextManager contextManager) {
        this.contextManager = contextManager;
    }

}
