/* Copyright (c) 2017, University of Oslo, Norway
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

import java.util.Objects;
import java.util.Optional;

/**
 * This class specifies how {@link Acl ACLs} are associated
 * with resources on creation (i.e. either inherited or 
 * explicitly specified).
 */
public final class AclMode {
    private Optional<Acl> acl;
    
    private AclMode(Optional<Acl> acl) {
        this.acl = Objects.requireNonNull(acl);
    }
 
    /**
     * Creates a new ACL mode which specifies that resources should 
     * inherit the ACL of the parent collection when created.
     */
    public static AclMode inherit() {
       return new AclMode(Optional.empty());
    }
    
    /**
     * Creates a new ACL mode which specifies that resources  
     * will get a specified (non-inherited) ACL when created.
     * @param acl the ACL which should be set on newly created resources
     */
    public static AclMode withAcl(Acl acl) {
       return new AclMode(Optional.of(acl));
    }

    /**
     * Gets the {@link Acl ACL} with which this ACL mode was created (if any)
     * @return an optional with the ACL of this ACL mode
     */
    public Optional<Acl> acl() {
       return acl;
    }

    /**
     * Indicates whether this ACL mode was created with the method {@link #inherited()} 
     * (i.e. if this ACL mode specifies inheritance for new resources or not)
     * @return {@code true} if this ACL mode specifies inheritance, {@code fales} otherwise
     */
    public boolean inherited() {
       return !acl.isPresent();
    }
}