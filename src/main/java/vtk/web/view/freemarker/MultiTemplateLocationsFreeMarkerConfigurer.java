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
package vtk.web.view.freemarker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationContextException;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.ui.freemarker.FreeMarkerConfigurationFactory;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfig;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.ext.jsp.TaglibFactory;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;

/**
 *  JavaBean to configure FreeMarker for web usage, mirroring Spring's
 *  <code>FreeMarkerConfigurer</code>, differing only in the way
 *  template paths are resolved.
 *  
 *  This bean look up beans implementing the
 *  <code>FreeMarkerTemplateLocation</code> interface, making a
 *  <code>MultiTemplateLoader</code> containing all the found template
 *  locations.
 *  
 *  Delegates the actual configuration creation to Spring's
 *  <code>FreemarkerConfigurationFactory</code> (the super class of
 *  <code>FreeMarkerConfigurer</code>)
 *  
 *  @see FreeMarkerConfigurationFactory
 *  @see FreeMarkerConfigurer
 *  @see freemarker.cache.MultiTemplateLoader
 *  @see FreeMarkerTemplateLocation
 */
public class MultiTemplateLocationsFreeMarkerConfigurer
  implements FreeMarkerConfig, InitializingBean, ApplicationContextAware, ServletContextAware {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private ApplicationContext applicationContext;
    private Resource configLocation;
    private Properties freemarkerSettings;
    @SuppressWarnings("rawtypes")
    private Map freemarkerVariables;
    private ResourceLoader resourceLoader = new DefaultResourceLoader();
    private boolean preferFileSystemAccess = true;
    private Configuration configuration;
    private TaglibFactory taglibFactory;
    private Map<String, Object> sharedVariables;

    
    @Override
    public Configuration getConfiguration() {
        return this.configuration;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.configuration == null) {
            this.configuration = createConfiguration();
        }
    }
    
    @SuppressWarnings("rawtypes")
    public Configuration createConfiguration() throws IOException, TemplateException {
        List<TemplateLoader> loaders = new ArrayList<>();

        class LatestCompatFreeMarkerConfigurationFactory extends FreeMarkerConfigurationFactory {
            @Override
            protected Configuration newConfiguration() throws IOException, TemplateException {
                return new Configuration(Configuration.VERSION_2_3_23);
            }
        }

        LatestCompatFreeMarkerConfigurationFactory factory = new LatestCompatFreeMarkerConfigurationFactory();
        
        factory.setPreferFileSystemAccess(this.preferFileSystemAccess);
        
        factory.setResourceLoader(this.resourceLoader);

        try {
            Map templateLocations = 
                    BeanFactoryUtils.beansOfTypeIncludingAncestors(
                            this.applicationContext, FreeMarkerTemplateLocation.class, true, false);

            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Found template locations: " + templateLocations);
            }

            if (templateLocations.isEmpty()) 
                throw new BeanCreationException("Couldn't find any beans implementing " 
                        + FreeMarkerTemplateLocation.class.getName()
                        + " in the application context");
                
            for (Iterator iter = templateLocations.values().iterator(); iter.hasNext();) {
                FreeMarkerTemplateLocation location = (FreeMarkerTemplateLocation) iter.next();

                factory.setTemplateLoaderPath(location.getLocation());
                loaders.add(factory.createConfiguration().getTemplateLoader());
            }
            
            loaders.add(new ClassTemplateLoader(FreeMarkerConfigurer.class, ""));
            
            TemplateLoader[] templateLoaders = (TemplateLoader[]) loaders.toArray(
                new TemplateLoader[0]);
            MultiTemplateLoader loader = new MultiTemplateLoader(templateLoaders);

            factory = new LatestCompatFreeMarkerConfigurationFactory();
            
            if (this.configLocation != null)
                factory.setConfigLocation(this.configLocation);
            
            if (this.freemarkerSettings != null)
                factory.setFreemarkerSettings(this.freemarkerSettings);
            
            if (this.freemarkerVariables != null)
                factory.setFreemarkerVariables(this.freemarkerVariables);
            
            Configuration configuration = factory.createConfiguration();
            configuration.setTemplateLoader(loader);

            if (this.sharedVariables != null) {
                for (String name: this.sharedVariables.keySet()) {
                    Object val = this.sharedVariables.get(name);
                    configuration.setSharedVariable(name, val);
                }
            }
            return configuration;

        } catch (NoSuchBeanDefinitionException ex) {
            throw new ApplicationContextException(
                    "Must define a single FreeMarkerConfig bean in this web application context " +
                    "(may be inherited): FreeMarkerConfigurer is the usual implementation. " +
                    "This bean may be given any name.", ex);
        }
        
    
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    
    
    public void setConfigLocation(Resource resource) {   
        this.configLocation = resource;
    }

    public void setFreemarkerSettings(Properties settings) {
        this.freemarkerSettings = settings;
    }

    @SuppressWarnings("rawtypes")
    public void setFreemarkerVariables(Map variables) {
        this.freemarkerVariables = variables;
    }

    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public void setPreferFileSystemAccess(boolean preferFileSystemAccess) {
        this.preferFileSystemAccess = preferFileSystemAccess;
    }

    public void setSharedVariables(Map<String, Object> sharedVariables) {
        this.sharedVariables = sharedVariables;
    }
    

    /**
     * Initialize the {@link TaglibFactory} for the given ServletContext.
     * @param servletContext
     */
    @Override
    public void setServletContext(ServletContext servletContext) {
        this.taglibFactory = new TaglibFactory(servletContext);
    }

    @Override
    public TaglibFactory getTaglibFactory() {
        return taglibFactory;
    }

}
