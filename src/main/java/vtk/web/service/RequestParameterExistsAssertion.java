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
package vtk.web.service;

import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Resource;
import vtk.security.Principal;


/**
 * Assertion that matches when a named request parameter exists (or
 * does not exist) in the query string.
 *
 * <p>Configurable properties:
 * <ul>
 *   <li><code>parameterName</code> - the parameter name to look for
 *   <li><code>invert</code> - if <code>true</code>, match only when
 *   the parameter does not exist
 * </ul>
 *
 */
public class RequestParameterExistsAssertion implements WebAssertion {

    private String parameterName = null;
    private boolean invert = false;
    
	
    public void setParameterName(String parameterName) {
        this.parameterName = parameterName;
    }
	
    public void setInvert(boolean invert) {
        this.invert = invert;
    }
	
    public String getParameterName() {
        return this.parameterName;
    }


    public boolean isInvert() {
        return this.invert;
    }


    @Override
    public boolean conflicts(WebAssertion assertion) {
        if (assertion instanceof RequestParameterExistsAssertion) {

            if (this.parameterName.equals(
                    ((RequestParameterExistsAssertion)assertion).getParameterName())) {

                return ! (this.invert == ((RequestParameterExistsAssertion)assertion).isInvert());
            }
        }
        if (assertion instanceof RequestParameterAssertion) {

            if (this.parameterName.equals(
                    ((RequestParameterAssertion)assertion).getParameterName())) {

                return !this.invert;
            }
        }
        return false;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (this.invert) sb.append("!");
        sb.append("request.parameters[").append(this.parameterName).append("].exists");
        return sb.toString();
    }

    @Override
    public Optional<URL> processURL(URL url, Resource resource, Principal principal) {
        return Optional.of(processURL(url));
    }

    @Override
    public boolean matches(HttpServletRequest request, Resource resource, Principal principal) {
        return (this.invert != request.getParameterMap().containsKey(this.parameterName));
    }

    @Override
    public URL processURL(URL url) {
        if (!this.invert && (url.getParameter(this.parameterName) == null))
            url.addParameter(this.parameterName, "");
        return url;
    }
}
