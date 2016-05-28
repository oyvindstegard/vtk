/* Copyright (c) 2006, University of Oslo, Norway
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

package vtk.web.actions.convert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import vtk.repository.Namespace;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.tidy.Tidy;
import vtk.util.io.IO;


/**
 * Transformer using jTidy to tidy up HTML/XHTML on the provided input
 * stream. The HTML is transformed to XHTML 1.0 Transitional/utf-8.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li> <code>generatedContentType</code> - content type to generate
 *        (default "text/html")</li>
 *   <li> <code>insertedCssReference</code> - URI (to compulsory CSS
 *        stylesheet to be added to tidy'ed document</li>
 * </ul>
 */
public class JTidyTransformer implements Filter { 
    
    private final static String MINIMAL_DOCUMENT = 
        "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
        + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title></title>"
        + "</head><body></body></html>";

    private final static String DEFAULT_ENCODING = "utf-8";
    
    private static boolean tidyMark = false;
    private static boolean makeClean = true;
    private static boolean smartIndent = true;
    private static boolean showWarnings = false;
    private static boolean quiet = true;
    private static boolean xhtml = true;
    private static String doctype = "transitional";
    private String generatedContentType = "text/html";
    private String insertedCssReference;
    
    
    public void setGeneratedContentType(String generatedContentType) {
        this.generatedContentType = generatedContentType;
    }
    
    public void setInsertedCssReference(String insertedCssReference) {
        this.insertedCssReference = insertedCssReference;
    }
    
    
    public InputStream transform(InputStream inStream, String characterEncoding) throws Exception {
        Tidy tidy = new Tidy();

        // Setting up Tidy (default) output
        tidy.setTidyMark(tidyMark);
        tidy.setMakeClean(makeClean);
        tidy.setSmartIndent(smartIndent);
        tidy.setShowWarnings(showWarnings);
        tidy.setXHTML(xhtml);
        tidy.setDocType(doctype);
        tidy.setQuiet(quiet);
        tidy.setInputEncoding(DEFAULT_ENCODING);
        tidy.setOutputEncoding(DEFAULT_ENCODING);

        if (null == characterEncoding ) {
            characterEncoding = DEFAULT_ENCODING;
        } else if ("null".equals(characterEncoding.toLowerCase()) || "".equals(characterEncoding)) {
            characterEncoding = DEFAULT_ENCODING;
        }

        // Buffer the input stream:
        byte[] buffer = IO.read(inStream).perform();

        // Convert stream to utf-8 encoding if necessary:
        if (!DEFAULT_ENCODING.equals(characterEncoding)) {
            String s = new String(buffer, characterEncoding);
            buffer = s.getBytes(DEFAULT_ENCODING);
        }

        if ("".equals(new String(buffer, DEFAULT_ENCODING).trim())) {
            buffer = MINIMAL_DOCUMENT.getBytes(DEFAULT_ENCODING);
        }

        InputStream bufferedStream = new ByteArrayInputStream(buffer);
        tidy.setInputStreamName(bufferedStream.getClass().getName());
        Document document = tidy.parseDOM(bufferedStream, null);

        // Handle (re)setting of doctype and encoding meta-tag
        alterContentTypeMetaElement(document);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        tidy.pprint(document, outputStream);

        byte[] byteArrayBuffer = outputStream.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(byteArrayBuffer);

        outputStream.close();
        bais.reset(); // must reset buffer pointer to [0]

        return bais;
    }
    
    
    public InputStream transform(InputStream inStream, Resource resource) throws Exception {
        Tidy tidy = new Tidy();

        // Setting up Tidy (default) output
        tidy.setInputStreamName(inStream.getClass().getName());
        tidy.setTidyMark(tidyMark);
        tidy.setMakeClean(makeClean);
        tidy.setSmartIndent(smartIndent);
        tidy.setShowWarnings(showWarnings);
        // tidy.setOnlyErrors(onlyErrors); // If set TRUE, then only error
        // messages are written to the
        // OutputStream (i.e. no file
        // content is written)
        tidy.setQuiet(quiet);
        tidy.setXHTML(xhtml);
        tidy.setDocType(doctype);
        tidy.setInputEncoding(DEFAULT_ENCODING);
        tidy.setOutputEncoding(DEFAULT_ENCODING);

        byte[] buffer = IO.read(inStream).perform();
        String s = new String(buffer, resource.getCharacterEncoding());
        if ("".equals(s.trim())) {
            s = MINIMAL_DOCUMENT;
        }
        InputStream newStream = IO.stringStream(s, DEFAULT_ENCODING);

        Document document = tidy.parseDOM(newStream, null);
        alterContentTypeMetaElement(document);
        if (this.insertedCssReference != null && !"".equals(this.insertedCssReference.trim())) {
            insertCssReference(document, this.insertedCssReference);
        }

        resource.removeProperty(Namespace.DEFAULT_NAMESPACE, 
                PropertyType.CHARACTERENCODING_USER_SPECIFIED_PROP_NAME);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        tidy.pprint(document, outputStream);

        byte[] byteArrayBuffer = outputStream.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(byteArrayBuffer);

        outputStream.close();
        bais.reset(); // must reset buffer pointer to [0]


        return bais;
    }

    
    private void insertCssReference(Document doc, String cssReference) {
        NodeList headNodes = doc.getElementsByTagName("head");
        Node head = headNodes.item(0);
        if (head != null) {
            Element link = doc.createElement("link");
            link.setAttribute("type", "text/css");
            link.setAttribute("rel", "stylesheet");
            link.setAttribute("href", cssReference);
            head.appendChild(link);
        }
    }
    

    private void alterContentTypeMetaElement(Document doc) {

        NodeList headNodes = doc.getElementsByTagName("head");
        Node head = headNodes.item(0);
        Node remove = null;

        NodeList metaElements = doc.getElementsByTagName("meta");
        for (int i = 0; i < metaElements.getLength(); i++) {
            Node meta = metaElements.item(i);

            if (meta.hasAttributes()) {
                NamedNodeMap attrMap = meta.getAttributes();
                Node httpEquivAttr = attrMap.getNamedItem("http-equiv");

                if (httpEquivAttr != null) {
                    String httpEquiv = httpEquivAttr.getNodeValue();

                    if (httpEquiv!= null && "Content-Type".toLowerCase().equals(httpEquiv.toLowerCase())) {
                        remove = meta;
                        break;
                    }
                }
            }
        }
        if (remove != null) {
            remove.getParentNode().removeChild(remove);
        }
        Element meta = doc.createElement("meta");
        meta.setAttribute("http-equiv", "Content-Type");
        meta.setAttribute("content", this.generatedContentType + ";charset=utf-8");
        head.appendChild(meta);
    }
    
}
