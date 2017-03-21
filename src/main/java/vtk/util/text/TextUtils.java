/* Copyright (c) 2006, 2009, 2015 University of Oslo, Norway
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
package vtk.util.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.lang.WordUtils;

/**
 * Various text parsing and string utility functions.
 */
public class TextUtils {

    private static final char[] HEX = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f'
        };

    /**
     * Parsing flag indicating that values should be trimmed.
     */
    public static final int TRIM = 0x01;
    
    /**
     * Parsing flag indicating that empty string values should be discarded
     * from results.
     */
    public static final int DISCARD = 0x02;
    
    /**
     * Parsing flag indicating that invalid escape sequences should be ignored.
     */
    public static final int IGNORE_INVALID_ESCAPE = 0x04;
    
    /**
     * Parsing flag indicating that unescaped separators in value of key-value
     * pair should not cause an exception to be thrown.
     * @see #parseKeyValue(java.lang.String, char, int) 
     */
    public static final int IGNORE_UNESCAPED_SEP_IN_VALUE = 0x08;
    
    /**
     * Parsing flag indicating the backslash character shall be removed
     * from invalid escape sequences. The only way then to get a literal
     * backslash in the result is to provide two backslashes "\\".
     */
    public static final int UNESCAPE_INVALID_ESCAPE = 0x10;
    
    /**
     * Encode a single char in Unicde BMP as a Java Unicode escape sequence.
     * @param c input char
     * @return string on the form <code>"\\uXXXX"</code>, where XXXX are hexadecimal
     * digits, always zero-padded.
     */
    public static String toUnicodeEscape(char c) {
        char[] result = {'\\', 'u', 0, 0, 0, 0};
        result[2] = HEX[(c >>> 12) & 0xF];
        result[3] = HEX[(c >>> 8) & 0xF];
        result[4] = HEX[(c >>> 4) & 0xF];
        result[5] = HEX[c & 0xF];
        return new String(result);
    }
    
    /**
     * Render a byte array into a string of hexadecimal numbers.
     * @param buffer containing the bytes to convert
     * @return a character array of length 2 with hexadecimal digits representing the bytes.
     */
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
        Set<String> set = new HashSet<>(tokens.countTokens() + 10);

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
    
    /**
     * Delete all occurences of substring in input string.
     * 
     * @param input the input string
     * @param substring the substring to delete all occurences of in input string
     * @return the processed string
     */
    public static String deleteAll(String input, String substring) {
        return replaceAll(input, substring, "");
    }

    /**
     * Replace occurences of substring with another string. Can be used in
     * favour of String.replaceAll() when the string to be replaced does not
     * need to be a regular expression. This method will be faster.
     * 
     * @param input the string on which to perform replacement.
     * @param replace the string to replace with.
     * @param replacement the substring to replace
     * @throws NullPointerException if any parameter is <code>null</code>.
     * @return the string with the substitutions applied.
     */
    public static String replaceAll(String input, String replace, String replacement) {
        StringBuilder output = new StringBuilder(replacement.length() > replace.length() ? 
                                               (int)(input.length() * 1.5) : input.length());
        if (replace.isEmpty()) {
            output.append(replacement);
            for (int i=0; i<input.length(); i++){
                output.append(input.charAt(i));
                output.append(replacement);
            }
            return output.toString();
        }
        int p = 0, n;
        while ((n = input.indexOf(replace, p)) != -1) {
            output.append(input, p, n);
            p = n + replace.length();
            output.append(replacement);
        }
        if (p < input.length()) {
            output.append(input, p, input.length());
        }
        return output.toString();
    }
    
    /**
     * Unescape all escape sequences in string (strip one level of backslashes).
     * An escape sequence always consists of two characters on the form "\ &lt;anychar&gt;".
     * @param escaped The string to unescape
     * @return A string with one level of escaping backslashes stripped.
     */
    public static String unescape(String escaped) {
        char[] source = escaped.toCharArray();
        char[] dest = new char[source.length];
        int j = 0;
        for (int i = 0; i < source.length; i++) {
            if ((source[i] != '\\') || (i > 0 && source[i-1] == '\\')) {
                dest[j++]=source[i];
            }
        }
        return new String(dest, 0, j);
    }

    /**
     * Parse a string of character-separated values, with no parsing flags set.
     * @see #parseCsv(java.lang.String, char, int) 
     */
    public static String[] parseCsv(String input, char sep) {
        return parseCsv(input, sep, 0);
    }
    
    /**
     * Parse a string of character-separated values, optionally trimming each
     * value. The chosen separator character may be escaped using backslash '\\',
     * and any backslashes need to be escaped themselves.
     * 
     * Empty strings between consecutive separator characters are included
     * in results unless <code>discard</code> is <code>true</code>.
     * 
     * @param input the input string to parse.
     * @param sep the character to use as value separator.
     * @param flags Parsing flags.
     *        <ul><li>If {@link #TRIM} is set, then each value to be trimmed.
     *            <li>If {@link #DISCARD} is set, then empty string values
     *        will not to be included in result. When combined with {@link #TRIM}, empty
     *        values after trimming are discarded.
     *            <li>If {@link #IGNORE_INVALID_ESCAPE} is set, then invalid
     *                backslash escape sequences are ignored.
     *                The backslash character will be included in output
     *                in such cases, preceding the non-special character, unless
     *                the backslash appears at the very end of input.
     *            <li>If {@link #UNESCAPE_INVALID_ESCAPE} is set, then invalid
     *                backslash escape sequnces are ignored, and
     *                the backslash character will NOT be included
     *                such cases, only the character following it.
     *        </ul>
     * @return an array of strings containing the values.
     */
    public static String[] parseCsv(String input, char sep, int flags) {
        if (input == null) throw new IllegalArgumentException("null input");
        
        if (hasFlag(TRIM, flags) ? input.trim().isEmpty() : input.isEmpty()) {
            if (hasFlag(DISCARD, flags)) return new String[0];
            else return new String[] {""};
        }
        
        List<String> values = new ArrayList<String>();
        boolean escape = false;
        StringBuilder buf = new StringBuilder();
        for (int i=0; i<input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && escape) {
                buf.append('\\');
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == sep && escape) {
                buf.append(c);
                escape = false;
                continue;
            }
            if (escape) {
                if (hasFlag(IGNORE_INVALID_ESCAPE,flags) || hasFlag(UNESCAPE_INVALID_ESCAPE, flags)) {
                    if (!hasFlag(UNESCAPE_INVALID_ESCAPE,flags)) {
                        buf.append('\\');
                    }
                    escape = false;
                } else {
                    throw new IllegalArgumentException("Invalid escape sequence: '\\" 
                        + c + "' in value: " + input);
                }
            }
            if (c == sep) {
                String v = hasFlag(TRIM, flags) ? buf.toString().trim() : buf.toString();
                buf.setLength(0);
                if (!v.isEmpty() || !hasFlag(DISCARD, flags)) {
                    values.add(v);
                }
                if (i == input.length()-1) {
                    // Input ended with separator, be consistent
                    if (!hasFlag(DISCARD, flags)) values.add("");
                }
                continue;
            }
            buf.append(c);
        }
        if (escape && !hasFlag(IGNORE_INVALID_ESCAPE, flags)
                   && !hasFlag(UNESCAPE_INVALID_ESCAPE, flags)) {
            // Single escape char at end of input should trigger error
            throw new IllegalArgumentException(
                "Invalid escape char '\\' at end of key-value: '" + input + "'");
        }
        
        if (buf.length() > 0) {
            String v = hasFlag(TRIM, flags) ? buf.toString().trim() : buf.toString();
            if (!v.isEmpty() || !hasFlag(DISCARD, flags)) {
                values.add(v);
            }
        }
        
        return values.toArray(new String[values.size()]);
    }
    
    /**
     * Parses key-value with no parsing flags set.
     * @see #parseKeyValue(java.lang.String, char, int) 
     */
    public static String[] parseKeyValue(String input, char sep) {
        return parseKeyValue(input, sep, 0);
    }
    
    /**
     * Parses a string into a key and a value using a single character
     * as separator. The chosen separator character may be escaped using backslash
     * character '\\'. If no separator is found in input, then the returned string
     * array will have the entire input string as key part and <code>null</code>
     * as value part.
     * 
     * @param input The input string to parse.
     * @param sep The character to use as separator between key and value.
     * @param flags Set parsing flags. Valid flags:
     *        <ul><li>If {@link #TRIM} is set, then each value to be trimmed.
     *            <li>If {@link #IGNORE_INVALID_ESCAPE} is set, then
     *                then invalid backslash escape sequences are ignored.
     *                The backslash character will be included in output
     *                in such cases, preceding the non-special character, unless
     *                the backslash is at the very end of input.
     *            <li>If {@link #UNESCAPE_INVALID_ESCAPE} is set, then
     *                then invalid backslash escape sequences are ignored,
     *                and the backslash character in such sequnces will simply be removed.
     *            <li>If {@link #IGNORE_UNESCAPED_SEP_IN_VALUE} is set, then
     *             do not throw exception if unescaped separator characters
     *             are encountered in the value part.
     *        </ul>

     * @return A string array of length 2 with index 0 containing the key and index
     *         1 containing the value. Index 1 can contain <code>null</code>
     *         if there is no value.
     */
    public static String[] parseKeyValue(String input, char sep, int flags) {
        StringBuilder key = new StringBuilder();
        StringBuilder value = null;
        StringBuilder cur = key;
        boolean escape = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (cur == value && value == null) {
                value = new StringBuilder();
                cur = value;
            }
            if (c == '\\' && escape) {
                cur.append('\\');
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == sep && escape) {
                cur.append(c);
                escape = false;
                continue;
            }
            if (c == sep && cur == value) {
                if (!hasFlag(IGNORE_UNESCAPED_SEP_IN_VALUE, flags)) {
                    throw new IllegalArgumentException(
                        "Unescaped separator '" + sep + "' in  value: \"" + input + "\"");
                }
                cur.append(c);
                continue;
            }
            if (escape) {
                if (hasFlag(IGNORE_INVALID_ESCAPE, flags) || hasFlag(UNESCAPE_INVALID_ESCAPE, flags)) {
                    escape = false;
                    if (!hasFlag(UNESCAPE_INVALID_ESCAPE, flags)) {
                        cur.append('\\');
                    }
                } else {
                    throw new IllegalArgumentException("Invalid escape sequence: '\\" 
                        + c + "' in key-value: " + input);
                }
            }
            if (c == sep) {
                value = new StringBuilder();
                cur = value;
                continue;
            }
            cur.append(c);
        }
        if (escape && !hasFlag(IGNORE_INVALID_ESCAPE, flags)
                   && !hasFlag(UNESCAPE_INVALID_ESCAPE, flags)) {
            // Single escape char at end of input should trigger error
            throw new IllegalArgumentException(
                "Invalid escape char '\\' at end of key-value: '" + input + "'");
        }
        
        return new String[] { hasFlag(TRIM, flags) ? key.toString().trim() : key.toString(),
            value == null ? null : hasFlag(TRIM, flags) ? value.toString().trim() : value.toString() };
    }

    private static boolean hasFlag(int flag, int flags) {
        return (flag & flags) != 0;
    }
    
    /**
     * Join all strings into a new concatenated string.
     * @param strings
     * @param separator
     * @return concatenated string with separator in between elements. 
     */
    public static String join(String[] strings, String separator) {
        return join(Arrays.asList(strings), separator, false, false);
    }

    /**
     * Join all strings into a new concatenated string.
     * @param strings
     * @param separator
     * @param skipEmpty
     * @return concatenated string with separator in between elements. 
     */
    public static String join(String[] strings, String separator, boolean skipEmpty) {
        return join(Arrays.asList(strings), separator, skipEmpty, false);
    }

    /**
     * Join all strings into a new concatenated string.
     * @param strings
     * @param separator
     * @param skipEmpty
     * @param skipNull
     * @return concatenated string with separator in between elements. 
     */
    public static String join(String[] strings, String separator, boolean skipEmpty, boolean skipNull) {
        return join(Arrays.asList(strings), separator, skipEmpty, skipNull);
    }

    /**
     * Join all strings into a new concatenated string.
     * @param strings
     * @param separator
     * @return concatenated string with separator in between elements. 
     */
    public static String join(List<String> strings, String separator) {
        return join(strings, separator, false, false);
    }
    
    /**
     * Join all strings into a new concatenated string.
     * @param strings
     * @param separator
     * @param skipEmpty
     * @return concatenated string with separator in between elements. 
     */
    public static String join(List<String> strings, String separator, boolean skipEmpty) {
        return join(strings, separator, skipEmpty, false);
    }

    /**
     * Join all strings into a new concatenated string.
     * @param strings
     * @param separator
     * @param skipEmpty
     * @param skipNull
     * @return concatenated string with separator in between elements. 
     */
    public static String join(List<String> strings, String separator, boolean skipEmpty, boolean skipNull) {
        StringBuilder sb = new StringBuilder();
        boolean isAfterFirst = false;
        for (String string : strings) {
            boolean isSkip = (skipNull && string == null) || (skipEmpty && string.isEmpty());
            if (!isSkip) {
                if (isAfterFirst) {
                    sb.append(separator);
                }
                sb.append(string);
                isAfterFirst = true;
            }
        }
        return sb.toString();
    }

    /**
     * Only keep the safe to print characters and discard the rest.
     * Specifically, unknown control characters are removed.
     * @param content
     * @return content with harmful characters removed.
     */
    public static String removeUnprintables(String content) {
        if (content == null) return null;
        
        StringBuilder result = new StringBuilder(content.length());
        int length = content.length();
        for (int i = 0; i < length; i++) {
            char ch = content.charAt(i);
            if (ch >= 32 || ch == '\t' || ch == '\n' || ch == '\r') {
                result.append(ch);
            }
        }
        return result.toString();
    }

    /**
     * Tokenizes a string by splitting on whitespace and handling
     * of phrases, which will be kept as single tokens.
     *
     * <p>Whitespace inside phrases is preserved verbatim.
     *
     * <p>The characters <code>"</code> and <code>'</code>.are used to surround
     * text as a phrase.
     *
     * <p>Backslash may be used to escape pharse characters and whitespace outside
     * of phrases. Two consecutive backslashes must be present in input to get a single literal
     * backslash in an output token.
     *
     * <p>Unquoted characters next to start or end of a quoted phrase are included
     * in the phrase, like <code>"x'some phrase'"</code> will result in a single token: <code>"xsome phrase"</code>.
     * Directly adjacent phrases are also collapsed to a single phrase token.
     *
     * <p>Empty tokens are always discarded and not returned.
     *
     * @param text the text to tokenize
     * @return a mutable list of tokens
     */
    public static List<String> tokenizeWithPhrases(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean squotes = false;
        boolean dquotes = false;
        boolean esc = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\\' && !esc) {
                esc = true;
                continue;
            }
            
            if (Character.isWhitespace(c) && !esc) {
                if (squotes || dquotes) {
                    current.append(c);
                    continue;
                }
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            if (c == '"' && !esc) {
                if (dquotes) {
                    if (current.length() > 0
                            && i < text.length()-1 && Character.isWhitespace(text.charAt(i+1))) {
                        result.add(current.toString());
                        current.setLength(0);
                    }
                    dquotes = false;
                    continue;
                }
                if (!squotes) {
                    dquotes = true;
                    continue;
                }
            }
            if (c == '\'' && !esc) {
                if (squotes) {
                    if (current.length() > 0
                            && i < text.length()-1 && Character.isWhitespace(text.charAt(i+1))) {
                        result.add(current.toString());
                        current.setLength(0);
                    }
                    squotes = false;
                    continue;
                }
                if (!dquotes) {
                    squotes = true;
                    continue;
                }
            }

            esc = false;
            current.append(c);
        }

        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result;
    }

}
