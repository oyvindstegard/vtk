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
package org.vortikal.edit.xml;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jdom.Element;
import org.jdom.ProcessingInstruction;
import org.springframework.web.servlet.ModelAndView;




/**
 * Controller that prepares ("expands") an element for editing.
 *
 * @version $Id$
 */
public class EditController extends AbstractXmlEditController {



    protected ModelAndView handleRequestInternal(
        HttpServletRequest request, HttpServletResponse response,
        EditDocument document, SchemaDocumentDefinition documentDefinition) {
        
        Map model = new HashMap();
        
        String mode = document.getDocumentMode();

        if (mode.equals("default")) {

            String path = request.getParameter("path");

            Element element = document.findElementByPath(path);
            document.setEditingElement(element);
         
            document.setClone((Element)element.clone());
            
            document.setDocumentMode("edit");
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Expanding focused element: " + path);
            }
            
            element.addContent(new ProcessingInstruction("expanded", "true"));
            documentDefinition.translateToEditingElement(element);
            
            return new ModelAndView(this.viewName, model);

        }
        return null;
    }
}
