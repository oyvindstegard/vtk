/* Copyright (c) 2006, University of Oslo, Norway
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

import java.util.Locale;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import vtk.repository.Namespace;

/**
 * XXX: We should consider re-modeling some of this wrt. to mixins and regular
 *      resource types (with the current model, implementations of 
 *      MixinResourceTypeDefinitoin are required to implement getMixinTypeDefinitions, 
 *      but that does not really make sense, since mixins should not have their
 *      own mixin types) 
 */
public abstract class AbstractResourceTypeDefinitionImpl implements ResourceTypeDefinition, InitializingBean {

    private String name;
    private Namespace namespace;
    private PropertyTypeDefinition[] propertyTypeDefinitions = new PropertyTypeDefinitionImpl[0];
    
    private TypeLocalizationProvider typeLocalizationProvider; 
    
    @Override
    public void afterPropertiesSet() {
        if (name == null) {
            throw new BeanInitializationException("Property 'name' not set.");
        }
        
        if (name.contains("/") || name.contains(":")) {
            throw new IllegalArgumentException("Resource type name cannot contain '/' or ':': " + name);
        }

        if (namespace == null) {
            throw new BeanInitializationException("Property 'namespace' not set.");
        }

        // All regular property definitions for this resource type inherit the type's namespace
        for (int i = 0; i < propertyTypeDefinitions.length; i++) {
            if (propertyTypeDefinitions[i] instanceof PropertyTypeDefinitionImpl) {
                ((PropertyTypeDefinitionImpl) propertyTypeDefinitions[i]).setNamespace(namespace);
            }
        }
    }

    @Override
    public String getName() {
        return this.name;
    }
    
    @Override
    public String getLocalizedName(Locale locale) {
        if (this.typeLocalizationProvider != null) {
            return this.typeLocalizationProvider.getLocalizedResourceTypeName(
                                                                   this, locale);
        } else {
            return getName();
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Namespace getNamespace() {
        return this.namespace;
    }

    public void setNamespace(Namespace namespace) {
        this.namespace = namespace;
    }

    @Override
    public PropertyTypeDefinition[] getPropertyTypeDefinitions() {
        return this.propertyTypeDefinitions;
    }

    public void setPropertyTypeDefinitions(PropertyTypeDefinition[] propertyTypeDefinitions) {
        this.propertyTypeDefinitions = propertyTypeDefinitions;
    }
    
    public void setTypeLocalizationProvider(TypeLocalizationProvider provider) {
        this.typeLocalizationProvider = provider;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder(this.getClass().getName());
        buffer.append("[ namespace = ").append(this.namespace);
        buffer.append(", name = '").append(this.name).append("']");
        return buffer.toString();
    }
    
}
