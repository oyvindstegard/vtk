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
package vtk.webdav;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.springframework.web.servlet.ModelAndView;
import vtk.repository.ContentInputSources;
import vtk.repository.FailedDependencyException;
import vtk.repository.IllegalOperationException;
import vtk.repository.Lock;
import vtk.repository.Path;
import vtk.repository.ReadOnlyException;
import vtk.repository.Repository;
import vtk.repository.Repository.Depth;
import vtk.repository.Resource;
import vtk.repository.ResourceLockedException;
import vtk.repository.ResourceNotFoundException;
import vtk.security.AuthenticationException;
import vtk.util.web.HttpUtil;
import vtk.web.InvalidRequestException;
import vtk.web.RequestContext;

/**
 * Handler for LOCK requests.
 * 
 */
public class LockController extends AbstractWebdavController {

    /* Value (in seconds) of infinite timeout */
    private static final int INFINITE_TIMEOUT = 410000000;

    /*
     * Max length of lock owner info string. If the actual client supplied content exceeds this
     * value, an <code>InvalidRequestException</code> will be thrown.
     */
    private static final int MAX_LOCKOWNER_INFO_LENGTH = 128;

    /**
     * Handles WebDAV LOCK method.
     * @param request
     * @param response
     * @return
     * @throws Exception 
     */
    @Override
    public ModelAndView handleRequest(HttpServletRequest request, 
            HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        Map<String, Object> model = new HashMap<String, Object>();
        Resource resource;
        
        if (requestContext.getPrincipal() == null) {
            throw new AuthenticationException(
                "A principal is required to lock resources");
        }

        String lockToken = null;
        
        try {
            String ownerInfo = requestContext.getPrincipal().toString();
            String depthString = request.getHeader("Depth");
            if (depthString == null) {
                depthString = "infinity";
            }
            depthString = depthString.toLowerCase();
            Depth depth = null;
            if ("infinity".equals(depthString)) {
                // XXX:
                depth = Depth.ZERO;
            } else if ("0".equals(depthString)) {
                depth = Depth.ZERO;
            } else if ("1".equals(depthString)) {
                depth = Depth.ZERO;
            } else {
                throw new InvalidRequestException("Invalid depth header: " + depthString);
            }
            int timeout = parseTimeoutHeader(request.getHeader("TimeOut"));
           
            boolean exists = repository.exists(token, uri);

            if (exists) {
                resource = repository.retrieve(token, uri, false);
                if (request.getContentLength() <= 0) { // -1 if not known
                    //If contentLength <= 0 we assume we want to refresh a lock
                    Lock lock = resource.getLock();
                    if (lock != null) {
                        lockToken = lock.getLockToken();
                    }
                } else {
                    Document requestBody = parseRequestBody(request);
                    validateRequest(requestBody);
                    String suppliedOwnerInfo = getLockOwner(requestBody);
                    if (suppliedOwnerInfo != null) {
                        ownerInfo = suppliedOwnerInfo;
                    }
                }
            } else {
                if (!allowedResourceName(uri)) {
                    throw new IllegalOperationException("Rejecting resource creation: '"
                                                        + uri + "'");
                }
                if (this.logger.isDebugEnabled()) {
                    this.logger.debug("Creating null resource");
                }
                repository.createDocument(token, uri, ContentInputSources.empty());
                
                // Should get real lockOwnerInfo, even if resource don't exist when locked
                if (request.getContentLength() > 0) {
                    Document requestBody = parseRequestBody(request);
                    validateRequest(requestBody);
                    String suppliedOwnerInfo = getLockOwner(requestBody);
                    if (suppliedOwnerInfo != null) {
                        ownerInfo = suppliedOwnerInfo;
                    }
                }
            }

            if (this.logger.isDebugEnabled()) {
                String msg = "Atttempting to lock " + uri + " with timeout: " + timeout
                        + " seconds, " + "depth: " + depthString;
                if (lockToken != null)
                    msg += " (refreshing with token: " + lockToken + ")";
                this.logger.debug(msg);
            }

            resource = repository.lock(token, uri, ownerInfo, depth, timeout, lockToken);

            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Locking " + uri + " succeeded");
            }

            //Resource lockedResource = repository.retrieve(token, uri, false);

            model.put(WebdavConstants.WEBDAVMODEL_REQUESTED_RESOURCE, resource);

            return new ModelAndView("LOCK", model);

        } catch (InvalidRequestException e) {
            this.logger.info("Got InvalidRequestException for URI " + uri, e);
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE, HttpServletResponse.SC_BAD_REQUEST);

        } catch (ResourceNotFoundException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Got ResourceNotFoundException for URI " + uri);
            }
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE, new Integer(
                    HttpServletResponse.SC_NOT_FOUND));

        } catch (FailedDependencyException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Got FailedDependencyException for URI " + uri, e);
            }
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE, new Integer(
                    HttpServletResponse.SC_PRECONDITION_FAILED));

        } catch (ResourceLockedException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Got ResourceLockedException for URI " + uri, e);
            }
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model
                    .put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE, new Integer(
                            HttpUtil.SC_LOCKED));

        } catch (IllegalOperationException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Got IllegalOperationException for URI " + uri, e);
            }
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE, new Integer(
                    HttpServletResponse.SC_FORBIDDEN));

        } catch (ReadOnlyException e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Got ReadOnlyException for URI " + uri, e);
            }
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE, new Integer(
                    HttpServletResponse.SC_FORBIDDEN));

        }
        return new ModelAndView("HTTP_STATUS_VIEW", model);
    }


    /**
     * Builds a JDom tree of the LOCK request body.
     * 
     * @param request
     *            the <code>HttpServletRequest</code>
     * @return an <code>org.jdom.Document</code> containing the request
     * @exception InvalidRequestException
     *                if the body does not contain valid XML
     */
    protected Document parseRequestBody(HttpServletRequest request) throws InvalidRequestException {
        try {
            SAXBuilder builder = new SAXBuilder();
            org.jdom.Document requestBody = builder.build(request.getInputStream());
            return requestBody;

        } catch (JDOMException e) {
            throw new InvalidRequestException("Invalid request body");

        } catch (IOException e) {
            throw new InvalidRequestException("Invalid request body");
        }
    }

    /**
     * Checks if a JDom tree is a valid WebDAV LOCK request body.
     * 
     * @param requestBody
     *            the <code>org.jdom.Document</code> to check
     * @exception InvalidRequestException
     *                if the request is not valid
     */
    public void validateRequest(Document requestBody) throws InvalidRequestException {
        requestBody.getRootElement();
    }

    /**
     * Gets the requested lock scope from a LOCK request body.
     * 
     * @param requestBody
     *            the request body, represented as a <code>org.jdom.Document</code> tree (is
     *            assumed to be valid)
     * @return the lock scope as a <code>String</code>
     */
    protected String getLockScope(Document requestBody) {
        Element lockInfo = requestBody.getRootElement();
        Element lockScope = lockInfo.getChild("lockscope", WebdavConstants.DAV_NAMESPACE);
        String scope = ((Element) lockScope.getChildren().get(0)).getName();
        return scope;
    }

    protected String getLockType(Document requestBody) {
        Element lockInfo = requestBody.getRootElement();
        Element lockType = lockInfo.getChild("locktype", WebdavConstants.DAV_NAMESPACE);
        String type = ((Element) lockType.getChildren().get(0)).getName();
        return type;
    }

    protected String getLockOwner(Document requestBody) throws InvalidRequestException {
        Element lockInfo = requestBody.getRootElement();
        Element lockOwner = lockInfo.getChild("owner", WebdavConstants.DAV_NAMESPACE);
        String owner = "";

        if (lockOwner == null) {
            return null;
        }

        if (lockOwner.getChildren().size() > 0) {
            Element content = (Element) lockOwner.getChildren().get(0);

            Format format = Format.getRawFormat();
            format.setOmitDeclaration(true);

            XMLOutputter outputter = new XMLOutputter(format);
            owner = outputter.outputString(content);

        } else {
            owner = lockOwner.getText();
        }

        if (owner.length() > MAX_LOCKOWNER_INFO_LENGTH) {
            throw new InvalidRequestException("Length of owner info data exceeded " + "maximum of "
                    + MAX_LOCKOWNER_INFO_LENGTH);
        }

        return owner;
    }

    protected int parseTimeoutHeader(String timeoutHeader) {

        /*
         * FIXME: Handle the 'Extend' format (see section 4.2 of RFC 2068) and multiple TimeTypes
         * (see section 9.8 of RFC 2518)
         */
        int timeout = INFINITE_TIMEOUT;

        if (timeoutHeader == null || timeoutHeader.equals("")) {
            return timeout;
        }

        if (timeoutHeader.equals("Infinite")) {
            return timeout;
        }

        if (timeoutHeader.startsWith("Extend")) {
            return timeout;
        }

        if (timeoutHeader.startsWith("Second-")) {
            try {
                String timeoutStr = timeoutHeader.substring("Second-".length(), timeoutHeader
                        .length());
                timeout = Integer.parseInt(timeoutStr);

            } catch (NumberFormatException e) {
                this.logger.warn("Invalid timeout header: " + timeoutHeader);
            }
        }
        return timeout;
    }

}
