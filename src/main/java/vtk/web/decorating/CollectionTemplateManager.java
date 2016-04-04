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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.TypeInfo;
import vtk.repository.resourcetype.ResourceTypeDefinition;
import vtk.util.io.InputSource;
import vtk.util.repository.RepositoryInputSource;


/**
 * A template manager that loads templates from a specified collection
 * in a {@link Repository content repository}.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>repository</code> - the {@link Repository content repository}
 *   <li><code>collectionName</code> - the complete path to the
 *   templates collection, e.g. <code>/foo/bar/templates</code>.
 *   <li><code>templateResourceType</code> - the {@link
 *   ResourceTypeDefinition resource type} identifying templates (all
 *   candidate templates must be of this resource type).
 * </ul>
 *
 */
public class CollectionTemplateManager implements TemplateManager, InitializingBean {

    private static Logger logger = LoggerFactory.getLogger(CollectionTemplateManager.class);

    private Repository repository;
    private String collectionName;
    private TemplateFactory templateFactory;
    private ResourceTypeDefinition templateResourceType;
    private Map<String, Template> templatesMap;
    

    public void setRepository(Repository repository) {
        this.repository = repository;
    }


    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }


    public void setTemplateFactory(TemplateFactory templateFactory) {
        this.templateFactory = templateFactory;
    }
    

    public void setTemplateResourceType(ResourceTypeDefinition templateResourceType) {
        this.templateResourceType = templateResourceType;
    }
    

    public void afterPropertiesSet() {
        if (this.repository == null) {
            throw new BeanInitializationException(
                "JavaBean property 'repository' not specified");
        }
        if (this.collectionName == null) {
            throw new BeanInitializationException(
                "JavaBean property 'collectionName' not specified");
        }
        if (this.templateFactory == null) {
            throw new BeanInitializationException(
                "JavaBean property 'templateFactory' not specified");
        }
        if (this.templateResourceType == null) {
            throw new BeanInitializationException(
                "JavaBean property 'templateResourceType' not specified");
        }
    }
    

    public Template getTemplate(String name) throws Exception {
        if (this.templatesMap == null) {
            load();
        }
        if (this.templatesMap == null) {
            return null;
        }

        Template template = this.templatesMap.get(name);

        if (logger.isDebugEnabled()) {
            logger.debug("Resolved name '" + name + "' to template '"
                         + template + "'");
        }
        return template;
    }


    private void loadRecursively(Resource r, Set<Resource> result) throws Exception {
        TypeInfo type = this.repository.getTypeInfo(r);
        if (type.isOfType(this.templateResourceType)) {
            result.add(r);
        }
        if (r.isCollection()) {
            try {
                Resource[] children =
                    this.repository.listChildren(null, r.getURI(), true);
                for (Resource child : children) {
                    loadRecursively(child, result);
                }
            } catch (Throwable t) { }
        }
    }
    
    public synchronized void load() throws Exception {

        Map<String, Template> map = new HashMap<String, Template>();

        Path uri = Path.fromString(this.collectionName);
        Resource base = this.repository.retrieve(null, uri, true);
        Set<Resource> templatesResources = new HashSet<Resource>();
        loadRecursively(base, templatesResources);

        int numTemplates = 0;

        for (Resource resource: templatesResources) {

            InputSource templateSource =
                new RepositoryInputSource(resource.getURI(), this.repository, null);

            try {
                String identifier = resource.getURI().toString().substring(this.collectionName.length() + 1);
                if (logger.isDebugEnabled()) {
                    logger.debug("Attempt to load template '" + identifier + "'");
                }
                Template template = this.templateFactory.newTemplate(templateSource);

                if (logger.isDebugEnabled()) {
                    logger.debug("Loaded template '" + identifier + "'");
                }
                map.put(identifier, template);
                numTemplates++;
            } catch (Throwable t) {
                logger.info("Unable to compile template from resource " + resource, t);
            }
        }
        if (logger.isInfoEnabled()) {
            logger.info("Loaded " + numTemplates + " template(s) from collection '"
                        + this.collectionName + "'");
        }

        this.templatesMap = map;
    }
    

}


