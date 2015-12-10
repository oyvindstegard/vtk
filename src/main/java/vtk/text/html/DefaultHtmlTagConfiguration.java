/* Copyright (c) 2009, University of Oslo, Norway
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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


public final class DefaultHtmlTagConfiguration {

    private static final Set<String> compositeTags = new HashSet<String>();
    private static final Set<String> emptyTags = new HashSet<String>();
    
    static {
        compositeTags.add("button");
        compositeTags.add("pre");
        compositeTags.add("b");
        compositeTags.add("address");
        compositeTags.add("map");
        compositeTags.add("thead");
        compositeTags.add("tfoot");
        compositeTags.add("tbody");
        compositeTags.add("fieldset");
        compositeTags.add("colgroup");
        compositeTags.add("optgroup");
        compositeTags.add("small");
        compositeTags.add("big");
        compositeTags.add("i");
        compositeTags.add("tt");
        compositeTags.add("em");
        compositeTags.add("acronym");
        compositeTags.add("strong");
        compositeTags.add("code");
        compositeTags.add("samp");
        compositeTags.add("kbd");
        compositeTags.add("var");
        compositeTags.add("iframe");
        compositeTags.add("noscript");
        compositeTags.add("blockquote");
        compositeTags.add("strike");
        compositeTags.add("s");
        compositeTags.add("dd");
        compositeTags.add("dl");
        
        compositeTags.add("figure");
        compositeTags.add("figcaption");
        compositeTags.add("audio");
        compositeTags.add("video");
        
        emptyTags.add("br");
        emptyTags.add("area");
        emptyTags.add("link");
        emptyTags.add("img");
        emptyTags.add("param");
        emptyTags.add("hr");
        emptyTags.add("input");
        emptyTags.add("col");
        emptyTags.add("base");
        emptyTags.add("meta");
        emptyTags.add("source");
        emptyTags.add("esi:include");
    }

    public static Set<String> compositeTags() {
        return Collections.unmodifiableSet(compositeTags);
    }
    
    public static Set<String> emptyTags() {
        return Collections.unmodifiableSet(emptyTags);
    }
}
