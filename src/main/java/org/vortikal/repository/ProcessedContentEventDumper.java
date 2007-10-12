/* Copyright (c) 2004, 2007, University of Oslo, Norway
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
package org.vortikal.repository;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Required;

import static org.vortikal.repository.ChangeLogEntry.Operation;

import org.vortikal.repository.store.DataAccessException;
import org.vortikal.repository.store.DataAccessor;
import org.vortikal.security.Principal;
import org.vortikal.security.PseudoPrincipal;


public class ProcessedContentEventDumper extends AbstractRepositoryEventDumper {

    private DataAccessor dataAccessor;

    public final static String CREATED = "created";
    public final static String DELETED = "deleted";
    public final static String MODIFIED_PROPS = "modified_props";
    public final static String MODIFIED_CONTENT = "modified_content";
    public final static String ACL_READ_ALL_YES = "acl_read_all_yes";
    public final static String ACL_READ_ALL_NO = "acl_read_all_no";


    @Required
    public void setDataAccessor(DataAccessor dataAccessor)  {
        this.dataAccessor = dataAccessor;
    }


    public void created(Resource resource) {
        ChangeLogEntry entry = changeLogEntry(this.loggerId, this.loggerType, resource.getURI(), 
                Operation.CREATED,
                -1, resource.isCollection(), new Date());
        
        this.dataAccessor.addChangeLogEntry(entry, true);

    }

    public void deleted(String uri, int resourceId, boolean collection) {
        ChangeLogEntry entry = changeLogEntry(this.loggerId, this.loggerType, uri, 
                Operation.DELETED,
                resourceId, collection, new Date());
        
        this.dataAccessor.addChangeLogEntry(entry, false);
    }

    public void modified(Resource resource, Resource originalResource) {
        ChangeLogEntry entry = changeLogEntry(this.loggerId, this.loggerType, resource.getURI(), 
                Operation.MODIFIED_PROPS,
                -1, resource.isCollection(), new Date());
        
        this.dataAccessor.addChangeLogEntry(entry, false);
    }


    public void contentModified(Resource resource) {
        ChangeLogEntry entry = changeLogEntry(this.loggerId, this.loggerType, resource.getURI(),
                Operation.MODIFIED_CONTENT, -1, resource.isCollection(),
                new Date());
        
        this.dataAccessor.addChangeLogEntry(entry, false);
    }


    public void aclModified(Resource resource, Resource originalResource,
                            Acl originalACL, Acl newACL) {
        
        if (originalACL.equals(newACL)) {
            return;
        }
        
        /* Check if ACE (dav:all (UIO_READ_PROCESSED)) has changed:
         * XXX: WHY!?
         */

        Set principalListBefore = originalACL.getPrincipalSet(
            Privilege.READ_PROCESSED);
        Set principalListAfter = newACL.getPrincipalSet(
            Privilege.READ_PROCESSED);
           

        if (principalListBefore == null &&
            principalListAfter == null) {
            return;
        }
            
        principalListBefore = (principalListBefore == null) ?
            new HashSet() : principalListBefore;
            
        principalListAfter = (principalListAfter == null) ?
            new HashSet() : principalListAfter;

        if (principalListBefore.equals(principalListAfter)) {
            return;
        }
            
        Principal all = PseudoPrincipal.ALL;
        
        try {
            if (originalResource.isAuthorized(Privilege.READ_PROCESSED, all) &&
                resource.isAuthorized(Privilege.READ_PROCESSED, all)) {
                return;
            }
            
            Operation op = resource.isAuthorized(Privilege.READ_PROCESSED, all) ?
                    Operation.ACL_READ_ALL_YES : Operation.ACL_READ_ALL_NO;

            ChangeLogEntry entry = changeLogEntry(this.loggerId, this.loggerType, 
                    resource.getURI(), op, -1,
                    resource.isCollection(), new Date());
            
            this.dataAccessor.addChangeLogEntry(entry, false);
            
            if (resource.isCollection()) {
                
                Resource[] childResources =
                    this.dataAccessor.loadChildren(this.dataAccessor.load(resource.getURI()));
                for (int i=0; i < childResources.length; i++) {
                    entry = changeLogEntry(this.loggerId, this.loggerType, childResources[i].getURI(),
                            op, -1, childResources[i].isCollection(), new Date());
                    
                    this.dataAccessor.addChangeLogEntry(entry, false);
                }
            }
        } catch (IOException e) {
            throw new DataAccessException("Unable to authorize", e);
        }

    }

}
