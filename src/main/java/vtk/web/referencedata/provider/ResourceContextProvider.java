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
package vtk.web.referencedata.provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

import vtk.repository.Repository;
import vtk.repository.RepositoryException;
import vtk.repository.Resource;
import vtk.repository.Revision;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Service;
import vtk.web.service.ServiceUnlinkableException;

/**
 * Standard resource context model builder. Creates a model map with "standard"
 * model data for the current resource.
 * 
 * <p>
 * Configurable properties:
 * <ul>
 * <li><code>retrieveForProcessing</code> - boolean indicating whether to
 * retrieve resources using the <code>forProcessing</code> flag set to
 * <code>false</code> or false. The default is <code>true</code>.
 * <li><code>getResourceFromModel</code> - boolean indicating if the provider
 * should use the resource from the model provided (<code>true</code>), or get
 * it from the repository from the URI specified in the RequestContext. Default
 * is <code>false</code>.
 * <li><code>resourceFromModelKey</code> (only applicable when
 * <code>getResourceFromModel</code> is <code>true</code>) - the key to use when
 * looking up the resource from the model. Default is <code>resource</code>.
 * <li><code>modelName</code> - the name to use for the submodel (default is
 * <code>resourceContext</code>).
 * </ul>
 * 
 * <p>
 * Model data provided:
 * <ul>
 * <li><code>principal</code> - the current principal
 * <li><code>currentServiceName</code> - the name of the current service
 * <li><code>currentURI</code> - the URI of the requested resource
 * <li><code>parentURI</code> - the parent URI of the requested resource (
 * <code>null</code>) if the current resource is the root resource ('/').
 * <li><code>currentResource</code> - the requested resource
 * <li><code>repositoryId</code> - the repository id
 * </ul>
 */
public class ResourceContextProvider implements InitializingBean, ReferenceDataProvider {

    private boolean retrieveForProcessing = false;
    private boolean getResourceFromModel = false;
    private String resourceFromModelKey = "resource";
    private String modelName = "resourceContext";
    private String revisionRequestParameter = null;

    public void setRetrieveForProcessing(boolean retrieveForProcessing) {
        this.retrieveForProcessing = retrieveForProcessing;
    }

    public void setGetResourceFromModel(boolean getResourceFromModel) {
        this.getResourceFromModel = getResourceFromModel;
    }

    public void setResourceFromModelKey(String resourceFromModelKey) {
        this.resourceFromModelKey = resourceFromModelKey;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
    
    public void setRevisionRequestParameter(String revisionRequestParameter) {
        this.revisionRequestParameter = revisionRequestParameter;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.modelName == null) {
            throw new BeanInitializationException("Bean property 'modelName' must be set");
        }
        if (this.getResourceFromModel && null == this.resourceFromModelKey) {
            throw new BeanInitializationException("Bean property 'resourceFromModelKey' cannot be null when "
                    + "'getResourceFromModel' is set");
        }
    }

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {

        Map<String, Object> resourceContextModel = new HashMap<>();

        RequestContext requestContext = RequestContext.getRequestContext();
        Service currentService = requestContext.getService();
        Repository repository = requestContext.getRepository();

        Principal principal = requestContext.getPrincipal();

        Resource resource = null;
        Resource parent = null;

        if (this.revisionRequestParameter != null) {
            String revisionID = request.getParameter(this.revisionRequestParameter);
            if (revisionID != null) {
                try {
                    Revision rev = null;
                    List<Revision> revisions = repository.getRevisions(requestContext.getSecurityToken(),
                            requestContext.getResourceURI());
                    for (Revision revision : revisions) {
                        if (revisionID.equals(revision.getName())) {
                            rev = revision;
                            break;
                        }
                    }
                    if (rev != null) {
                        resource = repository.retrieve(requestContext.getSecurityToken(),
                                requestContext.getResourceURI(), this.retrieveForProcessing, rev);
                    }
                }
                catch (RepositoryException e) {
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }

            }
        }

        if (this.getResourceFromModel) {
            resource = (Resource) model.get(this.resourceFromModelKey);
        }

        if (resource == null) {
            try {
                resource = repository.retrieve(requestContext.getSecurityToken(), requestContext.getResourceURI(),
                        this.retrieveForProcessing);
            } 
            catch (RepositoryException|IOException e) { }
        }
        if (resource != null && !resource.getURI().isRoot()) {
            try {
                parent = repository.retrieve(requestContext.getSecurityToken(), requestContext.getResourceURI()
                        .getParent(), this.retrieveForProcessing);
            }
            catch (Exception e) { }
        }

        resourceContextModel.put("principal", principal);
        resourceContextModel.put("currentResource", resource);
        resourceContextModel.put("currentURI", requestContext.getResourceURI());
        resourceContextModel.put("currentCollection", requestContext.getCurrentCollection());
        if (resource != null) {
            resourceContextModel.put("parentURI", resource.getURI().getParent());
        }
        if (parent != null) {
            resourceContextModel.put("parentResource", parent);
        }

        resourceContextModel.put("currentServiceName", currentService.getName());
        try {
            resourceContextModel.put("currentServiceURL", currentService.constructURL(resource, principal));
        }
        catch (ServiceUnlinkableException e) { }
        catch (Exception e) { }

        resourceContextModel.put("repositoryId", repository.getId());
        resourceContextModel.put("requestContext", requestContext);
        resourceContextModel.put("repositoryReadOnly", repository.isReadOnly(requestContext.getResourceURI(), false));

        model.put(this.modelName, resourceContextModel);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append(" [ ");
        sb.append("modelName = ").append(this.modelName);
        sb.append(" ]");
        return sb.toString();
    }
}
