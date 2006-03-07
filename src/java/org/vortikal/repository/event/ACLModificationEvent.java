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
package org.vortikal.repository.event;

import org.vortikal.repository.Ace;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;


public class ACLModificationEvent extends RepositoryEvent {

    private static final long serialVersionUID = 3761971570608191029L;
    
    private Resource resource = null;
    private Resource original = null;
    private Ace[] acl = null;
    private Ace[] originalACL = null;

    public ACLModificationEvent(Repository source, Resource resource,
        Resource original, Ace[] acl, Ace[] originalACL) {
        super(source);
        this.resource = resource;
        this.original = original;
        this.acl = acl;
        this.originalACL = originalACL;
    }

    public Resource getResource() {
        return this.resource;
    }

    public String getURI() {
        return this.resource.getURI();
    }

    public Resource getOriginal() {
        return this.original;
    }

    public Ace[] getACL() {
        return this.acl;
    }

    public Ace[] getOriginalACL() {
        return this.originalACL;
    }
}
