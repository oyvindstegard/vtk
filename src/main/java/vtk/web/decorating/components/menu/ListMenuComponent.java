/* Copyright (c) 2007, 2008, University of Oslo, Norway
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.ConfigurablePropertySelect;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.Query;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.TypeTermQuery;
import vtk.repository.search.query.UriDepthQuery;
import vtk.repository.search.query.UriPrefixQuery;
import vtk.repository.search.query.UriTermQuery;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.decorating.components.DecoratorComponentException;
import vtk.web.decorating.components.ViewRenderingDecoratorComponent;
import vtk.web.service.URL;
import vtk.web.view.components.menu.ListMenu;
import vtk.web.view.components.menu.MenuItem;

/**
 * <p>
 * The following input is evaluated
 * <ul>
 * <li>uri - the collection uri to create a menu from</li>
 * <li>include-children - if specified, only these child names is search for</li>
 * <li>exclude-children - alternative to include-children, exclude named
 * children</li>
 * <li>depth - if greater than one and current resource is <b>below</b> uri,
 * build sub menus</li>
 * <li>include-parent-folder - include uri collection first in menu</li>
 * <li>authenticated - default is listing only read-for-all resources</li>
 * </ul>
 * 
 */
public class ListMenuComponent extends ViewRenderingDecoratorComponent {

    protected MenuGenerator menuGenerator;

    @Required
    public void setMenuGenerator(MenuGenerator menuGenerator) {
        this.menuGenerator = menuGenerator;
    }

    private static final int DEFAULT_DEPTH = 1;
    protected static final int DEFAULT_SEARCH_LIMIT = 1000;

    private static final String STYLE_NONE = "none";
    private static final String STYLE_VERTICAL = "vertical";
    private static final String STYLE_HORIZONTAL = "horizontal";
    private static final String STYLE_TABS = "tabs";

    private static final String DEFAULT_STYLE = STYLE_NONE;

    private static final Set<String> VALID_STYLES;
    static {
        VALID_STYLES = new HashSet<String>();
        VALID_STYLES.add(STYLE_NONE);
        VALID_STYLES.add(STYLE_VERTICAL);
        VALID_STYLES.add(STYLE_HORIZONTAL);
        VALID_STYLES.add(STYLE_TABS);
    }

    private static final String PARAMETER_INCLUDE_CHILDREN = "include-children";
    private static final String PARAMETER_INCLUDE_CHILDREN_DESC = "An explicit listing of the child resources to include. (Only applicable for resources at level 1.)";

    private static final String PARAMETER_EXCLUDE_CHILDREN = "exclude-children";
    private static final String PARAMETER_EXCLUDE_CHILDREN_DESC = "A listing of child resources to exclude (cannot be used in conjunction with '"
            + PARAMETER_INCLUDE_CHILDREN + "').";

    private static final String PARAMETER_INCLUDE_PARENT = "include-parent-folder";
    private static final String PARAMETER_INCLUDE_PARENT_DESC = "Whether or not to include the selected folder itself in the menu. Defaults to 'false'.";

    private static final String PARAMETER_PARENT_FOLDER_TITLE = "parent-folder-title";
    private static final String PARAMETER_PARENT_FOLDER_TITLE_DESC = "Overrides the parent folder title if include-parent-folder is set to true.";

    private static final String PARAMETER_STYLE = "style";
    private static final String PARAMETER_STYLE_DESC = "Defines the style of the menu. Must be one of "
            + VALID_STYLES.toString() + ". Defaults to " + DEFAULT_STYLE + ".";

    private static final String PARAMETER_URI = "uri";
    private static final String PARAMETER_URI_DESC = "The URI (path) to the selected folder.";

    private static final String PARAMETER_AUTENTICATED = "authenticated";
    private static final String PARAMETER_AUTENTICATED_DESC = "The default is that only resources readable for everyone is listed. "
            + "If this is set to 'true', the listing is done as the currently " + "logged in user (if any).";

    private static final String PARAMETER_DEPTH = "depth";
    private static final String PARAMETER_DEPTH_DESC = "Specifies the number of levels to retrieve subfolders for. The default value is '1', which retrieves the top level.";

    protected static final String PARAMETER_DISPLAY_FROM_LEVEL = "display-from-level";
    protected static final String PARAMETER_DISPLAY_FROM_LEVEL_DESC = "Defines the starting URI level for the menu (cannot be used with the '"
            + PARAMETER_URI + "' parameter)";

    protected String modelName = "menu";
    protected int searchLimit = DEFAULT_SEARCH_LIMIT;

    public void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {
        ListMenuRequest menuRequest = new ListMenuRequest(request);

        int currentLevel = menuRequest.getCurrentFolder().getDepth() + 1;

        if (menuRequest.getDisplayFromLevel() != -1) {
            if (currentLevel < menuRequest.getDisplayFromLevel()) {
                return;
            }
        }

        // Build main menu
        ListMenu<PropertySet> menu = buildMainMenu(menuRequest);

        // Add sub menu?
        MenuItem<PropertySet> activeItem = menu.getActiveItem();
        if (activeItem != null && menuRequest.getDepth() > 1) {
            ListMenu<PropertySet> submenu = buildSubMenu(menuRequest, request.getServletRequest().getRequestURI());
            if (submenu != null) {
                activeItem.setSubMenu(submenu);
            }
        }

        model.put(this.modelName, menu);
    }

    private ListMenu<PropertySet> buildMainMenu(ListMenuRequest menuRequest) {

        ListMenu<PropertySet> menu = new ListMenu<PropertySet>();
        menu.setLabel(menuRequest.getStyle());

        Query query = buildMainSearch(menuRequest);
        ResultSet rs = search(menuRequest.getToken(), query);

        Path currentURI = menuRequest.getCurrentURI();
        String[] childNames = menuRequest.getChildNames();
        MenuItem<PropertySet> parent = null;

        List<MenuItem<PropertySet>> items = new ArrayList<MenuItem<PropertySet>>();
        Map<String, MenuItem<PropertySet>> nameItemMap = new HashMap<String, MenuItem<PropertySet>>();

        for (int i = 0; i < rs.getSize(); i++) {
            PropertySet resource = rs.getResult(i);

            // Exclude?
            if (menuRequest.getExcludedChildren().contains(resource.getName())) {
                continue;
            }

            MenuItem<PropertySet> item = buildItem(resource);
            Path uri = resource.getURI();

            // Hidden?
            PropertyTypeDefinition hiddenPropDef = this.menuGenerator.getHiddenPropDef();
            if (hiddenPropDef != null && resource.getProperty(hiddenPropDef) != null) {
                if (!uri.equals(menuRequest.getURI())) {
                    continue;
                }
            }

            // Parent?
            if (uri.equals(menuRequest.getURI())) {
                parent = item;

                String title = menuRequest.getParentTitle();

                if (title != null) {
                    if (!title.equals("")) {
                        parent.setTitle(title);
                    }
                }

                continue;
            }

            // Active item?
            if (isActive(currentURI, uri)) {
                item.setActive(true);
                menu.setActiveItem(item);
            }

            nameItemMap.put(resource.getName(), item);
            items.add(item);
        }

        if (childNames != null) {
            items = sortSpecifiedOrder(childNames, nameItemMap);
        } else {
            items = sortByOrder(items, menuRequest.getLocale(), true);
        }

        // Insert parent first in list
        if (parent != null) {
            parent.setLabel(parent.getLabel() + " parent-folder");
            items.add(0, parent);

            if (menu.getActiveItem() == null
                    && (menuRequest.getURI().isAncestorOf(menuRequest.getCurrentURI()) || menuRequest.getURI().equals(
                            menuRequest.getCurrentURI()))) {
                parent.setActive(true);
                menu.setActiveItem(parent);
            }
        }

        menu.addAllItems(items);

        return menu;
    }

    private Query buildMainSearch(ListMenuRequest menuRequest) {
        Path uri = menuRequest.getURI();
        int startDepth = uri.getDepth() + 1;
        AndQuery query = new AndQuery();

        String[] childNames = menuRequest.getChildNames();

        if (childNames == null) {
            // Get all children
            query.add(getChildrenQuery(uri, startDepth));
        } else {
            query.add(getRequestedChildren(uri, childNames));
        }

        if (menuRequest.isParentIncluded()) {
            OrQuery or = new OrQuery();
            or.add(query);
            or.add(new UriTermQuery(uri.toString(), TermOperator.EQ));
            return or;
        }
        return query;
    }

    private Query getRequestedChildren(Path uri, String[] childNames) {

        if (childNames.length == 1) {
            String name = childNames[0];
            return getUriQuery(uri, name);
        }

        // An explicit list of child names is provided
        OrQuery orQ = new OrQuery();
        for (int i = 0; i < childNames.length; i++) {
            String name = childNames[i];
            orQ.add(getUriQuery(uri, name));
        }
        return orQ;

    }

    private Query getUriQuery(Path uri, String name) {
        if (name.indexOf("/") != -1) {
            throw new DecoratorComponentException("Invalid child name: '" + name + "'");
        }
        String childURI = uri.extend(name.trim()).toString();
        return new UriTermQuery(childURI, TermOperator.EQ);
    }

    // List all children based on depth:
    private Query getChildrenQuery(Path uri, int depth) {
        AndQuery q = new AndQuery();
        String uriString = uri.toString();
        if (!uri.isRoot()) {
            uriString += "/";
        }
        q.add(new UriPrefixQuery(uriString));
        q.add(new UriDepthQuery(depth));
        return q;
    }

    private ResultSet doSubSearch(ListMenuRequest menuRequest) {

        OrQuery orQuery = new OrQuery();

        int startDepth = menuRequest.getURI().getDepth() + 1;
        List<Path> uris = menuRequest.getCurrentURI().getPaths();
        int maxDepth = startDepth + menuRequest.getDepth() - 1;
        maxDepth = Math.min(maxDepth, uris.size());

        for (int i = startDepth; i < maxDepth; i++) {
            Path uri = uris.get(i);
            orQuery.add(getChildrenQuery(uri, i + 1));
        }

        return search(menuRequest.getToken(), orQuery);
    }

    private ResultSet search(String token, Query query) {

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();

        // We are searching for collections
        AndQuery q = new AndQuery();
        q.add(new TypeTermQuery(this.menuGenerator.getCollectionResourceType().getQName(), TermOperator.IN));
        q.add(query);

        ConfigurablePropertySelect select = new ConfigurablePropertySelect();
        select.addPropertyDefinition(this.menuGenerator.getTitlePropDef());
        select.addPropertyDefinition(this.menuGenerator.getNavigationTitlePropDef());
        PropertyTypeDefinition hiddenPropDef = this.menuGenerator.getHiddenPropDef();
        if (hiddenPropDef != null) {
            select.addPropertyDefinition(hiddenPropDef);
        }
        PropertyTypeDefinition importancePropDef = this.menuGenerator.getImportancePropDef();
        if (importancePropDef != null) {
            select.addPropertyDefinition(importancePropDef);
        }

        Search search = new Search();
        if (RequestContext.getRequestContext().isPreviewUnpublished()) {
            search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED_COLLECTIONS, Search.FilterFlag.UNPUBLISHED);
        }
        search.setSorting(null);
        search.setQuery(q);
        search.setLimit(this.searchLimit);
        search.setPropertySelect(select);

        return repository.search(token, search);
    }

    private boolean isActive(Path currentURI, Path uri) {
        return currentURI.equals(uri) || uri.isAncestorOf(currentURI);
    }

    private List<MenuItem<PropertySet>> sortSpecifiedOrder(String[] childNames,
            Map<String, MenuItem<PropertySet>> nameItemMap) {

        List<MenuItem<PropertySet>> result = new ArrayList<MenuItem<PropertySet>>();
        for (String name : childNames) {
            name = name.trim();
            MenuItem<PropertySet> item = nameItemMap.get(name);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

	protected List<MenuItem<PropertySet>> sortByOrder(List<MenuItem<PropertySet>> items, Locale locale, boolean ascendingSort) {
        PropertyTypeDefinition navigationTitlePropDef = this.menuGenerator.getNavigationTitlePropDef();
		PropertyTypeDefinition importancePropDef = this.menuGenerator.getImportancePropDef();
		
		ListMenuComparator listmenuComparator = new ListMenuComparator(locale, importancePropDef,
				navigationTitlePropDef);
		if (!ascendingSort) {
			listmenuComparator.setAscending(false);
		}
		Collections.sort(items, listmenuComparator);
        return items;
    }

    /**
     * Add sub menu if current uri is below uri
     */
    private ListMenu<PropertySet> buildSubMenu(ListMenuRequest menuRequest, String requestURI) {

        ResultSet rs = doSubSearch(menuRequest);

        if (rs == null || rs.getSize() == 0) {
            return null;
        }

        Map<Path, List<PropertySet>> childMap = new HashMap<Path, List<PropertySet>>();

        for (PropertySet resource : rs.getAllResults()) {

            Path uri = resource.getURI();
            Path parentURI = uri.getParent();

            List<PropertySet> childList = childMap.get(parentURI);
            if (childList == null) {
                childList = new ArrayList<PropertySet>();
                childMap.put(parentURI, childList);
            }

            // Exclude?
            if (menuRequest.getExcludedChildren().contains(resource.getName())) {
                continue;
            }

            // Hidden?
            PropertyTypeDefinition hiddenPropDef = this.menuGenerator.getHiddenPropDef();
            if (hiddenPropDef != null && resource.getProperty(hiddenPropDef) != null
                    && !requestURI.startsWith(uri.toString())) {
                continue;
            }
            childList.add(resource);
        }

        int rootDepth = menuRequest.getURI().getDepth() + 1;
        List<Path> uris = menuRequest.getCurrentURI().getPaths();
        Path rootUri = uris.get(rootDepth);

        return buildSubItems(rootUri, childMap, menuRequest);
    }

    private ListMenu<PropertySet> buildSubItems(Path childrenKey, Map<Path, List<PropertySet>> childMap,
            ListMenuRequest menuRequest) {

        List<MenuItem<PropertySet>> items = new ArrayList<MenuItem<PropertySet>>();
        List<PropertySet> children = childMap.get(childrenKey);

        if (children == null) {
            return null;
        }

        for (PropertySet resource : children) {

            // Exclude?
            if (menuRequest.getExcludedChildren().contains(resource.getName())) {
                continue;
            }

            MenuItem<PropertySet> item = buildItem(resource);

            items.add(item);

            if (isActive(menuRequest.getCurrentURI(), resource.getURI())) {
                item.setSubMenu(buildSubItems(resource.getURI(), childMap, menuRequest));
                item.setActive(true);
            }
        }
        items = sortByOrder(items, menuRequest.getLocale(), true);
        ListMenu<PropertySet> submenu = new ListMenu<PropertySet>();
        submenu.addAllItems(items);
        return submenu;
    }

    protected MenuItem<PropertySet> buildItem(PropertySet resource) {
        MenuItem<PropertySet> item = new MenuItem<PropertySet>(resource);

        // Url
        Path uri = resource.getURI();
        URL url = this.menuGenerator.getViewService().constructURL(uri);
        // Know it's a folder, append "/"
        url.setCollection(true);
        item.setUrl(url);

        // Label
        String label = resource.getName().replace(' ', '-');
        if (label.equals("/")) {
            label = "root-folder";
        }
        item.setLabel(label);

        // Title
        String title = null;

        PropertyTypeDefinition navigationTitlePropDef = this.menuGenerator.getNavigationTitlePropDef();
        Property navigationTitleProperty = resource.getProperty(navigationTitlePropDef);
        if (navigationTitleProperty != null) {
            title = navigationTitleProperty.getStringValue();
        }
        if (title == null) {
            PropertyTypeDefinition titlePropDef = this.menuGenerator.getTitlePropDef();
            Property titleProperty = resource.getProperty(titlePropDef);
            title = (titleProperty != null) ? titleProperty.getStringValue() : label;
        }
        item.setTitle(title);

        return item;
    }

    private static class ListMenuRequest {

        private Path uri;
        private int displayFromLevel = -1;
        private Path currentURI;
        private Path currentFolder;
        private boolean parentIncluded;
        private String parentTitle;
        private String[] childNames;
        private String style;
        private int depth = DEFAULT_DEPTH;
        private Locale locale;
        private String token;
        private List<String> excludedChildren = new ArrayList<String>();

        ListMenuRequest(DecoratorRequest request) {
            RequestContext requestContext = RequestContext.getRequestContext();
            this.currentURI = requestContext.getResourceURI();
            this.currentFolder = requestContext.getCurrentCollection();
            this.locale = request.getLocale();

            String uri = request.getStringParameter(PARAMETER_URI);
            String displayFromLevel = request.getStringParameter(PARAMETER_DISPLAY_FROM_LEVEL);

            if ((uri == null || "".equals(uri.trim()))
                    && (displayFromLevel == null || "".equals(displayFromLevel.trim()))) {
                throw new DecoratorComponentException("One of parameters '" + PARAMETER_URI + "' or '"
                        + PARAMETER_DISPLAY_FROM_LEVEL + "' must be specified");
            }
            if ((uri != null && !"".equals(uri.trim()))
                    && (displayFromLevel != null && !"".equals(displayFromLevel.trim()))) {
                throw new DecoratorComponentException("At most one of parameters '" + PARAMETER_URI + "' or '"
                        + PARAMETER_DISPLAY_FROM_LEVEL + "' can be specified");
            }

            if (uri != null && !"".equals(uri.trim())) {
                if (!"/".equals(uri) && uri.endsWith("/")) {
                    uri = uri.substring(0, uri.length() - 1);
                }
                if (!uri.startsWith("/")) {
                    uri = this.currentFolder.expand(uri).toString();
                }
                this.uri = Path.fromString(uri);
            }

            if (displayFromLevel != null && !"".equals(displayFromLevel.trim())) {
                int level = Integer.parseInt(displayFromLevel);
                if (level <= 0) {
                    throw new DecoratorComponentException("Parameter '" + PARAMETER_DISPLAY_FROM_LEVEL
                            + "' must be an integer > 0");
                }

                if (level <= requestContext.getCurrentCollection().getPaths().size()) {
                    this.uri = requestContext.getCurrentCollection().getPaths().get(level - 1);
                }
                this.displayFromLevel = level;
            }

            boolean authenticated = "true".equals(request.getStringParameter(PARAMETER_AUTENTICATED));

            // VTK-2460
            if (requestContext.isViewUnauthenticated()) {
                authenticated = false;
            }

            if (authenticated) {
                this.token = requestContext.getSecurityToken();
            }

            this.style = request.getStringParameter(PARAMETER_STYLE);
            if (this.style == null) {
                this.style = DEFAULT_STYLE;
            } else if (!VALID_STYLES.contains(this.style)) {
                throw new DecoratorComponentException("Invalid value for parameter 'style': must be one of "
                        + VALID_STYLES.toString());
            }

            this.parentIncluded = "true".equals(request.getStringParameter(PARAMETER_INCLUDE_PARENT));

            if (this.parentIncluded) {
                if (request.getStringParameter(PARAMETER_PARENT_FOLDER_TITLE) != null) {
                    this.parentTitle = request.getStringParameter(PARAMETER_PARENT_FOLDER_TITLE);
                }

            }

            String includeChildrenParam = request.getStringParameter(PARAMETER_INCLUDE_CHILDREN);
            if (includeChildrenParam != null) {
                if ("".equals(includeChildrenParam.trim())) {
                    throw new DecoratorComponentException("Invalid value for parameter '" + PARAMETER_INCLUDE_CHILDREN
                            + "': must provide at least one child name");
                }
                this.childNames = includeChildrenParam.split(",");
            }
            String excludeChildrenParam = request.getStringParameter(PARAMETER_EXCLUDE_CHILDREN);
            if (excludeChildrenParam != null) {
                if (this.childNames != null) {
                    throw new DecoratorComponentException("Cannot use both parameters '" + PARAMETER_INCLUDE_CHILDREN
                            + "' and '" + PARAMETER_EXCLUDE_CHILDREN + "'");
                }
                for (String s : excludeChildrenParam.split(",")) {
                    getExcludedChildren().add(s.trim());
                }
                if (this.getExcludedChildren().size() == 0) {
                    throw new DecoratorComponentException("Invalid value for parameter '" + PARAMETER_EXCLUDE_CHILDREN
                            + "': must provide at least one child name");
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
        }

        public Path getURI() {
            return this.uri;
        }

        public int getDisplayFromLevel() {
            return this.displayFromLevel;
        }

        public Path getCurrentURI() {
            return this.currentURI;
        }

        public Path getCurrentFolder() {
            return this.currentFolder;
        }

        public boolean isParentIncluded() {
            return this.parentIncluded;
        }

        public String getParentTitle() {
            return this.parentTitle;
        }

        public String[] getChildNames() {
            return this.childNames;
        }

        public String getStyle() {
            return this.style;
        }

        public int getDepth() {
            return this.depth;
        }

        public Locale getLocale() {
            return this.locale;
        }

        public String getToken() {
            return token;
        }

        public List<String> getExcludedChildren() {
            return excludedChildren;
        }
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public void setSearchLimit(int searchLimit) {
        if (searchLimit <= 0) {
            throw new IllegalArgumentException("JavaBean property 'searchLimit' must be a positive integer");
        }
        this.searchLimit = searchLimit;
    }

    protected String getDescriptionInternal() {
        return "Displays a menu based on the subfolders of a specified folder (path)";
    }

    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(PARAMETER_URI, PARAMETER_URI_DESC);
        map.put(PARAMETER_STYLE, PARAMETER_STYLE_DESC);
        map.put(PARAMETER_INCLUDE_CHILDREN, PARAMETER_INCLUDE_CHILDREN_DESC);
        map.put(PARAMETER_EXCLUDE_CHILDREN, PARAMETER_EXCLUDE_CHILDREN_DESC);
        map.put(PARAMETER_INCLUDE_PARENT, PARAMETER_INCLUDE_PARENT_DESC);
        map.put(PARAMETER_PARENT_FOLDER_TITLE, PARAMETER_PARENT_FOLDER_TITLE_DESC);
        map.put(PARAMETER_AUTENTICATED, PARAMETER_AUTENTICATED_DESC);
        map.put(PARAMETER_DEPTH, PARAMETER_DEPTH_DESC);
        map.put(PARAMETER_DISPLAY_FROM_LEVEL, PARAMETER_DISPLAY_FROM_LEVEL_DESC);
        return map;
    }

}
