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
package org.vortikal.web.view.wrapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * Content filter that merges the supplied text content into the
 * <code>&lt;head&gt;</code> element of the original content. An
 * example of such use is to include CSS stylesheet references in the
 * HTML output from another view.
 * <p>This filter also removes meta elements declaring charset
 * and title-elements from the original html, by default.

 * <p>Configurable properties:
 * <ul>
 *   <li><code>removeTitles</code> - default <code>true</code>
 *   <li><code>removeCharsets</code> - default <code>true</code>
 * </ul>
 */
public class HtmlHeadContentFilter
  extends AbstractViewProcessingTextContentFilter {

    private static Pattern HEAD_START_REGEXP =
        Pattern.compile("<\\s*head[^>]*>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static Pattern HEAD_END_REGEXP =
        Pattern.compile("<\\s*/\\s*head\\s*>",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    
    
    private static Pattern CHARACTER_ENCODING__REGEXP =
		Pattern.compile("((<\\s*meta[^>]+charset\\s*\\=\\s*)([\\w-]+)[^>]*>"
                                + "(\\s*</\\s*meta\\s*>)?)",
                                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static Pattern TITLE_REGEXP =
		Pattern.compile("<\\s*title[^>]*>[^<]*(</\\s*title\\s*>)?",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private boolean removeTitles = true;
    private boolean removeCharsets = true;
        
    protected String processInternal(String content, String head) throws Exception {

        // Get the head content to look for a charset meta element there.
        Matcher headStartMatcher = HEAD_START_REGEXP.matcher(content);
        Matcher headEndMatcher = HEAD_END_REGEXP.matcher(content);
        String headContent = null;
        int startHead, endHead;
        
        if (! (headStartMatcher.find(0) && headEndMatcher.find(0))) {
            // No head found, nothing to do
            return content;
        }

        startHead = headStartMatcher.end();
        endHead = headEndMatcher.start();
        
        headContent = content.substring(startHead, endHead);

        // Look for title element
        Matcher titleMatcher = TITLE_REGEXP.matcher(headContent);
            
        if (this.removeTitles && titleMatcher.find(0)) {
            headContent = titleMatcher.replaceAll("");
        }
            
        // Look for a meta element which declares a charset.
        Matcher charsetMatcher = CHARACTER_ENCODING__REGEXP.matcher(headContent);
	
        if (this.removeCharsets && charsetMatcher.find(0)) {
            if (this.debug && this.logger.isDebugEnabled()) {
                this.logger.debug("found charset content, will remove");
            }
            headContent = charsetMatcher.replaceAll("");
        }
            
        if (this.debug && this.logger.isDebugEnabled()) {
            this.logger.debug("New head content:\n" + headContent);
        }
        
        return content.substring(0, headStartMatcher.end())
            + headContent + head + content.substring(headEndMatcher.start());
    }
    
    
    public void setRemoveCharsets(boolean removeCharsets) {
        this.removeCharsets = removeCharsets;
    }

    public void setRemoveTitles(boolean removeTitles) {
        this.removeTitles = removeTitles;
    }
}
