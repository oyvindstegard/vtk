/* Copyright (c) 2005, 2007, 2008, University of Oslo, Norway
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
package vtk.web.view.components.menu;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Service;
import vtk.web.service.ServiceUnlinkableException;
import vtk.web.service.URL;

/**
 * A reference data provider that supplies a populated {@link ListMenu}.
 *
 * <p>
 * Constructor arguments:
 * <ul>
 * <li><code>services</code> - required array of {@link Service}s to create
 * ListMenu to
 * <li><code>label</code> - required ListMenu type descriptor
 * <li> <code>modelName</code> - the name to use as model key. The default is
 * 'label', override if you have multiple list menus with the same label.
 * <li> <code>matchAssertions</code> - boolean defaults to <code>true</code>, if
 * the link construction should match assertion.
 * <li> <code>retrieveForProcessing</code> - whether or not to set the
 * <code>forProcessing</code> flag to <code>true</code> when
 * {@link Repository#retrieve retrieving} the resource from the repository
 * (default <code>false</code>).
 * </ul>
 *
 * <p>
 * Configurable JavaBean properties:
 * <ul>
 * <li><code>matchAncestorServices</code> - a boolean deciding whether or not to
 * check ancestors of the current service when checking if a menu item is
 * selected (or "active"). The default is <code>false</code> (i.e. an exact
 * service match is required).
 * </ul>
 *
 * Model data provided:
 * <ul>
 * <li><code>'modelName'</code> - a {@link ListMenu} object. A note about the <code>title</code>
 * fields of this list menu's items: It is looked up from message localization
 * using the following steps:
 * <ol>
 * <li>A message key is constructed as follows:
 * <code>[label].[serviceName].[resourceType].[contentType]</code> where
 * <code>[serviceName]</code> is the name of the service and
 * <code>[resourceType]</code> is the name of the resource's
 * {@link Resource#getResourceType resource type}. <code>[contentType]</code> is
 * the MIME type of the resource (not applicable to collections). A lookup
 * attempt is then made using this key.</li>
 * <li>If that lookup does not produce a message, the
 * <code>.[resourceType]</code> and <code>.[contentType]</code> suffices are
 * removed from the key, and the lookup is performed again, using the service
 * name as the default value.</li>
 * </ol>
 * </ul>
 *
 */
public class DefaultListMenuProvider implements ReferenceDataProvider {

    private String modelName;
    private String label;
    private Iterable<Service> services;
    private Iterable<ReferenceDataProvider> referenceDataProviders;
    private ReferenceDataProvider referenceDataProvider;
    private boolean matchAncestorServices = false;
    private boolean matchAssertions;
    private boolean retrieveForProcessing = false;

    public DefaultListMenuProvider(String label, Iterable<Service> services) {
        this(label, label, true, services);
    }

    public DefaultListMenuProvider(String label, String modelName, Iterable<Service> services) {
        this(label, modelName, true, services);
    }

    public DefaultListMenuProvider(String label, String modelName, boolean matchAssertions, Iterable<Service> services) {
        this(label, modelName, matchAssertions, services, null);
    }

    public DefaultListMenuProvider(String label, String modelName, boolean matchAssertions, Iterable<Service> services,
            Iterable<ReferenceDataProvider> referenceDataProviders) {
        if (label == null)
            throw new IllegalArgumentException("Argument 'label' cannot be null");
        if (modelName == null)
            throw new IllegalArgumentException("Argument 'modelName' cannot be null");
        if (services == null)
            throw new IllegalArgumentException("Argument 'services' cannot be null");

        this.label = label;
        this.modelName = modelName;
        this.services = services;
        this.matchAssertions = matchAssertions;
        this.referenceDataProviders = referenceDataProviders;
    }

    public DefaultListMenuProvider(String label, String modelName, boolean matchAssertions,
            Iterable<Service> services, Iterable<ReferenceDataProvider> referenceDataProviders,
            ReferenceDataProvider referenceDataProvider) {
        if (label == null)
            throw new IllegalArgumentException("Argument 'label' cannot be null");
        if (modelName == null)
            throw new IllegalArgumentException("Argument 'modelName' cannot be null");
        if (services == null)
            throw new IllegalArgumentException("Argument 'services' cannot be null");

        this.label = label;
        this.modelName = modelName;
        this.services = services;
        this.matchAssertions = matchAssertions;
        this.referenceDataProviders = referenceDataProviders;
        this.referenceDataProvider = referenceDataProvider;
    }

    public void setMatchAncestorServices(boolean matchAncestorServices) {
        this.matchAncestorServices = matchAncestorServices;
    }

    public void setRetrieveForProcessing(boolean retrieveForProcessing) {
        this.retrieveForProcessing = retrieveForProcessing;
    }

    public void referenceData(Map<String, Object> model, HttpServletRequest request) {

        ListMenu<String> menu = new ListMenu<>();
        menu.setLabel(label);

        RequestContext requestContext = RequestContext.getRequestContext();
        Principal principal = requestContext.getPrincipal();
        Repository repository = requestContext.getRepository();
        try {
            Resource resource = repository.retrieve(requestContext.getSecurityToken(),
                    requestContext.getResourceURI(), retrieveForProcessing);
            Service currentService = requestContext.getService();

            MenuItem<String> activeItem = null;

            int servicesLinkableCounts = 0;

            for (Service service : services) {
                String label = service.getName();
                String title = getTitle(resource, service, request);
                URL url = null;
                try {
                    url = service.urlConstructor(requestContext.getRequestURL())
                            .withResource(resource)
                            .withPrincipal(principal)
                            .matchAssertions(matchAssertions)
                            .constructURL();
                    servicesLinkableCounts++;
                } catch (ServiceUnlinkableException ex) {
                    // ok
                }

                MenuItem<String> item = new MenuItem<>(title);
                item.setLabel(label);
                item.setTitle(title);
                item.setUrl(url);

                if (activeItem == null && isActiveService(currentService, service)) {
                    item.setActive(true);
                    activeItem = item;
                }

                menu.addItem(item);
            }

            menu.setActiveItem(activeItem);
            model.put(modelName, menu);
            model.put(modelName + "ServicesLinkable", servicesLinkableCounts);

            if (referenceDataProviders != null) {
                for (ReferenceDataProvider provider : referenceDataProviders) {
                    provider.referenceData(model, request);
                }
            }

            if (referenceDataProvider != null) {
                referenceDataProvider.referenceData(model, request);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private String getTitle(Resource resource, Service service, HttpServletRequest request) {
        String title;

        Object attr = service.getAttribute("ListMenu.titleResolver");
        if (attr != null && (attr instanceof ListMenuTitleResolver)) {
            ListMenuTitleResolver resolver = (ListMenuTitleResolver) attr;
            title = resolver.resolve(resource, service, request);
            if (title != null) {
                return title;
            }
        }

        org.springframework.web.servlet.support.RequestContext springContext =
                new org.springframework.web.servlet.support.RequestContext(request);
        String name = service.getName();

        String messageCode = label + "." + name;
        title = springContext.getMessage(messageCode, name);

        messageCode += "." + resource.getResourceType();
        title = springContext.getMessage(messageCode, title);

        if (!resource.isCollection()) {
            messageCode += "." + resource.getContentType();
            title = springContext.getMessage(messageCode, title);
        }

        return title;
    }

    /**
     * Checks whether a service is "active" (that is, the current service of the
     * request is either the same as, or a descendant of this service),
     * depending on the value of <code>matchAncestorServices</code>.
     *
     * @param currentService
     *            the current service of the request
     * @param service
     *            the service to check for
     * @return if the service is active.
     */
    private boolean isActiveService(Service currentService, Service service) {

        if (matchAncestorServices) {
            Service s = currentService;
            while (s != null) {
                if (service == s) {
                    return true;
                }
                s = s.getParent();
            }
            return false;
        }
        return (service == currentService);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "(" + modelName + "," + Arrays.asList(services) + ")";
    }

}
