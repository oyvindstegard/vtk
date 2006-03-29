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



import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.vortikal.util.repository.URIUtil;


/**
 * A resolver for <code>file:</code> (absolute) URLs. 
 *
 * TODO: add some security mechanism
 * (other than urlRegexp) to prevent files and http resources to be
 * fetched from anywhere.
 */
public class FileURIResolver implements StylesheetURIResolver, InitializingBean {

    public static final String PROTOCOL_PREFIX = "file://";


    protected Log logger = LogFactory.getLog(this.getClass());
    


    public void afterPropertiesSet() throws Exception {
        
    }

    
    /**
     * Will only match paths starting with a '/' character.
     *
     * @param stylesheetIdentifier a <code>String</code> value
     * @return a <code>boolean</code>
     */
    public final boolean matches(String stylesheetIdentifier) {
        if (stylesheetIdentifier == null || stylesheetIdentifier.trim().equals(""))
            return false;
        if (stylesheetIdentifier.startsWith(PROTOCOL_PREFIX)) {
            return true;
        } 
        return false;
    }
    


    public Date getLastModified(String url) throws IOException {
        url = url.substring(PROTOCOL_PREFIX.length());
        File f = new File(url);
        return new Date(f.lastModified());
    }
        


    protected InputStream getInputStream(String url) throws IOException {
        url = url.substring(PROTOCOL_PREFIX.length());
        return new FileInputStream(url);
    }
    
    

    public final Source resolve(String href, String base) throws TransformerException {
        if (logger.isDebugEnabled()) {
            logger.debug("Resolving: [href = " + href + ", base: " + base + "]");
        }

        String path = getAbsolutePath(href, base);
        if (path == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Unable to obtain absolute path for [href = '" + href +
                             "', base = '" + base+ "']");
            }
            return null;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Path after expansion: '" + path + "'");
        }

        try {
            InputStream inStream = getInputStream(path);
            if (logger.isDebugEnabled()) {
                logger.debug("Resolved URI '" + path + "' from [href = '" +
                             href + "', base = '" + base + "']");
            }
            StreamSource streamSource = new StreamSource(inStream);
            streamSource.setSystemId(PROTOCOL_PREFIX + path);
            return streamSource;
                
        } catch (IOException e) {
            throw new TransformerException(
                "Unable to resolve URI [href = '" + href + "', base = '" +
                base + "']", e);
        }
    }


    private String getAbsolutePath(String href, String base) {

        if (this.matches(href)) {
            return href;
        }

        if (base != null && !base.startsWith(PROTOCOL_PREFIX)) {
            return null;
        }

        if (!this.matches(href) && base != null) {
            return null;
        }

        if (base != null) {
            base = base.substring(PROTOCOL_PREFIX.length());
            base = base.substring(0, base.lastIndexOf("/"));
        }
            
        String uri = href;

        if (uri.indexOf("../") == 0) {
            uri = URIUtil.expandPath(base + "/" + uri);
        } else if (uri.indexOf("../") > 0) {
            if (!uri.startsWith("/")) {
                // Handle 'relative/path/../' type URIs:
                uri = (base.equals("/")) ?
                    base + uri :
                    base + "/" + uri;
            }
            uri = URIUtil.expandPath(uri);
        } else if (!uri.startsWith("/")) {
            uri = (base.endsWith("/")) ? base + uri : base + "/" + uri;
        }

        uri = PROTOCOL_PREFIX + uri;
        return uri;
    }

}
