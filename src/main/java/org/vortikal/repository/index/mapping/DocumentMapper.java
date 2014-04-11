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
package org.vortikal.repository.index.mapping;

import static org.vortikal.repository.resourcetype.PropertyType.Type.BINARY;
import static org.vortikal.repository.resourcetype.PropertyType.Type.JSON;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.FieldSelectorResult;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.IndexReader;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Path;
import org.vortikal.repository.Property;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.PropertySetImpl;
import org.vortikal.repository.ResourceTypeTree;
import org.vortikal.repository.resourcetype.PrimaryResourceTypeDefinition;
import org.vortikal.repository.resourcetype.PropertyType.Type;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.ResourceTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.repository.search.PropertySelect;
import org.vortikal.security.Principal;
import org.vortikal.security.PrincipalFactory;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Simple mapping from Lucene {@link org.apache.lucene.document.Document} to
 * {@link org.vortikal.repository.PropertySet} objects and vice-versa.
 * 
 */
public class DocumentMapper implements InitializingBean {

    private final Log logger = LogFactory.getLog(DocumentMapper.class);

    private ResourceTypeTree resourceTypeTree;
    private FieldValueMapper fieldValueMapper;

    // Fast lookup maps for flat list of resource type prop defs and
    // stored field-name to prop-def map
    private Map<String, PropertyTypeDefinition> storedFieldNamePropDefMap 
                                  = new HashMap<String, PropertyTypeDefinition>();

    @Override
    public void afterPropertiesSet() {
        populateTypeInfoCacheMaps(this.storedFieldNamePropDefMap, this.resourceTypeTree.getRoot());
    }

    private void populateTypeInfoCacheMaps(Map<String, PropertyTypeDefinition> storedFieldPropDefMap, 
            PrimaryResourceTypeDefinition rtDef) {

        // Prop-defs from mixins are included here.
        List<PropertyTypeDefinition> propDefs
                = this.resourceTypeTree.getPropertyTypeDefinitionsIncludingAncestors(rtDef);

        for (PropertyTypeDefinition propDef : propDefs) {
            String fieldName = FieldNames.getStoredFieldName(propDef);
            if (!storedFieldPropDefMap.containsKey(fieldName)) {
                storedFieldPropDefMap.put(fieldName, propDef);
            }
        }

        for (PrimaryResourceTypeDefinition child : this.resourceTypeTree.getResourceTypeDefinitionChildren(rtDef)) {
            populateTypeInfoCacheMaps(storedFieldPropDefMap, child);
        }
    }

    /**
     * Map <code>PropertySetImpl</code> to Lucene <code>Document</code>. Used
     * when indexing.
     */
    public Document getDocument(PropertySetImpl propSet, Set<Principal> aclReadPrincipals)
            throws DocumentMappingException {
        Document doc = new Document();

        // Special fields
        // uri
        Fieldable uriField = this.fieldValueMapper.getStoredKeywordField(
                FieldNames.URI_FIELD_NAME, propSet.getURI().toString());
        doc.add(uriField);

        // uriDepth (not stored, but indexed for use in searches)
        int uriDepth = propSet.getURI().getDepth();
        Fieldable uriDepthField = this.fieldValueMapper.getKeywordField(
                FieldNames.URI_DEPTH_FIELD_NAME, uriDepth);
        doc.add(uriDepthField);
        
        // Ancestor URIs (system field used for hierarchical queries)
        Fieldable ancestorPaths = this.fieldValueMapper.getUnencodedMultiValueFieldFromStrings(
                                          FieldNames.URI_ANCESTORS_FIELD_NAME, 
                                          getPathAncestorStrings(propSet.getURI()));
        doc.add(ancestorPaths);

        // name
        Fieldable nameField = this.fieldValueMapper.getKeywordField(
                FieldNames.NAME_FIELD_NAME, propSet.getName());
        doc.add(nameField);
        // name (lowercased, indexed, but not stored)
        Fieldable nameFieldLc = this.fieldValueMapper.getKeywordField(
                FieldNames.NAME_LC_FIELD_NAME, propSet.getName().toLowerCase());
        doc.add(nameFieldLc);

        // resourceType
        Fieldable resourceTypeField = this.fieldValueMapper.getStoredKeywordField(
                FieldNames.RESOURCETYPE_FIELD_NAME, propSet.getResourceType());
        doc.add(resourceTypeField);

        // ID (index system field)
        Fieldable idField = this.fieldValueMapper.getKeywordField(FieldNames.ID_FIELD_NAME, propSet.getID());
        doc.add(idField);
        Field storedIdField = this.fieldValueMapper.getStoredBinaryIntegerField(FieldNames.STORED_ID_FIELD_NAME,
                propSet.getID());
        doc.add(storedIdField);

        // ACL_INHERITED_FROM (index system field)
        Fieldable aclField = this.fieldValueMapper.getKeywordField(FieldNames.ACL_INHERITED_FROM_FIELD_NAME, propSet
                .getAclInheritedFrom());
        doc.add(aclField);
        Field storedAclField = this.fieldValueMapper.getStoredBinaryIntegerField(
                FieldNames.STORED_ACL_INHERITED_FROM_FIELD_NAME, propSet.getAclInheritedFrom());
        doc.add(storedAclField);

        // ACL_READ_PRINCIPALS (index system field)
        if (aclReadPrincipals != null) {
            // XXX: should probably fail harder if missing ACL read field data,
            // however, if missing
            // the document will never be allowed in query results (so really
            // not a problem).
            String[] qualifiedNames = getAclReadPrincipalsFieldValues(aclReadPrincipals);

            Fieldable aclReadPrincipalsField = getAclReadPrincipalsField(qualifiedNames);
            doc.add(aclReadPrincipalsField);

            Fieldable[] storedAclReadPrincipalsFields = getStoredAclReadPrincipalsFields(qualifiedNames);
            for (Fieldable f : storedAclReadPrincipalsFields) {
                doc.add(f);
            }
        }

        final ResourceTypeDefinition rtDef = 
                this.resourceTypeTree.getResourceTypeDefinitionByName(propSet.getResourceType());
        if (rtDef == null) {
            logger.warn("Missing type information for resource type '" + propSet.getResourceType()
                    + "', cannot create complete index document.");
            return doc;
        }
        
        List<PropertyTypeDefinition> rtPropDefs =
                this.resourceTypeTree.getPropertyTypeDefinitionsIncludingAncestors(rtDef);
        for (Property property: propSet) {
            // Completely ignore any Property without a definition
            if (property.getDefinition() == null) continue;
            
            // Resolve canonical prop-def instance
            String storedFieldName = FieldNames.getStoredFieldName(property.getDefinition());
            PropertyTypeDefinition canonicalDef = this.storedFieldNamePropDefMap.get(storedFieldName);

            // Skip all props not part of type and that are not inheritable
            if (!rtPropDefs.contains(canonicalDef) && !property.getDefinition().isInheritable()) {
                continue;
            }

            // Create indexed fields
            switch (property.getType()) {
            case BINARY:
            case JSON_BINARY:
                break; // Don't index any binary property value types

            case JSON:
                // Add any indexable JSON value attributes (both as lowercase
                // and regular)
                for (Fieldable jsonAttrField : getIndexedFieldsFromJSONProperty(property)) {
                    doc.add(jsonAttrField);
                }
                break;

            case STRING:
            case HTML:
                // Add lowercase version of search field for STRING and HTML
                // types
                Fieldable lowercaseIndexedField = getIndexedFieldFromProperty(property, true);
                doc.add(lowercaseIndexedField);

            default:
                // Create regular searchable index field of value(s)
                Fieldable indexedField = getIndexedFieldFromProperty(property, false);
                doc.add(indexedField);
            }

            // Create stored field-value(s) for all types except raw BINARY
            if (property.getType() != BINARY) {
                for (Fieldable storedField : getStoredFieldsFromProperty(property)) {
                    doc.add(storedField);
                }
            }
        }
        
//        // Index only properties that satisfy the following conditions:
//        // 1) Belongs to the resource type's definition
//        // 2) Exists in the property set.
//        // 3) Is of type that is sensible to index for searching.
//        // Store *all* properties, except those of type BINARY.
//        for (PropertyTypeDefinition propDef : this.resourceTypeTree.getPropertyTypeDefinitionsIncludingAncestors(rtDef)) {
//            Property property = propSet.getProperty(propDef);
//
//            if (property == null)
//                continue;
//
//            // Create indexed fields
//            switch (property.getType()) {
//            case BINARY:
//                continue; // Don't index or store BINARY property values
//
//            case JSON:
//                // Add any indexable JSON value attributes (both as lowercase
//                // and regular)
//                for (Fieldable jsonAttrField : getIndexedFieldsFromJSONProperty(property)) {
//                    doc.add(jsonAttrField);
//                }
//                break;
//
//            case STRING:
//            case HTML:
//                // Add lowercase version of search field for STRING and HTML
//                // types
//                Fieldable lowercaseIndexedField = getIndexedFieldFromProperty(property, true);
//                doc.add(lowercaseIndexedField);
//
//            default:
//                // Create regular searchable index field of value(s)
//                Fieldable indexedField = getIndexedFieldFromProperty(property, false);
//                doc.add(indexedField);
//            }
//
//            // Create stored field-value(s) for all types except BINARY
//            if (property.getType() != BINARY) {
//                for (Fieldable storedField : getStoredFieldsFromProperty(property)) {
//                    doc.add(storedField);
//                }
//            }
//        }

        return doc;
    }

    /**
     * 
     * @param select
     * @return 
     */
    public FieldSelector getDocumentFieldSelector(final PropertySelect select) {
        if (select == PropertySelect.ALL) {
            return new FieldSelector() {
                private static final long serialVersionUID = 1L;
                @Override
                public FieldSelectorResult accept(String fieldName) {
                    return FieldSelectorResult.LOAD;
                }
            };
        } else if (select == PropertySelect.NONE) {
            return new FieldSelector() {
                private static final long serialVersionUID = 1L;
                @Override
                public FieldSelectorResult accept(String fieldName) {
                    if (FieldNames.URI_FIELD_NAME.equals(fieldName)) {
                        return FieldSelectorResult.LOAD;
                    }
                    if (FieldNames.RESOURCETYPE_FIELD_NAME.equals(fieldName)) {
                        return FieldSelectorResult.LOAD;
                    }
                    
                    return FieldSelectorResult.NO_LOAD;
                }
            };
        } else {
            return new FieldSelector() {
                private static final long serialVersionUID = 1L;
                @Override
                public FieldSelectorResult accept(String fieldName) {
                    PropertyTypeDefinition def = storedFieldNamePropDefMap.get(fieldName);
                    if (def != null) {
                        if (select.isIncludedProperty(def)) {
                            return FieldSelectorResult.LOAD;
                        } else {
                            return FieldSelectorResult.NO_LOAD;
                        }
                    }
                    
                    // Check for required reserved fields
                    if (FieldNames.URI_FIELD_NAME.equals(fieldName)
                            || FieldNames.RESOURCETYPE_FIELD_NAME.equals(fieldName)) {
                        return FieldSelectorResult.LOAD;
                    }
                    
                    // Skip others
                    return FieldSelectorResult.NO_LOAD;
                }
            };
        }
    }
    
    /**
     * Map from Lucene <code>Document</code> instance to a repository
     * <code>PropertySetImpl</code> instance.
     * 
     * Used when searching.
     * 
     * This method is heavily used when generating query results and is critical
     * for general query performance. Emphasis should be placed on optimizing it
     * as much as possible. Preferably use
     * {@link #loadDocumentWithFieldSelection(IndexReader, int, PropertySelect)}
     * to load the document before passing it to this method. Only loaded fields
     * will be mapped to properties.
     * 
     * @param doc The {@link org.apache.lucene.document.Document} to map from.
     * 
     * @return
     * @throws DocumentMappingException
     */
    @SuppressWarnings("unchecked")
    public PropertySet getPropertySet(Document doc) throws DocumentMappingException {
        return new LazyMappedPropertySet(doc, this);
    }
    
    Property getPropertyFromStoredFieldValues(String fieldName, List<Fieldable> fieldValues)
            throws FieldValueMappingException {

        PropertyTypeDefinition def = this.storedFieldNamePropDefMap.get(fieldName);

        if (def == null) {
            // No definition found, make it a String type and log a warning
            String name = FieldNames.getPropertyNameFromStoredFieldName(fieldName);
            String nsPrefix = FieldNames.getPropertyNamespacePrefixFromStoredFieldName(fieldName);

            def = this.resourceTypeTree.getPropertyTypeDefinition(Namespace.getNamespaceFromPrefix(nsPrefix), name);

            logger.warn("Definition for property '" 
                    + nsPrefix + FieldNames.FIELD_NAMESPACEPREFIX_NAME_SEPARATOR
                    + name + "' not found by " + " property manager. Config might have been updated without "
                    + " updating index(es)");

        }
        
        Property property = def.createProperty();

        if (def.isMultiple()) {
            Value[] values = this.fieldValueMapper.getValuesFromStoredBinaryFields(fieldValues, def.getType());
            
            property.setValues(values);
        } else {
            if (fieldValues.size() != 1) {
                // Fail hard if multiple stored fields found for single
                // value property
                throw new FieldValueMappingException("Single value property '" + def.getNamespace().getPrefix()
                        + FieldNames.FIELD_NAMESPACEPREFIX_NAME_SEPARATOR + def.getName()
                        + "' has an invalid number of stored values (" + fieldValues.size() + ") in index.");
            }

            Value value = this.fieldValueMapper.getValueFromStoredBinaryField(fieldValues.get(0), def.getType());

            property.setValue(value);
        }

        return property;
    }

    /**
     * Creates an indexable <code>Field</code> from a <code>Property</code>.
     * 
     */
    private Fieldable getIndexedFieldFromProperty(Property property, boolean lowercase) throws FieldValueMappingException {

        PropertyTypeDefinition def = property.getDefinition();
        if (def == null) {
            throw new DocumentMappingException("Cannot create indexed field for property with null definition");
        }

        String fieldName = FieldNames.getSearchFieldName(def, lowercase);
        if (FieldNames.isReservedField(fieldName)) {
            throw new DocumentMappingException(
                    "Property type definition has name which collides with reserved index field:" + fieldName);
        }

        Fieldable field;
        if (def.isMultiple()) {
            Value[] values = property.getValues();
            field = this.fieldValueMapper.getFieldFromValues(fieldName, values, lowercase);
        } else {
            field = this.fieldValueMapper.getFieldFromValue(fieldName, property.getValue(), lowercase);
        }

        return field;
    }

    /**
     * Returns all searchable fields for a given JSON property, both lowercased
     * and regular variants.
     * @param prop
     * @return 
     */
    @SuppressWarnings("unchecked")
    private List<Fieldable> getIndexedFieldsFromJSONProperty(Property prop) {

        PropertyTypeDefinition def = prop.getDefinition();
        if (def == null || def.getType() != JSON) {
            throw new DocumentMappingException(
                    "Cannot create indexed JSON fields for property with no definition or non-JSON type");
        }

        Map<String, Object> metadata = def.getMetadata();
        if (! metadata.containsKey(PropertyTypeDefinition.METADATA_INDEXABLE_JSON)) {
            // No indexing hint for this JSON property.
            return Collections.EMPTY_LIST;
        }
        
        final List<Fieldable> fields = new ArrayList<Fieldable>();
        Value[] jsonPropValues = null;
        if (def.isMultiple()) {
            jsonPropValues = prop.getValues();
        } else {
            jsonPropValues = new Value[1];
            jsonPropValues[0] = prop.getValue();
        }
        
        try {
            final List<Object> indexFieldValues = new ArrayList<Object>();
            for (Value jsonValue : jsonPropValues) {
                JSONObject json = jsonValue.getJSONValue();
                for (Iterator it = json.entrySet().iterator(); it.hasNext();) {
                    indexFieldValues.clear();
                    Map.Entry entry = (Map.Entry) it.next();
                    final String jsonAttribute = (String)entry.getKey();
                    final Object value = entry.getValue();
                    if (value == null) continue;
                    
                    // Only index primitives and arrays of primitives, exactly ONE level deep.
                    if (value.getClass() == JSONArray.class) {
                        for (Iterator iter = ((JSONArray) value).iterator(); iter.hasNext();) {
                            Object nextVal = iter.next();
                            if (nextVal != null 
                                    && nextVal.getClass() != JSONObject.class
                                    && nextVal.getClass() != JSONArray.class) {
                                indexFieldValues.add(nextVal);
                            }
                        }
                    } else if (value.getClass() != JSONObject.class) {
                        indexFieldValues.add(value);
                    }
                    // Set up type for searchable JSON field
                    Type dataType = FieldValueMapper.getJsonFieldDataType(def, jsonAttribute);
                    
                    // Create regular searchable field for all values
                    String fieldName = FieldNames.getJsonSearchFieldName(def, jsonAttribute, false);
                    Fieldable field = this.fieldValueMapper.getEncodedMultiValueFieldFromObjects(
                            fieldName, indexFieldValues.toArray(), dataType, false);
                    fields.add(field);
                    
                    // Lowercased searchable field for all values with type hint STRING or HTML
                    if (dataType == Type.STRING || dataType == Type.HTML) {
                        fieldName = FieldNames.getJsonSearchFieldName(def, jsonAttribute, true);
                        field = this.fieldValueMapper.getEncodedMultiValueFieldFromObjects(
                                fieldName, indexFieldValues.toArray(), dataType, true);
                        fields.add(field);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("JSON property " 
                    + prop + " has a value(s) containing invalid or non-indexable JSON data: " 
                    + e.getMessage()
                    + ", will not index.");
            return Collections.EMPTY_LIST;
        }

        return fields;
    }

    /**
     * Creates a stored (binary) <code>Field</code> array from a
     * <code>Property</code>.
     */
    private Fieldable[] getStoredFieldsFromProperty(Property property) throws FieldValueMappingException {

        PropertyTypeDefinition def = property.getDefinition();
        if (def == null) {
            throw new DocumentMappingException("Cannot create stored field for a property with null definition");
        }

        String fieldName = FieldNames.getStoredFieldName(def);

        if (def.isMultiple()) {
            Value[] values = property.getValues();
            return this.fieldValueMapper.getStoredBinaryFieldsFromValues(fieldName, values);
        }
        Fieldable[] singleField = new Fieldable[1];
        singleField[0] = this.fieldValueMapper.getStoredBinaryFieldFromValue(fieldName, property.getValue());
        return singleField;
    }
    
    private String[] getPathAncestorStrings(Path path) {
        List<Path> ancestors = path.getAncestors();
        String[] ancestorStrings = new String[ancestors.size()];
        for (int i=0; i<ancestorStrings.length; i++) {
            ancestorStrings[i] = ancestors.get(i).toString();
        }
        return ancestorStrings;
    }

    public Set<String> getACLReadPrincipalNames(Document doc) throws DocumentMappingException {

        Fieldable[] fields = doc.getFieldables(FieldNames.STORED_ACL_READ_PRINCIPALS_FIELD_NAME);

        if (fields.length == 0) {
            throw new DocumentMappingException("The field " + FieldNames.STORED_ACL_READ_PRINCIPALS_FIELD_NAME
                    + " does not exist or is not loaded for document: " + doc);
        }

        Set<String> retval = new HashSet<String>(
                  Arrays.asList(this.fieldValueMapper.getStringsFromStoredBinaryFields(fields)));

        return retval;
    }
    
    public int getResourceId(Document doc) throws DocumentMappingException {
        Fieldable field = doc.getFieldable(FieldNames.STORED_ID_FIELD_NAME);
        if (field == null) {
            throw new DocumentMappingException("Document is missing field " + FieldNames.STORED_ID_FIELD_NAME);
        }
        return this.fieldValueMapper.getIntegerFromStoredBinaryField(field);
    }
    
    public int getAclInheritedFrom(Document doc) throws DocumentMappingException {
        Fieldable field = doc.getFieldable(FieldNames.STORED_ACL_INHERITED_FROM_FIELD_NAME);
        if (field == null) {
            throw new DocumentMappingException("Document is missing field " + FieldNames.STORED_ACL_INHERITED_FROM_FIELD_NAME);
        }
        return this.fieldValueMapper.getIntegerFromStoredBinaryField(field);
    }

    private String[] getAclReadPrincipalsFieldValues(Set<Principal> aclReadPrincipals) {
        String[] qualifiedNames;

        if (aclReadPrincipals.contains(PrincipalFactory.ALL)) {
            // Read-for-all, only index this field
            qualifiedNames = new String[] { PrincipalFactory.NAME_ALL };
        } else {
            qualifiedNames = new String[aclReadPrincipals.size()];
            int i = 0;
            for (Principal principal : aclReadPrincipals) {
                qualifiedNames[i++] = principal.getQualifiedName();
            }
        }

        return qualifiedNames;
    }

    private Fieldable getAclReadPrincipalsField(String[] qualifiedNames) {
        return this.fieldValueMapper.getUnencodedMultiValueFieldFromStrings(
                FieldNames.ACL_READ_PRINCIPALS_FIELD_NAME, qualifiedNames);
    }

    private Fieldable[] getStoredAclReadPrincipalsFields(String[] qualifiedNames) {
        return this.fieldValueMapper.getStoredBinaryFieldsFromStrings(
                FieldNames.STORED_ACL_READ_PRINCIPALS_FIELD_NAME, qualifiedNames);
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    @Required
    public void setFieldValueMapper(FieldValueMapper fieldValueMapper) {
        this.fieldValueMapper = fieldValueMapper;
    }

}