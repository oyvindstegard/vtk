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
package org.vortikal.repository.resourcetype;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

import org.vortikal.repository.Namespace;
import org.vortikal.repository.Property;
import org.vortikal.repository.RepositoryAction;
import org.vortikal.repository.Vocabulary;
import org.vortikal.repository.resourcetype.PropertyType.Type;


/**
 * The definition of a property
 */
public interface PropertyTypeDefinition {

    public enum ContentRelation {
        PRE_CONTENT,
        POST_CONTENT
    }
    
    /**
     * Temporary marker for properties that are visualized as content for the resource.
     * Will only have meaning for properties/resources using the new editor regime.
     */
    public ContentRelation getContentRelation();

    public Map<String, Object> getMetadata();
    
    public Namespace getNamespace();
    
    public String getName();
    
    public String getLocalizedName(Locale locale);

    public String getDescription(Locale locale);

    public Type getType();
    
    public boolean isMultiple();

    public RepositoryAction getProtectionLevel();
    
    public boolean isMandatory();

    public Value getDefaultValue();

    public Constraint getConstraint();

    public CreatePropertyEvaluator getCreateEvaluator();

    public ContentModificationPropertyEvaluator getContentModificationEvaluator();

    public PropertiesModificationPropertyEvaluator getPropertiesModificationEvaluator();

    public NameChangePropertyEvaluator getNameModificationEvaluator();

    public PropertyValidator getValidator();
    
    public Vocabulary<Value> getVocabulary();

    public ValueFormatter getValueFormatter();
    
    public ValueSeparator getValueSeparator(String format);
    
    
    /**
     * Creates (instantiates) a property with a given value, handling only single values.
     *
     * @return a property instance
     * @throws ValueFormatException if the supplied value's type does
     * not match that of the property definition
     */
    public Property createProperty(Object value) throws ValueFormatException;


    /**
     * Creates (instantiates) a property with a given value. 
     * 
     * @return a property instance
     * @throws ValueFormatException if the supplied value isn't representable in the type
     *  of this property definition
     */
    public Property createProperty(String stringValue) throws ValueFormatException;
    
    /**
     * Creates (instantiates) a property with a given set of values. 
     * 
     * @return a property instance
     * @throws ValueFormatException if the supplied values isn't representable in the type
     *  of this property definition
     */
    public Property createProperty(String[] stringValues) throws ValueFormatException;

    /**
     * Create a {@link Property} instance.
     */
    public Property createProperty();
    
    /**
     * Get a stream for the binary content of this property
     * 
     * @param binaryRef The reference to the binary content
     * @return Stream holding the binary content
     */
    public InputStream getBinaryStream(String binaryRef);

}
