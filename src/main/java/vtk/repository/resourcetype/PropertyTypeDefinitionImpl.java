/* Copyright (c) 2006–2017, University of Oslo, Norway
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
package vtk.repository.resourcetype;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.PropertyImpl;
import vtk.repository.RepositoryAction;
import vtk.repository.Vocabulary;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.security.Principal;

/**
 * Implementation of {@link PropertyTypeDefinition}
 * 
 * @see PropertyTypeDefinition
 * 
 */
@SuppressWarnings("deprecation")
public class PropertyTypeDefinitionImpl implements PropertyTypeDefinition, InitializingBean {

    private Map<String, Object> metadata = new HashMap<>();
    private Namespace namespace;
    private String name;
    private Type type = PropertyType.Type.STRING;
    private boolean multiple = false;
    private boolean mandatory = false;
    private boolean inheritable = false;
    private RepositoryAction protectionLevel = PropertyType.PROTECTION_LEVEL_ACL_WRITE;
    private Value defaultValue;
    private Value[] allowedValues;
    private PropertyEvaluator propertyEvaluator;
    private PropertyValidator validator;
    private ValueFormatter valueFormatter;
    private ValueSeparator defaultValueSeparator = new ConfigurableValueSeparator();
    private Map<String, ValueSeparator> valueSeparators = new HashMap<>();
    private Vocabulary<Value> vocabulary;
    private ValueFactory valueFactory;
    private ValueFormatterRegistry valueFormatterRegistry;
    private TypeLocalizationProvider typeLocalizationProvider = null;

    public void setMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata map cannot be null");
        }
        this.metadata = metadata;
    }

    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }

    @Override
    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(this.metadata);
    }

    @Override
    public Property createProperty() {
        return new PropertyImpl(this);
    }

    @Override
    public Property createProperty(Object value) throws ValueFormatException {

        PropertyImpl prop = new PropertyImpl(this);

        if (value instanceof Date) {
            prop.setDateValue((Date)value);
        } else if (value instanceof Boolean) {
            prop.setBooleanValue((Boolean)value);
        } else if (value instanceof Long) {
            prop.setLongValue((Long)value);
        } else if (value instanceof Integer) {
            prop.setIntValue((Integer)value);
        } else if (value instanceof Principal) {
            prop.setPrincipalValue((Principal)value);
        } else if (!(value instanceof String)) {
            throw new ValueFormatException("Supplied value of property [namespaces: " + namespace + ", name: " + name
                    + "] not of any supported type " + "(type was: " + value.getClass() + ")");
        } else {
            prop.setStringValue((String) value);
        }

        return prop;
    }

    @Override
    public Property createProperty(String stringValue) throws ValueFormatException {
        return createProperty(new String[] { stringValue });
    }

    @Override
    public Property createProperty(String[] stringValues) throws ValueFormatException {

        PropertyImpl prop = new PropertyImpl(this);

        if (this.isMultiple()) {
            Value[] values = this.valueFactory.createValues(stringValues, getType());
            prop.setValues(values);
        } else {
            // Not multi-value, stringValues must be of length 1, otherwise
            // there are
            // inconsistency problems between data store and config.
            if (stringValues.length > 1) {
                throw new ValueFormatException("Cannot convert multiple values: " + Arrays.asList(stringValues)
                        + " to a single-value property" + " for property " + prop);
            }

            Value value = this.valueFactory.createValue(stringValues[0], getType());
            prop.setValue(value);
        }

        return prop;

    }

    @Override
    public Property createProperty(BinaryValue binaryValue) throws ValueFormatException {
        return createProperty(new BinaryValue[] { binaryValue });
    }

    @Override
    public Property createProperty(BinaryValue[] binaryValues) throws ValueFormatException {

        switch (getType()) {
            case BINARY:
            case JSON:
            case STRING:
            case HTML:
            case IMAGE_REF:
                break;
                
            default:
                throw new ValueFormatException("Cannot create property of type " + getType() + " from binary value(s)");
        }

        PropertyImpl prop = new PropertyImpl(this);
        if (this.isMultiple()) {
            prop.setValues(this.valueFactory.createValues(binaryValues, type));
        } else {
            if (binaryValues.length > 1) {
                throw new ValueFormatException("Cannot convert multiple values: " 
                        + Arrays.asList(binaryValues)
                        + " to a single-value property for property " + prop);
            }

            prop.setValue(this.valueFactory.createValue(binaryValues[0], getType()));
        }

        return prop;

    }

    @Override
    public void afterPropertiesSet() {
        if (this.valueFormatter == null) {
            if (this.vocabulary != null && this.vocabulary.getValueFormatter() != null) {
                this.valueFormatter = this.vocabulary.getValueFormatter();
            } else {
                this.valueFormatter = this.valueFormatterRegistry.getValueFormatter(this.type);
            }
        }

        this.metadata = Collections.unmodifiableMap(this.metadata);
    }

    @Override
    public PropertyEvaluator getPropertyEvaluator() {
        return this.propertyEvaluator;
    }

    public void setPropertyEvaluator(PropertyEvaluator propertyEvaluator) {
        this.propertyEvaluator = propertyEvaluator;
    }

    @Override
    public boolean isMandatory() {
        return this.mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    @Override
    public boolean isMultiple() {
        return this.multiple;
    }
    
    @Override
    public boolean isInheritable() {
        return this.inheritable;
    }
    
    public void setInheritable(boolean inheritable) {
        this.inheritable = inheritable;
    }

    public void setDefaultValue(Value defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public Value getDefaultValue() {
        return this.defaultValue;
    }

    public void setMultiple(boolean multiple) {
        this.multiple = multiple;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public RepositoryAction getProtectionLevel() {
        return this.protectionLevel;
    }

    public void setProtectionLevel(RepositoryAction protectionLevel) {
        this.protectionLevel = protectionLevel;
    }

    @Override
    public Type getType() {
        return this.type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public PropertyValidator getValidator() {
        return this.validator;
    }

    public void setValidator(PropertyValidator validator) {
        this.validator = validator;
    }

    public Value[] getAllowedValues() {
        return this.allowedValues;
    }

    @Override
    public Namespace getNamespace() {
        return this.namespace;
    }

    public void setNamespace(Namespace namespace) {
        this.namespace = namespace;
    }

    @Override
    public String toString() {
        String prefix = namespace == null ? null : namespace.getPrefix();
        return "PropDef{"+(prefix != null ? prefix + ":" + name : name)+"}";
    }

    @Override
    public Vocabulary<Value> getVocabulary() {
        return this.vocabulary;
    }

    public void setVocabulary(Vocabulary<Value> vocabulary) {
        this.vocabulary = vocabulary;
    }

    @Override
    public ValueFormatter getValueFormatter() {
        return this.valueFormatter;
    }

    public void setValueFormatter(ValueFormatter valueFormatter) {
        this.valueFormatter = valueFormatter;
    }

    public void setTypeLocalizationProvider(TypeLocalizationProvider typeLocalizationProvider) {
        this.typeLocalizationProvider = typeLocalizationProvider;
    }

    @Override
    public String getLocalizedName(Locale locale) {
        if (this.typeLocalizationProvider != null) {
            return this.typeLocalizationProvider.getLocalizedPropertyName(this, locale);
        }
        return getName();
    }

    @Override
    public String getDescription(Locale locale) {
        if (this.typeLocalizationProvider != null) {
            return this.typeLocalizationProvider.getPropertyDescription(this, locale);
        }
        return null;
    }

    @Override
    public ValueSeparator getValueSeparator(String format) {
        ValueSeparator separator = this.valueSeparators.get(format);
        if (separator != null) {
            return separator;
        }
        return this.defaultValueSeparator;
    }

    public void setDefaultValueSeparator(ValueSeparator defaultValueSeparator) {
        this.defaultValueSeparator = defaultValueSeparator;
    }

    public void setValueSeparators(Map<String, ValueSeparator> valueSeparators) {
        this.valueSeparators = valueSeparators;
    }

    @Required
    public void setValueFactory(ValueFactory valueFactory) {
        this.valueFactory = valueFactory;
    }

    @Required
    public void setValueFormatterRegistry(ValueFormatterRegistry valueFormatterRegistry) {
        this.valueFormatterRegistry = valueFormatterRegistry;
    }

}
