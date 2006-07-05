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
package org.vortikal.webdav;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.ModelAndView;
import org.vortikal.repository.Resource;
import org.vortikal.repository.ResourceLockedException;
import org.vortikal.repository.ResourceNotFoundException;
import org.vortikal.security.SecurityContext;
import org.vortikal.util.web.HttpUtil;
import org.vortikal.web.RequestContext;
import org.vortikal.webdav.ifheader.IfHeaderImpl;



/**
 * Handler for UNLOCK requests
 *
 */
public class UnlockController extends AbstractWebdavController {


    public ModelAndView handleRequest(HttpServletRequest request,
                                      HttpServletResponse response) {
         
        SecurityContext securityContext = SecurityContext.getSecurityContext();
        String token = securityContext.getToken();
        RequestContext requestContext = RequestContext.getRequestContext();
        String uri = requestContext.getResourceURI();
        Map model = new HashMap();
        try {
            this.ifHeader = new IfHeaderImpl(request);
            Resource resource = this.repository.retrieve(token, uri, false);

            verifyIfHeader(resource, false);
            
            String lockToken = getLockToken(request);
            this.logger.debug("lockToken:" + lockToken);
            this.logger.debug("resource.getLock():" + resource.getLock());
            
            if (resource.getLock() != null && !resource.getLock().getLockToken().equals(lockToken)) {
                throw new PreconditionFailedException();
            }
            
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Attempting to unlock " + uri + ", lock token: "
                             + lockToken);
            }
            this.repository.unlock(token, uri, lockToken);
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Unlocking " + uri + " succeeded");
            }

            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_OK));
            model.put(WebdavConstants.WEBDAVMODEL_ETAG, resource.getEtag());

        } catch (InvalidRequestException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Caught InvalidRequestException for URI " + uri, e);
            }
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_BAD_REQUEST));

        } catch (ResourceNotFoundException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Caught ResourceNotFoundException for URI " + uri);
            }
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_NOT_FOUND));

        } catch (PreconditionFailedException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Caught PreconditionFailedException for URI " + uri);
            }
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_PRECONDITION_FAILED));
            
        } catch (ResourceLockedException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Caught ResourceLockedException for URI " + uri);
            }
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpUtil.SC_LOCKED));

        } catch (IOException e) {
            this.logger.info("Caught IOException for URI " + uri, e);
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        }

        return new ModelAndView("UNLOCK", model);
    }
   




    protected String getLockToken(HttpServletRequest request)
        throws InvalidRequestException {

        String header = request.getHeader("Lock-Token");
        // Lock-Token Header as defined in RFC 2518:
        // Lock-Token = "Lock-Token" ":" Coded-URL
        // Coded-URL = "<" absoluteURI ">"
        
        String lockToken = header;

        if (lockToken == null || lockToken.trim().equals("")) {
            throw new InvalidRequestException("Invalid Lock-Token header: \""
                                              + header + "\"");
        }
        
        if (! (lockToken.startsWith("<") && lockToken.endsWith(">"))) {
            //throw new InvalidRequestException("Invalid Lock-Token header: \""
            //                                  + header + "\"");

            // Some clients omit "<" and ">" from their Lock-Token
            // headers, we allow that behaviour here:
            lockToken = "<" + lockToken + ">";
        }

        // Trim "<" and ">"
        lockToken = lockToken.substring(1);
        lockToken = lockToken.substring(0, lockToken.length() - 1);

        return lockToken;
    }
    
}


