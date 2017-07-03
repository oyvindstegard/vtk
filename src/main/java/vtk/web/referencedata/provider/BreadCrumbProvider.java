/* Copyright (c) 2004, 2008, University of Oslo, Norway
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
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * Creates model data for a breadcrumb (list of URLs from the root resource to
 * the current).
 * 
 * <p>
 * Description: creates an array of {@link BreadcrumbElement} objects, which is
 * the "breadcrumb trail" from the root resource down to the collection
 * containing the current resource.
 * 
 * <p>
 * Configurable properties:
 * <ul>
 * <li><code>repository</code> - the content repository
 * <li><code>service</code> - the service for which to construct breadcrumb URLs
 * <li><code>breadcrumbName</code> - the name to publish the breadcrumb under
 * (default <code>breadcrumb</code>
 * <li><code>ignoreProperty</code> - a resource property definition specifying
 * whether to not include a given resource in the breadcrumb data model.
 * Resources are ignored when the property exists.
 * <li><code>titleOverrideProperties</code> - a {@link PropertyTypeDefinition}
 * array specifying properties that override the names of the resources in the
 * breadcrumb when present. If such a property is present on a resource, the
 * value of that property is used as the {@link BreadcrumbElement#getTitle
 * title} of the breadcrumb element.
 * <li><code>skipCurrentResource</code> - whether to skip the last element in
 * the breadcrumb. Defaults to <code>false</code>.
 * </ul>
 * 
 * <p>
 * In addition to the <code>skipCurrentResource</code> config property, this
 * component looks in the model for an entry by the name
 * <code>include-last-element</code>. If this entry exists and has the value
 * <code>true</code>, the last breadcrumb element will be included regardless of
 * the configuration.
 * 
 * <p>
 * Model data published:
 * <ul>
 * <li><code>breadcrumb</code> (or, if the property <code>breadcrumbName</code>
 * is specified, the value of that property): a {@link BreadcrumbElement} array
 * constituting the breadcrumb trail.
 * </ul>
 */
public class BreadCrumbProvider implements ReferenceDataProvider, InitializingBean {

    private Service service;
    private String breadcrumbName = "breadcrumb";
    private PropertyTypeDefinition ignoreProperty;
    private PropertyTypeDefinition[] titleOverrideProperties;
    private boolean skipCurrentResource;
    private boolean skipIndexFile;
    private PropertyTypeDefinition navigationTitlePropDef;
    private int displayFromLevel = 0;

    private final Logger logger = LoggerFactory.getLogger(BreadCrumbProvider.class);

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.isViewUnauthenticated() ? null : requestContext.getSecurityToken(); // VTK-2460
        Path uri = requestContext.getResourceURI();

        try {
            displayFromLevel = Integer.parseInt((String) model.get("display-from-level")) - 1;
        } catch (NumberFormatException e) {
            displayFromLevel = 0;
        }
        boolean skipLastElement = skipCurrentResource;
        Object includeLast = model.get("include-last-element");
        if (includeLast != null && ("true".equals(includeLast) || Boolean.TRUE.equals(includeLast))) {
            skipLastElement = false;
        }
        boolean displayServiceName = true;
        Object includeServiceName = model.get("display-services");
        if (includeServiceName != null
                && ("false".equals(includeServiceName) || Boolean.FALSE.equals(includeServiceName))) {
            displayServiceName = false;
        }

        boolean displayHidden = false;
        Object displayHiddenFromNavigation = model.get("display-folders-hidden-from-navigation");
        if (displayHiddenFromNavigation != null
                && ("true".equals(displayHiddenFromNavigation) || Boolean.TRUE.equals(displayHiddenFromNavigation))) {
            displayHidden = true;
        }

        String serviceName = null;
        if (!skipLastElement && displayServiceName) {
            try {
                Service currentService = requestContext.getService();
                Resource resource = repository.retrieve(token, uri, true);
                serviceName = currentService.getLocalizedName(resource, request);
            } catch (Exception e) {
                // Let's not fail the entire breadcrumb just
                // because we can't show the service name
                logger.error("An error occured while getting the servicename", e);
            }
        }

        List<BreadcrumbElement> breadCrumb = generateBreadcrumb(requestContext, uri, skipLastElement,
                requestContext.isIndexFile(), displayHidden, serviceName);
        model.put(breadcrumbName, breadCrumb.toArray(new BreadcrumbElement[breadCrumb.size()]));
    }

    private List<BreadcrumbElement> generateBreadcrumb(RequestContext requestContext, Path uri,
            boolean skipLastElement, boolean isIndexFile, boolean displayHidden, String serviceName) {

        List<BreadcrumbElement> breadCrumb = new ArrayList<>();
        if (uri.isRoot()) {
            return breadCrumb;
        }

        List<String> path = uri.getElements();
        List<Path> incrementalPath = uri.getPaths();

        int length = path.size();
        if (skipIndexFile && isIndexFile) {
            length--;
        }
        if (skipLastElement) {
            length--;
        }

        BreadcrumbElement serviceNameCrumb = null;
        if (serviceName != null && !"".equals(serviceName.trim())) {
            serviceNameCrumb = new BreadcrumbElement(null, serviceName, null);
        }

        Repository repository = requestContext.getRepository();
        String token = requestContext.isViewUnauthenticated() ? null : requestContext.getSecurityToken(); // VTK-2460
        Principal principal = requestContext.getPrincipal();

        for (int i = displayFromLevel; i < length; i++) {
            try {
                Resource r = repository.retrieve(token, incrementalPath.get(i), true);

                if (!displayHidden && hasIgnoreProperty(r)) {
                    continue;
                }
                String title = getTitle(requestContext, r);
                String navigationTitle = null;
                if (navigationTitlePropDef != null) {
                    navigationTitle = getNavigationTitle(r);
                }
                title = StringUtils.isBlank(navigationTitle) ? title : navigationTitle;

                URL url = service.urlConstructor(requestContext.getRequestURL())
                        .withResource(r)
                        .withPrincipal(principal)
                        .matchAssertions(false)
                        .constructURL();
                if (!skipLastElement) {
                    if (i == length - 1) {
                        if (serviceNameCrumb != null) {
                            breadCrumb.add(new BreadcrumbElement(url, title));
                            breadCrumb.add(serviceNameCrumb);
                        } else {
                            breadCrumb.add(new BreadcrumbElement(null, title, null));
                        }
                    } else {
                        breadCrumb.add(new BreadcrumbElement(url, title));
                    }
                } else {
                    breadCrumb.add(new BreadcrumbElement(url, title));
                }

            } catch (Exception e) {
                breadCrumb.add(new BreadcrumbElement(null, path.get(i)));
                String msg = e.getMessage();
                if (msg == null) {
                    msg = e.getClass().getName();
                }
            }
        }
        return breadCrumb;
    }

    private boolean hasIgnoreProperty(Resource resource) {
        if (ignoreProperty != null) {
            Property p = resource.getProperty(ignoreProperty);
            if (p != null) {
                return true;
            }
        }
        return false;
    }

    private String getTitle(RequestContext requestContext, Resource resource) {
        if (titleOverrideProperties != null && titleOverrideProperties.length > 0) {

            // Check titleOverrideProperties in correct order
            for (PropertyTypeDefinition overridePropDef : titleOverrideProperties) {
                Property property = resource.getProperty(overridePropDef);
                if (property != null && property.getStringValue() != null) {
                    return property.getStringValue();
                }
            }
        }
        if (resource.getName().equals("/")) {
            return requestContext.getRepository().getId();
        }
        return resource.getName();
    }

    private String getNavigationTitle(Resource resource) {
        Property prop = resource.getProperty(navigationTitlePropDef);
        if (prop != null) {
            return prop.getStringValue();
        }
        return null;
    }

    @Required
    public void setService(Service service) {
        this.service = service;
    }

    public void setBreadcrumbName(String breadcrumbName) {
        this.breadcrumbName = breadcrumbName;
    }

    public void setIgnoreProperty(PropertyTypeDefinition ignoreProperty) {
        this.ignoreProperty = ignoreProperty;
    }

    public void setTitleOverrideProperties(PropertyTypeDefinition[] titleOverrideProperties) {
        this.titleOverrideProperties = titleOverrideProperties;
    }

    public void setSkipIndexFile(boolean skipIndexFile) {
        this.skipIndexFile = skipIndexFile;
    }

    public void setSkipCurrentResource(boolean skipCurrentResource) {
        this.skipCurrentResource = skipCurrentResource;
    }

    public void setNavigationTitlePropDef(PropertyTypeDefinition navigationTitlePropDef) {
        this.navigationTitlePropDef = navigationTitlePropDef;
    }

    @Override
    public final void afterPropertiesSet() throws Exception {
        if (this.service == null) {
            throw new BeanInitializationException("Property 'service' not set");
        }
        if (this.breadcrumbName == null) {
            throw new BeanInitializationException("Property 'breadcrumbName' cannot be null");
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append(" [ ");
        sb.append("breadcrumbName = ").append(breadcrumbName);
        sb.append(" ]");
        return sb.toString();
    }
}
