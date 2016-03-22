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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ModelAndView;

import vtk.repository.IllegalOperationException;
import vtk.repository.InheritablePropertiesStoreContext;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.RepositoryAction;
import vtk.repository.Resource;
import vtk.repository.TypeInfo;
import vtk.repository.Vocabulary;
import vtk.repository.resourcetype.ConstraintViolationException;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.resourcetype.ValueFactory;
import vtk.repository.resourcetype.ValueFactoryImpl;
import vtk.repository.resourcetype.ValueFormatException;
import vtk.security.Principal;
import vtk.security.PrincipalManager;
import vtk.util.repository.DocumentPrincipalMetadataRetriever;
import vtk.web.RequestContext;
import vtk.web.SimpleFormController;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.referencedata.ReferenceDataProviding;
import vtk.web.service.Service;
import vtk.web.service.ServiceUnlinkableException;

/**
 * A {@link Property property} edit controller. This class is both a form
 * controller and {@link ReferenceDataProvider}, allowing it to both display and
 * edit a list of properties based on their {@link PropertyTypeDefinition
 * definitions}.
 * 
 * <p>
 * When invoked as a reference data provider, the list of property definitions
 * is traversed, and for each property found on the current resource, a
 * {@link PropertyItem} is placed in the model, containing the property itself,
 * along with a URL to edit the property. The property items are placed in the
 * model both as a list and a map, using configurable (sub)model names.
 * 
 * <p>
 * When acting as a form controller, the formObject must be a
 * {@link PropertyEditCommand}. Only one property may be edited at a time, and
 * the property must be "focused" (using the editURL from the PropertyItem)
 * before any values are submitted.
 * 
 * <p>
 * Configurable JavaBean properties:
 * <ul>
 * <li><code>repository</code> - the content {@link Repository repository}
 * <li><code>propertyTypeDefinitions</code> - the list of
 * {@link PropertyTypeDefinition} objects to display and/or edit
 * <li><code>valueFactory</code> - a {@link ValueFactoryImpl} for creating
 * property values.
 * <li><code>dateFormat</code> - a date format (string) used to parse date
 * values
 * <li><code>propertyListModelName</code> - the name to use (in the model) for
 * the property list
 * <li><code>propertyMapModelName</code> - the name to use (in the model) for
 * the property map
 * </ul>
 * 
 */
public class PropertyEditController extends SimpleFormController<PropertyEditCommand> 
    implements ReferenceDataProvider, ReferenceDataProviding {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private String toggleRequestParameter = "toggle";

    private PropertyTypeDefinition[] propertyTypeDefinitions;

    private ValueFactory valueFactory;

    private String dateFormat;

    private String propertyListModelName;
    private String propertyMapModelName;

    private PrincipalManager principalManager;
    private LocaleResolver localeResolver;
    private DocumentPrincipalMetadataRetriever documentPrincipalMetadataRetriever;

    @Override
    protected PropertyEditCommand formBackingObject(HttpServletRequest request) throws Exception {

        String inputNamespace = request.getParameter("namespace");
        String inputName = request.getParameter("name");
        PropertyTypeDefinition definition = null;

        for (PropertyTypeDefinition def: propertyTypeDefinitions) {
            if (isFocusedProperty(def, inputNamespace, inputName)) {
                definition = def;
                break;
            }
        }
        return buildPropertyEditCommand(definition);
    }

    private PropertyEditCommand buildPropertyEditCommand(PropertyTypeDefinition definition) throws Exception {
        if (definition == null) {
            return new PropertyEditCommand(null, null, null, null, null);
        }

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        Resource resource = repository.retrieve(requestContext.getSecurityToken(), requestContext.getResourceURI(),
                false);
        String value = null;

        Property property = resource.getProperty(definition);
        if (property != null) {
            PropertyType.Type t = property.getDefinition().getType();
            if (t == PropertyType.Type.DATE || t == PropertyType.Type.TIMESTAMP) {
                value = property.getFormattedValue(this.dateFormat, null);
            }
            else {
                value = property.getFormattedValue(null, null);
            }
        }

        Map<String, String> urlParameters = new HashMap<>();
        String namespaceURI = definition.getNamespace().getUri();
        if (namespaceURI != null)
            urlParameters.put("namespace", namespaceURI);
        urlParameters.put("name", definition.getName());

        List<String> formAllowedValues = null;
        String hierarchicalHelpUrl = null;

        Vocabulary<Value> vocabulary = definition.getVocabulary();
        if (vocabulary != null) {
            Value[] definitionAllowedValues = vocabulary.getAllowedValues();
            formAllowedValues = new ArrayList<>();
            if (!definition.isMandatory()) {
                formAllowedValues.add("");
            }
            for (Value v : definitionAllowedValues) {
                formAllowedValues.add(v.toString());
            }
        }

        Service service = requestContext.getService();
        String editURL = service.constructLink(resource, requestContext.getPrincipal(), urlParameters);

        return new PropertyEditCommand(editURL, definition, value, formAllowedValues, hierarchicalHelpUrl);
    }

    @Override
    protected void onBindAndValidate(HttpServletRequest request, PropertyEditCommand command, 
            BindException errors) throws Exception {
        if (command.getCancelAction() != null) {
            return;
        }

        PropertyTypeDefinition def = command.getDefinition();
        // Special handling of 'take ownership' action:
        if (Namespace.DEFAULT_NAMESPACE.equals(def.getNamespace())
                && PropertyType.OWNER_PROP_NAME.equals(def.getName())
                && "true".equals(request.getParameter(this.toggleRequestParameter))) {
            return;
        }

        if (isToggleProperty(command.getDefinition())) {
            return;
        }

        String formValue = command.getValue();
        if ("".equals(formValue) || formValue == null) {
            if (command.getDefinition().isMandatory()) {
                errors.rejectValue("value", "mandatory property"); // XXX
            }
            return;
        }

        try {

            if (command.getDefinition().isMultiple()) {
                String[] splitValues = formValue.split(",");
                Value[] values = this.valueFactory.createValues(splitValues, command.getDefinition().getType());

                if (command.getDefinition().getType() == PropertyType.Type.PRINCIPAL) {
                    for (Value v : values) {
                        if (!this.principalManager.validatePrincipal(v.getPrincipalValue())) {
                            throw new ValueFormatException("Invalid principal " + v);
                        }
                    }
                }

            }
            else {

                Value value = this.valueFactory.createValue(formValue, command.getDefinition().getType());
                if (command.getDefinition().getType() == PropertyType.Type.PRINCIPAL) {
                    if (!this.principalManager.validatePrincipal(value.getPrincipalValue())) {
                        throw new ValueFormatException("Invalid principal " + value);
                    }
                }

                // Constraint constraint =
                // command.getDefinition().getConstraint();
                // if (constraint != null) {
                // constraint.validate(value);
                // }

                Vocabulary<Value> vocabulary = command.getDefinition().getVocabulary();

                if (vocabulary == null) {
                    return;
                }

                Value[] allowedValues = vocabulary.getAllowedValues();

                if (allowedValues == null) {
                    return;
                }

                for (Value v : allowedValues) {
                    if (value.equals(v)) {
                        return;
                    }
                }
                errors.rejectValue("value", "Illegal value");

            }
        }
        catch (ValueFormatException e) {
            errors.rejectValue("value", "Illegal value: " + e.getMessage()); // XXX
        }
        catch (ConstraintViolationException e) {
            errors.rejectValue("value", "Illegal value: " + e.getMessage()); // XXX
        }
    }

    @Override
    protected ModelAndView onSubmit(HttpServletRequest request, HttpServletResponse response, 
            PropertyEditCommand command, BindException errors) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();

        String token = requestContext.getSecurityToken();


        if (command.getCancelAction() != null) {
            command.clear();
            command.setDone(true);
            return new ModelAndView(getSuccessView());
        }
        Path uri = requestContext.getResourceURI();
        Resource resource = repository.retrieve(token, uri, false);
        TypeInfo typeInfo = repository.getTypeInfo(resource);
        for (PropertyTypeDefinition def : this.propertyTypeDefinitions) {

            if (isFocusedProperty(def, command.getNamespace(), command.getName())) {
                Property property = resource.getProperty(def);

                String stringValue = command.getValue();

                boolean removed = false, created = false, modified = false;

                if (Namespace.DEFAULT_NAMESPACE.equals(def.getNamespace())
                        && PropertyType.OWNER_PROP_NAME.equals(def.getName())
                        && !resource.getOwner().equals(requestContext.getPrincipal())
                        && "true".equals(request.getParameter(this.toggleRequestParameter))) {

                    // Using toggle submit parameter to take ownership:
                    stringValue = requestContext.getPrincipal().getQualifiedName();
                }
                else if (isToggleProperty(def) && "true".equals(request.getParameter(this.toggleRequestParameter))) {

                    Value toggleValue = getToggleValue(def, property);
                    if (toggleValue == null) {
                        stringValue = "";
                    }
                    else {
                        stringValue = getValueAsString(toggleValue);
                    }
                }

                try {
                    if ("".equals(stringValue)) {
                        if (property == null) {
                            command.setDone(true);
                            command.clear();
                            return new ModelAndView(getSuccessView());
                        }
                        resource.removeProperty(def.getNamespace(), def.getName());
                        removed = true;

                    }
                    else {
                        if (property == null) {
                            if (this.logger.isDebugEnabled()) {
                                this.logger.debug("Property does not exist on resource " + resource
                                        + ", creating from definition: " + def);
                            }
                            property = typeInfo.createProperty(def.getNamespace(), def.getName());
                            resource.addProperty(property);
                            created = true;
                        }

                        if (def.isMultiple()) {
                            String[] splitValues = stringValue.trim().split(" *, *");
                            Value[] values = this.valueFactory.createValues(splitValues, def.getType());
                            property.setValues(values);
                            modified = true;
                        }
                        else {
                            Value value = this.valueFactory.createValue(stringValue, def.getType());
                            property.setValue(value);
                            modified = true;
                        }
                        if (this.logger.isDebugEnabled()) {
                            String debugOutput = def.isMultiple() ? java.util.Arrays.asList(property.getValues())
                                    .toString() : property.getValue().toString();
                            this.logger.debug("Setting property '" + property + "'for resource " + resource
                                    + " to value " + debugOutput);
                        }
                    }

                    if (def.isInheritable() && (created || removed || modified)) {
                        InheritablePropertiesStoreContext sc = new InheritablePropertiesStoreContext();
                        sc.addAffectedProperty(def);
                        repository.store(token, resource, sc);
                    }
                    else {
                        repository.store(token, resource);
                    }

                }
                catch (ConstraintViolationException e) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Error storing resource " + resource + ": constraint violation", e);
                    }
                    errors.rejectValue("value", "Illegal value: " + e.getMessage());
                    return showForm(request, errors, getFormView(), null);
                }
                break;
            }
        }

        command.clear();
        command.setDone(true);
        return new ModelAndView(getSuccessView());
    }

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        Service service = requestContext.getService();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();

        Resource resource = repository.retrieve(token, uri, false);
        TypeInfo typeInfo = repository.getTypeInfo(resource);
        List<PropertyItem> propsList = new ArrayList<>();
        Map<String, PropertyItem> propsMap = new HashMap<>();

        Principal modifiedBy = resource.getModifiedBy();
        Principal createdBy = resource.getCreatedBy();
        Principal owner = resource.getOwner();

        if (this.documentPrincipalMetadataRetriever != null
                && this.documentPrincipalMetadataRetriever.isDocumentSearchConfigured()) {

            Locale preferredLocale = null;
            if (this.localeResolver != null) {
                preferredLocale = this.localeResolver.resolveLocale(request);
            }

            Set<String> uids = new HashSet<>(Arrays.asList(modifiedBy.getName(), createdBy.getName(),
                    owner.getName()));
            Set<Principal> principalDocuments = this.documentPrincipalMetadataRetriever.getPrincipalDocumentsByUid(
                    uids, preferredLocale);

            if (principalDocuments != null) {
                for (Principal pd : principalDocuments) {
                    if (modifiedBy.equals(pd)) {
                        modifiedBy = pd;
                    }
                    if (createdBy.equals(pd)) {
                        createdBy = pd;
                    }
                    if (owner.equals(pd)) {
                        owner = pd;
                    }
                }
            }

        }

        model.put("modifiedBy", modifiedBy);
        model.put("createdBy", createdBy);
        model.put("owner", owner);

        for (PropertyTypeDefinition def : this.propertyTypeDefinitions) {

            if (!typeInfo.getResourceType().hasPropertyDefinition(def)) {
                if (this.logger.isDebugEnabled()) {
                    this.logger.debug("Property type definition " + def + " not applicable for resource " + resource
                            + ", skipping");
                }
                continue;
            }
            Property property = resource.getProperty(def);
            String editURL = null;
            String format = null;
            String toggleURL = null;
            String toggleValue = null;
            
            RepositoryAction protectionLevel = def.getProtectionLevel();
            if (protectionLevel == RepositoryAction.READ_WRITE && !resource.hasPublishDate()) {
                // Authorize for READ_WRITE_UNPUBLISHED instead:
                protectionLevel = RepositoryAction.READ_WRITE_UNPUBLISHED;
            }
            if (repository.isAuthorized(resource, protectionLevel, requestContext.getPrincipal(), true)) {

                Map<String, String> urlParameters = new HashMap<>();
                String namespaceURI = def.getNamespace().getUri();
                if (namespaceURI != null) {
                    urlParameters.put("namespace", namespaceURI);
                }
                urlParameters.put("name", def.getName());
                if (def.getType() == PropertyType.Type.TIMESTAMP || def.getType() == PropertyType.Type.DATE) {
                    format = this.dateFormat;
                }

                try {
                    editURL = service.constructLink(resource, requestContext.getPrincipal(), urlParameters);
                } catch (ServiceUnlinkableException e) {
                    // Assertion doesn't match, OK in this case
                }

                if (Namespace.DEFAULT_NAMESPACE.equals(def.getNamespace())
                        && PropertyType.OWNER_PROP_NAME.equals(def.getName())
                        && !resource.getOwner().equals(requestContext.getPrincipal())) {

                    // Using toggle parameter to take ownership:
                    urlParameters.put(this.toggleRequestParameter, "true");
                    toggleURL = service.constructLink(resource, requestContext.getPrincipal(), urlParameters);

                } else if (isToggleProperty(def)) {
                    Value toggleValueObject = getToggleValue(def, property);
                    if (toggleValueObject != null) {
                        toggleValue = getValueAsString(toggleValueObject);
                    }
                    urlParameters.put(this.toggleRequestParameter, "true");
                    toggleURL = service.constructLink(resource, requestContext.getPrincipal(), urlParameters);
                }
            }

            PropertyItem item = new PropertyItem(property, def, editURL, format, toggleURL, toggleValue);
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

    private boolean isToggleProperty(PropertyTypeDefinition def) {
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

        switch (value.getType()) {

        case DATE:
        case TIMESTAMP:
            SimpleDateFormat format = new SimpleDateFormat(this.dateFormat);
            Date date = value.getDateValue();
            stringValue = format.format(date);
            break;

        default:
            stringValue = value.toString();

        }
        return stringValue;
    }

    private boolean isFocusedProperty(PropertyTypeDefinition propDef, String inputNamespace, String inputName) {

        if (inputNamespace != null)
            inputNamespace = inputNamespace.trim();
        if (inputName != null)
            inputName = inputName.trim();

        if (inputName == null || "".equals(inputName)) {
            return false;
        }

        if (!inputName.equals(propDef.getName())) {
            return false;
        }

        // We now know it is the same name, check namespace:

        if (propDef.getNamespace().getUri() == null && (inputNamespace == null || "".equals(inputNamespace))) {
            return true;
        }

        return propDef.getNamespace().getUri().equals(inputNamespace);
    }

    @Required
    public void setValueFactory(ValueFactory valueFactory) {
        this.valueFactory = valueFactory;
    }

    @Required
    public void setPrincipalManager(PrincipalManager principalManager) {
        this.principalManager = principalManager;
    }

    public void setLocaleResolver(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @Required
    public void setPropertyListModelName(String propertyListModelName) {
        this.propertyListModelName = propertyListModelName;
    }

    @Required
    public void setPropertyMapModelName(String propertyMapModelName) {
        this.propertyMapModelName = propertyMapModelName;
    }

    @Required
    public void setPropertyTypeDefinitions(PropertyTypeDefinition[] propertyTypeDefinitions) {
        this.propertyTypeDefinitions = propertyTypeDefinitions;
    }

    @Required
    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public void setDocumentPrincipalMetadataRetriever(
            DocumentPrincipalMetadataRetriever documentPrincipalMetadataRetriever) {
        this.documentPrincipalMetadataRetriever = documentPrincipalMetadataRetriever;
    }

    public ReferenceDataProvider[] getReferenceDataProviders() {
        return new ReferenceDataProvider[] { this };
    }
}
