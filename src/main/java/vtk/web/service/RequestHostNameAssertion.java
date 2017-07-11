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

import org.springframework.util.StringUtils;

import vtk.repository.Resource;
import vtk.security.Principal;

/**
 * Assertion that matches on the request hostname.
 *
 * <p>Configurable JavaBean properties
 * <ul>
 *   <li><code>hostName</code> - the hostname to match. A value of
 *   <code>*</code> means that any hostname matches. A comma separated
 *   list of hostnames is also allowed.
 * </ul>
 */
public class RequestHostNameAssertion implements WebAssertion {

    private String defaultHostName;
    private String[] hostNames;

    public void setHostName(String hostName) {
        if (hostName == null || hostName.trim().equals("")) {
            throw new IllegalArgumentException("Illegal hostname: '" + hostName + "'");
        }
        
        this.hostNames = StringUtils.tokenizeToStringArray(hostName, ", ");
        if (this.hostNames.length == 0) {
            throw new IllegalArgumentException(
                "Unable to find host name in argument: '" + hostName + "'");
        }

        this.defaultHostName = this.hostNames[0];
        if ("*".equals(this.defaultHostName)) {
            this.defaultHostName = vtk.util.net.NetUtils.guessHostName();
        }
    }

    public String[] getHostNames() {
        return this.hostNames;
    }
    
    @Override
    public boolean conflicts(WebAssertion assertion) {
        if (assertion instanceof RequestHostNameAssertion) { 
            boolean conflict = true;
           for (String hostName: this.hostNames) {
                if ("*".equals(hostName)) {
                    conflict = false;
                    break;
                }
                String[] otherHostNames = ((RequestHostNameAssertion)assertion).getHostNames();
                for (String other: otherHostNames) {
                    if ("*".equals(other)) {
                        conflict = false;
                        break;
                    }
                    if (hostName.equals(other)) {
                        conflict = false;
                        break;
                    }
                }
            }
           return conflict;
        }
        return false;
    }

    @Override
    public Optional<URL> processURL(URL url, Resource resource, Principal principal) {
        url = new URL(url);
        boolean found = false;
        for (String hostName: this.hostNames) {
            if ("*".equals(hostName)
                    || url.getHost().equals(hostName)) {
                found = true;
                break;
            }
        }
        if (!found) {
            url.setHost(defaultHostName);
        }
        return Optional.of(url);
    }
    
    @Override
    public URL processURL(URL url) {
        return url;
    }

    @Override
    public boolean matches(HttpServletRequest request,
                           Resource resource, Principal principal) {
        for (String hostName: this.hostNames) {
            if ("*".equals(hostName)) {
                return true;
            }
            String requestHostName = request.getServerName();
            if (hostName.equals(requestHostName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("request.hostname in (");
        for (int i = 0; i < this.hostNames.length; i++) {
            sb.append(this.hostNames[i]);
            if (i < this.hostNames.length - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }

}
