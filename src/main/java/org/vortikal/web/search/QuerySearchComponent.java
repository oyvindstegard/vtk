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
package org.vortikal.web.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.edit.editor.ResourceWrapperManager;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Property;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.ResourceTypeTree;
import org.vortikal.repository.ResourceWrapper;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.repository.search.ConfigurablePropertySelect;
import org.vortikal.repository.search.ResultSet;
import org.vortikal.repository.search.Search;
import org.vortikal.repository.search.SortingImpl;
import org.vortikal.repository.search.query.Query;
import org.vortikal.web.RequestContext;
import org.vortikal.web.service.Service;
import org.vortikal.web.service.URL;

public abstract class QuerySearchComponent implements SearchComponent {

    private static Log logger = LogFactory.getLog(QuerySearchComponent.class.getName());

    private String name;
    private String titleLocalizationKey;
    private ResourceWrapperManager resourceManager;
    private Service viewService;
    private List<PropertyDisplayConfig> listableProperties;
    private SearchSorting searchSorting;
    private List<String> configurablePropertySelectPointers;
    private ResourceTypeTree resourceTypeTree;

    private MultiHostSearchComponent multiHostSearchComponent;
    private boolean searchMultiHosts;
    private PropertyTypeDefinition aggregationPropDef;
    private PropertyTypeDefinition manuallyApprovedResourcesPropDef;
    private PropertyTypeDefinition displayAggregationPropDef;
    private PropertyTypeDefinition displayManuallyApprovedPropDef;

    protected abstract Query getQuery(Resource collection, HttpServletRequest request);

    public Listing execute(HttpServletRequest request, Resource collection, int page, int pageLimit, int baseOffset)
            throws Exception {

        Search search = new Search();
        Query query = getQuery(collection, request);
        int offset = baseOffset + (pageLimit * (page - 1));

        search.setQuery(query);
        search.setLimit(pageLimit + 1);
        search.setCursor(offset);
        ConfigurablePropertySelect propertySelect = new ConfigurablePropertySelect();
        if (this.configurablePropertySelectPointers != null) {
            for (String propPointer : this.configurablePropertySelectPointers) {
                PropertyTypeDefinition ptd = this.resourceTypeTree.getPropertyDefinitionByPointer(propPointer);
                if (ptd != null) {
                    propertySelect.addPropertyDefinition(ptd);
                }
            }
        }
        if (!propertySelect.isEmpty()) {
            search.setPropertySelect(propertySelect);
        }

        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.isViewUnauthenticated() ? null : requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();

        String[] sortingParams = request.getParameterValues(Listing.SORTING_PARAM);
        if (sortingParams != null && sortingParams.length > 0) {
            search.setSorting(new SortingImpl(this.searchSorting.getSortFieldsFromRequestParams(sortingParams)));
        } else {
            search.setSorting(new SortingImpl(this.searchSorting.getSortFields(collection)));
        }

        ResultSet result = null;
        boolean multiHostResults = false;
        if (this.performMultiHostSearch(collection)) {
            try {
                MultiHostSearchImpl multiHostSearch = new MultiHostSearchImpl(token, search, collection);
                result = this.multiHostSearchComponent.search(multiHostSearch);
                multiHostResults = true;
            } catch (Throwable t) {
                logger.error("An error occured while searching multiple hosts. "
                        + "Defaulting to local repository search: " + t);
                result = repository.search(token, search);
            }
        } else {
            result = repository.search(token, search);
        }

        boolean more = result.getSize() == pageLimit + 1;
        int num = result.getSize();
        if (more) {
            num--;
        }

        Map<String, URL> urls = new HashMap<String, URL>();
        List<PropertySet> files = new ArrayList<PropertySet>();
        for (int i = 0; i < num; i++) {
            PropertySet res = result.getResult(i);
            files.add(res);
            URL url = this.viewService.constructURL(res.getURI());
            if (multiHostResults) {
                Property urlProp = res.getProperty(Namespace.DEFAULT_NAMESPACE, MultiHostSearch.SOLR_URL_PROP_NAME);
                if (urlProp != null) {
                    url = URL.parse(urlProp.getStringValue());
                }
            }
            urls.put(res.getURI().toString(), url);
        }

        List<PropertyTypeDefinition> displayPropDefs = new ArrayList<PropertyTypeDefinition>();
        for (PropertyDisplayConfig config : this.listableProperties) {
            Property hide = null;
            if (config.getPreventDisplayProperty() != null) {
                hide = collection.getProperty(config.getPreventDisplayProperty());
            }
            if (hide == null) {
                displayPropDefs.add(config.getDisplayProperty());
            }
        }

        String title = null;
        if (this.titleLocalizationKey != null) {
            org.springframework.web.servlet.support.RequestContext springRequestContext = new org.springframework.web.servlet.support.RequestContext(
                    request);
            title = springRequestContext.getMessage(this.titleLocalizationKey, (String) null);
        }

        ResourceWrapper resourceWrapper = this.resourceManager.createResourceWrapper(collection);

        Listing listing = new Listing(resourceWrapper, title, name, offset);
        listing.setMore(more);
        listing.setFiles(files);
        listing.setUrls(urls);
        listing.setDisplayPropDefs(displayPropDefs);
        listing.setTotalHits(result.getTotalHits());
        listing.setSorting(search.getSorting());
        return listing;
    }

    private boolean performMultiHostSearch(Resource collection) {

        if (this.multiHostSearchComponent == null || !this.searchMultiHosts) {
            return false;
        }

        // Check for multi host aggregation
        if (this.displayAggregationPropDef != null && this.aggregationPropDef != null) {
            Property displayAggregationProp = collection.getProperty(this.displayAggregationPropDef);
            if (displayAggregationProp != null && displayAggregationProp.getBooleanValue()) {
                Property aggregationProp = collection.getProperty(this.aggregationPropDef);
                if (aggregationProp != null) {
                    for (Value value : aggregationProp.getValues()) {
                        if (isUrl(value.getStringValue())) {
                            return true;
                        }
                    }
                }
            }
        }

        // Check for multi host manual approval
        if (this.displayManuallyApprovedPropDef != null && this.manuallyApprovedResourcesPropDef != null) {
            Property displayManuallyApprovedProp = collection.getProperty(this.displayManuallyApprovedPropDef);
            if (displayManuallyApprovedProp != null && displayManuallyApprovedProp.getBooleanValue()) {
                Property manuallyApprovedProp = collection.getProperty(this.manuallyApprovedResourcesPropDef);
                if (manuallyApprovedProp != null) {
                    String repositoryId = RequestContext.getRequestContext().getRepository().getId();
                    for (Value value : manuallyApprovedProp.getValues()) {
                        // We have at least one manually approved resource that
                        // is not on local host
                        if (!value.getStringValue().contains(repositoryId)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean isUrl(String stringValue) {
        try {
            URL.parse(stringValue);
            return true;
        } catch (Exception e) {
            return false;
        }
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
    public void setListableProperties(List<PropertyDisplayConfig> listableProperties) {
        this.listableProperties = listableProperties;
    }

    public void setTitleLocalizationKey(String titleLocalizationKey) {
        this.titleLocalizationKey = titleLocalizationKey;
    }

    public String getTitleLocalizationKey() {
        return titleLocalizationKey;
    }

    @Required
    public void setSearchSorting(SearchSorting searchSorting) {
        this.searchSorting = searchSorting;
    }

    public void setConfigurablePropertySelectPointers(List<String> configurablePropertySelectPointers) {
        this.configurablePropertySelectPointers = configurablePropertySelectPointers;
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    public void setMultiHostSearchComponent(MultiHostSearchComponent multiHostSearchComponent) {
        this.multiHostSearchComponent = multiHostSearchComponent;
    }

    public void setSearchMultiHosts(boolean searchMultiHosts) {
        this.searchMultiHosts = searchMultiHosts;
    }

    public void setAggregationPropDef(PropertyTypeDefinition aggregationPropDef) {
        this.aggregationPropDef = aggregationPropDef;
    }

    public void setManuallyApprovedResourcesPropDef(PropertyTypeDefinition manuallyApprovedResourcesPropDef) {
        this.manuallyApprovedResourcesPropDef = manuallyApprovedResourcesPropDef;
    }

    public void setDisplayAggregationPropDef(PropertyTypeDefinition displayAggregationPropDef) {
        this.displayAggregationPropDef = displayAggregationPropDef;
    }

    public void setDisplayManuallyApprovedPropDef(PropertyTypeDefinition displayManuallyApprovedPropDef) {
        this.displayManuallyApprovedPropDef = displayManuallyApprovedPropDef;
    }

}
