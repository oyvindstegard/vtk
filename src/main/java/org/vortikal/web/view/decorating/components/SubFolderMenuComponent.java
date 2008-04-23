/* Copyright (c) 2007, University of Oslo, Norway
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
package org.vortikal.web.view.decorating.components;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.vortikal.repository.Property;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.ResourceTypeTree;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.ResourceTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.repository.search.ResultSet;
import org.vortikal.repository.search.Search;
import org.vortikal.repository.search.Searcher;
import org.vortikal.repository.search.WildcardPropertySelect;
import org.vortikal.repository.search.query.AndQuery;
import org.vortikal.repository.search.query.OrQuery;
import org.vortikal.repository.search.query.TermOperator;
import org.vortikal.repository.search.query.TypeTermQuery;
import org.vortikal.repository.search.query.UriDepthQuery;
import org.vortikal.repository.search.query.UriPrefixQuery;
import org.vortikal.security.SecurityContext;
import org.vortikal.util.repository.URIUtil;
import org.vortikal.util.web.URLUtil;
import org.vortikal.web.RequestContext;
import org.vortikal.web.service.Service;
import org.vortikal.web.view.components.menu.ListMenu;
import org.vortikal.web.view.components.menu.MenuItem;
import org.vortikal.web.view.decorating.DecoratorRequest;
import org.vortikal.web.view.decorating.DecoratorResponse;

/**
 * <p>
 * The following input is evaluated
 * </p>
 * <ul>
 * <li>title - menu title</li>
 * <li>sort - the property to sort results by</li>
 * <li>direction - the sort direction (ascending / descending)</li>
 * <li>result-sets - the number of &lt;ul&gt; lists to split the result into</li>
 * <li>exclude-folders - comma-separated list with relative paths to folders which should be excluded</li>
 * <li>authenticated - default is listing only read-for-all resources</li>
 * <li>depth - specifies number of levels to retrieve subfolders from</li>
 * </ul>
 */
public class SubFolderMenuComponent extends ViewRenderingDecoratorComponent {

    private static final int DEFAULT_SEARCH_LIMIT = 250;

    private static final String DESCRIPTION = "Lists the child folders of the current folder";

    private static final String PARAMETER_TITLE = "title";
    private static final String PARAMETER_TITLE_DESC = "The menu title";

    private static final String PARAMETER_SORT = "sort";
    private static final String PARAMETER_SORT_DESC = "The name of a property to sort results by. Legal values are ('name', 'title'). "
            + "The default property is 'title'";

    private static final String PARAMETER_SORT_DIRECTION = "direction";
    private static final String PARAMETER_SORT_DIRECTION_DESC = "The sort direction. Legal values are 'asc', 'desc'. The default value is 'asc' ";

    private static final String PARAMETER_RESULT_SETS = "result-sets";
    private static final String PARAMETER_RESULT_SETS_DESC = "The number of result sets to split the result into. The default value is '1'";
    private static final int PARAMETER_RESULT_SETS_MAX_VALUE = 30;

    private static final String PARAMETER_EXCLUDE_FOLDERS = "exclude-folders";
    private static final String PARAMETER_EXCLUDE_FOLDERS_DESC = "Commma-separated list with relative paths to folders which should not be displayed in the list";

    private static final String PARAMETER_AS_CURRENT_USER = "authenticated";
    private static final String PARAMETER_AS_CURRENT_USER_DESC = "The default is that only resources readable for everyone is listed. "
            + "If this is set to 'true', the listing is done as the currently " + "logged in user (if any)";

    private static final String PARAMETER_DEPTH = "depth";
    private static final String PARAMETER_DEPTH_DESC = "Specifies the number of levels to retrieve subfolders for. The default value is '1' ";

    private static Log logger = LogFactory.getLog(SubFolderMenuComponent.class);

    private Service viewService;
    private PropertyTypeDefinition titlePropDef;
    private ResourceTypeDefinition collectionResourceType;
    private String modelName = "menu";
    private int searchLimit = DEFAULT_SEARCH_LIMIT;
    private Searcher searcher;
    private ResourceTypeTree resourceTypeTree;


    public void processModel(Map<Object, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {
        MenuRequest menuRequest = new MenuRequest(request);
        Search search = buildSearch(menuRequest);
        String token = menuRequest.getToken();
        ResultSet rs = this.searcher.execute(token, search);
        if (logger.isDebugEnabled()) {
            logger.debug("Executed search: " + search + ", hits: " + rs.getSize());
        }
        ListMenu<Value> menu = buildListMenu(rs, menuRequest);
        Map<String, Object> menuModel = buildMenuModel(menu, menuRequest);
        model.put(this.modelName, menuModel);
        if (logger.isDebugEnabled()) {
            logger.debug("Built model: " + model + " from menu: " + menu);
        }
    }


    private Map<String, Object> buildMenuModel(ListMenu<Value> menu, MenuRequest menuRequest) {
        List<ListMenu<Value>> resultList = new ArrayList<ListMenu<Value>>();

        int resultSets = menuRequest.getResultSets();
        List<MenuItem<Value>> allItems = menu.getItemsSorted();
        if (resultSets > allItems.size()) {
            resultSets = allItems.size();
        }

        int itemsPerResultSet = Math.round((float) allItems.size() / (float) resultSets);
        int remainder = allItems.size() - (resultSets * itemsPerResultSet);

        for (int i = 0; i < resultSets; i++) {
            int startIdx = i * itemsPerResultSet;
            int endIdx = startIdx + itemsPerResultSet;

            if (i == resultSets - 1 && remainder > 0) {
                endIdx += remainder;
            }
            if (endIdx > allItems.size()) {
                endIdx = allItems.size();
            }

            List<MenuItem<Value>> subList = allItems.subList(startIdx, endIdx);
            ListMenu<Value> m = new ListMenu<Value>();
            m.setComparator(new ItemValueComparator(menuRequest.isAscendingSort(), menuRequest.getLocale()));
            m.setTitle(menu.getTitle());
            m.setLabel(menu.getLabel());
            m.addAllItems(subList);
            resultList.add(m);
        }

        Map<String, Object> model = new HashMap<String, Object>();
        model.put("resultSets", resultList);
        model.put("size", new Integer(menu.getItems().size()));
        model.put("title", menu.getTitle());
        return model;
    }


    private Search buildSearch(MenuRequest menuRequest) {
        String uri = menuRequest.getCurrentCollectionUri();
        int depth = URLUtil.splitUri(uri).length;

        AndQuery mainQuery = new AndQuery();
        mainQuery.add(new UriPrefixQuery(uri));

        if (menuRequest.getDepth() > 1) {
            // Needs search support for this:
            // query.add(new UriDepthTermQuery).append(menuRequest.getDepth(), TermOperator.GT);
            OrQuery depthQuery = new OrQuery();
            for (int i = 0; i < menuRequest.getDepth(); i++) {
                depthQuery.add(new UriDepthQuery(depth + i));
            }
            mainQuery.add(depthQuery);
        } else {
            mainQuery.add(new UriDepthQuery(depth));
        }

        /**
         * TODO: Maybe add inversion filter for whole set rather than and'ing a ton of inverted queries?
         */
        if (!menuRequest.getExcludeURIs().isEmpty()) {
            for (Iterator<String> i = menuRequest.getExcludeURIs().iterator(); i.hasNext();) {
                String exUri = (String) i.next();
                mainQuery.add(new UriPrefixQuery(exUri, true));
            }
        }

        mainQuery.add(new TypeTermQuery(this.collectionResourceType.getName(), TermOperator.IN));

        WildcardPropertySelect select = new WildcardPropertySelect();

        Search search = new Search();
        search.setQuery(mainQuery);
        search.setLimit(this.searchLimit);
        search.setPropertySelect(select);
        return search;
    }


    private ListMenu<Value> buildListMenu(ResultSet rs, MenuRequest menuRequest) {

        Map<String, List<PropertySet>> childMap = new HashMap<String, List<PropertySet>>();
        List<PropertySet> toplevel = new ArrayList<PropertySet>();
        for (int i = 0; i < rs.getSize(); i++) {
            PropertySet resource = rs.getResult(i);
            String parentURI = URIUtil.getParentURI(resource.getURI());
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

        List<MenuItem<Value>> toplevelItems = new ArrayList<MenuItem<Value>>();
        for (PropertySet resource : toplevel) {
            toplevelItems.add(buildItem(resource, childMap, menuRequest));
        }

        ListMenu<Value> menu = new ListMenu<Value>();
        menu.setComparator(new ItemValueComparator(menuRequest.isAscendingSort(), menuRequest.getLocale()));
        menu.addAllItems(toplevelItems);
        menu.setTitle(menuRequest.getTitle());
        menu.setLabel(this.modelName);
        return menu;
    }


    private MenuItem<Value> buildItem(PropertySet resource, Map<String, List<PropertySet>> childMap,
            MenuRequest menuRequest) {
        String uri = resource.getURI();
        if (!uri.equals("/")) {
            // Know it's a folder, append "/"
            uri += "/";
        }

        String url = this.viewService.constructLink(uri);
        Property titleProperty = resource.getProperty(this.titlePropDef);
        Value title = titleProperty != null ? titleProperty.getValue() : new Value(resource.getName());

        MenuItem<Value> item = new MenuItem<Value>();
        item.setUrl(url);
        item.setTitle(title);
        item.setLabel(title.getStringValue());
        item.setActive(false);

        if (menuRequest.isSortByName()) {
            item.setSortField(new Value(resource.getName()));

        } else {
            PropertyTypeDefinition sortPropDef = menuRequest.getSortProperty();
            Property sortProp = resource.getProperty(sortPropDef);
            if (sortProp == null) {
                throw new DecoratorComponentException("Cannot sort on property '" + menuRequest.getSortProperty()
                        + "': no such property on resource '" + resource.getURI() + "'");
            }
            item.setSortField(sortProp.getValue());
        }

        List<PropertySet> children = childMap.get(resource.getURI());
        if (children != null) {
            ListMenu<Value> subMenu = new ListMenu<Value>();
            subMenu.setComparator(new ItemValueComparator(menuRequest.isAscendingSort(), menuRequest.getLocale()));
            for (PropertySet child : children) {
                subMenu.addItem(buildItem(child, childMap, menuRequest));
            }
            item.setSubMenu(subMenu);
        }
        return item;
    }

    private class MenuRequest {
        private String currentCollectionUri;
        private String title;
        private boolean sortByName = false;
        private PropertyTypeDefinition sortProperty;
        private boolean ascendingSort = true;
        private int resultSets = 1;
        private int depth = 1;
        private ArrayList<String> excludeURIs;
        private Locale locale;
        private String token;


        public MenuRequest(DecoratorRequest request) {

            RequestContext requestContext = RequestContext.getRequestContext();
            this.currentCollectionUri = requestContext.getCurrentCollection();

            boolean asCurrentUser = "true".equals(request.getStringParameter(PARAMETER_AS_CURRENT_USER));
            if (asCurrentUser) {
                SecurityContext securityContext = SecurityContext.getSecurityContext();
                this.token = securityContext.getToken();
            }

            this.title = request.getStringParameter(PARAMETER_TITLE);

            initSortField(request);

            if (request.getStringParameter(PARAMETER_RESULT_SETS) != null) {
                try {
                    this.resultSets = Integer.parseInt(request.getStringParameter(PARAMETER_RESULT_SETS));
                } catch (Throwable t) {
                    throw new DecoratorComponentException("Illegal value for parameter '" + PARAMETER_RESULT_SETS
                            + "': " + request.getStringParameter(PARAMETER_RESULT_SETS));
                }
                if (this.resultSets <= 0 || this.resultSets > PARAMETER_RESULT_SETS_MAX_VALUE) {
                    throw new DecoratorComponentException("Illegal value for parameter '" + PARAMETER_RESULT_SETS
                            + "': " + this.resultSets + ": must be a positive number between 1 and "
                            + PARAMETER_RESULT_SETS_MAX_VALUE);
                }
            }

            String depthStr = request.getStringParameter(PARAMETER_DEPTH);
            if (depthStr != null) {
                try {
                    int depth = Integer.parseInt(depthStr);
                    if (depth < 1) {
                        throw new IllegalArgumentException("Depth must be an integer >= 1");
                    }
                    this.depth = depth;
                } catch (Throwable t) {
                    throw new DecoratorComponentException("Illegal value for parameter '" + PARAMETER_DEPTH + "': "
                            + depthStr);
                }
            }

            // ArrayList excludeFolders = new ArrayList<String>();
            String excludeFolders = request.getStringParameter(PARAMETER_EXCLUDE_FOLDERS);
            if (excludeFolders != null) {
                try {
                    StringTokenizer excludeFoldersTokenized = new StringTokenizer(excludeFolders, ",");
                    ArrayList<String> excludeUIRs = new ArrayList<String>();
                    boolean addSlash = currentCollectionUri.endsWith("/") ? false : true;
                    while (excludeFoldersTokenized.hasMoreTokens()) {
                        String uri = excludeFoldersTokenized.nextToken().trim();
                        if (addSlash) {
                            uri = currentCollectionUri + "/" + uri;
                        } else {
                            uri = currentCollectionUri + uri;
                        }
                        excludeUIRs.add(uri);
                    }
                    this.excludeURIs = excludeUIRs;
                } catch (Throwable t) {
                    throw new DecoratorComponentException("Illegal value for parameter '" + PARAMETER_EXCLUDE_FOLDERS
                            + "': " + depthStr);
                }
            }

            this.locale = request.getLocale();
        }


        public String getCurrentCollectionUri() {
            return this.currentCollectionUri;
        }


        public String getTitle() {
            return this.title;
        }


        public boolean isSortByName() {
            return this.sortByName;
        }


        public PropertyTypeDefinition getSortProperty() {
            return this.sortProperty;
        }


        public boolean isAscendingSort() {
            return this.ascendingSort;
        }


        public int getResultSets() {
            return this.resultSets;
        }


        public Locale getLocale() {
            return this.locale;
        }


        public String getToken() {
            return this.token;
        }


        public int getDepth() {
            return this.depth;
        }


        public ArrayList<String> getExcludeURIs() {
            return excludeURIs;
        }


        private void initSortField(DecoratorRequest request) {
            String sortFieldParam = request.getStringParameter(PARAMETER_SORT);
            if (sortFieldParam == null) {
                this.sortProperty = titlePropDef;

            } else if ("name".equals(sortFieldParam)) {
                this.sortByName = true;

            } else if ("title".equals(sortFieldParam)) {
                this.sortProperty = titlePropDef;
            } else {
                throw new DecoratorComponentException("Illegal value for parameter '" + PARAMETER_SORT
                        + "': must be one of ('name', 'title')");
            }

            String sortDirectionParam = request.getStringParameter(PARAMETER_SORT_DIRECTION);
            if (sortDirectionParam == null) {
                sortDirectionParam = "asc";
            }

            if ("asc".equals(sortDirectionParam)) {
                this.ascendingSort = true;
            } else if ("desc".equals(sortDirectionParam)) {
                this.ascendingSort = false;
            } else {
                throw new DecoratorComponentException("Illegal value for parameter '" + PARAMETER_SORT_DIRECTION
                        + "': '" + sortDirectionParam + "' (must be one of 'asc', 'desc')");
            }
        }
    }

    private class ItemValueComparator implements Comparator<MenuItem<Value>> {

        private Collator collator;
        private boolean ascending = true;


        public ItemValueComparator(boolean ascending, Locale locale) {
            this.ascending = ascending;
            this.collator = Collator.getInstance(locale);
        }


        public int compare(MenuItem<Value> item1, MenuItem<Value> item2) {
            if (!this.ascending) {
                return collator.compare(item2.getSortField().getStringValue(), item1.getSortField().getStringValue());
            }
            return collator.compare(item1.getSortField().getStringValue(), item2.getSortField().getStringValue());
        }
    }


    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }


    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }


    public void setTitlePropDef(PropertyTypeDefinition titlePropDef) {
        this.titlePropDef = titlePropDef;
    }


    public void setCollectionResourceType(ResourceTypeDefinition collectionResourceType) {
        this.collectionResourceType = collectionResourceType;
    }


    public void setModelName(String modelName) {
        this.modelName = modelName;
    }


    public void setSearcher(Searcher searcher) {
        this.searcher = searcher;
    }


    public void setSearchLimit(int searchLimit) {
        this.searchLimit = searchLimit;
    }


    protected String getDescriptionInternal() {
        return DESCRIPTION;
    }


    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(PARAMETER_TITLE, PARAMETER_TITLE_DESC);
        map.put(PARAMETER_SORT, PARAMETER_SORT_DESC);
        map.put(PARAMETER_SORT_DIRECTION, PARAMETER_SORT_DIRECTION_DESC);
        map.put(PARAMETER_RESULT_SETS, PARAMETER_RESULT_SETS_DESC);
        map.put(PARAMETER_EXCLUDE_FOLDERS, PARAMETER_EXCLUDE_FOLDERS_DESC);
        map.put(PARAMETER_AS_CURRENT_USER, PARAMETER_AS_CURRENT_USER_DESC);
        map.put(PARAMETER_DEPTH, PARAMETER_DEPTH_DESC);
        return map;
    }


    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();

        if (this.viewService == null) {
            throw new BeanInitializationException("JavaBean property 'viewService set");
        }
        if (this.searcher == null) {
            throw new BeanInitializationException("JavaBean property 'searcher' not set");
        }
        if (this.resourceTypeTree == null) {
            throw new BeanInitializationException("JavaBean property 'resourceTypeTree' not set");
        }
        if (this.titlePropDef == null) {
            throw new BeanInitializationException("JavaBean property 'titlePropDef' not set");
        }
        if (this.modelName == null) {
            throw new BeanInitializationException("JavaBean property 'modelName' not set");
        }
        if (this.searchLimit <= 0) {
            throw new BeanInitializationException("JavaBean property '" + searchLimit + "' must be a positive integer");
        }
    }

}
