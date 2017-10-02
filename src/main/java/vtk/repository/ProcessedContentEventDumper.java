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
package vtk.repository;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.ChangeLogEntry.Operation;
import vtk.repository.store.ChangeLogDao;
import vtk.repository.store.ChangeLogDao.DescendantsSpec;
import vtk.repository.store.DataAccessException;
import vtk.repository.store.DataAccessor;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;


public class ProcessedContentEventDumper extends AbstractDBEventDumper {

    private DataAccessor dataAccessor;
    private ChangeLogDao changeLogDAO;

    @Required
    public void setDataAccessor(DataAccessor dataAccessor)  {
        this.dataAccessor = dataAccessor;
    }

    @Required
    public void setChangeLogDAO(ChangeLogDao changeLogDAO)  {
        this.changeLogDAO = changeLogDAO;
    }

    @Override
    public void created(Resource resource, Principal principal) {
        List<ChangeLogEntry> entries = changeLogEntries(resource.getURI(), Operation.CREATED, Optional.empty(), resource.isCollection(), new Date());
        
        changeLogDAO.addChangeLogEntries(entries, DescendantsSpec.SUBTREE);
    }

    @Override
    public void deleted(Resource resource, Principal principal) {
        List<ChangeLogEntry> entries = changeLogEntries(resource.getURI(), Operation.DELETED, resource.getResourceId(), resource.isCollection(), new Date());
        
        changeLogDAO.addChangeLogEntries(entries, DescendantsSpec.NONE);
    }

    @Override
    public void moved(Resource resource, Resource from, Principal principal) {
        created(resource, principal);
        deleted(resource, principal);
    }

    @Override
    public void modifiedInheritableProperties(Resource resource, Resource originalResource, Principal principal) {
        // XXX recurse or not for this dumper ?
        modified(resource, originalResource, principal);
    }

    @Override
    public void modified(Resource resource, Resource originalResource, Principal principal) {
        List<ChangeLogEntry> entries = changeLogEntries(resource.getURI(), Operation.MODIFIED_PROPS, Optional.empty(), resource.isCollection(), new Date());
        
        changeLogDAO.addChangeLogEntries(entries, DescendantsSpec.NONE);
    }


    @Override
    public void contentModified(Resource resource, Resource original, Principal principal) {
        List<ChangeLogEntry> entries = changeLogEntries(resource.getURI(), Operation.MODIFIED_CONTENT, Optional.empty(), resource.isCollection(), new Date());
        
        changeLogDAO.addChangeLogEntries(entries, DescendantsSpec.NONE);
    }


    @Override
    public void aclModified(Resource resource, Resource originalResource, Principal principal) {
        Acl newACL = resource.getAcl(), originalACL = originalResource.getAcl();
        if (originalACL.equals(newACL)) {
            return;
        }
        
        /* Check if ACE (dav:all (UIO_READ_PROCESSED)) has changed:
         * XXX: WHY!?
         */

        Set<Principal> principalListBefore = originalACL.getPrincipalSet(
            Privilege.READ_PROCESSED);
        Set<Principal> principalListAfter = newACL.getPrincipalSet(
            Privilege.READ_PROCESSED);
           

        if (principalListBefore == null &&
            principalListAfter == null) {
            return;
        }
            
        principalListBefore = (principalListBefore == null) ?
            new HashSet<>() : principalListBefore;
            
        principalListAfter = (principalListAfter == null) ?
            new HashSet<>() : principalListAfter;

        if (principalListBefore.equals(principalListAfter)) {
            return;
        }
            
        Principal all = PrincipalFactory.ALL;
        
        try {
            if (originalACL.hasPrivilege(Privilege.READ_PROCESSED, all) &&
                newACL.hasPrivilege(Privilege.READ_PROCESSED, all)) {
                return;
            }
            
            Acl acl = resource.getAcl();
            Operation op = acl.hasPrivilege(Privilege.READ_PROCESSED, all) ?
                    Operation.ACL_READ_ALL_YES : Operation.ACL_READ_ALL_NO;

            List<ChangeLogEntry> entries = changeLogEntries(resource.getURI(), op, Optional.empty(), resource.isCollection(), new Date());
            
            changeLogDAO.addChangeLogEntries(entries, DescendantsSpec.NONE);
            
            if (resource.isCollection()) {
                
                Resource[] childResources =
                    this.dataAccessor.loadChildren(this.dataAccessor.load(resource.getURI()));
                for (int i=0; i < childResources.length; i++) {
                    entries = changeLogEntries(childResources[i].getURI(), op, Optional.empty(), childResources[i].isCollection(), new Date());
                    
                    changeLogDAO.addChangeLogEntries(entries, DescendantsSpec.NONE);
                }
            }
        } catch (Exception e) {
            throw new DataAccessException("Unable to authorize", e);
        }

    }
}
