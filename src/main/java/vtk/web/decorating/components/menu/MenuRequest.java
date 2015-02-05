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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.ResourceTypeDefinition;
import vtk.repository.search.PropertySelect;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.TypeTermQuery;
import vtk.repository.search.query.UriDepthQuery;
import vtk.repository.search.query.UriPrefixQuery;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.components.DecoratorComponentException;

public class MenuRequest {

    private Path currentCollectionUri;
    private String title;
    private PropertyTypeDefinition sortProperty;
    private boolean ascendingSort = true;
    private boolean sortByName = false;
    private int resultSets = 1;
    private int groupResultSetsBy = 0;
    private int freezeAtLevel = 0;
    private int depth = 1;
    private int displayFromLevel = -1;
    private int maxNumberOfChildren = Integer.MAX_VALUE;
    private String display;
    private Locale locale;
    private String token;
    private int searchLimit = ListMenuComponent.DEFAULT_SEARCH_LIMIT;
    private List<Path> includeURIs;
    private static final int PARAMETER_RESULT_SETS_MAX_VALUE = 30;
    private static final int PARAMETER_GROUP_RESULT_SETS_BY_MAX_VALUE = 30;

    private PropertyTypeDefinition titlePropDef;
    private ResourceTypeDefinition collectionResourceType;

    public MenuRequest(Path currentCollectionUri, String title, PropertyTypeDefinition sortProperty,
            boolean ascendingSort, boolean sortByName, int resultSets, int groupResultSetsBy, int freezeAtLevel,
            int depth, int displayFromLevel, int maxNumberOfChildren, String display, Locale locale, String token,
            int searchLimit, ArrayList<Path> includeURIs) {

        this.currentCollectionUri = currentCollectionUri;
        this.title = title;
        this.sortProperty = sortProperty;
        this.ascendingSort = ascendingSort;
        this.sortByName = sortByName;
        this.resultSets = resultSets;
        this.groupResultSetsBy = groupResultSetsBy;
        this.freezeAtLevel = freezeAtLevel;
        this.depth = depth;
        this.displayFromLevel = displayFromLevel;
        this.maxNumberOfChildren = maxNumberOfChildren;
        this.display = display;
        this.locale = locale;
        this.token = token;
        this.searchLimit = searchLimit;
        this.includeURIs = includeURIs;
    }

    public MenuRequest(DecoratorRequest request, PropertyTypeDefinition titlePropDef,
            ResourceTypeDefinition collectionResourceType) {

        this.titlePropDef = titlePropDef;
        this.collectionResourceType = collectionResourceType;

        RequestContext requestContext = RequestContext.getRequestContext();
        this.currentCollectionUri = requestContext.getCurrentCollection();

        boolean asCurrentUser = "true".equals(request
                .getStringParameter(SubFolderMenuComponent.PARAMETER_AS_CURRENT_USER));
        if (asCurrentUser) {
            this.token = requestContext.getSecurityToken();
        }

        this.title = request.getStringParameter(SubFolderMenuComponent.PARAMETER_TITLE);

        initSortField(request);

        String resultSets = request.getStringParameter(SubFolderMenuComponent.PARAMETER_RESULT_SETS);
        if (resultSets != null) {
            try {
                this.resultSets = Integer.parseInt(resultSets);
            } catch (Throwable t) {
                throw new DecoratorComponentException("Illegal value for parameter '"
                        + SubFolderMenuComponent.PARAMETER_RESULT_SETS + "': " + resultSets);
            }
            if (this.resultSets <= 0 || this.resultSets > PARAMETER_RESULT_SETS_MAX_VALUE) {
                throw new DecoratorComponentException("Illegal value for parameter '"
                        + SubFolderMenuComponent.PARAMETER_RESULT_SETS + "': " + this.resultSets
                        + ": must be a positive number between 1 and " + PARAMETER_RESULT_SETS_MAX_VALUE);
            }
        }

        String groupResultsBy = request.getStringParameter(SubFolderMenuComponent.PARAMETER_GROUP_RESULT_SETS_BY);
        if (groupResultsBy != null) {
            try {
                this.groupResultSetsBy = Integer.parseInt(groupResultsBy);
            } catch (Throwable t) {
                throw new DecoratorComponentException("Illegal value for parameter '"
                        + SubFolderMenuComponent.PARAMETER_GROUP_RESULT_SETS_BY + "': " + groupResultsBy);
            }
            if (this.groupResultSetsBy <= 0 || this.groupResultSetsBy > PARAMETER_GROUP_RESULT_SETS_BY_MAX_VALUE) {
                throw new DecoratorComponentException("Illegal value for parameter '"
                        + SubFolderMenuComponent.PARAMETER_GROUP_RESULT_SETS_BY + "': " + this.groupResultSetsBy
                        + ": must be a positive number between 1 and " + PARAMETER_GROUP_RESULT_SETS_BY_MAX_VALUE);
            }
        }

        String uri = request.getStringParameter(SubFolderMenuComponent.PARAMETER_URI);
        String displayFromLevel = request.getStringParameter(SubFolderMenuComponent.PARAMETER_DISPLAY_FROM_LEVEL);
        if ((uri != null && !"".equals(uri)) && (displayFromLevel != null && !"".equals(displayFromLevel))) {
            throw new DecoratorComponentException("At most one of parameters '" + SubFolderMenuComponent.PARAMETER_URI
                    + "' or '" + SubFolderMenuComponent.PARAMETER_DISPLAY_FROM_LEVEL + "' can be specified");
        }
        if (uri != null && !"".equals(uri.trim())) {
            try {
                if (uri.startsWith("http://") || uri.startsWith("https://")) {

                } else {
                    if (!"/".equals(uri) && uri.endsWith("/")) {
                        uri = uri.substring(0, uri.length() - 1);
                    }
                }
                this.currentCollectionUri = Path.fromString(uri); // override
            } catch (Throwable t) {
                throw new DecoratorComponentException("Illegal value for parameter '"
                        + SubFolderMenuComponent.PARAMETER_URI + "': " + uri);
            }
        }

        try {
            setMaxNumberOfChildren(Integer.parseInt(request
                    .getStringParameter(SubFolderMenuComponent.PARAMETER_MAX_NUMBER_OF_CHILDREN)));
        } catch (NumberFormatException e) {
            // Not a required parameter
        }

        String displayParam = request.getStringParameter(SubFolderMenuComponent.PARAMETER_DISPLAY);
        if (displayParam != null && !"".equals(displayParam.trim())) {
            try {
                setDisplay(displayParam);
            } catch (Throwable t) {
                throw new DecoratorComponentException("Illegal value for parameter '"
                        + SubFolderMenuComponent.PARAMETER_DISPLAY + "': " + displayParam);
            }
        }

        String displayFromLevelParam = request.getStringParameter(SubFolderMenuComponent.PARAMETER_DISPLAY_FROM_LEVEL);
        if (displayFromLevelParam != null && !"".equals(displayFromLevelParam.trim())) {
            try {
                this.displayFromLevel = Integer.parseInt(displayFromLevelParam);
            } catch (Throwable t) {
                throw new DecoratorComponentException("Illegal value for parameter '"
                        + SubFolderMenuComponent.PARAMETER_DISPLAY_FROM_LEVEL + "': " + displayFromLevelParam);
            }

            if (this.displayFromLevel <= 0) {
                throw new DecoratorComponentException("Illegal value for parameter '"
                        + SubFolderMenuComponent.PARAMETER_DISPLAY_FROM_LEVEL + "': " + this.displayFromLevel
                        + ": must be a positive number between larger than 0");
            }

            if (this.displayFromLevel <= currentCollectionUri.getPaths().size()) {
                this.currentCollectionUri = currentCollectionUri.getPaths().get(this.displayFromLevel - 1);
            }
        }

        String freezeAtLevelParam = request.getStringParameter(SubFolderMenuComponent.PARAMETER_FREEZE_AT_LEVEL);
        if (freezeAtLevelParam != null) {
            try {
                this.freezeAtLevel = Integer.parseInt(freezeAtLevelParam);
            } catch (Throwable t) {
                throw new DecoratorComponentException("Illegal value for parameter '"
                        + SubFolderMenuComponent.PARAMETER_FREEZE_AT_LEVEL + "': " + freezeAtLevelParam);
            }
            if (this.freezeAtLevel <= 0) {
                throw new DecoratorComponentException("Illegal value for parameter '"
                        + SubFolderMenuComponent.PARAMETER_FREEZE_AT_LEVEL + "': " + this.freezeAtLevel
                        + ": must be a positive number larger than 0");
            } else {
                if (this.currentCollectionUri.getDepth() > (this.freezeAtLevel - 1)) {
                    this.currentCollectionUri = currentCollectionUri.getPaths().get(this.freezeAtLevel - 1);
                }
            }
        }

        String depthStr = request.getStringParameter(SubFolderMenuComponent.PARAMETER_DEPTH);
        if (depthStr != null) {
            try {
                int depth = Integer.parseInt(depthStr);
                if (depth < 1) {
                    throw new IllegalArgumentException("Depth must be an integer >= 1");
                }
                this.depth = depth;
            } catch (Throwable t) {
                throw new DecoratorComponentException("Illegal value for parameter '"
                        + SubFolderMenuComponent.PARAMETER_DEPTH + "': " + depthStr);
            }
        }

        List<Path> children = new ArrayList<Path>();
        Search search = getSearchForCollectionChildren(requestContext, currentCollectionUri, searchLimit);
        Repository repository = requestContext.getRepository();
        ResultSet rs = repository.search(token, search);
        for (Iterator<PropertySet> child = rs.iterator(); child.hasNext();) {
            PropertySet p = child.next();
            children.add(p.getURI());
        }
        this.includeURIs = children;

        String excludeFolders = request.getStringParameter(SubFolderMenuComponent.PARAMETER_EXCLUDE_FOLDERS);
        if (excludeFolders != null) {
            try {
                StringTokenizer excludeFoldersTokenized = new StringTokenizer(excludeFolders, ",");
                while (excludeFoldersTokenized.hasMoreTokens()) {
                    String excludedFolder = excludeFoldersTokenized.nextToken().trim();
                    Path theUri = this.currentCollectionUri.extend(excludedFolder);
                    includeURIs.remove(theUri);
                }
            } catch (Throwable t) {
                throw new DecoratorComponentException("Illegal value for parameter '"
                        + SubFolderMenuComponent.PARAMETER_EXCLUDE_FOLDERS + "': " + depthStr);
            }
        }

        this.locale = request.getLocale();
    }

    public PropertyTypeDefinition getTitlePropDef() {
        return titlePropDef;
    }

    public void setTitlePropDef(PropertyTypeDefinition titlePropDef) {
        this.titlePropDef = titlePropDef;
    }

    public ResourceTypeDefinition getCollectionResourceType() {
        return collectionResourceType;
    }

    public void setCollectionResourceType(ResourceTypeDefinition collectionResourceType) {
        this.collectionResourceType = collectionResourceType;
    }

    public Path getCurrentCollectionUri() {
        return this.currentCollectionUri;
    }

    public String getTitle() {
        return this.title;
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

    public int getGroupResultSetsBy() {
        return this.groupResultSetsBy;
    }

    public int getDisplayFromLevel() {
        return this.displayFromLevel;
    }

    public int getFreezeAtLevel() {
        return this.freezeAtLevel;
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

    private void initSortField(DecoratorRequest request) {
        String sortFieldParam = request.getStringParameter(SubFolderMenuComponent.PARAMETER_SORT);
        if (sortFieldParam != null) {
            if ("title".equals(sortFieldParam)) {
                this.sortProperty = this.titlePropDef;
            } else if ("name".equals(sortFieldParam)) {
                this.setSortByName(true);
            } else {
                throw new DecoratorComponentException("Illegal value for parameter '"
                        + SubFolderMenuComponent.PARAMETER_SORT + "': must be one of ('name', 'title')");
            }
        }

        String sortDirectionParam = request.getStringParameter(SubFolderMenuComponent.PARAMETER_SORT_DIRECTION);
        if (sortDirectionParam == null) {
            sortDirectionParam = "asc";
        }

        if ("asc".equals(sortDirectionParam)) {
            this.ascendingSort = true;
        } else if ("desc".equals(sortDirectionParam)) {
            this.ascendingSort = false;
        } else {
            throw new DecoratorComponentException("Illegal value for parameter '"
                    + SubFolderMenuComponent.PARAMETER_SORT_DIRECTION + "': '" + sortDirectionParam
                    + "' (must be one of 'asc', 'desc')");
        }
    }

    public void setMaxNumberOfChildren(int maxNumberOfChildren) {
        this.maxNumberOfChildren = maxNumberOfChildren;
    }

    public int getMaxNumberOfChildren() {
        return maxNumberOfChildren;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }

    public void setSortByName(boolean sortByName) {
        this.sortByName = sortByName;
    }

    public boolean isSortByName() {
        return sortByName;
    }

    public void setIncludeURIs(List<Path> includeURIs) {
        this.includeURIs = includeURIs;
    }

    public List<Path> getIncludeURIs() {
        return includeURIs;
    }

    private Search getSearchForCollectionChildren(RequestContext requestContext, Path currentCollectionUri,
            int searchLimit) {
        Path uri = currentCollectionUri;
        int depth = uri.getDepth() + 1;
        AndQuery mainQuery = new AndQuery();
        mainQuery.add(new UriDepthQuery(depth));
        mainQuery.add(new UriPrefixQuery(uri.toString()));
        mainQuery.add(new TypeTermQuery(this.collectionResourceType.getName(), TermOperator.IN));
        Search search = new Search();
        if (RequestContext.getRequestContext().isPreviewUnpublished()) {
            search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
        }
        search.setQuery(mainQuery);
        search.setLimit(searchLimit);
        search.setPropertySelect(PropertySelect.ALL_PROPERTIES);
        return search;
    }

    public int getSearchLimit() {
        return this.searchLimit;
    }

}
