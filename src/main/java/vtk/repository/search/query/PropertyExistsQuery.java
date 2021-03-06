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
package vtk.repository.search.query;

import vtk.repository.Namespace;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;

/**
 * Match resources where property exists.
 */
public class PropertyExistsQuery extends AbstractPropertyQuery {

    private final boolean inverted;
    
    public PropertyExistsQuery(PropertyTypeDefinition propertyDefinition, boolean inverted) {
        super(propertyDefinition);
        this.inverted = inverted;
    }
    public PropertyExistsQuery(PropertyTypeDefinition propertyDefinition, String complexValueAttributeSpecifier, boolean inverted) {
        super(propertyDefinition, complexValueAttributeSpecifier);
        this.inverted = inverted;
    }

    public PropertyExistsQuery(String name, Namespace ns, PropertyType.Type type, boolean inverted) {
        super(name, ns, type);
        this.inverted = inverted;
    }
    public PropertyExistsQuery(String name, Namespace ns, PropertyType.Type type, String complexValueAttributeSpecifier, boolean inverted) {
        super(name, ns, type, complexValueAttributeSpecifier);
        this.inverted = inverted;
    }

    public boolean isInverted() {
        return this.inverted;
    }

    @Override
    public Object accept(QueryVisitor visitor, Object data) {
        return visitor.visit(this, data);
    }

    @Override
    public String toString() {
        return "PropertyExistsQuery{" + super.fieldsToString() + ", inverted=" + inverted + '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PropertyExistsQuery other = (PropertyExistsQuery) obj;
        if (this.inverted != other.inverted) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return super.hashCode() * 59 + (this.inverted ? 1 : 0);
    }
    
}
