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

import vtk.repository.resourcetype.PropertyTypeDefinition;

public class PropertySortField extends SortField {

    private PropertyTypeDefinition definition;
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

    @Override
    public String toString() {
        if (this.complexValueAttributeSpecifier != null){
            return this.definition.getName() + "@"
                    + this.complexValueAttributeSpecifier
                    + " " + getDirection().toString();
        } else {
            return this.definition.getName() + " " + getDirection().toString();
        }
    }

    public String getComplexValueAttributeSpecifier() {
        return complexValueAttributeSpecifier;
    }

    public void setComplexValueAttributeSpecifier(String complexValueAttributeSpecifier) {
        this.complexValueAttributeSpecifier = complexValueAttributeSpecifier;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PropertySortField other = (PropertySortField) obj;
        if (this.definition != other.definition && (this.definition == null || !this.definition.equals(other.definition))) {
            return false;
        }
        if ((this.complexValueAttributeSpecifier == null) ? (other.complexValueAttributeSpecifier != null) : !this.complexValueAttributeSpecifier.equals(other.complexValueAttributeSpecifier)) {
            return false;
        }
        if (getDirection() != other.getDirection()) {
            return false;
        }
        if (getLocale() != other.getLocale()) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode() * 7;
        hash = 79 * hash + (this.definition != null ? this.definition.hashCode() : 0);
        hash = 79 * hash + (this.complexValueAttributeSpecifier != null ? this.complexValueAttributeSpecifier.hashCode() : 0);
        return hash;
    }
    
}
