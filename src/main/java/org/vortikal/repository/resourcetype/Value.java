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
package org.vortikal.repository.resourcetype;

import java.util.Arrays;
import java.util.Date;

import org.vortikal.repository.resourcetype.PropertyType.Type;
import org.vortikal.security.Principal;


public final class Value implements Cloneable, Comparable<Value> {
    
    private Type type = PropertyType.Type.STRING;
    public static final long MAX_LENGTH = 2048;

    private String stringValue;
    private Date dateValue;
    private boolean booleanValue;
    private int intValue;
    private long longValue;
    private Principal principalValue;
    
    // Oh no...
    private byte[] binaryValue;
    private String binaryRef;
    private String binaryMimeType;

    public Value(String stringValue) {
        if (stringValue == null || stringValue.equals(""))
            throw new IllegalArgumentException("Value object cannot be null or empty");
        if (stringValue.length() > MAX_LENGTH) {
            throw new ValueFormatException(
                "String value too large: " + stringValue.length() + " (max size = "
                + MAX_LENGTH + ")");
        }

        this.type = PropertyType.Type.STRING;
        this.stringValue = stringValue;
    }
    
    public Value(boolean booleanValue) {
        this.type = PropertyType.Type.BOOLEAN;
        this.booleanValue = booleanValue;
    }

    public Value(Date dateValue, boolean date) {
        if (dateValue == null)
            throw new IllegalArgumentException("Value object cannot be null");

        if (date) {
            this.type = PropertyType.Type.DATE;            
        } else {
            this.type = PropertyType.Type.TIMESTAMP;
        }
        this.dateValue = (Date)dateValue.clone();
    }

    public Value(long longValue) {
        this.type = PropertyType.Type.LONG;
        this.longValue = longValue;
    }
    
    public Value(int intValue) {
        this.type = PropertyType.Type.INT;
        this.intValue = intValue;
    }
    
    public Value(Principal principalValue) {
        if (principalValue == null)
            throw new IllegalArgumentException("Value object cannot be null");
        String qualifiedName = principalValue.getQualifiedName();
        if (qualifiedName.length() > MAX_LENGTH) {
            throw new ValueFormatException(
                "Princpal name too long: " + qualifiedName.length() + " (max size = "
                + MAX_LENGTH + ")");
        }

        this.type = PropertyType.Type.PRINCIPAL;
        this.principalValue = principalValue;
    }
    
    public Value(byte[] binaryValue, String binaryRef, String binaryMimType) {
    	this.binaryValue = binaryValue;
    	this.binaryRef = binaryRef;
    	this.binaryMimeType = binaryMimType;
    	this.type = PropertyType.Type.BINARY;
    }

    public Type getType() {
        return this.type;
    }
    
    public boolean getBooleanValue() {
        return this.booleanValue;
    }

    public Date getDateValue() {
        return (Date)this.dateValue.clone();
    }
    
    public long getLongValue() {
        return this.longValue;
    }
    
    public int getIntValue() {
        return this.intValue;
    }
    
    public Principal getPrincipalValue() {
        return this.principalValue;
    }

    public String getStringValue() {
        return this.stringValue;
    }
    
    public byte[] getBinaryValue() {
    	return this.binaryValue;
    }
    
    public String getBinaryMimeType() {
    	return this.binaryMimeType;
    }
    
    public String getBinaryRef() {
    	return this.binaryRef;
    }
    
    public Object getObjectValue() {
        switch (this.type) {
        
            case BOOLEAN:
                return Boolean.valueOf(this.booleanValue);
            
            case DATE:    
            case TIMESTAMP:
                return this.dateValue.clone();
            
            case INT:
                return new Integer(this.intValue);
            
            case LONG:
                return new Long(this.longValue);

            case STRING:
            case HTML:
            case IMAGE_REF:
                return this.stringValue;
            
            case PRINCIPAL:
                return this.principalValue;
            case BINARY:
            	return this.binaryValue;
        }
        
        throw new IllegalStateException(
            "Unable to return value: Illeal type: " + this.type);
    }
    

    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Value)) {
            return false;
        }
        
        if (obj == this) return true;
        
        Value v = (Value) obj;
        
        if (this.type != v.getType()) 
            return false;
        
        switch (this.type) {
        case BOOLEAN:
            return (this.booleanValue == v.getBooleanValue());
        case INT:
            return (this.intValue == v.getIntValue());
        case LONG:
            return (this.longValue == v.getLongValue());
        case DATE:    
        case TIMESTAMP:
            return (this.dateValue == null && v.getDateValue() == null) ||
                (this.dateValue != null && this.dateValue.equals(v.getDateValue()));
        case PRINCIPAL:
            return (this.principalValue == null && v.getPrincipalValue() == null) ||
                (this.principalValue != null && this.principalValue.equals(v.getPrincipalValue()));
        case BINARY:
        	return Arrays.equals(this.binaryValue, v.getBinaryValue());
        default:
            return (this.stringValue == null && v.getStringValue() == null) ||
                (this.stringValue != null && this.stringValue.equals(v.getStringValue()));
        }
    }
    
    public int hashCode() {
        int hash  = 7 * 31;

        switch (this.type) {
        case BOOLEAN:
            return hash + 1 + (this.booleanValue ? 1231 : 1237);
        case INT:
            return hash + 2 + this.intValue;   
        case LONG:
            return hash + 3 + (int)(this.longValue ^ (this.longValue >>> 32));
        case DATE:    
        case TIMESTAMP:    
            return hash + 4 + (this.dateValue == null ? 0 : this.dateValue.hashCode());
        case PRINCIPAL:
            return hash + 5 + (this.principalValue == null ? 0 : this.principalValue.hashCode());
        case BINARY:
        	return hash + 6 + (this.binaryValue == null ? 0 : this.binaryValue.hashCode());
        default:
            return hash + (this.stringValue == null ? 0 : this.stringValue.hashCode());
        }
    }
    
    public Object clone() {

        switch (this.type) {
        case BOOLEAN:
            return new Value(this.booleanValue);
        case INT:
            return new Value(this.intValue);   
        case LONG:
            return new Value(this.longValue);
        case DATE:    
            return new Value((Date)this.dateValue.clone(), true);
        case TIMESTAMP:    
            return new Value((Date)this.dateValue.clone(), false);
        case PRINCIPAL:
            return new Value(this.principalValue);
        case BINARY:
        	return new Value(this.binaryValue, this.binaryRef, this.binaryMimeType);
        default:
            return new Value(this.stringValue);
        }
    }
    
    public int compareTo(Value other) {
        if (this.type != other.type) {
            throw new IllegalArgumentException("Values not of same type");
        }
        switch (this.type) {
        case BOOLEAN:
            return Boolean.valueOf(this.booleanValue).compareTo(other.booleanValue);
        case INT:
            return Integer.valueOf(this.intValue).compareTo(other.intValue);
        case LONG:
            return Long.valueOf(this.longValue).compareTo(other.longValue);
        case DATE:    
        case TIMESTAMP:    
            return this.dateValue.compareTo(other.dateValue);
        case PRINCIPAL:
            return this.principalValue.getQualifiedName().compareTo(
                other.principalValue.getQualifiedName());
        default:
            return this.stringValue.compareTo(other.stringValue);
        }
    }
    

    public String toString() {
        StringBuilder sb = new StringBuilder();

        switch (this.type) {
            case STRING:
            case HTML:
            case IMAGE_REF:
                sb.append(this.stringValue);
                break;
            case INT:
                sb.append(this.intValue);
                break;
            case LONG:
                sb.append(this.longValue);
                break;
            case DATE:    
            case TIMESTAMP:
                sb.append(this.dateValue);
                break;
            case BOOLEAN:
                sb.append(this.booleanValue);
                break;
            case PRINCIPAL:
                sb.append(this.principalValue);
                break;
            case BINARY:
            	sb.append(this.binaryRef + ", mimetype: " + this.binaryMimeType +
            			", contentlength:" + this.binaryValue.length);
            	break;
            default:
                sb.append(this.stringValue);
                break;
        }
        return sb.toString();
    }
    
    public String getNativeStringRepresentation() {
        
        String representation = null;
        switch (this.type) {
        
        case BOOLEAN:
            representation = this.booleanValue ? "true" : "false";
            break;
            
        case DATE:    
        case TIMESTAMP:
            Date date = this.dateValue;
            
            if (date == null) {
                throw new ValueFormatException(
                    "Cannot convert date value to string, field was null");
            }
            
            representation = Long.toString(date.getTime());
            break;
            
        case INT:
            representation = Integer.toString(this.intValue);
            break;
            
        case LONG:
            representation = Long.toString(this.longValue);
            break;
            
        case STRING:
        case HTML:
        case IMAGE_REF:
            representation = this.stringValue;
            break;
        case BINARY:
        	representation = this.binaryValue.toString();
        	break;
            
        case PRINCIPAL:
            Principal principal = this.principalValue;
            if (principal == null) {
                throw new ValueFormatException(
                    "Cannot convert principal value to string, field was null");
            }
            
            representation = principal.getQualifiedName();
        }
        
        return representation;
    }
}
