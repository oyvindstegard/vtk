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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
    private static FastDateFormat HTTP_DATE_FORMATTER =
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

    /* HTTP status codes defined by WebDAV */
    public static final int SC_PROCESSING = 102;
    public static final int SC_MULTI_STATUS = 207;
    public static final int SC_UNPROCESSABLE_ENTITY = 418;
    public static final int SC_INSUFFICIENT_SPACE_ON_RESOURCE = 419;
    public static final int SC_METHOD_FAILURE = 420;
    public static final int SC_LOCKED = 423;
    public static final int SC_FAILED_DEPENDENCY = 424;
    public static final int SC_INSUFFICIENT_STORAGE = 507;

    private static final Map<Integer, String> statusMessages;
    static {
        statusMessages = new HashMap<Integer, String>();
        statusMessages.put(HttpServletResponse.SC_ACCEPTED, "Accepted");
        statusMessages.put(HttpServletResponse.SC_BAD_GATEWAY, "Bad Gateway");
        statusMessages.put(HttpServletResponse.SC_BAD_REQUEST, "Bad Request");
        statusMessages.put(HttpServletResponse.SC_CONFLICT, "Conflict");
        statusMessages.put(HttpServletResponse.SC_CONTINUE, "Continue");
        statusMessages.put(HttpServletResponse.SC_CREATED, "Created");
        statusMessages.put(HttpServletResponse.SC_EXPECTATION_FAILED, "Expectation Failed");
        statusMessages.put(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
        statusMessages.put(HttpServletResponse.SC_GATEWAY_TIMEOUT, "Gateway Timeout");
        statusMessages.put(HttpServletResponse.SC_GONE, "Gone");
        statusMessages.put(HttpServletResponse.SC_HTTP_VERSION_NOT_SUPPORTED, "HTTP Version Not Supported");
        statusMessages.put(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
        statusMessages.put(HttpServletResponse.SC_LENGTH_REQUIRED, "Length Required");
        statusMessages.put(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method Not Allowed");
        statusMessages.put(HttpServletResponse.SC_MOVED_PERMANENTLY, "Moved Permanently");
        statusMessages.put(HttpServletResponse.SC_MOVED_TEMPORARILY, "Moved Temporarily");
        statusMessages.put(HttpServletResponse.SC_MULTIPLE_CHOICES, "Multiple Choices");
        statusMessages.put(HttpServletResponse.SC_NO_CONTENT, "No Content");
        statusMessages.put(HttpServletResponse.SC_NON_AUTHORITATIVE_INFORMATION, "Non Authorative Information");
        statusMessages.put(HttpServletResponse.SC_NOT_ACCEPTABLE, "Not Acceptible");
        statusMessages.put(HttpServletResponse.SC_NOT_FOUND, "Not Found");
        statusMessages.put(HttpServletResponse.SC_NOT_MODIFIED, "Not Modified");
        statusMessages.put(HttpServletResponse.SC_OK, "OK");
        statusMessages.put(HttpServletResponse.SC_PARTIAL_CONTENT, "Partial Content");
        statusMessages.put(HttpServletResponse.SC_PAYMENT_REQUIRED, "Payment Required");
        statusMessages.put(HttpServletResponse.SC_PRECONDITION_FAILED, "Precondition Failed");
        statusMessages.put(HttpServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED, "Proxy Authentication Required");
        statusMessages.put(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request Entity Too Large");
        statusMessages.put(HttpServletResponse.SC_REQUEST_TIMEOUT, "Request Timeout");
        statusMessages.put(HttpServletResponse.SC_REQUEST_URI_TOO_LONG, "Request URI Too Long");
        statusMessages.put(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE, "Requested Range Not Satisfiable");
        statusMessages.put(HttpServletResponse.SC_RESET_CONTENT, "Reset Content");
        statusMessages.put(HttpServletResponse.SC_SEE_OTHER, "See Other");
        statusMessages.put(HttpServletResponse.SC_SWITCHING_PROTOCOLS, "Switching Protocols");
        statusMessages.put(HttpServletResponse.SC_TEMPORARY_REDIRECT, "Temporary Redirect");
        statusMessages.put(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
        statusMessages.put(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type");
        statusMessages.put(HttpServletResponse.SC_USE_PROXY, "Use Proxy");

        /* Include some WebDAV status codes: */
        statusMessages.put(SC_MULTI_STATUS, "Multi-Status");
        statusMessages.put(SC_PROCESSING, "Processing");
        statusMessages.put(SC_UNPROCESSABLE_ENTITY, "Unprocessable Entity");
        statusMessages.put(SC_INSUFFICIENT_SPACE_ON_RESOURCE, "Insufficient Space On Resource");
        statusMessages.put(SC_METHOD_FAILURE, "Method Failure");
        statusMessages.put(SC_LOCKED, "Locked");
        statusMessages.put(SC_FAILED_DEPENDENCY, "Failed Dependency");
        statusMessages.put(SC_INSUFFICIENT_STORAGE, "Insufficient Storage");

        CACHED_HTTP_DATE_FORMATS = new ReusableObjectCache[HTTP_DATE_PARSE_FORMATS.length];
        for (int i=0; i<HTTP_DATE_PARSE_FORMATS.length; i++) {
            final String dateFormat = HTTP_DATE_PARSE_FORMATS[i];
            CACHED_HTTP_DATE_FORMATS[i] = new ArrayStackCache<>(() -> new SimpleDateFormat(dateFormat), 4);
        }
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
        if (headerValue.indexOf(";") == -1) {
            return headerValue.trim();
        }
        return headerValue.substring(0, headerValue.indexOf(";")).trim();
    }      
    


    /**
     * Gets the status message for a HTTP status code. For example,
     * <code>getStatusMessage(HttpServletResponse.SC_NOT_FOUND)</code>
     * would return <code>Not Found</code>.
     *
     * @param status an <code>int</code> value
     * @return a <code>String</code>
     */
    public static String getStatusMessage(int status) {
        if (!statusMessages.containsKey(status)) {
            throw new IllegalArgumentException("Unknown status code: " + status);
        }
        return statusMessages.get(status);
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
