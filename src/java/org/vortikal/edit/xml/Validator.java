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
package org.vortikal.edit.xml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/** Simple validation helper
 *
 */
public class Validator {

    private static Log logger = LogFactory.getLog(Validator.class);
    
    /** Validates a JDOM {@link Document} throwing appropriate exceptions when failing
     * @param document
     * @throws IOException
     * @throws JDOMException
     */
    public void validate(Document document) throws IOException, XMLEditException {
        Format format = Format.getRawFormat();
        format.setLineSeparator("\n");

        XMLOutputter xmlOutputter = new XMLOutputter();
        xmlOutputter.setFormat(format);
        
        SAXBuilder builder = 
            new SAXBuilder("org.apache.xerces.parsers.SAXParser");
        builder.setValidation(true);

        /* turn on schema support */
        builder.setFeature("http://apache.org/xml/features/validation/schema",
                true);
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            xmlOutputter.output(document, outputStream);
            InputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
            builder.build(inputStream);
        } catch (JDOMException e) {
            logger.error("Document not validating:\n" + xmlOutputter.outputString(document));
            throw new XMLEditException("The document isn't valid", e);
        } 
    }
}
