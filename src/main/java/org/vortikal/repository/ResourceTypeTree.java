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

import java.util.List;

import org.vortikal.repository.resourcetype.MixinResourceTypeDefinition;
import org.vortikal.repository.resourcetype.PrimaryResourceTypeDefinition;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.ResourceTypeDefinition;

public interface ResourceTypeTree extends HierarchicalVocabulary<String> {

    public PropertyTypeDefinition getPropertyTypeDefinition(
            Namespace namespace, String name);

    public String getResourceTypeTreeAsString();

    public PrimaryResourceTypeDefinition getRoot();
    
    public List<MixinResourceTypeDefinition> getMixinTypes(PrimaryResourceTypeDefinition def);
    
    /**
     * Search upwards in resource type tree, collect property type definitions
     * from all encountered resource type definitions including mixin resource types.
     * Assuming that mixin types can never have other mixin types attached.
     * 
     * If there are more than one occurence of the same property type definition
     * for the given resource type, only the first occurence in the resource type
     * tree is added to the returned list (upward direction).
     * 
     * @param def The <code>ResourceTypeDefinition</code> 
     * @return A <code>Set</code> of <code>PropertyTypeDefinition</code> instances.
     */
    public List<PropertyTypeDefinition> getPropertyTypeDefinitionsForResourceTypeIncludingAncestors(
                                                    ResourceTypeDefinition def);

    /**
     * Determines whether a named resource type is contained in another resource type
     * @param def the resource type definition
     * @param resourceTypeName the resource type to check for
     * @return <code>true</code> if the named type exists and equals,
     * or is a child of, or is a mixin of the given type definition,
     * <code>false</code> otherwise
     */
    public boolean isContainedType(ResourceTypeDefinition def, String resourceTypeName);
    

    /**
     * Gets a resource type definition object by name.
     * @param name the name of the resource type
     * @return the resource type definition
     */
    public ResourceTypeDefinition getResourceTypeDefinitionByName(String name);
    
    /**
     * Gets a property type definition by prefix and name
     *
     * @param prefix the prefix of the property type
     * @param name the name of the property
     * @return a the property definition, or <code>null</code> if not found
     */
    public PropertyTypeDefinition getPropertyDefinitionByPrefix(String prefix, String name);

    /**
     * XXX: equivalent methods for resource-types, mixin-types, etc ?
     * @return Return flat list of all registered property type definitions.
     */
    public List<PropertyTypeDefinition> getPropertyTypeDefinitions();

    /**
     * Return a <code>List</code> of the immediate children of the given resource type.
     * @param def
     * @return
     */
    public List<PrimaryResourceTypeDefinition> getResourceTypeDefinitionChildren(PrimaryResourceTypeDefinition def);

    public Namespace getNamespace(String namespaceUrl);

    /** 
     * Since a mixin might be included in several primary resource types, this
     * method returns an array.
     * 
     * @return an array containing the <code>PrimaryResourceTypeDefinition</code>s that define
     * this property, or an empty array if none 
     */
    public PrimaryResourceTypeDefinition[] getPrimaryResourceTypesForPropDef(
        PropertyTypeDefinition definition);

}
