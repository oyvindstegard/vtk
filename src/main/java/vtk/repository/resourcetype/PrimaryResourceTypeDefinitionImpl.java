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
package vtk.repository.resourcetype;

import java.util.Collections;
import java.util.List;

import vtk.web.service.RepositoryAssertion;


public class PrimaryResourceTypeDefinitionImpl
  extends AbstractResourceTypeDefinitionImpl implements PrimaryResourceTypeDefinition {

    private List<MixinResourceTypeDefinition> mixinTypeDefinitions;

    private PrimaryResourceTypeDefinition parentTypeDefinition;
    private RepositoryAssertion[] assertions;

    public PrimaryResourceTypeDefinition getParentTypeDefinition() {
        return this.parentTypeDefinition;
    }

    public void setParentTypeDefinition(PrimaryResourceTypeDefinition parentTypeDefinition) {
        this.parentTypeDefinition = parentTypeDefinition;
    }

    public RepositoryAssertion[] getAssertions() {
        return this.assertions;
    }

    public void setAssertions(RepositoryAssertion[] assertions) {
        this.assertions = assertions;
    }

    public List<MixinResourceTypeDefinition> getMixinTypeDefinitions() {
        if (this.mixinTypeDefinitions == null) {
            return Collections.emptyList();
        }
        return this.mixinTypeDefinitions;
    }

    public void setMixinTypeDefinitions(List<MixinResourceTypeDefinition> mixinTypeDefinitions) {
        this.mixinTypeDefinitions = mixinTypeDefinitions;
    }

    public boolean hasPropertyDefinition(PropertyTypeDefinition def) {
        for (PropertyTypeDefinition propDef: getPropertyTypeDefinitions()) {
            if (propDef.equals(def)) {
                return true;
            }
        }

        for (MixinResourceTypeDefinition mixin: getMixinTypeDefinitions()) {
            for (PropertyTypeDefinition mixinPropDef: mixin.getPropertyTypeDefinitions()) {
                if (mixinPropDef.equals(def)) {
                    return true;
                }
            }
        }

        if (getParentTypeDefinition() != null) {
            return getParentTypeDefinition().hasPropertyDefinition(def);
        }
        
        return false;
    }

    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append("[namespace = ").append(getNamespace());
        sb.append(", name = '").append(getName());
        sb.append("']");
        return sb.toString();
    }
    
}
