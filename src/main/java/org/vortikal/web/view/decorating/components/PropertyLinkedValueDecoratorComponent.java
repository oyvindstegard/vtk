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
package org.vortikal.web.view.decorating.components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.BeanInitializationException;

import org.vortikal.repository.Property;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.security.SecurityContext;
import org.vortikal.web.RequestContext;
import org.vortikal.web.view.decorating.DecoratorRequest;
import org.vortikal.web.view.decorating.DecoratorResponse;


public class PropertyLinkedValueDecoratorComponent extends ViewRenderingDecoratorComponent {
    private static final String DESCRIPTION = 
        "Display the value(s) of a string property, with link(s) to search";
    private static final String PARAMETER_TITLE = "title";
    private static final String PARAMETER_TITLE_DESC = "Optional title (default is 'Tags')";
    private static final String PARAMETER_SERVICEURL = "service-url";
    //private static final String PARAMETER_SERVICEURL_DESC = ""; // Settes dynamisk ved populering av output map

    
    private String defaultURLpattern;
    private Repository repository;
    private PropertyTypeDefinition propertyTypeDefinition;


    private boolean forProcessing = true;

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    protected void processModel(Map<Object, Object> model, DecoratorRequest request,
                                DecoratorResponse response) throws Exception {

        String token = SecurityContext.getSecurityContext().getToken();
        String uri = RequestContext.getRequestContext().getResourceURI();

        Resource resource = repository.retrieve(token, uri, this.forProcessing);
        Property prop = resource.getProperty(this.propertyTypeDefinition);

        if (prop == null) {
            return;
        }

        String title = request.getStringParameter(PARAMETER_TITLE);
        model.put("title", title);
        
        String serviceURL = request.getStringParameter(PARAMETER_SERVICEURL);
        
        List<String> valueList = new ArrayList<String>();
        List<String> urlList = new ArrayList<String>();

        model.put("urls", urlList);
        model.put("values", valueList);
            
        if (this.propertyTypeDefinition.isMultiple()) {
            Value[] values = prop.getValues();
            for (Value value: values) {
                String s = value.getStringValue();
                valueList.add(s);
                urlList.add(getUrl(s, serviceURL));
            }
        } else {
            String value = prop.getValue().getStringValue();
            urlList.add(getUrl(value, serviceURL));
            valueList.add(value);
        }
    }
    

    private String getUrl(String value, String serviceUrl) {
        if(serviceUrl == null)
            return this.defaultURLpattern.replaceAll("%v", value);
        else
            return serviceUrl.replaceAll("%v", value);
    }

    
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();

        if (this.repository == null) {
            throw new BeanInitializationException(
                    "JavaBean property 'repository' not set");
        }
        if (this.propertyTypeDefinition == null) {
            throw new BeanInitializationException(
            "JavaBean property 'propertyTypeDefinition' not set");
        }
        
        if (this.propertyTypeDefinition.getType() != PropertyType.Type.STRING) {
            throw new BeanInitializationException(
                "JavaBean property 'propertyTypeDefinition' not of required type "
                + "PropertyType.Type.STRING");
        }
        
        if (this.defaultURLpattern == null) {
            throw new BeanInitializationException(
            "JavaBean property 'defaultURLpattern' not set");
        }
    }

    public void setForProcessing(boolean forProcessing) {
        this.forProcessing = forProcessing;
    }

    protected String getDescriptionInternal() {
        return DESCRIPTION;
    }

    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new HashMap<String, String>();
        map.put(PARAMETER_TITLE, PARAMETER_TITLE_DESC);
        map.put(PARAMETER_SERVICEURL, "Optional URL to tag service (default is '" + defaultURLpattern + "')");
        return map;
    }

    public void setPropertyTypeDefinition(
            PropertyTypeDefinition propertyTypeDefinition) {
        this.propertyTypeDefinition = propertyTypeDefinition;
    }

    public void setDefaultURLpattern(String defaultURLpattern) {
        this.defaultURLpattern = defaultURLpattern;
    }

}
