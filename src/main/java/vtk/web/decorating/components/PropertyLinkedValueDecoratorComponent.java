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
package vtk.web.decorating.components;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.util.text.TextUtils;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.service.URL;

public class PropertyLinkedValueDecoratorComponent extends ViewRenderingDecoratorComponent {

    private static final String DESCRIPTION = "Display the value(s) of a string property, with link(s) to search";
    private static final String PARAMETER_TITLE = "title";
    private static final String PARAMETER_TITLE_DESC = "Optional title (default is 'Tags')";
    private static final String PARAMETER_SERVICEURL = "service-url";

    private String defaultURLpattern;
    private PropertyTypeDefinition propertyTypeDefinition;

    private boolean forProcessing = true;

    @Override
    protected void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        Path uri = requestContext.getResourceURI();

        Resource resource = repository.retrieve(token, uri, this.forProcessing);
        Property prop = resource.getProperty(this.propertyTypeDefinition);

        if (prop == null) {
            prop = resource.getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE, this.propertyTypeDefinition.getName());
        }
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
            for (Value value : values) {
                String s = value.getStringValue();
                valueList.add(s);
                urlList.add(getUrl(s, serviceURL, requestContext.getRequestURL(), request.getLocale()));
            }
        } else {
            String value = prop.getValue().getStringValue();
            try {
                value = java.net.URLEncoder.encode(value, "utf-8");
            }
            catch (UnsupportedEncodingException e) {
                throw new Error("utf-8 not supported");
            }
            urlList.add(getUrl(value, serviceURL, requestContext.getRequestURL(), request.getLocale()));
            valueList.add(value);
        }
    }

    protected String getUrl(String value, String serviceUrl, URL requestURL, Locale locale) {
        if (value == null) {
            throw new IllegalArgumentException("Value is NULL");
        }
        String serviceURLpattern = null;
        value = Matcher.quoteReplacement(value);
        if (serviceUrl == null) {
            serviceURLpattern = TextUtils.replaceAll(this.defaultURLpattern, "%v", value);
        } else {
            serviceURLpattern = TextUtils.replaceAll(serviceUrl, "%v", value);
        }

        return serviceURLpattern;
    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new HashMap<String, String>();
        map.put(PARAMETER_TITLE, PARAMETER_TITLE_DESC);
        map.put(PARAMETER_SERVICEURL, "Optional reference to service (default is '" + defaultURLpattern + "')");
        return map;
    }

    @Override
    protected String getDescriptionInternal() {
        return DESCRIPTION;
    }

    @Required
    public void setPropertyTypeDefinition(PropertyTypeDefinition propertyTypeDefinition) {
        this.propertyTypeDefinition = propertyTypeDefinition;
    }

    @Required
    public void setDefaultURLpattern(String defaultURLpattern) {
        this.defaultURLpattern = defaultURLpattern;
    }

    public void setForProcessing(boolean forProcessing) {
        this.forProcessing = forProcessing;
    }

}
