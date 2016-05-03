/* Copyright (c) 2009,2016 University of Oslo, Norway
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

import java.util.List;

import vtk.repository.ChangeLogEntry;

/**
 * Low level interface to changelog event storage.
 */
public interface ChangeLogDAO {
    
    /**
     * Specifies mode for generating extra events per added changelog entry.
     */
    public enum GenerateDescendantEntries {
        /**
         * No extra entries are generated, just the provided changelog entries
         * are inserted as-is.
         */
        NONE,
        /**
         * Extra entries are generated for the entire subtree of the resource
         * in changelog entry.
         */
        SUBTREE,
        /**
         * Extra entries are generated for all resources which inherit their ACL
         * from the resource in changelog entry.
         * 
         * <p>Can be used when an ACL has been set on a resource and updates for
         * all sub resources that now inherit this new ACL should be generated.
         */
        ACL_INHERITED,
        /**
         * Extra entries are generated for all resources in the subtree of the
         * resource in changelog entry that inherit their ACL from the
         * same ancestor that resource does.
         * 
         * <p>Can be used when an ACL has been removed from resource (switched to inheritance).
         * Events for all sub resources that will have their ACL affected by such
         * a change are generated.
         */
        ACL_INHERITED_TO_INHERITANCE
    }

    /**
     * Get changelog entries with the given logger type and id.
     * 
     * @param loggerType
     * @param loggerId
     * @return
     * @throws DataAccessException 
     */
    public List<ChangeLogEntry> getChangeLogEntries(int loggerType, int loggerId)
        throws DataAccessException;

    /**
     * Get changelog entries with the given logger type and id.
     * 
     * @param loggerType
     * @param loggerId
     * @param limit Limit the number of entries returned.
     * @return
     * @throws DataAccessException 
     */
    public List<ChangeLogEntry> getChangeLogEntries(int loggerType, int loggerId, int limit)
    	throws DataAccessException;

    /**
     * Remove list of changelog entries.
     * @param entries
     * @throws DataAccessException 
     */
    public void removeChangeLogEntries(List<ChangeLogEntry> entries)
        throws DataAccessException;
    
    /**
     * Add a changelog entry with spec for generating extra entries.
     * @param entry changelog entry
     * @param generate spec for how to generate extra entries
     * @throws DataAccessException 
     */
    public void addChangeLogEntry(ChangeLogEntry entry, GenerateDescendantEntries generate)
        throws DataAccessException;
    
    /**
     * Add a list of changelog entries with spec for generating extra entries
     * per changelog entry in list.
     * 
     * @param entries list of changelog entries
     * @param generate spec for hwo to generate extra entries.
     * @throws DataAccessException 
     */
    public void addChangeLogEntries(List<ChangeLogEntry> entries, GenerateDescendantEntries generate)
        throws DataAccessException;


}
