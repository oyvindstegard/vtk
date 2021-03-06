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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.repository.IllegalOperationException;
import vtk.repository.MultiHostSearcher;
import vtk.repository.Namespace;
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

    private static final Logger logger = LoggerFactory.getLogger(MultiHostUtil.class);

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

        if (!prop.getDefinition().getName().equals(PropertyType.PICTURE_PROP_NAME)) {
            addThumbnailForNoneAbsoluteRefs = false;
        }

        Value[] vals;
        if (prop.getDefinition().isMultiple()) {
            vals = prop.getValues();
        } else {
            vals = new Value[]{prop.getValue()};
        }

        Value[] resolvedVals = new Value[vals.length];
        for (int i = 0; i < vals.length; i++) {
            String resolvedRef = resolveImageRef(vals[i].getStringValue(), multiHostUrl,
                    addThumbnailForNoneAbsoluteRefs);
            resolvedVals[i] = new Value(resolvedRef, Type.IMAGE_REF);
        }

        if (prop.getDefinition().isMultiple()) {
            prop.setValues(resolvedVals);
        } else {
            prop.setValue(resolvedVals[0]);
        }

        return prop;
    }

    /**
     * Resolve URI Property to {@code String}.
     *
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

        if (!prop.getDefinition().getName().equals(PropertyType.PICTURE_PROP_NAME)) {
            addThumbnailForNoneAbsoluteRefs = false;
        }

        // XXX this will fail for multi-value props
        return resolveImageRef(prop.getStringValue(), multiHostUrl, addThumbnailForNoneAbsoluteRefs);
    }

    private static String resolveImageRef(String ref, Property multiHostUrl, boolean addThumbnailForNoneAbsoluteRefs) {

        if (multiHostUrl == null) {
            // Nothing to resolve against
            return ref;
        }

        URL remoteResource = URL.parse(multiHostUrl.getStringValue());
        URL remoteRelative = remoteResource.relativeURL(ref);

        if (remoteRelative.getHost() != null && remoteResource.getHost() != null) {
            if (addThumbnailForNoneAbsoluteRefs && remoteResource.getHost().equals(remoteRelative.getHost())) {
                remoteRelative.setParameter("vrtx", "thumbnail");
            }
        }

        return remoteRelative.toString();
    }

}
