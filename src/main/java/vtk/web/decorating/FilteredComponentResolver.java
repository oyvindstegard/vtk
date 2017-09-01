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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

/**
 * A {@code ComponentResolver} which can filter available components
 * based on component namespace.
 *
 * <p>This resolver uses the a central bean context component registry as basis
 * for the componenets it is able to resolve.
 */
public class FilteredComponentResolver implements ComponentResolver {

    private static final Logger logger = LoggerFactory.getLogger(FilteredComponentResolver.class);
   
    private final Map<String, DecoratorComponent> components = new HashMap<>();
    private Set<String> availableComponentNamespaces = new HashSet<>();
    private BeanContextComponentRegistry registry;
    private volatile boolean initialized = false;

    @Required
    public void setBeanContextComponentRegistry(BeanContextComponentRegistry registry) {
        this.registry = registry;
    }

    /**
     * @param availableComponentNamespaces set of component namespaces which restricts which components this
     * resolver instance is able to resolve and list. A value of "*" in this set will match any namespace.
     */
    public void setAvailableComponentNamespaces(Set<String> availableComponentNamespaces) {
        this.availableComponentNamespaces = availableComponentNamespaces;
    }

    @Override
    public DecoratorComponent resolveComponent(String namespace, String name) {
        if (!this.initialized) {
            init();
        }

        DecoratorComponent component = this.components.get(namespace + ":" + name);
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

        return new ArrayList<>(components.values());
    }

    private synchronized void init() {
        if (this.initialized) return;

        for (DecoratorComponent c: registry.listComponents(c ->
            availableComponentNamespaces.contains("*") 
                    || availableComponentNamespaces.contains(c.getNamespace()))) {

            components.put(c.getNamespace()+":"+c.getName(), c);
        }

        this.initialized = true;
    }
}