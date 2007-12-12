/* Copyright (c) 2007, University of Oslo, Norway
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
package org.vortikal.edit.editor;

import java.io.ByteArrayInputStream;

import org.vortikal.text.html.HtmlContent;
import org.vortikal.text.html.HtmlElement;
import org.vortikal.text.html.HtmlPage;
import org.vortikal.text.html.HtmlPageFilter;
import org.vortikal.text.html.HtmlPageParser;

public class StructuredXHtmlEditDataBinder extends ResourceEditDataBinder {

    public StructuredXHtmlEditDataBinder(Object target, String objectName,
            HtmlPageParser htmlParser, HtmlPageFilter htmlPropsFilter) {
        super(target, objectName, htmlParser, htmlPropsFilter);
    }

    protected void parseContent(ResourceEditWrapper command,
            String suppliedContent) {
        HtmlPageParser parser = getHtmlParser();
        String encoding = command.getCharacterEncoding();
        try {
            ByteArrayInputStream is = new ByteArrayInputStream(suppliedContent.getBytes(encoding));
            HtmlPage page = parser.parse(is, encoding);

            String userTitle = command.getValueByName("userTitle");
            if (userTitle != null) {
                HtmlElement head = page.selectSingleElement("html.head");
                HtmlElement title = page.selectSingleElement("html.head.title");
                HtmlElement body = page.selectSingleElement("html.body");
                HtmlElement h1 = page.selectSingleElement("html.body.h1");
                
                if (head != null && body != null) {
                    
                    if (title == null) {
                        title = page.createElement("title");
                        head.addContent(title);
                    }
                    title.setChildNodes(new HtmlContent[]{page.createTextNode(userTitle)});
                    if (h1 == null || !h1.getContent().equals(userTitle)) {
                        h1 = page.createElement("h1");
                        body.addContent(0, h1);
                    } 
                    h1.setChildNodes(new HtmlContent[]{page.createTextNode(userTitle)});
                }
            }

            command.setContent(page);
            command.setContentChange(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
