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
package vtk.repository;

import vtk.util.io.InputStreamWithLength;
import java.util.Date;
import java.util.Locale;

import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.resourcetype.ValueFormatException;
import vtk.security.Principal;
import vtk.util.text.Json;

/**
 * This interface represents meta information about resources. A resource
 * may have several properties set on it, each of which are identified
 * by a namespace and a name. Properties may contain arbitrary string
 * values, such as XML. The application programmer is responsible for
 * the interpretation and processing of properties.
 */
public interface Property extends Cloneable {

    /**
     * Maximum length of string based values in characters.
     * <p>
     * Applies to {@link vtk.repository.resourcetype.PropertyType types}:
     * <ul>
     *   <li><code>STRING</code></li>
     *   <li><code>HTML</code></li>
     *   <li><code>IMAGE_REF</code></li>
     *   <li><code>PRINCIPAL</code> (length of qualified name cannot exceed this limit)</li>
     * </ul>
     * 
     * Note that the type <code>JSON</code> has no limit on length.
     */
    public static final long MAX_STRING_LENGTH = 2048;
    
    // IllegalOperationException thrown when property is multi-value
    public Value getValue() throws IllegalOperationException;

    public void setValue(Value value) throws ValueFormatException;

    // IllegalOperationException thrown when property is _not_ multi-value
    public Value[] getValues() throws IllegalOperationException;
    
    public void setValues(Value[] values) throws ValueFormatException;
    
    public void setDateValue(Date dateValue) throws ValueFormatException;

    public void setStringValue(String stringValue) throws ValueFormatException;

    public void setBooleanValue(boolean booleanValue) throws ValueFormatException;
    
    public void setLongValue(long longValue) throws ValueFormatException;
    
    public void setIntValue(int intValue) throws ValueFormatException;
    
    public void setPrincipalValue(Principal principalValue) throws ValueFormatException;

    public int getIntValue() throws IllegalOperationException;
    
    public Date getDateValue() throws IllegalOperationException;

    public String getStringValue() throws IllegalOperationException;

    public boolean getBooleanValue() throws IllegalOperationException;

    public long getLongValue() throws IllegalOperationException;
    
    public Principal getPrincipalValue() throws IllegalOperationException;
    
    public Json.MapContainer getJSONValue() throws IllegalOperationException;
    
    public void setJSONValue(Json.MapContainer value);

    public PropertyTypeDefinition getDefinition();
    
    public boolean isValueInitialized();

    /**
     * Returns
     * <code>true</code> if it the property is
     * inherited from an ancestor resource, which means it is not directly set on the
     * resource from which it was retrieved.
     *
     * @return
     * <code>true</code> if property is inherited from an ancestor resource.
     */
    public boolean isInherited();
    
    public Object clone() throws CloneNotSupportedException;

    public String getFormattedValue();
    
    public String getFormattedValue(String format, Locale locale);
    
    public Type getType();
    
    public void setBinaryValue(byte[] contentBytes, String contentType) throws ValueFormatException;

    public InputStreamWithLength getBinaryStream() throws IllegalOperationException;
    
    public String getBinaryContentType() throws IllegalOperationException;

}

