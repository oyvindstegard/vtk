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
package vtk.edit.editor;

import java.util.ArrayList;
import java.util.List;

import vtk.repository.Resource;
import vtk.repository.TypeInfo;
import vtk.repository.resourcetype.MixinResourceTypeDefinition;
import vtk.repository.resourcetype.PrimaryResourceTypeDefinition;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.ResourceTypeDefinition;

public class ResourceTypeEditablePropertyProvider implements EditablePropertyProvider {

    @Override
    public List<PropertyTypeDefinition> getEditProperties(Resource resource, TypeInfo typeInfo) {
        return getPropertyDefinitionsOfType(typeInfo);
    }

    private List<PropertyTypeDefinition> getPropertyDefinitionsOfType(TypeInfo typeInfo) {
        List<ResourceTypeDefinition> resourceDefinitions = new ArrayList<>();
        populateDefinitions(resourceDefinitions, typeInfo.getResourceType());
        List<PropertyTypeDefinition> defs = new ArrayList<>();
        for (ResourceTypeDefinition resourceDef : resourceDefinitions) {
            for (PropertyTypeDefinition propDef: resourceDef.getPropertyTypeDefinitions()) {
                defs.add(propDef);
	    }
        }
        return defs;
    }

    private void populateDefinitions(List<ResourceTypeDefinition> definitions, PrimaryResourceTypeDefinition resourceTypeDefinition) {
        if (resourceTypeDefinition != null) {
            populateDefinitions(definitions, resourceTypeDefinition.getParentTypeDefinition());
            definitions.add(resourceTypeDefinition);
            List<MixinResourceTypeDefinition> mixins = resourceTypeDefinition.getMixinTypeDefinitions();
            if (mixins != null) {
                for (MixinResourceTypeDefinition mixin : mixins) {
                    definitions.add(mixin);
                }
            }
        }
    }

}
