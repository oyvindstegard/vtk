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
package vtk.repository.resourcetype;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.time.FastDateFormat;
import vtk.util.repository.LocaleHelper;

public class DateValueFormatter implements ValueFormatter {

    /* Months where short version of the name is just as long as the full name is unnecessary.
     * For instance, Jun. and June is equally long, thus using June is better.
     */
    private final Pattern shortMonthsPattern = Pattern.compile("mar\\.|May\\.|mai\\.|Jun\\.|jun\\.|Jul\\.|jul\\.");
    private final Map<String, String> monthsEn = new HashMap<>();
    private final Map<String, String> monthsNo = new HashMap<>();

    private final String defaultDateFormatKey = "long";
    private final Locale defaultLocale = new Locale("en");

    private final Map<String, FastDateFormat> namedDateFormats = new HashMap<>();
    private final Set<String> recognizedLocales = new HashSet<>();
    private boolean isDate = false;

    public DateValueFormatter() {
        recognizedLocales.add("no");
        recognizedLocales.add("nn");
        recognizedLocales.add("en");

        monthsEn.put("May.", "May");
        monthsEn.put("Jun.", "June");
        monthsEn.put("Jul.", "July");
        monthsNo.put("mar.", "mars");
        monthsNo.put("mai.", "mai");
        monthsNo.put("jun.", "juni");
        monthsNo.put("jul.", "juli");

        namedDateFormats.put("short_en", FastDateFormat.getInstance("MMM. d, yyyy", new Locale("en")));
        namedDateFormats.put("short_no", FastDateFormat.getInstance("d. MMM. yyyy", new Locale("no")));
        namedDateFormats.put("short_nn", FastDateFormat.getInstance("d. MMM. yyyy", new Locale("no")));
        namedDateFormats.put("long_en", FastDateFormat.getInstance("MMM. d, yyyy h:mm a", new Locale("en")));
        namedDateFormats.put("long_no", FastDateFormat.getInstance("d. MMM. yyyy HH:mm", new Locale("no")));
        namedDateFormats.put("long_nn", FastDateFormat.getInstance("d. MMM. yyyy HH:mm", new Locale("no")));
        namedDateFormats.put("longlong_en", FastDateFormat.getInstance("MMM. d, yyyy h:mm:ss a", new Locale("en")));
        namedDateFormats.put("longlong_no", FastDateFormat.getInstance("d. MMM. yyyy HH:mm:ss", new Locale("no")));
        namedDateFormats.put("longlong_nn", FastDateFormat.getInstance("d. MMM. yyyy HH:mm:ss", new Locale("no")));
        namedDateFormats.put("hours-minutes_en", FastDateFormat.getInstance("h:mm a", new Locale("en")));
        namedDateFormats.put("hours-minutes_no", FastDateFormat.getInstance("HH:mm", new Locale("no")));
        namedDateFormats.put("hours-minutes_nn", FastDateFormat.getInstance("HH:mm", new Locale("no")));
        namedDateFormats.put("iso-8601", FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ssZZ", new Locale("en")));
        namedDateFormats.put("iso-8601-short", FastDateFormat.getInstance("yyyy-MM-dd", new Locale("en")));
        namedDateFormats.put("rfc-822", FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss Z", new Locale("en")));

        namedDateFormats.put("full-month-year-short_no", FastDateFormat.getInstance("d. MMMM yyyy", new Locale("no")));
        namedDateFormats.put("full-month-year-short_nn", FastDateFormat.getInstance("d. MMMM yyyy", new Locale("no")));
        namedDateFormats.put("full-month-year-short_en", FastDateFormat.getInstance("MMMM d, yyyy", new Locale("en")));
        namedDateFormats.put("full-month-year_no", FastDateFormat.getInstance("MMMM yyyy", new Locale("no")));
        namedDateFormats.put("full-month-year_nn", FastDateFormat.getInstance("MMMM yyyy", new Locale("no")));
        namedDateFormats.put("full-month-year_en", FastDateFormat.getInstance("MMMM yyyy", new Locale("en")));
        namedDateFormats.put("full-month-short_no", FastDateFormat.getInstance("d. MMMM", new Locale("no")));
        namedDateFormats.put("full-month-short_nn", FastDateFormat.getInstance("d. MMMM", new Locale("no")));
        namedDateFormats.put("full-month-short_en", FastDateFormat.getInstance("MMMM d", new Locale("en")));
    }

    public DateValueFormatter(boolean isDate) {
        this();
        this.isDate = isDate;
    }

    @Override
    public String valueToString(Value value, String format, Locale locale) throws IllegalValueTypeException {
        String ret;

        if (value.getType() != PropertyType.Type.TIMESTAMP && value.getType() != PropertyType.Type.DATE) {
            throw new IllegalValueTypeException(PropertyType.Type.TIMESTAMP, value.getType());
        }

        if (format == null) {
            format = defaultDateFormatKey;
        }
        Date date = value.getDateValue();

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        if (isDate && value.getType() == PropertyType.Type.DATE && format.contains("long")
                && cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0) {
            format = format.replace("long", "short");
        }

        locale = LocaleHelper.getMessageLocalizationLocale(locale);
        if (locale == null || !recognizedLocales.contains(locale.getLanguage())) {
            locale = defaultLocale;
        }
        // Check if format refers to any of the
        // predefined (named) formats:
        String key = format + "_" + locale.getLanguage();

        FastDateFormat f = namedDateFormats.get(key);
        if (f == null) {
            key = format;
            f = namedDateFormats.get(key);
        }

        try {
            if (f == null) {
                // Parse the given format
                // XXX: formatter instances should be cached
                f = FastDateFormat.getInstance(format, locale);
            }
            ret = f.format(date);
            Matcher matcher = shortMonthsPattern.matcher(ret);
            if (matcher.find()) {
                if (locale.getLanguage().equals("en")) {
                    ret = ret.replaceFirst(matcher.group(), monthsEn.get(matcher.group()));
                } else {
                    ret = ret.replaceFirst(matcher.group(), monthsNo.get(matcher.group()));
                }
            }
        } catch (Throwable t) {
            ret = "Error: " + t.getMessage();
        }

        return ret;
    }

    private static final String[] FALLBACK_DATE_FORMATS = new String[]{"dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy HH:mm",
        "dd.MM.yyyy", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd"};

    @Override
    public Value stringToValue(String string, String format, Locale locale) throws IllegalArgumentException {
        if (format == null) {
            format = defaultDateFormatKey;
        }
        if (locale == null || !recognizedLocales.contains(locale.getLanguage())) {
            locale = defaultLocale;
        }

        FastDateFormat fdf = namedDateFormats.get(format + "_" + locale.getLanguage());

        if (fdf == null) {
            fdf = namedDateFormats.get(format);
        }

        if (fdf != null) {
            format = fdf.getPattern();
            locale = fdf.getLocale();
        }

        SimpleDateFormat sdf = new SimpleDateFormat(format, locale);
        Date date;
        try {
            date = sdf.parse(string);
        } catch (ParseException e) {
            for (String fallbackFormat : FALLBACK_DATE_FORMATS) {
                try {
                    SimpleDateFormat fsdf = new SimpleDateFormat(fallbackFormat, Locale.getDefault());
                    date = fsdf.parse(string);
                    return new Value(date, isDate);
                } catch (ParseException t) {
                }
            }
            throw new IllegalArgumentException("Unable to parse to date value from '" + string
                    + "' object using string format '" + format + "'", e);
        }
        return new Value(date, isDate);
    }

}
