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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.RequestContext;
import vtk.web.display.collection.event.EventListingController;
import vtk.web.display.collection.event.EventListingHelper;
import vtk.web.display.listing.ListingPager;
import vtk.web.search.Listing;
import vtk.web.search.ListingEntry;
import vtk.web.search.SearchComponent;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * Controller for calendar view display of event listing.
 */
public class EventCalendarListingController extends EventListingController {

    protected EventListingHelper helper;

    private int daysAhead;
    private Service viewAllUpcomingService;
    private Service viewAllPreviousService;
    private SearchComponent calendarSearchComponent;
    private PropertyTypeDefinition publishDatePropDef;

    // max 5 further upcoming events on 1 page
    private final int furtherUpcomingLimit = 5;

    @Override
    public void runSearch(HttpServletRequest request, Resource collection, Map<String, Object> model, int pageLimit)
            throws Exception {

        Listing result = this.calendarSearchComponent.execute(request, collection, 1, 500, 0);

        Calendar currentMonth = this.helper.getCurrentMonth();
        DateTimeFormatter dtf = DateTimeFormat.forPattern("YYYY-MM");
        String requestedDate = dtf.print(currentMonth.getTimeInMillis());
        model.put("requestedDate", requestedDate);

        // Days with events, with localized calendar titles for clickable days.
        String eventDates = this.helper.getCalendarWidgetMonthEventDates(result.getPropertySets(), currentMonth);
        model.put("allowedDates", eventDates);
        this.helper.setCalendarTitles(request, collection, model);

        int page = ListingPager.getPage(request, ListingPager.UPCOMING_PAGE_PARAM);
        model.put(MODEL_KEY_PAGE, page);

        List<GroupedEvents> groupedByDayEvents = this.groupEvents(result);
        model.put("groupedByDayEvents", groupedByDayEvents);
        String groupedByDayTitle = this.helper.getLocalizedTitle(request, "eventListing.groupedEvents",
                new Object[] { this.daysAhead });
        model.put("groupedEventsTitle", groupedByDayTitle);

        Listing furtherUpcoming = this.getFurtherUpcoming(result, groupedByDayEvents);
        model.put("furtherUpcoming", furtherUpcoming);
        String titleKey = groupedByDayEvents.size() == 0 ? "eventListing.allupcoming"
                : "eventListing.furtherUpcomingEvents";
        String furtherUpcomingTitle = this.helper.getEventTypeTitle(request, collection, titleKey, false);
        model.put("furtherUpcomingTitle", furtherUpcomingTitle);

        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DATE, 1);

        model.put("tomorrow", tomorrow.getTime());
        model.put("today", new Date());

        if (groupedByDayEvents.size() > 0 || (furtherUpcoming != null && furtherUpcoming.size() > 0)) {
            model.put(EventListingHelper.DISPLAY_LISTING_ICAL_LINK, true);
        }
        
        RequestContext requestContext = RequestContext.getRequestContext();
        URL viewAllUpcomingURL = viewAllUpcomingService.urlConstructor(requestContext.getRequestURL())
                .withResource(collection)
                .matchAssertions(false)
                .constructURL();
        
        model.put("viewAllUpcomingURL", viewAllUpcomingURL);
        model.put("viewAllUpcomingTitle",
                this.helper.getEventTypeTitle(request, collection, "eventListing.viewAllUpcoming", false));

        URL viewAllPreviousURL = viewAllPreviousService.urlConstructor(requestContext.getRequestURL())
                .withResource(collection)
                .matchAssertions(false)
                .constructURL();

        model.put("viewAllPreviousURL", viewAllPreviousURL);
        model.put("viewAllPreviousTitle",
                this.helper.getEventTypeTitle(request, collection, "eventListing.viewAllPrevious", false));

    }

    private List<GroupedEvents> groupEvents(Listing result) {
        List<GroupedEvents> groupedByDayEvents = new ArrayList<>();
        if (result != null && result.size() > 0) {
            List<ListingEntry> allEvents = result.getEntries();
            for (int i = 0; i < this.daysAhead; i++) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_MONTH, i);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                Listing subListing = new Listing(result.getResource(), null, result.getName(), result.getOffset());
                subListing.setDisplayPropDefs(result.getDisplayPropDefs());
                List<ListingEntry> subListingEvents = new ArrayList<>();
                for (ListingEntry entry : allEvents) {
                    if (this.isWithinDaysAhead(cal.getTime(), entry.getPropertySet())) {
                        subListingEvents.add(entry);
                    }
                }
                if (subListingEvents.size() > 0) {
                    subListing.setEntries(subListingEvents);
                    groupedByDayEvents.add(new GroupedEvents(cal.getTime(), subListing));
                }
            }
        }
        return groupedByDayEvents;
    }

    private boolean isWithinDaysAhead(Date time, PropertySet ps) {
        Property sdProp = this.helper.getStartDateProperty(ps);
        Property edProp = this.helper.getEndDateProperty(ps);
        if (sdProp == null) {
            sdProp = edProp != null ? edProp : ps.getProperty(this.publishDatePropDef);
        }
        Date sd = sdProp.getDateValue();
        Calendar cal = Calendar.getInstance();
        cal.setTime(time);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        boolean isToday = sd.equals(time) || (sd.after(time) && sd.before(cal.getTime()));
        if (edProp == null) {
            return isToday;
        }
        Date ed = edProp.getDateValue();
        return isToday || (sd.before(time) && (ed.after(time) || ed.equals(time)));
    }

    private Listing getFurtherUpcoming(Listing result, List<GroupedEvents> groupedEvents) {
        if (result != null && result.size() > 0) {

            // Default is now, in case there are no grouped by events
            Date lastGroupedByDay = Calendar.getInstance().getTime();
            Set<ListingEntry> allGroupedByEvents = new HashSet<>();
            if (groupedEvents != null && groupedEvents.size() > 0) {
                for (GroupedEvents ge : groupedEvents) {
                    allGroupedByEvents.addAll(ge.getEvents().getEntries());
                    Date groupedByDay = ge.getDay();
                    if (groupedByDay.after(lastGroupedByDay)) {
                        lastGroupedByDay = groupedByDay;
                    }
                }
            }

            List<ListingEntry> filteredEventsWithoutGrouped = new ArrayList<>(result.getEntries());
            filteredEventsWithoutGrouped.removeAll(allGroupedByEvents);
            if (filteredEventsWithoutGrouped.size() == 0) {
                return null;
            }

            // XXX WTF does this mean?!?!?!
            // No need to sort, already sorted as we want and we have removed
            // grouped events. Now get "furtherUpcomingLimit" further events
            // "daysAhead" from now

            List<ListingEntry> furtherEvents = new ArrayList<>();
            for (ListingEntry entry : filteredEventsWithoutGrouped) {
                PropertySet ps = entry.getPropertySet();
                if (this.isWithinFurtherUpcoming(ps, lastGroupedByDay)) {
                    furtherEvents.add(entry);
                    if (furtherEvents.size() == this.furtherUpcomingLimit) {
                        break;
                    }
                }
            }

            if (furtherEvents.size() > 0) {
                Listing furtherUpcoming = new Listing(result.getResource(), null, result.getName(), result.getOffset());
                furtherUpcoming.setEntries(furtherEvents);
                furtherUpcoming.setDisplayPropDefs(result.getDisplayPropDefs());
                return furtherUpcoming;
            }

        }
        return null;
    }

    private boolean isWithinFurtherUpcoming(PropertySet ps, Date lastGroupedByDay) {
        Property sdProp = this.helper.getStartDateProperty(ps);
        Date eventStartDate = null;
        if (sdProp != null) {
            eventStartDate = sdProp.getDateValue();
        }
        if (eventStartDate != null && eventStartDate.after(lastGroupedByDay)) {
            return true;
        }

        Property edProp = this.helper.getEndDateProperty(ps);
        Date eventEndDate = null;
        if (edProp != null) {
            eventEndDate = edProp.getDateValue();
        }
        if (eventEndDate != null && eventEndDate.after(lastGroupedByDay)) {
            return true;
        }

        return false;
    }

    @Required
    public void setHelper(EventListingHelper helper) {
        this.helper = helper;
    }

    @Required
    public void setDaysAhead(int daysAhead) {
        this.daysAhead = daysAhead;
    }

    @Required
    public void setViewAllUpcomingService(Service viewAllUpcomingService) {
        this.viewAllUpcomingService = viewAllUpcomingService;
    }

    @Required
    public void setViewAllPreviousService(Service viewAllPreviousService) {
        this.viewAllPreviousService = viewAllPreviousService;
    }

    @Required
    public void setCalendarSearchComponent(SearchComponent calendarSearchComponent) {
        this.calendarSearchComponent = calendarSearchComponent;
    }

    @Required
    public void setPublishDatePropDef(PropertyTypeDefinition publishDatePropDef) {
        this.publishDatePropDef = publishDatePropDef;
    }

    public static class GroupedEvents {

        private Date day;
        private Listing events;

        public GroupedEvents(Date day, Listing events) {
            this.day = day;
            this.events = events;
        }

        public Date getDay() {
            return day;
        }

        public Listing getEvents() {
            return events;
        }

    }

}
