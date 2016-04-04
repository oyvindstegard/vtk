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

import java.io.InputStream;
import java.util.Date;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.repository.Path;


/**
 * Resolves abstract stylesheet identifiers to path based resources,
 * such as files and resources in the repository. Subclasses must
 * implement the <code>getLastModifiedInternal()</code> and
 * <code>getInputStream()</code> methods.
 *
 * Configurable properties:
 * <ul>
 *  <li><code>prefix</code> - the prefix to prepend to all paths
 *  <!--li><code>pathRegexp</code> - regular expression denoting the
 *      legal values of stylesheet references. If this regexp does not
 *      match the value of the expanded repository URI, the resolver
 *      will act as if the stylesheet was not found. Default value is
 *      <code>.*</code>.-->
 * </ul>
 */
public abstract class AbstractPathBasedURIResolver implements StylesheetURIResolver {

    public static final String PROTOCOL_PREFIX = "path://";

    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    

    private String prefix = null;

    public void setPrefix(String prefix)  {
        this.prefix = prefix;
    }

    /**
     * Gets an input stream for a path based resource. Subclasses must
     * implement this method.
     *
     * @param path a <code>String</code> value
     * @exception Exception if an error occurs
     */
    protected abstract InputStream getInputStream(Path path) throws Exception;


    /**
     * Gets the last modified value for a path resource. Subclasses
     * must implement this method.
     *
     * @param path a <code>String</code> value
     * @return a <code>Date</code>
     * @exception Exception if an error occurs
     */
    public abstract Date getLastModifiedInternal(Path path) throws Exception;
    

    /**
     * Will only match paths starting with a '/' character.
     *
     * @param stylesheetIdentifier a <code>String</code> value
     * @return a <code>boolean</code>
     */
    @Override
    public final boolean matches(String stylesheetIdentifier) {
        if (stylesheetIdentifier.startsWith("/")) {
            return true;
        } 
        return false;
    }

    /**
     * Gets the last modified date for a path based resource.
     *
     * @param identifier a <code>String</code> value
     * @throws Exception if an error occurs
     */
    @Override
    public final Date getLastModified(String identifier) throws Exception {
        if (identifier.endsWith("/") && !identifier.equals("/"))
            identifier = identifier.substring(0, identifier.length() - 1);

        Path path = Path.fromString(addPrefix(identifier));
        return getLastModifiedInternal(path);
    }
    
    @Override
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

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Resolved URI '" + path + "' from [href = '" +
                              href + "', base = '" + base + "']");
        }

        try {

            Source source = null;

            if (path.endsWith("/") && !path.equals("/"))
                path = path.substring(0, path.length() - 1);

            InputStream inStream = getInputStream(Path.fromString(path));

            if (inStream != null)
                source = new StreamSource(inStream);
            else
                source = new StreamSource();
            source.setSystemId(PROTOCOL_PREFIX + path);
            return source;
            
        } catch (Exception e) {
            throw new TransformerException(
                "Unable to resolve URI [href = '" + href + "', base = '" +
                base + "']", e);
        }
    }

    private String getAbsolutePath(String href, String base) {
        String uri;
		if (href == null || href.trim().equals("")) {
			return null;
		} else if (href.startsWith("/")) {
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
        Path path = Path.ROOT.expand(uri.substring(1));
        return path.toString();
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
