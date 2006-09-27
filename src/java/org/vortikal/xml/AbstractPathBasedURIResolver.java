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
package org.vortikal.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.vortikal.util.cache.SimpleCache;
import org.vortikal.util.repository.URIUtil;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;


/**
 * Resolves abstract stylesheet identifiers to path based resources,
 * such as files and resources in the repository. Subclasses must
 * implement the <code>getLastModifiedInternal()</code> and
 * <code>getInputStream()</code> methods.
 *
 * Configurable properties:
 * <ul>
 *  <li><code>prefix</code> - the prefix to prepend to all paths
 *  <li><code>simpleCache</code> - a cache for source ids contained in
 *      <code>cacheIdentifiers</code>
 *  <li><code>cacheIdentifiers</code> - a list of path ids to resources to be cached.
 *      required if simpleCache is specified.
 *  <!--li><code>pathRegexp</code> - regular expression denoting the
 *      legal values of stylesheet references. If this regexp does not
 *      match the value of the expanded repository URI, the resolver
 *      will act as if the stylesheet was not found. Default value is
 *      <code>.*</code>.-->
 * </ul>
 */
public abstract class AbstractPathBasedURIResolver
  implements StylesheetURIResolver, InitializingBean {

    public static final String PROTOCOL_PREFIX = "path://";


    protected Log logger = LogFactory.getLog(this.getClass());
    

    private String prefix = null;

    private List cacheIdentifiers;
    private SimpleCache simpleCache;

    public void setSimpleCache(SimpleCache simpleCache) {
        this.simpleCache = simpleCache;
    }

    public void setCacheIdentifiers(String[] cacheIdentifiers) {
        this.cacheIdentifiers = Arrays.asList(cacheIdentifiers);
    }

    public void setPrefix(String prefix)  {
        this.prefix = prefix;
    }


    public void afterPropertiesSet() throws Exception {
        if (this.simpleCache != null && this.cacheIdentifiers == null) 
            throw new BeanInitializationException(
                "Java Bean property 'cacheIdentifiers' required when "
                + "'simpleCahce' is specified");
    }

    
    /**
     * Gets an input stream for a path based resource. Subclasses must
     * implement this method.
     *
     * @param path a <code>String</code> value
     * @exception IOException if an error occurs
     */
    protected abstract InputStream getInputStream(String path) throws IOException;


    /**
     * Gets the last modified value for a path resource. Subclasses
     * must implement this method.
     *
     * @param path a <code>String</code> value
     * @return a <code>Date</code>
     * @exception IOException if an error occurs
     */
    public abstract Date getLastModifiedInternal(String path) throws IOException;
    

    /**
     * Will only match paths starting with a '/' character.
     *
     * @param stylesheetIdentifier a <code>String</code> value
     * @return a <code>boolean</code>
     */
    public final boolean matches(String stylesheetIdentifier) {
        if (stylesheetIdentifier.startsWith("/")) {
            return true;
        } 
        return false;
    }
    

    /**
     * Gets the last modified date for a path based resource.
     *
     * @param path a <code>String</code> value
     * @exception IOException if an error occurs
     */
    public final Date getLastModified(String path) throws IOException {
        path = addPrefix(path);
        return getLastModifiedInternal(path);
    }
    


    public final Source resolve(String href, String base) 
        throws TransformerException {
        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Resolving: [href = " + href + ", base: " + base + "]");
        }

        String path = getAbsolutePath(href, base);
        if (path == null) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Unable to obtain absolute path for [href = '" + href +
                             "', base = '" + base+ "']");
            }
            return null;
        }
        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Path after expansion: '" + path + "'");
        }

        if (base == null) {
            path = addPrefix(path);

            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Path after prefix prepended: '" + path + "'");
            }
        }

        try {
            Source source = null;

            if (this.simpleCache != null) {
                source = (Source) this.simpleCache.get(path);

                if (source != null) {
                    if (this.logger.isDebugEnabled())
                        this.logger.debug("Using cached source for resource: " + path);
                    return source;
                }
            }

            InputStream inStream = getInputStream(path);
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Resolved URI '" + path + "' from [href = '" +
                             href + "', base = '" + base + "']");
            }
            
            if (inStream != null) {
                if (this.simpleCache != null && this.cacheIdentifiers.contains(path)) {
                    try {
                        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                        Document doc = builder.parse(inStream);
                        source = new DOMSource(doc);
                        this.simpleCache.put(path, source);
                    } catch (SAXException e) {
                        throw new TransformerException("Unable to build DOM reprsentation of resource '"
                                + path + "'", e);
                    } catch (ParserConfigurationException e) {
                        throw new TransformerException("Unable to build DOM reprsentation of resource '"
                                + path + "'", e);
                    }
                } else {
                    source = new StreamSource(inStream);
                }
            } else 
                source = new StreamSource();
            
            source.setSystemId(PROTOCOL_PREFIX + path);
            return source;
                
        } catch (IOException e) {
            throw new TransformerException(
                "Unable to resolve URI [href = '" + href + "', base = '" +
                base + "']", e);
        }
    }

    private String getAbsolutePath(String href, String base) {

        String uri = null;
        
        if (href.startsWith("/")) {
            // hrefs starting with '/' don't care about base
            uri = href;

        } else if (href.matches(".+://.+") || base == null || !base.startsWith(PROTOCOL_PREFIX)) {
            // Fully qualified hrefs isn't handled.
            // Relative hrefs need to be resolved relative to a base with protocol 'PROTOCOL_PREFIX'
            return null;
        
        } else {

            // Strip protocol and the name of the base resource    
            base = base.substring(PROTOCOL_PREFIX.length());
            base = base.substring(0, base.lastIndexOf("/") + 1);
            
            uri = base + href;
        }
        
        if (uri.indexOf("../") > -1) {
            uri = URIUtil.expandPath(uri);
        }
        return uri;
    }
    
    private String addPrefix(String uri) {
        if (this.prefix == null) {
            return uri;
        }
        if (this.prefix.endsWith("/") && uri.startsWith("/")) {
            uri = uri.substring(1);
        }
        uri = this.prefix + uri;
        return uri;
    }
    

}
