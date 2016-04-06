/* Copyright (c) 2013, University of Oslo, Norway
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

package vtk.repository.hooks;

import java.io.InputStream;
import java.util.function.Consumer;
import vtk.repository.ContentStream;
import vtk.repository.InheritablePropertiesStoreContext;
import vtk.repository.NoSuchContentException;
import vtk.repository.ResourceImpl;
import vtk.repository.SystemChangeContext;
import vtk.repository.content.ContentRepresentationRegistry;
import vtk.repository.resourcetype.Content;
import vtk.repository.store.ContentStore;

import org.springframework.beans.factory.annotation.Required;

/**
 * Default implementation of {@link TypeHandlerHooks} with no-op hooks
 * and access to {@link ContentStore content store} and
 * {@link ContentRepresentationRegistry content representation registry}.
 * Can be used to selectively override only certain hooks.
 */
public abstract class DefaultTypeHanderHooks implements TypeHandlerHooks {

    private ContentStore contentStore;
    private ContentRepresentationRegistry contentRepresentationRegistry;

    /**
     * Set the default repository {@link ContentStore}.
     * @param repositoryContentStore the content store
     */
    @Required
    public void setContentStore(ContentStore repositoryContentStore) {
        this.contentStore = repositoryContentStore;
    }

    /**
     * Set the default repository {@link ContentRepresentationRegistry}.
     * @param contentRepresentationRegistry  the content representation registry
     */
    @Required
    public void setContentRepresentationRegistry(ContentRepresentationRegistry contentRepresentationRegistry) {
        this.contentRepresentationRegistry = contentRepresentationRegistry;
    }
    
    /**
     * @return the repository default content store.
     */
    protected ContentStore getContentStore() {
        return this.contentStore;
    }
    
    /**
     * @return the repository content representation registry.
     */
    protected ContentRepresentationRegistry getContentRepresentationRegistry() {
        return this.contentRepresentationRegistry;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public Content getContentForEvaluation(ResourceImpl resource, Content defaultContent) throws Exception {
        return defaultContent;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public ResourceImpl onRetrieve(ResourceImpl resource) throws Exception {
        return resource;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public ResourceImpl[] onListChildren(ResourceImpl parent, ResourceImpl[] children) throws Exception {
        return children;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public ResourceImpl onCreateCollection(ResourceImpl newCollection) throws Exception {
        return newCollection;
    }

    /**
     * @inheritDoc
     */
    @Override
    public ResourceImpl onStore(ResourceImpl resource) throws Exception {
        return resource;
    }

    /**
     * @inheritDoc 
     */
    @Override
    public ResourceImpl onStoreSystemChange(ResourceImpl resource, 
            SystemChangeContext ctx) throws Exception {
        return resource;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public ResourceImpl onStoreInheritableProps(ResourceImpl resource, 
            InheritablePropertiesStoreContext ctx) throws Exception {
        return resource;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public ResourceImpl storeContent(ResourceImpl resource, InputStream stream, 
            String contentType, Consumer<Long> progressCallback, int progressInterval) throws Exception {
        getContentStore().storeContent(resource.getURI(), stream, progressCallback, progressInterval);
        return resource;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public InputStream getInputStream(ResourceImpl resource) throws Exception {
        return getContentStore().getInputStream(resource.getURI());
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public ContentStream onGetAlternativeContentStream(ResourceImpl resource, String contentIdentifier) 
            throws NoSuchContentException, Exception {
        throw new NoSuchContentException("No such content");
    }
    
    /**
     * {@inheritDoc }
     */
    @Override
    public ResourceImpl storeContentOnCreate(ResourceImpl resource, InputStream stream, 
            String contentType, Consumer<Long> progressCallback, int progressInterval) throws Exception {
        getContentStore().storeContent(resource.getURI(), stream, progressCallback, progressInterval);
        return resource;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void onCopy(ResourceImpl src, ResourceImpl dst) throws Exception {
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void onMove(ResourceImpl src, ResourceImpl dst) throws Exception {
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void onDelete(ResourceImpl resource, boolean restorable) throws Exception {
    }
    
}
