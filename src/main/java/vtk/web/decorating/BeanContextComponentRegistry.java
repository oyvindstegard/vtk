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
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * Registry of all instances of {@link DecoratorComponent} defined in application
 * context.
 *
 * <p>The registry is populated by looking up instances after context has completed
 * init (refreshed).
 */
public class BeanContextComponentRegistry implements ApplicationContextAware, ApplicationListener<ContextRefreshedEvent> {

    private ApplicationContext applicationContext;
    private Collection<DecoratorComponent> components = null;

    private final Logger logger = LoggerFactory.getLogger(BeanContextComponentRegistry.class.getName());

    /**
     * @return collection of all known instances of {@link DecoratorComponent}
     * in current application context.
     *
     * @throws IllegalStateException if called before application context
     * has finished initializing
     */
    public Collection<DecoratorComponent> listComponents() {
        return listComponents(c -> true);
    }

    /**
     *
     * @param filter filter for components to return
     * @return collection of instances of {@link DecoratorComponent} in current application
     * context matching the provided filter.
     *
     * @throws IllegalStateException if called before application context
     * has finished initializing
     */
    public Collection<DecoratorComponent> listComponents(Predicate<DecoratorComponent> filter) {
        if (components == null) {
            throw new IllegalStateException("Component registry has not completed its initialization");
        }

        return components.stream().filter(filter).collect(Collectors.toList());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        Collection<DecoratorComponent> beans =
                BeanFactoryUtils.beansOfTypeIncludingAncestors(
                        applicationContext,
                        DecoratorComponent.class, false, false).values();

        List<DecoratorComponent> allComponents = new ArrayList<>();

        for (DecoratorComponent component: beans) {
            String ns = component.getNamespace();
            String name = component.getName();
            if (ns == null) {
                throw new IllegalStateException("Component " + component
                        + " has invalid namespace (NULL)");
            }

            if (name == null) {
                throw new IllegalStateException("Component " + component
                        + " has invalid name (NULL)");
            }

            logger.info("Registering decorator component " + component);

            allComponents.add(component);
        }

        this.components = allComponents;
    }

}
