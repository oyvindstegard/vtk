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
package org.vortikal.repositoryimpl;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.vortikal.repository.AuthorizationException;
import org.vortikal.repository.AuthorizationManager;
import org.vortikal.repository.InternalRepositoryException;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Property;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.Resource;
import org.vortikal.repository.ResourceTypeTree;
import org.vortikal.repository.resourcetype.ConstraintViolationException;
import org.vortikal.repository.resourcetype.Content;
import org.vortikal.repository.resourcetype.ContentModificationPropertyEvaluator;
import org.vortikal.repository.resourcetype.CreatePropertyEvaluator;
import org.vortikal.repository.resourcetype.MixinResourceTypeDefinition;
import org.vortikal.repository.resourcetype.NameChangePropertyEvaluator;
import org.vortikal.repository.resourcetype.OverridablePropertyTypeDefinition;
import org.vortikal.repository.resourcetype.PrimaryResourceTypeDefinition;
import org.vortikal.repository.resourcetype.PropertiesModificationPropertyEvaluator;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.ResourceTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.repositoryimpl.content.ContentImpl;
import org.vortikal.repositoryimpl.content.ContentRepresentationRegistry;
import org.vortikal.repositoryimpl.dao.ContentStore;
import org.vortikal.security.AuthenticationException;
import org.vortikal.security.Principal;
import org.vortikal.web.service.RepositoryAssertion;


public class RepositoryResourceHelperImpl 
    implements RepositoryResourceHelper, InitializingBean {

    private static Log logger = LogFactory.getLog(RepositoryResourceHelperImpl.class);

    private AuthorizationManager authorizationManager;
    private ResourceTypeTree resourceTypeTree;
    private PropertyManager propertyManager;
    
    // Needed for property-evaluation. Should be a reasonable dependency.
    private ContentStore contentStore;
    private ContentRepresentationRegistry contentRepresentationRegistry;
    
    public ResourceImpl create(Principal principal, String uri, boolean collection) throws IOException {

        ResourceImpl resource = 
            new ResourceImpl(uri, this.propertyManager, this.authorizationManager);

        if (collection) {
            resource.setChildURIs(new String[]{});
        }
        
        EvaluationContext ctx = getCreateEvaluationContext(resource, principal, collection);

        recursiveTreeEvaluation(ctx, this.resourceTypeTree.getRoot());
        return ctx.getNewResource();
    }

    
    public ResourceImpl propertiesChange(ResourceImpl originalResource, Principal principal,
            ResourceImpl suppliedResource) throws AuthenticationException, AuthorizationException,
               InternalRepositoryException, IOException {
        EvaluationContext ctx = getPropertiesChangeEvaluationContext(originalResource, suppliedResource, principal);

        recursiveTreeEvaluation(ctx, this.resourceTypeTree.getRoot());
        checkForDeadAndZombieProperties(ctx);
        return ctx.getNewResource();
    }    
    
    public ResourceImpl contentModification(ResourceImpl resource, Principal principal) throws IOException {
        EvaluationContext ctx = getContentChangeEvaluationContext(resource, principal);

        recursiveTreeEvaluation(ctx, this.resourceTypeTree.getRoot());
        checkForDeadAndZombieProperties(ctx);
        return ctx.getNewResource();
    }
    
    public ResourceImpl nameChange(ResourceImpl resource, Principal principal) throws IOException {
        EvaluationContext ctx = getNameChangeEvaluationContext(resource, principal);

        recursiveTreeEvaluation(ctx, this.resourceTypeTree.getRoot());
        checkForDeadAndZombieProperties(ctx);
        return ctx.getNewResource();
    }
    
    /**
     * XXX: This hard coded list must be replaced by standard prop handling methods..
     */
    public PropertySet getFixedCopyProperties(Resource resource,
            Principal principal, String destUri)
            throws CloneNotSupportedException {
        PropertySetImpl fixedProps = new PropertySetImpl(destUri);
        java.util.Date now = new java.util.Date();

        Property owner = (Property) resource.getProperty(
                Namespace.DEFAULT_NAMESPACE, PropertyType.OWNER_PROP_NAME)
                .clone();
        owner.setPrincipalValue(principal);
        fixedProps.addProperty(owner);

        Property creationTime = (Property) resource.getProperty(
                Namespace.DEFAULT_NAMESPACE,
                PropertyType.CREATIONTIME_PROP_NAME).clone();
        creationTime.setDateValue(now);
        fixedProps.addProperty(creationTime);

        Property lastModified = (Property) resource.getProperty(
                Namespace.DEFAULT_NAMESPACE,
                PropertyType.LASTMODIFIED_PROP_NAME).clone();
        lastModified.setDateValue(now);
        fixedProps.addProperty(lastModified);

        Property contentLastModified = (Property) resource.getProperty(
                Namespace.DEFAULT_NAMESPACE,
                PropertyType.CONTENTLASTMODIFIED_PROP_NAME).clone();
        contentLastModified.setDateValue(now);
        fixedProps.addProperty(contentLastModified);

        Property propertiesLastModified = (Property) resource.getProperty(
                Namespace.DEFAULT_NAMESPACE,
                PropertyType.PROPERTIESLASTMODIFIED_PROP_NAME).clone();
        propertiesLastModified.setDateValue(now);
        fixedProps.addProperty(propertiesLastModified);

        Property createdBy = (Property) resource.getProperty(
                Namespace.DEFAULT_NAMESPACE, PropertyType.CREATEDBY_PROP_NAME)
                .clone();
        createdBy.setPrincipalValue(principal);
        fixedProps.addProperty(createdBy);

        Property modifiedBy = (Property) resource.getProperty(
                Namespace.DEFAULT_NAMESPACE, PropertyType.MODIFIEDBY_PROP_NAME)
                .clone();
        modifiedBy.setPrincipalValue(principal);
        fixedProps.addProperty(modifiedBy);

        Property contentModifiedBy = (Property) resource.getProperty(
                Namespace.DEFAULT_NAMESPACE,
                PropertyType.CONTENTMODIFIEDBY_PROP_NAME).clone();
        contentModifiedBy.setPrincipalValue(principal);
        fixedProps.addProperty(contentModifiedBy);

        Property propertiesModifiedBy = (Property) resource.getProperty(
                Namespace.DEFAULT_NAMESPACE,
                PropertyType.PROPERTIESMODIFIEDBY_PROP_NAME).clone();
        propertiesModifiedBy.setPrincipalValue(principal);
        fixedProps.addProperty(propertiesModifiedBy);

        return fixedProps;
    }

    private void checkForDeadAndZombieProperties(EvaluationContext ctx) {
        ResourceImpl newResource = ctx.getNewResource();

        Resource resource = ctx.getSuppliedResource();
        if (resource == null)
            resource = ctx.getOriginalResource();
        
        for (Iterator i = resource.getProperties().iterator(); i.hasNext();) {
            Property suppliedProp = (Property) i.next();
            PropertyTypeDefinition propDef = suppliedProp.getDefinition();
            
            if (propDef == null) {
                // Dead property, preserve
                newResource.addProperty(suppliedProp);
            } else if (newResource.getProperty(propDef) == null) {
                // If it hasn't been set for the new resource, check if zombie
                ResourceTypeDefinition[] rts = 
                    resourceTypeTree.getPrimaryResourceTypesForPropDef(propDef);

                for (ResourceTypeDefinition definition: rts) {
                    if (newResource.isOfType(definition)) {
                        // Zombie prop, preserve
                        newResource.addProperty(suppliedProp);
                        break;
                    }
                }
            }
        }

    }
    
    private boolean recursiveTreeEvaluation(EvaluationContext ctx, 
            PrimaryResourceTypeDefinition rt) throws IOException {

        // Check resource type assertions
        if (!checkAssertions(rt, ctx.getNewResource(), ctx.getPrincipal())) {
            return false;
        }
        
        // Set resource type
        ctx.getNewResource().setResourceType(rt.getName());
        
        // For all prop defs, do evaluation
        PropertyTypeDefinition[] propertyDefinitions = rt.getPropertyTypeDefinitions();
        for (PropertyTypeDefinition def: propertyDefinitions) {
            System.out.println("__eval_regular_prop: " + rt.getName() + ":" + def.getName());
            evaluateManagedProperty(ctx, def);
        }

        // Evaluating overridden properties
        OverridablePropertyTypeDefinition[] overrides = rt.getOverridablePropertyTypeDefinitions();
        
        if (overrides != null)
            for (OverridablePropertyTypeDefinition override: overrides) {
                System.out.println("__eval_overridden_prop: " + rt.getName() + ":" + override.getName());
                evaluateManagedProperty(ctx, override);
            }

        
        // For all prop defs in mixin types, also do evaluation
        List<MixinResourceTypeDefinition> mixinTypes = this.resourceTypeTree.getMixinTypes(rt);
        for (MixinResourceTypeDefinition mixinDef: mixinTypes) {
            PropertyTypeDefinition[] mixinPropDefs = mixinDef.getPropertyTypeDefinitions();
            for (PropertyTypeDefinition def: mixinPropDefs) {
                evaluateManagedProperty(ctx, def);
            }
        }

        // Trigger child evaluation
        List<PrimaryResourceTypeDefinition> childTypes = 
            this.resourceTypeTree.getResourceTypeDefinitionChildren(rt);
        for (PrimaryResourceTypeDefinition childDef: childTypes) {
            if (recursiveTreeEvaluation(ctx, childDef)) {
                break;
            }
        }
      
        return true;
    }

    private void evaluateManagedProperty(EvaluationContext ctx,
                                         PropertyTypeDefinition propDef) {

        Property evaluatedProp = null;
        switch (ctx.getEvaluationType()) {
        case Create:
            evaluatedProp = evaluateCreate(ctx, propDef);
            break;
        case ContentChange:
            evaluatedProp = evaluateContentChange(ctx, propDef);
            break;
        case PropertiesChange:
            evaluatedProp = evaluatePropertiesChange(ctx, propDef);
            break;
        default:
            evaluatedProp = evaluateNameChange(ctx, propDef);
            break;
        }
        
        ResourceImpl newResource = ctx.getNewResource();

        if (evaluatedProp == null && propDef.isMandatory()) {
            Value defaultValue = propDef.getDefaultValue();
            if (defaultValue == null) {
                throw new InternalRepositoryException(
                    "Property " + propDef + " is " +
                    "mandatory and evaluator returned false, but no default value is set. " +
                    "Resource " + newResource + " not evaluated.");
            }
            evaluatedProp = this.propertyManager.createProperty(
                propDef.getNamespace(), propDef.getName());
            evaluatedProp.setValue(defaultValue);
        } 

        if (evaluatedProp != null) {
            newResource.addProperty(evaluatedProp);
        }
    }


    private Property evaluateCreate(EvaluationContext ctx, 
            PropertyTypeDefinition propDef) {
    
        CreatePropertyEvaluator evaluator = propDef.getCreateEvaluator();            

        if (evaluator == null) {
            return null;
        }
        
        Property property = this.propertyManager.createProperty(
                    propDef.getNamespace(), propDef.getName());

        Resource newResource = ctx.getNewResource();

        boolean evaluated = evaluator.create(ctx.getPrincipal(), property, newResource, ctx.isCollection(), ctx.getTime());

        if (!evaluated) {
            return null;
        } 
        
        if (!property.isValueInitialized()) {
            throw new InternalRepositoryException(
                "Evaluator " + evaluator + " on resource '"
                + newResource.getURI() + "' returned not value initialized property " + 
                propDef);
        } 

        return property;
    }
        

    /**
     * The evaluator will be given a clone of the original property as input if it 
     * previously existed, or an uninitialized property otherwise.
     * 
     * @return the evaluated prop or null if a {@link ContentModificationPropertyEvaluator} 
     * evaluator exists, depending of whether it evaluated to true or false. 
     * Otherwise return the original property, if it exists.
     */
    private Property evaluateContentChange(EvaluationContext ctx, 
            PropertyTypeDefinition propDef) {

        Resource newResource = ctx.getNewResource();
        Content content = null;

        ContentModificationPropertyEvaluator evaluator =
            propDef.getContentModificationEvaluator();
        
        Property prop = ctx.getOriginalResource().getProperty(propDef);
        if (prop != null) {
            try {
                prop = (Property) prop.clone();
            } catch (CloneNotSupportedException e) {
                throw new InternalRepositoryException(
                        "Couldn't clone property " + propDef + "on resource '"
                                + newResource.getURI() + "'", e);
            }
        }
        
        if (evaluator != null) {
            if (!ctx.getOriginalResource().isCollection()) {
                content = new ContentImpl(newResource.getURI(), this.contentStore,
                                          this.contentRepresentationRegistry);
            }
            // Initialize prop if necessary
            if (prop == null)
                prop = this.propertyManager.createProperty(
                    propDef.getNamespace(), propDef.getName());
        
            boolean evaluated =
                evaluator.contentModification(ctx.getPrincipal(), prop, newResource, content, ctx.getTime());
            if (!evaluated) {
                return null;
            } 
            
            if (!prop.isValueInitialized()) {
                throw new InternalRepositoryException(
                    "Evaluator " + evaluator + " on resource '"
                    + newResource.getURI() + "' returned not value initialized property " + 
                    propDef);
            }
        } else if (propDef instanceof OverridablePropertyTypeDefinition) {
            // Trying to fix overridden properties with default value that changes
            return null;
        }
        return prop;
    }

    private Property evaluateNameChange(EvaluationContext ctx, 
            PropertyTypeDefinition propDef) {
    
        Property property = ctx.getOriginalResource().getProperty(propDef);
        if (property != null) {
            try {
                property = (Property) property.clone();
            } catch (CloneNotSupportedException e) {
                throw new InternalRepositoryException(
                        "Couldn't clone property " + property + "on resource '"
                        + ctx.getNewResource().getURI() + "'", e);
            }
        }
        
        NameChangePropertyEvaluator evaluator = 
            propDef.getNameModificationEvaluator();

        if (evaluator == null) {
            if (propDef instanceof OverridablePropertyTypeDefinition) {
                // Trying to fix overridden properties with default value that changes
                return null;
            }
            return property;
        }
        
        if (property == null)
            property = this.propertyManager.createProperty(
                    propDef.getNamespace(), propDef.getName());

        Resource newResource = ctx.getNewResource();

        boolean evaluated = evaluator.nameModification(
                ctx.getPrincipal(), property, newResource, ctx.getTime());

        if (!evaluated) {
            return null;
        } 
        
        if (!property.isValueInitialized()) {
            throw new InternalRepositoryException(
                "Evaluator " + evaluator + " on resource '"
                + newResource.getURI() + "' returned not value initialized property " + 
                propDef);
        } 

        return property;
    }
    
    private Property evaluatePropertiesChange(EvaluationContext ctx, 
            PropertyTypeDefinition propDef) {

        // Check for user change or addition
        Property property = checkForUserAdditionOrChange(ctx, propDef);
        if (property != null)
            return property;
        
        // Check for user deletion
        if (checkForUserDeletion(ctx, propDef))
            return null;
        
        Resource newResource = ctx.getNewResource();
        try {
            property = ctx.getOriginalResource().getProperty(propDef);
            if (property != null)
                property = (Property) property.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalRepositoryException(
                    "Couldn't clone property " + property + "on resource '"
                    + ctx.getNewResource().getURI() + "'", e);
        }

        PropertiesModificationPropertyEvaluator evaluator = 
            propDef.getPropertiesModificationEvaluator();

        if (evaluator != null) {
            if (property == null)
                property = this.propertyManager.createProperty(
                        propDef.getNamespace(), propDef.getName());

            boolean evaluated = evaluator.propertiesModification(
                    ctx.getPrincipal(), property, newResource, ctx.getTime());

            if (!evaluated) {
                return null;
            } 
            
            if (!property.isValueInitialized()) {
                throw new InternalRepositoryException(
                    "Evaluator " + evaluator + " on resource '"
                    + newResource.getURI() + "' returned not value initialized property " + 
                    propDef);
            } 
        } else {
            // On propchange we have to do contentchange on all props not having a 
            // propchangeevaluator.
            property = evaluateContentChange(ctx, propDef);
        }

        return property;
    }

    private boolean checkForUserDeletion(EvaluationContext ctx,
            PropertyTypeDefinition propDef) throws ConstraintViolationException {
        Property originalProp = ctx.getOriginalResource().getProperty(propDef);
        Property suppliedProp = ctx.getSuppliedResource().getProperty(propDef);
        
        if (originalProp != null && suppliedProp == null) {
            return true;
        }
        
        return false;
    }


    private Property checkForUserAdditionOrChange(EvaluationContext ctx,
                                                  PropertyTypeDefinition propDef) {
        Property originalProp = ctx.getOriginalResource().getProperty(propDef);
        Property suppliedProp = ctx.getSuppliedResource().getProperty(propDef);
     
        try {
            // Added
            if (originalProp == null && suppliedProp != null)
                return (Property) suppliedProp.clone();

            // Changed
            if (originalProp != null && suppliedProp != null
                    && !originalProp.equals(suppliedProp)) {
                return (Property) suppliedProp.clone();
            }
        } catch (CloneNotSupportedException e) {
            throw new InternalRepositoryException(
                    "Couldn't clone property " + suppliedProp + "on resource '"
                            + ctx.getNewResource().getURI() + "'", e);
        }

        return null;

    }
    

    /** 
     * Checking that all resource type assertions match for resource 
     */
    private boolean checkAssertions(PrimaryResourceTypeDefinition rt,
                                    Resource resource, Principal principal) {

        RepositoryAssertion[] assertions = rt.getAssertions();

        if (assertions != null) {
            for (int i = 0; i < assertions.length; i++) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Checking assertion "
                                 + assertions[i] + " for resource " + resource);
                }

                if (!assertions[i].matches(resource, principal)) {
                    
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                            "Checking for type '" + rt.getName() + "', resource " + resource
                            + " failed, unmatched assertion: " + assertions[i]);
                    }
                    return false;
                }
                
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Checking for type '" + rt.getName() + "', resource "
                         + resource + " succeeded, assertions matched: "
                         + (assertions != null ? Arrays.asList(assertions) : null));
        }
        return true;
    }

    
    public void afterPropertiesSet() throws Exception {
        if (this.propertyManager == null) {
            throw new BeanInitializationException(
                "Property 'propertyManager' not set.");
        } 
        if (this.contentStore == null) {
            throw new BeanInitializationException(
                "Property 'contentStore' not set.");
        }
        if (this.contentRepresentationRegistry == null) {
            throw new BeanInitializationException(
                "Property 'contentRepresentationRegistry' not set.");
        }
        if (this.resourceTypeTree == null) {
            throw new BeanInitializationException(
                "Property 'resourceTypeTree' not set.");
        }
        if (this.authorizationManager == null) {
            throw new BeanInitializationException(
                "Property 'authorizationManager' not set.");
        }
        
    }

    
    public void setContentStore(ContentStore contentStore) {
        this.contentStore = contentStore;
    }

    public void setContentRepresentationRegistry(
        ContentRepresentationRegistry contentRepresentationRegistry) {
        this.contentRepresentationRegistry = contentRepresentationRegistry;
    }
    
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    public ResourceTypeTree getResourceTypeTree() {
        return this.resourceTypeTree;
    }

    public void setPropertyManager(PropertyManager propertyManager) {
        this.propertyManager = propertyManager;
    }

    public void setAuthorizationManager(AuthorizationManager authorizationManager) {
        this.authorizationManager = authorizationManager;
    }
    
    private enum EvaluationType {Create, ContentChange, PropertiesChange, NameChange}
    
    private EvaluationContext getPropertiesChangeEvaluationContext(ResourceImpl originalResource, 
            ResourceImpl suppliedResource, Principal principal) throws InternalRepositoryException {
        EvaluationContext ctx = new EvaluationContext(originalResource, principal, EvaluationType.PropertiesChange);    
        ctx.suppliedResource = suppliedResource;
        return ctx;
    }

    private EvaluationContext getCreateEvaluationContext(ResourceImpl originalResource, 
            Principal principal, boolean collection) throws InternalRepositoryException {
        EvaluationContext ctx = new EvaluationContext(originalResource, principal, EvaluationType.Create);    
        ctx.collection = collection;
        return ctx;
    }
    
    private EvaluationContext getContentChangeEvaluationContext(ResourceImpl originalResource, 
            Principal principal) throws InternalRepositoryException {
        return new EvaluationContext(originalResource, principal, EvaluationType.ContentChange);    
    }
    
    private EvaluationContext getNameChangeEvaluationContext(ResourceImpl originalResource, 
            Principal principal) throws InternalRepositoryException {
        return new EvaluationContext(originalResource, principal, EvaluationType.NameChange);    
    }
    
    private class EvaluationContext {
        private EvaluationType evaluationType;
        private Resource originalResource;
        private ResourceImpl newResource;

        private boolean collection;
        private Date time = new Date();
        private Resource suppliedResource; 
        private Principal principal;

        EvaluationContext(ResourceImpl originalResource, Principal principal, EvaluationType evaluationType) {
            this.originalResource = originalResource;
            this.principal = principal;
            this.evaluationType = evaluationType;
            try {
                newResource = originalResource.cloneWithoutProperties();
            } catch (CloneNotSupportedException e) {
                throw new InternalRepositoryException(
                        "Unable to clone resource '" + originalResource.getURI() + "'", e);
            }
            
        }

        public Resource getOriginalResource() {
            return this.originalResource;
        }

        public Resource getSuppliedResource() {
            return this.suppliedResource;
        }

        public ResourceImpl getNewResource() {
            return this.newResource;
        } 
        
        public Principal getPrincipal() {
            return this.principal;
        }
        public EvaluationType getEvaluationType() {
            return evaluationType;
        }
        public boolean isCollection() {
            return collection;
        }
        public Date getTime() {
            return time;
        }
        
    }

}
