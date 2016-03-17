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
package vtk.xml;

import java.util.Iterator;
import java.util.List;

import org.jdom.Document;
import org.jdom.ProcessingInstruction;

/**
 * Stylesgeet resolver that gets XSLT stylesheets from
 * "xml-stylesheet" processing instructions in XML documents.
 */
public class StylesheetInDocumentResolver implements StylesheetReferenceResolver {

    @Override
    public String getStylesheetIdentifier(Document document) {
        List<?> content = document.getContent();
        
        for (Iterator<?> i = content.iterator(); i.hasNext();) {
            Object o = i.next();

            if (o instanceof ProcessingInstruction) {

                ProcessingInstruction pi = (ProcessingInstruction) o;
                if ("xml-stylesheet".equals(pi.getTarget()) &&
                    "text/xsl".equals(pi.getPseudoAttributeValue("type"))) {

                    String url = pi.getPseudoAttributeValue("href");

                    if (url != null) {
                        return url;
                    }
                }
            }
        }
        return null;
    }
}
