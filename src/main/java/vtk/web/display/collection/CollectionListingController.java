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
package vtk.web.display.collection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.decorating.components.CollectionListingHelper;
import vtk.web.display.listing.ListingPager;
import vtk.web.display.listing.ListingPagingLink;
import vtk.web.search.Listing;
import vtk.web.search.SearchComponent;
import vtk.web.service.Service;
import vtk.web.service.URL;
import vtk.web.servlet.ResourceAwareLocaleResolver;

public class CollectionListingController extends BaseCollectionListingController {

    protected List<SearchComponent> searchComponents;
    protected PropertyTypeDefinition hideIcon;
    protected CollectionListingHelper helper;
    private ResourceAwareLocaleResolver localeResolver;
    private boolean displayEditLinks;
    private boolean resolvePrincipalLink;
    private String customDisplay;
    private String emptyDisplay;

    @Override
    public void runSearch(HttpServletRequest request, Resource collection, Map<String, Object> model, int pageLimit)
            throws Exception {

        int page = ListingPager.getPage(request, ListingPager.UPCOMING_PAGE_PARAM);
        int offset = (page - 1) * pageLimit;
        int limit = pageLimit;
        int totalHits = 0;

        RequestContext requestContext = RequestContext.getRequestContext();
        Service service = requestContext.getService();
        Repository repository = requestContext.getRepository();
        Principal principal = requestContext.getPrincipal();

        List<Listing> results = new ArrayList<>();

        for (SearchComponent component : searchComponents) {

            Listing listing = component.execute(request, collection, page, limit, 0);
            totalHits += listing.getTotalHits();
            int numberOfFiles = listing.getEntries().size();

            // Add the listing to the results
            if (numberOfFiles > 0) {
                results.add(listing);
            }
            // Check previous result (by redoing the previous search),
            // to see if we need to adjust the offset.
            // XXX: is there a better way?
            if (numberOfFiles == 0 && offset > 0) {
                Listing prevListing = component.execute(request, collection, page - 1, limit, 0);
                if (prevListing.getEntries().size() > 0 && !prevListing.hasMoreResults()) {
                    offset -= prevListing.getEntries().size();
                }
            }

            // We have more results to display for this listing
            if (listing.hasMoreResults()) {
                break;
            }
            // Only include enough results to fill the page:
            if (numberOfFiles > 0) {
                limit -= listing.getEntries().size();
            }
        }

        if (resolvePrincipalLink && results.size() > 0 && helper != null) {
            Locale preferredLocale = localeResolver.resolveResourceLocale(collection);

            Set<PropertySet> allFiles = new HashSet<>();
            for (Listing l : results) {
                allFiles.addAll(l.getPropertySets());
            }

            Map<String, Principal> principalDocuments = helper.getPrincipalDocumentLinks(allFiles, preferredLocale,
                    null);
            model.put("principalDocuments", principalDocuments);
        }

        URL baseURL = URL.create(request);

        if (getHideIcon(collection)) {
            model.put("hideIcon", true);
        }

        if (displayEditLinks && helper != null) {
            model.put("editCurrentResource", helper.checkResourceForEditLink(repository, collection, principal));
        }

        Optional<ListingPager.Pagination> pagination = 
                ListingPager.pagination(totalHits, pageLimit, baseURL, page);
        if (pagination.isPresent()) {
            List<ListingPagingLink> urls = pagination.get().pageThroughLinks();
            model.put(MODEL_KEY_PAGE_THROUGH_URLS, urls);
            model.put(MODEL_KEY_PAGINATION, pagination.get());
        }

        model.put(MODEL_KEY_SEARCH_COMPONENTS, results);
        model.put(MODEL_KEY_PAGE, page);
        if (results.size() > 0 && results.get(0) != null) {
            model.put("numberOfRecords", getNumberOfRecords(page, pageLimit, results.get(0).size()));
        }

        if (customDisplay != null) {
            model.put("customDisplay", customDisplay);
        }
        if (emptyDisplay != null) {
            model.put("emptyDisplay", emptyDisplay);
        }
    }

    protected Map<String, Integer> getNumberOfRecords(int page, int pageLimit, int resultSize) {
        Map<String, Integer> numbers = new HashMap<>();
        int numberShownElements = ((page - 1) * pageLimit) + 1;
        int includingThisPage = ((page - 1) * pageLimit) + resultSize;
        numbers.put("elementsOnPreviousPages", numberShownElements);
        numbers.put("elementsIncludingThisPage", includingThisPage);
        return numbers;
    }

    protected boolean getHideIcon(Resource collection) {
        if (hideIcon == null)
            return false;
        Property p = collection.getProperty(hideIcon);
        if (p == null) {
            return false;
        }
        return p.getBooleanValue();
    }

    public void setHelper(CollectionListingHelper helper) {
        this.helper = helper;
    }

    @Required
    public void setSearchComponents(List<SearchComponent> searchComponents) {
        this.searchComponents = searchComponents;
    }

    @Required
    public void setHideIcon(PropertyTypeDefinition hideIcon) {
        this.hideIcon = hideIcon;
    }

    @Required
    public void setLocaleResolver(ResourceAwareLocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    public void setDisplayEditLinks(boolean displayEditLinks) {
        this.displayEditLinks = displayEditLinks;
    }

    public void setResolvePrincipalLink(boolean resolvePrincipalLink) {
        this.resolvePrincipalLink = resolvePrincipalLink;
    }

    public void setCustomDisplay(String customDisplay) {
        this.customDisplay = customDisplay;
    }
    
    public void setEmptyDisplay(String emptyDisplay) {
        this.emptyDisplay = emptyDisplay;
    }

}
