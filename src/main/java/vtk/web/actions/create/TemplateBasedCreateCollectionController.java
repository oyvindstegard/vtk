/* Copyright (c) 2004, 2008 University of Oslo, Norway
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
package vtk.web.actions.create;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import vtk.repository.InheritablePropertiesStoreContext;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.TypeInfo;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.web.RequestContext;
import vtk.web.SimpleFormController;
import vtk.web.actions.ActionsHelper;
import vtk.web.service.Service;
import vtk.web.templates.ResourceTemplate;
import vtk.web.templates.ResourceTemplateManager;
import vtk.web.view.freemarker.MessageLocalizer;

public class TemplateBasedCreateCollectionController 
    extends SimpleFormController<CreateCollectionCommand> {

    private static final String NORMAL_FOLDER_IDENTIFIER = "NORMAL_FOLDER";

    private ResourceTemplateManager templateManager;
    private PropertyTypeDefinition userTitlePropDef;
    private boolean downcaseCollectionNames = false;
    private Map<String, String> replaceNameChars;
    private String cancelView;
    private PropertyTypeDefinition descriptionPropDef;
    private PropertyTypeDefinition publishDatePropDef;
    private PropertyTypeDefinition unpublishedCollectionPropDef;
    private Map<PropertyTypeDefinition, Value> normalFolderProperties; 

    @Override
    protected CreateCollectionCommand formBackingObject(HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Service service = requestContext.getService();
        Repository repository = requestContext.getRepository();
        Resource resource = repository.retrieve(requestContext.getSecurityToken(), requestContext.getResourceURI(),
                false);
        String url = service.constructLink(resource, requestContext.getPrincipal());

        CreateCollectionCommand command = new CreateCollectionCommand(url);
        
        // Set normal folder template as the selected
        command.setSourceURI(NORMAL_FOLDER_IDENTIFIER);

        return command;
    }

    
    
    @Override
    protected Map<String, Object> referenceData(HttpServletRequest request,
            CreateCollectionCommand command, Errors errors) throws Exception {
        
        RequestContext requestContext = RequestContext.getRequestContext();
        Map<String, Object> model = new HashMap<String, Object>();

        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();

        List<ResourceTemplate> templates = templateManager.getFolderTemplates(token, uri);

        HttpServletRequest servletRequest = requestContext.getServletRequest();
        org.springframework.web.servlet.support.RequestContext springRequestContext = new org.springframework.web.servlet.support.RequestContext(
                servletRequest);
        Map<String, String> tmp = new LinkedHashMap<String, String>();

        String standardCollectionName = new MessageLocalizer("property.standardCollectionName", "Standard collection",
                null, springRequestContext).get(null).toString();

        // List normal folder first
        tmp.put(NORMAL_FOLDER_IDENTIFIER, standardCollectionName);

        for (ResourceTemplate t : templates) {
            tmp.put(t.getUri().toString(), t.getTitle());
        }

        model.put("templates", tmp);
        return model;
    }

    
    
    @Override
    protected ModelAndView onSubmit(HttpServletRequest request,
            HttpServletResponse response, CreateCollectionCommand command,
            BindException errors) throws Exception {

        Map<String, Object> model = new HashMap<String, Object>();

        if (command.getCancelAction() != null) {
            command.setDone(true);
            return new ModelAndView(cancelView);
        }
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();

        // The location of the folder to copy
        String source = command.getSourceURI();
        if (source == null || source.equals(NORMAL_FOLDER_IDENTIFIER)) {
            // Just create a new folder if no "folder-template" is selected
            model.put("resource", createNewFolder(command, uri, requestContext));
            command.setDone(true);
            return new ModelAndView(getSuccessView(), model);
        }
        Path sourceURI = Path.fromString(source);

        String title = command.getTitle();
        String name = fixCollectionName(command.getName());

        // Setting the destination to the current folder/uri
        Path destinationURI = uri.extend(name);

        // Copy folder-template to destination (implicit rename)
        repository.copy(token, sourceURI, destinationURI, false, true);
        Resource dest = repository.retrieve(token, destinationURI, false);

        dest.removeProperty(userTitlePropDef);

        InheritablePropertiesStoreContext sc = null;
        Property titleProp = null, dp;
        if ((dp = dest.getProperty(descriptionPropDef)) != null) {
            String desc = dp.getStringValue();
            if (desc.contains("|")) {
                String[] split = desc.split("\\|");
                if (split.length == 2) {
                    TypeInfo ti = repository.getTypeInfo(dest);
                    Namespace ns = ti.getNamespaceByPrefix(split[0]);
                    if (ns != null) {
                        titleProp = ti.createProperty(ns, split[1]);
                    }
                    if (titleProp != null && titleProp.getDefinition().isInheritable()) {
                        sc = new InheritablePropertiesStoreContext();
                        sc.addAffectedProperty(titleProp.getDefinition());
                    }
                }
            }
        }

        dest.removeProperty(descriptionPropDef);

        if (titleProp == null)
            titleProp = userTitlePropDef.createProperty();

        if (title == null || "".equals(title))
            title = name.substring(0, 1).toUpperCase() + name.substring(1);

        titleProp.setStringValue(title);
        dest.addProperty(titleProp);

        model.put("resource", repository.store(token, dest, sc));

        if (!command.getPublish()) {
            ActionsHelper.unpublishResource(publishDatePropDef, unpublishedCollectionPropDef, repository, token,
                    destinationURI, null);
        }

        command.setDone(true);

        return new ModelAndView(getSuccessView(), model);
    }

    private Resource createNewFolder(CreateCollectionCommand command, Path uri, RequestContext requestContext) throws Exception {
        CreateCollectionCommand createCollectionCommand = (CreateCollectionCommand) command;

        String title = createCollectionCommand.getTitle();
        String name = fixCollectionName(createCollectionCommand.getName());
        Path newURI = uri.extend(name);
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Resource collection = repository.createCollection(token, newURI);

        if (title == null || "".equals(title))
            title = name.substring(0, 1).toUpperCase() + name.substring(1);

        Property titleProp = userTitlePropDef.createProperty();
        titleProp.setStringValue(title);
        collection.addProperty(titleProp);

        if (normalFolderProperties != null) {
            for (PropertyTypeDefinition def: normalFolderProperties.keySet()) {
                Property prop = def.createProperty();
                prop.setValue(normalFolderProperties.get(def));
                collection.addProperty(prop);
            }
        }        
        Resource r = repository.store(token, collection);

        if (!createCollectionCommand.getPublish()) {
            ActionsHelper.unpublishResource(publishDatePropDef, 
                    unpublishedCollectionPropDef, repository, token,
                    newURI, null);
        }

        return r;
    }

    @Override
    protected void onBindAndValidate(HttpServletRequest request, 
            CreateCollectionCommand command, BindException errors) throws Exception {

        super.onBindAndValidate(request, command, errors);
        RequestContext requestContext = RequestContext.getRequestContext();

        if (command.getCancelAction() != null) {
            return;
        }
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();

        String name = command.getName();
        if (null == name || "".equals(name.trim())) {
            errors.rejectValue("name", "manage.create.collection.missing.name",
                    "A name must be provided for the collection");
            return;
        }

        name = fixCollectionName(name);

        if (name.contains("/")) {
            errors.rejectValue("name", "manage.create.collection.invalid.name", 
                    "This is an invalid collection name");
            return;
        }

        if (name.isEmpty()) {
            errors.rejectValue("name", "manage.create.collection.invalid.name", 
                    "This is an invalid collection name");
            return;
        }

        Path newURI;
        try {
            newURI = uri.extend(name);
        } catch (Throwable t) {
            errors.rejectValue("name", "manage.create.collection.invalid.name", 
                    "This is an invalid collection name");
            return;
        }

        if (repository.exists(token, newURI)) {
            errors.rejectValue("name", "manage.create.collection.exists", 
                    "A collection with this name already exists");
            return;
        }

        if (newURI.isAncestorOf(uri)) {
            errors.rejectValue("name", "manage.create.collection.invalid.destination",
                    "Cannot copy a collection into itself");
        }
    }

    private String fixCollectionName(String name) {
        if (downcaseCollectionNames) {
            name = name.toLowerCase();
        }
        if (replaceNameChars != null) {
            for (String regex : replaceNameChars.keySet()) {
                String replacement = replaceNameChars.get(regex);
                name = name.replaceAll(regex, replacement);
            }
        }
        return name;
    }

    @Required
    public void setTemplateManager(ResourceTemplateManager templateManager) {
        this.templateManager = templateManager;
    }

    @Required
    public void setUserTitlePropDef(PropertyTypeDefinition userTitlePropDef) {
        this.userTitlePropDef = userTitlePropDef;
    }

    public void setDowncaseCollectionNames(boolean downcaseCollectionNames) {
        this.downcaseCollectionNames = downcaseCollectionNames;
    }

    public void setReplaceNameChars(Map<String, String> replaceNameChars) {
        this.replaceNameChars = replaceNameChars;
    }

    @Required
    public void setCancelView(String cancelView) {
        this.cancelView = cancelView;
    }

    @Required
    public void setDescriptionPropDef(PropertyTypeDefinition descriptionPropDef) {
        this.descriptionPropDef = descriptionPropDef;
    }

    public void setPublishDatePropDef(PropertyTypeDefinition publishDatePropDef) {
        this.publishDatePropDef = publishDatePropDef;
    }

    public void setUnpublishedCollectionPropDef(PropertyTypeDefinition unpublishedCollectionPropDef) {
        this.unpublishedCollectionPropDef = unpublishedCollectionPropDef;
    }

    public void setNormalFolderProperties(Map<PropertyTypeDefinition, Value> normalFolderProperties) {
        this.normalFolderProperties = new HashMap<PropertyTypeDefinition, Value>();
        this.normalFolderProperties.putAll(normalFolderProperties);
    }
}
