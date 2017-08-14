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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import vtk.repository.RepositoryImpl;
import vtk.repository.ResourceImpl;

/**
 * Assists repository with management, lookup and registration of 
 * {@link TypeHandlerHooks} beans.
 */
public class TypeHandlerHooksRegistry implements ApplicationListener<ContextRefreshedEvent> {
    
    private List<TypeHandlerHooks> typeHandlerHooks = new ArrayList<>();
    
    private final Logger logger = LoggerFactory.getLogger(RepositoryImpl.class.getName() 
            + ".TypeHandlerHooks");

    /**
     * Register a {@link TypeHandlerHook}.
     *
     * <p>This method can only be called during application initialization phase. After
     * that point, new type handler hooks cannot be registered.
     *
     * @param hooks
     */
    public void register(TypeHandlerHooks hooks) {
        if (typeHandlerHooks.contains(hooks)) {
            throw new IllegalArgumentException("Already registered: " + hooks);
        }
        typeHandlerHooks.add(hooks);
    }
    
    /**
     * Get registered type handler for resource.
     * 
     * @param r the resource
     * @return a registered type handler or <code>null</code> if no such handler
     * exists.
     */
    public TypeHandlerHooks getTypeHandlerHooks(ResourceImpl r) {
        for (TypeHandlerHooks hooks : this.typeHandlerHooks) {
            if (hooks.handleResourceType(r.getResourceType())) {
                return hooks;
            }
        }

        return null;
    }

    /**
     * Get registered type handler for a content type.
     * 
     * @param contentType the fully qualified content type (media type) specification.
     * @return a registered type handler or <code>null</code> if no such handler
     * exists.
     */
    public TypeHandlerHooks getTypeHandlerHooks(String contentType) {
        for (TypeHandlerHooks hooks : this.typeHandlerHooks) {
            if (hooks.handleCreateForContent(contentType)) {
                return hooks;
            }
        }

        return null;
    }

    /**
     * Get hooks registered for invocation upon collection creation.
     * @return a registered type handler or <code>null</code> if no such handler
     * exists.
     */
    public TypeHandlerHooks getTypeHandlerHooksForCreateCollection() {
        for (TypeHandlerHooks hooks : this.typeHandlerHooks) {
            if (hooks.handleCreateCollection()) {
                return hooks;
            }
        }
        return null;
    }


    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        typeHandlerHooks = Collections.unmodifiableList(typeHandlerHooks);
        if (!typeHandlerHooks.isEmpty()) {
            logger.info("Registered type handler hooks: " + typeHandlerHooks);
        }
    }
    
}
