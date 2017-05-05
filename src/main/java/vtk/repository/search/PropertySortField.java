/* Copyright (c) 2006, 2007, University of Oslo, Norway
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
package vtk.repository.search;

import java.util.Locale;
import java.util.Objects;

import vtk.repository.resourcetype.PropertyTypeDefinition;

/**
 * XXX this class is not yet serializable
 * XXX instances of this class are not immutable
 */
public class PropertySortField extends SortField {

    private transient PropertyTypeDefinition definition;
    private String complexValueAttributeSpecifier;

    public PropertySortField(PropertyTypeDefinition def) {
        this.definition = def;
    }

    public PropertySortField(PropertyTypeDefinition def, SortField.Direction direction) {
        super(direction);
        if (def == null) {
            throw new IllegalArgumentException("Argument 'def' cannot be NULL");
        }
        this.definition = def;
    }

    public PropertySortField(PropertyTypeDefinition def, SortField.Direction direction, Locale locale) {
        super(direction, locale);
        if (def == null) {
            throw new IllegalArgumentException("Argument 'def' cannot be NULL");
        }
        this.definition = def;
    }

    public PropertyTypeDefinition getDefinition() {
        return this.definition;
    }

    public String getComplexValueAttributeSpecifier() {
        return complexValueAttributeSpecifier;
    }

    public void setComplexValueAttributeSpecifier(String complexValueAttributeSpecifier) {
        this.complexValueAttributeSpecifier = complexValueAttributeSpecifier;
    }

    @Override
    public String toString() {
        return "PropertySortField{" + "name=" + definition.getName() + ", namespace=" + definition.getNamespace() + ", type=" + definition.getType()
                + ", complexValueAttributeSpecifier=" + complexValueAttributeSpecifier + ", direction="
                + super.getDirection() + ", locale=" + super.getLocale() + '}';
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 97 * hash + Objects.hashCode(this.definition);
        hash = 97 * hash + Objects.hashCode(this.complexValueAttributeSpecifier);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PropertySortField other = (PropertySortField) obj;
        if (!Objects.equals(this.complexValueAttributeSpecifier, other.complexValueAttributeSpecifier)) {
            return false;
        }
        if (!Objects.equals(this.definition, other.definition)) {
            return false;
        }
        return true;
    }


}
