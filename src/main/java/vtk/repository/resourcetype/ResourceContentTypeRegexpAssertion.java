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
package vtk.repository.resourcetype;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import vtk.repository.Resource;
import vtk.security.Principal;

/**
 * Assertion that performs a regular expression match on a resource's
 * content type.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>pattern</code> - the regular expression to match
 *   <li><code>exceptionPattern</code> - a regular expression denoting
 *   possible exceptions to the <code>pattern</code>
 *   expression. Content types that match this expression prevent the
 *   assertion from matching.
 * </ul>
 */
public class ResourceContentTypeRegexpAssertion implements RepositoryAssertion {
    private Pattern pattern;
    private Pattern exceptionPattern;
    
    public ResourceContentTypeRegexpAssertion(String pattern) {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        this.pattern = Pattern.compile(pattern);
    }
    
    public ResourceContentTypeRegexpAssertion(String pattern, String exceptionPattern) {
        this(pattern);
        Objects.requireNonNull(exceptionPattern, "exceptionPattern cannot be null");
        this.exceptionPattern = Pattern.compile(exceptionPattern);
    }
        
    @Override
    public boolean matches(Optional<Resource> resource,
            Optional<Principal> principal) {
        return resource.map(r -> matches(r)).orElse(false);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("property.content-type ~ ");
        sb.append(this.pattern.pattern());
        if (this.exceptionPattern != null) {
            sb.append(" && (content-type !~ ");
            sb.append(this.exceptionPattern.pattern());
            sb.append(")");
        }
        return sb.toString();
    }

    private boolean matches(Resource resource) {
        if (resource != null && resource.getContentType() != null) {
            Matcher m = this.pattern.matcher(resource.getContentType());
            boolean match = m.matches();
            
            if (this.exceptionPattern != null) {
                m = this.exceptionPattern.matcher(resource.getContentType());
                if (m.matches()) {
                    match = false;
                }
            }
            return match;
        }
        return false;
    }

}
