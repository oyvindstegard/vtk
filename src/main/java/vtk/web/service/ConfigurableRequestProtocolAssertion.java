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

import java.util.List;
import java.util.Optional;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.InitializingBean;

import vtk.repository.Path;
import vtk.repository.Resource;
import vtk.security.Principal;

/**
 * Assertion that matches on URI path prefixes.  Uses a {@link
 * Properties} object with entries of type <code>&lt;uri-prefix&gt; =
 * &lt;value&gt;</code>.
 * 
 * <p>Configurable properties:
 * <ul>
 *   <li><code>restrictedProtocol</code> - the protocol to use if the resource is 
 *   {@link Resource#isReadRestricted() read restricted} (overrides the other options)</li>
 *   <li><code>configuration</code> - the {@link Properties} object
 *   </li>
 *   <li><code>defaultProtocol</code> - the protocol to use if no configuration exists
 *   </li>
 * </ul>
 */
public class ConfigurableRequestProtocolAssertion implements WebAssertion, InitializingBean {

    private final static String PROTO_HTTP = "http";
    private final static String PROTO_HTTPS = "https";

    private String defaultProtocol = null;
    private String restrictedProtocol = null;
    private Properties configuration;
    private boolean invert = false;
    
    public void setConfiguration(Properties configuration) {
        this.configuration = configuration;
    }
    
    public void setInvert(boolean invert) {
        this.invert = invert;
    }
    
    public void setDefaultProtocol(String defaultProtocol) {
        if ("*".equals(defaultProtocol) || PROTO_HTTP.equals(defaultProtocol) 
                || PROTO_HTTPS.equals(defaultProtocol)) {
            this.defaultProtocol = defaultProtocol;
        } else {
            throw new IllegalArgumentException("Illegal value for default protocol: '" 
                    + defaultProtocol + "'");
        }
    }
    
    public void setRestrictedProtocol(String restrictedProtocol) {
        this.restrictedProtocol = restrictedProtocol;
    }

    @Override
    public void afterPropertiesSet() {
        if (this.configuration == null) throw new IllegalArgumentException(
            "JavaBean property 'configuration' not specified");
    }

    @Override
    public boolean conflicts(WebAssertion assertion) {
        return false;
    }

    @Override
    public URL processURL(URL url) {
        return processURL(url, null, null).get();
    }
    
    @Override
    public Optional<URL> processURL(URL url, Resource resource, Principal principal) {

       Path uri = url.getPath();
       if (resource != null && resource.isReadRestricted()) {
            if (this.restrictedProtocol != null && !"*".equals(this.restrictedProtocol)) {
                url.setProtocol(this.restrictedProtocol);
                return Optional.of(url);
            }
        }
        if (this.configuration == null || this.configuration.isEmpty()) {
            if (this.defaultProtocol != null) {
                if (!"*".equals(this.defaultProtocol)) {
                    url.setProtocol(this.defaultProtocol);
                }
            }
            return Optional.of(url);
        }
        
        while (uri != null) {
            String value = this.configuration.getProperty(uri.toString());
            if (value != null) {
                value = value.trim();
                if (PROTO_HTTP.equals(value) || PROTO_HTTPS.equals(value)) {
                    url.setProtocol(invertProtocol(value, this.invert));
                    return Optional.of(url);
                }
            }
            uri = uri.getParent();
        }
        if (this.defaultProtocol != null) {
            if (!"*".equals(this.defaultProtocol)) {
                url.setProtocol(this.defaultProtocol);
            }
            return Optional.of(url);
        } 
        url.setProtocol(PROTO_HTTP);
        return Optional.of(url);
    }


    @Override
    public boolean matches(HttpServletRequest request, Resource resource,
                           Principal principal) {
        if (this.configuration == null || this.configuration.isEmpty()) {
            return this.invert ? false : true;
        }
        URL url = URL.create(request);
        String protocol = url.getProtocol();
        Path path = url.getPath();
        List<Path> paths = path.getPaths();
        for (int i = paths.size() - 1; i >= 0; i--) {
            String prefix = paths.get(i).toString();
            String value = this.configuration.getProperty(prefix);
            if (value != null) {
                
                boolean match = protocol.equals(value.trim());
                return this.invert ? !match : match;
            }
        }
        return this.invert ? false : true;
    }
    
    @Override
    public String toString() {
        return "request.protocol.matches(" + this.configuration + ")";
   }

    private String invertProtocol(String protocol, boolean invert) {
        if (!invert) {
            return protocol;
        }
        if (PROTO_HTTPS.equals(protocol)) {
            return PROTO_HTTP;
        }
        return PROTO_HTTPS;
    }
}
