/* Copyright (c) 2009, University of Oslo, Norway
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import vtk.repository.resourcetype.Content;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.Principal;

/**
 * Holds property evaluation state and info for and a single resource being
 * changed.
 */
public class PropertyEvaluationContext {

    /**
     * Specifies what type of change that is occuring to resource under
     * evaluation.
     */
    public enum Type {
        Create,
        ContentChange,
        NameChange,
        CommentsChange,
        PropertiesChange,
        SystemPropertiesChange,
        InheritablePropertiesChange,
        LoadRevision
    }

    private Type evaluationType;
    private ResourceImpl originalResource;
    private ResourceImpl newResource;
    private ResourceImpl suppliedResource;
    private Revision revision;
    private Content content;
    private boolean collection;
    private final Date time = new Date();
    private Principal principal;
    private Map<String, Object> propertyValueMap;
    private StoreContext storeContext;
    
    private final List<PropertyTypeDefinition> lateEvaluationPropDefs = new ArrayList<>();

    private Map<String, Object> contextAttributes = new HashMap<>();

    public static PropertyEvaluationContext propertiesChangeContext(ResourceImpl originalResource,
            ResourceImpl suppliedResource, Principal principal, Content content) throws InternalRepositoryException {
        PropertyEvaluationContext ctx = new PropertyEvaluationContext(originalResource,
                originalResource.isCollection(), principal, content, Type.PropertiesChange);
        ctx.suppliedResource = suppliedResource;
        return ctx;
    }

    public static PropertyEvaluationContext inheritablePropertiesChangeContext(ResourceImpl originalResource,
            ResourceImpl suppliedResource, Principal principal, Content content) throws InternalRepositoryException {
        PropertyEvaluationContext ctx = new PropertyEvaluationContext(originalResource,
                originalResource.isCollection(), principal, content, Type.InheritablePropertiesChange);
        ctx.suppliedResource = suppliedResource;
        return ctx;
    }
    
    public static PropertyEvaluationContext createResourceContext(ResourceImpl originalResource, boolean collection,
            Principal principal, Content content) throws InternalRepositoryException {
        PropertyEvaluationContext ctx = new PropertyEvaluationContext(originalResource, collection, principal, content,
                Type.Create);
        return ctx;
    }

    public static PropertyEvaluationContext contentChangeContext(ResourceImpl originalResource, Principal principal,
            Content content) throws InternalRepositoryException {
        return new PropertyEvaluationContext(originalResource, originalResource.isCollection(), principal, content,
                Type.ContentChange);
    }

    public static PropertyEvaluationContext revisionLoadContext(ResourceImpl originalResource, Principal principal,
            Content content, Revision revision) throws InternalRepositoryException {
        PropertyEvaluationContext ctx = new PropertyEvaluationContext(
                originalResource, originalResource.isCollection(), principal, content,
                Type.LoadRevision);
        ctx.revision = revision;
        return ctx;
    }

    public static PropertyEvaluationContext nameChangeContext(ResourceImpl originalResource,
            ResourceImpl suppliedResource, Principal principal, Content content) throws InternalRepositoryException {
        PropertyEvaluationContext ctx = new PropertyEvaluationContext(originalResource,
                originalResource.isCollection(), principal, content, Type.NameChange);
        ctx.suppliedResource = suppliedResource;
        ctx.newResource = suppliedResource;
        return ctx;
    }

    public static PropertyEvaluationContext commentsChangeContext(ResourceImpl originalResource,
            ResourceImpl suppliedResource, Principal principal, Content content) throws InternalRepositoryException {
        PropertyEvaluationContext ctx = new PropertyEvaluationContext(originalResource,
                originalResource.isCollection(), principal, content, Type.CommentsChange);
        ctx.suppliedResource = suppliedResource;
        return ctx;
    }

    public static PropertyEvaluationContext systemChangeContext(ResourceImpl originalResource,
            ResourceImpl suppliedResource, Principal principal, Content content) throws InternalRepositoryException {
        PropertyEvaluationContext ctx = new PropertyEvaluationContext(originalResource,
                originalResource.isCollection(), principal, content, Type.SystemPropertiesChange);
        ctx.suppliedResource = suppliedResource;
        return ctx;
    }

    private PropertyEvaluationContext(ResourceImpl originalResource, boolean collection, Principal principal,
            Content content, Type evaluationType) {
        this.collection = collection;
        this.originalResource = originalResource;
        this.principal = principal;
        this.content = content;
        this.evaluationType = evaluationType;
        try {
            this.newResource = (ResourceImpl) originalResource.clone();
            this.newResource.removeAllProperties();

        } catch (CloneNotSupportedException e) {
            throw new InternalRepositoryException("Unable to clone resource '" + originalResource.getURI() + "'", e);
        }
    }

    public ResourceImpl getOriginalResource() {
        return this.originalResource;
    }

    public ResourceImpl getSuppliedResource() {
        return this.suppliedResource;
    }

    public ResourceImpl getNewResource() {
        return this.newResource;
    }

    public Principal getPrincipal() {
        return this.principal;
    }

    public Type getEvaluationType() {
        return this.evaluationType;
    }

    public Content getContent() {
        return this.content;
    }

    public boolean isCollection() {
        return this.collection;
    }

    public Date getTime() {
        return this.time;
    }
    
    public Revision getRevision() {
        return this.revision;
    }

    public void addPropertyValue(String propertyName, Object value) {
        if (this.propertyValueMap == null) {
            this.propertyValueMap = new HashMap<String, Object>();
        }
        this.propertyValueMap.put(propertyName, value);
    }

    public Object getPropertyValue(String propertyName) {
        if (this.propertyValueMap != null) {
            return this.propertyValueMap.get(propertyName);
        }
        return null;
    }

    public void addEvaluationAttribute(String name, Object value) {
        this.contextAttributes.put(name, value);
    }

    public Object getEvaluationAttribute(String name) {
        return this.contextAttributes.get(name);
    }
    
    public boolean shouldEvaluateInheritableProperty(PropertyTypeDefinition def) {
        if (this.evaluationType != Type.InheritablePropertiesChange) {
            return false;
        }
        if (!(this.storeContext instanceof InheritablePropertiesStoreContext)) {
            return false;
        }
        
        InheritablePropertiesStoreContext ipsc = (InheritablePropertiesStoreContext)this.storeContext;
        List<PropertyTypeDefinition> affectedProps = ipsc.getAffectedProperties();
        return affectedProps.contains(def);
    }

    public boolean isSystemChangeAffectedProperty(PropertyTypeDefinition propDef) {
        if (this.evaluationType != Type.SystemPropertiesChange) {
            return false;
        }

        if (!(this.storeContext instanceof SystemChangeContext)) {
            return false;
        }
        
        SystemChangeContext systemChangeContext  = (SystemChangeContext)storeContext;
        
        if (propDef == systemChangeContext.getSystemJobStatusPropDef()) {
            return true;
        }
        
        List<PropertyTypeDefinition> affectedProperties = systemChangeContext.getAffectedProperties();
        return affectedProperties == null || affectedProperties.contains(propDef);
    }
    
    public void addPropertyTypeDefinitionForLateEvaluation(PropertyTypeDefinition def) {
        this.lateEvaluationPropDefs.add(def);
    }
    
    public List<PropertyTypeDefinition> getLateEvalutionPropertyTypeDefinitions() {
        return this.lateEvaluationPropDefs;
    }
    
    public void setStoreContext(StoreContext storeContext) {
        this.storeContext = storeContext;
    }
    
    public StoreContext getStoreContext() {
        return this.storeContext;
    }
    
}
