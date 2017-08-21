/* Copyright (c) 2017, University of Oslo, Norway
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.util.cache.ArrayStackCache;
import vtk.util.cache.ReusableObjectCache;

/**
 * A value factory which is self contained with no external
 * factory dependencies.
 *
 * <p>This factory cannot be used to create values of type {@link PropertyType.Type#PRINCIPAL}.
 */
public class DefaultValueFactory implements ValueFactory {

    private static final String[] DATE_FORMATS = new String[] {
                                               "yyyy-MM-dd HH:mm:ss",
                                               "yyyy-MM-dd HH:mm",
                                               "yyyy-MM-dd",
                                               "dd.MM.yyyy HH:mm:ss",
                                               "dd.MM.yyyy HH:mm",
                                               "dd.MM.yyyy"
                                              };

    private static final ReusableObjectCache<SimpleDateFormat>[]
                                                    CACHED_DATE_FORMAT_PARSERS;
    static {
        CACHED_DATE_FORMAT_PARSERS = new ReusableObjectCache[DATE_FORMATS.length];
        for (int i = 0; i < DATE_FORMATS.length; i++) {
            final String dateFormat = DATE_FORMATS[i];
            CACHED_DATE_FORMAT_PARSERS[i] = new ArrayStackCache<>(
                    () -> {
                        SimpleDateFormat f = new SimpleDateFormat(dateFormat);
                        f.setLenient(false);
                        return f;
                    }, 3);
        }
    }

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /*
     * (non-Javadoc)
     *
     * @see
     * vtk.repository.resourcetype.ValueFactory#createValues(java.lang
     * .String[], vtk.repository.resourcetype.PropertyType.Type)
     */
    @Override
    public Value[] createValues(String[] stringValues, PropertyType.Type type) throws ValueFormatException {

        if (stringValues == null) {
            throw new IllegalArgumentException("stringValues cannot be null.");
        }

        Value[] values = new Value[stringValues.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = createValue(stringValues[i], type);
        }

        return values;

    }

    /*
     * (non-Javadoc)
     *
     * @see
     * vtk.repository.resourcetype.ValueFactory#createValue(java.lang
     * .String, vtk.repository.resourcetype.PropertyType.Type)
     */
    @Override
    public Value createValue(String stringValue, PropertyType.Type type) throws ValueFormatException {

        if (stringValue == null) {
            throw new IllegalArgumentException("stringValue cannot be null");
        }

        switch (type) {

        case STRING:
        case HTML:
        case IMAGE_REF:
        case JSON:
            if (stringValue.length() == 0) {
                throw new ValueFormatException("Illegal string value: empty");
            }
            return new Value(stringValue, type);

        case BOOLEAN:
            return Boolean.parseBoolean(stringValue) ? Value.TRUE : Value.FALSE;

        case DATE:
            Date date = getDateFromStringValue(stringValue);
            return new Value(date, true);
        case TIMESTAMP:
            // old: Dates are represented as number of milliseconds since
            // January 1, 1970, 00:00:00 GMT
            // Dates are represented as described in the configuration for this
            // bean in the List stringFormats
            Date timestamp = getDateFromStringValue(stringValue);
            return new Value(timestamp, false);

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

        // Type PRINCIPAL is purposefully omitted in this implementation

        default:
            throw new IllegalArgumentException("Cannot make string value '"
                    + stringValue + "' into type '" + type + "'");
        }

    }

    @Override
    public Value createValue(BinaryValue binaryValue, PropertyType.Type type) throws ValueFormatException {
        return new Value(binaryValue, type);
    }

    @Override
    public Value[] createValues(BinaryValue[] binaryValues, PropertyType.Type type) throws ValueFormatException {
        Value[] values = new Value[binaryValues.length];
        for (int i=0; i<binaryValues.length; i++) {
            values[i] = createValue(binaryValues[i], type);
        }
        return values;
    }

    private Date getDateFromStringValue(String stringValue) throws ValueFormatException {
        try {
            return new Date(Long.parseLong(stringValue));
        } catch (NumberFormatException nfe) {}

        // Try different date formats in order
        for (ReusableObjectCache<SimpleDateFormat> dateFormatCache: CACHED_DATE_FORMAT_PARSERS) {
            final SimpleDateFormat formatter = dateFormatCache.getInstance();
            try {
                return formatter.parse(stringValue);
            } catch (ParseException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to parse date using format '"
                        + formatter.toPattern()
                        + "', input '" + stringValue + "'", e);
                }
            } finally {
                // Return constructed date parser for later re-use
                dateFormatCache.putInstance(formatter);
            }
        }
        throw new ValueFormatException("Unable to parse date value for input string: '"
                + stringValue + "'");
    }
}
