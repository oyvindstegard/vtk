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

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.Resource;
import vtk.security.Principal;


/**
 * Assertion that matches when the request URI matches a specified URI.
 *
 * <p>Properties:
 * 
 * <ul>
 * <li>{@link #setPath(java.lang.String) path} - the path string to match against.
 * This path should not be encoded in any way. (Matching takes care of decoding
 * the request URI.)
 *
 * <li>{@link #setMatchTrailingSlash(boolean) matchTrailingSlash} - set to match request
 * paths that have a trailing slash as well. Default is {@code true}.
 *
 * <li>{@link #setMatchDescendants(boolean) matchDescendants} - match any
 * descendant request paths as well. This also implies matching of request
 * paths with a {@link #setMatchTrailingSlash(boolean) trailing slash}. Default
 * is {@code false}.
 * </ul>
 */
public class RequestPathAssertion implements WebAssertion {

    private Path path;
    private boolean matchDescendants = false;
    private boolean matchTrailingSlash = true;
    
    @Override
    public boolean conflicts(WebAssertion assertion) {
        // Certain classes of path conflicts are ordering sensitive wrt. to service matching
        // so we only detect basic cases here
        if (assertion instanceof RequestPathAssertion) {
            RequestPathAssertion other = (RequestPathAssertion)assertion;
            if (this.path.equals(other.path)) {
                return false;
            }

            if (this.matchDescendants || other.matchDescendants) {
                // One must be descendant of other or vice versa
                if (this.path.isAncestorOf(other.path) || other.path.isAncestorOf(this.path)) {
                    return false;
                }
            }

            return true;
        }
        return false;
    }

    @Override
    public boolean matches(HttpServletRequest request, Resource resource, Principal principal) {
        URL requestUrl = URL.create(request);
        if (matchDescendants && path.isAncestorOf(requestUrl.getPath())) {
            return true;
        }

        if (path.equals(requestUrl.getPath())) {
            if (!matchTrailingSlash && !matchDescendants && requestUrl.isCollection()) {
                return false;
            }
            return true;
        }

        return false;
    }

    @Override
    public Optional<URL> processURL(URL url, Resource resource, Principal principal) {
        return Optional.of(processURL(url));
    }

    @Override
    public URL processURL(URL url) {
        url.setPath(this.path);
        return url;
    }
    
    @Required 
    public void setPath(String path) {
        this.path = Path.fromStringWithTrailingSlash(path);
    }

    public void setMatchDescendants(boolean matchDescendants) {
        this.matchDescendants = matchDescendants;
    }

    public void setMatchTrailingSlash(boolean matchTrailingSlash) {
        this.matchTrailingSlash = matchTrailingSlash;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("request.path");
        if (matchDescendants) {
            b.append(" ~ ").append(path).append("[").append(!path.isRoot() ? "/*" : "*").append("]");
        } else if (matchTrailingSlash && !path.isRoot()) {
            b.append(" = ").append(path).append("[/]");
        } else {
            b.append(" = ").append(path);
        }
        return b.toString();
    }

}
