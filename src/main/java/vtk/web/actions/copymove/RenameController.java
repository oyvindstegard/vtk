/* Copyright (c) 2004, University of Oslo, Norway
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
package vtk.web.actions.copymove;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.SimpleFormController;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.web.RequestContext;
import vtk.web.service.Service;

public class RenameController extends SimpleFormController {

    private static Log logger = LogFactory.getLog(RenameController.class);

    protected Object formBackingObject(HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Service service = requestContext.getService();
        Repository repository = requestContext.getRepository();
        
        Resource resource = repository.retrieve(
                requestContext.getSecurityToken(), requestContext.getResourceURI(), false);
        String url = service.constructLink(resource, requestContext.getPrincipal());

        RenameCommand command = new RenameCommand(resource, url);
        return command;
    }

    protected ModelAndView onSubmit(Object command, BindException errors) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();

        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();

        RenameCommand renameCommand = (RenameCommand) command;

        if (renameCommand.getCancel() != null) {
            return new ModelAndView(getSuccessView());
        }

        if (renameCommand.isConfirmOverwrite()) {
            return new ModelAndView(getFormView());
        }

        Repository repository = requestContext.getRepository();
        Resource resource = repository.retrieve(token, uri, false);
        String name = resource.getName();

        boolean overwrite = false;
        if (renameCommand.getOverwrite() != null) {
            overwrite = true;
        }

        try {
            Path newUri = renameCommand.getRenamePath();
            if (!name.equals(renameCommand.getName())) {
                if (overwrite) {
                    repository.delete(token, newUri, true);
                }
                
                repository.move(token, uri, newUri, overwrite);
                resource = repository.retrieve(token, newUri, false);
            }
            Map<String, Object> model = new HashMap<String, Object>();
            model.put("resource", resource);
            return new ModelAndView(getSuccessView(), model);
        } catch (Exception e) {
            logger.error("An error occured while renaming resource " + uri, e);
            errors.rejectValue("name", "manage.rename.resource.validation.failed", "Renaming of resource failed");
            return new ModelAndView(getFormView(), errors.getModel());
        }
    }
}
