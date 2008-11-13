/* Copyright (c) 2008, University of Oslo, Norway
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
package org.vortikal.web.decorating;

import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.vortikal.text.html.HtmlContent;
import org.vortikal.text.html.HtmlText;

public class StaticTextComponent implements HtmlDecoratorComponent {
    
    private StringBuilder content;

    public StaticTextComponent(StringBuilder content) {
        this.content = content;
    }
    
    public void render(DecoratorRequest request, DecoratorResponse response)
        throws Exception {
        
        Writer out = response.getWriter();
        out.write(this.content.toString());
        out.close();
    }

    public List<HtmlContent> render(DecoratorRequest request) throws Exception {
        List<HtmlContent> result = new ArrayList<HtmlContent>();
        result.add(new HtmlText() {
            public String getContent() {
                return content.toString();
            }
        });
        return result;
    }
    
    public String getNamespace() {
        return null;
    }

    public String getName() {    
        return "StaticText";
    }

    public String getDescription() {
        return "";
    }

    public Map<String, String> getParameterDescriptions() {
        return new HashMap<String, String>();
    }

    public String toString() {
        return getName();
    }

    public StringBuilder getBuffer() {
        return this.content;
    }

}
