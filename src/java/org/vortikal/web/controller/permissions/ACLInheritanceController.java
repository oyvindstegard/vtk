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
package org.vortikal.web.controller.permissions;


import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.servlet.mvc.SimpleFormController;
import org.vortikal.repository.Acl;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.security.SecurityContext;
import org.vortikal.web.RequestContext;
import org.vortikal.web.service.Service;


public class ACLInheritanceController extends SimpleFormController {

    private static Log logger = LogFactory.getLog(ACLInheritanceController.class);
    
    private Repository repository = null;
    
    
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    protected Object formBackingObject(HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        SecurityContext securityContext = SecurityContext.getSecurityContext();
        Service service = requestContext.getService();
        
        String uri = requestContext.getResourceURI();
        String token = securityContext.getToken();
        Resource resource = this.repository.retrieve(token, uri, false);
        Acl acl = this.repository.getACL(token, uri);
        String url = service.constructLink(resource, securityContext.getPrincipal());
         
        UpdateACLInheritanceCommand command =
            new UpdateACLInheritanceCommand(acl.isInherited(), url);
        return command;
    }


    protected void doSubmitAction(Object command) throws Exception {        
        RequestContext requestContext = RequestContext.getRequestContext();
        SecurityContext securityContext = SecurityContext.getSecurityContext();
        
        UpdateACLInheritanceCommand updateCommand =
            (UpdateACLInheritanceCommand) command;

        if (updateCommand.getCancelAction() != null) {
            updateCommand.setDone(true);
            return;
        }

        String uri = requestContext.getResourceURI();
        if ("/".equals(uri)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Not setting ACL inheritance for '/'");
            }
            return;
        }
        
        String token = securityContext.getToken();

        Acl acl = this.repository.getACL(token, uri);

        if (logger.isDebugEnabled()) {
            logger.debug("Update inheritance: input = "
                         + updateCommand.isInherited());
        }

        acl.setInherited(updateCommand.isInherited());

        if (logger.isDebugEnabled()) {
            logger.debug("Storing new acl for resource " + uri + ": " + acl);
        }
        
        this.repository.storeACL(token, uri, acl);
        updateCommand.setDone(true);
    }
    

}

