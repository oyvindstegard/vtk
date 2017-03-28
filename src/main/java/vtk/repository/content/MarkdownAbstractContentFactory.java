/* Copyright (c) 2016, University of Oslo, Norway
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
package vtk.repository.content;

import java.io.InputStream;
import java.util.Collections;

import vtk.text.html.HtmlContent;
import vtk.text.html.HtmlElement;
import vtk.text.html.HtmlNodeFilter;
import vtk.text.html.HtmlPageParser;
import vtk.text.html.HtmlUtil;
import vtk.util.io.IO;
import vtk.util.text.Markdown;

public abstract class MarkdownAbstractContentFactory  {
        
    protected InfoCollector getInfoCollector(Markdown.Flavor flavor, InputStream content) 
    		throws Exception {
        String input = IO.readString(content, "utf-8").perform();
        String output = Markdown.html(input, flavor);
        HtmlPageParser htmlParser = new HtmlPageParser();
        InfoCollector collector = new InfoCollector();
        htmlParser.parseFragment(output, Collections.singletonList(collector));
        return collector;
    }
    
    protected static final class InfoCollector implements HtmlNodeFilter {
        
        private String header = null;
        int headerLevel = Integer.MAX_VALUE;
        private String summary = null;
        
        public String getHeader() {
            return header;
        }
        
        public String getSummary() {
            return summary;
        }

        @Override
        public HtmlContent filterNode(HtmlContent content) {
            if (content instanceof HtmlElement) {
                HtmlElement element = (HtmlElement) content;
                switch (element.getName()) {
                case "h1":
                    if (header == null || headerLevel > 1) {
                        header = HtmlUtil.flatten(element.getContent());
                        headerLevel = 1;
                    }
                    break;
                case "h2":
                    if (header == null || headerLevel > 2) {
                        header = HtmlUtil.flatten(element.getContent());
                        headerLevel = 2;
                    }
                    break;
                case "h3":
                    if (header == null || headerLevel > 3) {
                        header = HtmlUtil.flatten(element.getContent());
                        headerLevel = 3;
                    }
                    break;
                case "p":
                case "div":
                    if (summary == null) {
                        String s = element.getContent();
                        if (s != null && s.trim().length() > 0) {
                            summary = element.getContent();
                        }
                    }
                    break;
                default:
                    break;
                }
            }
            return content;
        }
    }
    
}
