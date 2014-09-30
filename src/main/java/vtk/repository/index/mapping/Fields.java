/* Copyright (c) 2014, University of Oslo, Norway
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

package vtk.repository.index.mapping;

import com.ibm.icu.text.Collator;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.collation.ICUCollationAttributeFactory;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;
import vtk.repository.resourcetype.ValueFormatException;
import vtk.util.cache.ReusableObjectArrayStackCache;
import vtk.util.cache.ReusableObjectCache;

/**
 *
 */
public abstract class Fields {
    
    /* Common field prefixes */
    public static final String LOWERCASE_FIELD_PREFIX = "l_";
    public static final String SORT_FIELD_PREFIX = "s_";
            
    
    // Note that order (complex towards simpler format) is important here.
    public static final String[] SUPPORTED_DATE_FORMATS = {
                                                "yyyy-MM-dd HH:mm:ss Z",
                                                "yyyy-MM-dd HH:mm:ss",
                                                "yyyy-MM-dd HH:mm",
                                                "yyyy-MM-dd HH",
                                                "yyyy-MM-dd" };
    
    private static final FieldType STRING_SORT_FIELDTYPE = new FieldType();

    private static final ReusableObjectCache<SimpleDateFormat>[] CACHED_DATE_FORMAT_PARSERS;

    static {
        STRING_SORT_FIELDTYPE.setIndexed(true);
        STRING_SORT_FIELDTYPE.setOmitNorms(true);
        STRING_SORT_FIELDTYPE.setIndexOptions(FieldInfo.IndexOptions.DOCS_ONLY);
        STRING_SORT_FIELDTYPE.setTokenized(true);
        STRING_SORT_FIELDTYPE.freeze();

        // Create parser caches for each date format (maximum capacity of 3
        // instances per format)
        CACHED_DATE_FORMAT_PARSERS = new ReusableObjectCache[SUPPORTED_DATE_FORMATS.length];

        for (int i = 0; i < SUPPORTED_DATE_FORMATS.length; i++) {
            CACHED_DATE_FORMAT_PARSERS[i] = new ReusableObjectArrayStackCache<SimpleDateFormat>(3);
        }
    }
    
    private final Locale locale;
    private final Collator collator;
    private final ICUCollationAttributeFactory collationAttributeFactory;
    
    Fields(Locale locale) {
        this.locale = locale != null ? locale : Locale.getDefault();
        this.collator = Collator.getInstance(this.locale);
        this.collationAttributeFactory = new ICUCollationAttributeFactory(collator);
    }
    
    public Locale getLocale() {
        return locale;
    }
    
    public Collator getCollator() {
        return collator;
    }
    
    /**
     * Specify indexing characteristics for field.
     * 
     * <p>Values:
     * <ul>
     *   <li>{@link #INDEXED} - Only indexed.
     *   <li>{@link #INDEXED_STORED} - Both indexed and stored.
     *   <li>{@link #INDEXED_LOWERCASE} - Lowercase-indexed, but not stored.
     *   <li>{@link #STORED} - Only stored (not searchable, but retrievable from docs).
     * </ul>
     */
    public enum FieldSpec {
        INDEXED,
        INDEXED_STORED,
        INDEXED_LOWERCASE,
        STORED
    }

    /**
     * Create special field used only for sorting string values in a localized
     * fashion. The field value will be encoded as a collation key using the
     * configured default locale.
     * 
     * @param name the field name
     * @param value the field value, a string.
     * @return 
     */
    public IndexableField makeSortField(String name, String value) {
        // Use a "token stream", even though we only have one value. This is due to
        // Lucene API not allowing creation of raw binary indexable terms without going
        // through this path.
        StringArrayTokenStream ts = new StringArrayTokenStream(collationAttributeFactory, value);
        return new Field(name, ts, STRING_SORT_FIELDTYPE);
    }
    
    public List<IndexableField> makeFields(String fieldName, String value, FieldSpec spec) {
        List<IndexableField> fields = new ArrayList<IndexableField>(2);
        if (isIndex(spec)) {
            if (isLowercase(spec)) {
                value = lowercase(value, locale);
            }
            fields.add(new StringField(fieldName, value, Field.Store.NO));
        }
        if (isStore(spec)) {
            fields.add(new StoredField(fieldName, value));
        }
        
        return fields;
    }
    
    public List<IndexableField> makeFields(String fieldName, Date value, FieldSpec spec) {
        List<IndexableField> fields = new ArrayList<IndexableField>(2);
        long longValue = value.getTime();
        if (isIndex(spec)) {
            long indexValue = DateTools.round(longValue, DateTools.Resolution.SECOND);
            fields.add(new LongField(fieldName, indexValue, Field.Store.NO));
        }
        if (isStore(spec)) {
            fields.add(new StoredField(fieldName, longValue));
        }
        return fields;
    }
    
    public List<IndexableField> makeFields(String fieldName, Boolean value, FieldSpec spec) {
        if (spec == FieldSpec.INDEXED_LOWERCASE) {
            spec = FieldSpec.INDEXED;
        }
        return makeFields(fieldName, value ? "true" : "false", spec);
    }
    
    public List<IndexableField> makeFields(String fieldName, long value, FieldSpec spec) {
        List<IndexableField> fields = new ArrayList<IndexableField>(2);
        if (isIndex(spec)) {
            fields.add(new LongField(fieldName, value, Field.Store.NO));
        }
        if (isStore(spec)) {
            fields.add(new StoredField(fieldName, value));
        }
        return fields;
    }
    
    public List<IndexableField> makeFields(String fieldName, int value, FieldSpec spec) {
        List<IndexableField> fields = new ArrayList<IndexableField>(2);
        if (isIndex(spec)) {
            fields.add(new IntField(fieldName, value, Field.Store.NO));
        }
        if (isStore(spec)) {
            fields.add(new StoredField(fieldName, value));
        }
        return fields;
    }
    
    public List<IndexableField> makeFields(String fieldName, byte[] value) {
        List<IndexableField> fields = new ArrayList<IndexableField>(1);
        fields.add(new StoredField(fieldName, value));
        return fields;
    }
    
    private boolean isIndex(FieldSpec spec) {
        return spec == FieldSpec.INDEXED 
                || spec == FieldSpec.INDEXED_LOWERCASE 
                || spec == FieldSpec.INDEXED_STORED;
    }
    
    private boolean isStore(FieldSpec spec) {
        return spec == FieldSpec.STORED || spec == FieldSpec.INDEXED_STORED;
    }
    
    private boolean isLowercase(FieldSpec spec) {
        return spec == FieldSpec.INDEXED_LOWERCASE;
    }
  
    private String lowercase(String value, Locale locale) {
        if (locale != null) {
            return value.toLowerCase(locale);
        }

        return value.toLowerCase();
    }
    
    /**
     * Create a (possibly encoded) query term for the given value object with the
     * given value type, optionally lowercasing. (Lowercasing is only applicable
     * to string value types.)
     * 
     * @param fieldName
     * @param value
     * @param basicType
     * @param lowercase
     * @return 
     */
    public Term queryTerm(String fieldName, Object value, 
                    Class basicType, boolean lowercase) {
        if (basicType == String.class) {
            if (lowercase) {
                return new Term(fieldName, lowercase(value.toString(), locale));
            }
            return new Term(fieldName, value.toString());
            
        } else if (basicType == java.util.Date.class) {
            return new Term(fieldName, dateValueToIndexTerm(value));
            
        } else if (basicType == java.lang.Integer.class) {
            return new Term(fieldName, integerValueToIndexTerm(value));
            
        } else if (basicType == java.lang.Long.class) {
            return new Term(fieldName, longValueToIndexTerm(value));
            
        } else if (basicType == java.lang.Boolean.class) {
            boolean booleanValue = "true".equals(value) 
                    || (value instanceof Boolean) && ((Boolean)value).booleanValue();
            return new Term(fieldName, booleanValue ? "true" : "false");
        } else {
            return new Term(fieldName, value.toString());
        }
    }
    
    /**
     * Parse object as date. If object is a <code>Long</code>, value is interpreted
     * as number of milliseconds since epoch, otherwise an attempt to parse 
     * {@link Object#toString() toString} in various formats is done.
     * 
     * <p>The returned value is rounded to nearest second.
     * 
     * @see #SUPPORTED_DATE_FORMATS
     * @param value value of date, as object. 
     * @return long value as number of milliseconds since epoch, rounded to nearest second
     */
    public long parseDate(Object value) {
        Long longValue = null;
        if (value.getClass() == Long.class) {
            longValue = (Long) value;
        } else if (value.getClass() == Date.class) {
            longValue = ((Date)value).getTime();
        }

        if (longValue == null) {
            String stringValue = value.toString();
            try {
                longValue = Long.parseLong(stringValue);
            } catch (NumberFormatException nfe) {
                // Failed to parse "long" format, try other formats
                Date d = null;
                for (int i = 0; i < SUPPORTED_DATE_FORMATS.length; i++) {
                    SimpleDateFormat formatter = CACHED_DATE_FORMAT_PARSERS[i].getInstance();
                    if (formatter == null) {
                        formatter = new SimpleDateFormat(SUPPORTED_DATE_FORMATS[i]);
                    }

                    try {
                        d = formatter.parse(stringValue);
                        break;
                    } catch (ParseException e) {
                        // Ignore failed parsing attempt
                    } finally {
                        // Cache the constructed date parser for re-use
                        CACHED_DATE_FORMAT_PARSERS[i].putInstance(formatter);
                    }
                }
                if (d != null) {
                    longValue = d.getTime();
                }
            }
        }

        if (longValue == null) {
            throw new ValueFormatException("Unable to parse date value '" + value
                    + "'");
        }
        
        return DateTools.round(longValue, DateTools.Resolution.SECOND);
    }
    
    private BytesRef dateValueToIndexTerm(Object value) {
        long longValue = parseDate(value);
        
        // Finally, create encoded index field representation of the parsed
        // date.
        BytesRef bytes = new BytesRef();
        NumericUtils.longToPrefixCoded(longValue, 0, bytes);
        return bytes;
    }
    
    private BytesRef integerValueToIndexTerm(Object value) {
        final Integer intValue;
        if (value.getClass() == Integer.class) {
            intValue = (Integer) value;
        } else {
            try {
                // Validate and encode
                intValue = Integer.parseInt(value.toString());
            } catch (NumberFormatException nfe) {
                throw new ValueFormatException("Unable to encode integer string value to "
                        + "to index field value representation: " + nfe.getMessage());
            }
        }
        BytesRef bytes = new BytesRef();
        NumericUtils.intToPrefixCoded(intValue, 0, bytes);
        return bytes;
    }
    
    private BytesRef longValueToIndexTerm(Object value) {
        final Long longValue;
        if (value.getClass() == Long.class) {
            longValue = (Long)value;
        } else {
            try {
                // Validate and encode
                longValue = Long.parseLong(value.toString());
            } catch (NumberFormatException nfe) {
                throw new ValueFormatException("Unable to encode long integer string value to "
                        + "to index field value representation: " + nfe.getMessage());
            }
        }
        BytesRef bytes = new BytesRef();
        NumericUtils.longToPrefixCoded(longValue, 0, bytes);
        return bytes;
    }
    
    
}
