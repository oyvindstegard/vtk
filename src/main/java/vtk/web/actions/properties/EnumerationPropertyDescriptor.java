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
package vtk.web.actions.properties;


import java.util.Optional;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

import vtk.repository.Resource;
import vtk.repository.resourcetype.RepositoryAssertion;
import vtk.security.Principal;


/**
 * Descriptor class for editing properties that can have a distinct
 * set of values.
 *
 */
public class EnumerationPropertyDescriptor implements InitializingBean {

    private String namespace = null;
    private String name = null;
    private String[] values = null;
    private String defaultValue = null;
    private RepositoryAssertion[] assertions = null;
    
    public boolean isApplicableProperty(Resource resource, Principal principal) {
        if (this.assertions == null)
            return true;
        
        Optional<Resource> optResource = Optional.ofNullable(resource);
        Optional<Principal> optPrincipal = Optional.ofNullable(principal);
        
        for (int i = 0; i < this.assertions.length; i++) {
            if (!this.assertions[i].matches(optResource, optPrincipal)) {
                return false;
            }
        }
        return true;
    }
    


    public void afterPropertiesSet() {
        if (this.namespace == null || this.namespace.trim().equals("")) {
            throw new BeanInitializationException(
                "Bean property 'namespace' not set");
        }
        // FIXME: check for default namespace (dav:), and possibly
        // certain protected namespaces.

        if (this.name == null || this.name.trim().equals("")) {
            throw new BeanInitializationException(
                "Bean property 'name' not set");
        }

//         if (this.values == null) {
//             throw new BeanInitializationException(
//                 "Bean property 'values' not set");
//         }

    }
    


    /**
     * Gets the value of namespace
     *
     * @return the value of namespace
     */
    public String getNamespace() {
        return this.namespace;
    }

    /**
     * Sets the value of namespace
     *
     * @param namespace Value to assign to this.namespace
     */
    public void setNamespace(String namespace)  {
        this.namespace = namespace;
    }

    /**
     * Gets the value of name
     *
     * @return the value of name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the value of name
     *
     * @param name Value to assign to this.name
     */
    public void setName(String name)  {
        this.name = name;
    }

    
    /**
     * Gets the value of values
     *
     * @return the value of values
     */
    public String[] getValues() {
        return this.values;
    }

    /**
     * Sets the values
     *
     * @param values
     */
    public void setValues(String[] values)  {
        if (values == null) {
            throw new IllegalArgumentException("Values cannot be null");
        }
        this.values = new String[values.length + 1];
        for (int i = 0; i < values.length; i++) {
            this.values[i] = values[i];
        }
    }


    /**
     * Gets the value of defaultValue
     *
     * @return the value of defaultValue
     */
    public String getDefaultValue() {
        return this.defaultValue;
    }

    /**
     * Sets the value of defaultValue
     *
     * @param defaultValue Value to assign to this.defaultValue
     */
    public void setDefaultValue(String defaultValue)  {
        this.defaultValue = defaultValue;
    }

    public RepositoryAssertion[] getAssertions() {
        return this.assertions;
    }

    public void setAssertions(RepositoryAssertion[] assertions) {
        this.assertions = assertions;
    }
    
    
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(this.getClass().getName()).append("[");
        sb.append("property=").append(this.namespace).append(":").append(this.name).append("]");
        return sb.toString();
    }
    
}
