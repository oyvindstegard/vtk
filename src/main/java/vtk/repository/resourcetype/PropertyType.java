/* Copyright (c) 2006, University of Oslo, Norway
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
package vtk.repository.resourcetype;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import vtk.repository.RepositoryAction;

public final class PropertyType {

    /**
     * Property data types
     */ 
    public enum Type {
        STRING,
        HTML,
        IMAGE_REF,  // XXX: this should be renamed to simply "HREF", a generic link value of basic type STRING..
        INT, 
        LONG,
        TIMESTAMP, 
        DATE, 
        BOOLEAN, 
        PRINCIPAL,
        BINARY,
        JSON,
    }
    
    /*
     *  Protection levels
     */
    
    // Minimum write privilege needed to edit prop
    public static final RepositoryAction PROTECTION_LEVEL_ACL_WRITE =
        RepositoryAction.READ_WRITE;

    // Principals with admin permissions can edit property
    public static final RepositoryAction PROTECTION_LEVEL_ACL_ADMIN =
        RepositoryAction.WRITE_ACL;

    // Used for special properties, only to be modified by special "admin" 
    // users in special cases. unsure about this one...
    public static final RepositoryAction PROTECTION_LEVEL_ROLE_ADMIN =
        RepositoryAction.REPOSITORY_ADMIN_ROLE_ACTION;
    
    // Only principals of the ROOT role  are allowed to set property
    public static final RepositoryAction PROTECTION_LEVEL_ROLE_ROOT =
        RepositoryAction.REPOSITORY_ROOT_ROLE_ACTION;

    //  Property is uneditable
    public static final RepositoryAction PROTECTION_LEVEL_UNEDITABLE =
        RepositoryAction.UNEDITABLE_ACTION;
 

    /* Known evaluated props (extra props) */
    public static final String TITLE_PROP_NAME = "title";
    public static final String INTRODUCTION_PROP_NAME = "introduction";
    public static final String DESCRIPTION_PROP_NAME = "description";
    public static final String MEDIA_PROP_NAME = "media";
    public static final String PICTURE_PROP_NAME = "picture";
    public static final String SCHEMA_PROP_NAME = "schema";
    public static final String XHTML_PROP_NAME = "xhtml10-type";
    public static final String THUMBNAIL_PROP_NAME = "thumbnail";
    public static final String NUMBER_OF_COMMENTS_PROP_NAME = "numberOfComments";
    public static final String PUBLISH_DATE_PROP_NAME = "publish-date";
    public static final String UNPUBLISH_DATE_PROP_NAME = "unpublish-date";
    public static final String PUBLISHED_PROP_NAME = "published";
    public static final String TAGS_PROP_NAME = "tags";
    public static final String CONTENTLOCALE_PROP_NAME = "contentLocale";
    
    /*
     * Special properties
     */
    public static final String EXTERNAL_ID_PROP_NAME = "externalId";
    public static final String COLLECTION_PROP_NAME = "collection";
    public static final String OWNER_PROP_NAME = "owner";
    public static final String CREATIONTIME_PROP_NAME = "creationTime";
    public static final String CREATEDBY_PROP_NAME = "createdBy";
    public static final String CONTENTTYPE_PROP_NAME = "contentType";
    public static final String CHARACTERENCODING_PROP_NAME = "characterEncoding";
    public static final String CHARACTERENCODING_USER_SPECIFIED_PROP_NAME = "userSpecifiedCharacterEncoding";
    public static final String CHARACTERENCODING_GUESSED_PROP_NAME = "guessedCharacterEncoding";
    public static final String LASTMODIFIED_PROP_NAME = "lastModified";
    public static final String MODIFIEDBY_PROP_NAME = "modifiedBy";
    public static final String CONTENTLASTMODIFIED_PROP_NAME = "contentLastModified";
    public static final String CONTENTMODIFIEDBY_PROP_NAME = "contentModifiedBy";
    public static final String PROPERTIESLASTMODIFIED_PROP_NAME = "propertiesLastModified";
    public static final String PROPERTIESMODIFIEDBY_PROP_NAME = "propertiesModifiedBy";
    public static final String CONTENTLENGTH_PROP_NAME = "contentLength";
    
    public static final Set<String> SPECIAL_PROPERTIES_SET =
        Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            EXTERNAL_ID_PROP_NAME,    
            COLLECTION_PROP_NAME,
            OWNER_PROP_NAME,
            CREATIONTIME_PROP_NAME,
            CREATEDBY_PROP_NAME,
            CONTENTTYPE_PROP_NAME,
            CHARACTERENCODING_PROP_NAME,
            CHARACTERENCODING_USER_SPECIFIED_PROP_NAME,
            CHARACTERENCODING_GUESSED_PROP_NAME,
            LASTMODIFIED_PROP_NAME,
            MODIFIEDBY_PROP_NAME,
            CONTENTLASTMODIFIED_PROP_NAME,
            CONTENTMODIFIEDBY_PROP_NAME,
            PROPERTIESLASTMODIFIED_PROP_NAME,
            PROPERTIESMODIFIEDBY_PROP_NAME,
            CONTENTLENGTH_PROP_NAME
    )));

    public static final Set<String> NOT_REPRODUCABLE_CONTENT_PROPERTIES_SET = 
        Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            CONTENTLASTMODIFIED_PROP_NAME,
            CONTENTMODIFIEDBY_PROP_NAME
    )));
    
    /**
     * Set of property names (in DEFAULT namespace) which should always be
     * removed for resources that are copied.
     */
    public static final Set<String> UNCOPYABLE_PROPERTIES = 
        Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            NUMBER_OF_COMMENTS_PROP_NAME
    )));
}
