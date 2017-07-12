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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.BeanInitializationException;

import vtk.repository.Acl;
import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.Repository;
import vtk.repository.RepositoryException;
import vtk.repository.Resource;
import vtk.repository.TypeInfo;
import vtk.repository.resourcetype.ResourceTypeDefinition;
import vtk.security.AuthenticationException;
import vtk.security.Principal;
import vtk.util.repository.ResourceSorter;
import vtk.util.repository.ResourceSorter.Order;
import vtk.web.ACLTooltipHelper;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Service;
import vtk.web.service.ServiceUnlinkableException;
import vtk.web.service.URL;

/**
 * Directory listing model builder. Creates a model map 'collectionListing' with
 * a list of children and associated data for the requested collection.
 * <p>
 * Configurable properties:
 * <ul>
 * <li><code>repository</code> - the repository is required
 * <li><code>retrieveForProcessing</code> - boolean indicating whether to
 * retrieve resources using the <code>forProcessing</code> flag set to
 * <code>false</code> or false. The default is <code>true</code>.
 * <li><code>linkedServices</code> - map of services providing the possible
 * operations for each child, e.g. delete,
 * <li><code>browsingService</code> - the service used for linking to the
 * children and the parent collection
 * <li><code>matchingResourceTypes</code> - set of resource types to filter
 * with.
 * <li><code>childInfoItems</code> - list of info items to be displayed for the
 * children. Valid items are <code>title</code>, <code>name</code>,
 * <code>size</code>, <code>locked</code>, <code>content-type</code>,
 * <code>owner</code> and <code>last-modified</code>. Default is
 * <code>name</code>, <code>content-length</code>, <code>last-modified</code>.
 * </ul>
 * 
 * Possible input:
 * <ul>
 * <li>
 * <code>sort-by = (name | size | locked | content-type | owner | *  last-modified)</code>
 * - default <code>name</code>
 * <li><code>invert = (true | false)</code> - default <code>false</code>
 * </ul>
 * 
 * Model data provided:
 * <ul>
 * <li><code>children</code> - list of this resource' child resources
 * <li><code>linkedServiceNames</code> - the key set for the childLinks
 * (linkedServices)
 * <li><code>childLinks</code> (Map[]) - for every child, a map of links to all
 * linkedServices specified on this ModelBuilder (with service name as key)
 * <li><code>sortByLinks</code> (Map) - links to collection listing, sorted by
 * the different configured childInfoItems
 * <li><code>childInfoItems</code> - the configured child information to support
 * sorting for
 * <li><code>sortedBy</code> - the name of the info item the child list is
 * sorted by
 * <li><code>invertedSort</code> - is the child list inverted?
 * <li><code>browsingLinks</code> - array of the links to the browsing service
 * for each child
 * <li><code>resourceURIs</code> - array of the resource URI for each child
 * (used in naming the check boxes for copy/move
 * <li><code>parentURL</code> - link to the parent collection, generated using
 * the <code>browsingService</code>. If the parent collection is the root, or
 * the user is not allowed to navigate to the parent, the URL is set to
 * <code>null</code>
 * </ul>
 */
public class CollectionListingProvider implements ReferenceDataProvider {

    public static final String DEFAULT_SORT_BY_PARAMETER = "name";

    private static final Set<String> supportedResourceColumns = new HashSet<>(Arrays.asList(new String[] {
            DEFAULT_SORT_BY_PARAMETER, "title", "content-length", "last-modified", "locked", "published",
            "permissions", "content-type", "resource-type", "owner" }));

    private Map<String, Service> linkedServices = new HashMap<>();
    private Service browsingService;
    private boolean retrieveForProcessing = false;
    private Set<ResourceTypeDefinition> matchingResourceTypes = null;
    private ACLTooltipHelper aclTooltipHelper;

    public void setBrowsingService(Service browsingService) {
        this.browsingService = browsingService;
    }

    private String[] childInfoItems = new String[] { DEFAULT_SORT_BY_PARAMETER, "content-length", "last-modified" };

    public void setLinkedServices(Map<String, Service> linkedServices) {
        this.linkedServices = linkedServices;
    }

    public void setChildInfoItems(String[] childInfoItems) {
        this.childInfoItems = childInfoItems;
    }

    public void setRetrieveForProcessing(boolean retrieveForProcessing) {
        this.retrieveForProcessing = retrieveForProcessing;
    }

    public void afterPropertiesSet() {
        if (this.browsingService == null) {
            throw new BeanInitializationException("JavaBean Property 'browsingService' must be set");
        }

        for (int i = 0; i < this.childInfoItems.length; i++) {
            String column = this.childInfoItems[i];
            if (!supportedResourceColumns.contains(column))
                throw new BeanInitializationException("JavaBean Property 'childInfoColumns' "
                        + "can only contain supported resource properties. Expected one of " + supportedResourceColumns
                        + ", not '" + column + "'");
        }
    }

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {

        Map<String, Object> collectionListingModel = new HashMap<>();
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        Principal principal = requestContext.getPrincipal();
        Repository repository = requestContext.getRepository();
        collectionListingModel.put("childInfoItems", this.childInfoItems);
        Resource[] children = null;

        try {
            Resource resource = repository.retrieve(token, uri, this.retrieveForProcessing);
            if (!resource.isCollection()) {
                // Can't do anything unless resource is a collection
                return;
            }
            children = repository.listChildren(token, uri, true);
            children = filterChildren(requestContext, children);

            // Sort children according to input parameters
            String sortBy = request.getParameter("sort-by");
            boolean invertedSort = "true".equals(request.getParameter("invert"));
            boolean validSortByParameter = false;
            for (String childInfoItem : this.childInfoItems) {
                if (childInfoItem.equals(sortBy))
                    validSortByParameter = true;
            }
            if (!validSortByParameter)
                sortBy = DEFAULT_SORT_BY_PARAMETER;
            org.springframework.web.servlet.support.RequestContext rc = new org.springframework.web.servlet.support.RequestContext(
                    request);
            this.sortChildren(children, sortBy, invertedSort, rc);
            collectionListingModel.put("sortedBy", sortBy);
            collectionListingModel.put("invertedSort", invertedSort);
            collectionListingModel.put("children", children);
            List<String> linkedServiceNames = new ArrayList<>();

            for (String linkName : this.linkedServices.keySet()) {
                linkedServiceNames.add(linkName);
            }

            collectionListingModel.put("linkedServiceNames", linkedServiceNames);

            @SuppressWarnings("unchecked")
            Map<String, String>[] childLinks = new HashMap[children.length];
            String[] permissionTooltips = new String[children.length];
            String[] browsingLinks = new String[children.length];

            for (int i = 0; i < children.length; i++) {
                Resource child = children[i];
                Map<String, String> linkMap = new HashMap<>();
                for (String linkName : this.linkedServices.keySet()) {
                    Service service = this.linkedServices.get(linkName);
                    try {
                        URL url = service.urlConstructor(URL.create(request))
                                .withResource(child)
                                .withPrincipal(principal)
                                .constructURL();
                        linkMap.put(linkName, url.toString());
                    }
                    catch (ServiceUnlinkableException e) {
                        // do nothing
                    }
                }
                childLinks[i] = linkMap;

                try {
                    URL url = browsingService.urlConstructor(URL.create(request))
                            .withResource(child)
                            .withPrincipal(principal)
                            .constructURL();
                                       // XXX: until we straighten out the manage service assertion configuration:
                    if (repository.authorize(principal, child.getAcl(), Privilege.READ)) {
                        browsingLinks[i] = url.toString();
                    }
                }
                catch (ServiceUnlinkableException e) {
                    // do nothing
                }
                if (aclTooltipHelper != null) {
                    permissionTooltips[i] = aclTooltipHelper.generateTitle(
                            child, child.getAcl(), child.isInheritedAcl(), request);
                }
            }
            collectionListingModel.put("permissionTooltips", permissionTooltips);
            collectionListingModel.put("childLinks", childLinks);
            collectionListingModel.put("browsingLinks", browsingLinks);

            Map<String, String> sortByLinks = new HashMap<>();

            for (String column : this.childInfoItems) {
                URL sortURL = browsingService.urlConstructor(URL.create(request))
                        .withResource(resource)
                        .withPrincipal(principal)
                        .constructURL();
                sortURL = sortURL.addParameter("sort-by", column);
                if (sortBy.equals(column) && !invertedSort) {
                    sortURL = sortURL.addParameter("invert", "true");
                }
                sortByLinks.put(column, sortURL.toString());
            }
            collectionListingModel.put("sortByLinks", sortByLinks);

            String parentURL = null;
            if (resource.getURI().getParent() != null) {
                try {
                    Resource parent = repository.retrieve(token, resource.getURI().getParent(), true);
                    parentURL = browsingService.urlConstructor(URL.create(request))
                        .withResource(parent)
                        .withPrincipal(principal)
                        .constructURL()
                        .toString();
                }
                catch (RepositoryException | AuthenticationException | ServiceUnlinkableException e) {
                    // Ignore
                }
            }
            collectionListingModel.put("parentURL", parentURL);

            // Check if resource has READ_WRITE_UNPUBLISHED principals
            Acl acl = resource.getAcl();
            boolean hasWriteUnpublished = false;
            if (acl != null) {
                Set<Principal> principals = acl.getPrincipalSet(Privilege.READ_WRITE_UNPUBLISHED);
                if (principals != null && !principals.isEmpty()) {
                    hasWriteUnpublished = true;

                }
            }
            model.put("hasWriteUnpublished", hasWriteUnpublished);
            model.put("collectionListing", collectionListingModel);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Resource[] filterChildren(RequestContext requestContext, Resource[] children) throws Exception {

        if (this.matchingResourceTypes == null) {
            return children;
        }
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();

        List<Resource> filteredChildren = new ArrayList<>();
        for (Resource resource : children) {
            TypeInfo type = repository.getTypeInfo(resource);
            for (ResourceTypeDefinition resourceDef : this.matchingResourceTypes) {
                if (type.isOfType(resourceDef))
                    filteredChildren.add(resource);
            }
        }
        return filteredChildren.toArray(new Resource[filteredChildren.size()]);
    }

    private void sortChildren(Resource[] children, String sortBy, boolean invert,
            org.springframework.web.servlet.support.RequestContext rc) {
        Order order = ResourceSorter.Order.BY_NAME;

        if ("title".equals(sortBy)) {
            order = ResourceSorter.Order.BY_TITLE;
        } else if ("content-length".equals(sortBy)) {
            order = ResourceSorter.Order.BY_FILESIZE;
        } else if ("last-modified".equals(sortBy)) {
            order = ResourceSorter.Order.BY_DATE;
        } else if ("locked".equals(sortBy)) {
            order = ResourceSorter.Order.BY_LOCKS;
        } else if ("content-type".equals(sortBy)) {
            order = ResourceSorter.Order.BY_CONTENT_TYPE;
        } else if ("resource-type".equals(sortBy)) {
            order = ResourceSorter.Order.BY_RESOURCE_TYPE;
        } else if ("owner".equals(sortBy)) {
            order = ResourceSorter.Order.BY_OWNER;
        } else if ("permissions".equals(sortBy)) {
            order = ResourceSorter.Order.BY_PERMISSIONS;
        } else if ("published".equals(sortBy)) {
            order = ResourceSorter.Order.BY_PUBLISHED;
        }

        ResourceSorter.sort(children, order, invert, rc);
    }

    public void setMatchingResourceTypes(Set<ResourceTypeDefinition> matchingResourceTypes) {
        this.matchingResourceTypes = matchingResourceTypes;
    }

    public void setAclTooltipHelper(ACLTooltipHelper aclTooltipHelper) {
        this.aclTooltipHelper = aclTooltipHelper;
    }

}
