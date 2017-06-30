/* Copyright (c) 2007, 2012 University of Oslo, Norway
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

package vtk.edit.editor;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import vtk.repository.ContentInputSources;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceWrapper;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.actions.SaveImageHelper;
import vtk.web.actions.copymove.CopyHelper;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class ImageEditController extends ResourceEditController {

    private CopyHelper copyHelper;
    protected Service editService;
    
    private Service loadImageService;
    private SaveImageHelper saveImageHelper;
    private PropertyTypeDefinition heightPropDef;
    private PropertyTypeDefinition widthPropDef;
    
    
    @Override
    protected ServletRequestDataBinder createBinder(HttpServletRequest request, ResourceEditWrapper wrapper) throws Exception {
        ServletRequestDataBinder binder = new ImageEditDataBinder(wrapper, getCommandName(),
                resourceManager.getHtmlParser(), resourceManager.getHtmlPropsFilter(), propertyEditPreprocessors);
        return binder;
    }
    

    @Override
    protected ModelAndView onSubmit(HttpServletRequest request,
            HttpServletResponse response, ResourceEditWrapper wrapperObj,
            BindException errors) throws Exception {
        ImageResourceEditWrapper wrapper = (ImageResourceEditWrapper) wrapperObj;
        
        Resource resource = wrapper.getResource();
        RequestContext requestContext = RequestContext.getRequestContext();
        Principal principal = requestContext.getPrincipal();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();

        if (wrapper.hasErrors()) {
            Map<String, Object> model = getModelProperties(wrapper, resource, principal, repository);
            return new ModelAndView(getFormView(), model);
        }
        
        if (!wrapper.isSave() && !wrapper.isSaveCopy() && !wrapper.isView()) {
            this.resourceManager.unlock();
            return new ModelAndView(getSuccessView(), new HashMap<String, Object>());
        }
        
        Property imageHeightProp = heightPropDef.createProperty();
        imageHeightProp.setIntValue(wrapper.getNewHeight());
        resource.addProperty(imageHeightProp);

        Property imageWidthProp = widthPropDef.createProperty();
        imageWidthProp.setIntValue(wrapper.getNewWidth());
        resource.addProperty(imageWidthProp);
        
        InputStream is = saveImageHelper.getEditedImageInputStream(resource, repository, token, resource.getURI(),
                wrapper.getCropX(), wrapper.getCropY(), wrapper.getCropWidth(), wrapper.getCropHeight(),
                wrapper.getNewWidth(), wrapper.getNewHeight());

        if (wrapper.isSaveCopy()) {
            Path destUri = copyHelper.copyResource(resource.getURI(), resource.getURI(), repository, token, resource, is);
            this.resourceManager.unlock();
            URL url = editService.urlConstructor(URL.create(request))
                    .withURI(destUri)
                    .constructURL();
            return new ModelAndView(new RedirectView(url.toString()));
        }

        repository.store(token, resource);
        if (is != null) {
            repository.storeContent(token, wrapper.getURI(), ContentInputSources.fromStream(is));
        }

        if (!wrapper.isView()) {
            Map<String, Object> model = getModelProperties(wrapper, resource, principal, repository);
            wrapper.setSave(false);
            model = addImageEditorServices(model, resource, principal);
            return new ModelAndView(getFormView(), model);
        }

        this.resourceManager.unlock();
        return new ModelAndView(getSuccessView());
    }

    @Override
    protected Map<String, Object> referenceData(HttpServletRequest request, ResourceEditWrapper command, Errors errors) throws Exception {
        Resource resource = ((ResourceWrapper) command).getResource();
        RequestContext requestContext = RequestContext.getRequestContext();
        Principal principal = requestContext.getPrincipal();

        Map<String, Object> model = super.referenceData(request, command, errors);

        if (model == null) {
            model = new HashMap<>();
        }

        model = addImageEditorServices(model, resource, principal);

        return model;
    }

    private Map<String, Object> addImageEditorServices(Map<String, Object> model, Resource resource, Principal principal) {
        if (this.loadImageService != null) {
            
            RequestContext requestContext = RequestContext.getRequestContext();
            URL imageSourceURL = loadImageService.urlConstructor(requestContext.getRequestURL())
                    .withResource(resource)
                    .withPrincipal(principal)
                    .constructURL();
            if(requestContext.getServletRequest().isSecure() && imageSourceURL.getProtocol().equals("http")) {
                imageSourceURL.setProtocol("https");
            }
            model.put("imageURL", imageSourceURL);
        }
        return model;
    }

    public void setLoadImageService(Service loadImageService) {
        this.loadImageService = loadImageService;
    }

    @Required
    public void setSaveImageHelper(SaveImageHelper saveImageHelper) {
        this.saveImageHelper = saveImageHelper;
    }

    public void setHeightPropDef(PropertyTypeDefinition hightPropDef) {
        this.heightPropDef = hightPropDef;
    }

    public void setWidthPropDef(PropertyTypeDefinition widthPropDef) {
        this.widthPropDef = widthPropDef;
    }
    
    @Required
    public void setCopyHelper(CopyHelper copyHelper) {
        this.copyHelper = copyHelper;
    }
    
    @Required
    public void setEditService(Service editService) {
        this.editService = editService;
    }

}
