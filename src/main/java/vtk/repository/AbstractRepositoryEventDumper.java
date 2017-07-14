/* Copyright (c) 2004,2016 University of Oslo, Norway
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
package vtk.repository;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationListener;

import vtk.repository.event.ACLModificationEvent;
import vtk.repository.event.ContentModificationEvent;
import vtk.repository.event.InheritablePropertiesModificationEvent;
import vtk.repository.event.RepositoryEvent;
import vtk.repository.event.ResourceCreationEvent;
import vtk.repository.event.ResourceDeletionEvent;
import vtk.repository.event.ResourceModificationEvent;
import vtk.repository.event.ResourceMovedEvent;
import vtk.security.Principal;


/**
 * Base class for consumers of repository events which delegates to 
 * specific methods based on event type.
 */
public abstract class AbstractRepositoryEventDumper implements ApplicationListener<RepositoryEvent> {

    protected Repository repository;

    @Required
    public void setRepository(Repository repository)  {
        this.repository = repository;
    }
    
    @Override
    public void onApplicationEvent(RepositoryEvent event) {

        Repository rep = event.getRepository();

        if (! rep.getId().equals(this.repository.getId())) {
            return;
        }

        if (event instanceof ResourceCreationEvent) {
            created(((ResourceCreationEvent) event).getResource(), event.getPrincipal());
        } else if (event instanceof ResourceDeletionEvent) {
            deleted(((ResourceDeletionEvent) event).getResource(), event.getPrincipal());
        } else if (event instanceof ResourceMovedEvent) {
            moved(((ResourceMovedEvent) event).getResource(),
                    ((ResourceMovedEvent) event).getFrom(), event.getPrincipal());
        } else if (event instanceof ResourceModificationEvent) {
            final Resource resource = ((ResourceModificationEvent)event).getResource();
            final Resource original = ((ResourceModificationEvent)event).getOriginal();
            if (event instanceof InheritablePropertiesModificationEvent) {
                modifiedInheritableProperties(resource, original, event.getPrincipal());
            } else {
                modified(resource, original, event.getPrincipal());
            }
        } else if (event instanceof ContentModificationEvent) {
            contentModified(((ContentModificationEvent) event).getResource(),
                            ((ContentModificationEvent) event).getOriginal(),
                            event.getPrincipal());
        } else if (event instanceof ACLModificationEvent) {
            aclModified(((ACLModificationEvent)event).getResource(),
                        ((ACLModificationEvent)event).getOriginalResource(), 
                        event.getPrincipal());
        }
    }

    public abstract void created(Resource resource, Principal principal);

    public abstract void deleted(Resource resource, Principal principal);

    public abstract void moved(Resource resource, Resource from, Principal principal);
    
    public abstract void modified(Resource resource, Resource originalResource, Principal principal);
    
    public abstract void modifiedInheritableProperties(Resource resource, Resource originalResource, Principal principal);

    public abstract void contentModified(Resource resource, Resource original, Principal principal);

    public abstract void aclModified(Resource resource, Resource originalResource, Principal principal);

}
