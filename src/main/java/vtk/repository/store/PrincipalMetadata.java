/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.repository.store;

import java.util.Map;

import vtk.security.Principal;

/**
 * Simplistic and generic interface for accessing principal metadata as
 * "attribute -> list of values"-mappings.
 * 
 * This will typically be used by {@link vtk.security.PrincipalFactory}
 * when creating {@link vtk.security.Principal} instances.
 * 
 */
public interface PrincipalMetadata extends Metadata {

    public static PrincipalMetadata defaultPrincipalMetadata(Principal principal) {
        return new PrincipalMetadataImpl(principal.getQualifiedName());
    }

    public static PrincipalMetadata defaultPrincipalMetadata(String str) {
        return new PrincipalMetadataImpl(str);
    }

    /**
     * Get the qualified name of the principal to which this instance's metadata
     * applies.
     * 
     * @see vtk.security.Principal#getQualifiedName()
     * 
     * @return The qualified name as a <code>String</code>.
     */
    public String getQualifiedName();

    /**
     * 
     * @return The user id as a <code>String</code>
     */
    public String getUid();

    public Map<String,Object> toMap();

}
