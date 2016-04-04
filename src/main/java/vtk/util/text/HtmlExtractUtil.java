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
package vtk.util.text;

import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class with methods for extracting doctype and declared character
 * encoding from raw HTML content.
 */
public abstract class HtmlExtractUtil {

    private static Logger logger = LoggerFactory.getLogger(HtmlExtractUtil.class);
    private static final int NUM_HEAD_BYTES = 4096;
    private static final Pattern DOCTYPE_REGEXP =
            Pattern.compile("<\\s*!DOCTYPE\\s+([^>]+)>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HEAD_REGEXP =
            Pattern.compile("(<\\s*head.*?>)(.*)(<\\s*/\\s*head\\s*>)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CHARSET_REGEXP =
            Pattern.compile("(<\\s*meta[^>]+charset\\s*\\=\\s*)([\\w-]+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Tries to extract the HTML doctype from the first bytes of the content.
     * Note that this method does not validate that the doctype found is
     * actually a well-known doctype.
     *
     * @param html a <code>byte[]</code> sequence
     * @return the HTML doctype, or <code>null</code> if not found
     */
    public static String getDoctypeFromBody(byte[] html) {
        String doctype = null;
        int numBytes = Math.min(NUM_HEAD_BYTES, html.length);
        try {
            String content = new String(html, 0, numBytes, "iso-8859-1");
            Matcher matcher = DOCTYPE_REGEXP.matcher(content);
            if (matcher.find(0)) {
                doctype = matcher.group(1);
            }
        } catch (UnsupportedEncodingException e) {
            // Should never happen
        }
        return doctype;
    }

    /**
     * Tries to guess the HTML character encoding using regular expression
     * matching.
     *
     * @param html a <code>byte[]</code> value
     * @return a <code>String</code>
     */
    public static String getCharacterEncodingFromBody(byte[] html) {
        // Reads the first bytes of the content to look for the charset to use.
        int numBytes = Math.min(NUM_HEAD_BYTES, html.length);

        try {

            String content = new String(html, 0, numBytes, "iso-8859-1");

            // Get the head content to look for a charset meta element there.
            Matcher matcher = HEAD_REGEXP.matcher(content);
            String headContent = null;
            if (matcher.find(0)) {
                headContent = matcher.group(2);
            }

            // Look for charset meta element in head.
            if (headContent != null) {
                // Looks for a meta element which declares a charset.
                matcher = CHARSET_REGEXP.matcher(headContent);

                if (matcher.find(0)) {
                    String charset = matcher.group(2).toLowerCase();
                    if (logger.isDebugEnabled()) {
                        logger.debug("Found meta element with charset '" + charset + "'.");
                    }
                    return charset;
                }
            }
        } catch (UnsupportedEncodingException e) {
            logger.warn("Exception: Get charset, default encoding. This should not happen!!");
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Didn't find any charset meta in document.");
        }
        return null;
    }
}
