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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;

import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.search.PropertySelect;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.TypeTermQuery;
import vtk.repository.search.query.UriDepthQuery;
import vtk.repository.search.query.UriPrefixQuery;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.decorating.components.DecoratorComponentException;
import vtk.web.view.components.menu.ListMenu;

/**
 * <p>
 * The following input is evaluated
 * </p>
 * <ul>
 * <li>title - menu title</li>
 * <li>sort - the property to sort results by</li>
 * <li>direction - the sort direction (ascending / descending)</li>
 * <li>result-sets - the number of &lt;ul&gt; lists to split the result into</li>
 * <li>group-result-sets-by - the number of results-sets in grouping divs</li>
 * <li>freeze-at-level - at which level the subfolder-listing should freeze</li>
 * <li>exclude-folders - comma-separated list with relative paths to folders
 * which should be excluded</li>
 * <li>authenticated - default is listing only read-for-all resources</li>
 * <li>depth - specifies number of levels to retrieve subfolders from</li>
 * </ul>
 */
public class SubFolderMenuComponent extends ListMenuComponent {

    private static final String DESCRIPTION = "Lists the child folders of the current folder";

    protected static final String PARAMETER_TITLE = "title";
    private static final String PARAMETER_TITLE_DESC = "The menu title";

    protected static final String PARAMETER_SORT = "sort";
    private static final String PARAMETER_SORT_DESC = "The name of a property to sort results by. Legal values are ('name', 'title'). "
            + "The default property is 'title'";

    protected static final String PARAMETER_SORT_DIRECTION = "direction";
    private static final String PARAMETER_SORT_DIRECTION_DESC = "The sort direction. Legal values are 'asc', 'desc'. The default value is 'asc' ";

    protected static final String PARAMETER_RESULT_SETS = "result-sets";
    private static final String PARAMETER_RESULT_SETS_DESC = "The number of result sets to split the result into. The default value is '1'";

    protected static final String PARAMETER_GROUP_RESULT_SETS_BY = "group-result-sets-by";
    private static final String PARAMETER_GROUP_RESULT_SETS_BY_DESC = "The number of results-sets in grouping divs";

    protected static final String PARAMETER_FREEZE_AT_LEVEL = "freeze-at-level";
    private static final String PARAMETER_FREEZE_AT_LEVEL_DESC = "At which level the subfolder-listing should freeze and show the same listing further down. The default is none.";

    protected static final String PARAMETER_EXCLUDE_FOLDERS = "exclude-folders";
    private static final String PARAMETER_EXCLUDE_FOLDERS_DESC = "Commma-separated list with relative paths to folders which should not be displayed in the list";

    protected static final String PARAMETER_URI = "uri";
    private static final String PARAMETER_URI_DESC = "The URI (path) to the selected folder.";

    protected static final String PARAMETER_AS_CURRENT_USER = "authenticated";
    private static final String PARAMETER_AS_CURRENT_USER_DESC = "The default is that only resources readable for everyone is listed. "
            + "If this is set to 'true', the listing is done as the currently " + "logged in user (if any)";

    protected static final String PARAMETER_DEPTH = "depth";
    private static final String PARAMETER_DEPTH_DESC = "Specifies the number of levels to retrieve subfolders for. The default value is '1' ";

    protected static final String PARAMETER_DISPLAY_FROM_LEVEL = "display-from-level";
    private static final String PARAMETER_DISPLAY_FROM_LEVEL_DESC = "Defines the starting URI level for the subfolder-menu";

    protected static final String PARAMETER_MAX_NUMBER_OF_CHILDREN = "max-number-of-children";
    private static final String PARAMETER_MAX_NUMBER_OF_CHILDREN_DESC = "Defines the maximum number of children displayed for each element";

    protected static final String PARAMETER_DISPLAY = "display";
    private static final String PARAMETER_DISPLAY_DESC = "Specifies how to display the subfolder-menu. The default is normal lists. 'comma-separated' separates sublist-elements with commas.";

    private static Logger logger = LoggerFactory.getLogger(SubFolderMenuComponent.class);

    @Override
    public void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {

        MenuRequest menuRequest = this.menuGenerator.getMenuRequest(request);

        int currentLevel = menuRequest.getCurrentCollectionUri().getDepth() + 1;
        if (menuRequest.getDisplayFromLevel() != -1) {
            if (currentLevel < menuRequest.getDisplayFromLevel()) {
                return;
            }
        }
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.isViewUnauthenticated() ? null : requestContext.getSecurityToken(); // VTK-2460
        Repository repository = requestContext.getRepository();
		Path uri = requestContext.getResourceURI();

		boolean ascendingSort = true;
		Resource currentResource = null;
		String sortDirectionParam = request.getStringParameter(SubFolderMenuComponent.PARAMETER_SORT_DIRECTION);

		if (sortDirectionParam == null) {
		try {
			currentResource = repository.retrieve(token, uri, true);
			if (currentResource.getProperty(menuGenerator.getSortDescendingPropDef()) != null) {
				if (currentResource.getProperty(menuGenerator.getSortDescendingPropDef()).getBooleanValue()) {
					ascendingSort = false;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			}
		} else {
			if ("asc".equals(sortDirectionParam)) {
				ascendingSort = true;
			} else if ("desc".equals(sortDirectionParam)) {
				ascendingSort = false;
			} else {
				throw new DecoratorComponentException("Illegal value for parameter '"
						+ SubFolderMenuComponent.PARAMETER_SORT_DIRECTION + "': '" + sortDirectionParam
						+ "' (must be one of 'asc', 'desc')");
			}
		}

        Search search = buildSearch(requestContext, menuRequest);

        ResultSet rs = repository.search(token, search);
        if (logger.isDebugEnabled()) {
            logger.debug("Executed search: " + search + ", hits: " + rs.getSize());
        }
		ListMenu<PropertySet> menu = this.menuGenerator.buildListMenu(rs, menuRequest, this.modelName, ascendingSort);
		Map<String, Object> menuModel = this.menuGenerator.buildMenuModel(menu, menuRequest, ascendingSort);
        model.put(this.modelName, menuModel);
        if (logger.isDebugEnabled()) {
            logger.debug("Built model: " + model + " from menu: " + menu);
        }
    }

    private Search buildSearch(RequestContext requestContext, MenuRequest menuRequest) {

        Path uri = menuRequest.getCurrentCollectionUri();

        int depth = uri.getDepth() + 1;

        AndQuery mainQuery = new AndQuery();
        mainQuery.add(new UriPrefixQuery(uri.toString()));

        if (menuRequest.getDepth() > 1) {
            // Needs search support for this:
            // query.add(new UriDepthTermQuery).append(menuRequest.getDepth(),
            // TermOperator.GT);
            OrQuery depthQuery = new OrQuery();
            for (int i = 0; i < menuRequest.getDepth(); i++) {
                depthQuery.add(new UriDepthQuery(depth + i));
            }
            mainQuery.add(depthQuery);
        } else {
            mainQuery.add(new UriDepthQuery(depth));
        }

        OrQuery includeFolders = new OrQuery();
        if (menuRequest.getIncludeURIs() != null && !menuRequest.getIncludeURIs().isEmpty()) {
            for (Iterator<Path> i = menuRequest.getIncludeURIs().iterator(); i.hasNext();) {
                Path exUri = i.next();
                includeFolders.add(new UriPrefixQuery(exUri.toString(), false));
            }
        }
        mainQuery.add(includeFolders);
        mainQuery.add(new TypeTermQuery(menuRequest.getCollectionResourceType().getName(), TermOperator.IN));
        Search search = new Search();
        if (RequestContext.getRequestContext().isPreviewUnpublished()) {
            search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
        }
        search.setQuery(mainQuery);
        search.setLimit(menuRequest.getSearchLimit());
        search.setPropertySelect(PropertySelect.ALL_PROPERTIES);
        return search;
    }

    @Override
    protected String getDescriptionInternal() {
        return DESCRIPTION;
    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(PARAMETER_TITLE, PARAMETER_TITLE_DESC);
        map.put(PARAMETER_SORT, PARAMETER_SORT_DESC);
        map.put(PARAMETER_SORT_DIRECTION, PARAMETER_SORT_DIRECTION_DESC);
        map.put(PARAMETER_RESULT_SETS, PARAMETER_RESULT_SETS_DESC);
        map.put(PARAMETER_GROUP_RESULT_SETS_BY, PARAMETER_GROUP_RESULT_SETS_BY_DESC);
        map.put(PARAMETER_DISPLAY_FROM_LEVEL, PARAMETER_DISPLAY_FROM_LEVEL_DESC);
        map.put(PARAMETER_URI, PARAMETER_URI_DESC);
        map.put(PARAMETER_FREEZE_AT_LEVEL, PARAMETER_FREEZE_AT_LEVEL_DESC);
        map.put(PARAMETER_EXCLUDE_FOLDERS, PARAMETER_EXCLUDE_FOLDERS_DESC);
        map.put(PARAMETER_AS_CURRENT_USER, PARAMETER_AS_CURRENT_USER_DESC);
        map.put(PARAMETER_DEPTH, PARAMETER_DEPTH_DESC);
        map.put(PARAMETER_MAX_NUMBER_OF_CHILDREN, PARAMETER_MAX_NUMBER_OF_CHILDREN_DESC);
        map.put(PARAMETER_DISPLAY, PARAMETER_DISPLAY_DESC);
        return map;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        if (this.searchLimit <= 0) {
            throw new BeanInitializationException("JavaBean property '" + searchLimit + "' must be a positive integer");
        }
    }

}
