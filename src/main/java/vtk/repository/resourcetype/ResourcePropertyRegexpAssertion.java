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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.Resource;
import vtk.security.Principal;

public class ResourcePropertyRegexpAssertion implements RepositoryAssertion {

    private Namespace namespace;
    private String name;
    private Pattern[] patterns = null;
    private boolean invert = false;
    
    public void setName(String name) {
        this.name = name;
    }
    
    public void setNamespace(Namespace namespace) {
        this.namespace = namespace;
    }

    public void setPattern(String pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException(
                    "Property 'pattern' cannot be null");
        }
    
        this.patterns = new Pattern[1];
        this.patterns[0] = Pattern.compile(pattern);
    }
    
    public void setPatterns(String[] patterns) {
        if (patterns == null || patterns.length == 0) {
            throw new IllegalArgumentException(
                    "Property 'pattern' cannot be null or length 0");
            
        }
        this.patterns = new Pattern[patterns.length];
        for (int i = 0; i < patterns.length; i++) {
            this.patterns[i] = Pattern.compile(patterns[i]);
        }
    }
    
    public void setInvert(boolean invert) {
        this.invert = invert;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        if (this.patterns.length == 1) {
            sb.append("property.").append(this.name);
            sb.append(this.invert ? " !~ " : " ~ ");
            sb.append(this.patterns[0].pattern());
            return sb.toString();
        }
        
        sb.append("(");
        for (int i = 0; i < this.patterns.length; i++) {
            if (i > 0) sb.append(" and ");
            sb.append("property.").append(this.name);
            sb.append(this.invert ? " !~ " : " ~ ");
            sb.append(this.patterns[i].pattern());
        }
        sb.append(")");
        return sb.toString();
    }
    
    @Override
    public boolean matches(Optional<Resource> resource,
            Optional<Principal> principal) {
        if (resource.isPresent()) {
            return matches(resource.get());
        }
        return invert;
    }


    private boolean matches(Resource resource) {
        boolean match = false;
        Property property = resource.getProperty(this.namespace, this.name);
        if (property != null) {
            Value[] values = property.getDefinition().isMultiple() ? 
                    property.getValues() : new Value[] { property.getValue() } ;
                    for (Value v: values) {
                        String s = v.getStringValue();
                        for (Pattern p: this.patterns) {
                            Matcher m = p.matcher(s);
                            match = m.find();
                            if (!match) break;
                        }
                        if (match) break;
                    }
        }
        return match && !invert;
    }

}
