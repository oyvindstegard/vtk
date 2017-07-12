package vtk.web.decorating.components;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.query.AbstractMultipleQuery;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.TypeTermQuery;
import vtk.repository.search.query.UriPrefixQuery;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;

public class NumberOfResourcesComponent extends ViewRenderingDecoratorComponent {

    private static final String DEFAULT_RESOURCE_TYPE = "file";

    @Override
    protected void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext(request.getServletRequest());
        String token = requestContext.getSecurityToken();

        String resourceType = DEFAULT_RESOURCE_TYPE;
        String requestedResourceType = request.getStringParameter("resource-type");
        resourceType = requestedResourceType != null ? requestedResourceType : resourceType;

        AndQuery query = new AndQuery();
        query.add(new TypeTermQuery(resourceType, TermOperator.IN));

        // Check for any other requested or excluded folders
        String requestedFoldersString = request.getStringParameter("folders");
        String requestedExcludeFoldersString = request.getStringParameter("exclude-folders");

        // No specific folder request, scope is current collection
        if (StringUtils.isBlank(requestedFoldersString) && StringUtils.isBlank(requestedExcludeFoldersString)) {
            query.add(new UriPrefixQuery(requestContext.getCurrentCollection().toString()));
        } else {
            // Check requested folders against excluded and set up scope query
            Set<Path> folderPaths = getValidPaths(requestedFoldersString);
            Set<Path> excludePaths = getValidPaths(requestedExcludeFoldersString);
            // Exclusion overrides inclusion
            folderPaths.removeAll(excludePaths);
            addFolderQuery(query, folderPaths, false);
            addFolderQuery(query, excludePaths, true);
        }

        // No sorting required, we just want count
        Search search = new Search();
        if (RequestContext.getRequestContext(request.getServletRequest()).isPreviewUnpublished()) {
            search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
        }
        search.setLimit(1);
        search.setQuery(query);

        Repository repository = requestContext.getRepository();

        ResultSet results = repository.search(token, search);
        model.put("numberOfResources", results.getTotalHits());
    }

    private Set<Path> getValidPaths(String folderPathsStrings) {
        Set<Path> paths = new HashSet<>();
        if (StringUtils.isNotBlank(folderPathsStrings)) {
            String[] folders = folderPathsStrings.split(",");
            for (String folder : folders) {
                try {
                    Path path = Path.fromString(folder.trim());
                    paths.add(path);
                } catch (IllegalArgumentException iae) {
                    // Ignore, invalid path string
                }
            }
        }
        return paths;
    }

    private void addFolderQuery(AndQuery query, Set<Path> paths, boolean inverted) {
        if (!paths.isEmpty()) {
            if (paths.size() == 1) {
                query.add(new UriPrefixQuery(paths.iterator().next().toString(), inverted));
            } else {
                AbstractMultipleQuery multiQuery = !inverted ? new OrQuery() : new AndQuery();
                for (Path path : paths) {
                    multiQuery.add(new UriPrefixQuery(path.toString(), inverted));
                }
                query.add(multiQuery);
            }
        }
    }

}
