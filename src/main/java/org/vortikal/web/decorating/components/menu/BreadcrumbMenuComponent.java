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
package org.vortikal.web.decorating.components.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.vortikal.repository.AuthorizationException;
import org.vortikal.repository.Path;
import org.vortikal.repository.Property;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.security.AuthenticationException;
import org.vortikal.security.Principal;
import org.vortikal.web.RequestContext;
import org.vortikal.web.decorating.DecoratorRequest;
import org.vortikal.web.decorating.DecoratorResponse;
import org.vortikal.web.decorating.components.DecoratorComponentException;
import org.vortikal.web.referencedata.provider.BreadCrumbProvider;
import org.vortikal.web.referencedata.provider.BreadcrumbElement;
import org.vortikal.web.service.URL;
import org.vortikal.web.view.components.menu.MenuItem;

public class BreadcrumbMenuComponent extends ListMenuComponent {

    private static final String PARAMETER_MAX_NUMBER_OF_SIBLINGS = "max-number-of-siblings";
    private static final String PARAMETER_MAX_NUMBER_OF_SIBLINGS_DESC = "Defines the maximum number of siblings. When this limit is"
            + " reached no siblings are going to be displayed. Default limit is: " + Integer.MAX_VALUE;
    private static final String PARAMETER_DISPLAY_FROM_LEVEL_DESC = "Defines the starting URI level for the menu";

    @Override
    public void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {

        int displayFromLevel = getIntegerGreaterThanZero(PARAMETER_DISPLAY_FROM_LEVEL, request, -1);
        int maxSiblings = getIntegerGreaterThanZero(PARAMETER_MAX_NUMBER_OF_SIBLINGS, request, Integer.MAX_VALUE);

        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        Path uri = requestContext.getResourceURI();
        Principal principal = requestContext.getPrincipal();

        List<BreadcrumbElement> breadCrumbElements = getBreadcrumbElements();
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

        List<MenuItem<PropertySet>> menuItemList = generateMenuItemList(currentResource.getChildURIs(),
                currentResource, token);

        // If menu is null or empty, i.e. current resource had no children or
        // all children were hidden, then generate menu based on siblings.
        if (menuItemList != null && menuItemList.size() == 0) {
            Resource currentResourceParent = null;
            try {
                currentResourceParent = repository.retrieve(token, currentResource.getURI().getParent(), true);
            } catch (AuthorizationException e) {
            } catch (AuthenticationException e) {
            }

            if (currentResourceParent != null) {
                menuItemList = generateMenuItemList(currentResourceParent.getChildURIs(), currentResource, token);
                breadCrumbElements.remove(breadCrumbElements.size() - 1);
                if (menuItemList.size() > maxSiblings) {
                    menuItemList = new ArrayList<MenuItem<PropertySet>>();
                    menuItemList.add(buildItem(currentResource));
                }
            }
        }

        menuItemList = sortDefaultOrder(menuItemList, request.getLocale());

        model.put("breadcrumb", breadCrumbElements);
        model.put("children", menuItemList);
        model.put("markedurl", markedUrl);
    }

    private List<BreadcrumbElement> getBreadcrumbElements() throws Exception {

        String breadcrumbName = "breadcrumb";
        BreadCrumbProvider breadCrumbProvider = new BreadCrumbProvider();
        breadCrumbProvider.setSkipCurrentResource(true);
        breadCrumbProvider.setService(this.menuGenerator.getViewService());
        breadCrumbProvider.setBreadcrumbName(breadcrumbName);
        breadCrumbProvider.setSkipIndexFile(false);
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

    private List<MenuItem<PropertySet>> generateMenuItemList(List<Path> children, Resource currentResource, String token)
            throws Exception {

        Repository repository = RequestContext.getRequestContext().getRepository();
        List<MenuItem<PropertySet>> items = new ArrayList<MenuItem<PropertySet>>();

        for (Path childPath : children) {
            Resource childResource = null;
            try {
                childResource = repository.retrieve(token, childPath, true);
            } catch (AuthorizationException e) {
                continue; // can't access resource - not displayed in menu
            } catch (AuthenticationException e) {
                continue; // can't access resource - not displayed in menu
            }
            if (!childResource.isCollection()) {
                continue;
            }
            if (childResource.getProperty(this.menuGenerator.getHiddenPropDef()) != null
                    && !childResource.equals(currentResource)) {
                continue; // hidden
            }
            items.add(buildItem(childResource));
        }

        return items;
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
        map.put(PARAMETER_DISPLAY_FROM_LEVEL, PARAMETER_DISPLAY_FROM_LEVEL_DESC);
        map.put(PARAMETER_MAX_NUMBER_OF_SIBLINGS, PARAMETER_MAX_NUMBER_OF_SIBLINGS_DESC);
        return map;
    }

}
