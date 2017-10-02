/* Copyright (c) 2014-2017, University of Oslo, Norway
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
package vtk.repository.index.mapping;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.PropertySetImpl;
import vtk.repository.ResourceTypeTree;
import static vtk.repository.index.mapping.Fields.FieldSpec.*;
import vtk.repository.store.db.ResourceTypeMapper;

/**
 * Fields for resource meta properties, like URI and name and ID.
 *
 */
public class ResourceFields extends Fields {

    public static final String RESOURCE_FIELDS_PREFIX = "";

    /* Resource meta fields */
    public static final String NAME_FIELD_NAME = PropertySet.NAME_IDENTIFIER;
    public static final String NAME_LC_FIELD_NAME = LOWERCASE_FIELD_PREFIX + NAME_FIELD_NAME;
    public static final String NAME_SORT_FIELD_NAME = SORT_FIELD_PREFIX + NAME_FIELD_NAME;

    public static final String URI_FIELD_NAME = PropertySet.URI_IDENTIFIER;
    public static final String URI_SORT_FIELD_NAME = SORT_FIELD_PREFIX + URI_FIELD_NAME;

    public static final String URI_DEPTH_FIELD_NAME = "uriDepth";
    public static final String URI_ANCESTORS_FIELD_NAME = "uriAncestors";

    public static final String RESOURCETYPE_NAME_FIELD_NAME = "resourceType";
    public static final String RESOURCETYPE_PATH_FIELD_NAME = "resourceTypePath";
    public static final String RESOURCETYPES_FIELD_NAME = "resourceTypes";

    public static final String ID_FIELD_NAME = "ID";

    /* Set of all reserved fields */
    private static final Set<String> RESOURCE_FIELD_NAMES = new HashSet<>();

    static {
        RESOURCE_FIELD_NAMES.add(NAME_FIELD_NAME);
        RESOURCE_FIELD_NAMES.add(NAME_LC_FIELD_NAME);
        RESOURCE_FIELD_NAMES.add(NAME_SORT_FIELD_NAME);
        RESOURCE_FIELD_NAMES.add(URI_FIELD_NAME);
        RESOURCE_FIELD_NAMES.add(URI_SORT_FIELD_NAME);
        RESOURCE_FIELD_NAMES.add(URI_ANCESTORS_FIELD_NAME);
        RESOURCE_FIELD_NAMES.add(URI_DEPTH_FIELD_NAME);
        RESOURCE_FIELD_NAMES.add(RESOURCETYPE_PATH_FIELD_NAME);
        RESOURCE_FIELD_NAMES.add(RESOURCETYPE_NAME_FIELD_NAME);
        RESOURCE_FIELD_NAMES.add(RESOURCETYPES_FIELD_NAME);
        RESOURCE_FIELD_NAMES.add(ID_FIELD_NAME);
    }

    public static boolean isResourceField(String fieldName) {
        return RESOURCE_FIELD_NAMES.contains(fieldName);
    }

    private final ResourceTypeMapper resourceTypeMapper;
    private final ResourceTypeTree resourceTypeTree;

    ResourceFields(Locale locale, ResourceTypeTree resourceTypeTree) {
        super(locale);
        this.resourceTypeTree = resourceTypeTree;
        this.resourceTypeMapper = new ResourceTypeMapper(resourceTypeTree);
    }

    void addResourceFields(final List<IndexableField> fields, PropertySetImpl propSet) {
        // URI
        fields.addAll(makeFields(URI_FIELD_NAME, propSet.getURI().toString(), INDEXED_STORED));
        fields.add(makeStringSortField(URI_SORT_FIELD_NAME, propSet.getURI().toString()));

        // URI depth (not stored, but indexed for use in searches)
        int uriDepth = propSet.getURI().getDepth();
        fields.addAll(makeFields(URI_DEPTH_FIELD_NAME, uriDepth, INDEXED_WITH_DOCVALUE));

        // Ancestor URIs (system field used for hierarchical URI namespace queries)
        for (String ancestor : getPathAncestorStrings(propSet.getURI())) {
            fields.addAll(makeFields(URI_ANCESTORS_FIELD_NAME, ancestor, INDEXED));
        }

        // URI name
        fields.addAll(makeFields(NAME_FIELD_NAME, propSet.getName(), INDEXED));
        fields.addAll(makeFields(NAME_LC_FIELD_NAME, propSet.getName(), INDEXED_LOWERCASE));
        fields.add(makeStringSortField(NAME_SORT_FIELD_NAME, propSet.getName()));

        // Resource type fields
        fields.addAll(makeFields(RESOURCETYPE_NAME_FIELD_NAME, propSet.getResourceType(), INDEXED_WITH_DOCVALUE));
        fields.addAll(makeFields(RESOURCETYPE_PATH_FIELD_NAME, resourceTypeMapper.resourceTypePath(propSet.getResourceType()), STORED));
        for (String ancestorType: resourceTypeTree.flattenedAncestors(propSet.getResourceType())) {
            fields.addAll(makeFields(RESOURCETYPES_FIELD_NAME, ancestorType, INDEXED));
        }
        fields.addAll(makeFields(RESOURCETYPES_FIELD_NAME, propSet.getResourceType(), INDEXED)); // Include leaf type in types field

        // ID (system field, stored and indexed, but only as a string type)
        fields.addAll(makeFields(ID_FIELD_NAME, Integer.toString(propSet.getNumericId()), INDEXED_STORED));
    }
    
    public String resolveResourceType(String resourceTypePath) {
        return resourceTypeMapper.resolveResourceType(resourceTypePath);
    }

    public static int getResourceId(Document doc) throws DocumentMappingException {
        String id = doc.get(ID_FIELD_NAME);
        if (id == null) {
            throw new DocumentMappingException("Document is missing field " + ID_FIELD_NAME);
        }
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException nfe) {
            throw new DocumentMappingException("Illegal stored value for field " + ID_FIELD_NAME);
        }
    }

    private String[] getPathAncestorStrings(Path path) {
        List<Path> ancestors = path.getAncestors();
        String[] ancestorStrings = new String[ancestors.size()];
        for (int i = 0; i < ancestorStrings.length; i++) {
            ancestorStrings[i] = ancestors.get(i).toString();
        }
        return ancestorStrings;
    }

}
