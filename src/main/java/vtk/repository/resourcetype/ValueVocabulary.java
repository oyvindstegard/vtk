/* Copyright (c) 2007,2017 University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import vtk.repository.Vocabulary;

/**
 * An ordered set of allowed values of uniform type.
 *
 * <p>Supports formatting of values through resource bundle files.
 */
public class ValueVocabulary implements Vocabulary<Value> {

    private final PropertyType.Type type;
    private List<Value> values = new ArrayList<>();
    private MessageSourceValueFormatter messageSourceValueFormatter;

    /**
     * Construct vocabulary with default value type {@link PropertyType.Type#STRING}.
     */
    public ValueVocabulary() {
        this.type = PropertyType.Type.STRING;
    }

    /**
     * Construct vocabulary with specified value type.
     * @param type
     */
    public ValueVocabulary(PropertyType.Type type) {
        this.type = type;
    }

    @Override
    public List<Value> vocabularyValues() {
        return values;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + values.toString() + "}";
    }

    /**
     * Set list of allowed values.
     *
     * <p>If list contains <code>Value</code> objects of a different type than
     * declared for the vocabulary, then value will converted if possible. For
     * other objects, conversion of their default string representation will
     * be attempted.
     *
     * @param vocabularyValues initial list of allowed values for vocabulary
     */
    public void setValues(List<Value> vocabularyValues) {
        if (vocabularyValues == null) {
            throw new IllegalArgumentException("values cannot be null");
        }

        this.values = vocabularyValues.stream().map(this::convert).collect(Collectors.toList());
    }

    /**
     * Add an allowed value to the vocabulary.
     * @see #setValues(java.util.List)
     * @param value
     */
    public void addValue(Value value) {
        values.add(convert(value));
    }

    private Value convert(Value value) {
        if (value.getType() == type) {
            return value;
        }

        return new Value(value.toString(), type);
    }

    /**
     * Set initial message source base name used for localized value formatting.
     * @param baseName
     */
    public void setMessageSourceBaseName(String baseName) {
        this.messageSourceValueFormatter = new MessageSourceValueFormatter(type, baseName);
    }

    /**
     * Add a message source basename for the value vocabulary.
     * @param baseName
     */
    public void addMessageSourceBaseName(String baseName) {
        if (this.messageSourceValueFormatter == null) {
            this.messageSourceValueFormatter = new MessageSourceValueFormatter(type, baseName);
        } else {
            this.messageSourceValueFormatter.addMessageSourceBaseNames(baseName);
        }
    }

    /**
     * @return a value formatter for this vocabulary, possibly <code>null</code> if
     * no message source has been configured
     */
    @Override
    public ValueFormatter getValueFormatter() {
        return this.messageSourceValueFormatter;
    }
    
}
