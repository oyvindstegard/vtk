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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Resource;
import vtk.security.Principal;

/**
 * Assertion that matches on HTTP method(s).
 *
 * <p>Configurable properties:
 * <ul>
 *   <li><code>method</code> - the HTTP method to match
 *   <li><code>methods</code> - alternatively, a {@link Set set} of
 *   HTTP methods can be specified.
 * </ul>
 */
public class RequestMethodAssertion implements WebAssertion {
    private Set<String> methods = new HashSet<>();
    
    public void setMethod(String method) {
        this.methods.clear();
        this.methods.add(method);
    }
	
    public void setMethods(Set<String> methods) {
        this.methods.clear();
        for (String method: methods) {
            this.methods.add(method);
        }
    }

    @Override
    public boolean conflicts(WebAssertion assertion) {
        if (!(assertion instanceof RequestMethodAssertion)) {
            return false;
        }
        RequestMethodAssertion other = (RequestMethodAssertion) assertion;
        for (String method: other.methods) {
            if (this.methods.contains(method)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Optional<URL> processURL(URL url, Resource resource, Principal principal) {
        return Optional.of(url);
    }

    @Override
    public URL processURL(URL url) {
        return url;
    }
    
    @Override
    public boolean matches(HttpServletRequest request, Resource resource, Principal principal) {
        String reqMethod = request.getMethod();
        return this.methods.contains(reqMethod);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("request.method in (");
        for (Iterator<String> i = this.methods.iterator(); i.hasNext();) {
            sb.append(i.next());
            if (i.hasNext()) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }

}
