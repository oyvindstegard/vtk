/* Copyright (c) 2012, University of Oslo, Norway
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
package vtk.text.html;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.w3c.tidy.Tidy;

import com.googlecode.htmlcompressor.compressor.HtmlCompressor;

import vtk.util.io.NullOutputStream;

/**
 * XXX Should also handle inputstreams and doms
 */
public class HtmlDigester {

    private static final Pattern TAG_PATTERN = Pattern.compile("<.*?>");

    private static final int DEFAULT_TRUNCATE_LIMIT = 1000;
    private static final String DEFAULT_TAIL = "...";

    /**
     * Compress the html
     */
    public String compress(String html) {
        HtmlCompressor compressor = new HtmlCompressor();
        compressor.setRemoveIntertagSpaces(true);
        return compressor.compress(html);
    }

    public String truncateHtml(String html) {
        return this.truncateHtml(html, DEFAULT_TRUNCATE_LIMIT);
    }

    /**
     * Truncate supplied html to within the given limit. Append tail to
     * truncated result and use JTidy to close removed tags.
     */
    public String truncateHtml(String html, int limit) {

        if (html == null) {
            throw new IllegalArgumentException("Must supply html to truncate");
        }

        if (limit < 0 || limit < DEFAULT_TAIL.length()) {
            throw new IllegalArgumentException("Limit is too small");
        }

        if (html.length() < limit) {
            return html;
        }

        // Compress before truncating. If within limits, no need to remove
        // content and truncate
        String compressed = this.compress(html);
        if (compressed.length() < limit) {
            return compressed;
        }

        int processedLimit = limit;
        int tryCount = 0;

        // Worst case, make 3 attempts
        while (tryCount <= 3) {

            // Be greedy. Assume that half of the tags removed will be added
            // back during sanitization (tags are closed and made valid) ->
            // leave room for them by further reducing limit
            String removed = compressed.substring(processedLimit, compressed.length());
            int removedTagsLength = this.getRemoveTagsLength(removed);
            int truncationLimit = processedLimit - (removedTagsLength / 2);
            // Leave room for the tail
            truncationLimit -= DEFAULT_TAIL.length();

            // We were too greedy, we ended up removing everything. Go with half
            // the original limit and hope
            if (truncationLimit < 0) {
                truncationLimit = processedLimit / 2;
            }
            String truncated = compressed.substring(0, truncationLimit);

            // If html was truncated in the middle of a tag, remove what remains
            // of that tag
            if (truncated.lastIndexOf("<") > truncated.lastIndexOf(">")) {
                truncated = truncated.substring(0, truncated.lastIndexOf("<"));
            }

            // Add the tail
            truncated = this.addTail(truncated, DEFAULT_TAIL);

            String sanitizedTruncated = this.sanitizeTruncated(truncated);

            if (sanitizedTruncated.length() <= limit) {
                return sanitizedTruncated;
            }

            // Try again with a ~5% shorter limit
            processedLimit -= (limit / 20);

        }

        return null;

    }

    private int getRemoveTagsLength(String removed) {
        Matcher m = TAG_PATTERN.matcher(removed);
        int removedTagsLength = 0;
        while (m.find()) {
            removedTagsLength += m.group().length();
        }
        return removedTagsLength;
    }

    private String addTail(String truncated, String tail) {

        if (truncated.endsWith(">")) {
            List<String> endTags = new ArrayList<String>();
            while (truncated.endsWith(">")) {
                String endTag = truncated.substring(truncated.lastIndexOf("<"), truncated.length());
                endTags.add(endTag);
                truncated = truncated.substring(0, truncated.lastIndexOf("<"));
            }
            truncated = truncated.trim();
            if (!truncated.equals("")) {
                truncated = truncated.concat(tail);
                Collections.reverse(endTags);
                truncated = truncated.concat(StringUtils.join(endTags, ""));
            } else {
                truncated = truncated.concat(StringUtils.join(endTags, ""));
                truncated = truncated.concat(tail);
            }
            return truncated;
        }

        return truncated.trim().concat(tail);
    }

    private String sanitizeTruncated(String truncated) {
        
        // XXX Setup/configuration? Add tags without attributes... e.g. <li
        // style...>? Print body only?
        Tidy tidy = new Tidy();
        tidy.setPrintBodyOnly(true);
        tidy.setQuiet(true);
        tidy.setOnlyErrors(false);
        tidy.setShowWarnings(false);
        tidy.setErrout(new PrintWriter(NullOutputStream.INSTANCE));
        tidy.setMakeClean(true);
        tidy.setDropFontTags(true);
        tidy.setDropProprietaryAttributes(true);
        tidy.setInputEncoding("utf-8");
        tidy.setOutputEncoding("utf-8");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteArrayInputStream bais = new ByteArrayInputStream(truncated.getBytes());
        tidy.parseDOM(new BufferedInputStream(bais), baos);

        String sanitized = null;
        try {
            sanitized = new String(baos.toByteArray(), "utf-8");
        } catch (UnsupportedEncodingException e) {
            // XXX log?
            sanitized = new String(baos.toByteArray());
        }

        // Compress result
        sanitized = this.compress(sanitized);

        return sanitized;
    }

}
