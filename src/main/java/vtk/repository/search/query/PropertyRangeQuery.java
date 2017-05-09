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
 * Range matching on property values.
 */
public class PropertyRangeQuery extends AbstractPropertyQuery {

    private final String fromTerm;
    private final String toTerm;
    private final boolean inclusive;

    public PropertyRangeQuery(PropertyTypeDefinition propertyDefinition, String from, String to, boolean inclusive) {
        super(propertyDefinition);
        this.fromTerm = from;
        this.toTerm = to;
        this.inclusive = inclusive;
    }

    public PropertyRangeQuery(PropertyTypeDefinition propDef, String complexValueAttribute, String from, String to, boolean inclusive) {
        super(propDef, complexValueAttribute);
        this.fromTerm = from;
        this.toTerm = to;
        this.inclusive = inclusive;
    }

    public PropertyRangeQuery(String name, Namespace ns, PropertyType.Type type, String from, String to, boolean inclusive) {
        super(name, ns, type);
        this.fromTerm = from;
        this.toTerm = to;
        this.inclusive = inclusive;
    }

    public PropertyRangeQuery(String name, Namespace ns, PropertyType.Type type, String complexValueAttribute, String from, String to, boolean inclusive) {
        super(name, ns, type, complexValueAttribute);
        this.fromTerm = from;
        this.toTerm = to;
        this.inclusive = inclusive;
    }

    public String getFromTerm() {
        return this.fromTerm;
    }

    public boolean isInclusive() {
        return this.inclusive;
    }

    public String getToTerm() {
        return this.toTerm;
    }

    @Override
    public String toString() {
        return "PropertyRangeQuery{" + "fromTerm=" + fromTerm + ", toTerm=" + toTerm
                + ", inclusive=" + inclusive + ", " + super.fieldsToString() + '}';
    }

    @Override
    public Object accept(QueryVisitor visitor, Object data) {
        return visitor.visit(this, data);
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
        
        final PropertyRangeQuery other = (PropertyRangeQuery) obj;
        if ((this.fromTerm == null) ? (other.fromTerm != null) : !this.fromTerm.equals(other.fromTerm)) {
            return false;
        }
        if ((this.toTerm == null) ? (other.toTerm != null) : !this.toTerm.equals(other.toTerm)) {
            return false;
        }
        if (this.inclusive != other.inclusive) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 89 * hash + (this.fromTerm != null ? this.fromTerm.hashCode() : 0);
        hash = 89 * hash + (this.toTerm != null ? this.toTerm.hashCode() : 0);
        hash = 89 * hash + (this.inclusive ? 1 : 0);
        return hash;
    }
    
    
}
