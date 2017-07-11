/* Copyright (c) 2011, University of Oslo, Norway
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
package vtk.web.display.collection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.RequestContext;
import vtk.web.display.listing.ListingPager;
import vtk.web.display.listing.ListingPagingLink;
import vtk.web.search.Listing;
import vtk.web.search.ListingEntry;
import vtk.web.search.SearchComponent;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class AlphabeticalCollectionListingController extends CollectionListingController {

    private PropertyTypeDefinition titlePropDef;
    private PropertyTypeDefinition displayTypePropDef;
    private SearchComponent alternateSearchComponent;

    @Override
    public void runSearch(HttpServletRequest request, Resource collection, Map<String, Object> model, int pageLimit)
            throws Exception {

        if (alternateSearchComponent != null) {
            Listing listing = alternateSearchComponent.execute(request, collection, 1, pageLimit, 0);
            if (listing != null && listing.size() > 0) {
                model.put("displayAlternateLink", "true");
            }
        }

        Property type = collection.getProperty(displayTypePropDef);
        if (type != null && "alphabetical".equals(type.getStringValue())) {
            getAlphabeticalOrdredListing(request, collection, model, pageLimit);
        } else {
            super.runSearch(request, collection, model, pageLimit);
        }
    }

    /*
     * Putting an alphabetical ordered list on the view model. No need to sort
     * the result as it is already sorted by the search component. The result is
     * a map consisting of a key that is the first character of the title and a
     * value that is a list of files starting with the key char. The key is
     * upper case.
     */
    public void getAlphabeticalOrdredListing(HttpServletRequest request, Resource collection,
            Map<String, Object> model, int pageLimit) throws Exception {

        int page = ListingPager.getPage(request, ListingPager.UPCOMING_PAGE_PARAM);
        int limit = pageLimit;
        int totalHits = 0;
        Map<String, List<ListingEntry>> alpthabeticalOrdredResult = new LinkedHashMap<>();
        List<Listing> results = new ArrayList<>();

        for (SearchComponent component : searchComponents) {
            Listing listing = component.execute(request, collection, page, limit, 0);
            results.add(listing);
            totalHits = listing.getTotalHits();
            List<ListingEntry> entries = listing.getEntries();
            List<ListingEntry> tmpFiles = new ArrayList<>();

            // array is convenient for string constructors
            char currentIndexChar[] = new char[1];
            for (int i = 0; i < entries.size(); i++) {
                ListingEntry entry = entries.get(i);
                PropertySet file = entry.getPropertySet();
                Property title = file.getProperty(titlePropDef);
                char firstCharInTitle = title.getStringValue().trim().charAt(0);
                if (i == 0) {
                    currentIndexChar[0] = firstCharInTitle;
                }
                if (currentIndexChar[0] != firstCharInTitle) {
                    String key = new String(currentIndexChar).toUpperCase();
                    if (alpthabeticalOrdredResult.get(key) != null) {
                        alpthabeticalOrdredResult.get(key).addAll(tmpFiles);
                    } else {
                        alpthabeticalOrdredResult.put(key, tmpFiles);
                    }
                    currentIndexChar[0] = firstCharInTitle;
                    tmpFiles = new ArrayList<>();
                }
                tmpFiles.add(entry);
            }
            if (tmpFiles.size() > 0) {
                String key = new String(currentIndexChar).toUpperCase();
                if (alpthabeticalOrdredResult.get(key) != null) {
                    alpthabeticalOrdredResult.get(key).addAll(tmpFiles);
                } else {
                    alpthabeticalOrdredResult.put(key, tmpFiles);
                }
            }
        }

        Service service = RequestContext.getRequestContext(request).getService();
        URL baseURL = service.urlConstructor(URL.create(request))
                .withURI(RequestContext.getRequestContext(request).getResourceURI())
                .constructURL();

        model.put("alpthabeticalOrdredResult", alpthabeticalOrdredResult);
        
        Optional<ListingPager.Pagination> pagination = 
                ListingPager.pagination(totalHits, pageLimit, baseURL, page);
        if (pagination.isPresent()) {
            List<ListingPagingLink> urls = pagination.get().pageThroughLinks();
            model.put(MODEL_KEY_PAGE_THROUGH_URLS, urls);
            model.put(MODEL_KEY_PAGINATION, pagination.get());
        }
        model.put(MODEL_KEY_PAGE, page);
        model.put(MODEL_KEY_SEARCH_COMPONENTS, results);
    }

    public void setTitlePropDef(PropertyTypeDefinition titlePropDef) {
        this.titlePropDef = titlePropDef;
    }

    public PropertyTypeDefinition getTitlePropDef() {
        return titlePropDef;
    }

    public void setDisplayTypePropDef(PropertyTypeDefinition displayTypePropDef) {
        this.displayTypePropDef = displayTypePropDef;
    }

    public PropertyTypeDefinition getDisplayTypePropDef() {
        return displayTypePropDef;
    }

    public void setAlternateSearchComponent(SearchComponent alternateSearchComponent) {
        this.alternateSearchComponent = alternateSearchComponent;
    }

}
