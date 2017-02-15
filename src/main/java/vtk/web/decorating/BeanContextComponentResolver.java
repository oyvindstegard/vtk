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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.TypeInfo;
import vtk.repository.resourcetype.ResourceTypeDefinition;
import vtk.web.RequestContext;

public class BeanContextComponentResolver
    implements ComponentResolver, ApplicationContextAware, InitializingBean {

    private static Logger logger = LoggerFactory.getLogger(BeanContextComponentResolver.class);

    private volatile boolean initialized = false;
    private ApplicationContext applicationContext;
    private Map<String, DecoratorComponent> components = new HashMap<>();
    private Set<String> availableComponentNamespaces = new HashSet<>();
    private ResourceTypeDefinition resourceType;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void setAvailableComponentNamespaces(Set<String> availableComponentNamespaces) {
        this.availableComponentNamespaces = availableComponentNamespaces;
    }

    public void setResourceType(ResourceTypeDefinition resourceType) {
        this.resourceType = resourceType;
    }

    @Override
    public void afterPropertiesSet() {
        if (this.applicationContext == null) {
            throw new BeanInitializationException(
                    "JavaBean property 'applicationContext' not specified");
        }
        if (this.availableComponentNamespaces == null) {
            throw new BeanInitializationException(
                    "JavaBean property 'availableComponentNamespaces' not specified");
        }
    }

    @Override
    public DecoratorComponent resolveComponent(String namespace, String name) {
        if (!this.initialized) {
            init();
        }
        DecoratorComponent component = this.components.get(namespace + ":" + name);
        if (this.resourceType != null) {
            TypeInfo type = getResourceTypeInfo();
            if (type == null || !type.isOfType(this.resourceType)) {
                component = null;
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Resolved namespace: '" + namespace + "', name: '" + name
                    + "' to component:  " + component);
        }
        return component;
    }


    @Override
    public List<DecoratorComponent> listComponents() {
        if (!this.initialized) {
            init();
        }
        List<DecoratorComponent> result = new ArrayList<>();
        for (DecoratorComponent component : this.components.values()) {
            String namespace = component.getNamespace();
            if (this.availableComponentNamespaces != null) {
                if (!this.availableComponentNamespaces.contains(namespace)) {
                    continue;
                }
            }
            result.add(component);
        }
        return result;
    }


    private synchronized void init() {
        if (this.initialized) return;

        Collection<DecoratorComponent> beans = 
                BeanFactoryUtils.beansOfTypeIncludingAncestors(
                        this.applicationContext, 
                        DecoratorComponent.class, false, false).values();        

        for (DecoratorComponent component: beans) {
            String ns = component.getNamespace();
            String name = component.getName();
            if (ns == null) {
                throw new IllegalStateException("Component " + component
                        + " has invalid namespace (NULL)");
            }
            
            if (!this.availableComponentNamespaces.contains(component.getNamespace())) {
                if (!this.availableComponentNamespaces.contains("*")) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Component " + component + " not added.");
                    }
                    continue;
                }
            }
            if (name == null) {
                throw new IllegalStateException("Component " + component
                        + " has invalid name (NULL)");
            }
            String key = ns + ":" + name;
            logger.debug("Registering decorator component " + component);
            this.components.put(key, component);
        }

        this.initialized = true;
    }

    private TypeInfo getResourceTypeInfo() {
        try {
            RequestContext requestContext = RequestContext.getRequestContext();
            Repository repository = requestContext.getRepository();
            String token = requestContext.getSecurityToken();
            Resource r = repository.retrieve(token, requestContext.getResourceURI(), true);
            return repository.getTypeInfo(r); 
        } catch (Throwable t) {
            return null;
        }
    }
}
