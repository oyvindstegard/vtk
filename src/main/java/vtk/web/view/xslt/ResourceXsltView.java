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
package vtk.web.view.xslt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jdom.Document;
import org.jdom.input.SAXBuilder;
import org.jdom.transform.JDOMSource;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.view.AbstractView;

import vtk.repository.Resource;
import vtk.util.repository.LocaleHelper;
import vtk.web.InvalidModelException;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.referencedata.ReferenceDataProviding;
import vtk.xml.AbstractPathBasedURIResolver;
import vtk.xml.TransformerManager;


/**
 * XSLT transformation view. Supports transformation of both {@link
 * InputStream input streams} and JDOM {@link Document
 * documents}. Uses {@link TransformerManager} to obtain XSL
 * stylesheets.
 *
 * <p> The transformation works as follows:
 * <ul>
 * <li> First, the model is examined for a JDOM {@link Document}
 * having key <code>jdomDocument</code>. If this object exists, it is
 * used as the XML document in the transformation.
 * <li> Secondly, if the model contains an {@link InputStream} having
 * key <code>resourceStream</code>, a JDOM document is built from that
 * stream and used in the transformation.
 * <li> The model is also required to contain a {@link Resource}object
 * of key <code>resource</code>, used for metadata information and URI
 * resolving.
 * <li>If the model contains a {@link Map} of key
 * <code>xsltParameters</code>, the (key, value) pairs of that map are
 * set as parameters for the transformer.
 * <!--li> If the transformation fails for some reason, the raw XML
 * stream is written to the response.
 * </ul-->
 *
 * <p>Configurable properties:
 * <ul>
 *   <li><code>transformerManager</code> - the {@link
 *   TransformerManager} to use for obtaining stylesheets
 *   <li><code>includeContentLanguageHeader</code> - whether to set the
 *   HTTP header <code>Content-Language</code> based on the content
 *   language of the transformed resource. Default is
 *   <code>false</code>.
 * </ul>
 *
 * <p>Sets the following HTTP headers:
 * <ul>
 *   <li><code>Content-Type</code>
 *   <li><code>Content-Language</code> (if
 *   <code>includeContentLanguageHeader</code> is specified)
 * </ul>
 * 
 */
public class ResourceXsltView extends AbstractView
  implements ReferenceDataProviding, InitializingBean {

    private static Logger logger = LoggerFactory.getLogger(ResourceXsltView.class);

    private static String PARAMETER_NAMESPACE = "{http://www.uio.no/vortex/xsl-parameters}";

    private Map<String, Object> staticAttributes = null;

    private TransformerManager transformerManager = null;

    private ReferenceDataProvider[] referenceDataProviders;
    
    private boolean includeContentLanguageHeader = false;
    

    public ReferenceDataProvider[] getReferenceDataProviders() {
        return this.referenceDataProviders;
    }

    public void setReferenceDataProviders(
        ReferenceDataProvider[] referenceDataProviders) {
        this.referenceDataProviders = referenceDataProviders;
    }
    
    public void setStaticAttributes(Map<String, Object> staticAttributes) {
        this.staticAttributes = staticAttributes;
    }
    
    public void setTransformerManager(TransformerManager transformerManager)  {
        this.transformerManager = transformerManager;
    }


    public void setIncludeContentLanguageHeader(boolean includeContentLanguageHeader) {
        this.includeContentLanguageHeader = includeContentLanguageHeader;
    }
    

    public final void afterPropertiesSet() throws Exception {
        if (this.transformerManager == null) {
            throw new BeanInitializationException(
                "Property 'transformerManager' must be set");
        }
    }


    @Override
    @SuppressWarnings("rawtypes")
    protected void renderMergedOutputModel(Map model, HttpServletRequest request,
                                           HttpServletResponse response)
        throws Exception {

        Resource resource = (Resource) model.get("resource");
        if (resource == null) {
            throw new InvalidModelException(
                "Missing resource in model " +
                "(expected a Resource object having key 'resource')");
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Requested resource: " + resource);
        }

        

        Document document = (Document) model.get("jdomDocument");

        if (document == null) {
            InputStream inStream = (InputStream) model.get("resourceStream");
            if (inStream == null) {
                throw new InvalidModelException(
                    "Missing stream in model " +
                    "(expected an InputStream object having key 'resourceStream')");
            }

            try {
                // Build a JDOM tree of the input stream:
                SAXBuilder builder = new SAXBuilder();
                document = builder.build(inStream);
            
            } catch (Exception e) {            
                throw new InvalidModelException(
                    "Unable to build JDOM document from input stream", e);
            }            
        }

        ByteArrayOutputStream resultBuffer = new ByteArrayOutputStream();
        ErrorListener err = new ErrorListener();
        Transformer transformer = null;

        transformer = transformerManager.getTransformer(document);
        transformer.setErrorListener(err);
        setParameters(model, transformer);
        transformer.setParameter(
            PARAMETER_NAMESPACE + "RequestContext",
            new org.springframework.web.servlet.support.RequestContext(request));

        if (this.staticAttributes != null) {
            for (String key: this.staticAttributes.keySet()) {
                transformer.setParameter(PARAMETER_NAMESPACE + key, this.staticAttributes.get(key));
            }
        }

        // Do the transformation
        JDOMSource source = new JDOMSource(document);
        source.setSystemId(AbstractPathBasedURIResolver.PROTOCOL_PREFIX 
                + document.getBaseURI());

        transformer.transform(source, new StreamResult(resultBuffer));

        if (err.getError() != null) {
            throw err.getError();
        }

        InputStream resultStream = new ByteArrayInputStream(resultBuffer.toByteArray());
             
        OutputStream out = null;
        try {
            String contentType = getTransformedContentType(transformer);

            if (logger.isDebugEnabled()) {
                logger.debug("Transformed Content-Type is: " + contentType);
            }
            response.setContentType(contentType);

            response.setHeader("Content-Length", "" + resultBuffer.toByteArray().length);
            
            if (this.includeContentLanguageHeader) {
                Locale locale = LocaleHelper.getLocale(resource.getContentLanguage());
                if (locale != null) {
                    String contentLanguage = locale.getLanguage();
                    response.setHeader("Content-Language", contentLanguage);
                }
            }
            out = response.getOutputStream();
            byte[] buffer = new byte[5000];
            int n = 0;
            while (((n = resultStream.read(buffer, 0, 5000)) > 0)) {
                out.write(buffer, 0, n);
            }
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
            if (resultStream != null) resultStream.close();
        }
    }




    /**
     * Gets the content type (MIME type + character encoding) of the
     * transformed result based on output properties of the
     * transformer.
     *
     * @param transformer a <code>Filter</code> value
     * @return a <code>String</code>
     */
    private String getTransformedContentType(Transformer transformer) {
        String mediaType = transformer.getOutputProperty("media-type");
        String method = transformer.getOutputProperty("method");
        String encoding = transformer.getOutputProperty("encoding");

        encoding = encoding.toLowerCase();
        
        String contentType = null;
        if (mediaType != null) {
            contentType = mediaType;
            if (encoding != null) {
                contentType += ";charset=" + encoding;
            }

        } else {
            if (encoding == null)
                encoding = "utf-8"; // FIXME: what to do if encoding == null?

            if ("text".equals(method)) {
                contentType = "text/plain;charset=" + encoding;
            } else if ("html".equals(method)) {
                contentType = "text/html;charset=" + encoding;
            } else if ("xml".equals(method)) {
                contentType = "text/xml;charset=" + encoding;
            } else {
                contentType = "application/octet-stream";
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug(
                "Resolved content type: '" + contentType + "' from transformer "
                + "output properties [media-type = " + mediaType + ", method = "
                + method + ", encoding = " + encoding + "]");
        }

        
        return contentType;
    }


    protected Transformer getTransformer(Document document)
        throws Exception {
        return this.transformerManager.getTransformer(document);
    }

    @SuppressWarnings("rawtypes")
    protected void setParameters(Map model, Transformer transformer) {

        if (!model.containsKey("xsltParameters")) {
            return;
        }

        Object o = model.get("xsltParameters");
        if (!(o instanceof Map)) {
            logger.info("Model contains entry of name 'xsltParameters', " +
                        "but not of expected class java.util.Map. Actual class is " +
                        o.getClass().getName());
            return;
        }

        Map map = (Map) o;
        if (logger.isDebugEnabled()) {
            logger.debug("Will provide model to XSLT transformation: " + o);
        }

        for (Iterator i = map.keySet().iterator(); i.hasNext();) {
            Object key = i.next();
            Object value = map.get(key);

            // Check that xslt parameter is not NULL
            if (value == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Supplied XSLT parameter '" + key.toString() + 
                                 "' has NULL value, will not add.");
                }
                continue;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Setting XSLT parameter " + key.toString() + " = "
                             + value);
            }


            transformer.setParameter(key.toString(), value);
        }

    }
    

    private static class ErrorListener implements javax.xml.transform.ErrorListener {
        TransformerException error = null;
        
        public void error(TransformerException e) {
            logger.error("Transformation error: " + e.getMessage(), e);
            this.error = e;
        }
        
        public void fatalError(TransformerException e) {
            logger.error("Transformation fatal error: " + e.getMessage(), e);
            this.error = e;
        }

        public void warning(TransformerException e) {
        }

        public TransformerException getError() {
            return this.error;
        }
    }

}
