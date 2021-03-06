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
package vtk.web.actions.lock;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.web.RequestContext;
import vtk.web.SimpleFormController;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class UnlockFormController extends SimpleFormController<UnlockFormCommand> {

    @Override
    protected UnlockFormCommand formBackingObject(HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Path uri = requestContext.getResourceURI();
        Service service = requestContext.getService();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Resource resource = repository.retrieve(token, uri, false);
        URL url = service.urlConstructor(URL.create(request))
                .withResource(resource)
                .withPrincipal(requestContext.getPrincipal())
                .constructURL();
        UnlockFormCommand command = new UnlockFormCommand(url.toString());
        return command;
    }

    @Override
    protected ModelAndView onSubmit(HttpServletRequest request,
            HttpServletResponse response, UnlockFormCommand command,
            BindException errors) throws Exception {
        
        if (command.getCancel() != null) {
            return new ModelAndView(getSuccessView());
        }
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        repository.unlock(token, uri, null);
        return new ModelAndView(getSuccessView());
    }
}
