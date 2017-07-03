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
package vtk.web.display.shell;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.shell.AbstractConsole;
import vtk.web.RequestContext;
import vtk.web.SimpleFormController;
import vtk.web.service.Service;
import vtk.web.service.URL;


public class CommandExecutorController extends SimpleFormController<ExecutorCommand> {

    private AbstractConsole console;
    
    
    public void setConsole(AbstractConsole console) {
        this.console = console;
    }

    @Override
    protected ExecutorCommand formBackingObject(HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Service service = requestContext.getService();
        
        Resource resource = repository.retrieve(
            token, requestContext.getResourceURI(), false);
        
        URL url = service.urlConstructor(URL.create(request))
                .withResource(resource)
                .withPrincipal(requestContext.getPrincipal())
                .constructURL();
        
        ExecutorCommand command = new ExecutorCommand(url.toString());
        return command;
    }

    
    
    @Override
    protected ModelAndView onSubmit(HttpServletRequest request,
            HttpServletResponse response, ExecutorCommand command,
            BindException errors) throws Exception {
        
        if ("true".equals(request.getParameter("pipe-output"))) {
            PrintStream out = new PrintStream(response.getOutputStream());
            response.setContentType("text/plain;charset=utf-8");
            console.eval(command.getCommand(), out);
            out.flush();
            out.close();
            return null;
        }
        
        Map<String, Object> model = errors.getModel();
        ByteArrayOutputStream bufferStream = new ByteArrayOutputStream();
        PrintStream resultStream = new PrintStream(bufferStream);
        console.eval(command.getCommand(), resultStream);
        command.setResult(new String(bufferStream.toByteArray()));
        return new ModelAndView(getFormView(), model);
    }


}

