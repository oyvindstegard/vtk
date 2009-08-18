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

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.ModelAndView;
import org.vortikal.repository.FailedDependencyException;
import org.vortikal.repository.IllegalOperationException;
import org.vortikal.repository.Path;
import org.vortikal.repository.ReadOnlyException;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.ResourceLockedException;
import org.vortikal.repository.ResourceNotFoundException;
import org.vortikal.repository.ResourceOverwriteException;
import org.vortikal.repository.Repository.Depth;
import org.vortikal.security.SecurityContext;
import org.vortikal.util.web.HttpUtil;
import org.vortikal.web.InvalidRequestException;
import org.vortikal.web.RequestContext;
import org.vortikal.webdav.ifheader.IfHeaderImpl;

/**
 * Handler for COPY requests
 *
 */
public class CopyController extends AbstractWebdavController {

    public static final String PRESERVE_ACL_HEADER = "X-Vortex-Preserve-ACL";

    /**
     * Performs the WebDAV 'COPY' method. This method recognizes a
     * custom HTTP Header: <code>X-Vortex-Preserve-ACL</code>. If this
     * header is set and has value <code>T</code>, access control
     * lists (ACLs) of the source resource will be preserved during
     * copying.
     *
     * @param request the HTTP request object
     * @param response the <code>HttpServletResponse</code> response object
     */
    public ModelAndView handleRequest(HttpServletRequest request,
                                      HttpServletResponse response) throws Exception {

        SecurityContext securityContext = SecurityContext.getSecurityContext();
        String token = securityContext.getToken();
        RequestContext requestContext = RequestContext.getRequestContext();

        Path uri = requestContext.getResourceURI();

        String destHeader = request.getHeader("Destination");
        Map<String, Object> model = new HashMap<String, Object>();

        try {
            Resource resource = this.repository.retrieve(token, uri, false);
            this.ifHeader = new IfHeaderImpl(request);
            verifyIfHeader(resource, false);

            Path destURI = mapToResourceURI(destHeader);
            String depthString = request.getHeader("Depth");
            Repository.Depth depth;
            if (depthString == null) {
                depthString = "infinity";
            }
            depthString = depthString.trim();
            if (depthString.equals("0")) {
                depth = Depth.ZERO;
            } else if (depthString.equals("1")) {
                depth = Depth.ONE;
            } else if (depthString.equals("infinity")) {
                depth = Depth.INF;
            } else {
                throw new InvalidRequestException(
                        "Invalid depth header value: " + depthString);
            }
         
            boolean overwrite = false;
            String overwriteHeader = request.getHeader("Overwrite");
            if ("T".equals(overwriteHeader)) {
                overwrite = true;
            }
         
            boolean preserveACL = false;
            String preserveACLHeader = request.getHeader(PRESERVE_ACL_HEADER);
            if ("T".equals(preserveACLHeader)) {
                preserveACL = true;
            }

            boolean existed = this.repository.exists(token, destURI);
            
            if (existed) {
                Resource destination = this.repository.retrieve(token, destURI, false);
                verifyIfHeader(destination, true);
            }

            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Copying " + uri + " to " + destURI + ", depth = "
                             + depthString + ", overwrite = " + overwrite
                             + ", preserveACL = " + preserveACL
                             + ", existed = " + existed);
            }
            this.repository.copy(token, uri, destURI, depth, overwrite, preserveACL);
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Copying " + uri + " to " + destURI + " succeeded");
            }
            
            if (existed) {
                model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                          new Integer(HttpServletResponse.SC_NO_CONTENT));
            } else {
                model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                          new Integer(HttpServletResponse.SC_CREATED));
            }

            return new ModelAndView("COPY", model);

        } catch (InvalidRequestException e) {
            this.logger.info("Caught InvalidRequestException for URI "
                         + uri);
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_BAD_REQUEST));

        } catch (IllegalOperationException e) {
            this.logger.info("Caught IllegalOperationException for URI "
                         + uri);
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_FORBIDDEN));

        } catch (ResourceOverwriteException e) {
            this.logger.info("Caught ResourceOverwriteException for URI "
                         + uri);
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_PRECONDITION_FAILED));

        } catch (FailedDependencyException e) {
            this.logger.info("Caught FailedDependencyException for URI "
                         + uri);
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_PRECONDITION_FAILED));

        } catch (ResourceLockedException e) {
            this.logger.info("Caught ResourceLockedException for URI "
                         + uri);
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpUtil.SC_LOCKED));

        } catch (ResourceNotFoundException e) {
            this.logger.info("Caught ResourceNotFoundException for URI "
                         + uri);
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_NOT_FOUND));

        } catch (ReadOnlyException e) {
            this.logger.info("Caught ReadOnlyException for URI "
                         + uri);
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_FORBIDDEN));

        } 
        return new ModelAndView("COPY", model);
    }
   
   
}



