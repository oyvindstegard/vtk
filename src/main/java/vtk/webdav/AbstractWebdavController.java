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
package vtk.webdav;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jdom.Namespace;
import org.springframework.web.servlet.mvc.Controller;
import vtk.repository.Path;
import vtk.repository.resourcetype.PropertyType;
import vtk.web.service.URL;


/**
 * The superclass of all the WebDAV method controllers.
 *
 */
public abstract class AbstractWebdavController implements Controller {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    private List<Pattern> deniedFileNamePatterns = new ArrayList<Pattern>();

    public void setDeniedFileNames(List<String> deniedFileNames) {
        if (deniedFileNames == null) {
            throw new IllegalArgumentException("Argument cannot be NULL");
        }
        this.deniedFileNamePatterns = new ArrayList<Pattern>();
        for (String patternStr: deniedFileNames) {
            Pattern p = Pattern.compile(patternStr);
            this.deniedFileNamePatterns.add(p);
        }
    }
    
    /**
     * Maps WebDAV property names to resource property names
     */
    public static final Map<String, String> MAPPED_DAV_PROPERTIES;

    /**
     * The set of WebDAV property names that do not map directly to
     * resource propery names
     */
    public static final Set<String> SPECIAL_DAV_PROPERTIES;

    /**
     * The set of all WebDAV property names
     */
    public static final Set<String> DAV_PROPERTIES;

    static {
        MAPPED_DAV_PROPERTIES = new HashMap<String, String>();
        MAPPED_DAV_PROPERTIES.put("creationdate", PropertyType.CREATIONTIME_PROP_NAME);
        MAPPED_DAV_PROPERTIES.put("getcontentlanguage", PropertyType.CONTENTLOCALE_PROP_NAME);
        MAPPED_DAV_PROPERTIES.put("getcontentlength", PropertyType.CONTENTLENGTH_PROP_NAME);
        MAPPED_DAV_PROPERTIES.put("getcontenttype", PropertyType.CONTENTTYPE_PROP_NAME);
        MAPPED_DAV_PROPERTIES.put("getlastmodified", PropertyType.LASTMODIFIED_PROP_NAME);

        SPECIAL_DAV_PROPERTIES = new HashSet<String>();
        SPECIAL_DAV_PROPERTIES.add("getetag");
        SPECIAL_DAV_PROPERTIES.add("lockdiscovery");
        SPECIAL_DAV_PROPERTIES.add("resourcetype");
        SPECIAL_DAV_PROPERTIES.add("displayname");
        SPECIAL_DAV_PROPERTIES.add("source");
        SPECIAL_DAV_PROPERTIES.add("supportedlock");

        DAV_PROPERTIES = new HashSet<String>();
        DAV_PROPERTIES.addAll(MAPPED_DAV_PROPERTIES.keySet());
        DAV_PROPERTIES.addAll(SPECIAL_DAV_PROPERTIES);
    }

    /**
     * Determines whether a DAV property name is supported.
     *
     * @param propertyName the name to check
     * @return <code>true</code> if the property is recognized,
     * <code>false</code> otherwise
     */
    protected boolean isSupportedProperty(String propertyName, Namespace namespace) {
        if (!WebdavConstants.DAV_NAMESPACE.equals(namespace)) {
            return true;
        }
        
        return DAV_PROPERTIES.contains(propertyName);
    }
    
    public Path mapToResourceURI(String value) {
    	if (value.startsWith("/")) {
    	    Path p = Path.fromString(value);
    	    return URL.decode(p);
    	} else {
            URL url = URL.parse(value);
            return url.getPath();
    	}
    }

    
    protected boolean allowedResourceName(Path uri) {
        String name = uri.getName();
        for (Pattern pattern: this.deniedFileNamePatterns) {
            Matcher m = pattern.matcher(name);
            if (m.find()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Denying file name creation: '" + name + "'");
                }
                return false;
            }
        }
        return true;
    }
    

}

