/* Copyright (c) 2008, University of Oslo, Norway
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
package org.vortikal.repository.reporting;

/**
 * Finds all unique values for the property and returns a report
 * with a list of value<->frequency pairs.
 *
 */
public class PropertyValueFrequencyQuery extends AbstractPropertyValueQuery {

    public static final int DEFAULT_LIMIT = 10;
    public static final int LIMIT_UNLIMITED = -1;
    
    public static enum Ordering {
        ASCENDING_BY_FREQUENCY("FREQ_ASC"),
        DESCENDING_BY_FREQUENCY("FREQ_DESC"),
        ASCENDING_BY_PROPERTY_VALUE("VALUE_ASC"),
        DESCENDING_BY_PROPERTY_VALUE("VALUE_DESC"),
        NONE("NONE");
        
        private String value;
        Ordering(String value) {
            this.value = value;
        }

        @Override
        public String toString()  {
            return this.value;
        }
    }
    
    private int limit = LIMIT_UNLIMITED;
    private Ordering ordering = Ordering.NONE;  

    public void setLimit(int limit) {
        if (limit < 0 && limit != LIMIT_UNLIMITED) {
            throw new IllegalArgumentException(
                                "Limit must be greater that zero or -1");
        }
        this.limit = limit;
    }
    
    public int getLimit() {
        return this.limit;
    }

    public Ordering getOrdering() {
        return ordering;
    }

    public void setOrdering(Ordering ordering) {
        this.ordering = ordering;
    }

    @Override
    public int hashCode() {
        int code = 7;

        if (getPropertyTypeDefintion() != null) {
            code = 31 * code + getPropertyTypeDefintion().hashCode();
        }
        
        if (getUriScope() != null) {
            code = 31 * code + getUriScope().hashCode();
        }
        
        code = 31 * code + this.ordering.hashCode();
        
        code = 31 * code + this.limit;
        
        return code;
    }
    
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        
        if (other == null || (this.getClass() != other.getClass())) {
            return false;
        }

        PropertyValueFrequencyQuery otherQuery = (PropertyValueFrequencyQuery)other;

        if (getPropertyTypeDefintion() != otherQuery.getPropertyTypeDefintion()) return false;
        
        if (getUriScope() != null) {
            if (otherQuery.getUriScope() == null) return false;
            
            if (! getUriScope().equals(otherQuery.getUriScope())) return false;
        } else {
            if (otherQuery.getUriScope() != null) return false;
        }

        if (this.ordering != otherQuery.ordering) return false;
        
        if (this.limit != otherQuery.limit) return false;
        
        return true;
    }
    
    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder(getClass().getSimpleName());

        buffer.append('[');
        buffer.append("propDef = ").append(getPropertyTypeDefintion()).append(", ");
        buffer.append("uriScope = ").append(getUriScope()).append(", ");
        buffer.append("ordering = ").append(this.ordering).append(", ");
        buffer.append("limit = ").append(this.limit).append(']');
        
        return buffer.toString();
    }

    @Override
    public Object clone() {
        PropertyValueFrequencyQuery clone = new PropertyValueFrequencyQuery();
        clone.setPropertyTypeDefinition(getPropertyTypeDefintion());
        if (getUriScope() != null) {
            clone.setUriScope(new UriScope(getUriScope().getUri()));
        }
        clone.setLimit(this.limit);
        clone.setOrdering(this.ordering);
        return clone;
    }
    
}
