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
package vtk.web.display.collection.event.calendar;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Resource;
import vtk.web.RequestContext;
import vtk.web.display.collection.event.EventListingHelper;
import vtk.web.display.listing.ListingPager;
import vtk.web.display.listing.ListingPagingLink;
import vtk.web.search.Listing;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * Controller for all upcoming or previous (depending on configuration) display
 * of event listing.
 */
public class EventCalendarAllListingController extends EventCalendarListingController {

    private boolean upcoming = true;

    @Override
    public void runSearch(HttpServletRequest request, Resource collection, Map<String, Object> model, int pageLimit)
            throws Exception {
        int page = ListingPager.getPage(request, ListingPager.UPCOMING_PAGE_PARAM);
        model.put(MODEL_KEY_PAGE, page);

        Listing result = null;
        if (upcoming) {
            result = searcher.searchUpcoming(request, collection, page, pageLimit, 0);
            if (result.getTotalHits() > 0) {
                model.put(EventListingHelper.DISPLAY_LISTING_ICAL_LINK, true);
            }
        }
        else {
            model.put(MODEL_KEY_HIDE_ALTERNATIVE_REP, Boolean.TRUE);
            result = searcher.searchPrevious(request, collection, page, pageLimit, 0);
        }
        
        Service service = RequestContext.getRequestContext().getService();
        URL serviceURL = service.constructURL(collection.getURI());
        String viewType = serviceURL.getParameter(EventListingHelper.REQUEST_PARAMETER_VIEW);

        model.put(viewType, result);
        
        if (viewType == null) {
            viewType = upcoming ? "upcoming" : "previous";
        }
        
        String title = helper.getEventTypeTitle(request, collection, "eventListing." + viewType, false);
        String titleKey = viewType + "Title";
        model.put(titleKey, title);

        if (result == null || result.getEntries().isEmpty()) {
            String noPlannedTitle = helper.getEventTypeTitle(request, collection, "eventListing.noPlanned." + viewType,
                    false);
            String noPlannedTitleKey = viewType + "NoPlannedTitle";
            model.put(noPlannedTitleKey, noPlannedTitle);
            
            model.put(MODEL_KEY_SEARCH_COMPONENTS, Collections.emptyList());            
        }
        else {
            Optional<ListingPager.Pagination> pagination = 
                    ListingPager.pagination(result.getTotalHits(), pageLimit,
                            serviceURL, page);
            if (pagination.isPresent()) {
                List<ListingPagingLink> urls = pagination.get().pageThroughLinks();
                model.put(MODEL_KEY_PAGE_THROUGH_URLS, urls);
                model.put(MODEL_KEY_PAGINATION, pagination.get());
            }
            
            model.put(MODEL_KEY_SEARCH_COMPONENTS, Collections.singletonList(result));
        }
    }

    public void setUpcoming(boolean upcoming) {
        this.upcoming = upcoming;
    }

}
