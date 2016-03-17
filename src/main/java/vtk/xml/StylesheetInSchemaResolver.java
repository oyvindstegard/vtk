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


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.xpath.XPath;
import org.springframework.beans.factory.annotation.Required;


/**
 * Stylesheet resolver that looks in the xsd annotation element for
 * <code>view/xsl</code> references
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>schemaRegistry</code> - the XML {@link
 *   XmlSchemaRegistry schema registry} used to look up schema
 *   references
 *   <li><code>elementXPath</code> - the XPath expression used to look
 *   for XSLT references inside the XML schema document. The default
 *   expression is
 *   <code>/xsd:schema/xsd:annotation/xsd:appinfo/view/xsl</code>.
 * </ul>
 */
public class StylesheetInSchemaResolver implements StylesheetReferenceResolver {

    private final Namespace XSD_NAMESPACE = 
        Namespace.getNamespace("xsd", "http://www.w3.org/2001/XMLSchema");

    private final Namespace XSI_NAMESPACE = 
        Namespace.getNamespace("xsi",
                               "http://www.w3.org/2001/XMLSchema-instance");

    private static Log logger = LogFactory.getLog(StylesheetInSchemaResolver.class);
    
    private XmlSchemaRegistry schemaRegistry;
    private String elementXPath = "/xsd:schema/xsd:annotation/xsd:appinfo/view/xsl";
    

    public void setElementXPath(String elementXPath) {
        this.elementXPath = elementXPath;
    }

    @Required
    public void setSchemaRegistry(XmlSchemaRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    @Override
    public String getStylesheetIdentifier(Document document) {

        String docType = document.getRootElement().getAttributeValue(
            "noNamespaceSchemaLocation", this.XSI_NAMESPACE);

        if (docType == null) {
            return null;
        }

        try {
            Document schemaDoc = this.schemaRegistry.getXMLSchema(docType);
            XPath xPath = XPath.newInstance(this.elementXPath);
            xPath.addNamespace(this.XSD_NAMESPACE);
            Element e = (Element) xPath.selectSingleNode(schemaDoc);
            if (e == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Cannot find stylesheet reference for document " + 
                                 document + " in schema '" + docType + "'");
                }
                return null;
            }
            return e.getTextNormalize();
            
        }
        catch (Exception e) {            
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to find stylesheet reference for document " +
                             document + " in schema '" + docType + "'", e);
            }
            return null;
        }
    }





}
