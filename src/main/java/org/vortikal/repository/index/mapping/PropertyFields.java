/* Copyright (c) 2014, University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Property;
import org.vortikal.repository.PropertyImpl;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.PropertyType.Type;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.repository.resourcetype.ValueFactory;

import static org.vortikal.repository.index.mapping.Fields.FieldSpec.*;

/**
 * TODO missing JavaDoc for most methods
 */
public class PropertyFields extends Fields {

    public static final String PROPERTY_FIELD_PREFIX = "p_";
    public static final String NAMESPACEPREFIX_NAME_SEPARATOR = ":";
    public static final String JSON_ATTRIBUTE_SEPARATOR = "@";
    
    private final Log logger = LogFactory.getLog(PropertyFields.class.getName());
    
    private final ValueFactory valueFactory;

    PropertyFields(Locale locale, ValueFactory vf) {
        super(locale);
        if (vf == null) {
            throw new IllegalArgumentException("vf cannot be null");
        }
        this.valueFactory = vf;
    }

    void addSortField(final List<IndexableField> fields, Property property) {
        if (property.getDefinition().getType() != Type.STRING) {
            throw new IllegalArgumentException("Explicit sorting fields can only be created for STRING properties");
        }
        if (property.getDefinition().isMultiple()) {
            throw new IllegalArgumentException("Sorting fields cannot be created for multi-value properties");
        }
        String fieldName = sortFieldName(property.getDefinition());
        fields.add(makeSortField(fieldName, property.getStringValue()));
    }

    /**
     * Add fields for a <code>Property</code>.
     *
     * @param lowercase if <code>true</code>, then lowercase the value as apporpriate.
     * <em>When lowercased, fields for storing will not be created.</em>
     */
    void addPropertyFields(final List<IndexableField> fields, Property property, boolean lowercase) throws DocumentMappingException {
        PropertyTypeDefinition def = property.getDefinition();
        if (def == null) {
            throw new DocumentMappingException("Cannot create index field for property with null definition");
        }
        String fieldName = propertyFieldName(def, lowercase);
        FieldSpec spec = lowercase ? INDEXED_LOWERCASE : INDEXED_STORED;
        if (def.isMultiple()) {
            for (Value v: property.getValues()) {
                fields.addAll(valueFields(fieldName, v, spec));
            }
        } else {
            fields.addAll(valueFields(fieldName, property.getValue(), spec));
        }
    }
    
    /**
     * Adds all fields for a given JSON property to field list, both lowercased
     * and regular variants, and a stored field for the raw JSON string value.
     * @param prop
     * @param fields list of fields to add to
     * @return
     */
    void addJsonPropertyFields(final List<IndexableField> fields, Property prop) {
        PropertyTypeDefinition def = prop.getDefinition();
        if (def == null || def.getType() != Type.JSON) {
            throw new DocumentMappingException("Cannot create indexed JSON fields for property with no definition or non-JSON type");
        }

        Value[] jsonPropValues;
        if (def.isMultiple()) {
            jsonPropValues = prop.getValues();
        } else {
            jsonPropValues = new Value[1];
            jsonPropValues[0] = prop.getValue();
        }

        // Stored RAW JSON values
        String propertyFieldName = propertyFieldName(def);
        for (Value v: jsonPropValues) {
            fields.addAll(valueFields(propertyFieldName, v, STORED));
        }

        Map<String, Object> metadata = def.getMetadata();
        if (!metadata.containsKey(PropertyTypeDefinition.METADATA_INDEXABLE_JSON)) {
            return;
        }
        
        // Drill one level into JSON for possible extra indexable fields
        try {
            final List<Object> indexFieldValues = new ArrayList<Object>();
            for (Value jsonValue : jsonPropValues) {
                JSONObject json = jsonValue.getJSONValue();
                for (Iterator it = json.entrySet().iterator(); it.hasNext();) {
                    indexFieldValues.clear();
                    Map.Entry entry = (Map.Entry) it.next();
                    final String jsonAttribute = (String) entry.getKey();
                    final Object value = entry.getValue();
                    if (value == null) {
                        continue;
                    }
                    if (value.getClass() == JSONArray.class) {
                        for (Iterator iter = ((JSONArray) value).iterator(); iter.hasNext();) {
                            Object nextVal = iter.next();
                            if (nextVal != null && nextVal.getClass() != JSONObject.class && nextVal.getClass() != JSONArray.class) {
                                indexFieldValues.add(nextVal);
                            }
                        }
                    } else if (value.getClass() != JSONObject.class) {
                        indexFieldValues.add(value);
                    }
                    Type dataType = PropertyFields.jsonFieldDataType(def, jsonAttribute);
                    String fieldName = jsonFieldName(def, jsonAttribute, false);
                    for (Object indexFieldValue: indexFieldValues) {
                        // Indexed fields
                        fields.addAll(objectFields(fieldName, indexFieldValue, dataType, INDEXED));
                        
                        // Lowercased fields for STRING and HTML
                        if (dataType == Type.STRING || dataType == Type.HTML) {
                            String lcFieldName = jsonFieldName(def, jsonAttribute, true);
                            fields.addAll(objectFields(lcFieldName, indexFieldValue, dataType, INDEXED_LOWERCASE));
                        }
                    }
                    
                    // Sort field if single value STRING type
                    if (dataType == Type.STRING && indexFieldValues.size() == 1) {
                        fields.add(makeSortField(jsonSortFieldName(def, jsonAttribute), indexFieldValues.get(0).toString()));
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("JSON property " + prop + " has a value(s) containing invalid or non-indexable JSON data: " + e.getMessage());
        }
    }
    
    /**
     * Create a <code>Property</code> instance from a definition and a list of index
     * fields.
     * 
     * @param def
     * @param fields
     * @return
     * @throws DocumentMappingException 
     */
    Property fromFields(PropertyTypeDefinition def, List<IndexableField> fields) throws DocumentMappingException {
        PropertyImpl property = new PropertyImpl();
        property.setDefinition(def);
        if (def.isMultiple()) {
            Value[] values = valuesFromFields(def.getType(), fields);
            property.setValues(values, false);
        } else {
            if (fields.size() != 1) {
                logger.warn("Single value property '" + def.getNamespace().getPrefix()
                        + NAMESPACEPREFIX_NAME_SEPARATOR + def.getName() 
                        + "' has an invalid number of stored values (" + fields.size() + ") in index");
            }
            Value value = valueFromField(def.getType(), fields.get(0));
            property.setValue(value, false);
        }
        return property;
    }

    /**
     * Create a suitably encoded query term from a value with a given {@link PropertyType.Type type}.
     * 
     * <p>The value object should either be a parseable string or an instance of
     * one of the basic types supported by
     * {@link Fields#queryTerm(java.lang.String, java.lang.Object, java.lang.Class, boolean) Fields.queryTerm}.
     * 
     * @param fieldName
     * @param value
     * @param type
     * @param lowercase
     * @return 
     */
    public Term queryTerm(String fieldName, Object value, PropertyType.Type type, boolean lowercase) {
        switch (type) {
        case BOOLEAN:
            return super.queryTerm(fieldName, value, Boolean.class, lowercase);
            
        case DATE:
        case TIMESTAMP:
            return super.queryTerm(fieldName, value, java.util.Date.class, lowercase);
            
        case LONG:
            return super.queryTerm(fieldName, value, Long.class, lowercase);
            
        case INT:
            return super.queryTerm(fieldName, value, Integer.class, lowercase);
            
        case BINARY:
            throw new UnsupportedOperationException("Cannot make query term from a binary value");
            
        default:
            return super.queryTerm(fieldName, value, String.class, lowercase);
            
        }
    }
    
    private List<IndexableField> valueFields(String fieldName, Value v, FieldSpec spec) {
        switch (v.getType()) {
        case BOOLEAN:
            return makeFields(fieldName, v.getBooleanValue(), spec);
            
        case DATE:
        case TIMESTAMP:
            return makeFields(fieldName, v.getDateValue(), spec);
            
        case LONG:
            return makeFields(fieldName, v.getLongValue(), spec);
            
        case INT:
            return makeFields(fieldName, v.getIntValue(), spec);
            
        case BINARY:
            return makeFields(fieldName, v.getBinaryValue().getBytes());
            
        default:
            return makeFields(fieldName, v.getNativeStringRepresentation(), spec);
        }
    }
    
    private List<IndexableField> objectFields(String fieldName, Object value, Type dataType, FieldSpec spec) {
        switch (dataType) {
        case BOOLEAN:
            boolean bVal;
            if (value.getClass() == Boolean.class) {
                bVal = (Boolean)value;
            } else {
                bVal = "true".equals(value.toString());
            }
            return makeFields(fieldName, bVal, spec);
            
        case DATE:
        case TIMESTAMP:
            return makeFields(fieldName, parseDate(value), spec);
            
        case LONG:
            long lVal;
            if (value.getClass() == Long.class) {
                lVal = (Long)value;
            } else {
                lVal = Long.parseLong(value.toString());
            }
            return makeFields(fieldName, lVal, spec);
            
        case INT:
            int iVal;
            if (value.getClass() == Integer.class) {
                iVal = (Integer)value;
            } else {
                iVal = Integer.parseInt(value.toString());
            }
            return makeFields(fieldName, iVal, spec);

            
        case BINARY:
            byte[] binVal;
            if (value.getClass() == byte[].class){
                binVal = (byte[])value;
            } else {
                binVal = value.toString().getBytes();
            }
            return makeFields(fieldName, binVal);
            
        default:
            return makeFields(fieldName, value.toString(), spec);
        }
    }

    private Value[] valuesFromFields(Type type, List<IndexableField> fields) {
        Value[] values = new Value[fields.size()];
        for (int i=0; i<fields.size(); i++) {
            values[i] = valueFromField(type, fields.get(i));
        }
        return values;
    }
    
    // Package private for testing purposes
    Value valueFromField(Type type, IndexableField f) {
        switch (type) {
        case STRING:
        case HTML:
        case JSON:
        case IMAGE_REF:
        case PRINCIPAL:
        case BOOLEAN:
            String stringValue = f.stringValue();
            if (stringValue == null) {
                throw new IllegalArgumentException("Field " + f + " has no stored string value");
            }
            return this.valueFactory.createValue(stringValue, type);

        case DATE:
        case TIMESTAMP:
        case LONG:
            Number longNumber = f.numericValue();
            if (longNumber == null) {
                throw new IllegalArgumentException("Field " + f + " has no stored numeric long value");
            }
            long longValue = longNumber.longValue();
            if (type == Type.LONG) {
                return new Value(longValue);
            } else {
                return new Value(new Date(longValue), type == Type.DATE);
            }
            
        case INT:
            Number intNumber = f.numericValue();
            if (intNumber == null) {
                throw new IllegalArgumentException("Field " + f + " has no stored numeric integer value");
            }
            return new Value(intNumber.intValue());

        default:
            throw new IllegalArgumentException("Unsupported value type: " + type);
        }
    }
    
    
    /**
     * Get suitable data type for a JSON field. Only accepts property
     * defs with value type JSON. Uses JSON field type hints set in property
     * definition metadata.
     * 
     * @param def
     * @param jsonFieldName Name of a JSON field.
     * @return 
     */
    public static Type jsonFieldDataType(PropertyTypeDefinition def, String jsonFieldName) {
        if (def.getType() != PropertyType.Type.JSON) {
            throw new IllegalArgumentException("Type JSON required: " + def);
        }
        Map<String,Object> metadata = def.getMetadata();
        String typeHint = (String) metadata.get(
                PropertyTypeDefinition.METADATA_INDEXABLE_JSON_TYPEHINT_FIELD_PREFIX + jsonFieldName);
        if (typeHint == null) {
            typeHint = (String) metadata.get(
                    PropertyTypeDefinition.METADATA_INDEXABLE_JSON_TYPEHINT_DEFAULT);
        }
        return typeHint != null ? Type.valueOf(typeHint) : Type.STRING;
    }
    
    public static String propertyFieldName(String propName, String nsPrefix, boolean lowercase, boolean sort) {
        if (sort && lowercase) {
            throw new IllegalArgumentException("Field cannot be used both for lowercase and sorting");
        }
        
        StringBuilder fieldName = new StringBuilder(PROPERTY_FIELD_PREFIX);
        if (lowercase) {
            fieldName.append(LOWERCASE_FIELD_PREFIX);
        } else if (sort) {
            fieldName.append(SORT_FIELD_PREFIX);
        }
        
        if (nsPrefix != null) {
            fieldName.append(nsPrefix).append(NAMESPACEPREFIX_NAME_SEPARATOR);
        }
        
        fieldName.append(propName);
        return fieldName.toString();
    }

    public static String jsonFieldName(PropertyTypeDefinition def, String jsonAttrKey, 
                                                                          boolean lowercase) {
        StringBuilder fieldName = new StringBuilder(propertyFieldName(def, lowercase));
        fieldName.append(JSON_ATTRIBUTE_SEPARATOR).append(jsonAttrKey);
        return fieldName.toString();
    }
    
    /**
     * Checks if the stored property field name has the given namespace.
     * 
     * @param fieldName
     * @param ns
     * @return 
     */
    public static boolean isPropertyFieldInNamespace(String fieldName, Namespace ns) {
        if (!isPropertyField(fieldName)) {
            return false;
        }
        
        if (ns.getPrefix() == null) {
            return !fieldName.contains(NAMESPACEPREFIX_NAME_SEPARATOR);
        }
        
        int offset = PROPERTY_FIELD_PREFIX.length();
        if (fieldName.startsWith(LOWERCASE_FIELD_PREFIX, offset)) {
            offset += LOWERCASE_FIELD_PREFIX.length();
        } else if (fieldName.startsWith(SORT_FIELD_PREFIX, offset)) {
            offset += SORT_FIELD_PREFIX.length();
        }
        
        return fieldName.startsWith(ns.getPrefix() + NAMESPACEPREFIX_NAME_SEPARATOR, offset);
    }
    
    public static String propertyNamespace(String fieldName) {
        if (!isPropertyField(fieldName)) {
            throw new IllegalArgumentException("Not a property field name: " + fieldName);
        }
        
        int offset = PROPERTY_FIELD_PREFIX.length();
        if (fieldName.startsWith(LOWERCASE_FIELD_PREFIX, offset)) {
            offset += LOWERCASE_FIELD_PREFIX.length();
        }
        
        int pos = fieldName.indexOf(NAMESPACEPREFIX_NAME_SEPARATOR, offset);

        if (pos == -1) {
            return null;
        } else {
            return fieldName.substring(offset, pos);
        }
    }
    
    public static String propertyName(String fieldName) {
        if (! isPropertyField(fieldName)) {
            throw new IllegalArgumentException("Not a property field name: " + fieldName);
        }
        
        int offset = PROPERTY_FIELD_PREFIX.length();
        if (fieldName.startsWith(LOWERCASE_FIELD_PREFIX, offset)) {
            offset += LOWERCASE_FIELD_PREFIX.length();
        }

        int pos = fieldName.indexOf(NAMESPACEPREFIX_NAME_SEPARATOR, offset);

        if (pos == -1) {
            return fieldName.substring(offset);
        } else {
            return fieldName.substring(pos + 1);
        }
    }

    public static String sortFieldName(PropertyTypeDefinition def) {
        // For STRING data type, use dedicated sorting field
        boolean useDedicatedSortField = def.getType() == PropertyType.Type.STRING;
        return propertyFieldName(def.getName(), def.getNamespace().getPrefix(), false, useDedicatedSortField);
    }
    
    public static String jsonSortFieldName(PropertyTypeDefinition def, String jsonAttrKey) {
        PropertyType.Type dataType = PropertyFields.jsonFieldDataType(def, jsonAttrKey);
        // For STRING data type, use dedicated sorting field
        boolean useDedicatedSortField = dataType == PropertyType.Type.STRING;
        StringBuilder fieldName = new StringBuilder(propertyFieldName(def.getName(), def.getNamespace().getPrefix(), false, useDedicatedSortField));
        fieldName.append(JSON_ATTRIBUTE_SEPARATOR).append(jsonAttrKey);
        return fieldName.toString();
    }
    
    public static String propertyFieldName(PropertyTypeDefinition def, boolean lowercase) {
        switch (def.getType()) {
        case STRING:
        case HTML:
        case JSON:
            break;
        
        default:
            lowercase = false; // No lowercasing-support for type
        }

        return propertyFieldName(def.getName(), def.getNamespace().getPrefix(), lowercase, false);
    }
    
    public static boolean isPropertyField(String fieldName) {
        return fieldName.startsWith(PROPERTY_FIELD_PREFIX);
    }
    
    public static boolean isLowercaseField(String fieldName) {
        int offset = isPropertyField(fieldName) ? PROPERTY_FIELD_PREFIX.length() : 0;
        return fieldName.startsWith(LOWERCASE_FIELD_PREFIX, offset);
    }
    
    public static boolean isSortField(String fieldName) {
        int offset = isPropertyField(fieldName) ? PROPERTY_FIELD_PREFIX.length() : 0;
        return fieldName.startsWith(SORT_FIELD_PREFIX, offset);
    }
    
    public static String propertyFieldName(PropertyTypeDefinition def) {
        return propertyFieldName(def, false);
    }
    
}
