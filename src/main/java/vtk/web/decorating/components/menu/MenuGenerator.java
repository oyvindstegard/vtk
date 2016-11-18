/* Copyright (c) 2010, University of Oslo, Norway
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.ResourceTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.search.ResultSet;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.service.Service;
import vtk.web.service.URL;
import vtk.web.view.components.menu.ListMenu;
import vtk.web.view.components.menu.MenuItem;

public final class MenuGenerator {

    private Service viewService;
    private PropertyTypeDefinition titlePropDef;
    private PropertyTypeDefinition hiddenPropDef;
	private PropertyTypeDefinition sortDescendingPropDef;
    private PropertyTypeDefinition importancePropDef;
    private ResourceTypeDefinition collectionResourceType;
    private PropertyTypeDefinition navigationTitlePropDef;
    private PropertyTypeDefinition unpublishedCollectionPropDef;

    public MenuRequest getMenuRequest(DecoratorRequest request) {
        MenuRequest menuRequest = new MenuRequest(request, this.titlePropDef, this.collectionResourceType);
        return menuRequest;
    }

    public MenuRequest getMenuRequest(Path currentCollectionUri, String title, PropertyTypeDefinition sortProperty,
            boolean ascendingSort, boolean sortByName, int resultSets, int groupResultSetsBy, int freezeAtLevel,
            int depth, int displayFromLevel, int maxNumberOfChildren, String display, Locale locale, String token,
            int searchLimit, ArrayList<Path> includeURIs) {
        MenuRequest menuRequest = new MenuRequest(currentCollectionUri, title, sortProperty, ascendingSort, sortByName,
                resultSets, groupResultSetsBy, freezeAtLevel, depth, displayFromLevel, maxNumberOfChildren, display,
                locale, token, searchLimit, includeURIs);
        menuRequest.setCollectionResourceType(this.collectionResourceType);
        menuRequest.setTitlePropDef(this.titlePropDef);
        return menuRequest;
    }

	public ListMenu<PropertySet> buildListMenu(ResultSet rs, MenuRequest menuRequest, String modelName, boolean ascendingSort) {
        ListMenu<PropertySet> menu = new ListMenu<PropertySet>();
        Map<Path, List<PropertySet>> childMap = new HashMap<Path, List<PropertySet>>();
        List<PropertySet> toplevel = new ArrayList<PropertySet>();
        for (int i = 0; i < rs.getSize(); i++) {
            PropertySet resource = rs.getResult(i);

            // Hidden?
            if (this.hiddenPropDef != null && resource.getProperty(this.hiddenPropDef) != null) {
                continue;
            }

            Path parentURI = resource.getURI().getParent();
            if (parentURI.equals(menuRequest.getCurrentCollectionUri())) {
                toplevel.add(resource);
            }
            List<PropertySet> childList = childMap.get(parentURI);

            if (childList == null) {
                childList = new ArrayList<PropertySet>();
                childMap.put(parentURI, childList);
            }
            childList.add(resource);
        }

        List<MenuItem<PropertySet>> toplevelItems = new ArrayList<MenuItem<PropertySet>>();

        for (PropertySet resource : toplevel) {
            toplevelItems.add(buildItem(resource, childMap, menuRequest));
        }

        menu.setComparator(new ListMenuComparator(menuRequest.getLocale(), this.importancePropDef,
				this.navigationTitlePropDef, ascendingSort, menuRequest.isSortByName(), menuRequest
                        .getSortProperty()));

        menu.addAllItems(toplevelItems);
        menu.setTitle(menuRequest.getTitle());
        menu.setLabel(modelName);
        return menu;
    }

    private MenuItem<PropertySet> buildItem(PropertySet resource, Map<Path, List<PropertySet>> childMap,
            MenuRequest menuRequest) {
        Path uri = resource.getURI();

        URL url = null;
        url = this.viewService.constructURL(uri);
        url.setCollection(true);

        Property titleProperty = resource.getProperty(this.navigationTitlePropDef);
        titleProperty = titleProperty == null ? resource.getProperty(this.titlePropDef) : titleProperty;
        Value titleValue = titleProperty != null ? titleProperty.getValue() : new Value(resource.getName(),
                PropertyType.Type.STRING);

        MenuItem<PropertySet> item = new MenuItem<PropertySet>(resource);
        item.setUrl(url);

        String itemTitle = titleProperty != null ? titleProperty.getFormattedValue() : titleValue.getStringValue();
        item.setTitle(itemTitle);
        item.setLabel(titleValue.getStringValue());
        item.setActive(false);

        List<PropertySet> children = childMap.get(resource.getURI());
        if (children != null) {
            ListMenu<PropertySet> subMenu = new ListMenu<PropertySet>();

            subMenu.setComparator(new ListMenuComparator(menuRequest.getLocale(), this.importancePropDef,
                    this.navigationTitlePropDef, menuRequest.isAscendingSort(), menuRequest.isSortByName(), menuRequest
                            .getSortProperty()));

            for (PropertySet child : children) {
                subMenu.addItem(buildItem(child, childMap, menuRequest));
            }

            URL moreUrl = this.viewService.constructURL(resource.getURI());
            moreUrl.setCollection(true);
            subMenu.setMoreUrl(moreUrl);
            subMenu.setMaxNumberOfItems(menuRequest.getMaxNumberOfChildren());
            item.setSubMenu(subMenu);
        }
        return item;
    }

    public Map<String, Object> buildMenuModel(ListMenu<PropertySet> menu, MenuRequest menuRequest, boolean ascendingSort) {
        List<ListMenu<PropertySet>> resultList = new ArrayList<ListMenu<PropertySet>>();

        int resultSets = menuRequest.getResultSets();
        List<MenuItem<PropertySet>> allItems = menu.getItemsSorted();
        if (allItems == null || allItems.isEmpty()) {
            return null;
        }

        int groupResultSetsBy = menuRequest.getGroupResultSetsBy();
        int allItemsSize = allItems.size();

        if (resultSets > allItemsSize) {
            resultSets = allItemsSize;
        }

        int itemsPerResultSet = allItemsSize / resultSets;
        int remainder = allItemsSize % resultSets;

        int startIdx = 0;
        int endIdx = itemsPerResultSet;
        for (int i = 0; i < resultSets; i++) {

            // If you have remainders, keep adding an extra element to each
            // result set menu, starting from the first one until all remainders
            // are distributed
            if (remainder > 0) {
                endIdx++;
                remainder--;
            }

            List<MenuItem<PropertySet>> subList = allItems.subList(startIdx, endIdx);

            startIdx = endIdx;
            endIdx = startIdx + itemsPerResultSet;

            ListMenu<PropertySet> m = new ListMenu<PropertySet>();
            m.setComparator(new ListMenuComparator(menuRequest.getLocale(), this.importancePropDef,
					this.navigationTitlePropDef, ascendingSort, menuRequest.isSortByName(), menuRequest
                            .getSortProperty()));

            m.setTitle(menu.getTitle());
            m.setLabel(menu.getLabel());
            m.addAllItems(subList);
            resultList.add(m);
        }

        Map<String, Object> model = new HashMap<String, Object>();
        model.put("resultSets", resultList);
        if (groupResultSetsBy > 0) {
            model.put("groupResultSetsBy", groupResultSetsBy);
        }
        model.put("size", new Integer(menu.getItems().size()));
        model.put("title", menu.getTitle());
        model.put("display", menuRequest.getDisplay());
        return model;
    }

    public Service getViewService() {
        return viewService;
    }

    public PropertyTypeDefinition getTitlePropDef() {
        return titlePropDef;
    }

    public PropertyTypeDefinition getHiddenPropDef() {
        return hiddenPropDef;
    }

	public PropertyTypeDefinition getSortDescendingPropDef() {
		return sortDescendingPropDef;
	}

	public PropertyTypeDefinition getImportancePropDef() {
        return importancePropDef;
    }

    public ResourceTypeDefinition getCollectionResourceType() {
        return collectionResourceType;
    }

    public PropertyTypeDefinition getNavigationTitlePropDef() {
        return navigationTitlePropDef;
    }

    @Required
    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }

    @Required
    public void setTitlePropDef(PropertyTypeDefinition titlePropDef) {
        this.titlePropDef = titlePropDef;
    }

    @Required
    public void setHiddenPropDef(PropertyTypeDefinition hiddenPropDef) {
        this.hiddenPropDef = hiddenPropDef;
    }

	@Required
	public void setSortDescendingPropDef(PropertyTypeDefinition sortDescendingPropDef) {
		this.sortDescendingPropDef = sortDescendingPropDef;
	}

	@Required
    public void setImportancePropDef(PropertyTypeDefinition importancePropDef) {
        this.importancePropDef = importancePropDef;
    }

    @Required
    public void setCollectionResourceType(ResourceTypeDefinition collectionResourceType) {
        this.collectionResourceType = collectionResourceType;
    }

    @Required
    public void setNavigationTitlePropDef(PropertyTypeDefinition navigationTitlePropDef) {
        this.navigationTitlePropDef = navigationTitlePropDef;
    }

    public PropertyTypeDefinition getUnpublishedCollectionPropDef() {
        return unpublishedCollectionPropDef;
    }

    public void setUnpublishedCollectionPropDef(PropertyTypeDefinition unpublishedCollectionPropDef) {
        this.unpublishedCollectionPropDef = unpublishedCollectionPropDef;
    }

}
