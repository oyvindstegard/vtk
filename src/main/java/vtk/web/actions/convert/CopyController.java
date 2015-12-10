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
package vtk.web.actions.convert;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.SimpleFormController;
import vtk.web.service.Service;

public abstract class CopyController<T extends CopyCommand> extends SimpleFormController<T> {

    private String cancelView;
    private String extension;
    private String resourceName;
    private boolean parentViewOnSuccess;

    protected CopyAction copyAction;

    protected abstract void processCopyAction(Path originalUri, Path copyUri, T copyCommand) throws Exception;

    protected abstract T createCommand(String name, String url);

    @Override
    protected T formBackingObject(HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Service service = requestContext.getService();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Principal principal = requestContext.getPrincipal();
        
        Resource resource = repository.retrieve(token, requestContext.getResourceURI(), false);
        String url = service.constructLink(resource, principal);

        String name = resource.getName();
        if (name.indexOf(".") > 0) {
            name = name.substring(0, name.lastIndexOf("."));
        }
        if (this.extension != null) {
            name += this.extension;
        }
        if (this.resourceName != null) {
            name = this.resourceName;
        }
        return createCommand(name, url);
    }

    
    
    @Override
    protected ModelAndView onSubmit(HttpServletRequest request,
            HttpServletResponse response, T copyCommand, BindException errors)
            throws Exception {

        Map<String, Object> model = errors.getModel();

        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        Path uri = requestContext.getResourceURI();

        if (copyCommand.getCancelAction() != null) {
            copyCommand.setDone(true);
            return new ModelAndView(this.cancelView);
        }

        Path copyToCollection = uri.getParent();
        if (copyToCollection == null)
            copyToCollection = Path.fromString("/");
        Path copyUri = copyToCollection.extend(copyCommand.getName());

        // perform the actual copy action
        this.processCopyAction(uri, copyUri, copyCommand);

        copyCommand.setDone(true);

        Resource resource = null;

        if (this.parentViewOnSuccess) {
            resource = repository.retrieve(token, copyToCollection, false);
        } else {
            resource = repository.retrieve(token, copyUri, false);
        }
        model.put("resource", resource);

        return new ModelAndView(getSuccessView(), model);
    }

    @Required
    public void setCopyAction(CopyAction copyAction) {
        this.copyAction = copyAction;
    }

    public void setCancelView(String cancelView) {
        this.cancelView = cancelView;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public void setParentViewOnSuccess(boolean parentViewOnSuccess) {
        this.parentViewOnSuccess = parentViewOnSuccess;
    }

}
