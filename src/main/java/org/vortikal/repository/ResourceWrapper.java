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
package org.vortikal.repository;

import java.net.URLDecoder;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.vortikal.edit.editor.ResourceWrapperManager;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.security.Principal;

public class ResourceWrapper implements Resource {

    private ResourceWrapperManager resourceManager;
    
    private Resource resource;


    public ResourceWrapper(ResourceWrapperManager resourceManager) {
        super();
        this.resourceManager = resourceManager;
    }

    public Resource getResource() {
        return this.resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public Property getPropertyByName(String name) {
        for (Property prop : getProperties()) {
            if (prop.getDefinition().getName().equals(name)) {
                return prop;
            }
        }
        return null;
    }
    
    public String getValueByName(String name) {
        for (Property prop : getProperties()) {
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

    public ResourceWrapper getPropResource(PropertyTypeDefinition propDef) {
        if (propDef.getType().equals(PropertyType.Type.IMAGE_REF)) {
            try {
                String ref = resource.getProperty(propDef).getStringValue();
                ref = URLDecoder.decode(ref, "utf-8");
                Path uri = Path.fromString(ref);
                return this.resourceManager.createResourceWrapper(uri);
            } catch (Exception e) { }
        }
        return null; 
    }
    
    public Locale getContentLocale() {
        return this.resource.getContentLocale();
    }
    
    public Acl getAcl() {
        return this.resource.getAcl();
    }

    public String getCharacterEncoding() {
        return this.resource.getCharacterEncoding();
    }

    public List<Path> getChildURIs() {
        return this.resource.getChildURIs();
    }

    public String getContentLanguage() {
        return this.resource.getContentLanguage();
    }

    public Date getContentLastModified() {
        return this.resource.getContentLastModified();
    }

    public long getContentLength() {
        return this.resource.getContentLength();
    }

    public Principal getContentModifiedBy() {
        return this.resource.getContentModifiedBy();
    }

    public String getContentType() {
        return this.resource.getContentType();
    }

    public Principal getCreatedBy() {
        return this.resource.getCreatedBy();
    }

    public Date getCreationTime() {
        return this.resource.getCreationTime();
    }

    public String getEtag() {
        return this.resource.getEtag();
    }

    public String getGuessedCharacterEncoding() {
        return this.resource.getGuessedCharacterEncoding();
    }

    public Date getLastModified() {
        return this.resource.getLastModified();
    }

    public Lock getLock() {
        return this.resource.getLock();
    }

    public Principal getModifiedBy() {
        return this.resource.getModifiedBy();
    }

    public String getName() {
        return this.resource.getName();
    }

    public Principal getOwner() {
        return this.resource.getOwner();
    }

    public Date getPropertiesLastModified() {
        return this.resource.getPropertiesLastModified();
    }

    public Principal getPropertiesModifiedBy() {
        return this.resource.getPropertiesModifiedBy();
    }

    public String getSerial() {
        return this.resource.getSerial();
    }

    public String getTitle() {
        return this.resource.getTitle();
    }

    public String getUserSpecifiedCharacterEncoding() {
        return this.resource.getUserSpecifiedCharacterEncoding();
    }

    public boolean isCollection() {
        return this.resource.isCollection();
    }

    public boolean isInheritedAcl() {
        return this.resource.isInheritedAcl();
    }

    public void removeProperty(Namespace namespace, String name) {
        this.resource.removeProperty(namespace, name);
    }

    public void removeProperty(PropertyTypeDefinition propDef) {
        this.resource.removeProperty(propDef);
    }

    public void setAcl(Acl acl) {
        this.resource.setAcl(acl);
    }

    public void setInheritedAcl(boolean inheritedAcl) {
        this.resource.setInheritedAcl(inheritedAcl);
    }

    public List<Property> getProperties() {
        return this.resource.getProperties();
    }

    public List<Property> getProperties(Namespace namespace) {
        return this.resource.getProperties(namespace);
    }

    public Property getProperty(Namespace namespace, String name) {
        return this.resource.getProperty(namespace, name);
    }

    public Property getProperty(PropertyTypeDefinition type) {
        return this.resource.getProperty(type);
    }

    public Property getPropertyByPrefix(String prefix, String name) {
        return this.resource.getPropertyByPrefix(prefix, name);
    }

    public String getResourceType() {
        return this.resource.getResourceType();
    }

    public Path getURI() {
        return this.resource.getURI();
    }
    
    public Object clone() throws CloneNotSupportedException {
        return this.resource.clone();
    }
    
    public boolean isReadRestricted() {
        return this.resource.isReadRestricted();
    }

    public void addProperty(Property property) {
        this.resource.addProperty(property);
        
    }

}
