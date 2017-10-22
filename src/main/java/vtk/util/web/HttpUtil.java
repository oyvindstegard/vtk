/* Copyright (c) 2004, University of Oslo, Norway
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
package vtk.util.web;


import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import javax.servlet.http.Cookie;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.util.cache.ArrayStackCache;
import vtk.util.cache.ReusableObjectCache;


/**
 * Various Http utility methods.
 *
 */
public class HttpUtil {

    private static final Logger LOG = LoggerFactory.getLogger(HttpUtil.class);
    private static final FastDateFormat HTTP_DATE_FORMATTER =
        FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss z",
                                   java.util.TimeZone.getTimeZone("GMT"),
                                   java.util.Locale.US);
    
    private static final String HTTP_DATE_FORMAT_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz";
    private static final String HTTP_DATE_FORMAT_RFC1036 = "EEEE, dd-MMM-yy HH:mm:ss zzz";
    private static final String HTTP_DATE_FORMAT_ASCTIME = "EEE MMM d HH:mm:ss yyyy";

    private static final String[] HTTP_DATE_PARSE_FORMATS = new String[] {
        HTTP_DATE_FORMAT_RFC1123,
        HTTP_DATE_FORMAT_RFC1036,
        HTTP_DATE_FORMAT_ASCTIME
    };

    private static final ReusableObjectCache<DateFormat>[] CACHED_HTTP_DATE_FORMATS;

    private static final int INFINITE_TIMEOUT = 410000000;

    static {
        CACHED_HTTP_DATE_FORMATS = new ReusableObjectCache[HTTP_DATE_PARSE_FORMATS.length];
        for (int i=0; i<HTTP_DATE_PARSE_FORMATS.length; i++) {
            final String dateFormat = HTTP_DATE_PARSE_FORMATS[i];
            CACHED_HTTP_DATE_FORMATS[i] = new ArrayStackCache<>(() -> new SimpleDateFormat(dateFormat), 4);
        }
    }

    /**
     * Get first instance of named cookie in a request.
     * @param request the request
     * @param name the cookie name
     * @return an optionally present cookie with the given name
     */
    public static Optional<Cookie> getCookie(HttpServletRequest request, String name) {
       return Optional.ofNullable(request.getCookies())
            .flatMap(cookies -> Arrays.stream(cookies)
                    .filter(c -> c.getName().equals(name)).findFirst());
     }

    /**
     * Formats a HTTP date suitable for headers such as "Last-Modified".
     *
     * @param date a <code>Date</code> value
     * @return a date string
     */
    public static String getHttpDateString(Date date) {
        return HTTP_DATE_FORMATTER.format(date);
    }


    public static Date parseHttpDate(String str) {
        for (ReusableObjectCache<DateFormat> dateFormatCache : CACHED_HTTP_DATE_FORMATS) {
            final DateFormat parser = dateFormatCache.getInstance();
            try {
                return parser.parse(str);
            } catch (Throwable t) {
            } finally {
                dateFormatCache.putInstance(parser);
            }
        }
        try {
            return new Date(Long.parseLong(str));
        } catch (Throwable t) { }
        return null;
    }

    /**
     * Gets the MIME type part from the header of a request possibly containing a
     * 'charset' parameter.
     *
     * @param request the we want to get  HTTP 'Content-Type:' header from.
     * @return the content type, or <code>null</code> if there is no
     * such header, or if it is unparseable.
     */
    public static String getContentType(HttpServletRequest request) {
        String headerValue = request.getHeader("Content-Type");
        if (headerValue == null) {
            return null;
        }
        if (!headerValue.matches("\\s*\\w+/\\w+(;charset=[^\\s]+)?")) {
            return null;
        }
        if (!headerValue.contains(";")) {
            return headerValue.trim();
        }
        return headerValue.substring(0, headerValue.indexOf(";")).trim();
    }      

    /**
     * Searches for <code>field1="value1",field2=value,...</code>
     */
    public static String extractHeaderField(String header, String name) {
        
        int pos = 0;
        while (true) {
            int equalsIdx = header.indexOf("=", pos);
            if (equalsIdx == -1 || equalsIdx == pos) {
                break;
            }

            int valueStartIdx = equalsIdx;
            int valueEndIdx = -1;

            if (header.charAt(equalsIdx + 1) == '"') {
                valueStartIdx++;
                valueEndIdx = header.indexOf("\"", valueStartIdx + 1);
                if (valueEndIdx == -1) {
                    break;
                }
            } else {
                valueEndIdx = header.indexOf(",", valueStartIdx + 1);
                if (valueEndIdx == -1) {
                    valueEndIdx = header.indexOf(" ", valueStartIdx + 1);
                }
                if (valueEndIdx == -1) {
                    valueEndIdx = header.length();
                }
            }
            
            String fieldName = header.substring(pos, equalsIdx).trim();
            String fieldValue = header.substring(valueStartIdx + 1, valueEndIdx);

            if (fieldName.equals(name)) {
                return fieldValue;
            }
            int commaIdx = header.indexOf(",", valueEndIdx);
            if (commaIdx == -1) {
                pos = valueEndIdx + 1;
            } else {
                pos = commaIdx + 1;
            } 
        }
        return null;
    }

    public static int parseTimeoutHeader(String timeoutHeader) {
        /*
         * FIXME: Handle the 'Extend' format (see section 4.2 of RFC 2068) and multiple TimeTypes
         * (see section 9.8 of RFC 2518)
         */
        int timeout = INFINITE_TIMEOUT;

        if (timeoutHeader == null || timeoutHeader.equals("")) {
            return timeout;
        }

        if (timeoutHeader.equals("Infinite")) {
            return timeout;
        }

        if (timeoutHeader.startsWith("Extend")) {
            return timeout;
        }

        if (timeoutHeader.startsWith("Second-")) {
            try {
                String timeoutStr = timeoutHeader.substring("Second-".length(), timeoutHeader
                        .length());
                timeout = Integer.parseInt(timeoutStr);

            } catch (NumberFormatException e) {
                LOG.warn("Invalid timeout header: " + timeoutHeader);
            }
        }
        return timeout;
    }

}
