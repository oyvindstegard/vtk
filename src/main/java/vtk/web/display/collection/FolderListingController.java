package vtk.web.display.collection;

import vtk.repository.Resource;
import vtk.web.display.listing.ListingPager;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

public class FolderListingController extends AbstractCollectionListingController {

    @Override
    public void runSearch(
            HttpServletRequest request,
            Resource collection,
            Map<String, Object> model,
            int pageLimit
    ) throws Exception {
        return;
    }
}
