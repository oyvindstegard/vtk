/* Copyright (c) 2005, University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Path;
import vtk.web.Message;
import vtk.web.RequestContext;

/**
 * A controller stores the Uri of the resources the user has selected for
 * copy/move in a session variable
 * 
 * <p>
 * Description:
 * 
 * <p>
 * Configurable properties:
 * <ul>
 * <li><code>repository</code> - the content repository
 * <li><code>viewName</code> - the view to return to
 * </ul>
 * 
 * <p>
 * Model data published:
 * <ul>
 * <li><code>infoMessage</code>: a message that is presented under the
 * breadcrumb component</li>
 * </ul>
 */

public class CopyMoveController implements Controller {

    private static final String COPYMOVE_SESSION_ATTRIBUTE = "copymovesession";
    private String viewName;

    @SuppressWarnings("unchecked")
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        CopyMoveSessionBean sessionBean = (CopyMoveSessionBean) request.getSession(true).getAttribute(
                COPYMOVE_SESSION_ATTRIBUTE);

        /*
         * Deleting session if you get a POST-request (because then it is a new
         * request for copy/move)
         */
        if (sessionBean != null && request.getMethod().equals("POST")) {
            request.getSession(true).removeAttribute(COPYMOVE_SESSION_ATTRIBUTE);
            sessionBean = null;
        }

        if (sessionBean == null) {
            sessionBean = new CopyMoveSessionBean();
            List<String> filesToBeCopied = new ArrayList<String>();

            /*
             * Walk through the request-parameters to find the resources
             * selected for copy/move and store them in session
             */

            Enumeration<String> e = request.getParameterNames();

            while (e.hasMoreElements()) {
                String name = e.nextElement();

                try {
                    Path.fromString(name);
                    filesToBeCopied.add(name);
                } catch (IllegalArgumentException iae) {
                    // Not a path, ignore it, try next one
                    continue;
                }
            }
            String action = request.getParameter("action");
            sessionBean.setAction(action);
            sessionBean.setFilesToBeCopied(filesToBeCopied);
            request.getSession(true).setAttribute(COPYMOVE_SESSION_ATTRIBUTE, sessionBean);
        }
        
        RequestContext requestContext = RequestContext.getRequestContext();
        String msgCode = "copyMove." + sessionBean.getAction() + ".info";
        requestContext.addInfoMessage(new Message(msgCode));
        
        return new ModelAndView(this.viewName, new HashMap<>());
    }

    @Required
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

}
