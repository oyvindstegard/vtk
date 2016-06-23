/* Copyright (c) 2004, University of Oslo, Norway
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
package vtk.repository;

import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import vtk.repository.resourcetype.ConstraintViolationException;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.resourcetype.ValueFormatException;
import vtk.repository.resourcetype.ValueFormatter;
import vtk.repository.resourcetype.ValueSeparator;
import vtk.security.Principal;
import vtk.util.text.Json;


/**
 * This class represents meta information about resources. A resource
 * may have several properties set on it, each of which are identified
 * by a namespace and a name. Properties may contain arbitrary string
 * values, such as XML and JSON. The application programmer is responsible for
 * the interpretation and processing of properties.
 * 
 * XXX: Fail in all getters if value not initialized ?
 */
public class PropertyImpl implements Cloneable, Property {

    private static final long serialVersionUID = 3762531209208410417L;

    private static final Map<PropertyType.Type, Set<PropertyType.Type>> COMPATIBILITY_MAP;
    static {
        COMPATIBILITY_MAP = new EnumMap<Type, Set<Type>>(Type.class);

        Set<Type> STRING = EnumSet.noneOf(Type.class);
        STRING.add(Type.HTML);
        STRING.add(Type.IMAGE_REF);
        STRING.add(Type.JSON);
        COMPATIBILITY_MAP.put(Type.STRING, STRING);
        
        Set<Type> HTML = EnumSet.noneOf(Type.class);
        HTML.add(Type.STRING);
        HTML.add(Type.IMAGE_REF);
        HTML.add(Type.JSON);
        COMPATIBILITY_MAP.put(Type.HTML, HTML);

        Set<Type> IMAGE_REF = EnumSet.noneOf(Type.class);
        IMAGE_REF.add(Type.STRING);
        IMAGE_REF.add(Type.HTML);
        IMAGE_REF.add(Type.JSON);
        COMPATIBILITY_MAP.put(Type.IMAGE_REF, IMAGE_REF);

        Set<Type> JSON = EnumSet.noneOf(Type.class);
        JSON.add(Type.STRING);
        JSON.add(Type.HTML);
        JSON.add(Type.IMAGE_REF);
        COMPATIBILITY_MAP.put(Type.JSON, JSON);

        Set<Type> DATE = EnumSet.noneOf(Type.class);
        DATE.add(Type.TIMESTAMP);
        COMPATIBILITY_MAP.put(Type.DATE, DATE);

        Set<Type> TIMESTAMP = EnumSet.noneOf(Type.class);
        TIMESTAMP.add(Type.DATE);
        COMPATIBILITY_MAP.put(Type.TIMESTAMP, TIMESTAMP);

    }
    
    private PropertyTypeDefinition propertyTypeDefinition;
    private Value value;
    private Value[] values;
    private boolean inherited = false;

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PropertyImpl other = (PropertyImpl) obj;
        if (this.propertyTypeDefinition != other.propertyTypeDefinition && (this.propertyTypeDefinition == null || !this.propertyTypeDefinition.equals(other.propertyTypeDefinition))) {
            return false;
        }
        if (this.value != other.value && (this.value == null || !this.value.equals(other.value))) {
            return false;
        }
        if (!Arrays.deepEquals(this.values, other.values)) {
            return false;
        }
        if (this.inherited != other.inherited) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 13 * hash + (this.propertyTypeDefinition != null ? this.propertyTypeDefinition.hashCode() : 0);
        hash = 13 * hash + (this.value != null ? this.value.hashCode() : 0);
        hash = 13 * hash + Arrays.deepHashCode(this.values);
        hash = 13 * hash + (this.inherited ? 1 : 0);
        return hash;
    }
    
    @Override
    public Value getValue() {
        if (this.propertyTypeDefinition.isMultiple()) {
            throw new IllegalOperationException("Property " + this + " is multi-value"); 
        }
        
        return this.value;
    }

    @Override
    public void setValue(Value value) throws ValueFormatException {
        setValue(value, true);
    }
    
    @Override
    public void setValues(Value[] values) throws ValueFormatException {
        setValues(values, true);
    }

    /**
     * Internal API: allow setting values without strict validation. Used when
     * loading properties from DAO layer.
     * 
     * @param value
     * @param strictValidation if <code>true</code>, then various format/type specific
     * validations, such and size, will be performed.
     * @throws ValueFormatException in case of illegal value being set
     */
    public void setValue(Value value, boolean strictValidation) throws ValueFormatException {
        if (this.propertyTypeDefinition.isMultiple()) {
            throw new ValueFormatException("Property " + this + " is multi-value");
        }
        
        validateValue(value, strictValidation);
        this.value = value;
        this.inherited = false;
    }
    
    /**
     * Internal API: allow setting values without strict validation. Used when
     * loading properties from DAO layer.
     * @param value
     * @param strictValidation if <code>true</code>, then various format/type specific
     * validations, such and size, will be performed.
     * @throws ValueFormatException  in case of illegal value being set
     */
    public void setValues(Value[] values, boolean strictValidation) throws ValueFormatException {
        if (! this.propertyTypeDefinition.isMultiple()) {
            throw new ValueFormatException("Property " + this + " is not multi-value");
        }
        
        validateValues(values, strictValidation);
        this.values = values;
        this.inherited = false;
    }
    
    @Override
    public Value[] getValues() {
        if (! this.propertyTypeDefinition.isMultiple()) {
            throw new IllegalOperationException("Property " + this + " is not multi-value");
        }
        
        return this.values;
    }

    @Override
    public Date getDateValue() throws IllegalOperationException {
        if (this.value == null || (getType() != PropertyType.Type.TIMESTAMP
                && getType() != PropertyType.Type.DATE)) {
            throw new IllegalOperationException("Property " + this + " not of type Date");
        }
        
        return this.value.getDateValue();
    }

    @Override
    public void setDateValue(Date dateValue) throws ValueFormatException {
        boolean date = false;
        if (getType() == PropertyType.Type.DATE) {
            date = true;
        }
        Value v = new Value(dateValue, date);
        setValue(v);
    }

    @Override
    public String getStringValue() throws IllegalOperationException {
        if (this.propertyTypeDefinition.isMultiple()) {
            throw new IllegalOperationException("Property " + this + " is multi-valued");
        }
        if (this.value == null) {
            throw new IllegalOperationException("Property " + this + " has a null value");
        }
        
        switch (getType()) {
        case STRING:
        case HTML:
        case IMAGE_REF:
        case JSON:
            return this.value.getStringValue();
        case BOOLEAN:
            return String.valueOf(this.value.getBooleanValue());
         
        default:
            // XXX this is inconsistent with the handling of BOOLEAN type above:
            throw new IllegalOperationException("Property " + this + " not a string type");
        }
    }
    
    @Override
    public void setStringValue(String stringValue) throws ValueFormatException {
        Value v = new Value(stringValue, PropertyType.Type.STRING);
        setValue(v);
    }
    
    @Override
    public void setLongValue(long longValue) throws ValueFormatException {
        Value v = new Value(longValue);
        setValue(v);
    }

    @Override
    public long getLongValue() throws IllegalOperationException {
        if (this.value == null || getType() != PropertyType.Type.LONG) {
            throw new IllegalOperationException("Property " + this + " not of type Long");
        }
        return this.value.getLongValue();
    }

    @Override
    public void setIntValue(int intValue) throws ValueFormatException {
        Value v = new Value(intValue);
        setValue(v);
    }

    @Override
    public int getIntValue() throws IllegalOperationException {
        if (this.value == null || getType() != PropertyType.Type.INT) {
            throw new IllegalOperationException("Property " + this + " not of type Integer");
        }
        return this.value.getIntValue();
    }
        
    @Override
    public boolean getBooleanValue() throws IllegalOperationException {
        if (this.value == null || getType() != PropertyType.Type.BOOLEAN) {
            throw new IllegalOperationException("Property " + this + " not of type Boolean");
        }
        return this.value.getBooleanValue();
    }

    @Override
    public void setBooleanValue(boolean booleanValue) throws ValueFormatException {
        Value v = new Value(booleanValue);
        setValue(v);
    }
    
    @Override
    public Json.MapContainer getJSONValue() throws IllegalOperationException {
        if (this.value == null || getType() != PropertyType.Type.JSON) {
            throw new IllegalOperationException("Property " + this + " not of type JSON");
        }
        return this.value.getJSONValue();
    }
    
    @Override
    public void setJSONValue(Json.MapContainer jsonObject) {
        setValue(new Value(jsonObject));
    }

    @Override
    public Principal getPrincipalValue() throws IllegalOperationException {
        if (this.value == null || getType() != PropertyType.Type.PRINCIPAL) {
            throw new IllegalOperationException("Property " + this + " not of type Principal");
        }
        return this.value.getPrincipalValue();
    }
    
    @Override
    public Type getType() {
        return this.propertyTypeDefinition.getType();
    }
    
    @Override
    public PropertyTypeDefinition getDefinition() {
        return this.propertyTypeDefinition;
    }
    
    @Override
    public void setPrincipalValue(Principal principalValue) throws ValueFormatException {
        Value v = new Value(principalValue);
        setValue(v);
    }
    
    @Override
    public Object clone() {
        PropertyImpl clone = new PropertyImpl();
        
        // "Dumb" clone, avoid all type checks, just copy data structures
        clone.propertyTypeDefinition = this.propertyTypeDefinition;
        
        // Values
        if (this.value != null) 
            clone.value = (Value)this.value.clone();

        if (this.values != null) {
            clone.values = new Value[this.values.length];
            // Need to deep-copy array of values
            for (int i=0; i<this.values.length; i++) {
                clone.values[i] = (Value)this.values[i].clone();
            }
        }
        clone.inherited = this.inherited;
        
        return clone;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(this.getClass().getName()).append(": ");
        sb.append("[ ").append(this.getDefinition().getNamespace());
        sb.append(":").append(this.getDefinition().getName());
        if (this.propertyTypeDefinition.isMultiple()) {
            sb.append(" = {");
            for (int i=0; values != null && i<this.values.length; i++) {
                sb.append("'").append(this.values[i]).append("'");
                if (i < this.values.length-1) 
                    sb.append(",");
            }
            sb.append("}");
        } else {
            sb.append(" = '").append(this.value).append("'");
        }
        
        sb.append("]");

        return sb.toString();
    }

    private void validateValues(Value[] values, boolean strictValidation) throws ValueFormatException,
                                                ConstraintViolationException {
        if (values == null || values.length == 0) 
            throw new ValueFormatException("A property must have non null value");
        
        for (Value v: values) {
            validateValue(v, strictValidation);
        }
    }

    
    private void validateValue(Value value, boolean strictValidation) throws ValueFormatException,
                                                ConstraintViolationException {
        if (value == null) {
            throw new ValueFormatException("A property cannot have a null value");
        }
        
        if (value.getType() != getType()) {
            Set<Type> compatible = COMPATIBILITY_MAP.get(getType());
            if (compatible == null || !compatible.contains(value.getType())) {
                throw new ValueFormatException("Illegal value type " + 
                        value.getType() + 
                        " for value [" + value + "] on property " + this 
                        + ". Should be " +  getType());
            }
        }

        // Check for potential null values and size limitations
        switch (value.getType()) {
        case PRINCIPAL:
            if (value.getPrincipalValue() == null) {
                throw new ValueFormatException(
                        "Principal value of property '" + this + "' cannot be null");
            }
            String qualifiedName = value.getPrincipalValue().getQualifiedName();
            if (strictValidation && qualifiedName.length() > MAX_STRING_LENGTH) {
                throw new ValueFormatException("Princpal name too long: " + qualifiedName.length() + " (max size = "
                        + MAX_STRING_LENGTH + ")");
            }
            break;
        case STRING:
        case IMAGE_REF:
        case HTML:
        case JSON:
            if (value.getStringValue() == null) {
                throw new ValueFormatException(
                        "String value of property '" + this + "' cannot be null");
            }
            // If this property is of type JSON, then any compatible value type
            // over the normal maximum string length is allowed.
            if (getType() != Type.JSON
                    && strictValidation && value.getStringValue().length() > MAX_STRING_LENGTH) {
                throw new ValueFormatException("String value too large for " + getType() + ": " +
                        + value.getStringValue().length() + " (max size = " + MAX_STRING_LENGTH + ")");
            }
            
            break;
        case DATE:
        case TIMESTAMP:
            if (value.getDateValue() == null) {
                throw new ValueFormatException(
                        "Date value of property '" + this + "' cannot be null");
            }
        }
        
        if (strictValidation && getType() == Type.JSON) {
            try {
                Json.parse(value.getStringValue());
            } catch (Exception e) {
                throw new ValueFormatException(
                        "Value of property '" + this + "': invalid JSON object: " 
                        + value.getStringValue());
            }
        }
        
        Vocabulary<Value> vocabulary = this.propertyTypeDefinition.getVocabulary();
        if (strictValidation && vocabulary != null && vocabulary.getAllowedValues() != null) {
            List<Value> valuesList = Arrays.asList(vocabulary.getAllowedValues());
            if (!valuesList.contains(value)) {
                ConstraintViolationException e = 
                    new ConstraintViolationException(
                        "Value '" + value + "' not in list of allowed values for property " + this 
                        + ", definition: " + propertyTypeDefinition);
                e.setStatusCode(ConstraintViolationException.NOT_IN_ALLOWED_VALUES);
                throw e;
            }
        }
    }
    

    public void setDefinition(PropertyTypeDefinition propertyTypeDefinition) {
        this.propertyTypeDefinition = propertyTypeDefinition;
    }
    
    @Override
    public boolean isValueInitialized() {
        if (this.propertyTypeDefinition.isMultiple()) {
            if (this.values == null) return false;
            for (Value v : this.values) {
                if (v == null) return false;
            }
            return true;
        }
        return this.value != null;
    }

    @Override
    public String getFormattedValue() {
        return getFormattedValue(null, null);
    }
    
    @Override
    public String getFormattedValue(String format, Locale locale) {

        if (!this.propertyTypeDefinition.isMultiple()) {
            return this.propertyTypeDefinition.getValueFormatter().valueToString(this.value, format, locale);
        }

        ValueFormatter formatter = this.propertyTypeDefinition.getValueFormatter();
        ValueSeparator separator = this.propertyTypeDefinition.getValueSeparator(format);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.values.length; i++) {
            Value value = this.values[i];
            sb.append(formatter.valueToString(value, format, locale));
            if (i < this.values.length - 2) {
                sb.append(separator.getIntermediateSeparator(value, locale));
            } else if (i == this.values.length - 2) {
                sb.append(separator.getFinalSeparator(value, locale));
            }
        }
        return sb.toString();
    }
    
    @Override
    public void setBinaryValue(byte[] buffer, String contentType) {
        if (getType() == Type.JSON) {
            if (!"application/json".equals(contentType)) {
                throw new IllegalArgumentException("JSON property requires application/json content type.");
            }
        } else if (getType() != PropertyType.Type.BINARY) {
            throw new IllegalArgumentException("Property " + this + " not of type BINARY or JSON");
        }
        setValue(new Value(buffer, contentType, getType()));
    }

    @Override
    public ContentStream getBinaryStream() throws IllegalOperationException {
        if (this.value == null || (getType() != Type.BINARY && getType() != Type.JSON)) {
            throw new IllegalOperationException("Property " + this + " not of type BINARY or JSON, or property is multi-value");
        }
        return this.value.getBinaryValue().getContentStream();
    }

    @Override
    public String getBinaryContentType() throws IllegalOperationException {
        if (this.value == null || (getType() != Type.BINARY && getType() != Type.JSON)) {
            throw new IllegalOperationException("Property " + this + " not of type BINARY or JSON, or property is multi-value");
        }
        if (getType() == Type.JSON) {
            return "application/json";
        }
        return this.value.getBinaryValue().getContentType();
    }
    
    @Override
    public boolean isInherited() {
        return this.inherited;
    }
    
    public void setInherited(boolean inherited) {
        this.inherited = inherited;
    }

}
