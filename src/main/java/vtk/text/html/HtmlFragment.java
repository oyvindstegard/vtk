/* Copyright (c) 2007,2014-2015, University of Oslo, Norway
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
import java.util.List;

import vtk.util.text.TextUtils;


public final class HtmlFragment {

    private List<HtmlContent> content;
    
    public HtmlFragment(List<HtmlContent> content) {
        if (content == null) {
            throw new IllegalArgumentException("Constructor argument cannot be NULL");
        }
        this.content = content;
    }

    public List<HtmlContent> getContent() {
        return Collections.unmodifiableList(this.content);
    }

    public void filter(HtmlPageFilter filter) {
        this.content = HtmlPage.filterContent(this.content, filter);
    }
    
    public String getStringRepresentation() {
        StringBuilder result = new StringBuilder();
        for (HtmlContent c : this.content) {
            String stringContent;
            if (c instanceof EnclosingHtmlContent) {
                stringContent = ((EnclosingHtmlContent) c).getEnclosedContent();
            } else {
                stringContent = c.getContent();
            }
            result.append(TextUtils.removeUnprintables(stringContent));
        }
        return result;
    }
}
