/* Copyright (c) 2010, University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.util.repository.PropertyAspectDescription;
import vtk.util.repository.PropertyAspectField;
import vtk.util.repository.PropertyAspectResolver;
import vtk.util.text.Json;
import vtk.web.RequestContext;
import vtk.web.SimpleFormController;
import vtk.web.actions.UpdateCancelCommand;
import vtk.web.actions.properties.ConfigurableJSONPropertyEditor.Form;
import vtk.web.service.URL;

public class ConfigurableJSONPropertyEditor extends SimpleFormController<Form> {

    private PropertyTypeDefinition propertyDefinition;
    private String toplevelField;
    private PropertyAspectDescription fieldConfig;
    private String token;
    
    public static class Form extends UpdateCancelCommand {
        private String configError = null;
        private List<FormElement> elements;
        
        public Form(URL url, List<FormElement> elements) {
            super(url.toString());
            this.elements = elements;
        }
        public Form(String configError) {
            this.configError = configError;
        }
        public void setElements(List<FormElement> elements) {
            this.elements = elements;
        }
        public List<FormElement> getElements() {
            return this.elements;
        }
        public String getConfigError() {
            return this.configError;
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("configError : ").append(this.configError);
            sb.append(", elements : ").append(this.elements);
            sb.append("}");
            return sb.toString();
        }
    }
    
    public static class FormElement {
        private PropertyAspectField desc;
        private Locale locale;
        private Object value;
        private Object inheritedValue;
        
        public FormElement(PropertyAspectField desc, Locale locale) {
            this.desc = desc;
            this.locale = locale;
        }
        
        public Object getIdentifier() {
            return this.desc.getIdentifier();
        }

        public boolean isInheritable() {
            return this.desc.isInherited();
        }
        
        public String getLabel() {
            return this.desc.getLocalizedName(this.locale);
        }
        
        public String getType() {
            return this.desc.getType();
        }
        
        public Object getValue() {
            return this.value;
        }
        
        public void setValue(Object value) {
            this.value = value;
        }

        public List<Map<String, Object>> getPossibleValues() {
            if (!"enum".equals(this.desc.getType())) {
                return null;
            }
            List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
            List<?> values = this.desc.getValues();
            for (Object v : values) {
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("label", this.desc.getLocalizedValue(v, this.locale));
                map.put("value", v);
                boolean selected = this.value == null ? v == null 
                        : this.value.equals(v);
                map.put("selected", selected);
                result.add(map);
            }
            return result;
        }

        public void setInheritedValue(Object inheritedValue) {
            this.inheritedValue = inheritedValue;
        }

        public Object getInheritedValue() {
            return this.inheritedValue;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("desc : ").append(this.desc);
            sb.append(", locale : ").append(this.locale);
            sb.append(", value : ").append(this.value);
            sb.append(", inheritedValue : ").append(this.inheritedValue);
            sb.append("}");
            return sb.toString();
        }
    }

    @Override
    protected Form formBackingObject(HttpServletRequest request)
            throws Exception {
        if (this.fieldConfig.getError() != null) {
            return new Form(this.fieldConfig.getError().getMessage());
        }
        
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        Resource resource = repository.retrieve(token, uri, false);
        Property property = resource.getProperty(this.propertyDefinition);
        
        Json.MapContainer toplevel = null;
        
        if (property != null) {
            Json.MapContainer propertyValue = property.getJSONValue();
            if (propertyValue != null) {
                toplevel = propertyValue.objectValue(this.toplevelField);
            }
        }
        Locale requestLocale = RequestContextUtils.getLocale(request);
        
        PropertyAspectResolver resolver = new PropertyAspectResolver(
                this.propertyDefinition, this.fieldConfig, this.token);
        Json.MapContainer combined = uri == Path.ROOT ? null 
                : resolver.resolve(uri.getParent(), this.toplevelField);
        
        List<FormElement> elements = new ArrayList<FormElement>();
        for (PropertyAspectField field: this.fieldConfig.getFields()) {
            FormElement element = new FormElement(field, requestLocale);
            if (toplevel != null) {
                Object object = toplevel.get(field.getIdentifier());
                element.setValue(object);
            }
            if (field.isInherited()) {
                if (combined != null && combined.get(field.getIdentifier()) != null) {
                    element.setInheritedValue(combined.get(field.getIdentifier()));
                }
            }
            elements.add(element);
        }
        URL url = requestContext.getService().constructURL(uri);
        return new Form(url, elements);
    }

    
    @Override
    protected void onBindAndValidate(HttpServletRequest request,
            Form object, BindException errors) throws Exception {
        
        RequestContext requestContext = RequestContext.getRequestContext();
        Path uri = requestContext.getResourceURI();

        Locale requestLocale = RequestContextUtils.getLocale(request);
        List<FormElement> elements = new ArrayList<>();
        
        PropertyAspectResolver resolver = new PropertyAspectResolver(
                this.propertyDefinition, this.fieldConfig, this.token);
        Json.MapContainer combined = uri == Path.ROOT ? null 
                : resolver.resolve(uri.getParent(), this.toplevelField);

        for (PropertyAspectField field: this.fieldConfig.getFields()) {
            String input = request.getParameter(field.getIdentifier());
            FormElement element = new FormElement(field, requestLocale);
            if (input != null && "".equals(input.trim())) {
                input = null;
            }
            element.setValue(input);
            if (field.isInherited()) {
                if (combined != null && combined.get(field.getIdentifier()) != null) {
                    element.setInheritedValue(combined.get(field.getIdentifier()));
                }
            }
            elements.add(element);
        }
        ((Form) object).setElements(elements);
    }

    @Override
    protected ModelAndView onSubmit(HttpServletRequest request, HttpServletResponse response,
                                    Form form, BindException errors) throws Exception {    

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        Resource resource = repository.retrieve(token, uri, false);

        if (form.getCancelAction() != null) {
            return new ModelAndView(getSuccessView());
        }
        
        Json.MapContainer toplevel = new Json.MapContainer();
        for (FormElement element: form.getElements()) {
            Object key = element.getIdentifier();
            Object value = element.getValue();
            if (value == null || "".equals(value.toString().trim())) {
                toplevel.remove(key);
            } else {
                toplevel.put(element.getIdentifier().toString(), element.getValue().toString());
            }
        }
        
        Property property = resource.getProperty(this.propertyDefinition);
        Json.MapContainer propertyValue = null;
        if (property == null) {
            property = this.propertyDefinition.createProperty();
            resource.addProperty(property);
            propertyValue = new Json.MapContainer();
        } else {
            propertyValue = property.getJSONValue();
            if (propertyValue == null) {
                propertyValue = new Json.MapContainer();
            }
        }
        propertyValue.put(this.toplevelField, toplevel);
        property.setJSONValue(propertyValue);
        
        repository.store(token, resource);

        Map<String, Object> model = new HashMap<String, Object>();
        model.put("form", form);
        return new ModelAndView(getFormView(), model);
    }
    
    
    @Required
    public void setPropertyDefinition(PropertyTypeDefinition propertyDefinition) {
        this.propertyDefinition = propertyDefinition;
    }

    @Required
    public void setToplevelField(String toplevelField) {
        this.toplevelField = toplevelField;
    }
    
    @Required
    public void setFieldConfig(PropertyAspectDescription fieldConfig) {
        this.fieldConfig = fieldConfig;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
