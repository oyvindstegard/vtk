/* Copyright (c) 2007, University of Oslo, Norway
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


/**
 * A resource changelog entry.
 */
public class ChangeLogEntry {

    /** Denotes the types of operations that a changelog entry can reflect. */
    public static enum Operation { 
        MODIFIED_ACL("modified_acl"),
        MODIFIED_CONTENT("modified_content"),
        MODIFIED_PROPS("modified_props"),
        CREATED("created"),
        DELETED("deleted"),
        ACL_READ_ALL_YES("acl_read_all_yes"),
        ACL_READ_ALL_NO ("acl_read_all_no");

        private final String operationId;
        
        Operation(String operationId) {
            this.operationId = operationId;
        }
        
        public String getOperationId() {
            return this.operationId;
        }
        
        @Override
        public String toString() {
            return this.operationId;
        }
    }

    private Operation operation;
    private Path uri;
    private int changeLogEntryId = -1;
    private int resourceId = -1;
    private Date timestamp;
    private int loggerId = -1;
    private int loggerType = -1;
    private boolean collection;
    
    public Operation getOperation() {
        return operation;
    }
    
    public void setOperation(Operation operation) {
        this.operation = operation;
    }
    
    public void setUri(Path uri) {
        this.uri = uri;
    }
    
    public Path getUri() {
        return this.uri;
    }
    
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
    
    public Date getTimestamp() {
        return this.timestamp;
    }
    
    public int getChangeLogEntryId() {
        return changeLogEntryId;
    }
    
    public void setChangeLogEntryId(int changeLogEntryId) {
        this.changeLogEntryId = changeLogEntryId;
    }
    
    public int getResourceId() {
        return resourceId;
    }
    
    public void setResourceId(int resourceId) {
        this.resourceId = resourceId;
    }
    
    public int getLoggerId() {
        return loggerId;
    }
    
    public void setLoggerId(int loggerId) {
        this.loggerId = loggerId;
    }
    
    public int getLoggerType() {
        return loggerType;
    }
    
    public void setLoggerType(int loggerType) {
        this.loggerType = loggerType;
    }
    
    public boolean isCollection() {
        return collection;
    }
    
    public void setCollection(boolean collection) {
        this.collection = collection;
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        
        builder.append("ChangeLogEntry{uri=").append(this.uri);
        builder.append(", operation=").append(this.operation);
        builder.append(", changeLogEntryId=").append(this.changeLogEntryId);
        builder.append(", resourceId=").append(this.resourceId);
        builder.append(", loggerId=").append(this.loggerId);
        builder.append(", loggerType=").append(this.loggerType);
        builder.append(", timestamp=").append(this.timestamp);
        builder.append(", is collection=").append(this.collection ? 'Y' : 'N');
        builder.append('}');
        
        return builder.toString();
    }
    
}
