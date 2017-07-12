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
package vtk.web.actions.repo;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

import vtk.repository.Repository;
import vtk.web.RequestContext;

/**
 * Controller that switches read only mode of a specified repository.
 * Note that the principal needs to have root role to do this.
 */
public class RepositoryReadOnlyController
  extends AbstractController implements InitializingBean {

    private Repository repository;
    private String viewName;
    private String parameterName;

    
    public void setRepository(Repository repository) {
        this.repository = repository;
    }
    

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }


    public void setParameterName(String parameterName) {
        this.parameterName = parameterName;
    }
    
    

    public void afterPropertiesSet() {
        if (this.repository == null) {
            throw new BeanInitializationException(
                "Property 'repository' not set");
        }
        if (this.viewName == null) {
            throw new BeanInitializationException(
                "Property 'viewName' not set");
        }
        if (this.parameterName == null) {
            throw new BeanInitializationException(
                "Property 'parameterName' not set");
        }
    }
    

    @Override
    protected ModelAndView handleRequestInternal(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext
                .getRequestContext(request);
        String token = requestContext.getSecurityToken();
        String readOnlyStr = request.getParameter(this.parameterName);

        if ("true".equals(readOnlyStr)) {
            this.repository.setReadOnly(token, true);
        }
        else if ("false".equals(readOnlyStr)) {
            this.repository.setReadOnly(token, false);
        }

        return new ModelAndView(this.viewName);
    }

}
