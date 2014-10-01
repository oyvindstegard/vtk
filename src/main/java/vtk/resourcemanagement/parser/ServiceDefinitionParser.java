/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.resourcemanagement.parser;

import java.util.ArrayList;
import java.util.List;

import org.antlr.runtime.tree.CommonTree;
import vtk.repository.resource.ResourcetreeLexer;
import vtk.resourcemanagement.ServiceDefinition;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.property.PropertyDescription;

public class ServiceDefinitionParser {

    @SuppressWarnings("unchecked")
    public void parseServices(StructuredResourceDescription srd, List<CommonTree> services) {

        for (CommonTree service : services) {

            String propName = service.getText();
            CommonTree serviceDef = (CommonTree) service.getChild(0);
            List<CommonTree> serviceParams = serviceDef.getChildren();
            String serviceName = serviceDef.getText();

            List<String> requires = null;
            List<String> affects = null;
            if (serviceParams != null) {
                for (CommonTree param : serviceParams) {

                    if (ResourcetreeLexer.REQUIRES == param.getType()) {
                        requires = getList(param.getChildren());
                    }

                    if (ResourcetreeLexer.AFFECTS == param.getType()) {
                        affects = getList(param.getChildren());
                    }

                }
            }

            // Mark all properties the service affects.
            if (affects != null) {
                for (String affectsPropName : affects) {
                    PropertyDescription pd = srd.getPropertyDescription(affectsPropName);
                    if (pd != null) {
                        pd.setAffectingService(serviceName);
                    }
                }
            }

            srd.addServiceDefinition(new ServiceDefinition(propName, serviceName, requires, affects));
        }
    }

    private List<String> getList(List<CommonTree> listParams) {
        List<String> result = new ArrayList<String>();
        for (CommonTree listParam : listParams) {
            result.add(listParam.getText());
        }
        return result;
    }

}
