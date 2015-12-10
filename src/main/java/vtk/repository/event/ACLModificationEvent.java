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
package vtk.repository.event;

import vtk.repository.Acl;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;


public class ACLModificationEvent extends RepositoryEvent {

    private static final long serialVersionUID = 3761971570608191029L;
    
    private Resource resource = null;
    private Resource original = null;
    private Acl acl = null;
    private Acl originalACL = null;

    public ACLModificationEvent(Repository source,
                                Resource resource, Resource original) {
        super(source);
        this.resource = resource;
        this.original = original;
        this.acl = resource.getAcl();
        this.originalACL = original.getAcl();
    }

    public Resource getResource() {
        return this.resource;
    }

    @Override
    public Path getURI() {
        return this.resource.getURI();
    }

    public Resource getOriginalResource() {
        return this.original;
    }

    public Acl getACL() {
        return this.acl;
    }

    public Acl getOriginalACL() {
        return this.originalACL;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getName());
        sb.append("[");
        sb.append("source=").append(this.source);
        sb.append(";resource=").append(this.resource);
        sb.append(";original=").append(this.original);
        sb.append(";acl=").append(this.acl);
        sb.append(";originalACL=").append(this.originalACL);
        sb.append("]");
        return sb.toString();
    }
}
