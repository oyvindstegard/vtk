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
package vtk.web.actions.properties;

import java.util.List;

import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.actions.UpdateCancelCommand;



public class PropertyEditCommand extends UpdateCancelCommand {

    private static final long serialVersionUID = 7966396227515770572L;
    private PropertyTypeDefinition definition;
    private List<String> possibleValues;
    private String value;
    
    private String namespace;
    private String name;
    private final String hierarchicalHelpUrl;
    

    public PropertyEditCommand(String submitURL, PropertyTypeDefinition definition,
                               String value, List<String> possibleValues, String hierarchicalHelpUrl) {
        super(submitURL);
        this.definition = definition;
        this.value = value;
        this.possibleValues = possibleValues;
        this.hierarchicalHelpUrl = hierarchicalHelpUrl;
        this.namespace = definition != null ? definition.getNamespace().getUri() : null;
        this.name = definition != null ? definition.getName() : null;
    }

    public PropertyTypeDefinition getDefinition() {
        return this.definition;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public String getName() {
        return this.name;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }
    
    public List<String> getPossibleValues() {
        return this.possibleValues;
    }
    
    public void clear() {
        this.definition = null;
        this.value = null;
        this.possibleValues = null;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("PropertyEditCommand[");
        buffer.append("definition = ").append(this.definition);
        if (this.possibleValues == null) {
            buffer.append(", possibleValues = ").append("null");
        } else {
            buffer.append(", possibleValues = ").append(this.possibleValues);
        }
        buffer.append(", value = ").append(this.value);
        buffer.append(", namespace = ").append(this.namespace);
        buffer.append(", name = ").append(this.name);
        buffer.append("]");
        return buffer.toString();
    }

    public String getHierarchicalHelpUrl() {
        return hierarchicalHelpUrl;
    }
    
}

