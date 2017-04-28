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
package vtk.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;
import vtk.util.codec.MD5;
import vtk.util.repository.LocaleHelper;

/**
 * Implementation of {@link Resource} with the following characteristics:
 * <ul>
 * <li>Complete with all metadata available at the time of loading.
 * <li>Mutable, so must be cloned if cached.
 * <li>DTO with no runtime ties to inner workings.
 * </ul>
 *
 * <p>
 * XXX: Handling of child URI list should be improved/done differently.
 * Currently fragile and ugly (there are too many hidden assumptions).
 */
public class ResourceImpl extends PropertySetImpl implements Resource {

    private Lock lock = null;
    
    // The value of childURIs should be either an immutable list or null.
    private volatile List<Path> childURIs = null; 

    public ResourceImpl(Path uri) {
        super();
        this.uri = uri;
    }

    @Override
    public void removeProperty(Namespace namespace, String name) {
        Map<String, Property> props = super.propertyMap.get(namespace);
        if (props != null) {
            props.remove(name);
        }
    }

    @Override
    public void removeProperty(PropertyTypeDefinition propDef) {
        removeProperty(propDef.getNamespace(), propDef.getName());
    }

    @Override
    public void removeAllProperties() {
        super.propertyMap.clear();
    }

    @Override
    public String getContentLanguage() {
        return getPropValue(PropertyType.CONTENTLOCALE_PROP_NAME);
    }

    @Override
    public Lock getLock() {
        return this.lock;
    }

    @Override
    public Acl getAcl() {
        return super.acl;
    }

    public void setLock(Lock lock) {
        this.lock = lock;
    }

    @Override
    public String getSerial() {
        String serial = getURI().toString() + getContentLastModified() + getPropertiesLastModified()
                + getContentLength();
        serial = MD5.md5sum(serial);
        serial = "vortex-" + serial;
        return serial;
    }

    @Override
    public String getEtag() {
        return "\"" + getSerial() + "\"";
    }

    @Override
    public Principal getOwner() {
        return getPrincipalPropValue(PropertyType.OWNER_PROP_NAME);
    }

    @Override
    public Principal getCreatedBy() {
        return getPrincipalPropValue(PropertyType.CREATEDBY_PROP_NAME);
    }

    @Override
    public Principal getContentModifiedBy() {
        return getPrincipalPropValue(PropertyType.CONTENTMODIFIEDBY_PROP_NAME);
    }

    @Override
    public Principal getPropertiesModifiedBy() {
        return getPrincipalPropValue(PropertyType.PROPERTIESMODIFIEDBY_PROP_NAME);
    }

    @Override
    public Date getCreationTime() {
        return getDatePropValue(PropertyType.CREATIONTIME_PROP_NAME);
    }

    @Override
    public Date getContentLastModified() {
        return getDatePropValue(PropertyType.CONTENTLASTMODIFIED_PROP_NAME);
    }

    @Override
    public Date getPropertiesLastModified() {
        return getDatePropValue(PropertyType.PROPERTIESLASTMODIFIED_PROP_NAME);
    }

    @Override
    public String getContentType() {
        return getPropValue(PropertyType.CONTENTTYPE_PROP_NAME);
    }

    @Override
    public String getCharacterEncoding() {
        return getPropValue(PropertyType.CHARACTERENCODING_PROP_NAME);
    }

    @Override
    public String getUserSpecifiedCharacterEncoding() {
        return getPropValue(PropertyType.CHARACTERENCODING_USER_SPECIFIED_PROP_NAME);
    }

    @Override
    public String getGuessedCharacterEncoding() {
        return getPropValue(PropertyType.CHARACTERENCODING_GUESSED_PROP_NAME);
    }

    @Override
    public String getTitle() {
        return getPropValue(PropertyType.TITLE_PROP_NAME);
    }

    @Override
    public boolean isCollection() {
        return getBooleanPropValue(PropertyType.COLLECTION_PROP_NAME);
    }

    /**
     * Gets the date of this resource's last modification. The date returned is
     * either that of the <code>getContentLastModified()</code> or the
     * <code>getPropertiesLastModified()</code> method, depending on which one
     * is the most recent.
     * 
     * @return the time of last modification
     */
    @Override
    public Date getLastModified() {
        return getDatePropValue(PropertyType.LASTMODIFIED_PROP_NAME);
    }

    /**
     * Gets the name of the principal that last modified either the content or
     * the properties of this resource.
     */
    @Override
    public Principal getModifiedBy() {
        return getPrincipalPropValue(PropertyType.MODIFIEDBY_PROP_NAME);
    }

    @Override
    public long getContentLength() {
        return getLongPropValue(PropertyType.CONTENTLENGTH_PROP_NAME);
    }

    @Override
    public Locale getContentLocale() {
        return LocaleHelper.getLocale(this.getContentLanguage());
    }

    @Override
    public boolean isReadRestricted() {
        return !super.acl.hasPrivilege(Privilege.READ, PrincipalFactory.ALL)
                && !super.acl.hasPrivilege(Privilege.READ_PROCESSED, PrincipalFactory.ALL);
    }

    @Override
    public boolean isPublished() {
        return getBooleanPropValue(PropertyType.PUBLISHED_PROP_NAME);
    }
    
    @Override
    public Date getPublishDate() {
        return getDatePropValue(PropertyType.PUBLISH_DATE_PROP_NAME);
    }

    @Override
    public boolean hasPublishDate() {
        return getDatePropValue(PropertyType.PUBLISH_DATE_PROP_NAME) != null;
    }
    
    @Override
    public Date getUnpublishDate() {
        return getDatePropValue(PropertyType.UNPUBLISH_DATE_PROP_NAME);
    }
    
    ResourceImpl createCopy(Path newUri, Acl acl) {
        ResourceImpl resource = new ResourceImpl(newUri);
        resource.setResourceType(getResourceType());
        for (Property prop : this) {
            if (!prop.isInherited()) {
                resource.addProperty(prop);
            }
        }
        resource.setAcl(acl);
        return resource;
    }

    @Override
    // Leave unsynchronized (field is volatile, that should be enough)
    public List<Path> getChildURIs() {
        return this.childURIs;
    }
    
    public synchronized void setChildURIs(List<Path> childURIs) {
        if (childURIs != null) {
            this.childURIs = Collections.unmodifiableList(childURIs);
        }
    }

    // Mumble mumble. this.childURIs should never be null if this method is
    // called.
    // However, this method is only called from RepositoryImpl.create*().
    // Keeping old behaviour for now.
    public synchronized void addChildURI(Path childURI) {
        if (this.childURIs == null) {
            // XXX Tempted to throw IllegalStateException here ...
            this.childURIs = Collections.unmodifiableList(Arrays.asList(new Path[]{childURI}));
            
        } else {
            // Don't modify current child URI list, create copy and replace instead.
            // This is because the current list might be read simultaneously out
            // there in the wild..
            ArrayList<Path> newList = new ArrayList<Path>(this.childURIs);
            newList.add(childURI);
            this.childURIs = Collections.unmodifiableList(newList);
        }
    }

    // Mumble mumble. this.childURIs should never be null if this method is
    // called.
    // However, this method is only called from RepositoryImpl.delete(). Keeping
    // old behaviour for now.
    public synchronized void removeChildURI(Path childURI) {
        if (this.childURIs == null) {
            // XXX Very tempted to throw IllegalStateException here ...
            this.childURIs = Collections.EMPTY_LIST;
            
        } else {
            // Don't modify current child URI list, create copy and replace instead.
            // This is because the current list might be read simultaneously out
            // there in the wild
            ArrayList<Path> newList = new ArrayList<Path>(this.childURIs);
            newList.remove(childURI);
            this.childURIs = Collections.unmodifiableList(newList);
        }
    }

    private String getPropValue(String name) {
        Property prop = this.getProp(name);
        if (prop == null)
            return null;
        return prop.getStringValue();
    }

    private Date getDatePropValue(String name) {
        Property prop = this.getProp(name);
        if (prop == null)
            return null;
        return prop.getDateValue();
    }

    private long getLongPropValue(String name) {
        Property prop = this.getProp(name);
        if (prop == null)
            return -1;
        return prop.getLongValue();
    }

    private boolean getBooleanPropValue(String name) {
        Property prop = this.getProp(name);
        if (prop == null)
            return false;
        return prop.getBooleanValue();
    }

    private Principal getPrincipalPropValue(String name) {
        Property prop = this.getProp(name);
        if (prop == null)
            return null;
        return prop.getPrincipalValue();
    }

    /**
     * Get a property by name. Default namespace is tried first, then
     * structured resources namespace.
     */
    private Property getProp(String name) {
        Map<String, Property> props = this.propertyMap.get(Namespace.DEFAULT_NAMESPACE);
        if (props != null) {
            Property prop = props.get(name);
            if (prop != null) {
                return prop;
            }
        }
        props = this.propertyMap.get(Namespace.STRUCTURED_RESOURCE_NAMESPACE);
        if (props == null) {
            return null;
        }
        return props.get(name);
    }

    @Override
    public Iterator<Property> iterator() {
        // Resource interface API allows property removal, so iterator should as well.
        return new PropertyIteratorWithRemoval(super.propertyMap);
    }
    
}
