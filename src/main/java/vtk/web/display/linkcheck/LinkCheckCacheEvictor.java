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
package vtk.web.display.linkcheck;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.ehcache.Ehcache;
import vtk.repository.Resource;
import vtk.repository.event.RepositoryEvent;
import vtk.repository.event.ResourceCreationEvent;
import vtk.repository.event.ResourceDeletionEvent;
import vtk.repository.event.ResourceModificationEvent;
import vtk.repository.event.ResourceMovedEvent;
import vtk.util.repository.AbstractRepositoryEventHandler;

public class LinkCheckCacheEvictor extends AbstractRepositoryEventHandler {
    private static Log logger = LogFactory.getLog(LinkCheckCacheEvictor.class);
    
    private static int ITERATE_THRESHOLD = 1000000;
    
    private Ehcache cache;

    public LinkCheckCacheEvictor(Ehcache cache) {
        this.cache = cache;
    }
    
    @Override
    public void handleEvent(RepositoryEvent event) {
        
        if (event instanceof ResourceModificationEvent) {
            ResourceModificationEvent modEvent = 
                    (ResourceModificationEvent) event;
            if (modEvent.getOriginal().isPublished() != 
                    modEvent.getResource().isPublished()) {
                evict(modEvent.getResource());
            }
        }
        else if (event instanceof ResourceDeletionEvent) {
            evict(((ResourceDeletionEvent) event).getResource());
        }
        else if (event instanceof ResourceCreationEvent) {
            evict(((ResourceCreationEvent) event).getResource());
        }
        else if (event instanceof ResourceMovedEvent) {
            evict(((ResourceMovedEvent) event).getFrom());
            evict(((ResourceMovedEvent) event).getResource());
        }
    }

    private void evict(Resource resource) {
        try {
            int size = cache.getSize();
            if (size < ITERATE_THRESHOLD) {

                String uri = vtk.web.service.URL.encode(
                        resource.getURI()).toString();

                List<String> keys = cache.getKeys();
                // Iterate through all the cache keys, because
                // EhCache does not support the search API when
                // storing to disk:
                for (String key: keys) {
                    if (key.contains(uri)) {
                        logger.debug("Evict: " + key);
                        cache.remove(key);
                    }
                }
            }
        }
        catch (Throwable t) {
            logger.warn("Failed to evict resource " + resource.getURI() 
                + " from link check cache", t);
        }
    }
    
}
