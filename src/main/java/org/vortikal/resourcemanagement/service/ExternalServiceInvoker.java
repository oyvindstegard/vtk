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
package org.vortikal.resourcemanagement.service;

import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Property;
import org.vortikal.repository.PropertyEvaluationContext;
import org.vortikal.repository.resourcetype.PropertyType.Type;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.resourcemanagement.ServiceDefinition;

@Deprecated
public class ExternalServiceInvoker implements ApplicationContextAware {
    private ApplicationContext applicationContext;

    public void invokeService(Property invokingProperty, PropertyEvaluationContext ctx,
            ServiceDefinition serviceDefinition) {

        if (invalidProperty(invokingProperty)) {
            return;
        }

        if (missingRequired(ctx, serviceDefinition)) {
            return;
        }

        String serviceName = serviceDefinition.getServiceName();
        if (this.applicationContext != null && this.applicationContext.containsBean(serviceName)) {
            ExternalService externalService = (ExternalService) this.applicationContext.getBean(serviceName);
            externalService.invoke(invokingProperty, ctx, serviceDefinition);
        }
    }

    private boolean missingRequired(PropertyEvaluationContext ctx, ServiceDefinition serviceDefinition) {
        
        List<String> requiredProps = serviceDefinition.getRequires();
        if (requiredProps == null || requiredProps.size() == 0) {
            return false;
        }

        for (String requiredProp : requiredProps) {
            Property prop = ctx.getNewResource().getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE, requiredProp);
            if (invalidProperty(prop)) {
                return true;
            }
        }

        return false;
    }

    private boolean invalidProperty(Property property) {

        if (property == null) {
            return true;
        }

        if (property.getDefinition().isMultiple()) {
            Value[] values = property.getValues();
            return values == null || values.length == 0;
        }

        return property.getValue() == null
                || (Type.BOOLEAN.equals(property.getDefinition().getType()) && property.getBooleanValue() == false);
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

}
