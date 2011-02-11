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
package org.vortikal.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.vortikal.repository.resourcetype.AbstractResourceTypeDefinitionImpl;
import org.vortikal.repository.resourcetype.HierarchicalNode;
import org.vortikal.repository.resourcetype.MixinResourceTypeDefinition;
import org.vortikal.repository.resourcetype.OverridablePropertyTypeDefinition;
import org.vortikal.repository.resourcetype.OverridablePropertyTypeDefinitionImpl;
import org.vortikal.repository.resourcetype.OverridingPropertyTypeDefinitionImpl;
import org.vortikal.repository.resourcetype.PrimaryResourceTypeDefinition;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.PropertyTypeDefinitionImpl;
import org.vortikal.repository.resourcetype.ResourceTypeDefinition;
import org.vortikal.repository.resourcetype.TypeLocalizationProvider;
import org.vortikal.repository.resourcetype.ValueFactory;
import org.vortikal.repository.resourcetype.ValueFormatter;
import org.vortikal.repository.resourcetype.ValueFormatterRegistry;


public class ResourceTypeTreeImpl 
    implements ResourceTypeTree, InitializingBean, ApplicationContextAware {

    private static Log logger = LogFactory.getLog(ResourceTypeTreeImpl.class);
    
    private ApplicationContext applicationContext;
    
    /**
     * The root of the resource type hierarchy
     */
    private PrimaryResourceTypeDefinition rootResourceTypeDefinition;
    
    /**
     * Maps all parent resource type defs to its children
     */
    private Map<PrimaryResourceTypeDefinition, List<PrimaryResourceTypeDefinition>> parentChildMap = 
        new HashMap<PrimaryResourceTypeDefinition, List<PrimaryResourceTypeDefinition>>();
    

    /**
     * Maps all resource type names to resource type objects
     */
    private Map<String, ResourceTypeDefinition> resourceTypeNameMap = 
        new HashMap<String, ResourceTypeDefinition>();


    /**
     * Maps namespace:name to property def
     */
    private Map<Namespace, Map<String, PropertyTypeDefinition>> propertyTypeDefinitions = 
        new HashMap<Namespace, Map<String, PropertyTypeDefinition>>();
    

    /**
     * A collection containing all {@link MixinResourceTypeDefinition
     * mixin} resource type definitions
     */
    private Collection<MixinResourceTypeDefinition> mixins;


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
    private Map<PrimaryResourceTypeDefinition, List<MixinResourceTypeDefinition>> mixinTypeDefinitionMap = 
        new HashMap<PrimaryResourceTypeDefinition, List<MixinResourceTypeDefinition>>();

    
    /**
     * Maps from mixin types to its {@link Set} of primary resource types:
     */
    private Map<MixinResourceTypeDefinition, Set<PrimaryResourceTypeDefinition>> mixinTypePrimaryTypesMap = 
        new HashMap<MixinResourceTypeDefinition, Set<PrimaryResourceTypeDefinition>>();


    /**
     * Maps from name space URIs to {@link Namespace} objects
     */
    private Map<String, Namespace> namespaceUriMap = new HashMap<String, Namespace>();

    
    /**
     * Maps from name space prefixes to {@link Namespace} objects
     */
    private Map<String, Namespace> namespacePrefixMap = new HashMap<String, Namespace>();

    
    /**
     * Maps from namespaces to maps which map property names to a set
     * of primary resource types
     */
    private Map<Namespace, Map<String, Set<PrimaryResourceTypeDefinition>>> propDefPrimaryTypesMap = 
        new HashMap<Namespace, Map<String, Set<PrimaryResourceTypeDefinition>>>();


    /**
     * Map resource type name to flat list of _all_ descendant resource type names.
     * (Supports fast lookup for 'IN'-resource-type queries)
     */
    private Map<String, List<String>> resourceTypeDescendantNames;

    /**
     * All primary resource type names (Supports unused getAllowedValues)
     */
    private String[] primaryResourceTypeNames;
    
    private TypeLocalizationProvider typeLocalizationProvider;

    private ValueFormatterRegistry valueFormatterRegistry;

    private ValueFactory valueFactory;

    public PropertyTypeDefinition getPropertyTypeDefinition(Namespace namespace, String name) {
        Map<String, PropertyTypeDefinition> map = this.propertyTypeDefinitions.get(namespace);

        if (map != null) {
            PropertyTypeDefinition propDef = map.get(name);
            if (propDef != null) {
                return propDef;
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("No definition found for property "
                    + namespace.getPrefix() + ":" + name + ", returning default");
        }

        PropertyTypeDefinitionImpl propDef = new PropertyTypeDefinitionImpl();
        propDef.setNamespace(namespace);
        propDef.setName(name);
        propDef.setValueFactory(this.valueFactory);
        propDef.setValueFormatterRegistry(this.valueFormatterRegistry);
        
        propDef.afterPropertiesSet();
        return propDef;
    }

    public void afterPropertiesSet() throws Exception {
        init();
    }

    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
                this.applicationContext = applicationContext;
        
        
    }

    public PrimaryResourceTypeDefinition getRoot() {
        return this.rootResourceTypeDefinition;
    }

    public List<MixinResourceTypeDefinition> getMixinTypes(PrimaryResourceTypeDefinition rt) {
        return this.mixinTypeDefinitionMap.get(rt);
    }
    
    public List<PrimaryResourceTypeDefinition> getResourceTypeDefinitionChildren(PrimaryResourceTypeDefinition def) {
        List<PrimaryResourceTypeDefinition> children = this.parentChildMap.get(def);
        
        if (children == null) {
            return new ArrayList<PrimaryResourceTypeDefinition>();
        }
        return children;
    }

    public List<String> getDescendants(String name) {
         return this.resourceTypeDescendantNames.get(name);
    }

    public String[] getAllowedValues() {
        return this.primaryResourceTypeNames;
    }

    
    public ResourceTypeDefinition getResourceTypeDefinitionByName(String name) {
        ResourceTypeDefinition type = this.resourceTypeNameMap.get(name);
        if (type == null) {
            throw new IllegalArgumentException(
                "No resource type of name '" + name + "' exists");
        }

        return type;
    }

    public PropertyTypeDefinition getPropertyDefinitionByPrefix(String prefix, String name) {
        Namespace namespace = this.namespacePrefixMap.get(prefix);
        if (namespace == null) {
            return null;
        }
        PropertyTypeDefinition propertyTypeDefinition = getPropertyTypeDefinition(namespace, name);
        return propertyTypeDefinition;
    }

    public PropertyTypeDefinition getPropertyDefinitionByPointer(String pointer) {
        
        final String pointerDelimiter = ":";
        if (!pointer.contains(pointerDelimiter)) {
            return this.getPropertyDefinitionByPrefix(null, pointer);
        }

        String[] pointers = pointer.split(pointerDelimiter);
        if (pointers.length == 2) {
            return this.getPropertyDefinitionByPrefix(pointers[0], pointers[1]);
        }
        if (pointers.length == 3) {
            return this.getPropertyDefinitionForResource(pointers[0], pointers[1], pointers[2]);
        }

        return null;

    }

    private PropertyTypeDefinition getPropertyDefinitionForResource(String resource, String prefix, String name) {

        prefix = "".trim().equals(prefix) ? null : prefix;

        ResourceTypeDefinition resourceTypeDefinition = this.getResourceTypeDefinitionByName(resource);
        if (resourceTypeDefinition == null) {
            return null;
        }

        Namespace namespace = this.namespacePrefixMap.get(prefix);
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
     * FIXME: Assuming that mixin types can never have mixin parent.
     * 
     * If there are more than one occurence of the same property type definition
     * for the given resource type, only the first occurence in the resource type
     * tree is added to the returned list (upward direction).
     * 
     * @param def The <code>ResourceTypeDefinition</code> 
     * @return A <code>Set</code> of <code>PropertyTypeDefinition</code> instances.
     */
    public List<PropertyTypeDefinition> getPropertyTypeDefinitionsIncludingAncestors(
                                                    ResourceTypeDefinition def) {
        Set<String> encounteredIds = new HashSet<String>();
        List<PropertyTypeDefinition> propertyTypes = new ArrayList<PropertyTypeDefinition>();
        
        if (def instanceof MixinResourceTypeDefinition) {
            MixinResourceTypeDefinition mixinDef = (MixinResourceTypeDefinition)def;
            
            PropertyTypeDefinition[] propDefs = mixinDef.getPropertyTypeDefinitions();
            addPropertyTypeDefinitions(encounteredIds, propertyTypes, propDefs);
        } else {
            // Assuming instanceof PrimaryResourceTypeDefinition
            PrimaryResourceTypeDefinition primaryDef = (PrimaryResourceTypeDefinition)def; 

            while (primaryDef != null) {
                PropertyTypeDefinition[] propDefs = primaryDef.getPropertyTypeDefinitions();
                addPropertyTypeDefinitions(encounteredIds, propertyTypes, propDefs);
                
                // Add any mixin resource types' property type defs
                for (MixinResourceTypeDefinition mixinDef: primaryDef.getMixinTypeDefinitions()) {
                    addPropertyTypeDefinitions(encounteredIds, propertyTypes, 
                                                mixinDef.getPropertyTypeDefinitions());
                }

                primaryDef = primaryDef.getParentTypeDefinition();
            }
        }
        
        return propertyTypes;
    }
    
    public boolean isContainedType(ResourceTypeDefinition def, String resourceTypeName) {

        ResourceTypeDefinition type = this.resourceTypeNameMap.get(resourceTypeName);
        if (type == null || !(type instanceof PrimaryResourceTypeDefinition)) {
            return false;
        }

        PrimaryResourceTypeDefinition primaryDef = (PrimaryResourceTypeDefinition) type;

        // recursive ascent on the parent axis
        while (primaryDef != null) {
            if (def instanceof MixinResourceTypeDefinition) {
                for (MixinResourceTypeDefinition mixin: primaryDef.getMixinTypeDefinitions()) {
                    if (mixin.equals(def)) {
                        return true;
                    }
                }
            } else if (primaryDef.equals(def)) {
                return true;
            }
            primaryDef = primaryDef.getParentTypeDefinition();
        }
        return false;
    }

    
    public List<PropertyTypeDefinition> getPropertyTypeDefinitions() {
        ArrayList<PropertyTypeDefinition> definitions = 
            new ArrayList<PropertyTypeDefinition>();
        
        for (Map<String, PropertyTypeDefinition> propMap: this.propertyTypeDefinitions.values()) {
            definitions.addAll(propMap.values());
        }
        
        return definitions;
    }

    public Namespace getNamespace(String namespaceUrl) {
        Namespace namespace = this.namespaceUriMap.get(namespaceUrl);
        
        if (namespace == null) 
            namespace = new Namespace(namespaceUrl);
        return namespace;
    }


    public PrimaryResourceTypeDefinition[] getPrimaryResourceTypesForPropDef(
            PropertyTypeDefinition definition) {

        Map<String, Set<PrimaryResourceTypeDefinition>> nsPropMap =
                this.propDefPrimaryTypesMap.get(definition.getNamespace());
        
        if (nsPropMap != null) {
            Set<PrimaryResourceTypeDefinition> rts 
                = nsPropMap.get(definition.getName());
            
            if (rts != null){
                return rts.toArray(new PrimaryResourceTypeDefinition[rts.size()]);
            }
        }
        
        // No resource type definitions found for the given property type definition
        // (dead prop)
        return null;
    }

    public String getResourceTypeTreeAsString() {
        StringBuilder sb = new StringBuilder();
        printResourceTypes(sb, 0, this.rootResourceTypeDefinition);
        sb.append("\n");
        for (MixinResourceTypeDefinition mixin: this.mixins) {
            printResourceTypes(sb, 0, mixin);
            sb.append("\n");
        }
        return sb.toString();
    }

    private String[] getPrimaryResourceTypeNames() {
        List<String> list = new ArrayList<String>();
        for (PrimaryResourceTypeDefinition def : this.primaryTypes) {
            list.add(def.getName());
        }
        return list.toArray(new String[list.size()]);
    }

    public void registerDynamicResourceType(PrimaryResourceTypeDefinition def) {
        List<PrimaryResourceTypeDefinition> tmp = 
            new ArrayList<PrimaryResourceTypeDefinition>();
        tmp.addAll(this.primaryTypes);
        tmp.add(def);
        this.primaryTypes = tmp;
        List<String> primaryTypeNames = new ArrayList<String>();
        for (String name: this.primaryResourceTypeNames) {
            primaryTypeNames.add(name);
        }
        primaryTypeNames.add(def.getName());
        this.primaryResourceTypeNames = primaryTypeNames.toArray(new String[primaryTypeNames.size()]);
        this.resourceTypeNameMap.put(def.getName(), def);
        if (def.getNamespace() == null) {
            throw new IllegalArgumentException(
                "Definition's namespace is null: " + def);
        }
        
        addNamespacesAndProperties(def);
        PrimaryResourceTypeDefinition parent = def.getParentTypeDefinition();
        if (parent == null) {
            throw new IllegalStateException("Must register resource type under an existing resource type");
        }
        List<PrimaryResourceTypeDefinition> children = this.parentChildMap.get(parent);
        if (children == null) {
            children = new ArrayList<PrimaryResourceTypeDefinition>();
            this.parentChildMap.put(parent, children);
        }
        children.add(def);
        addMixins(def);
        injectTypeLocalizationProvider(def);
        
        mapPropertyDefinitionsToPrimaryTypes();

        this.resourceTypeDescendantNames = buildResourceTypeDescendantsMap();
        //logger.info("Resource type tree: \n" + getResourceTypeTreeAsString());
    }
    
    @SuppressWarnings("unchecked")
    private void init() {

        this.primaryTypes = 
            BeanFactoryUtils.beansOfTypeIncludingAncestors(this.applicationContext, 
                    PrimaryResourceTypeDefinition.class, false, false).values();

        this.primaryResourceTypeNames = getPrimaryResourceTypeNames();
        
        this.mixins = 
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
                List<PrimaryResourceTypeDefinition> children = this.parentChildMap.get(parent);

                if (children == null) {
                    children = new ArrayList<PrimaryResourceTypeDefinition>();
                    this.parentChildMap.put(parent, children);
                } 
                children.add(def);
            }

            addMixins(def);
            
            // Inject localized type name provider
            // XXX: I wanted to avoid having to explicitly configure the dependency for
            //      every defined resource type ...
            injectTypeLocalizationProvider(def);
        }

         for (MixinResourceTypeDefinition def: this.mixins) {
            this.resourceTypeNameMap.put(def.getName(), def);
            addNamespacesAndProperties(def);
            
            injectTypeLocalizationProvider(def);
        }

        mapPropertyDefinitionsToPrimaryTypes();
        logger.info("Resource type tree: \n" + getResourceTypeTreeAsString());

    
        this.resourceTypeDescendantNames = buildResourceTypeDescendantsMap();

    }

    private void addMixins(PrimaryResourceTypeDefinition def) {
        List<MixinResourceTypeDefinition> mixinTypes = def.getMixinTypeDefinitions();
        
        if (mixinTypes != null) {
            for (MixinResourceTypeDefinition mix: mixinTypes) {
                if (!this.namespaceUriMap.containsKey(mix.getNamespace().getUri()))
                    this.namespaceUriMap.put(mix.getNamespace().getUri(), mix.getNamespace());                    
            }
        }        

        // Do something else...
        this.mixinTypeDefinitionMap.put(def, mixinTypes);
    
        // Populate map from mixin types to all applicable primary types:
        for (MixinResourceTypeDefinition mixin: mixinTypes) {
            Set<PrimaryResourceTypeDefinition> set = this.mixinTypePrimaryTypesMap.get(mixin);
            if (set == null) {
                set = new HashSet<PrimaryResourceTypeDefinition>();
                this.mixinTypePrimaryTypesMap.put(mixin, set);     
            }
            set.addAll(getDescendantsAndSelf(def));
        }
        
    }
    
    private void addPropertyTypeDefinitions(Set<String> encounteredIds, List<PropertyTypeDefinition> propertyTypes, 
                                            PropertyTypeDefinition[] propDefs) {
        for (PropertyTypeDefinition propDef: propDefs) {
            String id = propDef.getNamespace().getUri() + ":" + propDef.getName();
            // Add only _first_ occurence of property type definition keyed on
            if (encounteredIds.add(id)) {
                propertyTypes.add(propDef);
            }
        }
    }
    
    private void injectTypeLocalizationProvider(ResourceTypeDefinition def) {
        AbstractResourceTypeDefinitionImpl defImpl = (AbstractResourceTypeDefinitionImpl)def;
        
        defImpl.setTypeLocalizationProvider(this.typeLocalizationProvider);
        
        for (PropertyTypeDefinition propDef: def.getPropertyTypeDefinitions()) {
            if (propDef instanceof PropertyTypeDefinitionImpl) {
                PropertyTypeDefinitionImpl propDefImpl
                    = (PropertyTypeDefinitionImpl)propDef;
                propDefImpl.setTypeLocalizationProvider(
                                            this.typeLocalizationProvider);
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
                this.propertyTypeDefinitions.get(namespace);

            if (propDefMap == null) {
                propDefMap = new HashMap<String, PropertyTypeDefinition>();
                this.propertyTypeDefinitions.put(namespace, propDefMap);
            }
            if (propDefMap.get(propDef.getName()) == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Registering property type definition "
                            + propDef + " with namespace : " + namespace);
                }

                propDefMap.put(propDef.getName(), propDef);
            }
        }
    }


    private Set<PrimaryResourceTypeDefinition> getDescendantsAndSelf(PrimaryResourceTypeDefinition def) {
        Set<PrimaryResourceTypeDefinition> s = new HashSet<PrimaryResourceTypeDefinition>();
        s.add(def);
        List<PrimaryResourceTypeDefinition> children = this.parentChildMap.get(def);
        if (children != null) {
            for (PrimaryResourceTypeDefinition child: children) {
                s.addAll(getDescendantsAndSelf(child));
            }
        }
        return s;
    }
    
    
    
    /**
     * Build map of resource type names to names of all descendants
     */
    private Map<String, List<String>> buildResourceTypeDescendantsMap() {
        Map<String, List<String>> resourceTypeDescendantNames = new HashMap<String, List<String>>();
        for (PrimaryResourceTypeDefinition def: this.primaryTypes) {
            List<String> descendantNames = new LinkedList<String>();
            resourceTypeDescendantNames.put(def.getName(), descendantNames);
            populateDescendantNamesRecursively(descendantNames, def);
        }
        return resourceTypeDescendantNames;
    }

    /**
     * Recursively get all descendant names for a given resource type 
     */
    private void populateDescendantNamesRecursively(List<String> names, PrimaryResourceTypeDefinition def) {
        List<PrimaryResourceTypeDefinition> children = getResourceTypeDefinitionChildren(def);
        
        for (PrimaryResourceTypeDefinition child: children) {
            names.add(child.getName());
            populateDescendantNamesRecursively(names, child);
        }
    }


    private void mapPropertyDefinitionsToPrimaryTypes() {

        for (PrimaryResourceTypeDefinition primaryTypeDef: this.primaryTypes) {
            PropertyTypeDefinition[] propDefs = primaryTypeDef.getPropertyTypeDefinitions();
            mapPropertyDefinitionsToPrimaryType(propDefs, primaryTypeDef.getNamespace(), primaryTypeDef);
        }

        for (MixinResourceTypeDefinition mixin: this.mixins) {
            PropertyTypeDefinition[] mixinPropDefs = mixin.getPropertyTypeDefinitions();

            Set<PrimaryResourceTypeDefinition> primaryTypes = mixinTypePrimaryTypesMap.get(mixin);
            for (PrimaryResourceTypeDefinition primaryTypeDef: primaryTypes) {
                mapPropertyDefinitionsToPrimaryType(mixinPropDefs, mixin.getNamespace(), primaryTypeDef);
            }
        }
    }

    private void mapPropertyDefinitionsToPrimaryType(PropertyTypeDefinition[] propDefs,
                                                     Namespace namespace,
                                                     PrimaryResourceTypeDefinition primaryTypeDef) {
        Map<String, Set<PrimaryResourceTypeDefinition>> propDefMap = 
            this.propDefPrimaryTypesMap.get(namespace);
        if (propDefMap == null) {
            propDefMap = new HashMap<String, Set<PrimaryResourceTypeDefinition>>();
            this.propDefPrimaryTypesMap.put(namespace, propDefMap);
        }
            
        for (PropertyTypeDefinition propDef: propDefs) {
            // FIXME: should be removed
            if (propDef instanceof OverridingPropertyTypeDefinitionImpl) {
                continue;
            }
            Set<PrimaryResourceTypeDefinition> definitions = propDefMap.get(propDef.getName());
            if (definitions == null) {
                definitions = new HashSet<PrimaryResourceTypeDefinition>();
                propDefMap.put(propDef.getName(), definitions);
            }
            definitions.add(primaryTypeDef);
        }
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
            sb.append(" [ns: ").append(def.getNamespace().getUri()).append("]");
        }
        if (def instanceof MixinResourceTypeDefinition) {
            sb.append(" (mixin)");
        }
        sb.append("\n");

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
        printPropertyDefinitions(sb, level, def, propDefs);
        
        List<PrimaryResourceTypeDefinition> children = this.parentChildMap.get(def);

        if (children != null) {
            for (PrimaryResourceTypeDefinition child: children) {
                printResourceTypes(sb, level + 1, child);
            }
        }
    }

    private void printPropertyDefinitions(StringBuilder sb, int level, 
            ResourceTypeDefinition resourceType, PropertyTypeDefinition[] propDefs) {
        if (propDefs != null) {
            for (PropertyTypeDefinition definition: propDefs) {
                sb.append("  ");
                for (int j = 0; j < level; j++)
                    sb.append("  ");

                if (resourceType.getNamespace() != Namespace.DEFAULT_NAMESPACE) {
                    sb.append(resourceType.getNamespace().getPrefix()).append(":");
                }
                sb.append(definition.getName());
                sb.append(" ");
                
                String type = definition.getType().toString();
                sb.append("(").append(type.toLowerCase());
                if (definition.isMultiple())
                    sb.append("[]");
                sb.append(") ");
                if (definition.getProtectionLevel() == RepositoryAction.UNEDITABLE_ACTION) {
                    sb.append("(readonly) ");
                }
                if (definition.getPropertyEvaluator() != null) {
                    sb.append("(evaluated) ");
                }
                if (definition instanceof OverridablePropertyTypeDefinition) {
                    if (definition instanceof OverridablePropertyTypeDefinitionImpl) {
                        sb.append("(overridable)");
                    } else {
                        sb.append("(overriding)");
                    }
                }
                sb.append("\n");
            }
        }
    }
    
    public List<HierarchicalNode<String>> getRootNodes() {
        // XXX: Not implemented yet.
        return null;
    }

    public ValueFormatter getValueFormatter() {
        // XXX Not implemented, needs parameterized value formatter!
        return null;
    }

    public void setTypeLocalizationProvider(
            TypeLocalizationProvider typeLocalizationProvider) {
        this.typeLocalizationProvider = typeLocalizationProvider;
    }

    @Required public void setValueFormatterRegistry(ValueFormatterRegistry valueFormatterRegistry) {
        this.valueFormatterRegistry = valueFormatterRegistry;
    }

    @Required public void setValueFactory(ValueFactory valueFactory) {
        this.valueFactory = valueFactory;
    }

}
