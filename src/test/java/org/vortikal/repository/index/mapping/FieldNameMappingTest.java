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
package org.vortikal.repository.index.mapping;

import java.util.Locale;

import junit.framework.TestCase;

import org.vortikal.repository.Namespace;
import org.vortikal.repository.Property;
import org.vortikal.repository.resourcetype.IllegalValueTypeException;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.PropertyTypeDefinitionImpl;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.repository.resourcetype.ValueFormatter;

public class FieldNameMappingTest extends TestCase {

    private Property getUndefinedProperty(Namespace namespace, String name) {
        PropertyTypeDefinitionImpl propDef = new PropertyTypeDefinitionImpl();
        propDef.setNamespace(namespace);
        propDef.setName(name);
        propDef.setValueFormatter(new ValueFormatter() {

            public Value stringToValue(String string, String format,
                    Locale locale) {
                return new Value(string, PropertyType.Type.STRING);
            }

            public String valueToString(Value value, String format,
                    Locale locale) throws IllegalValueTypeException {
                return value.toString();
            }
            
        });
        propDef.afterPropertiesSet();
        return propDef.createProperty();
    }

    public void testGetSearchFieldNameProperty() {
        
        Property prop = getUndefinedProperty(Namespace.getNamespaceFromPrefix("bar"), "foo");
        
        assertEquals("bar:foo", FieldNameMapping.getSearchFieldName(prop, false));
        assertEquals(FieldNameMapping.LOWERCASE_FIELD_PREFIX + "bar:foo", 
                               FieldNameMapping.getSearchFieldName(prop, true));
        
        prop = getUndefinedProperty(Namespace.DEFAULT_NAMESPACE, "lastModified");
        
        assertEquals("lastModified", FieldNameMapping.getSearchFieldName(prop, false));
        
        assertEquals(FieldNameMapping.LOWERCASE_FIELD_PREFIX + 
                "lastModified", FieldNameMapping.getSearchFieldName(prop, true));
        
        
        
    }

    public void testGetSearchFieldNamePropertyTypeDefinition() {

        PropertyTypeDefinitionImpl def = 
            new PropertyTypeDefinitionImpl();
        
        def.setName("foo");
        def.setNamespace(Namespace.getNamespaceFromPrefix("bar"));
        
        assertEquals("bar:foo", FieldNameMapping.getSearchFieldName(def, false));
        assertEquals(FieldNameMapping.LOWERCASE_FIELD_PREFIX + 
                "bar:foo", FieldNameMapping.getSearchFieldName(def, true));
        
        def = new PropertyTypeDefinitionImpl();
        def.setName("lastModified");
        def.setNamespace(Namespace.DEFAULT_NAMESPACE);
        
        assertEquals("lastModified", FieldNameMapping.getSearchFieldName(def, false));
        assertEquals(FieldNameMapping.LOWERCASE_FIELD_PREFIX 
                + "lastModified", FieldNameMapping.getSearchFieldName(def, true));
        
    }

    public void testGetSearchFieldNameStringString() {
        
        String fieldName = FieldNameMapping.getSearchFieldName("foo", null, false);
        assertEquals("foo", fieldName);
        
        fieldName = FieldNameMapping.getSearchFieldName("foo", null, true);
        assertEquals(FieldNameMapping.LOWERCASE_FIELD_PREFIX + "foo", fieldName);
        
        fieldName = FieldNameMapping.getSearchFieldName("bar", "foo", false);
        assertEquals("foo:bar", fieldName);
        
        fieldName = FieldNameMapping.getSearchFieldName("bar", "foo", true);
        assertEquals(FieldNameMapping.LOWERCASE_FIELD_PREFIX + "foo:bar", fieldName);
    }

    public void testGetStoredFieldNameProperty() {
        
        Property prop = getUndefinedProperty(Namespace.getNamespaceFromPrefix("bar"), "foo");
        
        String fieldName = FieldNameMapping.getStoredFieldName(prop);
        
        assertEquals("b_bar:foo", fieldName);
        
        prop = getUndefinedProperty(Namespace.DEFAULT_NAMESPACE, "lastModified");
        
        fieldName = FieldNameMapping.getStoredFieldName(prop);
        assertEquals("b_lastModified", fieldName);
        
    }

    public void testGetStoredFieldNamePropertyTypeDefinition() {
        
        PropertyTypeDefinitionImpl def = 
            new PropertyTypeDefinitionImpl();
        def.setName("foo");
        def.setNamespace(Namespace.getNamespaceFromPrefix("bar"));
        
        String fieldName = FieldNameMapping.getStoredFieldName(def);
        
        assertEquals("b_bar:foo", fieldName);
        
        def = new PropertyTypeDefinitionImpl();
        def.setName("lastModified");
        def.setNamespace(Namespace.DEFAULT_NAMESPACE);
        
        fieldName = FieldNameMapping.getStoredFieldName(def);
        
        assertEquals("b_lastModified", fieldName);
        
    }

    public void testGetPropertyNamespacePrefixFromStoredFieldName() {
        
        String fieldName = "foo";
        
        String nsPrefix = FieldNameMapping.getPropertyNamespacePrefixFromStoredFieldName(fieldName);
        
        assertNull(nsPrefix);
        
        fieldName = "b_bar:foo";
        
        nsPrefix = FieldNameMapping.getPropertyNamespacePrefixFromStoredFieldName(fieldName);
        
        assertEquals("bar", nsPrefix);
    }

    public void testGetPropertyNameFromStoredFieldName() {
        String fieldName = "b_foo";
        
        String name = FieldNameMapping.getPropertyNameFromStoredFieldName(fieldName);
        
        assertEquals("foo", name);
        
        fieldName = "b_bar:foo";
        
        name = FieldNameMapping.getPropertyNameFromStoredFieldName(fieldName);
        
        assertEquals("foo", name);
        
    }

}
