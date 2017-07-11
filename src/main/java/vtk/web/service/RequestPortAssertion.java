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
 * Assertion matching on request port numbers.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>port</code> - either the string <code>*</code> meaning
 *   'match all ports', or a comma separated list of port numbers
 *   (positive integers)
 * </ul>
 */
public class RequestPortAssertion implements WebAssertion {

    private static final int PORT_ANY = -1;

    private int[] ports;
	

    public void setPort(String port) {
		
        String[] portsArray  = StringUtils.tokenizeToStringArray(port, ", ");
        this.ports = new int[portsArray.length];

        for (int i = 0; i < portsArray.length; i++) {
            if ("*".equals(portsArray[i])) {
                this.ports[i] = PORT_ANY;
            } else {
                this.ports[i] = Integer.parseInt(portsArray[i]);
                if (this.ports[i] <= 0) throw new IllegalArgumentException(
                    "Server port number must be a positive integer");
            }
        }

    }

    @Override
    public boolean conflicts(WebAssertion assertion) {

        if (assertion instanceof RequestPortAssertion) {
            boolean conflict = true;
            for (int i = 0; i < this.ports.length; i++) {
                if (PORT_ANY == this.ports[i]) {
                    conflict = false;
                    break;
                }
                int[] otherPorts = ((RequestPortAssertion)assertion).ports;
                for (int j = 0; j < otherPorts.length; j++) {
                    if (PORT_ANY == otherPorts[j]) {
                        conflict = false;
                        break;
                    }

                    if (this.ports[i] == otherPorts[j]) {
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
        if (this.ports.length == 1 && this.ports[0] == PORT_ANY) {
            // Handle special case of port = * (preserve port of original URL)
            return Optional.of(url);
        }
        for (int port: ports) {
            if (port == url.getPort()) {
                break;
            }
            if (port != PORT_ANY) {
                url.setPort(port);
                break;
            }
        }
        return Optional.of(url);
    }

    @Override
    public URL processURL(URL url) {
        return url;
    }

    @Override
    public boolean matches(HttpServletRequest request, Resource resource,
                           Principal principal) {
        for (int i = 0; i < this.ports.length; i++) {
            if (this.ports[i] == PORT_ANY) {
                return true;
            }
            if (this.ports[i] == request.getServerPort()) {
                return true;
            }
        }
        return false;
    }


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("request.port in (");
        for (int i = 0; i < this.ports.length; i++) {
            if (i != 0) sb.append(", ");
            if (this.ports[i] != PORT_ANY) {
                sb.append(this.ports[i]);
            } else {
                sb.append("*");
            }
        }
        sb.append(")");
        return sb.toString();
    }

}
