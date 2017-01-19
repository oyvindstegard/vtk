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
package vtk.web.referencedata.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.Revision;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.util.repository.RepositoryTraversal;
import vtk.util.repository.RepositoryTraversal.TraversalCallback;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Service;
import vtk.web.service.ServiceUnlinkableException;

/**
 * XXX This provider is named something generic, and as a result now contains
 *     different unrelated concepts. Split out into separate providers or re-model
 *     things.
 * 
 * Model builder that retrieves various resource detail about the current
 * resource. The information is made available in the model as a submodel of the
 * name <code>resourceDetail</code>.
 * 
 * <p>
 * Configurable properties:
 * <ul>
 * <li><code>serviceMap</code> - a <code>java.util.Map</code> containing
 * mappings between names and instances of {@link Service} objects, which will
 * be used to construct links in the resulting model.
 * </ul>
 * 
 * <p>
 * Model data provided:
 * <ul>
 * <li>a map between the keys of the configured <code>serviceMap</code>
 * parameter and the URLs that results from invoking
 * <code>Service.constructLink()</code> with the target services on the current
 * resource and principal.
 * <li>A map, named 'propertyInheritanceMap' in submodel, with keys for any inheritable
 *    properties set on resource (prefix:name), and values being paths to where the properties
 *    are inherited from.
 * <li>A boolean named 'hasWorkingCopy' in submodel, flagging if the resource
 *     as a working copy revision or not.
 * </ul>
 * 
 * <p>
 * Example: when specifying the following <code>serviceMap</code> configuration
 * parameter: <code>{foo = A, bar = B}</code>, the resulting map will be
 * <code>{foo = A', bar = B'}</code>, where <code>A'</code> and <code>B'</code>
 * are the URLs constructed using service <code>A</code> and <code>B</code>,
 * respectively.
 * 
 */
public class ResourceDetailProvider implements ReferenceDataProvider {

    private Map<String, Service> serviceMap = null;

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) throws Exception {
        Map<String, Object> resourceDetailModel = new HashMap<String, Object>();

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();

        Resource resource = null;
        try {
            resource = repository.retrieve(requestContext.getSecurityToken(), requestContext.getResourceURI(), false);
        } catch (Throwable t) {
        }

        // Detect workingcopy
        boolean hasWorkingCopy = false;
        for (Revision rev : repository.getRevisions(token, requestContext.getResourceURI())) {
            if (rev.getType() == Revision.Type.WORKING_COPY) {
                hasWorkingCopy = true;
                break;
            }
        }
        resourceDetailModel.put("hasWorkingCopy", hasWorkingCopy);

        // Resolve service links
        for (Map.Entry<String, Service> entry : this.serviceMap.entrySet()) {
            String key = entry.getKey();
            Service service = entry.getValue();

            String url = null;
            try {
                if (resource != null) {
                    url = service.constructLink(resource, requestContext.getPrincipal());
                }
            } catch (ServiceUnlinkableException e) {
                // Ignore
            }
            resourceDetailModel.put(key, url);
        }
        
        // Resolve inheritable props inheritance
        if (resource != null) {
            Map<String,Path> inheritanceMap = resolvePropertyInheritance(resource);
            resourceDetailModel.put("propertyInheritanceMap", inheritanceMap);
        } else {
            resourceDetailModel.put("propertyInheritanceMap", Collections.EMPTY_MAP);
        }
        
        model.put("resourceDetail", resourceDetailModel);
    }
    
    /**
     * Resolve from <em>where</em> each inherited property is inherited for resource.
     * 
     * @param resource
     * @return A map where keys are names of inherited properties and values
     *         are paths to the resource from which they are inherited. Inheritable
     *         properties directly set on the resource will not be part of this map.
     */
    private Map<String, Path> resolvePropertyInheritance(Resource resource) {
        if (resource.getURI().isRoot()) {
            // Nothing can be inherited for root resource except pre-configured default values,
            // which we don't provide as "source" here.
            return Collections.emptyMap();
        }
        
        final List<PropertyTypeDefinition> inheritedPropDefs = new ArrayList<PropertyTypeDefinition>();
        for (Property prop: resource) {
            if (prop.isInherited()) {
                inheritedPropDefs.add(prop.getDefinition());
            }
        }

        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        final Map<String, Path> propInheritanceMap = new HashMap<String, Path>();
        RepositoryTraversal traversal = requestContext.rootTraversal(token, resource.getURI().getParent());
        traversal.traverse(new TraversalCallback() {
            @Override
            public boolean callback(Resource resource) {
                for (Iterator<PropertyTypeDefinition> it = inheritedPropDefs.iterator(); it.hasNext();) {
                    PropertyTypeDefinition def = it.next();
                    Property prop = resource.getProperty(def);
                    if (prop != null && !prop.isInherited()) {
                        if (def.getNamespace() == Namespace.DEFAULT_NAMESPACE) {
                            propInheritanceMap.put(def.getName(), resource.getURI());
                        } else {
                            propInheritanceMap.put(def.getNamespace().getPrefix() + ":" + def.getName(), resource.getURI());
                        }
                        
                        it.remove();
                    }
                }
                
                return !inheritedPropDefs.isEmpty();
            }
            @Override
            public boolean error(Path uri, Throwable error) {
                return false;
            }
        });

        return propInheritanceMap;
    }

    @Required
    public void setServiceMap(Map<String, Service> serviceMap) {
        this.serviceMap = serviceMap;
    }
}
