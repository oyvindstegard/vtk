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
package vtk.web.service;

import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.web.actions.copymove.CopyMoveSessionBean;

/**
 * Assertion that matches when a named session variable exists 
 * with the specified action
 *
 * <p>Configurable properties:
 * <ul>
 *   <li><code>variableName</code> - name of the session variable
 *   <li><code>action</code> - name of action stored in session variable
 * </ul>
 *
 */
public class SessionVariableExistsAssertion implements WebAssertion {

    private String variableName = null;
    private String action = null;

    public String getAction() {
        return this.action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getVariableName() {
        return this.variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    @Override
    public Optional<URL> processURL(URL url, Resource resource,
            Principal principal) {
        return Optional.of(url);
    }

    @Override
    public URL processURL(URL url) {
        return url;
    }
    
    @Override
    public boolean conflicts(WebAssertion assertion) {
        if (assertion instanceof SessionVariableExistsAssertion) {
            return ! (this.variableName.equals(
                    ((SessionVariableExistsAssertion) assertion).getVariableName()) && 
                    this.action.equals(((SessionVariableExistsAssertion)assertion).getAction()));
        }
        return false;
    }

    @Override
    public boolean matches(HttpServletRequest request, Resource resource, Principal principal) {
        CopyMoveSessionBean sessionBean = (CopyMoveSessionBean) request.getSession(true)
                .getAttribute(this.variableName);

        return sessionBean != null && this.action.equals(sessionBean.getAction());
    }

    @Override
    public String toString() {
        return "request.session[" + this.variableName + "].exists";
    }
}
