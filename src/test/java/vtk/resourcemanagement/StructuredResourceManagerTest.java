/* Copyright (c) 2015, University of Oslo, Norway
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

import org.junit.Before;
import org.junit.Test;
import vtk.repository.resourcetype.ValueFormatterRegistry;
import vtk.resourcemanagement.property.EvaluatorResolver;
import vtk.resourcemanagement.property.PropertyDescription;
import vtk.resourcemanagement.property.SimplePropertyDescription;
import vtk.testing.mocktypes.MockResourceTypeTree;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


public class StructuredResourceManagerTest {
    private static final String BASE_TEMPLATE = "<!DOCTYPE html>\n" +
            "<html><head><title>%s</title></head><body>%s</body></html>";
    private StructuredResourceManager resourceManager;
    private StructuredResourceDescription parent;
    private StructuredResourceDescription child;

    @Before
    public void setUp() throws Exception {
        ValueFormatterRegistry valueFormatterRegistry = new ValueFormatterRegistry();
        valueFormatterRegistry.setValueFormatters(new HashMap<>());
        resourceManager = new StructuredResourceManager();
        resourceManager.setResourceTypeTree(new MockResourceTypeTree(new HashMap<>()));
        resourceManager.setEvaluatorResolver(new EvaluatorResolver());
        resourceManager.setValueFormatterRegistry(valueFormatterRegistry);
        resourceManager.setAssertion(new JSONObjectSelectAssertion());


        ComponentDefinition titleComponent = new ComponentDefinition("title", "title component");
        ComponentDefinition authorComponent = new ComponentDefinition("author", "author component");
        PropertyDescription titleProperty = new SimplePropertyDescription();
        titleProperty.setName("title");
        titleProperty.setType("string");

        PropertyDescription authorProperty = new SimplePropertyDescription();
        authorProperty.setName("author");
        authorProperty.setType("string");

        parent = new StructuredResourceDescription();
        parent.setName("base-type");
        parent.setDisplayTemplate(new DisplayTemplate(
                String.format(BASE_TEMPLATE, "parent", "<h1>parent</h1")
        ));
        parent.addComponentDefinition(titleComponent);
        parent.setPropertyDescriptions(Arrays.asList(
                titleProperty
        ));

        child = new StructuredResourceDescription();
        child.setName("sub-type");
        child.setInheritsFrom("base-type");
        child.setDisplayTemplate(new DisplayTemplate(
                String.format(BASE_TEMPLATE, "child", "<h1>child</h1")
        ));
        child.addComponentDefinition(authorComponent);
        child.setPropertyDescriptions(Arrays.asList(
                authorProperty
        ));
    }

    @Test
    public void child_should_inherit_from_parent() throws Exception {
        resourceManager.register(parent);
        resourceManager.register(child);
        StructuredResourceDescription subType = resourceManager.get("sub-type");
        PropertyDescription titleProperty = subType.getPropertyDescription("title");
        assertThat(titleProperty).isNotNull();
        assertThat(titleProperty.getName()).isEqualTo("title");

        assertThat(subType.getComponentDefinitions()).hasSize(1);
        assertThat(subType.getAllComponentDefinitions()).hasSize(2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void register_child_before_parent_is_illegal() throws Exception {
        resourceManager.register(child);
    }

    @Test
    public void refresh_should_update_component_definition_content_and_template() throws Exception {
        resourceManager.register(parent);
        resourceManager.register(child);

        ComponentDefinition newTitleComponent = new ComponentDefinition("title", "new title component");

        StructuredResourceDescription newParent = new StructuredResourceDescription();
        newParent.setName("base-type");
        newParent.setDisplayTemplate(new DisplayTemplate(
                String.format(BASE_TEMPLATE, "parent", "<h1>new parent</h1>")
        ));
        newParent.addComponentDefinition(newTitleComponent);
        newParent.setPropertyDescriptions(parent.getPropertyDescriptions());
        resourceManager.refresh(newParent);

        StructuredResourceDescription baseType = resourceManager.get("base-type");
        List<ComponentDefinition> componentDefinitions = baseType.getComponentDefinitions();
        assertThat(componentDefinitions).isNotEmpty();
        ComponentDefinition titleComponent = componentDefinitions.get(0);
        assertThat(titleComponent.getName()).isEqualTo("title");
        assertThat(titleComponent.getDefinition()).startsWith("new ");
        assertThat(baseType.getDisplayTemplate().getTemplate()).contains("new parent");
    }

    @Test
    public void handle_refresh_for_resource_without_template() throws Exception {
        ComponentDefinition titleComponent = new ComponentDefinition("title", "title component");
        StructuredResourceDescription oldParent = new StructuredResourceDescription();
        oldParent.setName("base-type");
        oldParent.addComponentDefinition(titleComponent);
        resourceManager.register(oldParent);

        ComponentDefinition newTitleComponent = new ComponentDefinition("title", "new title component");
        StructuredResourceDescription newParent = new StructuredResourceDescription();
        newParent.setName("base-type");
        newParent.addComponentDefinition(newTitleComponent);

        resourceManager.refresh(newParent);

        StructuredResourceDescription baseType = resourceManager.get("base-type");
        assertThat(baseType.getComponentDefinitions()).hasSize(1);
        assertThat(baseType.getAllComponentDefinitions().get(0).getDefinition()).startsWith("new");
    }

    @Test
    public void add_template_if_new_resource_have_it_but_the_old_do_not() throws Exception {
        StructuredResourceDescription oldParent = new StructuredResourceDescription();
        oldParent.setName("base-type");
        resourceManager.register(oldParent);

        StructuredResourceDescription newParent = new StructuredResourceDescription();
        newParent.setName("base-type");
        newParent.setDisplayTemplate(new DisplayTemplate(
                String.format(BASE_TEMPLATE, "parent", "<h1>parent</h1")
        ));

        resourceManager.refresh(newParent);

        StructuredResourceDescription baseType = resourceManager.get("base-type");
        assertThat(baseType.getDisplayTemplate().getTemplate()).contains("parent");
    }

    @Test
    public void remove_template_if_new_resource_do_not_have_it_but_the_old_do() throws Exception {
        StructuredResourceDescription oldParent = new StructuredResourceDescription();
        oldParent.setName("base-type");
        oldParent.setDisplayTemplate(new DisplayTemplate(
                String.format(BASE_TEMPLATE, "parent", "<h1>parent</h1")
        ));
        resourceManager.register(oldParent);

        StructuredResourceDescription newParent = new StructuredResourceDescription();
        newParent.setName("base-type");
        newParent.setDisplayTemplate(null);

        resourceManager.refresh(newParent);

        StructuredResourceDescription baseType = resourceManager.get("base-type");
        assertThat(baseType.getDisplayTemplate()).isNull();
    }
}
