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
package vtk.repository.resourcetype.property;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.resourcetype.PropertyEvaluator;
import vtk.util.io.IO;



/**
 * Evaluator for XML character encoding.
 *
 * <p>Note: This class should ideally use some XML representation
 * class, such as JDOM Document. However, org.jdom.Documents do not
 * return XML declarations as processing instructions, and there is no
 * other way to obtain the character encoding using JDOM, so we are
 * forced to inspect the raw content.
 * 
 * <p>
 * Can also use javax.xml.stream.XMLEventReader to obtain the character encoding,
 * but regexp should work just as well..
 *
 */
public class XMLCharacterEncodingEvaluator implements PropertyEvaluator {

    private int maxBytes = 2000;

    private static Pattern CHARSET_PATTERN = Pattern.compile(
        "\\s*<\\?xml\\s+[^>]*\\s+encoding=[\"']([A-Za-z0-9._\\-]+)[\"'][^>]*\\?>.*",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public void setMaxBytes(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public boolean evaluate(Property property, PropertyEvaluationContext ctx) throws PropertyEvaluationException {
        if (ctx.getContent() == null) {
            return false;
        }
        String characterEncoding = null;
        String xmlContent = null;
      
        try {
            InputStream inputStream = ctx.getContent().getContentInputStream();
            xmlContent = IO.readString(inputStream, "utf-8").limit(maxBytes).perform();
        } catch (Exception e) {
            throw new PropertyEvaluationException("Unable to get String content representation", e);
        }

        if (xmlContent == null) {
            throw new PropertyEvaluationException("Unable to get String content representation");
        }

        Matcher m = CHARSET_PATTERN.matcher(xmlContent);

        if (m.find()) {
            if (logger.isDebugEnabled())
                logger.debug("Regexp match in XML declaration for pattern "
                             + CHARSET_PATTERN.pattern());
            characterEncoding = m.group(1);
            
            try {
                Charset.forName(characterEncoding);
            } catch (Exception e) {
                if (logger.isDebugEnabled())
                    logger.debug(
                        "Invalid character encoding '" + characterEncoding
                        + "' for XML document <string>, using default encoding");
                characterEncoding = null;
            }
        }
            
        if (characterEncoding == null) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                    "Unable to find encoding using regexp pattern '" + CHARSET_PATTERN.pattern()
                    + "' for XML document <string>");
            }
            return false;
        }

        property.setStringValue(characterEncoding);
        return true;
    }
    
}
