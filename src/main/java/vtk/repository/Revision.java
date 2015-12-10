/* Copyright (c) 2011, University of Oslo, Norway
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

public final class Revision {

    public static enum Type {
        WORKING_COPY,
        REGULAR
    }
    
    private final long id;
    private final Type type;
    private final String name;
    private final String uid;
    private final Date timestamp;
    private final Acl acl;
    private final String checksum;
    
    private final String toString;

    public long getID() {
        return id;
    }
    
    public Type getType() {
        return type;
    }
    
    public String getName() {
        return name;
    }

    public String getUid() {
        return uid;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public Acl getAcl() {
        return acl;
    }
    
    public String getChecksum() {
        return checksum;
    }
    
    public String toString() {
        return this.toString;
    }

    private Revision(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.name = builder.name;
        this.uid = builder.uid;
        this.timestamp = builder.timestamp;
        this.acl = builder.acl;
        this.checksum = builder.checksum;
        
        StringBuilder sb = new StringBuilder("{");
        sb.append("id: ").append(this.id);
        sb.append(", name: ").append(this.name);
        sb.append(", uid: ").append(this.uid);
        sb.append(", timestamp: ").append(this.timestamp);
        sb.append(", acl: ").append(this.acl);
        sb.append(", checksum: ").append(this.checksum);
        sb.append("}");
        this.toString = sb.toString();
    }
    
    public Builder changeBuilder() {
        Builder builder = new Builder();
        builder.id = this.id;
        builder.type = this.type;
        builder.name = this.name;
        builder.uid = this.uid;
        builder.timestamp = this.timestamp;
        builder.acl = this.acl;
        builder.checksum = this.checksum;
        return builder;
    }
    
    public static Builder newBuilder() {
        return new Builder();
    }
    
    public static final class Builder {
        private long id = -1L;
        private Type type = null;
        private String name = null;
        private String uid = null;
        private Date timestamp = null;
        private Acl acl = null;
        private String checksum = null;

        public Builder id(long id) {
            this.id = id;
            return this;
        }
        
        public Builder type(Type type) {
            this.type = type;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder uid(String uid) {
            this.uid = uid;
            return this;
        }
        
        public Builder timestamp(Date timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder acl(Acl acl) {
            this.acl = acl;
            return this;
        }
        
        public Builder checksum(String checksum) {
            this.checksum = checksum;
            return this;
        }
        
        public Revision build() {
            if (id < 0) {
                throw new IllegalStateException("id must be specified");
            }
            if (type == null) {
                throw new IllegalStateException("type must be specified");
            }
            if (name == null) {
                throw new IllegalStateException("name must be specified");
            }
            if (uid == null) {
                throw new IllegalStateException("uid must be specified");
            }
            if (timestamp == null) {
                throw new IllegalStateException("timestamp must be specified");
            }
            if (acl == null && type != Type.WORKING_COPY) {
                throw new IllegalStateException(
                        "ACL must be specified unless type is " + Type.WORKING_COPY);
            }
            if (acl != null && type == Type.WORKING_COPY) {
                throw new IllegalStateException(
                        "ACL cannot be specified for revisions of type " 
                        + Type.WORKING_COPY);
            }
            if (checksum == null) {
                throw new IllegalStateException("checksum must be specified");
            }
            return new Revision(this);
        }

    }
}
