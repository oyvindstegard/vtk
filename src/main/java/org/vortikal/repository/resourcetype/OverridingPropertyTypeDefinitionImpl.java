/* Copyright (c) 2007, University of Oslo, Norway
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
package org.vortikal.repository.resourcetype;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Property;
import org.vortikal.repository.RepositoryAction;
import org.vortikal.repository.Vocabulary;
import org.vortikal.repository.resourcetype.PropertyType.Type;

public class OverridingPropertyTypeDefinitionImpl implements OverridablePropertyTypeDefinition, InitializingBean {

    private Map<String, Object> metadata = new HashMap<String, Object>();
	
    private OverridablePropertyTypeDefinition overriddenPropDef;
    private PropertyEvaluator propertyEvaluator;
    private Value defaultValue;

    @Override
    public PropertyEvaluator getPropertyEvaluator() {
        if (this.propertyEvaluator != null) {
            return this.propertyEvaluator;
        }
        return this.overriddenPropDef.getPropertyEvaluator();
    }

    @Override
    public Value getDefaultValue() {
        
        if (this.defaultValue != null) {
            return this.defaultValue;
        }
        return this.overriddenPropDef.getDefaultValue();
    }

    @Override
    public String getName() {
        return this.overriddenPropDef.getName();
    }
    
    @Override
    public String getLocalizedName(Locale locale) {
        return this.overriddenPropDef.getLocalizedName(locale);
    }

    @Override
    public Namespace getNamespace() {
        return this.overriddenPropDef.getNamespace();
    }

    @Override
    public RepositoryAction getProtectionLevel() {
        return this.overriddenPropDef.getProtectionLevel();
    }

    @Override
    public Type getType() {
        return this.overriddenPropDef.getType();
    }

    @Override
    public boolean isInherited() {
        return this.overriddenPropDef.isInherited();
    }
    
    @Override
    public PropertyValidator getValidator() {
        return this.overriddenPropDef.getValidator();
    }

    @Override
    public boolean isMandatory() {
        return this.overriddenPropDef.isMandatory();
    }

    @Override
    public boolean isMultiple() {
        return this.overriddenPropDef.isMultiple();
    }

    public void setOverriddenPropDef(OverridablePropertyTypeDefinition overriddenPropDef) {
        this.overriddenPropDef = overriddenPropDef;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.overriddenPropDef == null) {
            throw new BeanInitializationException("Java bean property 'overriddenPropDef' must be set");
        }
    
        this.metadata = Collections.unmodifiableMap(this.metadata);
    }

    public void setPropertyEvaluator(
            PropertyEvaluator propertyEvaluator) {
        this.propertyEvaluator = propertyEvaluator;
    }

    public void setDefaultValue(Value defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public Vocabulary<Value> getVocabulary() {
        return this.overriddenPropDef.getVocabulary();
    }

    @Override
    public ValueFormatter getValueFormatter() {
        return this.overriddenPropDef.getValueFormatter();
    }

    @Override
    public Property createProperty(Object value) throws ValueFormatException {
        return this.overriddenPropDef.createProperty(value);
    }

    @Override
    public Property createProperty(String stringValue)
            throws ValueFormatException {
        return this.overriddenPropDef.createProperty(stringValue);
    }

    @Override
    public Property createProperty(String[] stringValues)
            throws ValueFormatException {
        return this.overriddenPropDef.createProperty(stringValues);
    }

    @Override
    public Property createProperty(BinaryValue binaryValue) throws ValueFormatException {
        return this.overriddenPropDef.createProperty(binaryValue);
    }

    @Override
    public Property createProperty(BinaryValue[] binaryValues) throws ValueFormatException {
        return this.overriddenPropDef.createProperty(binaryValues);
    }

    @Override
    public Property createProperty() {
        return this.overriddenPropDef.createProperty();
    }

    @Override
    public ContentRelation getContentRelation() {
        return this.overriddenPropDef.getContentRelation();
    }
    
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
    	return this.metadata;
    }
    
    @Override
    public String getDescription(Locale locale) {
        return this.overriddenPropDef.getDescription(locale);
    }

    @Override
    public ValueSeparator getValueSeparator(String format) {
        return this.overriddenPropDef.getValueSeparator(format);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append(": [name=").append(getName()).append("]");
        return sb.toString();
    }

}
