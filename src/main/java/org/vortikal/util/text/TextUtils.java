/* Copyright (c) 2006, 2009 University of Oslo, Norway
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
package org.vortikal.util.text;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.lang.WordUtils;

public class TextUtils {

    private static final char[] HEX = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f'
        };

    public static char[] toHex(byte[] buffer) {
        char[] result = new char[buffer.length * 2];
        for (int i = 0; i < buffer.length; i++) {
            result[i << 1] = HEX[(buffer[i] & 0xF0) >>> 4];
            result[(i << 1)+1] = HEX[buffer[i] & 0x0F];
        }
        return result;
    }
    
    /**
     * Extracts a field from a string using the character <code>,</code> as field delimiter.
     * 
     * @param string the string in question
     * @param name   the name of the field wanted
     * @return the value of the field, or <code>null</code> if not found.
     */
    public static String extractField(String string, String name) {
        return extractField(string, name, ",");
    }


    /**
     * Extracts a field from a string using a given field delimiter.
     * 
     * @param string the string in question
     * @param name the name of the field wanted
     * @param fieldDelimiter the field delimiter
     * @return the value of the field, or <code>null</code> if not found.
     */
    public static String extractField(String string, String name, String fieldDelimiter) {

        int pos = string.indexOf(":") + 1;
        if (pos == -1 || pos >= string.length() - 1) {
            return null;
        }

        StringTokenizer tokenizer = new StringTokenizer(string.substring(pos).trim(), fieldDelimiter);

        while (tokenizer.hasMoreTokens()) {

            String token = tokenizer.nextToken().trim();

            if (token.startsWith(name + "=\"")) {
                int startPos = token.indexOf("\"") + 1;
                int endPos = token.indexOf("\"", startPos);

                if (startPos > 0 && endPos > startPos) {
                    return token.substring(startPos, endPos);
                }
            } else if (token.startsWith(name + "=")) {
                int startPos = token.indexOf("=") + 1;
                if (startPos > 0) {
                    return token.substring(startPos);
                }
            }
        }
        return null;
    }


    /**
     * Removes duplicates in a String between delimiter and capitalize, and return spaces and delimiter
     * 
     * @param string
     *            string in question
     * @param stringDelimiter
     *            the splitter for the string (examples: "," "." "-" )
     * 
     * @return the capitalized string without duplicates
     */
    public static String removeDuplicatesIgnoreCaseCapitalized(String string, String stringDelimiter) {
        return removeDuplicatesIgnoreCase(string, stringDelimiter, false, false, true);
    }


    /**
     * Removes duplicates in a String between delimiter, and return spaces and delimiter
     * 
     * @param string
     *            string in question
     * @param stringDelimiter
     *            the splitter for the string (examples: "," "." "-" )
     * 
     * @return the string without duplicates
     */
    public static String removeDuplicatesIgnoreCase(String string, String stringDelimiter) {
        return removeDuplicatesIgnoreCase(string, stringDelimiter, false, false, false);
    }


    /**
     * Removes duplicates in a String between delimiter
     * 
     * @param string
     *            string in question
     * @param stringDelimiter
     *            the splitter for the string (examples: "," "." "-" )
     * @param removeSpaces
     *            remove spaces between tokens in return String
     * @param removeDelimiter
     *            remove delimiter between tokens in return String
     * @param capitalizeWords
     *            capitalize words between delimiter
     * 
     * @return the string without duplicates
     */
    public static String removeDuplicatesIgnoreCase(String string, String stringDelimiter, boolean removeSpaces,
            boolean removeDelimiter, boolean capitalizeWords) {

        StringTokenizer tokens = new StringTokenizer(string, stringDelimiter, false);
        Set<String> set = new HashSet<String>(tokens.countTokens() + 10);

        int count = 0;
        StringBuilder noDupes = new StringBuilder();
        while (tokens.hasMoreTokens()) {
            String token = tokens.nextToken().trim();
            if (set.add(token.toLowerCase())) {
                if (count++ > 0) {
                    if (!removeDelimiter) {
                        noDupes.append(stringDelimiter);
                    }
                    if (!removeSpaces) {
                        noDupes.append(" ");
                    }
                }
                if (capitalizeWords) {
                    token = WordUtils.capitalize(token);
                }
                noDupes.append(token);
            }
        }
        return noDupes.toString();
    }
}
