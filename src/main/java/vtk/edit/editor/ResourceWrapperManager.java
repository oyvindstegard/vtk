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
package vtk.edit.editor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.InheritablePropertiesStoreContext;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Repository.Depth;
import vtk.repository.Resource;
import vtk.repository.ResourceWrapper;
import vtk.repository.TypeInfo;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.ResourceTypeDefinition;
import vtk.security.Principal;
import vtk.text.html.HtmlPage;
import vtk.text.html.HtmlPageFilter;
import vtk.text.html.HtmlPageParser;
import vtk.web.RequestContext;

public class ResourceWrapperManager {
    private EditablePropertyProvider editPropertyProvider = new ResourceTypeEditablePropertyProvider();
    private HtmlPageParser htmlParser;
    private HtmlPageFilter htmlPropsFilter;
    private Optional<ResourceTypeDefinition> contentResourceType = Optional.empty();
    private final static String defaultCharacterEncoding = "utf-8";
    private boolean allowInheritablePropertiesStore = false;
    private List<PropertyTypeDefinition> explicitlyStoredInheritableProperties = new ArrayList<>();

    public HtmlPageParser getHtmlParser() {
        return this.htmlParser;
    }

    public HtmlPageFilter getHtmlPropsFilter() {
        return htmlPropsFilter;
    }

    public ResourceWrapper createResourceWrapper(Resource resource) throws Exception {
        ResourceWrapper wrapper = new ResourceWrapper(this);
        populateWrapper(wrapper, resource, true);
        return wrapper;
    }

    public ResourceWrapper createResourceWrapper(Path uri) throws Exception {
        ResourceWrapper wrapper = new ResourceWrapper(this);
        populateWrapper(wrapper, uri, true);
        return wrapper;
    }

    public ResourceWrapper createResourceWrapper() throws Exception {
        Path uri = RequestContext.getRequestContext().getResourceURI();
        return createResourceWrapper(uri);
    }

    public ResourceEditWrapper createResourceEditWrapper() throws Exception {
        ResourceEditWrapper wrapper = new ResourceEditWrapper(this);
        Path uri = RequestContext.getRequestContext().getResourceURI();
        populateWrapper(wrapper, uri, false);
        return wrapper;
    }
    
    protected void populateWrapper(ResourceWrapper wrapper, Path uri, boolean forProcessing) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Resource resource = requestContext.getRepository().retrieve(token, uri, forProcessing);
        populateWrapper(wrapper, resource, forProcessing);
    }

    protected void populateWrapper(ResourceWrapper wrapper, Resource resource, boolean forProcessing) throws Exception {
        if (resource == null) {
            throw new IllegalArgumentException("Resource cannot be NULL");
        }
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        wrapper.setResource(resource);

        if (wrapper instanceof ResourceEditWrapper) {
            ResourceEditWrapper editWrapper = (ResourceEditWrapper) wrapper;
            TypeInfo type = requestContext.getRepository().getTypeInfo(resource);
            
            editWrapper.setEditProperties(editPropertyProvider.getEditProperties(resource, type));

            if (contentResourceType.isPresent() && type.isOfType(contentResourceType.get())) {
                InputStream is = requestContext.getRepository().getInputStream(token, resource.getURI(), forProcessing);
                HtmlPage content = null;

                if (resource.getCharacterEncoding() != null) {
                    // Read as default encoding (utf-8) if unsupported encoding.
                    if (Charset.isSupported(resource.getCharacterEncoding())) {
                        content = htmlParser.parse(is, resource.getCharacterEncoding());
                    }
                    else {
                        content = htmlParser.parse(is, defaultCharacterEncoding);
                    }
                }
                else {
                    content = htmlParser.parse(is, defaultCharacterEncoding);
                }
                editWrapper.setContent(content);
            }
        }
    }

    public void store(ResourceEditWrapper wrapper) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        Resource resource = wrapper.getResource();
        Repository repository = requestContext.getRepository();

        if (wrapper.isPropChange()) {
            if (allowInheritablePropertiesStore) {
                InheritablePropertiesStoreContext sc = new InheritablePropertiesStoreContext();

                for (PropertyTypeDefinition def : wrapper.getEditProperties()) {
                    if (def.isInheritable()) {
                        sc.addAffectedProperty(def);
                    }
                }

                if (explicitlyStoredInheritableProperties != null) {
                    for (PropertyTypeDefinition def : explicitlyStoredInheritableProperties) {
                        sc.addAffectedProperty(def);
                    }
                }

                if (!sc.getAffectedProperties().isEmpty()) {
                    resource = repository.store(token, resource, sc);
                }
                else {
                    resource = repository.store(token, resource);
                }

            }
            else {
                resource = repository.store(token, resource);
            }
        }

        if (wrapper.isContentChange()) {
            byte[] bytes;

            // Checks that is not a folder
            if (resource.getCharacterEncoding() != null) {
                // Store default encoding if unsupported encoding
                if (Charset.isSupported(resource.getCharacterEncoding())) {
                    bytes = wrapper.getContent().getStringRepresentation().getBytes(resource.getCharacterEncoding());
                    repository.storeContent(token, uri, new ByteArrayInputStream(bytes));

                }
                else {
                    bytes = wrapper.getContent().getStringRepresentation().getBytes(defaultCharacterEncoding);
                    repository.storeContent(token, uri, new ByteArrayInputStream(bytes));
                }
            }
            else {
                bytes = wrapper.getContent().getStringRepresentation().getBytes(defaultCharacterEncoding);
                repository.storeContent(token, uri, new ByteArrayInputStream(bytes));
            }
        }
        wrapper.setResource(resource);
    }

    @Required
    public void setHtmlParser(HtmlPageParser htmlParser) {
        this.htmlParser = htmlParser;
    }

    public void setContentResourceType(ResourceTypeDefinition contentResourceType) {
        this.contentResourceType = Optional.of(contentResourceType);
    }

    @Required
    public void setHtmlPropsFilter(HtmlPageFilter htmlPropsFilter) {
        this.htmlPropsFilter = htmlPropsFilter;
    }

    public void unlock() throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Path uri = RequestContext.getRequestContext().getResourceURI();
        requestContext.getRepository().unlock(token, uri, null);
    }

    public void lock() throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        Principal principal = requestContext.getPrincipal();
        requestContext.getRepository().lock(token, uri, principal.getQualifiedName(), Depth.ZERO, 600, null);
    }
    
    public void setEditPropertyProvider(EditablePropertyProvider editPropertyProvider) {
        this.editPropertyProvider = editPropertyProvider;
    }


    public void setAllowInheritablePropertiesStore(boolean allow) {
        this.allowInheritablePropertiesStore = allow;
    }

    public void setExplicitlyStoredInheritableProperties(
            List<PropertyTypeDefinition> explicitlyStoredInheritableProperties) {
        this.explicitlyStoredInheritableProperties = explicitlyStoredInheritableProperties;
    }

}
