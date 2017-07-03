/* Copyright (c) 2004-2014, University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import vtk.repository.FileUpload;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.util.ValidationException;
import vtk.web.RequestContext;
import vtk.web.SimpleFormController;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * A controller that uploads resources
 * 
 * 1. If client has JavaScript enabled ("overwrite"-parameter is sent), the user
 * can decide to skip or overwrite existing resources before uploading (VTK-3484).
 * 
 * 2. Otherwise gives error-message and upload nothing if existing resources
 */
public class FileUploadController extends SimpleFormController<FileUploadCommand> {
    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    private FileUpload fileUpload;

    @Override
    protected FileUploadCommand formBackingObject(HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Service service = requestContext.getService();
        Repository repository = requestContext.getRepository();

        Resource resource = repository.retrieve(requestContext.getSecurityToken(), requestContext.getResourceURI(),
                false);

        URL url = service.urlConstructor(URL.create(request))
                .withResource(resource)
                .withPrincipal(requestContext.getPrincipal())
                .constructURL();
        
        FileUploadCommand command = new FileUploadCommand(url.toString());
        return command;
    }

    @Override
    public ModelAndView onSubmit(HttpServletRequest request, HttpServletResponse response,
            FileUploadCommand command, BindException errors) throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        String filenamesToCheck = request.getParameter("filenamesToBeChecked");

        Map<String, Object> model = errors.getModel();
        
        if (filenamesToCheck != null) {
            String[] filenames = filenamesToCheck.split(",");

            ArrayList<String> existingFilenames = new ArrayList<>();
            ArrayList<String> existingFilenamesFixed = new ArrayList<>();
            
            for (String name : filenames) {
                String fixedName;
                try {
                    fixedName = fileUpload.fixFileName(name);
                } catch (ValidationException e) {
                    errors.rejectValue("file", e.getErrorCode(), e.getMessage());
                    return new ModelAndView(getFormView(), model);
                }
                Path itemPath = uri.extend(fixedName);
                if (fileUpload.fileExists(token, itemPath)) {
                    existingFilenames.add(name); 
                    existingFilenamesFixed.add(fixedName);
                }
            }
            // Return existing paths to let the user process them
            if (existingFilenames.isEmpty()) {
                return new ModelAndView(getSuccessView(), model);
            }
            
            command.setExistingFilenames(existingFilenames);
            command.setExistingFilenamesFixed(existingFilenamesFixed);
            errors.rejectValue("file", "manage.upload.resource.exists", "A resource of this name already exists");
            return new ModelAndView(getFormView(), model);
        }
        else {
            boolean shouldOverwriteExisting = request.getParameter("overwrite") != null;

            if (command.getCancelAction() != null) {
                command.setDone(true);
                return new ModelAndView(getSuccessView(), model);
            }

            ServletFileUpload upload = new ServletFileUpload();
            try {
                fileUpload.upload(token, upload.getItemIterator(request), uri, shouldOverwriteExisting);
            } catch (ValidationException e) {
                errors.rejectValue("file", e.getErrorCode(), e.getMessage());
                return new ModelAndView(getFormView(), model);
            }

            command.setDone(true);
        }
        return new ModelAndView(getSuccessView(), model);
    }

    @Required
    public void setFileUpload(FileUpload fileUpload) {
        this.fileUpload = fileUpload;
    }
}
