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
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.ChangeLogEntry.Operation;
import vtk.repository.store.ChangeLogDao;
import vtk.repository.store.ChangeLogDao.DescendantsSpec;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;


public class ProcessedContentEventDumperOpen extends AbstractDBEventDumper {

    private ChangeLogDao changeLogDAO;

    @Override
    public void created(Resource resource, Principal principal) {
        List<ChangeLogEntry> entries = changeLogEntries(resource.getURI(), 
                Operation.CREATED,
                Optional.empty(), resource.isCollection(), new Date());
        
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
        deleted(from, principal);
    }

    @Override
    public void modified(Resource resource, Resource originalResource, Principal principal) {
        List<ChangeLogEntry> entries = changeLogEntries(resource.getURI(), Operation.MODIFIED_PROPS, Optional.empty(), resource.isCollection(), new Date());
        
        changeLogDAO.addChangeLogEntries(entries, DescendantsSpec.NONE);
    }

    @Override
    public void modifiedInheritableProperties(Resource resource, Resource originalResource, Principal principal) {
        List<ChangeLogEntry> entries = changeLogEntries(resource.getURI(), Operation.MODIFIED_PROPS, Optional.empty(), resource.isCollection(), new Date());
        
        changeLogDAO.addChangeLogEntries(entries, DescendantsSpec.SUBTREE);
    }

    @Override
    public void contentModified(Resource resource, Resource original, Principal principal) {
        List<ChangeLogEntry> entries = changeLogEntries(resource.getURI(), Operation.MODIFIED_CONTENT, Optional.empty(), resource.isCollection(), new Date());
        
        changeLogDAO.addChangeLogEntries(entries, DescendantsSpec.NONE);
    }


    @Override
    public void aclModified(Resource resource, Resource originalResource, Principal principal) {
        Acl newACL = resource.getAcl(), originalACL = originalResource.getAcl();
                            
        // Add entries only if ReadForAll state has changed
        // XXX: ACL inheritance concern moved into Resource class, so a change of the
        //     inheritance property should perhaps be a new log event type (ACL_INHERITANCE_MODIFIED)
        if (newACL.equals(originalACL) && 
                originalResource.isInheritedAcl() == resource.isInheritedAcl()) {
            
            // ACL specific resource data hasn't actually changed
        	return;
        }
        
        Privilege[] privsForAllOrig = originalACL.getPrivilegeSet(PrincipalFactory.ALL);
        Privilege[] privsForAllNew = newACL.getPrivilegeSet(PrincipalFactory.ALL);
        
        boolean origAllowsReadForAll = false;
        boolean newAllowsReadForAll = false;
        
        for (Privilege action: privsForAllOrig) {
            if (action == Privilege.READ 
                    || action == Privilege.READ_PROCESSED
                    || action == Privilege.ALL) {
                origAllowsReadForAll = true;
                break;
            }
        }
        for (Privilege action: privsForAllNew) {
            if (action == Privilege.READ 
                    || action == Privilege.READ_PROCESSED
                    || action == Privilege.ALL) {
                newAllowsReadForAll = true;
                break;
            }
        }

        if (origAllowsReadForAll == newAllowsReadForAll) {
            // ReadForAll state has not changed
            return;
        }
 
        Operation op = newAllowsReadForAll ? Operation.ACL_READ_ALL_YES : Operation.ACL_READ_ALL_NO;
        
        List<ChangeLogEntry> entries = changeLogEntries(resource.getURI(), op, ((ResourceImpl) resource).getResourceId(), resource.isCollection(), new Date());
        if (resource.isInheritedAcl()) {
            changeLogDAO.addChangeLogEntries(entries, DescendantsSpec.ACL_INHERITED_TO_INHERITANCE);
        } else {
            changeLogDAO.addChangeLogEntries(entries, DescendantsSpec.ACL_INHERITED);
        }
    }


    @Required
    public void setChangeLogDAO(ChangeLogDao changeLogDAO)  {
        this.changeLogDAO = changeLogDAO;
    }

}
