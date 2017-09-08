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
import com.ibm.icu.text.RawCollationKey;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.util.BytesRef;
import vtk.repository.resourcetype.ValueFormatException;
import vtk.util.cache.ArrayStackCache;
import vtk.util.cache.ReusableObjectCache;

/**
 *
 */
public abstract class Fields {
    
    /* Common field prefixes */
    public static final String LOWERCASE_FIELD_PREFIX = "l_";
    public static final String SORT_FIELD_PREFIX = "s_";

    /**
     * A special meta field which shall index the names of all application level fields that exist
     * in a document.
     *
     * <p>Used for efficient EXISTS-type queries and is automatically populated
     * at indexing time.
     */
    public static final String FIELD_NAMES_METAFIELD = "doc_field_names";

    /**
     * Populates field names metafield based on list of document fields.
     * @param doc doc to add metafield to
     */
    public static void addFieldNamesMetaField(final Document doc) {
        addAll(doc, doc.getFields().stream()
                        .map(f -> f.name())
                        .distinct()
                        .map(fieldname -> new StringField(Fields.FIELD_NAMES_METAFIELD, fieldname, Field.Store.NO))
                        .collect(Collectors.toList()));
    }
    
    // Note that order (complex towards simpler format) is important here.
    public static final String[] SUPPORTED_DATE_FORMATS = {
                                                "yyyy-MM-dd HH:mm:ss Z",
                                                "yyyy-MM-dd HH:mm:ss",
                                                "yyyy-MM-dd HH:mm",
                                                "yyyy-MM-dd HH",
                                                "yyyy-MM-dd" };
    
    private static final ReusableObjectCache<DateFormat>[] CACHED_DATE_FORMAT_PARSERS;

    static {
        // Create parser caches for each date format (maximum capacity of 3
        // instances per format)
        CACHED_DATE_FORMAT_PARSERS = new ReusableObjectCache[SUPPORTED_DATE_FORMATS.length];

        for (int i = 0; i < SUPPORTED_DATE_FORMATS.length; i++) {
            final String dateFormat = SUPPORTED_DATE_FORMATS[i];
            CACHED_DATE_FORMAT_PARSERS[i] = new ArrayStackCache<>(
                    ()-> new SimpleDateFormat(dateFormat), 3);
        }
    }
    
    private final Locale locale;
    private final Collator collator;
    
    Fields(Locale locale) {
        this.locale = locale != null ? locale : Locale.getDefault();
        this.collator = Collator.getInstance(this.locale);
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
     *   <li>{@link #INDEXED_WITH_DOCVALUE} - Indexed and doc value (only for single value fields)
     *   <li>{@link #INDEXED_STORED} - Both indexed and stored.
     *   <li>{@link #INDEXED_STORED_WITH_DOCVALUE} - Indexed, stored and doc value (only for single value fields)
     *   <li>{@link #INDEXED_LOWERCASE} - Lowercase-indexed, but not stored or any dedicated doc value field
     *   <li>{@link #STORED} - Only stored (not searchable, but retrievable from docs).
     * </ul>
     */
    public enum FieldSpec {
        INDEXED,
        INDEXED_WITH_DOCVALUE,
        INDEXED_STORED,
        INDEXED_STORED_WITH_DOCVALUE,
        INDEXED_LOWERCASE,
        STORED
    }

    /**
     * Add all provided files to a document.
     * @param doc
     * @param fields
     */
    public static void addAll(Document doc, List<IndexableField> fields) {
        for (IndexableField f: fields) {
            doc.add(f);
        }
    }

    /**
     * Create special field used only for sorting string values in a localized
     * fashion. The field value will be encoded as a collation key using the
     * configured default locale, and it will be stored in index as doc value.
     * 
     * @param name the field name
     * @param value the field value, a string.
     * @return 
     */
    public IndexableField makeStringSortField(String name, String value) {
        RawCollationKey key = collator.getRawCollationKey(value, new RawCollationKey());
        return new SortedDocValuesField(name, new BytesRef(key.bytes, 0, key.size));
    }
    
    public List<IndexableField> makeFields(String fieldName, String value, FieldSpec spec) {
        List<IndexableField> fields = new ArrayList<>(2);
        if (isIndex(spec)) {
            if (isLowercase(spec)) {
                value = lowercase(value, locale);
            }
            fields.add(new StringField(fieldName, value, Field.Store.NO));
        }
        if (isDocvalue(spec)) {
            fields.add(new SortedDocValuesField(fieldName, new BytesRef(value)));
        }
        if (isStore(spec)) {
            fields.add(new StoredField(fieldName, value));
        }
        
        return fields;
    }
    
    public List<IndexableField> makeFields(String fieldName, Date value, FieldSpec spec) {
        List<IndexableField> fields = new ArrayList<>(3);
        long longValue = value.getTime();
        if (isIndex(spec)) {
            long indexValue = DateTools.round(longValue, DateTools.Resolution.SECOND);
            fields.add(new LongPoint(fieldName, indexValue));
            if (isDocvalue(spec)) {
                fields.add(new NumericDocValuesField(fieldName, longValue));
            }
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
        List<IndexableField> fields = new ArrayList<>(3);
        if (isIndex(spec)) {
            fields.add(new LongPoint(fieldName, value));
            if (isDocvalue(spec)) {
                fields.add(new NumericDocValuesField(fieldName, value));
            }
        }
        if (isStore(spec)) {
            fields.add(new StoredField(fieldName, value));
        }
        return fields;
    }
    
    public List<IndexableField> makeFields(String fieldName, int value, FieldSpec spec) {
        List<IndexableField> fields = new ArrayList<>(3);
        if (isIndex(spec)) {
            fields.add(new IntPoint(fieldName, value));
            if (isDocvalue(spec)) {
                fields.add(new NumericDocValuesField(fieldName, value));
            }
        }
        if (isStore(spec)) {
            fields.add(new StoredField(fieldName, value));
        }
        return fields;
    }
    
    public List<IndexableField> makeFields(String fieldName, byte[] value) {
        List<IndexableField> fields = new ArrayList<>(1);
        fields.add(new StoredField(fieldName, value));
        return fields;
    }
    
    private boolean isIndex(FieldSpec spec) {
        return spec != FieldSpec.STORED;
    }
    
    private boolean isStore(FieldSpec spec) {
        return spec == FieldSpec.STORED
                || spec == FieldSpec.INDEXED_STORED
                || spec == FieldSpec.INDEXED_STORED_WITH_DOCVALUE;
    }

    private boolean isDocvalue(FieldSpec spec) {
        return spec == FieldSpec.INDEXED_WITH_DOCVALUE
                || spec == FieldSpec.INDEXED_STORED_WITH_DOCVALUE;
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
     * Factory method for creating exact match queries on typed fields.
     * @param fieldName
     * @param val
     * @param basicType
     * @param lowercase
     * @return
     */
    public Query typedFieldQuery(String fieldName, Object val, Class basicType, boolean lowercase) {
        if (basicType == String.class) {
            if (lowercase) {
                return new TermQuery(new Term(fieldName, lowercase(val.toString(), locale)));
            }
            return new TermQuery(new Term(fieldName, val.toString()));

        } else if (basicType == java.util.Date.class) {
            return LongPoint.newExactQuery(fieldName, parseDate(val));

        } else if (basicType == java.lang.Integer.class) {
            return IntPoint.newExactQuery(fieldName, parseInt(val));

        } else if (basicType == java.lang.Long.class) {
            return LongPoint.newExactQuery(fieldName, parseLong(val));

        } else {
            return new TermQuery(new Term(fieldName, val.toString()));
        }
    }

    /**
     * Factory method for creating range queries on typed fields.
     * @param fieldName
     * @param lowerVal
     * @param upperVal
     * @param includeLower
     * @param includeUpper
     * @param basicType
     * @param lowercase
     * @return
     */
    public Query typedFieldRangeQuery(String fieldName, Object lowerVal, Object upperVal,
            boolean includeLower, boolean includeUpper, Class basicType, boolean lowercase) {

        if (basicType == java.util.Date.class) {

            long lowerTerm = lowerVal == null ? Long.MIN_VALUE : parseDate(lowerVal);
            long upperTerm = upperVal == null ? Long.MAX_VALUE : parseDate(upperVal);

            // Timestamps are always rounded to nearest second, and LongPoint values are milliseconds
            if (!includeUpper) upperTerm = Math.addExact(upperTerm, -1000);
            if (!includeLower) lowerTerm = Math.addExact(lowerTerm, 1000);

            return LongPoint.newRangeQuery(fieldName, lowerTerm, upperTerm);

        } else if (basicType == java.lang.Long.class) {

            long lowerTerm = lowerVal == null ? Long.MIN_VALUE : parseLong(lowerVal);
            long upperTerm = upperVal == null ? Long.MAX_VALUE : parseLong(upperVal);

            if (!includeUpper) upperTerm = Math.addExact(upperTerm, -1);
            if (!includeLower) lowerTerm = Math.addExact(lowerTerm, 1);

            return LongPoint.newRangeQuery(fieldName, lowerTerm, upperTerm);

        } else if (basicType == java.lang.Integer.class) {

            int lowerTerm = lowerVal == null ? Integer.MIN_VALUE : parseInt(lowerVal);
            int upperTerm = upperVal == null ? Integer.MAX_VALUE : parseInt(upperVal);
            if (!includeLower) lowerTerm = Math.addExact(lowerTerm, 1);
            if (!includeUpper) upperTerm = Math.addExact(upperTerm, -1);

            return IntPoint.newRangeQuery(fieldName, lowerTerm, upperTerm);

        } else {
            String lowerTerm = lowerVal != null ? lowerVal.toString() : null;
            String upperTerm = upperVal != null ? upperVal.toString() : null;
            if (lowercase) {
                lowerTerm = lowerTerm != null ? lowercase(lowerTerm, locale) : null;
                upperTerm = upperTerm != null ? lowercase(upperTerm, locale) : null;
            }

            return TermRangeQuery.newStringRange(fieldName, lowerTerm, upperTerm, includeLower, includeUpper);
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
                for (ReusableObjectCache<DateFormat> dateFormatCache: CACHED_DATE_FORMAT_PARSERS) {
                    final DateFormat formatter = dateFormatCache.getInstance();
                    try {
                        d = formatter.parse(stringValue);
                        break;
                    } catch (ParseException e) {
                        // Ignore failed parsing attempt
                    } finally {
                        // Cache the constructed date parser for re-use
                        dateFormatCache.putInstance(formatter);
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
    
    private int parseInt(Object value) {
        final Integer intValue;
        if (value instanceof Number) {
            intValue = ((Number)value).intValue();
        } else {
            try {
                // Validate and encode
                intValue = Integer.parseInt(value.toString());
            } catch (NumberFormatException nfe) {
                throw new ValueFormatException("Unable to encode integer string value to "
                        + "to index field value representation: " + nfe.getMessage());
            }
        }
        return intValue;
    }
    
    private long parseLong(Object value) {
        final Long longValue;
        if (value instanceof Number) {
            longValue = ((Number)value).longValue();
        } else {
            try {
                // Validate and encode
                longValue = Long.parseLong(value.toString());
            } catch (NumberFormatException nfe) {
                throw new ValueFormatException("Unable to encode long integer string value to "
                        + "to index field value representation: " + nfe.getMessage());
            }
        }
        return longValue;
    }

}
