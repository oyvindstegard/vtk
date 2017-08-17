/* Copyright (c) 2006, 2007, 2009, University of Oslo, Norway
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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.PropertyEvaluationContext.Type;
import vtk.repository.resourcetype.Content;
import vtk.repository.resourcetype.LatePropertyEvaluator;
import vtk.repository.resourcetype.MixinResourceTypeDefinition;
import vtk.repository.resourcetype.PrimaryResourceTypeDefinition;
import vtk.repository.resourcetype.PropertyEvaluator;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.RepositoryAssertion;
import vtk.repository.resourcetype.ResourceTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.security.AuthenticationException;
import vtk.security.Principal;

public class RepositoryResourceHelper {

    private static Logger logger = LoggerFactory.getLogger(RepositoryResourceHelper.class);

    private AuthorizationManager authorizationManager;
    private ResourceTypeTree resourceTypeTree;
    
    public ResourceImpl create(Principal principal, ResourceImpl resource, boolean collection, Content content) throws IOException {
        logger.debug("Evaluate create: {}", resource.getURI());
        PropertyEvaluationContext ctx = PropertyEvaluationContext
                .createResourceContext(resource, collection, principal, content);
        recursiveTreeEvaluation(ctx, this.resourceTypeTree.getRoot());
        lateEvaluation(ctx);
        return ctx.getNewResource();
    }

    public ResourceImpl propertiesChange(ResourceImpl originalResource, Principal principal,
            ResourceImpl suppliedResource, Content content) throws AuthenticationException, AuthorizationException,
            InternalRepositoryException, IOException {
        logger.debug("Evaluate properties change: {}", originalResource.getURI());

        PropertyEvaluationContext ctx = PropertyEvaluationContext.propertiesChangeContext(originalResource,
                suppliedResource, principal, content);
        recursiveTreeEvaluation(ctx, this.resourceTypeTree.getRoot());
        lateEvaluation(ctx);
        checkForDeadAndZombieProperties(ctx);
        return ctx.getNewResource();
    }

    public ResourceImpl inheritablePropertiesChange(ResourceImpl originalResource, Principal principal,
            ResourceImpl suppliedResource, Content content, InheritablePropertiesStoreContext storeContext) throws AuthenticationException, AuthorizationException,
            InternalRepositoryException, IOException {
        logger.debug("Evaluate inhertiable properties change: {}", originalResource.getURI());

        PropertyEvaluationContext ctx = PropertyEvaluationContext.inheritablePropertiesChangeContext(originalResource,
                suppliedResource, principal, content);
        ctx.setStoreContext(storeContext);
        recursiveTreeEvaluation(ctx, this.resourceTypeTree.getRoot());
        lateEvaluation(ctx);
        checkForDeadAndZombieProperties(ctx);
        return ctx.getNewResource();
    }
    
    public ResourceImpl commentsChange(ResourceImpl originalResource, Principal principal, 
            ResourceImpl suppliedResource, Content content)
            throws AuthenticationException, AuthorizationException, InternalRepositoryException, IOException {

        logger.debug("Evaluate comments change: {}", originalResource.getURI());
        PropertyEvaluationContext ctx = PropertyEvaluationContext.commentsChangeContext(originalResource,
                suppliedResource, principal, content);
        recursiveTreeEvaluation(ctx, this.resourceTypeTree.getRoot());
        lateEvaluation(ctx);
        checkForDeadAndZombieProperties(ctx);
        return ctx.getNewResource();
    }

    public ResourceImpl contentModification(ResourceImpl resource, Principal principal, Content content) throws IOException {
        logger.debug("Evaluate content modification: {}", resource.getURI());
        PropertyEvaluationContext ctx = PropertyEvaluationContext.contentChangeContext(resource, principal, content);
        recursiveTreeEvaluation(ctx, this.resourceTypeTree.getRoot());
        lateEvaluation(ctx);
        checkForDeadAndZombieProperties(ctx);
        return ctx.getNewResource();
    }

    public ResourceImpl loadRevision(ResourceImpl resource, Principal principal, Content content, Revision revision) throws IOException {
        logger.debug("Evaluate on load: {}, revision", resource.getURI(), revision);
        PropertyEvaluationContext ctx = PropertyEvaluationContext.revisionLoadContext(resource, principal, content, revision);
        recursiveTreeEvaluation(ctx, this.resourceTypeTree.getRoot());
        lateEvaluation(ctx);
        checkForDeadAndZombieProperties(ctx);
        return ctx.getNewResource();
    }

    public ResourceImpl nameChange(ResourceImpl original, ResourceImpl resource, Principal principal, Content content)
            throws IOException {
        logger.debug("Evaluate name change: {}", resource.getURI());
        PropertyEvaluationContext ctx = PropertyEvaluationContext.nameChangeContext(original, resource, principal,
                content);
        recursiveTreeEvaluation(ctx, this.resourceTypeTree.getRoot());
        lateEvaluation(ctx);
        checkForDeadAndZombieProperties(ctx);
        return ctx.getNewResource();
    }

    public ResourceImpl systemChange(ResourceImpl originalResource, Principal principal, 
            ResourceImpl suppliedResource, Content content, SystemChangeContext systemChangeContext)
            throws AuthenticationException, AuthorizationException, InternalRepositoryException, IOException {
        logger.debug("Evaluate system change: {}", originalResource.getURI());
        if (systemChangeContext == null) {
            throw new IllegalArgumentException("System change context cannot be null for system change evaluation");
        }
        
        PropertyEvaluationContext ctx = PropertyEvaluationContext.systemChangeContext(originalResource,
                suppliedResource, principal, content);
        ctx.setStoreContext(systemChangeContext);
        recursiveTreeEvaluation(ctx, this.resourceTypeTree.getRoot());
        lateEvaluation(ctx);
        checkForDeadAndZombieProperties(ctx);
        return ctx.getNewResource();
    }

    /**
     * XXX: This hard coded list must be replaced by standard prop handling
     * methods..
     */
    public PropertySetImpl getFixedCopyProperties(ResourceImpl resource, Principal principal, Path destUri)
            throws CloneNotSupportedException {
        PropertySetImpl fixedProps = new PropertySetImpl();
        fixedProps.setUri(destUri);

        final java.util.Date now = new java.util.Date();

        Property owner = (Property) resource.getProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.OWNER_PROP_NAME)
                .clone();
        owner.setPrincipalValue(principal);
        fixedProps.addProperty(owner);

        Property creationTime = (Property) resource.getProperty(Namespace.DEFAULT_NAMESPACE,
                PropertyType.CREATIONTIME_PROP_NAME).clone();
        creationTime.setDateValue(now);
        fixedProps.addProperty(creationTime);

        Property lastModified = (Property) resource.getProperty(Namespace.DEFAULT_NAMESPACE,
                PropertyType.LASTMODIFIED_PROP_NAME).clone();
        lastModified.setDateValue(now);
        fixedProps.addProperty(lastModified);

        Property contentLastModified = (Property) resource.getProperty(Namespace.DEFAULT_NAMESPACE,
                PropertyType.CONTENTLASTMODIFIED_PROP_NAME).clone();
        contentLastModified.setDateValue(now);
        fixedProps.addProperty(contentLastModified);

        Property propertiesLastModified = (Property) resource.getProperty(Namespace.DEFAULT_NAMESPACE,
                PropertyType.PROPERTIESLASTMODIFIED_PROP_NAME).clone();
        propertiesLastModified.setDateValue(now);
        fixedProps.addProperty(propertiesLastModified);

        Property createdBy = (Property) resource.getProperty(Namespace.DEFAULT_NAMESPACE,
                PropertyType.CREATEDBY_PROP_NAME).clone();
        createdBy.setPrincipalValue(principal);
        fixedProps.addProperty(createdBy);

        Property modifiedBy = (Property) resource.getProperty(Namespace.DEFAULT_NAMESPACE,
                PropertyType.MODIFIEDBY_PROP_NAME).clone();
        modifiedBy.setPrincipalValue(principal);
        fixedProps.addProperty(modifiedBy);

        Property contentModifiedBy = (Property) resource.getProperty(Namespace.DEFAULT_NAMESPACE,
                PropertyType.CONTENTMODIFIEDBY_PROP_NAME).clone();
        contentModifiedBy.setPrincipalValue(principal);
        fixedProps.addProperty(contentModifiedBy);

        Property propertiesModifiedBy = (Property) resource.getProperty(Namespace.DEFAULT_NAMESPACE,
                PropertyType.PROPERTIESMODIFIEDBY_PROP_NAME).clone();
        propertiesModifiedBy.setPrincipalValue(principal);
        fixedProps.addProperty(propertiesModifiedBy);
        
        return fixedProps;
    }

    private void checkForDeadAndZombieProperties(PropertyEvaluationContext ctx) {
        Resource newResource = ctx.getNewResource();

        Resource resource = ctx.getSuppliedResource();
        if (resource == null)
            resource = ctx.getOriginalResource();

        for (Property suppliedProp : resource) {
            PropertyTypeDefinition propDef = suppliedProp.getDefinition();

            PrimaryResourceTypeDefinition[] rts = resourceTypeTree.getPrimaryResourceTypesForPropDef(propDef);

            if (rts == null) {
                // Dead property, no resource type connected to it.
                // Preserve only if not inheritable (a tad paranoid, inheritable should never be set for dead props.)
                if (!propDef.isInheritable()) {
                    newResource.addProperty(suppliedProp);
                }
                else {
                    newResource.removeProperty(propDef);
                }
            }
            else if (newResource.getProperty(propDef) == null) {

                // If it hasn't been set for the new resource, check if zombie
                boolean zombie = true;
                for (ResourceTypeDefinition definition : rts) {
                    if (this.resourceTypeTree.isContainedType(definition, newResource.getResourceType())) {
                        zombie = false;
                        break;
                    }
                }
                if (zombie) {
                    // Zombie property, preserve only if not inheritable, otherwise remove.
                    if (!propDef.isInheritable()) {
                        newResource.addProperty(suppliedProp);                        
                    }
                    else {
                        newResource.removeProperty(propDef);
                    }
                }
            }
        }
    }

    private boolean recursiveTreeEvaluation(PropertyEvaluationContext ctx, PrimaryResourceTypeDefinition rt)
            throws IOException {

        // Check resource type assertions
        if (!checkAssertions(rt, ctx)) {
            return false;
        }

        // Set resource type
        ctx.getNewResource().setResourceType(rt.getName());

        // For all prop defs, do evaluation
        PropertyTypeDefinition[] propertyDefinitions = rt.getPropertyTypeDefinitions();
        for (PropertyTypeDefinition def : propertyDefinitions) {
            if (def.getPropertyEvaluator() instanceof LatePropertyEvaluator) {
                ctx.addPropertyTypeDefinitionForLateEvaluation(def);
                continue;
            }

            evaluateManagedProperty(ctx, def);
        }

        // For all prop defs in mixin types, also do evaluation
        List<MixinResourceTypeDefinition> mixinTypes = this.resourceTypeTree.getMixinTypes(rt);
        for (MixinResourceTypeDefinition mixinDef : mixinTypes) {
            PropertyTypeDefinition[] mixinPropDefs = mixinDef.getPropertyTypeDefinitions();
            for (PropertyTypeDefinition def : mixinPropDefs) {
                if (def.getPropertyEvaluator() instanceof LatePropertyEvaluator) {
                    ctx.addPropertyTypeDefinitionForLateEvaluation(def);
                    continue;
                }
                
                evaluateManagedProperty(ctx, def);
            }
        }

        // Trigger child evaluation
        List<PrimaryResourceTypeDefinition> childTypes = this.resourceTypeTree.getResourceTypeDefinitionChildren(rt);

        for (PrimaryResourceTypeDefinition childDef : childTypes) {
            if (recursiveTreeEvaluation(ctx, childDef)) {
                break;
            }
        }
        return true;
    }
    
    private void lateEvaluation(PropertyEvaluationContext ctx) throws IOException {
        List<PropertyTypeDefinition> lateEvalPropDefs = ctx.getLateEvalutionPropertyTypeDefinitions();

        lateEvalPropDefs.sort((d1,d2) ->
            ((LatePropertyEvaluator)d1.getPropertyEvaluator())
                    .compareTo((LatePropertyEvaluator)d2.getPropertyEvaluator()));

        for (PropertyTypeDefinition propDef: lateEvalPropDefs) {
            evaluateManagedProperty(ctx, propDef);
        }
    }

    private void evaluateManagedProperty(PropertyEvaluationContext ctx, PropertyTypeDefinition propDef)
            throws IOException {

        Property evaluatedProp = doEvaluate(ctx, propDef);
        Resource newResource = ctx.getNewResource();

        if (evaluatedProp == null && propDef.isMandatory()) {
            Value defaultValue = propDef.getDefaultValue();
            if (defaultValue == null) {
                throw new InternalRepositoryException("Property " + propDef + " is mandatory with no default value, "
                        + "and evaluator either did not exist or returned false. " + "Resource " + newResource
                        + " not evaluated (resource type: " + newResource.getResourceType() + ")");
            }
            evaluatedProp = propDef.createProperty();
            evaluatedProp.setValue(defaultValue);
        }

        if (propDef.getValidator() != null && evaluatedProp != null) {
            propDef.getValidator().validate(evaluatedProp, ctx);
        }
        if (evaluatedProp != null) {
            newResource.addProperty(evaluatedProp);
        }
        else {
            newResource.removeProperty(propDef);
        }
    }

    private Property doEvaluate(PropertyEvaluationContext ctx, PropertyTypeDefinition propDef) throws IOException {

        final Property originalUnchanged = ctx.getOriginalResource().getProperty(propDef);
        
        if (propDef.isInheritable() 
                && !ctx.shouldEvaluateInheritableProperty(propDef)) {
            
            // An inheritable property that should not be changed now.
            // Keep it only if it is actually set on this resource (not inherited).
            if (originalUnchanged != null && !((PropertyImpl)originalUnchanged).isInherited()) {
                return originalUnchanged;
            }
            else {
                return null;
            }
        }
        
        // TODO currently we cannot store inheritable properties in
        // system change context (mutually exclusive modes). Consider if that
        // should be possible or not.
        if (ctx.getEvaluationType() == Type.SystemPropertiesChange) {
            if (! ctx.isSystemChangeAffectedProperty(propDef)) {
                // Not to be affected by system change, return original unchanged.
                return originalUnchanged;
            }
        }
        
        if (ctx.getEvaluationType() == Type.PropertiesChange ||
            ctx.getEvaluationType() == Type.SystemPropertiesChange ||
            ctx.getEvaluationType() == Type.InheritablePropertiesChange) {
            // Authorize property change, addition or deletion by user
            Property property = checkForUserAdditionOrChange(ctx, propDef);
            final boolean deletedByUser = checkForUserDeletion(ctx, propDef);

            if (property != null || deletedByUser) {
                if (propDef.getProtectionLevel() != null) {
                    try {
                        if (propDef.getProtectionLevel() == RepositoryAction.READ_WRITE
                                && !ctx.getOriginalResource().hasPublishDate()
                                && !isPublishDateProperty(propDef)) {
                            // Authorize for READ_WRITE_UNPUBLISHED instead, if original resource is
                            // unpublished and protection level is READ_WRITE
                            this.authorizationManager.authorizeAction(ctx.getOriginalResource().getURI(),
                                RepositoryAction.READ_WRITE_UNPUBLISHED, ctx.getPrincipal());
                        }
                        else {
                            this.authorizationManager.authorizeAction(ctx.getOriginalResource().getURI(), propDef
                                .getProtectionLevel(), ctx.getPrincipal());
                        }
                    }
                    catch (AuthorizationException e) {
                        throw new AuthorizationException("Principal " + ctx.getPrincipal()
                                + " not authorized to " + (deletedByUser?"delete":"set") + " property " 
                                + propDef + " (protectionLevel=" + propDef.getProtectionLevel() 
                                + ") on resource " + ctx.getNewResource(), e);
                    }
                }

                if (deletedByUser) {
                    logger.debug("Property deleted by user: {} for resource {}, type {}", 
                            propDef, ctx.getNewResource(), ctx.getNewResource().getResourceType());
                    return null;
                }
                else {
                    logger.debug("Property user-modified or added: {} for resource {}, type {}",
                            property, ctx.getNewResource(), ctx.getNewResource().getResourceType());
                    return property;
                }
            }
        }

        Resource newResource = ctx.getNewResource();

        PropertyEvaluator evaluator = propDef.getPropertyEvaluator();
        Property property = ctx.getOriginalResource().getProperty(propDef);

        // The evaluator will be given a clone of the original property as input
        // if it previously existed, or an uninitialized property otherwise.
        if (property != null) {
            try {
                property = (Property) property.clone();
            }
            catch (CloneNotSupportedException e) {
                throw new InternalRepositoryException("Error: unable to clone property " 
                        + propDef + "on resource '" + newResource.getURI() + "'", e);
            }
        }

        if (evaluator != null) {
            // Initialize prop if necessary
            if (property == null) {
                property = propDef.createProperty();
            }
            boolean evaluated = evaluator.evaluate(property, ctx);
            if (!evaluated) {
                logger.debug("Property not evaluated: {} by evaluator {} for resource {}, type {}",
                        propDef, evaluator, ctx.getNewResource(), ctx.getNewResource().getResourceType());
                return null;
            }
            if (!property.isValueInitialized()) {
                throw new InternalRepositoryException("Evaluator " + evaluator + " on resource '"
                        + newResource.getURI() + "' returned un-initialized value: " + propDef);
            }
        }
        if (property == null && propDef.isMandatory()) {
            Value defaultValue = propDef.getDefaultValue();
            if (defaultValue == null) {
                throw new InternalRepositoryException("Property " + propDef + " is mandatory with no default value, "
                        + "and evaluator either did not exist or returned false. " + "Resource " + newResource
                        + " not evaluated (resource type: " + newResource.getResourceType() + ")");
            }
            property = propDef.createProperty();
            property.setValue(defaultValue);
        }
        logger.debug("[{}] evaluated: {}, evaluator {}, resource {}, type {}",
                ctx.getEvaluationType(), property, ctx.getNewResource(), 
                ctx.getNewResource().getResourceType());
        return property;
    }

    private boolean checkForUserDeletion(PropertyEvaluationContext ctx, PropertyTypeDefinition propDef) {
        Property originalProp = ctx.getOriginalResource().getProperty(propDef);
        Property suppliedProp = ctx.getSuppliedResource().getProperty(propDef);

        return originalProp != null && suppliedProp == null;
    }

    private Property checkForUserAdditionOrChange(PropertyEvaluationContext ctx, PropertyTypeDefinition propDef) {
        Property originalProp = ctx.getOriginalResource().getProperty(propDef);
        Property suppliedProp = ctx.getSuppliedResource().getProperty(propDef);
        try {
            // Added
            if (originalProp == null && suppliedProp != null) {
                return (Property) suppliedProp.clone();
            }
            // Changed
            if (originalProp != null && suppliedProp != null && !originalProp.equals(suppliedProp)) {
                return (Property) suppliedProp.clone();
            }
        }
        catch (CloneNotSupportedException e) {
            throw new InternalRepositoryException("Error: unable to clone property " 
                    + suppliedProp + "on resource '" + ctx.getNewResource().getURI() + "'", e);
        }
        return null;
    }

    /**
     * Checking that all resource type assertions match for resource
     */
    private boolean checkAssertions(PrimaryResourceTypeDefinition rt, PropertyEvaluationContext ctx) {

        Resource resource = ctx.getNewResource();
        Principal principal = ctx.getPrincipal();
        
        Optional<Resource> optResource = Optional.ofNullable(resource);
        Optional<Principal> optPrincipal = Optional.ofNullable(principal);
        

        RepositoryAssertion[] assertions = rt.getAssertions();

        if (assertions != null) {
            for (int i = 0; i < assertions.length; i++) {
                logger.debug("Checking assertion {} for resource {}", assertions[i], resource);

                if (assertions[i] instanceof RepositoryContentEvaluationAssertion) {
                    // XXX Hack for all assertions that implement this interface
                    // (they need content)
                    RepositoryContentEvaluationAssertion cea = (RepositoryContentEvaluationAssertion) assertions[i];

                    if (!cea.matches(optResource, optPrincipal, Optional.ofNullable(ctx.getContent()))) {
                        logger.debug("Checking for type '{}', resource {} "
                                + "failed, unmatched content evaluation assertion: {}", 
                                rt.getName(), resource, cea);
                        return false;
                    }
                }
                else {
                    // Normal assertions that should not require content or
                    // resource input stream:
                    if (!assertions[i].matches(optResource, optPrincipal)) {
                        logger.debug("Checking for type '{}', resource {} failed, unmatched assertion: {}", 
                                rt.getName(), resource, assertions[i]);
                        return false;
                    }
                }
            }
        }
        logger.debug("Checking for type '{}', resource succeeded, assertions matched: {}",
                rt.getName(), resource, (assertions != null ? Arrays.asList(assertions) : null));
        return true;
    }
    
    /**
     * 
     * @return <code>true</code> if the property definition is the well known
     *         publish-date property, <code>false</code> otherwise.
     */
    private boolean isPublishDateProperty(PropertyTypeDefinition def) {
        return Namespace.DEFAULT_NAMESPACE.equals(def.getNamespace())
                && PropertyType.PUBLISH_DATE_PROP_NAME.equals(def.getName());
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    @Required
    public void setAuthorizationManager(AuthorizationManager authorizationManager) {
        this.authorizationManager = authorizationManager;
    }

}
