/* Copyright (c) 2013 University of Oslo, Norway
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
package vtk.web.actions.publish;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.RequestContext;
import vtk.web.SimpleFormController;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class AdvancedPublishDialogController extends SimpleFormController<EditPublishingCommand> {

    private PropertyTypeDefinition publishDatePropDef;
    private PropertyTypeDefinition unpublishDatePropDef;

    @Override
    protected EditPublishingCommand formBackingObject(HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        Resource resource = repository.retrieve(token, uri, false);

        Service service = requestContext.getService();
        URL submitURL = service.urlConstructor(URL.create(request))
                .withResource(resource)
                .withPrincipal(requestContext.getPrincipal())
                .constructURL();
        
        EditPublishingCommand command = new EditPublishingCommand(submitURL.toString());
        command.setResource(resource);
        return command;
    }

    @Override
    protected Map<String, Object> referenceData(HttpServletRequest request,
            EditPublishingCommand command, Errors errors) throws Exception {

        Map<String, Object> model = new HashMap<>();
        model.put("formName", this.getCommandName());
        return model;
    }

    @Override
    protected ModelAndView onSubmit(HttpServletRequest request,
            HttpServletResponse response, EditPublishingCommand command,
            BindException errors) throws Exception {
        RequestContext requestContext = RequestContext
                .getRequestContext(request);
        Map<String, Object> model = new HashMap<>();
        model.put("command", command);

        if (command.getUpdateAction() != null) {

            Date publishDate = null;
            if (command.getPublishDateValue() != null) {
                publishDate = command.getPublishDateValue().getDateValue();
                setPropertyDateValue(publishDatePropDef, publishDate, requestContext);
            }
            else {
                removePropertyValue(publishDatePropDef, requestContext);
            }

            Date unpublishDate = null;
            if (command.getUnpublishDateValue() != null) {
                unpublishDate = command.getUnpublishDateValue().getDateValue();
                if (publishDate != null && unpublishDate.after(publishDate)) {
                    setPropertyDateValue(unpublishDatePropDef, unpublishDate, requestContext);
                }
            } else {
                removePropertyValue(unpublishDatePropDef, requestContext);
            }

        }
        return new ModelAndView(getSuccessView(), model);
    }

    private void removePropertyValue(PropertyTypeDefinition propDef, RequestContext requestContext) throws Exception {
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        Resource resource = repository.retrieve(token, uri, true);
        if (resource.getProperty(propDef) != null) {
            resource.removeProperty(propDef);
            repository.store(token, resource);
        }
    }

    private void setPropertyDateValue(PropertyTypeDefinition datePropDef, Date date, 
            RequestContext requestContext) throws Exception {
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        Resource resource = repository.retrieve(token, uri, true);
        Property dateProp = resource.getProperty(datePropDef);
        if (dateProp == null) {
            dateProp = datePropDef.createProperty();
            resource.addProperty(dateProp);
        }
        dateProp.setDateValue(date);
        repository.store(token, resource);
    }

    public void setPublishDatePropDef(PropertyTypeDefinition publishDatePropDef) {
        this.publishDatePropDef = publishDatePropDef;
    }

    public void setUnpublishDatePropDef(PropertyTypeDefinition unpublishDatePropDef) {
        this.unpublishDatePropDef = unpublishDatePropDef;
    }

}
