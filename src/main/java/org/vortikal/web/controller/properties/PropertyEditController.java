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
package org.vortikal.web.controller.properties;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.SimpleFormController;
import org.vortikal.repository.HierarchicalVocabulary;
import org.vortikal.repository.IllegalOperationException;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Property;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.Vocabulary;
import org.vortikal.repository.resourcetype.ConstraintViolationException;
import org.vortikal.repository.resourcetype.PrimaryResourceTypeDefinition;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.repository.resourcetype.ValueFactory;
import org.vortikal.security.PrincipalManager;
import org.vortikal.security.SecurityContext;
import org.vortikal.web.RequestContext;
import org.vortikal.web.referencedata.ReferenceDataProvider;
import org.vortikal.web.referencedata.ReferenceDataProviding;
import org.vortikal.web.service.Service;
import org.vortikal.web.service.ServiceUnlinkableException;


/**
 * A {@link Property property} edit controller. This class is both a
 * form controller and {@link ReferenceDataProvider}, allowing it to
 * both display and edit a list of properties based on their {@link
 * PropertyTyeDefinition definitions}.
 *
 * <p>When invoked as a reference data provider, the list of property
 * definitions is traversed, and for each property found on the
 * current resource, a {@link PropertyItem} is placed in the model,
 * containing the property itself, along with a URL to edit the
 * property. The property items are placed in the model both as a list
 * and a map, using configurable (sub)model names.
 *
 * <p>When acting as a form controller, the formObject must be a
 * {@link PropertyEditCommand}. Only one property may be edited at a
 * time, and the property must be "focused" (using the editURL from
 * the PropertyItem) before any values are submitted.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>repository</code> - the content {@link Repository repository}
 *   <li><code>principalManager</code> - a valid {@link PrincipalManager}
 *   <li><code>propertyTypeDefinitions</code> - the list of {@link
 *   PropertyTypeDefinition} objects to display and/or edit
 *   <li><code>valueFactory</code> - a {@link ValueFactory} for
 *   creating property values.
 *   <li><code>dateFormat</code> - a date format (string) used to
 *   parse date values
 *   <li><code>propertyListModelName</code> - the name to use (in the
 *   model) for the property list
 *   <li><code>propertyMapModelName</code> - the name to use (in the
 *   model) for the property map
 *   <li><code>editHooks</code> - a list of {@link
 *   PropertyEditHook} objects, allowing hooks to be run when specific
 *   properties are created, removed and edited.
 *   <li><code>vocabularyChooserService</code> - optional url to a service helping to choose a value, to be opened in another window.
 * </ul>
 *
 */
public class PropertyEditController extends SimpleFormController
  implements ReferenceDataProvider, ReferenceDataProviding, InitializingBean {

    private Log logger = LogFactory.getLog(this.getClass());

    private String toggleRequestParameter = "toggle";

    private Repository repository;
    private PrincipalManager principalManager;
    private PropertyTypeDefinition[] propertyTypeDefinitions;
    private PropertyEditHook[] editHooks;
    private ValueFactory valueFactory;
    private String dateFormat;
    
    private String propertyListModelName;
    private String propertyMapModelName;

    private Service vocabularyChooserService;

    public void setPropertyListModelName(String propertyListModelName) {
        this.propertyListModelName = propertyListModelName;
    }
    
    public void setPropertyMapModelName(String propertyMapModelName) {
        this.propertyMapModelName = propertyMapModelName;
    }
    
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public void setPrincipalManager(PrincipalManager principalManager) {
        this.principalManager = principalManager;
    }

    public void setPropertyTypeDefinitions(PropertyTypeDefinition[] propertyTypeDefinitions) {
        this.propertyTypeDefinitions = propertyTypeDefinitions;
    }
    
    public void setEditHooks(PropertyEditHook[] editHooks) {
        this.editHooks = editHooks;
    }
    
    public void setValueFactory(ValueFactory valueFactory) {
        this.valueFactory = valueFactory;
    }
    
    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public ReferenceDataProvider[] getReferenceDataProviders() {
        return new ReferenceDataProvider[] {this};
    }
    

    public void afterPropertiesSet() {
        if (this.propertyListModelName == null) {
            throw new BeanInitializationException(
            "JavaBean property 'propertyListModelName' not set");
        }
        
        if (this.propertyMapModelName == null) {
            throw new BeanInitializationException(
            "JavaBean property 'propertyMapModelName' not set");
        }
        
        if (this.repository == null) {
            throw new BeanInitializationException(
                "JavaBean property 'repository' not set");
        }
        if (this.propertyTypeDefinitions == null) {
            throw new BeanInitializationException(
                "JavaBean property 'propertyTypeDefinitions' not set");
        }
        if (this.dateFormat == null) {
            throw new BeanInitializationException(
                "JavaBean property 'dateFormat' not set");
        }
        if (this.principalManager == null) {
            throw new BeanInitializationException(
                "JavaBean property 'principalManager' not set");
        }
        setValidator(new PropertyEditValidator(this.valueFactory,
                                               this.principalManager));
    }
    


    protected Object formBackingObject(HttpServletRequest request)
        throws Exception {

        String inputNamespace = request.getParameter("namespace");
        String inputName = request.getParameter("name");
        PropertyTypeDefinition definition = null;

        for (PropertyTypeDefinition propertyTypeDefinition: this.propertyTypeDefinitions) {
            if (isFocusedProperty(propertyTypeDefinition, inputNamespace, inputName)) {
                definition  = propertyTypeDefinition;
                break;
            }
        }

        if (definition == null) {
            return new PropertyEditCommand(null, null, null, null, null);
        }

        RequestContext requestContext = RequestContext.getRequestContext();
        SecurityContext securityContext = SecurityContext.getSecurityContext();
        Resource resource = this.repository.retrieve(securityContext.getToken(),
                                                     requestContext.getResourceURI(), false);
        String value = null;

        Property property = resource.getProperty(definition);
        if (property != null) {
            if (definition.isMultiple()) {
                StringBuffer val = new StringBuffer();
                Value[] values = property.getValues();
                for (int i = 0; i < values.length; i++) {
                    val.append(values[i].toString());
                    if (i < values.length - 1)
                        val.append(", ");
                }
                value = val.toString();
            } else {
                value = getValueAsString(property.getValue());
            }
        }

        Map<String, String> urlParameters = new HashMap<String, String>();
        String namespaceURI = definition.getNamespace().getUri();
        if (namespaceURI != null)
            urlParameters.put("namespace", namespaceURI);
        urlParameters.put("name", definition.getName());

        List<String> formAllowedValues = null;
        String hierarchicalHelpUrl = null;

        Vocabulary<Value> vocabulary = definition.getVocabulary();
        if (vocabulary != null) {
            if ((vocabulary instanceof HierarchicalVocabulary) && this.vocabularyChooserService != null) {
                hierarchicalHelpUrl = this.vocabularyChooserService.constructLink(resource, securityContext.getPrincipal(), urlParameters);
            } else {
                Value[] definitionAllowedValues = vocabulary.getAllowedValues();
                formAllowedValues = new ArrayList<String>();
                
                if (!definition.isMandatory()) {
                    formAllowedValues.add("");
                }
                
                for (Value v : definitionAllowedValues) {
                    formAllowedValues.add(v.toString());
                }
            }


        }
        
        Service service = requestContext.getService();
        String editURL = service.constructLink(resource, 
                securityContext.getPrincipal(), urlParameters);

        return new PropertyEditCommand(editURL, definition, value, formAllowedValues, hierarchicalHelpUrl);
    }



    protected boolean isFormSubmission(HttpServletRequest request) {
        boolean isFormSubmission = super.isFormSubmission(request);
        if ("true".equals(request.getParameter(this.toggleRequestParameter))) {
            isFormSubmission = true;
        }
        return isFormSubmission;
    }
    
 
    protected ModelAndView onSubmit(HttpServletRequest request, HttpServletResponse response,
                                    Object command, BindException errors) throws Exception {    

        RequestContext requestContext = RequestContext.getRequestContext();
        SecurityContext securityContext = SecurityContext.getSecurityContext();
        
        String token = securityContext.getToken();

        PropertyEditCommand propertyCommand =
            (PropertyEditCommand) command;

        if (propertyCommand.getCancelAction() != null) {
            propertyCommand.clear();
            propertyCommand.setDone(true);
            return new ModelAndView(getSuccessView());
        }
        String uri = requestContext.getResourceURI();
        Resource resource = this.repository.retrieve(token, uri, false);
        for (int i = 0; i < this.propertyTypeDefinitions.length; i++) {
            
            PropertyTypeDefinition def = this.propertyTypeDefinitions[i];
            if (isFocusedProperty(def, propertyCommand.getNamespace(),
                                  propertyCommand.getName())) {
                Property property = resource.getProperty(def);

                String stringValue = propertyCommand.getValue();

                boolean removed = false, created = false, modified = false;                

                if (Namespace.DEFAULT_NAMESPACE.equals(def.getNamespace()) &&
                    PropertyType.OWNER_PROP_NAME.equals(def.getName()) &&
                    !resource.getOwner().equals(securityContext.getPrincipal()) &&
                    "true".equals(request.getParameter(this.toggleRequestParameter))) {

                    // Using toggle submit parameter to take ownership:
                    stringValue = securityContext.getPrincipal().getQualifiedName();
                    
                } else if (isToggleableProperty(def)
                    && "true".equals(request.getParameter(this.toggleRequestParameter))) {

                    Value toggleValue = getToggleValue(def, property);
                    if (toggleValue == null) {
                        stringValue = "";
                    } else {
                        stringValue = getValueAsString(toggleValue);
                    }
                }

                try {
                    if ("".equals(stringValue)) {
                        if (property == null) {
                            propertyCommand.setDone(true);
                            propertyCommand.clear();
                            return new ModelAndView(getSuccessView());
                        }
                        resource.removeProperty(def.getNamespace(), def.getName());
                        removed = true;


                    } else {
                        if (property == null) {
                            if (this.logger.isDebugEnabled()) {
                                this.logger.debug("Property does not exist on resource " + resource
                                                  + ", creating from definition: " + def);
                            }
                            property = resource.createProperty(def.getNamespace(), def.getName());
                            created = true;
                        }
                    
                        if (def.isMultiple()) {
                            String[] splitValues = stringValue.trim().split(" *, *");
                            Value[] values = this.valueFactory.createValues(
                                splitValues, def.getType());
                            property.setValues(values);
                            modified = true;
                        } else {
                            Value value = this.valueFactory.createValue(stringValue, def.getType());
                            property.setValue(value);
                            modified = true;
                        }
                        if (this.logger.isDebugEnabled()) {
                            String debugOutput = def.isMultiple()
                                ? java.util.Arrays.asList(property.getValues()).toString()
                                : property.getValue().toString();
                            this.logger.debug("Setting property '" + property + "'for resource "
                                              + resource + " to value " + debugOutput);
                        }
                    }
                    if (this.editHooks != null) {
                        for (int j = 0; j < this.editHooks.length; j++) {
                            PropertyEditHook hook = this.editHooks[j];
                            if (created) hook.created(def, resource);
                            if (removed) hook.removed(def, resource);
                            if (modified) hook.modified(def, resource);
                        }
                    }
                    
                    this.repository.store(token, resource);
                } catch (ConstraintViolationException e) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Error storing resource " + resource
                                     + ": constraint violation", e);
                    }
                    errors.rejectValue("value", "Illegal value: " + e.getMessage());
                    return showForm(request, response, errors);
                }
                break;
            }
        }

        propertyCommand.clear();
        propertyCommand.setDone(true);
        return new ModelAndView(getSuccessView());

    }
    
    private boolean isApplicableProperty(PropertyTypeDefinition def,
                                         PrimaryResourceTypeDefinition resourceType) {

        return resourceType.hasPropertyDefinition(def);
    }
    

    public void referenceData(Map model, HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        SecurityContext securityContext = SecurityContext.getSecurityContext();
        Service service = requestContext.getService();
        Resource resource = this.repository.retrieve(securityContext.getToken(),
                                                     requestContext.getResourceURI(), false);

        List<PropertyItem> propsList = new ArrayList<PropertyItem>();
        Map<String, PropertyItem> propsMap = new HashMap<String, PropertyItem>();

        for (PropertyTypeDefinition def: this.propertyTypeDefinitions) {

            if (!isApplicableProperty(def, resource.getResourceTypeDefinition())) {
                if (this.logger.isDebugEnabled()) {
                    this.logger.debug("Property type definition " + def
                                 + " not applicable for resource " + resource + ", skipping");
                }
                continue;
            }

            Property property = resource.getProperty(def);
            String editURL = null;
            String format = null;
            String toggleURL = null;
            String toggleValue = null;
            
            if (resource.isAuthorized(def.getProtectionLevel(),
                                      securityContext.getPrincipal())) {
                
                Map<String, String> urlParameters = new HashMap<String, String>();
                String namespaceURI = def.getNamespace().getUri();
                if (namespaceURI != null) {
                    urlParameters.put("namespace", namespaceURI);
                }
                urlParameters.put("name", def.getName());
                if (def.getType() == PropertyType.TYPE_DATE) {
                    format = this.dateFormat;
                }

                try {
                    editURL = service.constructLink(resource, securityContext.getPrincipal(),
                            urlParameters);
                } catch (ServiceUnlinkableException e) {
                    // Assertion doesn't match, OK in this case
                }

                if (Namespace.DEFAULT_NAMESPACE.equals(def.getNamespace()) &&
                    PropertyType.OWNER_PROP_NAME.equals(def.getName()) &&
                    !resource.getOwner().equals(securityContext.getPrincipal())) {

                    // Using toggle parameter to take ownership:
                    urlParameters.put(this.toggleRequestParameter, "true");
                    toggleURL = service.constructLink(resource,
                                                      securityContext.getPrincipal(),
                                                      urlParameters);

                } else if (isToggleableProperty(def)) {
                    Value toggleValueObject = getToggleValue(def, property);
                    if (toggleValueObject != null) {
                        toggleValue = getValueAsString(toggleValueObject);
                    }
                    urlParameters.put(this.toggleRequestParameter, "true");
                    toggleURL = service.constructLink(resource, 
                                                      securityContext.getPrincipal(),
                                                      urlParameters);
                }
            }

            PropertyItem item = new PropertyItem(property, def, editURL, format,
                                                 toggleURL, toggleValue);
            propsList.add(item);
            if (def.getNamespace() == Namespace.DEFAULT_NAMESPACE) {
                propsMap.put(def.getName(), item);
            } else {
                propsMap.put(def.getNamespace().getPrefix() + ":" + def.getName(), item);
            }
        }

        model.put(this.propertyListModelName, propsList);
        model.put(this.propertyMapModelName, propsMap);
    }
    

    private boolean isToggleableProperty(PropertyTypeDefinition def) {
        Vocabulary<Value> vocabulary = def.getVocabulary();
        
        if (vocabulary == null) {
            return false;
        }
        
        Value[] allowedValues = vocabulary.getAllowedValues();

        if (allowedValues == null) {
            return false;
        }
        
        if (def.isMandatory()) {
            return (allowedValues.length == 2);
        }

        return (allowedValues.length == 1);
    }
    
    private Value getToggleValue(PropertyTypeDefinition def, Property property) {
        
        Value[] allowedValues = def.getVocabulary().getAllowedValues();

        if (!def.isMandatory() && allowedValues != null && allowedValues.length == 1) {
            if (property == null) {
                return allowedValues[0];
            }
            return null;
        }

        if (def.isMandatory() && allowedValues != null && allowedValues.length == 2) {
            if (property.getValue().equals(allowedValues[0])) {
                return allowedValues[0];
            }
            return allowedValues[1];
        }

        throw new IllegalArgumentException("Property " + def + " is not a toggleable property");
    }
    

    private String getValueAsString(Value value) throws IllegalOperationException {
        String stringValue;
        int type = value.getType();
        switch (type) {

        case PropertyType.TYPE_DATE:
            SimpleDateFormat format = new SimpleDateFormat(this.dateFormat);
            Date date = value.getDateValue();
            stringValue = format.format(date);
            break;

        default:
            stringValue = value.toString();
            
        }
        return stringValue;
    }


    private boolean isFocusedProperty(PropertyTypeDefinition propDef,
                                      String inputNamespace, String inputName) {

        if (inputNamespace != null) inputNamespace = inputNamespace.trim();
        if (inputName != null) inputName = inputName.trim();
        
        if (inputName == null || "".equals(inputName)) {
            return false;
        }

        if (!inputName.equals(propDef.getName())) {
            return false;
        }
        
        // We now know it is the same name, check namespace:

        if (propDef.getNamespace().getUri() == null
            && (inputNamespace == null || "".equals(inputNamespace))) {
            return true;
        }

        return propDef.getNamespace().getUri().equals(inputNamespace);
    }

    public void setVocabularyChooserService(Service vocabularyChooserService) {
        this.vocabularyChooserService = vocabularyChooserService;
    }

}
