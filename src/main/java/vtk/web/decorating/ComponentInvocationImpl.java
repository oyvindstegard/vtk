/* Copyright (c) 2007, University of Oslo, Norway
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
package vtk.web.decorating;

import java.util.Collections;
import java.util.Map;


/**
 * A "runtime" representation of a component (a component 
 * instance and request).
 */
public class ComponentInvocationImpl implements ComponentInvocation {

    private String namespace;
    private String name;
    
    private Map<String, Object> parameters;
    
    public ComponentInvocationImpl(String namespace, String name, Map<String, Object> parameters) {
        if (name == null) {
            throw new IllegalArgumentException("Name argument is NULL");
        }
        if (parameters == null) {
            throw new IllegalArgumentException("Parameters argument is NULL");
        }
        this.namespace = namespace;
        this.name = name;
        this.parameters = parameters;
    }
    
    public String getNamespace() {
        return this.namespace;
    }
    
    public String getName() {
        return this.name;
    }

    public Map<String, Object> getParameters() {
        return Collections.unmodifiableMap(this.parameters);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getName()).append(":");
        sb.append(this.namespace).append(":").append(this.name);
        sb.append(" [").append(this.parameters.toString()).append("]");
        return sb.toString();
    }
}
