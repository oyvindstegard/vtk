/* Copyright (c) 2007, University of Oslo, Norway
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

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import vtk.edit.editor.ResourceWrapperManager;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.Principal;

public class ResourceWrapper implements Resource {

    private ResourceWrapperManager resourceManager;

    private Resource resource;

        public ResourceWrapper(ResourceWrapperManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public Resource getResource() {
        return this.resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public Property getPropertyByName(String name) {
        for (Property prop : this.resource) {
            if (prop.getDefinition().getName().equals(name)) {
                return prop;
            }
        }
        return null;
    }

    public String getValueByName(String name) {
        for (Property prop : this.resource) {
            if (prop.getDefinition().getName().equals(name)) {
                return prop.getFormattedValue();
            }
        }
        return "";
    }

    public String getValue(PropertyTypeDefinition propDef) {
        Property prop = resource.getProperty(propDef);
        if (prop == null) {
            return "";
        }
        return prop.getFormattedValue(null, null);

    }

    public ResourceWrapper getPropResource(HttpServletRequest request, PropertyTypeDefinition propDef) {
        if (propDef.getType().equals(PropertyType.Type.IMAGE_REF)) {
            try {
                String ref = resource.getProperty(propDef).getStringValue();
                // ref = URLDecoder.decode(ref, "utf-8");
                Path uri;
                if (ref.contains("/")) {
                    uri = Path.fromString(ref);
                }
                else {
                    if (resource.isCollection()) {
                        uri = resource.getURI().extend(ref);
                    }
                    else {
                        uri = resource.getURI().getParent().extend(ref);
                    }
                }
                return this.resourceManager.createResourceWrapper(request, uri);
            }
            catch (Exception e) { }
        }
        return null;
    }
    
    @Override
    public Optional<ResourceId> getResourceId() {
        return this.resource.getResourceId();
    }

    @Override
    public Locale getContentLocale() {
        return this.resource.getContentLocale();
    }

    @Override
    public Acl getAcl() {
        return this.resource.getAcl();
    }

    @Override
    public String getCharacterEncoding() {
        return this.resource.getCharacterEncoding();
    }

    @Override
    public List<Path> getChildURIs() {
        return this.resource.getChildURIs();
    }

    @Override
    public String getContentLanguage() {
        return this.resource.getContentLanguage();
    }

    @Override
    public Date getContentLastModified() {
        return this.resource.getContentLastModified();
    }

    @Override
    public long getContentLength() {
        return this.resource.getContentLength();
    }

    @Override
    public Principal getContentModifiedBy() {
        return this.resource.getContentModifiedBy();
    }

    @Override
    public String getContentType() {
        return this.resource.getContentType();
    }

    @Override
    public Principal getCreatedBy() {
        return this.resource.getCreatedBy();
    }

    @Override
    public Date getCreationTime() {
        return this.resource.getCreationTime();
    }

    @Override
    public String getEtag() {
        return this.resource.getEtag();
    }

    @Override
    public String getGuessedCharacterEncoding() {
        return this.resource.getGuessedCharacterEncoding();
    }

    @Override
    public Date getLastModified() {
        return this.resource.getLastModified();
    }

    @Override
    public Lock getLock() {
        return this.resource.getLock();
    }

    @Override
    public Principal getModifiedBy() {
        return this.resource.getModifiedBy();
    }

    @Override
    public String getName() {
        return this.resource.getName();
    }

    @Override
    public Principal getOwner() {
        return this.resource.getOwner();
    }

    @Override
    public Date getPropertiesLastModified() {
        return this.resource.getPropertiesLastModified();
    }

    @Override
    public Principal getPropertiesModifiedBy() {
        return this.resource.getPropertiesModifiedBy();
    }

    @Override
    public String getSerial() {
        return this.resource.getSerial();
    }

    @Override
    public String getTitle() {
        return this.resource.getTitle();
    }

    @Override
    public String getUserSpecifiedCharacterEncoding() {
        return this.resource.getUserSpecifiedCharacterEncoding();
    }

    @Override
    public boolean isCollection() {
        return this.resource.isCollection();
    }

    @Override
    public boolean isInheritedAcl() {
        return this.resource.isInheritedAcl();
    }

    @Override
    public void removeProperty(Namespace namespace, String name) {
        this.resource.removeProperty(namespace, name);
    }

    @Override
    public void removeProperty(PropertyTypeDefinition propDef) {
        this.resource.removeProperty(propDef);
    }

    @Override
    public void removeAllProperties() {
        this.resource.removeAllProperties();
    }

    @Override
    public List<Property> getProperties() {
        return this.resource.getProperties();
    }

    @Override
    public List<Property> getProperties(Namespace namespace) {
        return this.resource.getProperties(namespace);
    }

    @Override
    public Property getProperty(Namespace namespace, String name) {
        return this.resource.getProperty(namespace, name);
    }

    @Override
    public Property getProperty(PropertyTypeDefinition type) {
        return this.resource.getProperty(type);
    }

    @Override
    public Property getPropertyByPrefix(String prefix, String name) {
        return this.resource.getPropertyByPrefix(prefix, name);
    }

    @Override
    public String getResourceType() {
        return this.resource.getResourceType();
    }

    @Override
    public Path getURI() {
        return this.resource.getURI();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return this.resource.clone();
    }

    @Override
    public boolean isReadRestricted() {
        return this.resource.isReadRestricted();
    }
    
    @Override
    public boolean isPublished() {
        return this.resource.isPublished();
    }

    @Override
    public Date getPublishDate() {
        return this.resource.getPublishDate();
    }

    @Override
    public boolean hasPublishDate() {
        return this.resource.hasPublishDate();
    }
    
    @Override
    public Date getUnpublishDate() {
        return this.resource.getUnpublishDate();
    }

    @Override
    public void addProperty(Property property) {
        this.resource.addProperty(property);
    }
    
    @Override
    public Iterator<Property> iterator() {
        return this.resource.iterator();
    }

}
