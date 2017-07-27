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
package vtk.repository;

import java.util.Date;

import vtk.security.Principal;

/**
 * Represents repository resource locks.
 *
 * <p>Lock concepts is based on WebDAV locking with some extensions.
 *
 * <p>Instances of this class are immutable.
 */
public class Lock implements java.io.Serializable {

    private static final long serialVersionUID = 3546639889186633783L;

    /**
     * Type of resource lock.
     */
    public enum Type {

        /**
         * Classic exclusive locking for a single principal.
         *
         * <p>Only the lock owner will have write access to resource while
         * holding a valid exclusive lock.
         */
        EXCLUSIVE,

        /**
         * A "shared" lock for all principals with write access to the resource.
         *
         * <p>All principals which have write access to the resource through ACL will
         * be able to do write-operations while a lock of this type is present,
         * provided that a valid (shared) lock token is provided.
         */
        SHARED_ACL_WRITE;
    }

    private final Principal principal;
    private final String ownerInfo;
    private final Repository.Depth depth;
    private final Type type;
    private final Date timeout;
    private final String lockToken;
    
    public Lock(String lockToken, Principal principal, String ownerInfo, Repository.Depth depth,
                Date timeout, Type lockType) {
        this.lockToken = lockToken;
        this.principal = principal;
        this.timeout = (Date)timeout.clone();
        this.ownerInfo = ownerInfo;
        this.depth = depth;
        this.type = lockType;
    }

    public Lock(String lockToken, Principal principal, String ownerInfo, Repository.Depth depth,
                Date timeout) {
        this(lockToken, principal, ownerInfo, depth, timeout, Type.EXCLUSIVE);
    }

    public String getOwnerInfo() {
        return ownerInfo;
    }

    public Type getType() {
        return type;
    }

    public Repository.Depth getDepth() {
        return depth;
    }

    public String getLockToken() {
        return lockToken;
    }

    public Date getTimeout() {
        return (Date)timeout.clone();
    }

    public Principal getPrincipal() {
        return principal;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("{");
        sb.append("depth = ").append(depth);
        sb.append(", principal = ").append(principal);
        sb.append(", ownerInfo = ").append(ownerInfo);
        sb.append(", timeout = ").append(timeout);
        sb.append(", token = ").append(lockToken);
        sb.append(", type = ").append(type);
        sb.append("}");
        return sb.toString();
    }

}
