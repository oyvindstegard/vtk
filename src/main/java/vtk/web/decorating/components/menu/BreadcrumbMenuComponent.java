/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.web.decorating.components.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import vtk.repository.AuthorizationException;
import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.AuthenticationException;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.decorating.components.DecoratorComponentException;
import vtk.web.referencedata.provider.BreadCrumbProvider;
import vtk.web.referencedata.provider.BreadcrumbElement;
import vtk.web.service.URL;
import vtk.web.view.components.menu.MenuItem;

/**
 * XXX This is NOT a simple breadcrumb menu component, as the name would
 * suggest. It creates a parent/child menu.
 */
public class BreadcrumbMenuComponent extends ListMenuComponent {

    private static final int DEFAULT_NUMBER_OF_SIBLINGS = 3000;
    private static final String PARAMETER_MAX_NUMBER_OF_SIBLINGS = "max-number-of-siblings";
    private static final String PARAMETER_MAX_NUMBER_OF_SIBLINGS_DESC = "Defines the maximum number of siblings. When this limit is"
            + " reached no siblings are going to be displayed. Default limit is: " + DEFAULT_NUMBER_OF_SIBLINGS;
    private static final String BREAD_CRUMB_MENU_PARAMETER_DISPLAY_FROM_LEVEL_DESC = "Defines the starting URI level for the menu";

    @Override
    public void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {

        int displayFromLevel = getIntegerGreaterThanZero(PARAMETER_DISPLAY_FROM_LEVEL, request, -1);
        int maxSiblings = getIntegerGreaterThanZero(PARAMETER_MAX_NUMBER_OF_SIBLINGS, request,
                DEFAULT_NUMBER_OF_SIBLINGS);

		boolean ascendingSort = true;

        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        Path uri = requestContext.getResourceURI();
        Principal principal = requestContext.getPrincipal();

        List<BreadcrumbElement> breadCrumbElements = getBreadcrumbElements(requestContext);
        Resource currentResource = null;

        currentResource = repository.retrieve(token, uri, true);

        if ((!currentResource.isCollection() && (displayFromLevel + 1) > breadCrumbElements.size())
                || (displayFromLevel > breadCrumbElements.size())) {
            return;
        }

        for (int i = 0; i < displayFromLevel; i++) {
            if (breadCrumbElements.size() > 0) {
                breadCrumbElements.remove(0);
            }
        }

        // From here on we typically load other resources than current resource
        // and must respect plainServiceMode.
        if (requestContext.isViewUnauthenticated()) {
            token = null;
            principal = null;
        }

        if (!currentResource.isCollection()) {
            try {
				currentResource = repository.retrieve(token, uri.getParent(), true);
            } catch (AuthorizationException e) {
                model.put("breadcrumb", breadCrumbElements);
                return;
            } catch (AuthenticationException e) {
                model.put("breadcrumb", breadCrumbElements);
                return;
            }
            if (breadCrumbElements.size() > 0) {
                breadCrumbElements.remove(breadCrumbElements.size() - 1);
            }
        }
        URL markedUrl = this.menuGenerator.getViewService().constructURL(currentResource, principal, false);
        breadCrumbElements.add(new BreadcrumbElement(markedUrl, getMenuTitle(currentResource)));

        // XXX: for this case currentResource will never be equal any of the
        // resources in list to generate menu from,
        // so generatemenuItemList need not check for this condition for hidden
        // ones. However, it will need to
        // do that for the sibling case in the next call below.

        List<MenuItem<PropertySet>> menuItemList = generateMenuItemList(requestContext,
                repository.listChildren(token, currentResource.getURI(), true), currentResource, principal, repository);
        // If menu is empty, i.e. current resource has no children or
        // all children were hidden, then generate menu based on siblings.
        if (menuItemList.isEmpty()) {
            Resource currentResourceParent = null;
            try {
                currentResourceParent = repository.retrieve(token, currentResource.getURI().getParent(), true);
            } catch (Exception e) {
                // Ignore
            }

            if (currentResourceParent != null) {
                menuItemList = generateMenuItemList(requestContext,
                        repository.listChildren(token, currentResourceParent.getURI(), true), currentResource,
                        principal, repository);
                breadCrumbElements.remove(breadCrumbElements.size() - 1);
                if (menuItemList.size() > maxSiblings) {
                    menuItemList = new ArrayList<MenuItem<PropertySet>>();
                    menuItemList.add(buildItem(currentResource));
                }
            }
        }

		if (currentResource.getProperty(menuGenerator.getSortDescendingPropDef()) != null) {
			if (currentResource.getProperty(menuGenerator.getSortDescendingPropDef()).getBooleanValue()) {
				ascendingSort = false;
			}
		}

		menuItemList = sortByOrder(menuItemList, request.getLocale(), ascendingSort);
        model.put("breadcrumb", breadCrumbElements);
        model.put("children", menuItemList);
        model.put("markedurl", markedUrl);
    }

    private List<BreadcrumbElement> getBreadcrumbElements(RequestContext requestContext) throws Exception {

        // XXX NO! Reconsider this. Refactor BreadCrumbProvider and create
        // separate generic class for bread crumb creation. Use this separate
        // implementation in provider and here.
        String breadcrumbName = "breadcrumb";
        BreadCrumbProvider breadCrumbProvider = new BreadCrumbProvider();
        breadCrumbProvider.setSkipCurrentResource(true);
        breadCrumbProvider.setService(this.menuGenerator.getViewService());
        breadCrumbProvider.setBreadcrumbName(breadcrumbName);
        breadCrumbProvider.setSkipIndexFile(false);
        if (!requestContext.isPreviewUnpublished()) {
            breadCrumbProvider.setIgnoreProperty(menuGenerator.getUnpublishedCollectionPropDef());
        }
        PropertyTypeDefinition titleProp[] = new PropertyTypeDefinition[2];
        titleProp[0] = this.menuGenerator.getNavigationTitlePropDef();
        titleProp[1] = this.menuGenerator.getTitlePropDef();
        breadCrumbProvider.setTitleOverrideProperties(titleProp);
        breadCrumbProvider.afterPropertiesSet();
        Map<String, Object> map = new HashMap<String, Object>();
        breadCrumbProvider.referenceData(map, RequestContext.getRequestContext().getServletRequest());
        Object o = map.get(breadcrumbName);

        if (!(o instanceof BreadcrumbElement[])) {
            throw new IllegalStateException("Expected BreadCrumbElement[] in model, found " + o);
        }

        BreadcrumbElement[] list = (BreadcrumbElement[]) o;
        List<BreadcrumbElement> result = new ArrayList<BreadcrumbElement>();
        for (int i = 0; i < list.length; i++) {
            result.add(list[i]);
        }

        return result;
    }

    private List<MenuItem<PropertySet>> generateMenuItemList(RequestContext requestContext, Resource[] resources,
            Resource currentResource, Principal principal, Repository repository)
            throws Exception {

        List<MenuItem<PropertySet>> menuItems = new ArrayList<MenuItem<PropertySet>>();
        if (!requestContext.isPreviewUnpublished()) {
            if (currentResource.getProperty(menuGenerator.getUnpublishedCollectionPropDef()) != null) {
                return menuItems;
            }
        }

        for (Resource r : resources) {
            // Filtering:
            if (!r.isCollection()) {
                continue;
            }

            if (r.getProperty(menuGenerator.getHiddenPropDef()) != null && !r.getURI().equals(currentResource.getURI())) {
                continue;
            }
            if (!requestContext.isPreviewUnpublished()) {
                if (r.getProperty(menuGenerator.getUnpublishedCollectionPropDef()) != null) {
                    continue;
                }
            }
            Path uri = currentResource.getURI();
            if(!currentResource.isCollection()){
                uri = uri.getParent();
            }
            if (!r.isPublished() && (!r.getURI().equals(uri) && requestContext.isPreviewUnpublished())) {
                continue;
            }
            // Remove resources that current principal is not allowed to access
            // (they may appear when using Repository.loadChildren).
            if (!repository.authorize(principal, r.getAcl(), Privilege.READ_PROCESSED)) {
                continue;
            }

            // Passed filtering, build menu item:
            menuItems.add(buildItem(r));
        }

        return menuItems;
    }

    private String getMenuTitle(Resource resource) {
        Property prop = resource.getProperty(this.menuGenerator.getNavigationTitlePropDef());
        if (prop != null) {
            return prop.getStringValue();
        }
        return resource.getTitle();
    }

    private int getIntegerGreaterThanZero(String prameter, DecoratorRequest request, int returnWhenParamNotFound) {
        int value = returnWhenParamNotFound;
        try {
            value = Integer.parseInt((String) request.getStringParameter(prameter));
            if (value < 1)
                intergerMustBeGreaterThanZeroException(prameter);
        } catch (NumberFormatException e) {
            if (request.getRawParameter(prameter) != null)
                intergerMustBeGreaterThanZeroException(prameter);
        }
        return value;
    }

    private void intergerMustBeGreaterThanZeroException(String prameter) {
        throw new DecoratorComponentException("Parameter '" + prameter + "' must be an integer > 0");
    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(PARAMETER_DISPLAY_FROM_LEVEL, BREAD_CRUMB_MENU_PARAMETER_DISPLAY_FROM_LEVEL_DESC);
        map.put(PARAMETER_MAX_NUMBER_OF_SIBLINGS, PARAMETER_MAX_NUMBER_OF_SIBLINGS_DESC);
        return map;
    }

}
