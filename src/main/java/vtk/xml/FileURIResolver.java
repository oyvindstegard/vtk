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



import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.util.repository.InvalidURIException;
import vtk.util.web.LinkTypesPrefixes;


/**
 * A resolver for <code>file:</code> (absolute) URLs. 
 *
 * TODO: add some security mechanism
 * (other than urlRegexp) to prevent files and http resources to be
 * fetched from anywhere.
 */
public class FileURIResolver implements StylesheetURIResolver {

    public static final String PROTOCOL_PREFIX = LinkTypesPrefixes.FILE + "//";
    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    
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
    
    public Date getLastModified(String url) {
        url = url.substring(PROTOCOL_PREFIX.length());
        File f = new File(url);
        return new Date(f.lastModified());
    }
        
    protected InputStream getInputStream(String url) throws IOException {
        url = url.substring(PROTOCOL_PREFIX.length());
        return new FileInputStream(url);
    }
    
    public final Source resolve(String href, String base) throws TransformerException {
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

        try {
            InputStream inStream = getInputStream(path);
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Resolved URI '" + path + "' from [href = '" +
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
            uri = expandPath(base + "/" + uri);
        } else if (uri.indexOf("../") > 0) {
            if (!uri.startsWith("/")) {
                // Handle 'relative/path/../' type URIs:
                uri = (base.equals("/")) ?
                    base + uri :
                    base + "/" + uri;
            }
            uri = expandPath(uri);
        } else if (!uri.startsWith("/")) {
            uri = (base.endsWith("/")) ? base + uri : base + "/" + uri;
        }

        uri = PROTOCOL_PREFIX + uri;
        return uri;
    }

    private static String expandPath(String uri) throws InvalidURIException {

        // Test to check that start of URI is legal path (e.g. UNIX '/', WINDOWS 'C:')
        // [using string test and regex checking]
        if ( !uri.startsWith("/") && (!uri.substring(0,2).matches("[c-zA-Z]:")) ) {
            // XXX: throw something? 
        }

        if (uri.startsWith("/../")) {
            throw new InvalidURIException(
                "URI '" + uri + "' cannot be expanded: Too many '../'s");
        }

        int firstPos = uri.indexOf("../");
        if (firstPos == -1) {
            // Quickfix for trailing ../
            if (!uri.equals("/") && uri.endsWith("/"))
                return uri.substring(0, uri.length() -1);
            return uri;
        }
        String base = uri.substring(0, firstPos - 1);
        base = base.substring(0, base.lastIndexOf("/") + 1);
        uri = base + uri.substring(firstPos + "../".length());
        return expandPath(uri);
    }
    

}
