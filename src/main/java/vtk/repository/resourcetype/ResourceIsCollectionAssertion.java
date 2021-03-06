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
package vtk.repository.resourcetype;

import java.util.Optional;

import vtk.repository.Resource;
import vtk.security.Principal;

/**
 * Assertion that matches on resources being collections or not. If
 * the current resource is a collection, the assertion matches,
 * otherwise not.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>invert</code> - a boolean controlling whether or not to
 *   invert the match. Default is <code>false</code>.
 * </ul>
 */
public class ResourceIsCollectionAssertion implements RepositoryAssertion {
    private boolean invert;
    
    public ResourceIsCollectionAssertion() {
        this.invert = false;
    }
    
    public ResourceIsCollectionAssertion(boolean invert) {
        this.invert = invert;
    }
    
    @Override
    public boolean matches(Optional<Resource> resource, Optional<Principal> principal) {
        if (this.invert) {
            return (resource.isPresent() && !resource.get().isCollection());
        }
        return (resource.isPresent() && resource.get().isCollection());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.invert ? "!resource.collection" : "resource.collection");
        return sb.toString();
    }

}
