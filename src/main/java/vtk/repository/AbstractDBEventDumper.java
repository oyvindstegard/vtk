/* Copyright (c) 2014,2016 University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Required;
import vtk.repository.ChangeLogEntry.Operation;

/**
 * Base class for consumers of repository events which will typically write the events
 * as {@link ChangeLogEntry changelog entries} in persistent storage.
 * 
 * <p>It provides configuration of <code>loggerIds</code> and <code>loggerType</code>
 * and a factory method to generate change log entries using these values.
 */
public abstract class AbstractDBEventDumper extends AbstractRepositoryEventDumper {

    protected List<Integer> loggerIds = Collections.emptyList();
    protected int loggerType = -1;

    /**
     * Set logger ids used to make changelog events.
     * @param loggerIds 
     */
    public void setLoggerIds(List<Integer> loggerIds) {
        Objects.requireNonNull(loggerIds, "Logger ids cannot be null");
        this.loggerIds = loggerIds;
    }
    
    public void setLoggerId(int loggerId) {
        this.loggerIds = Collections.singletonList(loggerId);
    }

    @Required
    public void setLoggerType(int loggerType) {
        this.loggerType = loggerType;
    }      
    
    /**
     * Make a list of changelog entries for each configured logger ID and the
     * configured logger type.
     * 
     * @param uri the URI of the modified resource
     * @param operation the operation performed on the resource
     * @param resourceId the resource id or <code>-1</code> if not known
     * @param collection whether the resource is or was a collection
     * @param timestamp the timestamp of the changelog events
     * @return list of changelog entries for each configured logger id, where
     * each entry in list will be identical except for the logger id
     */
    protected List<ChangeLogEntry> changeLogEntries(Path uri, Operation operation, int resourceId,
            boolean collection, Date timestamp) {
        
        List<ChangeLogEntry> entries = new ArrayList<>(loggerIds.size());
        for (int loggerId : loggerIds) {
            ChangeLogEntry entry = new ChangeLogEntry();
            entry.setLoggerId(loggerId);
            entry.setLoggerType(loggerType);
            entry.setUri(uri);
            entry.setOperation(operation);
            entry.setResourceId(resourceId);
            entry.setCollection(collection);
            entry.setTimestamp(timestamp);
            entries.add(entry);
        }

        return entries;
    }
    

}
