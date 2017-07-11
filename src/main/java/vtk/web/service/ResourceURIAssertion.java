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

import vtk.repository.Path;
import vtk.repository.Resource;
import vtk.security.Principal;

/**
 * Assertion for exact string matching on the URI of the requested
 * resource.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>uri</code> - the exact URI (path) to match
 *   <li><code>inverted</code> - a flag to invert matching (default
 *   <code>false</code>).
 * </ul>
 */
public class ResourceURIAssertion extends AbstractAssertion {

    private Path uri;
    private boolean inverted = false;
    

    public void setUri(String uri) {
        if (uri == null) throw new IllegalArgumentException(
            "JavaBean property 'uri' cannot be null");
    
        this.uri = Path.fromString(uri);
    }
    
    public boolean isInverted() {
        return this.inverted;
    }

    public void setInverted(boolean inverted)  {
        this.inverted = inverted;
    }


    @Override
    public boolean conflicts(WebAssertion assertion) {
        if (assertion instanceof ResourceURIAssertion) {
            ResourceURIAssertion other = (ResourceURIAssertion) assertion;
            if (!this.inverted && !other.isInverted()) {
                return ! (this.uri.equals(other.uri));
                
            } else if (this.inverted && !other.isInverted() ||
                       !this.inverted && other.isInverted()) {
                return this.uri.equals(other.uri);
            }
        }
        return false;
    }

    @Override
    public String toString() {
        if (this.inverted) {
            return "request.uri != " + this.uri;
        }
        return "request.uri = " + this.uri;
    }

    @Override
    public Optional<URL> processURL(URL url, Resource resource,
            Principal principal) {
        if (matches(resource)) {
            return Optional.of(url.setPath(resource.getURI()));
        }
        return Optional.empty();
    }

    @Override
    public URL processURL(URL url) {
        return url.setPath(uri);
    }

    @Override
    public boolean matches(HttpServletRequest request, 
            Resource resource, Principal principal) {
        if (resource == null) {
            return this.inverted ? true : false;
        }
        if (this.inverted) {
            return ! this.uri.equals(resource.getURI());
        }
        return this.uri.equals(resource.getURI());
    }

    private boolean matches(Resource resource) {
        return inverted ? !uri.equals(resource.getURI()) : 
            uri.equals(resource.getURI());
    }

}
