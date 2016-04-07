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
package vtk.repository.resourcetype;

import java.io.UnsupportedEncodingException;
import java.util.Date;

import vtk.repository.IllegalOperationException;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.security.Principal;
import vtk.util.text.Json;
import vtk.util.text.JsonStreamer;

/**
 * Holds a single property value of appropriate type. Does not enforce value limits.
 * 
 * JSON values are always stored in the stringValue field, even though they
 * can also be set and get as binary values.
 */
public class Value implements Cloneable, Comparable<Value> {

    private Type type = Type.STRING;

    private String stringValue;
    private Date dateValue;
    private boolean booleanValue;
    private int intValue;
    private long longValue;
    private Principal principalValue;
    private BinaryValue binaryValue;

    public Value(String stringValue, Type type) {
        if (stringValue == null || stringValue.isEmpty())
            throw new IllegalArgumentException("Value object cannot be null or empty");
        switch (type) {
        case STRING:
        case HTML:
        case IMAGE_REF:
        case JSON:
            this.type = type;
            this.stringValue = stringValue;
            break;
            
        case BOOLEAN:
            if ("true".equals(stringValue) || "false".equals(stringValue)) {
                this.type = Type.BOOLEAN;
                this.booleanValue = "true".equals(stringValue);
                break;
            }
        default:
            throw new IllegalArgumentException("Invalid type [" + type 
                    + "] for constructor of value [" + stringValue + "]");
        }
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
        this.dateValue = (Date) dateValue.clone();
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

        this.type = PropertyType.Type.PRINCIPAL;
        this.principalValue = principalValue;
    }

    public Value(Json.MapContainer value) {
        this.type = Type.JSON;
        this.stringValue = JsonStreamer.toJson(value);
    }

    /**
     * Construct a new value from byte buffer. Value type can either be pure binary,
     * or of a string based type.
     * 
     * @param buffer
     * @param contentType
     * @param valueType one of BINARY, JSON, STRING, IMAGE_REF or HTML
     */
    public Value(byte[] buffer, String contentType, Type valueType) {
        if (buffer == null)
            throw new IllegalArgumentException("buffer cannot be null");
        
        switch (valueType) {
            case BINARY:
                this.type = Type.BINARY;
                this.binaryValue = new BufferedBinaryValue(buffer, contentType);
                break;
                
            case JSON:
            case STRING:
            case IMAGE_REF:
            case HTML:
                // Copy binary data to stringValue field
                try {
                    this.stringValue = new String(buffer, "UTF-8");
                    this.type = valueType;
                } catch (UnsupportedEncodingException ue) {
                    throw new IllegalStateException("UTF-8 encoding not available");
                }
                break;
                
            default:
                throw new IllegalArgumentException("valueType must be one of: BINARY, JSON, STRING, IMAGE_REF or HTML");
                
        }
    }

    Value(BinaryValue value, Type valueType) {
        if (value == null)
            throw new IllegalArgumentException("value cannot be null");
        
        switch (valueType) {
            case BINARY:
                this.type = Type.BINARY;
                this.binaryValue = value; // Possibly lazy-loaded variant
                break;
                
            case JSON:
            case STRING:
            case IMAGE_REF:
            case HTML:
                // Stay compatible with loading JSON from binary values for all string based types,
                // since JSON props may become dead and thus will be loaded as STRING type.
                // Validate content type, to make sure we don't allow creating garbage
                // strings from arbitrary binary data.
                String valueContentType = value.getContentType();
                if (valueContentType == null
                        || !("application/json".equals(valueContentType) || valueContentType.startsWith("text/"))) {
                    throw new IllegalArgumentException("Content type 'application/json' or 'text/*'"
                            + " required for creating " + valueType + " value type from binary storage (got content type '" + valueContentType + "' from binary value reference)");
                }
                // Copy binary data to stringValue field after sanity checking content type
                try {
                    this.stringValue = new String(value.getBytes(), "UTF-8");
                    this.type = valueType;
                } catch (UnsupportedEncodingException ue) {
                    throw new IllegalStateException("UTF-8 encoding not available");
                }
                break;
                
            default:
                throw new IllegalArgumentException("valueType must be one of: BINARY, JSON, STRING, IMAGE_REF or HTML");
                
        }
    }

    public Type getType() {
        return this.type;
    }

    public boolean getBooleanValue() {
        return this.booleanValue;
    }

    public Date getDateValue() {
        return (Date) this.dateValue.clone();
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

    public Json.MapContainer getJSONValue() {
        return Json.parseToContainer(getStringValue()).asObject();
    }

    /**
     * Get <code>BinaryValue</code> of this value. Works for raw binary values of
     * type {@link Type#BINARY} or string based JSON value type, which will be encoded
     * as UTF-8.
     * @return A <code>BinaryValue</code> instance for this value, or <code>null</code>
     * if no such value is applicable.
     */
    public BinaryValue getBinaryValue() {
        // For consistency, we allowing getting binary value for JSON, since we also
        // allow setting JSON through a binary value.
        if (this.type == Type.JSON && this.stringValue != null) {
            return new BufferedBinaryValue(this.stringValue, "application/json");
        }
        
        return this.binaryValue;
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
        case JSON:
            return getStringValue();

        case PRINCIPAL:
            return this.principalValue;

        case BINARY:
            return this.binaryValue;
        }

        throw new IllegalStateException("Unable to create object value: Illegal type: " + this.type);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Value)) {
            return false;
        }

        if (obj == this)
            return true;

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
            return (this.dateValue == null && v.getDateValue() == null)
                    || (this.dateValue != null && this.dateValue.equals(v.getDateValue()));
        case PRINCIPAL:
            return (this.principalValue == null && v.getPrincipalValue() == null)
                    || (this.principalValue != null && this.principalValue.equals(v.getPrincipalValue()));
        case BINARY:
            return (this.binaryValue == null && v.binaryValue == null)
                    || (this.binaryValue != null && this.binaryValue.equals(v.binaryValue));

        // String based types
        default:
            return (getStringValue() == null && v.getStringValue() == null)
                    || (getStringValue() != null && getStringValue().equals(v.getStringValue()));
        }
    }

    @Override
    public int hashCode() {
        int hash = 7 * 31;

        switch (this.type) {
        case BOOLEAN:
            return hash + 1 + (this.booleanValue ? 1231 : 1237);
        case INT:
            return hash + 2 + this.intValue;
        case LONG:
            return hash + 3 + (int) (this.longValue ^ (this.longValue >>> 32));
        case DATE:
        case TIMESTAMP:
            return hash + 4 + (this.dateValue == null ? 0 : this.dateValue.hashCode());
        case PRINCIPAL:
            return hash + 5 + (this.principalValue == null ? 0 : this.principalValue.hashCode());
        case BINARY:
            return this.binaryValue.hashCode();
        default:
            return hash + (getStringValue() == null ? 0 : getStringValue().hashCode());
        }
    }

    @Override
    public Object clone() {

        switch (this.type) {
        case BOOLEAN:
            return new Value(this.booleanValue);
        case INT:
            return new Value(this.intValue);
        case LONG:
            return new Value(this.longValue);
        case DATE:
            return new Value(this.dateValue, true);
        case TIMESTAMP:
            return new Value(this.dateValue, false);
        case PRINCIPAL:
            return new Value(this.principalValue);
        case BINARY:
            return new Value(this.binaryValue, Type.BINARY);
        case JSON:
            return new Value(getStringValue(), Type.JSON);
        default:
            return new Value(this.stringValue, this.type);
        }
    }

    @Override
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
            return this.principalValue.getQualifiedName().compareTo(other.principalValue.getQualifiedName());
        case BINARY:
            throw new IllegalOperationException("cannot compare binary values");
            
        // String based value types:
        default:
            return getStringValue().compareTo(other.getStringValue());
        }
    }

    @Override
    public String toString() {
        switch (this.type) {
        case INT:
            return String.valueOf(this.intValue);

        case LONG:
            return String.valueOf(this.longValue);

        case DATE:
        case TIMESTAMP:
            return String.valueOf(this.dateValue);

        case BOOLEAN:
            return String.valueOf(this.booleanValue);

        case PRINCIPAL:
            return String.valueOf(this.principalValue);

        case BINARY:
            return this.binaryValue.toString();
            
        default:
            return getStringValue();
        }
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
                throw new ValueFormatException("Cannot convert date value to string, field was null");
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
        case JSON:
            representation = getStringValue();
            break;

        case BINARY:
            representation = "#binary";
            break;

        case PRINCIPAL:
            Principal principal = this.principalValue;
            if (principal == null) {
                throw new ValueFormatException("Cannot convert principal value to string, field was null");
            }

            representation = principal.getQualifiedName();
        }

        return representation;
    }
}
