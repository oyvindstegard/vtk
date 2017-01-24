/* Copyright (c) 2004, 2012 University of Oslo, Norway
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
package vtk.util.repository;

import java.util.Locale;

/**
 * Utility methods for handling locales
 */
public class LocaleHelper {

    /**
     * Converting locale strings of the format
     * <code>language_country_variant</code> into {@link Locale} objects.
     */
    public static Locale getLocale(String localeString) {
        if (localeString == null) {
            return null;
        }

        localeString = localeString.trim();

        if (localeString.isEmpty()) {
            return null;
        }

        // FIXME: this is to get around the fact that the
        // content-language has been stored in the database using the
        // string 'unknown' when it should be null:
        // XXX: Remove
        if ("unknown".equals(localeString)) {
            return null;
        }

        String[] locale = localeString.split("_");
        switch (locale.length) {
        case 3:
            return new Locale(locale[0], locale[1], locale[2]);
        case 2:
            return new Locale(locale[0], locale[1]);
        case 1:
            return new Locale(locale[0]);
        default:
            return null;
        }
    }

    public static Locale getMessageLocalizationLocale(Locale providedResourceLocale) {
        if (providedResourceLocale == null) {
            return null;
        }
        final String localeString = providedResourceLocale.toString().trim().toLowerCase();
        if (localeString.isEmpty()) {
            return null;
        }
        if (localeString.contains("ny")) {
            return new Locale("nn");
        } else if (!localeString.contains("en")) {
            return new Locale("no");
        }
        return providedResourceLocale;
    }
    
    public static String getPreferredLang(Locale preferredLocale) {
        String lang = preferredLocale.toString().toLowerCase();
        if (lang.contains("ny")) {
            return "nn";
        }
        return preferredLocale.getLanguage();
    }


}
