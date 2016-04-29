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

import org.springframework.beans.factory.annotation.Required;

import vtk.security.Principal;
import vtk.security.SecurityContext;

/**
 * This class logs change history changes, currently to a log file.
 * If reportAll == true, all events are logged.
 * Otherwise only events thought to be important from a security perspective are logged
 * ..
 */
public class ChangeHistoryEventDumper extends AbstractDBEventDumper {

    protected final static int propertiesLastModifiedThreshold = 30;
    protected boolean reportAll = true;

    @Required
    public void setReportAll(boolean reportAll) {
        this.reportAll = reportAll;
    }

    @Override
    public void created(Resource resource) {
        if (reportAll) {
            Principal changer = SecurityContext.getSecurityContext().getPrincipal();
            logVersioningEvent("CREATED", false, resource.getURI(), 
                    "", resource.isCollection(), changer);
        }
    }
    
    @Override
    public void deleted(Resource resource) {
        if (reportAll) {
            Principal changer = SecurityContext.getSecurityContext().getPrincipal();
            logVersioningEvent("DELETED", false, resource.getURI(), 
                    "", resource.isCollection(), changer);
        }
    }

    @Override
    public void moved(Resource resource, Resource from) {
        created(resource);
        deleted(from);
    }

    @Override
    public void modifiedInheritableProperties(Resource resource, Resource originalResource) {
        // XXX should probably flag modification of inheritable properties in log, but
        //     for now, just delegate to regular modified(Resource,Resource)
        modified(resource, originalResource);
    }
    
    @Override
    public void modified(Resource resource, Resource originalResource) {
        Principal changer = SecurityContext.getSecurityContext().getPrincipal();
        boolean security = false;
        StringBuilder desc = new StringBuilder();
        if (!resource.getOwner().equals(originalResource.getOwner())) {
            security = true;
            desc.append("Owner_CHANGE: ").append(originalResource.getOwner());
            desc.append(" to: ").append(resource.getOwner()).append(" ");
        }
        if (!resource.getCreatedBy().equals(originalResource.getCreatedBy())) {
            security = true;
            desc.append("CreatedBy_CHANGE: ").append(originalResource.getCreatedBy());
            desc.append(" to: ").append(resource.getCreatedBy()).append(" ");
        }    
        if (!resource.getPropertiesModifiedBy().equals(changer)) {
            security = true;
            desc.append("PropertiesModifiedBy_CHANGE: ").append(originalResource.getPropertiesModifiedBy());
            desc.append(" to: ").append(resource.getPropertiesModifiedBy()).append(" ");
        }  
        if (!resource.getContentModifiedBy().equals(originalResource.getContentModifiedBy())) {
            // Does not happen as side effect of content change (modified event does not happen in that case)
            security = true;
            desc.append("ContentModifiedBy_CHANGE: ").append(originalResource.getContentModifiedBy());
            desc.append(" to: ").append(resource.getContentModifiedBy()).append(" ");
        }
        if (!resource.getCreationTime().equals(originalResource.getCreationTime())) {
            security = true;
            desc.append("CreationTime_CHANGE: ").append(originalResource.getCreationTime());
            desc.append(" to: ").append(resource.getCreationTime()).append(" ");
        }
        if (!resource.getContentLastModified().equals(originalResource.getContentLastModified())) {
            // Does not happen as side effect of content change (modified event does not happen in that case)
            security = true;
            desc.append("ContentLastModified_CHANGE: ").append(originalResource.getContentLastModified());
            desc.append(" to: ").append(resource.getContentLastModified()).append(" ");
        }
        Date now = new Date();
        Date threshold = new Date(now.getTime() - propertiesLastModifiedThreshold * 1000);
        Date propertiesLastModified = resource.getPropertiesLastModified();
        if ((propertiesLastModified.compareTo(now) > 0) || (propertiesLastModified.compareTo(threshold) < 0)) {
            // propertiesLastModified after now or before threshold
            security = true;
            desc.append("PropertiesLastModified_CHANGE: ").append(originalResource.getPropertiesLastModified());
            desc.append(" to: ").append(propertiesLastModified).append(" ");
        }
        if (reportAll || security) {
            logVersioningEvent("MODIFIED_PROPS", security, resource.getURI(), 
                    desc.toString(), resource.isCollection(), changer);
        }
    }

    @Override
    public void contentModified(Resource resource, Resource original) {
        if (reportAll) {
            Principal changer = SecurityContext.getSecurityContext().getPrincipal();
            logVersioningEvent("MODIFIED_CONTENT", false, resource.getURI(),
                    "", resource.isCollection(), changer);
        }
    }

    @Override
    public void aclModified(Resource resource, Resource originalResource) {
        Principal changer = SecurityContext.getSecurityContext().getPrincipal();
        String description = "ACL before " 
                + (originalResource.isInheritedAcl() ? "(inherited): " : "(not inherited): ") 
                + originalResource.getAcl() + ", ACL after " 
                + (resource.isInheritedAcl() ? "(inherited): " : "(not inherited): ") + resource.getAcl(); 
        logVersioningEvent("MODIFIED_ACL", true, resource.getURI(),
                description, resource.isCollection(), changer);
    }

    protected void logVersioningEvent(String operation, boolean security, Path uri, String description,
            boolean collection, Principal principal) {
        ChangeHistoryLog.change(operation, security, uri, description, collection, principal);

    }

}
