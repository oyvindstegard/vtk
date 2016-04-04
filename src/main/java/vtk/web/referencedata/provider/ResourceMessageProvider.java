/* Copyright (c) 2005, University of Oslo, Norway
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

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Assertion;

/**
 * A reference data provider that puts a message in the model, based
 * on the current resource. The current resource is either determined
 * trough the {@link RequestContext}, or trough looking in the model.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>resourceInModelKey</code> - the key used when looking
 *   up the resource object in the model. If the property is left
 *   unspecified (<code>null</code>), the resource is retrieved using
 *   the {@link RequestContext}. This property supports iterated
 *   lookups (in maps only), using a dot (<code>.</code>) syntax,
 *   i.e. if the key is <code>foo.bar</code>, the model is first
 *   examined for the map of key <code>foo</code>. If that map exists,
 *   it is in turn examined for the resource of key <code>bar</code>.
 *   <li><code>localizationKey</code> - the localization key to use
 *   when looking up the message to display in the model. The
 *   resource's name is used as a parameter.
 *   <li><code>modelName</code> - the name to use for the sub-model in
 *   the main model. 
 *   <li><code>assertions</code> - an array of {@link Assertion
 *   assertions} that must match in order for the message to be
 *   provided.
 * </ul>
 *
 * <p>Model data provided:
 * <ul>
 *   <li>the localized message (in a sub-model whose name is
 *   configurable trough the <code>modelName</code> JavaBean property)
 * </ul>
 * 
 */
public class ResourceMessageProvider implements ReferenceDataProvider, InitializingBean {

    private static Logger logger = LoggerFactory.getLogger(ResourceMessageProvider.class);

    private String resourceInModelKey;
    private String localizationKey;
    private String modelName;
    private Assertion[] assertions;

    public void setResourceInModelKey(String resourceInModelKey) {
        this.resourceInModelKey = resourceInModelKey;
    }

    public void setLocalizationKey(String localizationKey) {
        this.localizationKey = localizationKey;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public void setAssertions(Assertion[] assertions) {
        this.assertions = assertions;
    }

    public void afterPropertiesSet() {
        if (this.localizationKey == null) {
            throw new BeanInitializationException(
                "JavaBean property 'localizationKey' not set");
        }
        if (this.modelName == null) {
            throw new BeanInitializationException(
                "JavaBean property 'modelName' not set");
        }
    }
    
    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request)
            throws Exception {

        Resource resource = getResource(model);
        String message = null;
        if (resource != null) {
            RequestContext requestContext = RequestContext.getRequestContext();
            Principal principal = requestContext.getPrincipal();
            boolean proceed = true;

            if (this.assertions != null) {
                for (int i = 0; i < this.assertions.length; i++) {
                    if (!this.assertions[i].matches(request, resource, principal)) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Assertion " + this.assertions[i]
                                         + " did not match for resource " + resource);
                        }
                        proceed = false;
                        break;
                    }
                }
            }

            if (proceed) {

                org.springframework.web.servlet.support.RequestContext springContext =
                    new org.springframework.web.servlet.support.RequestContext(request);
                message = springContext.getMessage(this.localizationKey,
                                                   new Object[] { resource.getName() },
                                                   this.localizationKey);
            }
        }

        model.put(this.modelName, message);
    }
    

    @SuppressWarnings("rawtypes")
    private Resource getResource(Map model) {
        Resource resource = null;

        // Try to locate the resource in the model:
        if (this.resourceInModelKey != null) {

            if (logger.isDebugEnabled()) {
                logger.debug("Trying to locate resource in model with key '"
                             + this.resourceInModelKey+ "'");
            }


            String[] accessors = this.resourceInModelKey.split("\\.");
            Map m = model;
            for (int i = 0; i < accessors.length; i++) {
                Object o = m.get(accessors[i]);
                if (o == null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Key '" + accessors[i] + "' not present in model");
                    }
                    return null;
                }
                if (i < accessors.length - 1 && (o instanceof Map)) {
                    m = (Map) o;

                } else if (i < accessors.length - 1 && ! (o instanceof Map)) {
                    return null;

                } else if (i == accessors.length - 1 && (o instanceof Resource)) {
                    return (Resource) o;
                } 
            }
            // No resource was found in model:
            return null;
        }

        try {
            RequestContext requestContext = RequestContext.getRequestContext();
            Repository repository = requestContext.getRepository();
            String token = requestContext.getSecurityToken();
            resource = repository.retrieve(token, requestContext.getResourceURI(),
                                           true);
            return resource;
        } catch (Throwable t) {
            return null;
        }
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer(this.getClass().getName());
        sb.append(" [ ");
        sb.append("modelName = ").append(this.modelName);
        sb.append(", localizationKey = ").append(this.localizationKey);
        sb.append(" ]");
        return sb.toString();
    }

}
