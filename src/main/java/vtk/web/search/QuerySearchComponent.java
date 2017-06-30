/* Copyright (c) 2008, University of Oslo, Norway
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
package vtk.web.search;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.edit.editor.ResourceWrapperManager;
import vtk.repository.MultiHostSearcher;
import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceTypeTree;
import vtk.repository.ResourceWrapper;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.ConfigurablePropertySelect;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Sorting;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.decorating.components.CollectionListingHelper;
import vtk.web.service.Service;
import vtk.web.service.URL;

public abstract class QuerySearchComponent implements SearchComponent {

    private String name;
    private String titleLocalizationKey;
    private ResourceWrapperManager resourceManager;
    private List<PropertyTypeDefinition> hideableProperties;
    private SearchSorting searchSorting;
    private List<String> configurablePropertySelectPointers;
    private ResourceTypeTree resourceTypeTree;
    private CollectionListingHelper helper;
    private boolean displayEditLinks;

    protected Service viewService;

    protected abstract ResultSet getResultSet(HttpServletRequest request, Resource collection, String token,
            Sorting sorting, int searchLimit, int baseOffset, ConfigurablePropertySelect propertySelect);

    public Listing execute(HttpServletRequest request, Resource collection, int page, int pageLimit, int baseOffset)
            throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.isViewUnauthenticated() ? null : requestContext.getSecurityToken();

        int offset = baseOffset + (pageLimit * (page - 1));
        int searchLimit = pageLimit + 1;

        Sorting sorting = null;
        String[] sortingParams = request.getParameterValues(Listing.SORTING_PARAM);
        if (sortingParams != null && sortingParams.length > 0) {
            sorting = new Sorting(searchSorting.getSortFieldsFromRequestParams(sortingParams));
        } else {
            sorting = new Sorting(searchSorting.getSortFields(collection));
        }

        ConfigurablePropertySelect propertySelect = null;
        if (configurablePropertySelectPointers != null && resourceTypeTree != null) {
            for (String propPointer : configurablePropertySelectPointers) {
                PropertyTypeDefinition ptd = resourceTypeTree.getPropertyDefinitionByName(propPointer);
                if (ptd != null) {
                    if (propertySelect == null) {
                        propertySelect = new ConfigurablePropertySelect();
                    }
                    propertySelect.addPropertyDefinition(ptd);
                }
            }
        }

        ResultSet result = getResultSet(request, collection, token, sorting, searchLimit, offset, propertySelect);

        boolean more = result.getSize() == pageLimit + 1;
        int num = result.getSize();
        if (more) {
            num--;
        }

        // The actual resources we are going to display
        List<ListingEntry> entries = new ArrayList<>();
        Repository repository = requestContext.getRepository();
        Principal principal = requestContext.getPrincipal();
        for (int i = 0; i < num; i++) {
            PropertySet propSet = result.getResult(i);
            URL url = null;
            EditInfo editInfo = new EditInfo(false, false, null);
            Property urlProp = propSet.getProperty(Namespace.DEFAULT_NAMESPACE, MultiHostSearcher.URL_PROP_NAME);
            if (urlProp != null) {
                url = URL.parse(urlProp.getStringValue());
            }
            else {
                url = viewService.urlConstructor(URL.create(request))
                        .withURI(propSet.getURI())
                        .constructURL();
                if (displayEditLinks && helper != null) {
                    editInfo = helper.checkResourceForEditLink(repository, propSet, token, principal);
                }
            }
            entries.add(new ListingEntry(propSet, url, editInfo));
        }

        List<PropertyTypeDefinition> displayPropDefs = new ArrayList<>();
        if (hideableProperties != null) {
            for (PropertyTypeDefinition typeDefinition : hideableProperties) {
                Property hide = collection.getProperty(typeDefinition);
                if (hide != null) {
                    displayPropDefs.add(typeDefinition);
                }
            }
        }

        String title = null;
        if (titleLocalizationKey != null) {
            org.springframework.web.servlet.support.RequestContext springRequestContext = new org.springframework.web.servlet.support.RequestContext(
                    request);
            title = springRequestContext.getMessage(titleLocalizationKey, (String) null);
        }

        ResourceWrapper resourceWrapper = resourceManager.createResourceWrapper(collection);

        Listing listing = new Listing(resourceWrapper, title, name, offset);
        listing.setMore(more);
        listing.setEntries(entries);
        listing.setDisplayPropDefs(displayPropDefs);
        listing.setTotalHits(result.getTotalHits());
        listing.setSorting(sorting);
        return listing;
    }

    @Required
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    @Required
    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }

    @Required
    public void setResourceManager(ResourceWrapperManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @Required
    public void setSearchSorting(SearchSorting searchSorting) {
        this.searchSorting = searchSorting;
    }

    public void setHideableProperties(List<PropertyTypeDefinition> hideableProperties) {
        this.hideableProperties = hideableProperties;
    }

    public void setTitleLocalizationKey(String titleLocalizationKey) {
        this.titleLocalizationKey = titleLocalizationKey;
    }

    public String getTitleLocalizationKey() {
        return titleLocalizationKey;
    }

    public void setConfigurablePropertySelectPointers(List<String> configurablePropertySelectPointers) {
        this.configurablePropertySelectPointers = configurablePropertySelectPointers;
    }

    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    public void setHelper(CollectionListingHelper helper) {
        this.helper = helper;
    }

    public void setDisplayEditLinks(boolean displayEditLinks) {
        this.displayEditLinks = displayEditLinks;
    }

}
