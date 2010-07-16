/* Copyright (c) 2008, University of Oslo, Norway
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
package org.vortikal.repository.search.preprocessor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.Property;
import org.vortikal.repository.Resource;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.Value;

public class SubfoldersExpressionEvaluator extends MultiValuePropertyInExpressionEvaluator {

    private final String variableName = "subfolders";
    private PropertyTypeDefinition subfolderPropDef;
    private PropertyTypeDefinition recursiveListingPropDef;

    @Override
    protected Property getMultiValueProperty(Resource resource) {
        Property recursiveListing = resource.getProperty(recursiveListingPropDef);
        if (recursiveListing != null && recursiveListing.getBooleanValue() == false) {
            return null;
        }
        Property subfolders = resource.getProperty(subfolderPropDef);

        String parent = resource.getURI().toString();
        List<Value> values = new ArrayList<Value>();
        for (Value value : subfolders.getValues()) {
            String subfolder = value.getStringValue();
            subfolder = subfolder.startsWith("/") ? subfolder : "/" + subfolder;
            if (!subfolder.startsWith(parent)) {
                subfolder = parent + subfolder;
            }
            subfolder = subfolder.endsWith("/") ? subfolder : subfolder + "/";
            values.add(new Value(subfolder, PropertyType.Type.STRING));
        }
        subfolders.setValues(values.toArray(new Value[values.size()]));

        return subfolders;
    }

    @Override
    protected String getVariableName() {
        return this.variableName;
    }

    @Required
    public void setSubfolderPropDef(PropertyTypeDefinition subfolderPropDef) {
        this.subfolderPropDef = subfolderPropDef;
    }

    @Required
    public void setRecursiveListingPropDef(PropertyTypeDefinition recursiveListingPropDef) {
        this.recursiveListingPropDef = recursiveListingPropDef;
    }

}