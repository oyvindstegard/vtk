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
package vtk.edit.xml;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.jdom.Element;
import org.jdom.ProcessingInstruction;


/**
 * Controller that inserts a new element at a specified location in
 * the document.
 *
 */
public class NewElementAtController implements ActionHandler {

    public Map<String, Object> handle(HttpServletRequest request,
            EditDocument document,
            SchemaDocumentDefinition documentDefinition) throws XMLEditException {

        Map<String, Object> model = new HashMap<>();
        
        String mode = document.getDocumentMode();
        String con = request.getParameter("cont");

        if (mode.equals("default")
            || (mode.equals("newElement") && "true".equals(con))) {

            String elementName = (mode.equals("default")) ? 
                    request.getParameter("name") : document.getNewElementName();
                    
            if (elementName == null || elementName.equals("")) {
                Util.setXsltParameter(model,"ERRORMESSAGE", 
                "NEW_ELEMENT_AT_MISSING_ELEMENT_NAME");
                return model;
            }
                    
            String path = request.getParameter("at");

            if (path == null) {
                Util.setXsltParameter(model,"ERRORMESSAGE", 
                        "NEW_ELEMENT_AT_MISSING_PATH_PARAMETER");
                return model;
            }

            Element element = new Element(elementName);
            document.putElementByPath(path, element);
            documentDefinition.buildElement(element);
            element.addContent(new ProcessingInstruction("expanded", "true"));

            document.setEditingElement(element);

            document.setDocumentMode("newElementAt");
            return model;
        } else if (mode.equals("newElement") && !"true".equals(con)) {
            document.setDocumentMode("default");
            document.setNewElementName(null);
            document.setEditingElement(null);
            return model;
        } else if (mode.equals("newElementAt")) {
            if ("true".equals(con)) {

                /* Add input values to element and save: */
                document.addContentsToElement(document.getEditingElement(),
                        Util.getRequestParameterMap(request), documentDefinition);
                document.setDocumentMode("default");
                document.resetEditingElement();
                
                try {
                    document.save(request);
                }
                catch (Exception e) {
                    throw new XMLEditException("Unable to save document", e);
                }

            } else {
                /* Cancel; remove the new element from the document */
                document.setDocumentMode("default");
                document.getEditingElement().detach();
                document.setEditingElement(null);
            }
            return model;
        }
        return null;
    }


}
