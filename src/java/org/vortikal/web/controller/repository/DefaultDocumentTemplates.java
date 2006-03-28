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
package org.vortikal.web.controller.repository;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.ResourceNotFoundException;
import org.vortikal.util.repository.ContentTypeHelper;



/**
 * Default implementation of the {@link DocumentTemplates document
 * template listing} interface. Loads a set of (text-based) documents
 * from a configured collection in the repository, possibly recursing
 * to find templates in child collections.
 *
 * <p>Configurable properties:
 * <ul>
 *   <li><code>repository</code> - the {@link Repository content
 *   repository}
 *   <li><code>trustedToken</code> - the token to use for resource
 *   retrieval
 *   <li><code>templatesCollection</code> - the URI of the top level
 *   document templates collection
 *   <li><code>parseTopTemplates</code> - whether to include templates
 *   on the top level
 *   <li><code>parseCategoryTemplates</code> - whether to recurse into
 *   subdirectories
 * </ul>
 *
 */
public class DefaultDocumentTemplates implements DocumentTemplates, InitializingBean {

    private static Log logger = LogFactory.getLog(DefaultDocumentTemplates.class);
    
    private String templatesCollection = null;
    private Repository repository = null;
    private String trustedToken = null;

    private Map topTemplates;
    private Map categoryTemplates;
    
    private boolean parseTopTemplates = true;
    private boolean parseCategoryTemplates = true;
    
    public void setParseCategoryTemplates(boolean parseCategoryTemplates) {
        this.parseCategoryTemplates = parseCategoryTemplates;
    }
    
    public void setParseTopTemplates(boolean parseTopTemplates) {
        this.parseTopTemplates = parseTopTemplates;
    }
    
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public void setTrustedToken(String trustedToken) {
        this.trustedToken = trustedToken;
    }

    public void setTemplatesCollection(String templatesCollection) {
        this.templatesCollection = templatesCollection;
    }
    
    public Map getTopTemplates() {
        return this.topTemplates;
    }


    public Map getCategoryTemplates() {
        return this.categoryTemplates;
    }


    public void afterPropertiesSet() throws Exception {
        if (repository == null) {
            throw new BeanInitializationException(
                "Required bean property 'repository' not set.");
        }

        if (this.templatesCollection == null) {
            logger.warn("Required bean property 'templatesCollection' not set. " +
                        "Unable to supply document templates to create document service.");
        } else {

            loadTemplates();
        }
    }


    public void refresh() throws IOException {

        if (logger.isDebugEnabled()) {
            logger.debug("Refreshing document templates list");
        }

        if (this.templatesCollection == null) {
            return;
        }

        loadTemplates();
    }
    

    private void loadTemplates() throws IOException {

        if (!parseCategoryTemplates && !parseTopTemplates) return;
        
        try {
            Resource templatesCollectionResource = this.repository.retrieve(
                this.trustedToken, this.templatesCollection, true);
            
            if (!templatesCollectionResource.isCollection()) {
                logger.warn("Resource specified as 'templatesCollection': "
                            + this.templatesCollection + " is not a collection resource. "
                            + "Unable to load templates.");
                return;
            }

            Map topTemplates = null;
            Map categoryTemplates = null;

            if (this.parseTopTemplates) {
                topTemplates = findTopTemplates();
            }
            
            if (this.parseCategoryTemplates) {
                categoryTemplates = findCategoryTemplates();
            }

            this.topTemplates = topTemplates;
            this.categoryTemplates = categoryTemplates;

        } catch (ResourceNotFoundException e) {
            logger.warn(
                "Error loading document templates [templatesCollection = "
                + this.templatesCollection + ", parseTopTemplates = "
                + this.parseTopTemplates + ", parseCategoryTemplates = "
                + this.parseCategoryTemplates + "]:" + e.getMessage());
        }
    }
    


    private Map findTopTemplates() throws IOException {
        Map topTemplates = new HashMap();

        Resource[] templates = this.repository.listChildren(
            this.trustedToken, this.templatesCollection, true);

        for (int i = 0; i < templates.length; i++) {
            Resource child = templates[i];
            if (!child.isCollection()) {
                String contentType = child.getContentType();
                if (ContentTypeHelper.isTextContentType(contentType)) {
                    topTemplates.put(child.getName(), child.getURI());
                }
            }
        }
        
        if (topTemplates.size() < 1) return null;
        
        Set set = topTemplates.keySet();
        
        String[] keys = (String[]) set.toArray(
            new String[set.size()]);
        
        Arrays.sort(keys);
        Map sortedMap = new LinkedHashMap();
        
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            sortedMap.put(topTemplates.get(key), key);
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("Loaded " + sortedMap.size() + " toplevel templates");
        }

        return sortedMap;

    }


    private Map findCategoryTemplates() throws IOException {
        Resource[] templates = repository.listChildren(trustedToken, templatesCollection, true);

        Map categories = new HashMap();
        
        
        for (int i = 0; i < templates.length; i++) {
            Resource child = templates[i];
            if (child.isCollection()) {
                Map categoryTemplates = new HashMap(); 
                findTemplatesRecursively(child, categoryTemplates);
                if (categoryTemplates.size() > 0) {

                    Set set = categoryTemplates.keySet();
                    
                    String[] keys = (String[]) set.toArray(
                        new String[set.size()]);
                    
                    Arrays.sort(keys);
                    Map sortedMap = new LinkedHashMap();
                    
                    for (int j = 0; j < keys.length; j++) {
                        String key = keys[j];
                        sortedMap.put(categoryTemplates.get(key), key);
                    }
                    
                    categories.put(child.getName(), sortedMap);
                }
            }
        }
        if (categories.size() < 1) return null;
        
        if (logger.isDebugEnabled()) {
            logger.debug("Loaded " + categories.size() + " category templates");
        }

        return categories;
    }


    private void findTemplatesRecursively(Resource parent, Map templates) 
        throws IOException{

        Resource[] children = repository.listChildren(trustedToken, parent.getURI(), true);
        for (int i = 0; i < children.length; i++) {
            Resource child = children[i];
            if (!child.isCollection()) {
                String contentType = child.getContentType();
                if (ContentTypeHelper.isTextContentType(contentType)) {
                    templates.put(child.getName(), child.getURI());
                }
            } else {
                findTemplatesRecursively(child, templates);
            }
        }
    }


    
}
