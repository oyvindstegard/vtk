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
package vtk.webdav;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import vtk.util.cache.ArrayStackCache;
import vtk.util.cache.ReusableObjectCache;
import vtk.util.web.HttpUtil;

/**
 * Some WebDAV utility methods.
 * 
 * @author oyviste
 *
 */
public final class WebdavUtil {

    /**
     * Gets the HTTP "status message" for the status codes defined in
     * {@link vtk.util.web.HttpUtil}, 
     * i.e. <code>vtk.util.web.HttpUtil.SC_MULTI_STATUS</code> will map to
     * <code>207 Multi-Status</code>, etc.
     *
     * @param statusCode an <code>int</code> value
     * @return a <code>String</code>
     */
    public static String getStatusMessage(int statusCode) {

        StringBuilder message = new StringBuilder("HTTP/");
        message.append(WebdavConstants.HTTP_VERSION_USED).append(" ");
        message.append(statusCode).append(" ");
        message.append(HttpUtil.getStatusMessage(statusCode));
        
        return message.toString();
       
    }


    /**
     * WebDAV date format cache
     */
    private static final ReusableObjectCache<SimpleDateFormat> WEBDAV_DATE_FORMAT_CACHE
            = new ArrayStackCache<>(() -> {
                    SimpleDateFormat sdf = new SimpleDateFormat(WebdavConstants.WEBDAV_PROPERTY_DATE_VALUE_FORMAT, Locale.US);
                    sdf.setTimeZone(TimeZone.getTimeZone(WebdavConstants.WEBDAV_PROPERTY_DATE_VALUE_TIMEZONE));
                    return sdf;
                }, 4);

    /**
     * Utility method for parsing date values of controlled properties for WebDAV.
     */
    public static Date parsePropertyDateValue(String dateValue) throws ParseException {
       final SimpleDateFormat parser = WEBDAV_DATE_FORMAT_CACHE.getInstance();
       try {
            return parser.parse(dateValue);
       } finally {
           WEBDAV_DATE_FORMAT_CACHE.putInstance(parser);
       }
    }

    /**
     * Utility method for formatting date values of controlled properties for WebDAV.
     */
    public static String formatPropertyDateValue(Date date) {
        SimpleDateFormat formatter = WEBDAV_DATE_FORMAT_CACHE.getInstance();
        try {
            return formatter.format(date);
        } finally {
            WEBDAV_DATE_FORMAT_CACHE.putInstance(formatter);
        }
    }

}
