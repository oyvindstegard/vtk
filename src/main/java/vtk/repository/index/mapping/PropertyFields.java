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

package vtk.repository.index.mapping;

import static vtk.repository.index.mapping.Fields.FieldSpec.INDEXED;
import static vtk.repository.index.mapping.Fields.FieldSpec.INDEXED_WITH_DOCVALUE;
import static vtk.repository.index.mapping.Fields.FieldSpec.INDEXED_LOWERCASE;
import static vtk.repository.index.mapping.Fields.FieldSpec.INDEXED_STORED;
import static vtk.repository.index.mapping.Fields.FieldSpec.INDEXED_STORED_WITH_DOCVALUE;
import static vtk.repository.index.mapping.Fields.FieldSpec.STORED;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;

import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.PropertyImpl;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.resourcetype.ValueFactory;
import vtk.util.text.Json;

/**
 * TODO missing JavaDoc for most methods
 */
public class PropertyFields extends Fields {

    public static final String PROPERTY_FIELD_PREFIX = "p_";
    public static final String NAMESPACEPREFIX_NAME_SEPARATOR = ":";
    public static final String JSON_ATTRIBUTE_SEPARATOR = "@";
    
    private final Logger logger = LoggerFactory.getLogger(PropertyFields.class.getName());
    
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
        fields.add(makeStringSortField(fieldName, property.getStringValue()));
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
        FieldSpec spec = INDEXED_STORED;
        if (lowercase) {
            spec = INDEXED_LOWERCASE;
        } else if (!def.isMultiple() && def.getType() != Type.STRING) {
            spec = INDEXED_STORED_WITH_DOCVALUE;
        }
        if (def.isMultiple()) {
            for (Value v: property.getValues()) {
                fields.addAll(valueFields(fieldName, v, spec));
            }
        } else {
            fields.addAll(valueFields(fieldName, property.getValue(), spec));
        }
    }
    
    // Flatten JSON object to a map of dot-nation field names to list of values.
    // Multiple objects in JSON structure can have individual primitive values
    // mapped to the same index field due to the flattening, but this should
    // at least provide a general solution for searching in different JSON object values.
    private void gatherIndexFieldsFromJson(StringBuilder fldNameBuf, Map<String,List<Object>> fields, Object json) {
        if (json instanceof Json.MapContainer) {
            for (Map.Entry<String, Object> entry : ((Json.MapContainer)json).entrySet()) {
                String key = entry.getKey();
                if (fldNameBuf.length() > 0) {
                    fldNameBuf.append(".");
                }
                fldNameBuf.append(key);
                gatherIndexFieldsFromJson(fldNameBuf, fields, entry.getValue());
                int lastDot = fldNameBuf.lastIndexOf(".");
                if (lastDot > -1) {
                    fldNameBuf.setLength(lastDot);
                } else {
                    fldNameBuf.setLength(0);
                }
            }
        } else if (json instanceof Json.ListContainer) {
            for (Object value: (Json.ListContainer)json) {
                gatherIndexFieldsFromJson(fldNameBuf, fields, value);
            }
        } else {
            // Some primitive value, use current field name and add values
            String field = fldNameBuf.toString();
            List<Object> values = fields.get(field);
            if (values == null) {
                values = new ArrayList<>();
                fields.put(field, values);
            }
            values.add(json);
        }
    }
    
    /**
     * Adds all fields for a given JSON property to field list, both lowercased
     * and regular variants, and a stored field for the raw JSON string value.
     * 
     * <p>
     * Field values encountered in indexable JSON data that are larger than
     * {@link Property#MAX_STRING_LENGTH} will not be added. A warning will be
     * logged in such cases.
     *
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
        
        final Map<String,List<Object>> jsonFields = new HashMap<>();
        for (Value v: jsonPropValues) {
            gatherIndexFieldsFromJson(new StringBuilder(), jsonFields, v.getJSONValue());
        }
        
        try {
            for (Map.Entry<String,List<Object>> entry: jsonFields.entrySet()) {
                final String jsonDottedField = entry.getKey();
                final List<Object> values = entry.getValue();
                if (jsonDottedField.isEmpty()) continue;
                
                String indexFieldName = jsonFieldName(def, jsonDottedField, false);
                Type dataType = PropertyFields.jsonFieldDataType(def, jsonDottedField);
                for (Object indexFieldValue : values) {
                    // Guard against terms that are too large, since we don't really know
                    // what kind of JSON data we are dealing with. Lucene has limits
                    // on token size.
                    if (indexFieldValue instanceof String
                            && indexFieldValue.toString().length() > Property.MAX_STRING_LENGTH) {
                        logger.warn("Skipped indexing of too large field value in JSON property " 
                                + prop + "(@" + jsonDottedField + ")");
                        continue;
                    }
                    
                    // Indexed fields
                    fields.addAll(objectFields(indexFieldName, indexFieldValue, dataType, INDEXED));

                    // Lowercased fields for STRING and HTML
                    if (dataType == Type.STRING || dataType == Type.HTML) {
                        String lcFieldName = jsonFieldName(def, jsonDottedField, true);
                        fields.addAll(objectFields(lcFieldName, indexFieldValue, dataType, INDEXED_LOWERCASE));
                    }
                }
                // Sort field if single value STRING type
                if (dataType == Type.STRING && values.size() == 1) {
                    final String stringValue = values.get(0).toString();
                    if (stringValue.length() <= Property.MAX_STRING_LENGTH) {
                        fields.add(makeStringSortField(jsonSortFieldName(def, jsonDottedField), stringValue));
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("JSON property " + prop 
                    + " has a value(s) containing invalid or non-indexable JSON data: "
                    + e.getMessage());
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
        PropertyImpl property = new PropertyImpl(def);
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
