/* Copyright (c) 2007, University of Oslo, Norway
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.resourcetype.PropertyType.Type;
import org.vortikal.repository.store.BinaryContentDataAccessor;
import org.vortikal.security.InvalidPrincipalException;
import org.vortikal.security.Principal;
import org.vortikal.security.PrincipalFactory;

/**
 * Implementation of {@link ValueFactory}.
 */
public class ValueFactoryImpl implements ValueFactory {

    private PrincipalFactory principalFactory;
    private BinaryContentDataAccessor binaryDao;
    
    private static final String[] dateFormats = new String[] {        
        "dd.MM.yyyy HH:mm:ss",
        "dd.MM.yyyy HH:mm",
        "dd.MM.yyyy",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd"
    };

    private Log logger = LogFactory.getLog(this.getClass());

    /* (non-Javadoc)
     * @see org.vortikal.repository.resourcetype.ValueFactory#createValues(java.lang.String[], org.vortikal.repository.resourcetype.PropertyType.Type)
     */
    public Value[] createValues(String[] stringValues, Type type) 
        throws ValueFormatException {

        if (stringValues == null) {
            throw new IllegalArgumentException("stringValues cannot be null.");
        }

        Value[] values = new Value[stringValues.length];
        for (int i=0; i<values.length; i++) {
            values[i] = createValue(stringValues[i], type);
        }

        return values;

    }

    /* (non-Javadoc)
     * @see org.vortikal.repository.resourcetype.ValueFactory#createValue(java.lang.String, org.vortikal.repository.resourcetype.PropertyType.Type)
     */
    public Value createValue(String stringValue, Type type)
        throws ValueFormatException {

        if (stringValue == null) {
            throw new IllegalArgumentException("stringValue cannot be null");
        }

        switch (type) {

        case STRING:
        case HTML:
        case IMAGE_REF:
            if (stringValue.length() == 0) {
                throw new ValueFormatException("Illegal string value: empty");
            }
            return new Value(stringValue);

        case BOOLEAN:
            return new Value("true".equalsIgnoreCase(stringValue));

        case DATE:
            Date date = getDateFromStringValue(stringValue);
            return new Value(date, true);
        case TIMESTAMP:
            // old: Dates are represented as number of milliseconds since January 1, 1970, 00:00:00 GMT
            // Dates are represented as described in the configuration for this bean in the List stringFormats
            Date date2 = getDateFromStringValue(stringValue);
            return new Value(date2, false);

        case INT:
            try {
                return new Value(Integer.parseInt(stringValue));
            } catch (NumberFormatException nfe) {
                throw new ValueFormatException(nfe.getMessage());
            }

        case LONG:
            try {
                return new Value(Long.parseLong(stringValue));
            } catch (NumberFormatException nfe) {
                throw new ValueFormatException(nfe.getMessage());
            }

        case PRINCIPAL:
            try {
                Principal principal = principalFactory.getPrincipal(stringValue, Principal.Type.USER);
                return new Value(principal);
            } catch (InvalidPrincipalException e) {
                throw new ValueFormatException(e.getMessage(), e);
            }
        case BINARY:
        	// Don't fetch any of the binary content until it's specifically needed
        	return new Value(new byte[0], stringValue, "");        	
        }

        throw new IllegalArgumentException("Cannot convert '" + stringValue 
                + "' to unknown type '" + type+ "'");

    }
    
    public InputStream getBinaryStream(String binaryName, String binaryRef) {
    	return this.binaryDao.getBinaryStream(binaryName, binaryRef);
    }
    
    public String getBinaryMimeType(String binaryName, String binaryRef) {
    	return this.binaryDao.getBinaryMimeType(binaryName, binaryRef);
    }

    private Date getDateFromStringValue(String stringValue) throws ValueFormatException {

        try {
            return new Date(Long.parseLong(stringValue));
        } catch (NumberFormatException nfe) {}

        SimpleDateFormat format;
        Date date;
        for (String dateFormat: dateFormats) {
            format = new SimpleDateFormat(dateFormat);
            format.setLenient(false);
            try {
                date = format.parse(stringValue);
                return date;
            } catch (ParseException e) {
                this.logger.debug("Failed to parse date using format '" + dateFormat
                        + "', input '" + stringValue + "'", e);
            }
        }
        throw new ValueFormatException(
                "Unable to parse date value for input string: '" + stringValue + "'");
    }
    
    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }
    
    @Required
    public void setBinaryDao(BinaryContentDataAccessor binaryDao) {
    	this.binaryDao = binaryDao;
    }

}