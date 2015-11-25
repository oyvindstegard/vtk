/* Copyright (c) 2015, University of Oslo, Norway
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
package vtk.web.search;

import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import vtk.repository.IllegalOperationException;
import vtk.repository.MultiHostSearcher;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.Value;
import vtk.repository.resourcetype.ValueFormatException;
import vtk.repository.search.ResultSet;
import vtk.web.service.URL;

/**
 *
 * Utility functions for MultiHost properties
 */
public class MultiHostUtil {

    private static final Log logger = LogFactory.getLog(MultiHostUtil.class);

    /**
     * Get MultiHostUrl Property from a PropertySet.
     *
     * @param ps PropertySet containing MultiHostUrl Property
     * @return MultiHostUrl Property or null
     */
    public static Property getMultiHostUrlProp(PropertySet ps) {
        return ps.getProperty(Namespace.DEFAULT_NAMESPACE, MultiHostSearcher.URL_PROP_NAME);
    }

    /**
     * Checks if MultiHostUrl Property is contained in a PropertySet.
     *
     * @param ps PropertySet containing MultiHostUrl Property
     * @return true if MultiHostUrl Property is contained in a PropertySet
     */
    public static boolean isMultiHostPropertySet(PropertySet ps) {
        return getMultiHostUrlProp(ps) != null;
    }

    /**
     * Resolve all IMAGE_REF Properties in a ResultSet to absolute URI. The absolute URI will be set per Property as a
     * new value.
     *
     * @param rs ResultSet to be changed
     * @return PropertySet with new IMAGE_REF Property values
     */
    public static ResultSet resolveResultSetImageRefProperties(ResultSet rs) {
        for (PropertySet ps : rs.getAllResults()) {
            if (isMultiHostPropertySet(ps)) {
                resolveImageRefProperties(ps);
            }
        }

        return rs;
    }

    /**
     * Resolve all IMAGE_REF Properties in a Set of PropertySets to absolute URI. The absolute URI will be set per
     * Property as a new value.
     *
     * @param propertySets Set to be changed
     * @return Set of PropertySets with new IMAGE_REF Property values
     */
    public static Set<PropertySet> resolveSetImageRefProperties(Set<PropertySet> propertySets) {
        for (PropertySet ps : propertySets) {
            if (isMultiHostPropertySet(ps)) {
                resolveImageRefProperties(ps);
            }
        }

        return propertySets;
    }

    /**
     * Resolve all IMAGE_REF Properties in a PropertySet to absolute URI. The absolute URI will be set per Property as a
     * new value.
     *
     * @param ps PropertySet containing IMAGE_REF Properties
     * @return PropertySet with new IMAGE_REF Property values
     */
    public static PropertySet resolveImageRefProperties(PropertySet ps) {
        Property multiHostUrl = getMultiHostUrlProp(ps);

        if (multiHostUrl != null) {
            for (Property prop : ps) {
                try {
                    if (prop.getType().equals(Type.IMAGE_REF)) {
                        /*
                         * resolveImageRefProperty with thumbnailForNoneAbsoluteRefs=true to maintain
                         * behavior form SolrResultMapper.java as default
                         */
                        resolveImageRefProperty(prop, multiHostUrl, true);
                    }
                } catch (IllegalOperationException | ValueFormatException e) {
                    logger.error("An error occured while mapping property '" + prop.getDefinition().getName() + "' for "
                            + "resource " + ps.getURI() + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }

        return ps;
    }

    /**
     * Resolve URI Property and set new value.
     *
     * @param prop to be resolved
     * @param multiHostUrl Required to resolve {@code prop}
     * @return Property {@code prop} resolved
     */
    public static Property resolveImageRefProperty(Property prop, Property multiHostUrl) {
        return resolveImageRefProperty(prop, multiHostUrl, false);
    }

    private static Property resolveImageRefProperty(Property prop, Property multiHostUrl,
            boolean addThumbnailForNoneAbsoluteRefs) {
        if (!prop.getType().equals(Type.IMAGE_REF)) {
            throw new IllegalArgumentException(prop.getDefinition().getName() + " is not of type IMAGE_REF");
        }

        String ref = resolveImageRef(prop, multiHostUrl, addThumbnailForNoneAbsoluteRefs);
        Value newValue = new Value(ref, Type.IMAGE_REF);
        prop.setValue(newValue);

        return prop;
    }

    /**
     * Resolve URI Property to {@code String}.
     *
     * @param prop to be resolved
     * @param multiHostUrl Required to resolve {@code prop}
     * @param addThumbnailForNoneAbsoluteRefs if true adds parameter vrtx=thumbnail to return value if IMAGE_REF is a
     * relative path
     * @return resolved String value for IMAGE_REF
     */
    public static String resolveImageRefStringValue(Property prop, Property multiHostUrl,
            boolean addThumbnailForNoneAbsoluteRefs) {
        if (!prop.getType().equals(Type.IMAGE_REF)) {
            throw new IllegalArgumentException(prop.getDefinition().getName() + " is not of type IMAGE_REF");
        }

        return resolveImageRef(prop, multiHostUrl, addThumbnailForNoneAbsoluteRefs);
    }

    private static String resolveImageRef(Property prop, Property multiHostUrl,
            boolean addThumbnailForNoneAbsoluteRefs) {
        String ret = prop.getStringValue();

        // Don't do anything if ref is complete url
        if (!isValidUrl(ret) && multiHostUrl != null) {
            URL url = URL.parse(multiHostUrl.getStringValue());

            // Is it a valid path?
            Path path = getAsPath(ret);
            if (path == null && url != null) {
                // Assume relative path
                try {
                    Path resourceParentPath = url.getPath().getParent();
                    path = resourceParentPath.expand(ret);
                } catch (Exception e) {
                    // Ignore. Log??
                }
            }

            if (path != null && url != null) {
                url.setPath(path).toString();
                // Display thumbnail if addThumbnailForNoneAbsoluteRefs is true and if IMAGE_REF is a picture
                if (addThumbnailForNoneAbsoluteRefs
                        && prop.getDefinition().getName().equals(PropertyType.PICTURE_PROP_NAME)) {
                    url.addParameter("vrtx", "thumbnail");
                }
                ret = url.toString();
            }
        }

        return ret;
    }

    private static boolean isValidUrl(String imageUrl) {
        try {
            vtk.web.service.URL.parse(imageUrl);
        } catch (Exception e) {
            // Ignore, invalid url
        }
        return false;
    }

    private static Path getAsPath(String pathString) {
        try {
            return Path.fromString(pathString);
        } catch (IllegalArgumentException iae) {
            // Ignore, invalid path ref
        }
        return null;
    }

}
