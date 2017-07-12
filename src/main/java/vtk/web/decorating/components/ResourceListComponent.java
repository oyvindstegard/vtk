package vtk.web.decorating.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.TypeTermQuery;
import vtk.repository.search.query.UriPrefixQuery;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;

public class ResourceListComponent extends ViewRenderingDecoratorComponent {

    private static final String DEFAULT_RESOURCE_TYPE = "file";
    private static final int DEFAULT_NUMBER_OF_RESULTSETS = 2;

    @Override
    protected void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {

        String includeFolders = request.getStringParameter("folders");
        if (StringUtils.isBlank(includeFolders)) {
            throw new DecoratorComponentException("Parameter \"folders\" is required.");
        }

        String parentFolder = request.getStringParameter("parent-folder");
        String resourceType = DEFAULT_RESOURCE_TYPE;
        String requestedResourceType = request.getStringParameter("resource-type");
        resourceType = requestedResourceType != null ? requestedResourceType : resourceType;
        String maxItems = request.getStringParameter("max-items");
        String goToFolderLink = request.getStringParameter("go-to-folder-link");
        String resultSets = request.getStringParameter("result-sets");
        int numberOfResultSets = DEFAULT_NUMBER_OF_RESULTSETS;
        if (StringUtils.isNumeric(resultSets)) {
            numberOfResultSets = Integer.parseInt(resultSets);
        }
        model.put("resultSets", numberOfResultSets);

        List<Path> validPaths = getValidFolderPaths(includeFolders, parentFolder);
        List<Resource> validResources = new ArrayList<>();
        for (Path folder : validPaths) {

            RequestContext requestContext = RequestContext.getRequestContext(request.getServletRequest());
            String token = requestContext.getSecurityToken();
            Repository repository = requestContext.getRepository();

            Resource resource = null;
            try {
                resource = repository.retrieve(token, folder, true);
            } catch (Exception e) {
                // Resource not available, ignore (most likely not found)
                continue;
            }
            validResources.add(resource);

            AndQuery query = new AndQuery();
            query.add(new TypeTermQuery(resourceType, TermOperator.IN));
            query.add(new UriPrefixQuery(folder.toString()));

            Search search = new Search();
            if (RequestContext.getRequestContext(request.getServletRequest()).isPreviewUnpublished()) {
                search.removeFilterFlag(Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
            }
            if (maxItems != null) {
                try {
                    search.setLimit(Integer.parseInt(maxItems));
                } catch (NumberFormatException e) {
                }
            }

            // XXX Sorting?
            search.setSorting(null);
            // XXX Default limit?
            search.setQuery(query);
            ResultSet results = repository.search(token, search);

            model.put(folder.toString(), results.getAllResults());

            if ("true".equals(goToFolderLink)) {
                model.put("goToFolderLink", "true");
            }
        }

        model.put("folders", validResources);
        model.put("numberOfFolders", validResources.size());

    }

    private List<Path> getValidFolderPaths(String includeFolders, String parentFolder) {
        List<Path> validPaths = new ArrayList<>();
        String[] folders = includeFolders.split(",");
        Path parentPath = getValidPath(parentFolder);
        String parentPathString = parentPath != null ? parentPath.toString() : null;
        for (String folder : folders) {

            folder = folder.trim();

            Path validPath = null;
            if (parentPathString == null) {
                validPath = getValidPath(folder);
            } else {
                if (!folder.startsWith("/")) {
                    String extendedFolderWithParent = parentPathString.concat("/").concat(folder);
                    validPath = getValidPath(extendedFolderWithParent);
                } else {
                    validPath = getValidPath(folder);
                }
            }

            if (validPath != null) {
                validPaths.add(validPath);
            }

        }
        return validPaths;
    }

    private Path getValidPath(String pathString) {
        try {
            return Path.fromString(pathString);
        } catch (IllegalArgumentException iae) {
            // Invalid path, ignore
        }
        return null;
    }

}
