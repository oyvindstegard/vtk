/* Copyright (c) 2006-2017, University of Oslo, Norway
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;

import vtk.repository.resourcetype.AbstractResourceTypeDefinitionImpl;
import vtk.repository.resourcetype.LatePropertyEvaluator;
import vtk.repository.resourcetype.MixinResourceTypeDefinition;
import vtk.repository.resourcetype.OverridablePropertyTypeDefinition;
import vtk.repository.resourcetype.OverridablePropertyTypeDefinitionImpl;
import vtk.repository.resourcetype.OverridingPropertyTypeDefinitionImpl;
import vtk.repository.resourcetype.PrimaryResourceTypeDefinition;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.PropertyTypeDefinitionImpl;
import vtk.repository.resourcetype.ResourceTypeDefinition;
import vtk.repository.resourcetype.TypeLocalizationProvider;
import vtk.repository.resourcetype.event.DynamicTypeRegisteredEvent;
import vtk.repository.resourcetype.event.DynamicTypeRegistrationComplete;
import vtk.repository.resourcetype.event.StaticTypesInitializedEvent;


/**
 * Repository resource type manager.
 *
 * <p>Gathers up static resource types defined in application context and allows
 * various queries to be performed.
 *
 * <h2>Application events
 * <p>Dispatches a {@link StaticTypesInitializedEvent } event when all static types have been initialized.
 * 
 * <p>Dispatches a {@link DynamicTypeRegisteredEvent} whenever a resource type
 * is registered through the method {@link #registerDynamicResourceType(vtk.repository.resourcetype.PrimaryResourceTypeDefinition) registerDynamicResourceType}.
 * 
 * <p>Listens for a {@link DynamicTypeRegistrationComplete} event from an external
 * dynamic type manager and logs the complete resource type tree upon reception of this event.
 *
 * <p><em>XXX in need of refactoring and cleanup.</em>
 */
public class ResourceTypeTreeImpl implements ResourceTypeTree, InitializingBean,
        ApplicationContextAware, ApplicationListener<DynamicTypeRegistrationComplete> {

    private final Logger logger = LoggerFactory.getLogger(ResourceTypeTreeImpl.class);
    
    private ApplicationContext applicationContext;
    
    /**
     * The root of the resource type hierarchy
     */
    private PrimaryResourceTypeDefinition rootResourceTypeDefinition;
    
    /**
     * Maps all parent resource type defs to its children.
     *
     * <p>No mixin types are present in this map.
     */
    private final Map<PrimaryResourceTypeDefinition, List<PrimaryResourceTypeDefinition>> parentChildMap =
            new LinkedHashMap<>();
    

    /**
     * Maps all resource type names to resource type objects, mixins included.
     */
    private final Map<String, ResourceTypeDefinition> resourceTypeNameMap = new LinkedHashMap<>();


    /**
     * Maps namespace:name to property definition
     */
    private final Map<Namespace, Map<String, PropertyTypeDefinition>> propertyTypeDefinitions =
            new LinkedHashMap<>();
    

    /**
     * A collection containing all {@link MixinResourceTypeDefinition
     * mixin} resource type definitions
     */
    private Collection<MixinResourceTypeDefinition> mixinTypes;


    /**
     * A collection containing all {@link
     * PrimaryResourceTypeDefinition primary} resource type
     * definitions
     */
    private Collection<PrimaryResourceTypeDefinition> primaryTypes;    


    /**
     * Maps from primary resource types to a list of mixin types:
     *
     */
    private final Map<PrimaryResourceTypeDefinition, List<MixinResourceTypeDefinition>> mixinTypeDefinitionMap =
        new LinkedHashMap<>();

    
    /**
     * Maps from mixin types to complete sets of primary resource types including
     * all descendants.
     */
    private final Map<MixinResourceTypeDefinition, Set<PrimaryResourceTypeDefinition>> mixinTypePrimaryTypesMap =
        new LinkedHashMap<>();


    /**
     * Maps from name space URIs to {@link Namespace} objects
     */
    private final Map<String, Namespace> namespaceUriMap = new HashMap<>();

    
    /**
     * Maps from name space prefixes to {@link Namespace} objects
     */
    private final Map<String, Namespace> namespacePrefixMap = new HashMap<>();


    /**
     * Maps from namespaces to maps which map property names to a set
     * of primary resource types
     */
    private final Map<Namespace, Map<String, Set<PrimaryResourceTypeDefinition>>> propDefPrimaryTypesMap =
        new HashMap<>();

    private TypeLocalizationProvider typeLocalizationProvider;

    // Lazy cache for method flattenedDescendants
    private final Map<String, Set<String>> nameDescendantsCache = new ConcurrentHashMap<>();
    // Lazy cache for method flattenedAncestors
    private final Map<String, Set<String>> nameAncestorsCache = new ConcurrentHashMap<>();
    // Lazy cache for method getPropertyTypeDefinitionsIncludingAncestors
    private final Map<ResourceTypeDefinition, List<PropertyTypeDefinition>>
                              propDefsIncludingAncestorsCache = new ConcurrentHashMap<>();

    // Call whenever resource type tree is modified
    private void clearLazyCaches() {
        nameDescendantsCache.clear();
        nameAncestorsCache.clear();
        propDefsIncludingAncestorsCache.clear();
    }
    
    @Override
    public PropertyTypeDefinition getManagedPropertyTypeDefinition(Namespace namespace, String name) {
        Map<String, PropertyTypeDefinition> map = propertyTypeDefinitions.get(namespace);
        if (map == null) {
            return null;
        }

        return map.get(name);
    }

    @Override
    public boolean isManagedProperty(PropertyTypeDefinition def) {
        return getManagedPropertyTypeDefinition(def.getNamespace(), def.getName()) != null;
    }

    // TODO rename to "getPropertyTypeDefinitionOrDefault" to reduce overall confusion
    @Override
    public PropertyTypeDefinition getPropertyTypeDefinition(Namespace namespace, String name) {
        PropertyTypeDefinition propDef = getManagedPropertyTypeDefinition(namespace, name);
        if (propDef != null) {
            return propDef;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("No definition found for property "
                    + namespace.getPrefix() + ":" + name + ", returning default");
        }

        return PropertyTypeDefinitionImpl.createDefault(namespace, name, false);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        init();
    }

    @Override
    public PrimaryResourceTypeDefinition getRoot() {
        return this.rootResourceTypeDefinition;
    }

    @Override
    public List<MixinResourceTypeDefinition> getMixinTypes(PrimaryResourceTypeDefinition rt) {
        return this.mixinTypeDefinitionMap.get(rt);
    }
    
    @Override
    public List<PrimaryResourceTypeDefinition> getResourceTypeDefinitionChildren(PrimaryResourceTypeDefinition def) {
        List<PrimaryResourceTypeDefinition> children = this.parentChildMap.get(def);
        if (children == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(children);
    }

    /**
     * Get collection of all descendant type names of the provided type name.
     *
     * <p>If a mixin resource type name is provided, then descendats are all primary types
     * which has the mixin and all their descendants.
     *
     * @param name
     * @return collection of all descendant type names of the provided type name
     */
    @Override
    public Set<String> flattenedDescendants(String name) {
        return nameDescendantsCache.computeIfAbsent(name, n -> flattenedDescendantsInternal(n, false));
    }

    private Set<String> flattenedDescendantsInternal(String name, boolean includeSelf) {
        Set<String> descendants = new LinkedHashSet<>();
        if (includeSelf) {
            descendants.add(name);
        }
        Collection<String> children = children(name);
        for (String child: children) {
            descendants.addAll(flattenedDescendantsInternal(child, true));
        }

        return Collections.unmodifiableSet(descendants);
    }

    /**
     * Get a flattened collection of all super type names of the given name.
     *
     * <p>If name is a mixin type, then there will never be any ancestors as these
     * are considered root-only types.
     *
     * <p>If name is a primary type, then the following applies:
     * <ul>
     *    <li>The type itself is not included.
     *    <li>Any mixin types defined on the type itself <strong>are included</strong>.
     *    <li>All super types of the type, and any mixins defined on syper types are included.
     * </ul>
     *
     * @param name
     * @return
     */
    @Override
    public Set<String> flattenedAncestors(String name) {
        return nameAncestorsCache.computeIfAbsent(name, n -> flattenedAncestorsInternal(n, false));
    }

    private Set<String> flattenedAncestorsInternal(String name, boolean includeSelf) {
        Set<String> ancestors = new LinkedHashSet<>();
        if (includeSelf) {
            ancestors.add(name);
        }
        Collection<String> parents = parents(name);
        for (String parent: parents) {
            ancestors.addAll(flattenedAncestorsInternal(parent, true));
        }
        return Collections.unmodifiableSet(ancestors);
    }

    /**
     * Get parent types of given type name.
     *
     * <p>Includes all mixin types defined on the given type, and any single
     * primary parent type.
     * @param name
     * @return
     */
    @Override
    public Collection<String> parents(String name) {
        ResourceTypeDefinition def = resourceTypeNameMap.get(name);
        if (def == null || def instanceof MixinResourceTypeDefinition) {
            return Collections.emptyList();
        }
        List<String> parents = new ArrayList<>();
        PrimaryResourceTypeDefinition primaryDef = (PrimaryResourceTypeDefinition)def;
        if (primaryDef.getParentTypeDefinition() != null) {
            parents.add(primaryDef.getParentTypeDefinition().getName());
        }
        parents.addAll(primaryDef.getMixinTypeDefinitions().stream().map(m -> m.getName()).collect(Collectors.toList()));
        return parents;
    }

    /**
     * Get the immediate child type names of the given type name.
     *
     * <p>If the type is a mixin, then the children will be all primary types
     * for which this mixin has been defined.
     *
     * <p>If the type is a primary type, then
     * @param name
     * @return
     */
    @Override
    public Collection<String> children(String name) {
        ResourceTypeDefinition def = resourceTypeNameMap.get(name);
        if (def == null) {
            return Collections.emptyList();
        }
        List<String> children = new ArrayList<>();
        if (def instanceof MixinResourceTypeDefinition) {
            Set<PrimaryResourceTypeDefinition> primaryTypesForMixin = mixinTypePrimaryTypesMap.get(def);
            if (primaryTypesForMixin != null) {
                children.addAll(primaryTypesForMixin.stream().map(p -> p.getName()).collect(Collectors.toList()));
            }
            return Collections.unmodifiableList(children);
        } else {
            List<PrimaryResourceTypeDefinition> childDefs = parentChildMap.get(def);
            if (childDefs != null) {
                children.addAll(childDefs.stream().map(d -> d.getName()).collect(Collectors.toList()));
            }
            return Collections.unmodifiableList(children);
        }
    }

    /**
     * Root type names include the root resource type definition, as well as
     * all mixin types.
     * @return collection of root nodes
     */
    @Override
    public Collection<String> roots() {
        List<String> roots = new ArrayList<>();
        roots.add(rootResourceTypeDefinition.getName());
        for (MixinResourceTypeDefinition m: mixinTypes) {
            roots.add(m.getName());
        }
        return roots;
    }

    /**
     *
     * @return flat list of names of all resource type definitions including mixins.
     */
    @Override
    public List<String> vocabularyValues() {
        return new ArrayList<>(resourceTypeNameMap.keySet());
    }

    @Override
    public ResourceTypeDefinition getResourceTypeDefinitionByName(String name) {
        ResourceTypeDefinition type = this.resourceTypeNameMap.get(name);
        // XXX inconsistent with other parts of API which simply return null instead of throwing IAE:
        if (type == null) {
            throw new IllegalArgumentException(
                "No resource type of name '" + name + "' exists");
        }

        return type;
    }

    /**
     * @param prefix
     * @param name
     * @return
     */
    @Override
    public PropertyTypeDefinition getPropertyDefinitionByPrefix(String prefix, String name) {
        Namespace namespace = this.namespacePrefixMap.get(prefix);
        if (namespace == null) {
            return null;
        }

        return getManagedPropertyTypeDefinition(namespace, name);
    }

    @Override
    public PropertyTypeDefinition getPropertyDefinitionByName(String qname) {
        String[] qnameParts = qname.split(":");

        if (qnameParts.length == 1) {
            return getPropertyDefinitionByPrefix(null, qname);
        }
        if (qnameParts.length == 2) {
            return getPropertyDefinitionByPrefix(qnameParts[0].isEmpty() ? null : qnameParts[0], qnameParts[1]);
        }
        if (qnameParts.length == 3) {
            return getPropertyDefinitionForResourceType(qnameParts[0], qnameParts[1].isEmpty() ? null : qnameParts[1], qnameParts[2]);
        }

        return null;
    }

    private PropertyTypeDefinition getPropertyDefinitionForResourceType(String resourceType, String prefix, String name) {
        ResourceTypeDefinition resourceTypeDefinition = getResourceTypeDefinitionByName(resourceType);
        if (resourceTypeDefinition == null) {
            return null;
        }

        Namespace namespace = namespacePrefixMap.get(prefix);
        if (namespace == null) {
            return null;
        }

        for (PropertyTypeDefinition propDef : resourceTypeDefinition.getPropertyTypeDefinitions()) {
            if (propDef.getNamespace().equals(namespace) && propDef.getName().equals(name)) {
                return propDef;
            }
        }

        return null;
    }
    
    /**
     * Search upwards in resource type tree, collect property type definitions
     * from all encountered resource type definitions including mixin resource types.
     * Assuming that mixin types can never have mixin parent.
     * 
     * If there are more than one occurence of the same property type definition
     * for the given resource type, only the first occurence in the resource type
     * tree is added to the returned list (upward direction).
     * 
     * @param def The <code>ResourceTypeDefinition</code> 
     * @return A <code>List</code> of <code>PropertyTypeDefinition</code> instances.
     */
    @Override
    public List<PropertyTypeDefinition> getPropertyTypeDefinitionsIncludingAncestors(
                                              final ResourceTypeDefinition def) {
        
        List<PropertyTypeDefinition> collectedPropDefs = this.propDefsIncludingAncestorsCache.get(def);
        if (collectedPropDefs != null) {
            return collectedPropDefs;
        }
        
        Set<String> encountered = new HashSet<>();
        collectedPropDefs = new ArrayList<>();
        
        if (def instanceof MixinResourceTypeDefinition) {
            MixinResourceTypeDefinition mixinDef = (MixinResourceTypeDefinition)def;
            
            PropertyTypeDefinition[] propDefs = mixinDef.getPropertyTypeDefinitions();
            addPropertyTypeDefinitions(encountered, collectedPropDefs, propDefs);
        } else {
            // Assuming instanceof PrimaryResourceTypeDefinition
            PrimaryResourceTypeDefinition primaryDef = (PrimaryResourceTypeDefinition)def; 

            while (primaryDef != null) {
                PropertyTypeDefinition[] propDefs = primaryDef.getPropertyTypeDefinitions();
                addPropertyTypeDefinitions(encountered, collectedPropDefs, propDefs);
                
                // Add any mixin resource types' property type defs
                for (MixinResourceTypeDefinition mixinDef: primaryDef.getMixinTypeDefinitions()) {
                    addPropertyTypeDefinitions(encountered, collectedPropDefs, 
                                                mixinDef.getPropertyTypeDefinitions());
                }

                primaryDef = primaryDef.getParentTypeDefinition();
            }
        }
        
        collectedPropDefs = Collections.unmodifiableList(collectedPropDefs);
        this.propDefsIncludingAncestorsCache.put(def, collectedPropDefs);
        return collectedPropDefs;
    }
    
    private void addPropertyTypeDefinitions(Set<String> encountered,
                                           List<PropertyTypeDefinition> collectedPropDef, 
                                           PropertyTypeDefinition[] propDefs) {
        for (PropertyTypeDefinition propDef: propDefs) {
            String id = propDef.getNamespace().getUri() + ":" + propDef.getName();
            // Add only _first_ occurence of property type definition keyed on id
            // Also go through getPropertyTypeDefintion to get canonical instance (
            if (encountered.add(id)) {
                collectedPropDef.add(getPropertyTypeDefinition(propDef.getNamespace(), propDef.getName()));
            }
        }
    }
    
    @Override
    public boolean isContainedType(ResourceTypeDefinition def, String resourceTypeName) {
        if (resourceTypeName == null) return false;
        return flattenedAncestors(resourceTypeName).contains(def.getName())
                        || def.getName().equals(resourceTypeName);
    }

    
    @Override
    public List<PropertyTypeDefinition> getPropertyTypeDefinitions() {
        ArrayList<PropertyTypeDefinition> definitions = new ArrayList<>();
        
        for (Map<String, PropertyTypeDefinition> propMap: this.propertyTypeDefinitions.values()) {
            definitions.addAll(propMap.values());
        }
        
        return definitions;
    }

    @Override
    public Namespace getNamespace(String namespaceUrl) {
        Namespace namespace = this.namespaceUriMap.get(namespaceUrl);
        
        if (namespace == null) {
            namespace = new Namespace(namespaceUrl);
        }

        return namespace;
    }
    
    @Override
    public Namespace getNamespaceByPrefix(String prefix) {
        Namespace namespace = this.namespacePrefixMap.get(prefix);

        if (namespace == null) {
            namespace = Namespace.getNamespaceFromPrefix(prefix);
        }

        return namespace;
    }

    @Override
    public PrimaryResourceTypeDefinition[] getPrimaryResourceTypesForPropDef(
            PropertyTypeDefinition definition) {

        Map<String, Set<PrimaryResourceTypeDefinition>> nsPropMap = propDefPrimaryTypesMap.get(definition.getNamespace());
        
        if (nsPropMap != null) {
            Set<PrimaryResourceTypeDefinition> rts 
                = nsPropMap.get(definition.getName());
            
            if (rts != null) {
                return rts.toArray(new PrimaryResourceTypeDefinition[rts.size()]);
            }
        }
        
        // No resource type definitions found for the given property type definition
        // (dead prop)
        return null;
    }

    @Override
    public String getResourceTypeTreeAsString() {
        StringBuilder sb = new StringBuilder();
        printResourceTypes(sb, 0, this.rootResourceTypeDefinition);
        sb.append("\n");
        for (MixinResourceTypeDefinition mixin: this.mixinTypes) {
            printResourceTypes(sb, 0, mixin);
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * This method is only safe to call during context initialization.
     * @param def
     */
    @Override
    public void registerDynamicResourceType(PrimaryResourceTypeDefinition def) {
        List<PrimaryResourceTypeDefinition> tmp = new ArrayList<>(this.primaryTypes.size()+1);
        tmp.addAll(this.primaryTypes);
        tmp.add(def);
        this.primaryTypes = tmp;
        this.resourceTypeNameMap.put(def.getName(), def);
        if (def.getNamespace() == null) {
            throw new IllegalArgumentException("Definition's namespace is null: " + def);
        }
        
        addNamespacesAndProperties(def);
        PrimaryResourceTypeDefinition parent = def.getParentTypeDefinition();
        if (parent == null) {
            throw new IllegalStateException("Must register resource type under an existing resource type");
        }

        this.parentChildMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(def);

        registerMixins(def);
        injectTypeLocalizationProvider(def);
        mapPropertyDefinitionsToPrimaryTypes();
        clearLazyCaches();

        applicationContext.publishEvent(new DynamicTypeRegisteredEvent(this, def));
    }
    
    private void init() {

        this.primaryTypes = 
            BeanFactoryUtils.beansOfTypeIncludingAncestors(this.applicationContext, 
                    PrimaryResourceTypeDefinition.class, false, false).values();

        this.mixinTypes =
            BeanFactoryUtils.beansOfTypeIncludingAncestors(this.applicationContext, 
                    MixinResourceTypeDefinition.class, false, false).values();

        PrimaryResourceTypeDefinition rootDefinition = null;
        for (PrimaryResourceTypeDefinition def: this.primaryTypes) {
            if (def.getParentTypeDefinition() == null) {
                if (rootDefinition != null) {
                    throw new IllegalStateException(
                        "Only one PrimaryResourceTypeDefinition having "
                        + "parentTypeDefinition = null may be defined");
                }
                rootDefinition = def;
            }
        }
        if (rootDefinition == null) {
                    throw new IllegalStateException(
                        "A PrimaryResourceTypeDefinition having "
                        + "parentTypeDefinition = null must be defined");
        }
        this.rootResourceTypeDefinition = rootDefinition;

        for (PrimaryResourceTypeDefinition def: this.primaryTypes) {
            
            this.resourceTypeNameMap.put(def.getName(), def);
            if (def.getNamespace() == null) {
                throw new BeanInitializationException(
                    "Definition's namespace is null: " + def
                    + " (already initialized resourceTypes = " + this.resourceTypeNameMap + ")");
            }

            addNamespacesAndProperties(def);
            
            // Populate map of resourceTypeDefiniton parent -> children
            PrimaryResourceTypeDefinition parent = def.getParentTypeDefinition();
            
            // Don't add the root resource type's "parent"
            if (parent != null) {
                this.parentChildMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(def);
            }

            registerMixins(def);
            
            // Inject localized type name provider
            // XXX: I wanted to avoid having to explicitly configure the dependency for
            //      every defined resource type ...
            injectTypeLocalizationProvider(def);
        }

        for (MixinResourceTypeDefinition mixinDef: this.mixinTypes) {
            this.resourceTypeNameMap.put(mixinDef.getName(), mixinDef);
            addNamespacesAndProperties(mixinDef);
            
            injectTypeLocalizationProvider(mixinDef);
        }

        mapPropertyDefinitionsToPrimaryTypes();

        applicationContext.publishEvent(new StaticTypesInitializedEvent(this));
    }

    private void registerMixins(PrimaryResourceTypeDefinition def) {
        List<MixinResourceTypeDefinition> mixins = def.getMixinTypeDefinitions();
        if (mixins == null) {
            return;
        }
        for (MixinResourceTypeDefinition mixin: mixins) {
            if (!this.namespaceUriMap.containsKey(mixin.getNamespace().getUri()))
                this.namespaceUriMap.put(mixin.getNamespace().getUri(), mixin.getNamespace());


            mixinTypePrimaryTypesMap.computeIfAbsent(mixin, k -> new HashSet<>()).add(def);
        }

        this.mixinTypeDefinitionMap.put(def, mixins);
    }
    
    private void injectTypeLocalizationProvider(ResourceTypeDefinition def) {
        AbstractResourceTypeDefinitionImpl defImpl = (AbstractResourceTypeDefinitionImpl)def;
        
        defImpl.setTypeLocalizationProvider(this.typeLocalizationProvider);
        
        for (PropertyTypeDefinition propDef: def.getPropertyTypeDefinitions()) {
            if (propDef instanceof PropertyTypeDefinitionImpl) {
                PropertyTypeDefinitionImpl propDefImpl
                    = (PropertyTypeDefinitionImpl)propDef;
                propDefImpl.setTypeLocalizationProvider(this.typeLocalizationProvider);
            }
        }
    }

    private void addNamespacesAndProperties(ResourceTypeDefinition def) {
        if (!this.namespaceUriMap.containsKey(def.getNamespace().getUri())) {
            this.namespaceUriMap.put(def.getNamespace().getUri(), def.getNamespace());
        }        

        if (!this.namespacePrefixMap.containsKey(def.getNamespace().getPrefix())) {            
            this.namespacePrefixMap.put(def.getNamespace().getPrefix(), def.getNamespace());
        }

        // Populate map of property type definitions
        for (PropertyTypeDefinition propDef : def.getPropertyTypeDefinitions()) {
            // XXX: Should be removed
            if (propDef instanceof OverridingPropertyTypeDefinitionImpl) {
                continue;
            }
            Namespace namespace = propDef.getNamespace();
            Map<String, PropertyTypeDefinition> propDefMap =
                    this.propertyTypeDefinitions.computeIfAbsent(namespace, k -> new HashMap<>());
            if (propDefMap.get(propDef.getName()) == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Registering property type definition "
                            + propDef + " with namespace : " + namespace);
                }

                propDefMap.put(propDef.getName(), propDef);
            }
        }
    }

    private void mapPropertyDefinitionsToPrimaryTypes() {

        for (PrimaryResourceTypeDefinition primaryTypeDef: this.primaryTypes) {
            PropertyTypeDefinition[] propDefs = primaryTypeDef.getPropertyTypeDefinitions();
            mapPropertyDefinitionsToPrimaryType(propDefs, primaryTypeDef.getNamespace(), primaryTypeDef);
        }

        for (MixinResourceTypeDefinition mixin: this.mixinTypes) {
            PropertyTypeDefinition[] mixinPropDefs = mixin.getPropertyTypeDefinitions();
            Set<PrimaryResourceTypeDefinition> mixinPrimarySet = mixinTypePrimaryTypesMap.get(mixin);
            for (PrimaryResourceTypeDefinition primaryTypeDef: mixinPrimarySet) {
                mapPropertyDefinitionsToPrimaryType(mixinPropDefs, mixin.getNamespace(), primaryTypeDef);
            }
        }
    }

    private void mapPropertyDefinitionsToPrimaryType(PropertyTypeDefinition[] propDefs,
                                                    Namespace namespace,
                                                    PrimaryResourceTypeDefinition primaryTypeDef) {
        Map<String, Set<PrimaryResourceTypeDefinition>> propDefMap = 
            this.propDefPrimaryTypesMap.computeIfAbsent(namespace, ns -> new HashMap<>());
        for (PropertyTypeDefinition propDef: propDefs) {
            // FIXME: should be removed
            if (propDef instanceof OverridingPropertyTypeDefinitionImpl) {
                continue;
            }
            propDefMap.computeIfAbsent(propDef.getName(), n -> new HashSet<>()).add(primaryTypeDef);
        }
    }
        

    @Override
    public void onApplicationEvent(DynamicTypeRegistrationComplete event) {
        logger.info("Default resource type tree:");
        logger.info("\n" + getResourceTypeTreeAsString());
    }

    private void printResourceTypes(StringBuilder sb, int level,
            ResourceTypeDefinition def) {

        if (level > 0) {
            for (int i = 1; i < level; i++)
                sb.append("  ");
            sb.append("|\n");
            for (int i = 1; i < level; i++)
                sb.append("  ");
            sb.append("+--");
        }
        sb.append(" ");
        sb.append(def.getName());
        if (def.getNamespace() != Namespace.DEFAULT_NAMESPACE) {
            Namespace ns = def.getNamespace();
            sb.append(" [ns:").append(ns.getPrefix()).append(" = ")
              .append(ns.getUri()).append("]");
        }
        if (def instanceof MixinResourceTypeDefinition) {
            sb.append(" (mixin)");
        }
        sb.append("\n");
        
        if (def instanceof PrimaryResourceTypeDefinition) {
            PrimaryResourceTypeDefinition pdef = (PrimaryResourceTypeDefinition) def;
            if (pdef.getAssertions() != null) {
                for (Object a: pdef.getAssertions()) {
                    for (int j = 0; j < level; j++)
                        sb.append("  ");
                    sb.append("  assertion: ").append(a).append('\n');
                }
            }
        }

        List<MixinResourceTypeDefinition> mixins = this.mixinTypeDefinitionMap.get(def);
        if (mixins != null) {
            for (MixinResourceTypeDefinition mixin: mixins) {
                for (int j = 0; j < level; j++)
                    sb.append("  ");
                sb.append("  mixin: [");
                sb.append(mixin.getNamespace()).append("] ");
                sb.append(mixin.getName()).append("\n");
            }
        }

        PropertyTypeDefinition[] propDefs = def.getPropertyTypeDefinitions();
        printPropertyDefinitions(sb, level, propDefs);
        
        List<PrimaryResourceTypeDefinition> children = this.parentChildMap.get(def);

        if (children != null) {
            for (PrimaryResourceTypeDefinition child: children) {
                printResourceTypes(sb, level + 1, child);
            }
        }
    }

    private void printPropertyDefinitions(StringBuilder sb, int level, PropertyTypeDefinition[] propDefs) {
        if (propDefs != null) {
            for (PropertyTypeDefinition definition: propDefs) {
                sb.append("  ");
                for (int j = 0; j < level; j++) sb.append("  ");

                if (definition.getNamespace() != Namespace.DEFAULT_NAMESPACE) {
                    sb.append(definition.getNamespace().getPrefix()).append(":");
                }
                sb.append(definition.getName());
                sb.append(" ");
                
                String type = definition.getType().toString();
                sb.append(": ").append(type.toLowerCase());
                if (definition.isMultiple())
                    sb.append("[]");
                sb.append(" ");
                List<String> flags = new ArrayList<>();
                if (definition.getProtectionLevel() == RepositoryAction.UNEDITABLE_ACTION) {
                    flags.add("readonly");
                }
                if (definition.getPropertyEvaluator() instanceof LatePropertyEvaluator) {
                    flags.add("evaluated_late");
                }
                else if (definition.getPropertyEvaluator() != null) {
                    flags.add("evaluated");
                }
                if (definition instanceof OverridablePropertyTypeDefinition) {
                    if (definition instanceof OverridablePropertyTypeDefinitionImpl) {
                        flags.add("overridable");
                    } else {
                        flags.add("overriding");
                    }
                }
                if (definition.isInheritable()) {
                    flags.add("inheritable");
                }
                if (definition.getDefaultValue() != null) {
                    flags.add("default=" + definition.getDefaultValue());
                }
                if (flags.size() > 0) {
                    sb.append("(");
                    for (int i = 0; i < flags.size(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append(flags.get(i));
                    }
                    sb.append(")");
                }
                sb.append("\n");
            }
        }
    }
    
    public void setTypeLocalizationProvider(
            TypeLocalizationProvider typeLocalizationProvider) {
        this.typeLocalizationProvider = typeLocalizationProvider;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
}
