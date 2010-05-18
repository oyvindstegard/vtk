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
package org.vortikal.repository.resourcetype.property;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.vortikal.repository.Property;
import org.vortikal.repository.PropertyEvaluationContext;
import org.vortikal.repository.PropertyEvaluationContext.Type;
import org.vortikal.repository.resourcetype.PropertyEvaluator;
import org.vortikal.util.io.StreamUtil;
import org.vortikal.util.repository.MimeHelper;

public class ContentTypeEvaluator implements PropertyEvaluator {

    private static final String X_VORTEX_COLLECTION = "application/x-vortex-collection";

    // {content-type -> {regexp -> new-content-type}}
    // Example: "{text/plain" -> {"\\<\\?php" -> "application/php"}}
    private Map<String, Map<Pattern, String>> contentPeekRegexps;
    private int regexpChunkSize = 1024;
    private Charset peekCharacterEncoding = Charset.forName("utf-8");
    
    public boolean evaluate(Property property, PropertyEvaluationContext ctx) throws PropertyEvaluationException {
        Type evalType = ctx.getEvaluationType();

        if (evalType == Type.Create) { 
            if (ctx.isCollection()) {
                property.setStringValue(X_VORTEX_COLLECTION);
                return true;
            }
            String guessedContentType = MimeHelper.map(ctx.getNewResource().getName());
            property.setStringValue(guessedContentType);
            return true;
        } else if (evalType == Type.ContentChange && this.contentPeekRegexps != null) {
            String guessedContentType = MimeHelper.map(ctx.getNewResource().getName());
            property.setStringValue(guessedContentType);
            if (this.contentPeekRegexps.containsKey(guessedContentType)) {
                try {
                    InputStream inputStream = ctx.getContent().getContentInputStream();
                    byte[] buffer = StreamUtil.readInputStream(inputStream, this.regexpChunkSize);
                    String chunk = new String(buffer, this.peekCharacterEncoding.name());
                    Map<Pattern, String> mapping = this.contentPeekRegexps.get(guessedContentType);
                    for (Pattern pattern: mapping.keySet()) {
                        Matcher m = pattern.matcher(chunk);
                        boolean match = m.find();
                        if (match) {
                            // XXX: temporary hack:
                            if ("application/json".equals(mapping.get(pattern))) {
                                try {
                                    ctx.getContent().getContentRepresentation(net.sf.json.JSONObject.class);
                                    property.setStringValue(mapping.get(pattern));
                                    return true;
                                } catch (Exception e) { }
                            }
                        }
                    }
                } catch (Throwable t) {
                }
            }
        }
        return true;
    }

    public void setContentPeekRegexps(Map<String, Map<String, String>> contentPeekRegexps) {
        if (contentPeekRegexps != null) {
            this.contentPeekRegexps = new HashMap<String, Map<Pattern, String>>();
            for (String contentType: contentPeekRegexps.keySet()) {
                Map<String, String> mapping = contentPeekRegexps.get(contentType);
                Map<Pattern, String> internal = this.contentPeekRegexps.get(contentType);
                if (internal == null) {
                    internal = new HashMap<Pattern, String>();
                    this.contentPeekRegexps.put(contentType, internal);
                }
                for (String regexp: mapping.keySet()) {
                    Pattern p = Pattern.compile(regexp, Pattern.MULTILINE | Pattern.DOTALL);
                    internal.put(p, mapping.get(regexp));
                }
            }
        }
    }

}
