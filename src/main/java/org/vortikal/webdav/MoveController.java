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
import org.vortikal.repository.FailedDependencyException;
import org.vortikal.repository.IllegalOperationException;
import org.vortikal.repository.Path;
import org.vortikal.repository.ReadOnlyException;
import org.vortikal.repository.Resource;
import org.vortikal.repository.ResourceLockedException;
import org.vortikal.repository.ResourceNotFoundException;
import org.vortikal.repository.ResourceOverwriteException;
import org.vortikal.security.SecurityContext;
import org.vortikal.util.web.HttpUtil;
import org.vortikal.util.web.URLUtil;
import org.vortikal.web.RequestContext;
import org.vortikal.webdav.ifheader.IfHeaderImpl;

/**
 * Handler for MOVE requests
 *
 */
public class MoveController extends AbstractWebdavController {

	/**
     * Performs the WebDAV 'MOVE' method.      
     *
     * @param request the HTTP servlet request
     * @param response the HTTP servlet response
     * @return a <code>ModelAndView</code> containing the HTTP status code.
     */
    public ModelAndView handleRequest(HttpServletRequest request,
                                      HttpServletResponse response) {

        SecurityContext securityContext = SecurityContext.getSecurityContext();
        String token = securityContext.getToken();
        RequestContext requestContext = RequestContext.getRequestContext();
        Path uri = requestContext.getResourceURI();
        String dest = request.getHeader("Destination");
        Map<String, Object> model = new HashMap<String, Object>();

        try {
            Resource resource = this.repository.retrieve(token, uri, false);
            this.ifHeader = new IfHeaderImpl(request);
            verifyIfHeader(resource, true);
            
            if (dest == null || dest.trim().equals("")) {
                throw new InvalidRequestException(
                    "Missing `Destination' request header");
            }
            dest = mapToResourceURI(dest);
            try {
                dest = URLUtil.urlDecode(dest, "utf-8");
            } catch (Exception e) {
                throw new InvalidRequestException(
                    "Error trying to URL decode uri" + dest, e);
            }
            Path destURI = Path.fromString(dest);
            String depth = request.getHeader("Depth");
            if (depth == null) {
                depth = "infinity";
            }
         
            boolean overwrite = true;
            String overwriteHeader = request.getHeader("Overwrite");
            if (overwriteHeader != null && overwriteHeader.equals("F")) {
                overwrite = false;
            } 

            boolean existed = this.repository.exists(token, destURI);
            
            if (existed) {
                Resource destination = this.repository.retrieve(token, destURI, false);
                verifyIfHeader(destination, true);
            }
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Moving " + uri + " to " + dest + ", depth = "
                             + depth + ", overwrite = " + overwrite
                             + ", existed = " + existed);
            }
            this.repository.move(token, uri, destURI, overwrite);

            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Moving " + uri + " to " + dest + " succeeded");
            }

            if (existed) {
                model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                          new Integer(HttpServletResponse.SC_NO_CONTENT));
            } else {
                model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                          new Integer(HttpServletResponse.SC_CREATED));
            }

            return new ModelAndView("MOVE", model);

        } catch (InvalidRequestException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Caught InvalidRequestException for URI "
                             + uri, e);
            }            
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_BAD_REQUEST));

        } catch (IllegalOperationException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Caught IllegalOperationException for URI "
                             + uri, e);
            }
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_FORBIDDEN));

        } catch (ReadOnlyException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Caught ReadOnlyException for URI "
                             + uri, e);
            }
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_FORBIDDEN));

        } catch (FailedDependencyException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Caught FailedDependencyException for URI "
                             + uri, e);
            }
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_PRECONDITION_FAILED));

        } catch (ResourceOverwriteException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Caught ResourceOverwriteException for URI "
                             + uri, e);
            }
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_PRECONDITION_FAILED));

        } catch (ResourceLockedException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Caught ResourceLockedException for URI "
                             + uri, e);
            }
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpUtil.SC_LOCKED));

        } catch (ResourceNotFoundException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Caught ResourceNotFoundException for URI "
                             + uri, e);
            }
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_NOT_FOUND));

        } catch (IOException e) {
            this.logger.info("Caught IOException for URI " + uri, e);
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE,
                      new Integer(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        }

        return new ModelAndView("MOVE", model);
    }
   
   
}
