/* Copyright (c) 2015, University of Oslo, Norway
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
package vtk.resourcemanagement.parser;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.StructuredResourceManager;

import java.util.ArrayList;
import java.util.List;

public class StructuredResourceSpringAdapter implements InitializingBean, ResourceLoaderAware {
    private static Log logger = LogFactory.getLog(StructuredResourceSpringAdapter.class);
    private String[] defaultTypeDefinitionFiles;
    private List<Resource> typeDefinitionFileStore = new ArrayList<>();
    private StructuredResourceManager structuredResourceManager;
    private ResourceLoader resourceLoader;

    public void afterPropertiesSet() throws Exception {
        this.parseAllAndRegister(defaultTypeDefinitionFiles);
        structuredResourceManager.registrationComplete();
    }

    public void parseAndRegister(Resource sourceFile)  throws Exception {
        if (sourceFile.exists()) {
            StructuredResourceParser parser = new StructuredResourceParser(sourceFile, resourceLoader);
            registerParsedResourceDescriptions(parser.parse());
            typeDefinitionFileStore.add(sourceFile);
        } else {
            logger.warn("Resource not found: " + sourceFile.getURI());
        }
    }

    public void parseAllAndRefresh() throws Exception {
        for (Resource resource : typeDefinitionFileStore) {
            if (resource.exists()) {
                StructuredResourceParser parser = new StructuredResourceParser(resource, resourceLoader);
                refreshParsedResourceDescriptions(parser.parse());
            } else {
                logger.warn("Resource not found: " + resource.getURI());
            }
        }
    }

    @Required
    public void setStructuredResourceManager(StructuredResourceManager structuredResourceManager) {
        this.structuredResourceManager = structuredResourceManager;
    }

    public StructuredResourceDescription getResourceDescription(String name) {
        return structuredResourceManager.get(name);
    }

    @Required
    public void setDefaultTypeDefinitionFiles(String[] defaultTypeDefinitionFiles) {
        this.defaultTypeDefinitionFiles = defaultTypeDefinitionFiles;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    private void parseAllAndRegister(String[] defaultTypeDefinitionFiles) throws Exception {
        for (String sourceFile : defaultTypeDefinitionFiles) {
            parseAndRegister(resourceLoader.getResource(sourceFile));
        }
    }

    private void refreshParsedResourceDescriptions(
            List<StructuredResourceParser.ParsedNode> parseNodes
    ) throws Exception {
        for (StructuredResourceParser.ParsedNode node : parseNodes) {
            structuredResourceManager.refresh(node.getStructuredResourceDescription());
            if (node.hasChildren()) {
                refreshParsedResourceDescriptions(node.getChildren());
            }
        }
    }

    private void registerParsedResourceDescriptions(
            List<StructuredResourceParser.ParsedNode> parseNodes
    ) throws Exception {
        for (StructuredResourceParser.ParsedNode node : parseNodes) {
            structuredResourceManager.register(node.getStructuredResourceDescription());
            if (node.hasChildren()) {
                registerParsedResourceDescriptions(node.getChildren());
            }
        }
    }
}
