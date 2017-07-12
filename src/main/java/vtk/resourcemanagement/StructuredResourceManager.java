/* Copyright (c) 2009,2015 University of Oslo, Norway
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
package vtk.resourcemanagement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Namespace;
import vtk.repository.RepositoryAction;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.OverridablePropertyTypeDefinitionImpl;
import vtk.repository.resourcetype.OverridingPropertyTypeDefinitionImpl;
import vtk.repository.resourcetype.PrimaryResourceTypeDefinition;
import vtk.repository.resourcetype.PrimaryResourceTypeDefinitionImpl;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.RepositoryAssertion;
import vtk.repository.resourcetype.ResourceTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.resourcetype.ValueFactory;
import vtk.repository.resourcetype.ValueFormatterRegistry;
import vtk.resourcemanagement.EditRule.EditRuleType;
import vtk.resourcemanagement.parser.ParserConstants;
import vtk.resourcemanagement.property.DerivedPropertyDescription;
import vtk.resourcemanagement.property.EvaluatorResolver;
import vtk.resourcemanagement.property.JSONPropertyDescription;
import vtk.resourcemanagement.property.PropertyDescription;
import vtk.resourcemanagement.property.SimplePropertyDescription;

public class StructuredResourceManager {
    private static final Logger logger = LoggerFactory.getLogger(StructuredResourceManager.class);

    private static final Map<String, PropertyType.Type> PROPTYPE_MAP = new HashMap<>();
    static {
        PROPTYPE_MAP.put(ParserConstants.PROPTYPE_STRING, PropertyType.Type.STRING);
        PROPTYPE_MAP.put(ParserConstants.PROPTYPE_HTML, PropertyType.Type.HTML);
        PROPTYPE_MAP.put(ParserConstants.PROPTYPE_SIMPLEHTML, PropertyType.Type.HTML);
        PROPTYPE_MAP.put(ParserConstants.PROPTYPE_BOOLEAN, PropertyType.Type.BOOLEAN);
        PROPTYPE_MAP.put(ParserConstants.PROPTYPE_INT, PropertyType.Type.INT);
        PROPTYPE_MAP.put(ParserConstants.PROPTYPE_DATETIME, PropertyType.Type.TIMESTAMP);
        PROPTYPE_MAP.put(ParserConstants.PROPTYPE_IMAGEREF, PropertyType.Type.IMAGE_REF);
        PROPTYPE_MAP.put(ParserConstants.PROPTYPE_MEDIAREF, PropertyType.Type.IMAGE_REF);
        PROPTYPE_MAP.put(ParserConstants.PROPTYPE_RESOURCEREF, PropertyType.Type.IMAGE_REF);
        PROPTYPE_MAP.put(ParserConstants.PROPTYPE_BINARY, PropertyType.Type.BINARY);
        PROPTYPE_MAP.put(ParserConstants.PROPTYPE_JSON, PropertyType.Type.JSON);
    }
    private ResourceTypeTree resourceTypeTree;
    private PrimaryResourceTypeDefinition baseType;
    private JSONObjectSelectAssertion assertion;
    private Namespace namespace = Namespace.STRUCTURED_RESOURCE_NAMESPACE;

    private Map<String, StructuredResourceDescription> types = new HashMap<>();
    private ValueFactory valueFactory;
    private ValueFormatterRegistry valueFormatterRegistry;
    private EvaluatorResolver evaluatorResolver;

    public void register(StructuredResourceDescription description) throws Exception {
        String name = description.getName();
        ResourceTypeDefinition existing = null;
        try {
            existing = resourceTypeTree.getResourceTypeDefinitionByName(name);
        } catch (IllegalArgumentException e) {
            // Ignore
        }
        if (existing != null) {
            throw new IllegalArgumentException("Resource type of name " + name + " already exists");
        }
        addNewTypeDefinition(description, name);
    }

    public void refresh(StructuredResourceDescription newDescription) throws Exception {
        String name = newDescription.getName();
        StructuredResourceDescription oldDescription = this.get(name);
        if (oldDescription == null) {
            throw new IllegalStateException("Resource type of name " + name + " do not exists");
        }

        if (oldDescription.getDisplayTemplate() == null
                && newDescription.getDisplayTemplate() != null) {
            logger.info("Adding display template: " + oldDescription.getName());
            oldDescription.setDisplayTemplate(newDescription.getDisplayTemplate());
        }
        else if (newDescription.getDisplayTemplate() == null
                && oldDescription.getDisplayTemplate() != null) {
            logger.info("Removing display template: " + oldDescription.getName());
            oldDescription.setDisplayTemplate(null);
        }
        else if ((
                oldDescription.getDisplayTemplate() != null
                        && !newDescription.getDisplayTemplate().equals(oldDescription.getDisplayTemplate())
        )) {
            logger.info("Updating display template: " + oldDescription.getName());
            oldDescription.getDisplayTemplate().setContent(newDescription.getDisplayTemplate().getTemplate());
        }
        for (ComponentDefinition newCompDef : newDescription.getComponentDefinitions()) {
            if (newCompDef.getName().equals("view")) continue;
            for (ComponentDefinition oldCompDef : oldDescription.getComponentDefinitions()) {
                if (newCompDef.getName().equals(oldCompDef.getName())) {
                    if (!newCompDef.getDefinition().equals(oldCompDef.getDefinition())) {
                        logger.info("Updating component " + oldDescription.getName()
                                + ":" + oldCompDef);
                        oldCompDef.setDefinition(newCompDef.getDefinition());
                    }
                }
            }

        }
    }

    public StructuredResourceDescription get(String name) {
        return this.types.get(name);
    }

    public List<StructuredResourceDescription> list() {
        List<StructuredResourceDescription> result = new ArrayList<>();
        result.addAll(this.types.values());
        return result;
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    @Required
    public void setBaseType(PrimaryResourceTypeDefinition baseType) {
        this.baseType = baseType;
    }

    @Required
    public void setValueFactory(ValueFactory valueFactory) {
        this.valueFactory = valueFactory;
    }

    @Required
    public void setValueFormatterRegistry(ValueFormatterRegistry valueFormatterRegistry) {
        this.valueFormatterRegistry = valueFormatterRegistry;
    }

    @Required
    public void setAssertion(JSONObjectSelectAssertion assertion) {
        this.assertion = assertion;
    }

    public void setNamespace(Namespace namespace) {
        this.namespace = namespace;
    }

    @Required
    public void setEvaluatorResolver(EvaluatorResolver evaluatorResolver) {
        this.evaluatorResolver = evaluatorResolver;
    }

    private void addNewTypeDefinition(StructuredResourceDescription description, String name) throws Exception {
        if (description.getInheritsFrom() !=  null) {
            description.setParent(this.get(description.getInheritsFrom()));
        }
        description.validate();
        PrimaryResourceTypeDefinition def = createResourceType(description);
        this.resourceTypeTree.registerDynamicResourceType(def);

        this.types.put(name, description);
    }

    private PrimaryResourceTypeDefinition createResourceType(StructuredResourceDescription description)
            throws Exception {
        PrimaryResourceTypeDefinitionImpl def = new PrimaryResourceTypeDefinitionImpl();

        def.setName(description.getName());
        def.setNamespace(this.namespace);

        ResourceTypeDefinition parentDefinition = this.baseType;

        PropertyTypeDefinition[] descPropDefs = createPropDefs(description);

        List<PropertyTypeDefinition> allPropDefs = new ArrayList<PropertyTypeDefinition>();
        for (PropertyTypeDefinition d : descPropDefs) {
            allPropDefs.add(d);
        }
        def.setPropertyTypeDefinitions(allPropDefs.toArray(new PropertyTypeDefinition[allPropDefs.size()]));

        List<RepositoryAssertion> assertions = createAssertions(description);
        def.setAssertions(assertions.toArray(new RepositoryAssertion[assertions.size()]));

        if (description.getInheritsFrom() != null) {
            String parent = description.getInheritsFrom();
            if (!this.types.containsKey(parent)) {
                throw new IllegalArgumentException("Can only inherit from other structured resource types: ["
                        + description.getName() + ":" + parent + "]");
            }
            parentDefinition = this.resourceTypeTree.getResourceTypeDefinitionByName(parent);
        }

        if (parentDefinition instanceof PrimaryResourceTypeDefinitionImpl) {
            def.setParentTypeDefinition((PrimaryResourceTypeDefinitionImpl) parentDefinition);
            updateAssertions(parentDefinition, description.getName());
        }

        def.afterPropertiesSet();
        return def;
    }

    private void updateAssertions(ResourceTypeDefinition parent, String name) {

        while (true) {
            if (!(parent instanceof PrimaryResourceTypeDefinitionImpl)) {
                break;
            }
            PrimaryResourceTypeDefinitionImpl impl = (PrimaryResourceTypeDefinitionImpl) parent;

            for (RepositoryAssertion repositoryAssertion : impl.getAssertions()) {
                if (repositoryAssertion instanceof JSONObjectSelectAssertion) {
                    JSONObjectSelectAssertion jsonAssertion = (JSONObjectSelectAssertion) repositoryAssertion;
                    if ("resourcetype".equals(jsonAssertion.getExpression())) {
                        jsonAssertion.addExpectedValue(name);
                    }
                }
            }
            if (parent == this.baseType) {
                break;
            }
            parent = impl.getParentTypeDefinition();
        }
    }

    private List<RepositoryAssertion> createAssertions(StructuredResourceDescription description) {
        List<RepositoryAssertion> assertions = new ArrayList<RepositoryAssertion>();
        JSONObjectSelectAssertion typeElementAssertion = this.assertion.createAssertion("resourcetype",
                description.getName());
        assertions.add(typeElementAssertion);
        if (description.getPropertyDescriptions() != null) {
            for (PropertyDescription propDesc : description.getPropertyDescriptions()) {
                if (propDesc instanceof SimplePropertyDescription) {
                    if (((SimplePropertyDescription) propDesc).isRequired()) {
                        JSONObjectSelectAssertion propAssertion = this.assertion.createAssertion("properties."
                                + propDesc.getName());
                        assertions.add(propAssertion);
                    }
                }
            }
        }
        return assertions;
    }

    private PropertyTypeDefinition[] createPropDefs(StructuredResourceDescription description) throws Exception {
        List<PropertyDescription> propertyDescriptions = description.getPropertyDescriptions();
        List<PropertyTypeDefinition> result = new ArrayList<PropertyTypeDefinition>();
        if (propertyDescriptions != null) {
            for (PropertyDescription d : propertyDescriptions) {
                PropertyTypeDefinition def = createPropDef(d, description);
                if (def != null) {
                    result.add(def);
                }
            }
        }

        return result.toArray(new PropertyTypeDefinition[result.size()]);
    }

    private OverridablePropertyTypeDefinitionImpl resolveOverride(PropertyDescription propDesc,
                                                                  StructuredResourceDescription resourceDesc) {
        String name = propDesc.getOverrides();
        // Allow overriding of only "internal" properties for now:
        Namespace defaultNamespace = Namespace.DEFAULT_NAMESPACE;
        PropertyTypeDefinition target = this.resourceTypeTree.getPropertyTypeDefinition(defaultNamespace, name);

        String typeName = resourceDesc.getInheritsFrom();
        if (typeName == null) {
            typeName = this.baseType.getName();
        }
        ResourceTypeDefinition startingPoint = this.resourceTypeTree.getResourceTypeDefinitionByName(typeName);
        List<PropertyTypeDefinition> allProps = this.resourceTypeTree
                .getPropertyTypeDefinitionsIncludingAncestors(startingPoint);
        boolean foundDef = false;
        for (PropertyTypeDefinition propDef : allProps) {
            if (propDef.getNamespace().equals(target.getNamespace()) && propDef.getName().equals(target.getName())) {
                foundDef = true;
                break;
            }
        }
        if (!foundDef) {
            throw new IllegalArgumentException("Property " + resourceDesc.getName() + "." + propDesc.getName()
                    + " cannot override property '" + name + "' from an unrelated resource type");
        }
        if (!(target instanceof OverridablePropertyTypeDefinitionImpl)) {
            throw new IllegalArgumentException("Property " + resourceDesc.getName() + "." + propDesc.getName()
                    + " cannot override property '" + name + "' (not overridable)");
        }
        return (OverridablePropertyTypeDefinitionImpl) target;
    }

    private PropertyTypeDefinition createPropDef(PropertyDescription propertyDescription,
                                                 StructuredResourceDescription resourceDescription) throws Exception {
        if (propertyDescription.isNoExtract()) {
            return null;
        }
        if (propertyDescription.getOverrides() != null) {
            return createOverridingPropDef(propertyDescription, resourceDescription);
        }
        return createRegularPropDef(propertyDescription, resourceDescription);
    }

    private PropertyTypeDefinition createOverridingPropDef(PropertyDescription propertyDescription,
                                                           StructuredResourceDescription resourceDescription) throws Exception {
        OverridablePropertyTypeDefinitionImpl overridableDef = resolveOverride(propertyDescription, resourceDescription);

        OverridingPropertyTypeDefinitionImpl overridingDef = new OverridingPropertyTypeDefinitionImpl();
        overridingDef.setOverriddenPropDef(overridableDef);
        overridingDef.setPropertyEvaluator(this.evaluatorResolver.createPropertyEvaluator(propertyDescription,
                resourceDescription));
        overridingDef.afterPropertiesSet();
        return overridingDef;
    }

    private PropertyTypeDefinition createRegularPropDef(PropertyDescription propertyDescription,
                                                        StructuredResourceDescription resourceDescription) {
        OverridablePropertyTypeDefinitionImpl def = new OverridablePropertyTypeDefinitionImpl();

        def.setName(propertyDescription.getName());
        def.setNamespace(this.namespace);
        if (propertyDescription instanceof DerivedPropertyDescription) {
            def.setType(Type.STRING);
        } else if (propertyDescription instanceof JSONPropertyDescription) {
            def.setType(Type.JSON);
            // No need to blindly index all JSON fields:
            if (((JSONPropertyDescription) propertyDescription).getIndexable()) {
                def.addMetadata(PropertyTypeDefinition.METADATA_INDEXABLE_JSON, true);
            }
        } else {
            def.setType(mapType(propertyDescription));
        }
        def.setProtectionLevel(RepositoryAction.UNEDITABLE_ACTION);
        boolean mandatory = false;
        if (propertyDescription instanceof SimplePropertyDescription) {
            mandatory = ((SimplePropertyDescription) propertyDescription).isRequired();
        }
        def.setMandatory(mandatory);
        def.setMultiple(propertyDescription.isMultiple());
        def.setValueFactory(this.valueFactory);
        def.setValueFormatterRegistry(this.valueFormatterRegistry);
        def.setPropertyEvaluator(this.evaluatorResolver.createPropertyEvaluator(propertyDescription,
                resourceDescription));

        if (propertyDescription instanceof SimplePropertyDescription) {

            SimplePropertyDescription spd = ((SimplePropertyDescription) propertyDescription);

            List<EditRule> editRules = resourceDescription.getEditRules();
            if (editRules != null && editRules.size() > 0) {
                Map<String, Set<String>> editHints = new HashMap<String, Set<String>>();
                for (EditRule editRule : editRules) {
                    if (EditRuleType.EDITHINT.equals(editRule.getType())) {
                        if (editRule.getName().equals(propertyDescription.getName())) {
                            String key = editRule.getEditHintKey();
                            Set<String> values = editHints.get(key);
                            if (values == null) {
                                values = new HashSet<String>();
                                editHints.put(key, values);
                            }
                            values.add(editRule.getEditHintValue());
                        }
                    }
                }
                def.addMetadata(PropertyTypeDefinition.METADATA_EDITING_HINTS, editHints);
            }

            String defaultValue = spd.getDefaultValue();
            if (defaultValue != null) {
                this.setDefaultValue(def, defaultValue);
            }
        }
        def.afterPropertiesSet();
        return def;
    }

    private void setDefaultValue(OverridablePropertyTypeDefinitionImpl def, String defaultValue) {
        Type type = def.getType();
        switch (type) {
            case STRING:
                def.setDefaultValue(new Value(defaultValue, type));
                return;
            case BOOLEAN:
                if ("true".equalsIgnoreCase(defaultValue) || "false".equalsIgnoreCase(defaultValue)) {
                    def.setDefaultValue(new Value(Boolean.valueOf(defaultValue)));
                    return;
                }
                throw new IllegalArgumentException("Default value of a boolean property can only be 'true' or 'false'");
            case INT:
                Integer numb = null;
                try {
                    numb = Integer.parseInt(defaultValue);
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("Default value of an int property can only be a valid number");
                }
                def.setDefaultValue(new Value(numb));
                return;
            default:
                return;
        }
    }

    private Type mapType(PropertyDescription d) {
        String type = d.getType();
        Type result = PROPTYPE_MAP.get(type);
        if (result == null) {
            throw new IllegalArgumentException("Unmapped property type: " + type);
        }
        return result;
    }

}
