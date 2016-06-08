/* Copyright (c) 2005, University of Oslo, Norway
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
package vtk.util.repository;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.util.io.IO;
import vtk.util.text.HtmlExtractUtil;


public class TextResourceContentHelper {

    private static final int MAX_XML_DECLARATION_SIZE = 500;

    private String defaultCharacterEncoding = "utf-8";

    private static Logger logger = LoggerFactory.getLogger(TextResourceContentHelper.class);
    private Repository repository;


    public TextResourceContentHelper(Repository repository) {
        this.repository = repository;
    }
    
    public TextResourceContentHelper(Repository repository, String defaultCharacterEncoding) {
        this.repository = repository;
        this.defaultCharacterEncoding = defaultCharacterEncoding;
    }
    

    public String getResourceContent(Resource resource, String token)
        throws Exception {

        /**
         * if character encoding is set on the resource, just read it
         * as a plain stream using that encoding, regardless of
         * whether it is an XML resource. Otherwise, let the XML
         * parser handle the job */

        if (resource.getCharacterEncoding() != null) {
            return getPlainTextContent(resource, token);
        }

        if (ContentTypeHelper.isXMLContentType(resource.getContentType())) {
            return getXMLContent(resource, token);

        } else if (ContentTypeHelper.isHTMLContentType(resource.getContentType())) {
            return getHTMLContent(resource, token);
        }

        return getPlainTextContent(resource, token);
    }
    

    public String getXMLContent(Resource resource, String token)
        throws Exception {
        SAXBuilder builder = new SAXBuilder();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n = 0;
        byte[] buf = new byte[1024];
        InputStream in = this.repository.getInputStream(token, resource.getURI(), false);
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();

        try {
            
            Document doc = builder.build(new ByteArrayInputStream(out.toByteArray()));

            Format format = Format.getRawFormat();
            if (resource.getCharacterEncoding() != null) {
                format.setEncoding(resource.getCharacterEncoding());
            }

            XMLOutputter xmlOutputter = new XMLOutputter(format);
            String xml = xmlOutputter.outputString(doc);
            return xml;
        
        } catch (JDOMException e) {

            /**
             * Parsing the XML content did not work, so return the
             * content converted to a string in a "best-effort"
             * fashion: */
            String characterEncoding = "utf-8";
            if (resource.getCharacterEncoding() != null) {
                characterEncoding = resource.getCharacterEncoding();
            }

            if (logger.isDebugEnabled()) {
                logger.debug(
                    "Unable to build DOM tree for resource " 
                    + resource + ": " + e.getMessage() + ", converting "
                    + "byte stream to string using character encoding "
                    + characterEncoding);
            }
            return new String(out.toByteArray(), characterEncoding);
        }
    }
    

    public String getHTMLContent(Resource resource, String token)
        throws Exception {
        InputStream is = this.repository.getInputStream(
            token, resource.getURI(), false);
        byte[] bytes = IO.read(is).perform();
        String encoding = resource.getCharacterEncoding();
        if (encoding == null) encoding = HtmlExtractUtil.getCharacterEncodingFromBody(bytes);
        if (encoding == null) encoding = this.defaultCharacterEncoding;
        String content = new String(bytes, encoding);
        return content;
    }
    


    public String getPlainTextContent(Resource resource, String token)
        throws Exception {

        InputStream is = this.repository.getInputStream(token, resource.getURI(),
                                                   false);
        byte[] bytes = IO.read(is).perform();
        String encoding = resource.getCharacterEncoding();
        if (encoding == null) encoding = this.defaultCharacterEncoding;
        String content = new String(bytes, encoding);
        return content;
    }




    public String getXMLCharacterEncoding(String xmlContent) {
        // FIXME: more accurate regexp:
        Pattern charsetPattern = Pattern.compile(
            "^\\s*<\\?xml.*\\s+encoding=[\"']"
            + "([A-Za-z0-9._\\-]+)[\"'][^>]*\\?>.*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        String characterEncoding = this.defaultCharacterEncoding;
      
        Matcher m = charsetPattern.matcher(xmlContent);

        if (!m.matches()) {
            if (logger.isDebugEnabled())
                logger.debug("No regexp match in XML declaration for pattern "
                             + charsetPattern.pattern());
            return this.defaultCharacterEncoding;
        }
            
        if (logger.isDebugEnabled())
            logger.debug("Regexp match in XML declaration for pattern "
                         + charsetPattern.pattern());
        characterEncoding = m.group(1);

        try {
            Charset.forName(characterEncoding);
        } catch (Exception e) {
            if (logger.isDebugEnabled())
                logger.debug(
                    "Invalid character encoding '" + characterEncoding
                    + "' for XML document <string>, using utf-8");
            return this.defaultCharacterEncoding;
        }
                        
        return characterEncoding;

    }


    public String getXMLCharacterEncoding(Resource resource, String token) {

        int len = MAX_XML_DECLARATION_SIZE;
        BufferedReader reader = null;
        InputStream inStream = null;
      
        try {
            if (logger.isDebugEnabled())
                logger.debug("Opening document " + resource.getURI());
            inStream = this.repository.getInputStream(
                token, resource.getURI(), false);
            
            reader = new BufferedReader(new InputStreamReader(
                                            inStream, "utf-8"));

            char[] chars = new char[len];
            int read = reader.read(chars, 0, len);
            
            // resource didn't have content;
            if (read == -1)
                return this.defaultCharacterEncoding;
                
            String string = new String(chars, 0, read);
            return getXMLCharacterEncoding(string);
                        
        } catch (Exception e) {
            logger.warn("Unexpected exception while performing "
                        + "XML charset regexp matching on resource "
                        + resource, e);
            return this.defaultCharacterEncoding;
        }
    }


    public String getHTMLCharacterEncoding(Resource resource, String token) {
        if (!ContentTypeHelper.isHTMLContentType(resource.getContentType())) {
            return null;
        }

        int len = MAX_XML_DECLARATION_SIZE;
        InputStream inStream = null;
      
        try {
            if (logger.isDebugEnabled())
                logger.debug("Opening document " + resource.getURI());
            inStream = this.repository.getInputStream(
                token, resource.getURI(), false);
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            int n = 0, total = 0;
            byte[] buffer = new byte[1024];
            while (total <= len && (n = inStream.read(buffer, 0, 1024)) > 0) {
                total += n;
                out.write(buffer, 0, n);
            }

            String storedEncoding = HtmlExtractUtil.getCharacterEncodingFromBody(
                out.toByteArray());
            return storedEncoding;
        
        } catch (Exception e) {
            logger.warn("Unexpected IO exception while finding "
                        + "HTML character encoding on resource "
                        + resource, e);
            return null;
        }
    }
    
}
