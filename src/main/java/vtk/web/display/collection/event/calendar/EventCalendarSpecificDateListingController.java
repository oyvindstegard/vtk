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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Resource;
import vtk.web.RequestContext;
import vtk.web.display.collection.event.EventListingHelper;
import vtk.web.display.collection.event.EventListingHelper.SpecificDateSearchType;
import vtk.web.display.listing.ListingPager;
import vtk.web.display.listing.ListingPagingLink;
import vtk.web.search.Listing;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * Controller for a specific date display of event listing. The specific date is
 * supplied as request parameter (yyyy-mm-dd). If invalid, defaults to standard
 * calendar view.
 */
public class EventCalendarSpecificDateListingController extends EventCalendarListingController {

    @Override
    public void runSearch(HttpServletRequest request, Resource collection, Map<String, Object> model, int pageLimit)
            throws Exception {

        int page = ListingPager.getPage(request, ListingPager.UPCOMING_PAGE_PARAM);
        model.put(MODEL_KEY_PAGE, page);

        Date date = helper.getSpecificSearchDate(request);
        if (date != null) {
            SpecificDateSearchType searchType = helper.getSpecificDateSearchType(request);
            Listing specificDateEvents = searcher.searchSpecificDate(request, collection, pageLimit, page);

            model.put("specificDate", Boolean.TRUE);
            String messageKey = searchType == SpecificDateSearchType.Day ? "eventListing.specificDayEvent"
                    : "eventListing.specificDateEvent";
            String specificDateEventsTitle = helper.getEventTypeTitle(request, collection, searchType, date,
                    messageKey, true, true);
            model.put("specificDateEventsTitle", specificDateEventsTitle);
            model.put(MODEL_KEY_OVERRIDDEN_TITLE, specificDateEventsTitle);

            if (specificDateEvents != null && !specificDateEvents.getEntries().isEmpty()) {
                model.put("specificDateEvents", specificDateEvents);
                model.put(MODEL_KEY_SEARCH_COMPONENTS, Collections.singletonList(specificDateEvents));

                RequestContext requestContext = RequestContext.getRequestContext();
                Service service = requestContext.getService();
                
                URL baseURL = service.urlConstructor(requestContext.getRequestURL())
                        .withURI(requestContext.getResourceURI())
                        .constructURL();
                
                baseURL.setParameter(EventListingHelper.REQUEST_PARAMETER_DATE,
                        request.getParameter(EventListingHelper.REQUEST_PARAMETER_DATE));

                Optional<ListingPager.Pagination> pagination = 
                        ListingPager.pagination(specificDateEvents.getTotalHits(),
                                pageLimit, baseURL, page);
                if (pagination.isPresent()) {
                    List<ListingPagingLink> urls = pagination.get().pageThroughLinks();
                    model.put(MODEL_KEY_PAGE_THROUGH_URLS, urls);
                    model.put(MODEL_KEY_PAGINATION, pagination.get());
                }

                model.put(EventListingHelper.DISPLAY_LISTING_ICAL_LINK, true);
            }
            else {
                model.put(MODEL_KEY_SEARCH_COMPONENTS, Collections.emptyList());
                model.put("noPlannedEventsMsg",
                        helper.getEventTypeTitle(request, collection, "eventListing.noPlannedEvents", false));
            }
        } else {
            // invalid date given in request, run default search
            super.runSearch(request, collection, model, pageLimit);
        }

    }

}
