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
package vtk.repository.resourcetype.property;

import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.PropertySet;
import vtk.repository.resourcetype.PropertyEvaluator;
import vtk.repository.resourcetype.PropertyType;

public class LastModifiedByEvaluator implements PropertyEvaluator {

    public boolean evaluate(Property property, PropertyEvaluationContext ctx) throws PropertyEvaluationException {

    	PropertySet ancestorPropertySet = ctx.getNewResource();

        Property contentLastModified = ancestorPropertySet.getProperty(
            Namespace.DEFAULT_NAMESPACE, PropertyType.CONTENTLASTMODIFIED_PROP_NAME);

        Property contentModifiedBy = ancestorPropertySet.getProperty(
            Namespace.DEFAULT_NAMESPACE, PropertyType.CONTENTMODIFIEDBY_PROP_NAME);

        Property propertiesLastModified = ancestorPropertySet.getProperty(
            Namespace.DEFAULT_NAMESPACE, PropertyType.PROPERTIESLASTMODIFIED_PROP_NAME);
        
        Property propertiesModifiedBy = ancestorPropertySet.getProperty(
            Namespace.DEFAULT_NAMESPACE, PropertyType.PROPERTIESMODIFIEDBY_PROP_NAME);
        
        if (contentLastModified == null || contentModifiedBy == null || propertiesLastModified == null || propertiesModifiedBy == null) {
            throw new PropertyEvaluationException(
                "contentLastModified, contentModifiedBy, propertiesLastModified, propertiesModifiedBy are all needed for evaluation");
        }

        if (propertiesLastModified.getDateValue().getTime() > contentLastModified.getDateValue().getTime()) {
            property.setPrincipalValue(propertiesModifiedBy.getPrincipalValue());            
        } else {
            property.setPrincipalValue(contentModifiedBy.getPrincipalValue());
        }

        return true;
    }
}
