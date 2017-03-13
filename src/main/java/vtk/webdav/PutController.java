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

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;
import vtk.repository.ContentInputSources;
import vtk.repository.IllegalOperationException;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.ReadOnlyException;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceLockedException;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.TypeInfo;
import vtk.repository.resourcetype.PropertyType;
import vtk.util.web.HttpUtil;
import vtk.web.RequestContext;
import vtk.web.filter.UploadLimitInputStreamFilter;

/**
 * Handler for PUT requests.
 *
 * <p>Configurable JavaBean properties (in addition to those defined
 * by the {@link AbstractWebdavController superclass}):
 * <ul>
 *   <li><code>maxUploadSize</code> - optional long value specifying
 *   the maximum upload size in bytes. Default is <code>-1</code> (a
 *   negative number means no limit).
 *   <li><code>viewName</code> - the view name (default is
 *   <code>PUT</code>).
 * </ul>
 *
 */
public class PutController extends AbstractWebdavController {

    private long maxUploadSize = -1;
    private String viewName = "PUT";
    private boolean obeyClientCharacterEncoding = true;
    private boolean removeUserSpecifiedCharacterEncoding = false;
    

    public void setMaxUploadSize(long maxUploadSize) {
        if (maxUploadSize == 0) {
            throw new IllegalArgumentException(
                "Invalid upload size: " + maxUploadSize
                + " (must be a number != 0)");
        }

        this.maxUploadSize = maxUploadSize;
    }
    

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }
    

    public void setObeyClientCharacterEncoding(boolean obeyClientCharacterEncoding) {
        this.obeyClientCharacterEncoding = obeyClientCharacterEncoding;
    }
    
    public void setRemoveUserSpecifiedCharacterEncoding(boolean removeUserSpecifiedCharacterEncoding) {
        this.removeUserSpecifiedCharacterEncoding = removeUserSpecifiedCharacterEncoding;
    }
    

    public ModelAndView handleRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws Exception {
         
        if (this.maxUploadSize > 0) {
            request = new UploadLimitInputStreamFilter(this.maxUploadSize).
                filterRequest(request);
        }

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();

        Map<String, Object> model = new HashMap<String, Object>();

        try {
            /* Get the document or collection: */
            Resource resource = null;
            boolean exists = repository.exists(token, uri);

            if (exists) {
                this.logger.debug("Resource '" + uri + "' already exists");
                resource = repository.retrieve(token, uri, false);
                
                if (resource.isCollection()) {
                    if (this.logger.isDebugEnabled()) {
                        this.logger.debug("PUT to collection: CONFLICT");
                    }
                    throw new WebdavConflictException(
                        "Trying to PUT to collection resource '" + uri + "'");
                }
                InputStream inStream = request.getInputStream();
                repository.storeContent(token, resource.getURI(), ContentInputSources.fromStream(inStream));

            } else {

                /* check for parent: */
                Path parentURI = uri.getParent();                

                if (!repository.exists(token, parentURI)) {
                    if (this.logger.isDebugEnabled()) {
                        this.logger.debug("Parent " + parentURI +
                                     " does not exist. CONFLICT.");
                    }
                    throw new WebdavConflictException(
                        "Trying to PUT to non-existing resource. PUT URI was `" +
                        uri + "', parent resource '" + parentURI + "' does not exist.");
                }
                if (!allowedResourceName(uri)) {
                    model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE, HttpServletResponse.SC_OK);
                    throw new IllegalOperationException("Rejecting resource creation: '"
                                                        + uri + "'");
                }

                if (this.logger.isDebugEnabled()) {
                    this.logger.debug("Resource does not exist (creating)");
                }
                InputStream inStream = request.getInputStream();
                resource = repository.createDocument(token, uri, ContentInputSources.fromStream(inStream));
            }

            resource = repository.retrieve(token, resource.getURI(), false);
            TypeInfo typeInfo = repository.getTypeInfo(resource);
            
            boolean store = false;
            
            // XXX: userSpecifiedCharacterEncoding is a separate issue
            if (this.obeyClientCharacterEncoding) {
                String characterEncoding = request.getCharacterEncoding();
                if (characterEncoding != null) {
                    if (this.logger.isDebugEnabled()) {
                        this.logger.debug("Setting character encoding: " + characterEncoding);
                    }
                    Property prop = typeInfo.createProperty(
                            Namespace.DEFAULT_NAMESPACE, 
                            PropertyType.CHARACTERENCODING_USER_SPECIFIED_PROP_NAME);
                    prop.setStringValue(characterEncoding);
                    resource.addProperty(prop);
                    store = true;
                }
            } else if (this.removeUserSpecifiedCharacterEncoding) {
                resource.removeProperty(
                        Namespace.DEFAULT_NAMESPACE, 
                        PropertyType.CHARACTERENCODING_USER_SPECIFIED_PROP_NAME);
                store = true;
            }

            if (store) {
                resource = repository.store(token, resource);
            }

            if (exists) {
                model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE, HttpServletResponse.SC_NO_CONTENT);
            } else {
                model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE, HttpServletResponse.SC_CREATED);
            }

            model.put(WebdavConstants.WEBDAVMODEL_ETAG, resource.getEtag());
            return new ModelAndView(this.viewName, model);

        } catch (ResourceNotFoundException e) {
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE, HttpServletResponse.SC_NOT_FOUND);

        } catch (ResourceLockedException e) {
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE, HttpUtil.SC_LOCKED);
            
        } catch (IllegalOperationException e) {
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE, HttpServletResponse.SC_FORBIDDEN);

        } catch (WebdavConflictException e) {
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE, HttpServletResponse.SC_CONFLICT);

        } catch (ReadOnlyException e) {
            model.put(WebdavConstants.WEBDAVMODEL_ERROR, e);
            model.put(WebdavConstants.WEBDAVMODEL_HTTP_STATUS_CODE, HttpServletResponse.SC_FORBIDDEN);
        }
        return new ModelAndView("PUT", model);
    }
}
