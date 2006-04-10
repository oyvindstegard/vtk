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
package org.vortikal.web.service;

import org.vortikal.repository.Namespace;
import org.vortikal.repository.Property;
import org.vortikal.repository.Resource;
import org.vortikal.security.Principal;

/**
 * Assertion for matching on whether the current resource has a
 * property with a given name, namespace and value.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>namespace</code> - the {@link Property#getNamespace
 *   namespace} of the property to match
 *   <li><code>name</code> - the {@link Property#getName name} of
 *   the property to match
 *   <li><code>value</code> - the {@link Property#getValue value}
 *   of the property to match
 * </ul>
 */
public class ResourcePropertyAssertion
  extends AbstractRepositoryAssertion {

    private Namespace namespace;
    private String name;
    private String value;
    private boolean invert = false;
    
    public void setName(String name) {
        this.name = name;
    }
    
    
    public void setNamespace(String namespace) {
        this.namespace = Namespace.getNamespace(namespace);
    }
    
    
    public String getName() {
        return this.name;
    }


    public String getNamespace() {
        return this.namespace.getUri();
    }


    public String getValue() {
        return this.value;
    }


    public void setValue(String value) {
        this.value = value;
    }


    public boolean conflicts(Assertion assertion) {
        if (assertion instanceof ResourcePropertyAssertion) {

            ResourcePropertyAssertion other = (ResourcePropertyAssertion) assertion;
			
            if (this.namespace.equals(other.getNamespace()) && 
                this.name.equals(other.getName())) {
				
                boolean sameValue = (this.value == null && other.getValue() == null)
                    || (this.value != null && this.value.equals(other.getValue()));

                if (!this.invert && !other.invert)
                    return  !sameValue;
                else if (this.invert != other.invert)
                    return sameValue;
            }
        }
        return false;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
		
        sb.append(super.toString());
        sb.append("; namespace = ").append(this.namespace);
        sb.append("; name = ").append(this.name);
        sb.append("; value = ").append(this.value);

        return sb.toString();
    }


    public void setInvert(boolean invert) {
        this.invert = invert;
    }


    public boolean matches(Resource resource, Principal principal) {

        if (resource != null) {
            Property property = resource.getProperty(namespace, name);

            // XXX: type the 'value' private variable:
            if (property != null && value.equals(property.getStringValue())) return !invert;
        }
        
        return invert;
    }
    

}
