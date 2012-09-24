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
package org.vortikal.web.display.collection.event;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.support.RequestContext;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Property;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.Resource;
import org.vortikal.repository.resourcetype.DateValueFormatter;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.web.display.listing.ListingPager;
import org.vortikal.web.search.Listing;
import org.vortikal.web.servlet.ResourceAwareLocaleResolver;

public final class EventListingHelper implements InitializingBean {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern MONTH_PATTERN = Pattern.compile("\\d{4}-\\d{2}");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\d{4}");
    private Map<Pattern, DateTimeFormatter> dateformats;
    private Map<Pattern, SpecificDateSearchType> searchTypes;

    private DateValueFormatter dateValueFormatter;
    private ResourceAwareLocaleResolver localeResolver;
    private PropertyTypeDefinition eventTypeTitlePropDef;

    public static final String REQUEST_PARAMETER_DATE = "date";
    public static final String REQUEST_PARAMETER_VIEW = "view";
    public static final String VIEW_TYPE_ALL_UPCOMING = "allupcoming";
    public static final String VIEW_TYPE_ALL_PREVIOUS = "allprevious";
    public static final String DISPLAY_LISTING_ICAL_LINK = "displayEventListingICalLink";

    public enum SpecificDateSearchType {
        Day, Month, Year;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.dateformats = new HashMap<Pattern, DateTimeFormatter>();

        this.dateformats.put(DATE_PATTERN, DateTimeFormat.forPattern("yyyy-MM-dd"));
        this.dateformats.put(MONTH_PATTERN, DateTimeFormat.forPattern("yyyy-MM"));
        this.dateformats.put(YEAR_PATTERN, DateTimeFormat.forPattern("yyyy"));
        this.searchTypes = new HashMap<Pattern, SpecificDateSearchType>();
        this.searchTypes.put(DATE_PATTERN, SpecificDateSearchType.Day);
        this.searchTypes.put(MONTH_PATTERN, SpecificDateSearchType.Month);
        this.searchTypes.put(YEAR_PATTERN, SpecificDateSearchType.Year);
    }

    public SpecificDateSearchType getSpecificDateSearchType(HttpServletRequest request) {
        String specificDate = request.getParameter(EventListingHelper.REQUEST_PARAMETER_DATE);
        if (specificDate != null && !"".equals(specificDate.trim())) {
            for (Pattern regex : this.searchTypes.keySet()) {
                if (regex.matcher(specificDate).matches()) {
                    return this.searchTypes.get(regex);
                }
            }
        }
        return null;
    }

    public Date getSpecificSearchDate(HttpServletRequest request) {
        String specificDate = request.getParameter(EventListingHelper.REQUEST_PARAMETER_DATE);
        if (specificDate != null && !"".equals(specificDate.trim())) {
            DateTimeFormatter sdf = null;
            for (Pattern regex : this.searchTypes.keySet()) {
                if (regex.matcher(specificDate).matches()) {
                    sdf = this.dateformats.get(regex);
                }
            }
            if (sdf != null) {
                try {
                    long millis = sdf.parseMillis(specificDate);
                    Date date = new Date(millis);
                    return date;
                } catch (IllegalArgumentException e) {
                    // Ignore, return null
                }
            }
        }
        return null;
    }

    public String getEventTypeTitle(HttpServletRequest request, Resource collection, String key, boolean capitalize) {
        return this.getEventTypeTitle(request, collection, null, null, key, capitalize, true);
    }

    public String getEventTypeTitle(HttpServletRequest request, Resource collection, SpecificDateSearchType searchType,
            Date date, String key, boolean capitalize, boolean includePage) {
        List<Object> params = new ArrayList<Object>();
        String eventTypeTitle = this.getEventTypeTitle(collection, capitalize);
        if (eventTypeTitle != null) {
            key = key + ".overrideDefault";
            params.add(eventTypeTitle);
        }
        if (searchType != null && date != null) {
            String titleDate = this.getRequestedDateAsLocalizedString(collection, searchType, date);
            params.add(titleDate);
        }
        String title = getLocalizedTitle(request, key, params.toArray());
        int page = ListingPager.getPage(request, ListingPager.UPCOMING_PAGE_PARAM);
        if (includePage && page > 1) {
            String pageText = this.getLocalizedTitle(request, "viewCollectionListing.page", null);
            title = title + " - " + pageText + " " + page;
        }
        return title;
    }

    public String getLocalizedTitle(HttpServletRequest request, String key, Object[] params) {
        RequestContext springRequestContext = new RequestContext(request);
        if (params != null) {
            return springRequestContext.getMessage(key, params);
        }
        return springRequestContext.getMessage(key);
    }

    public String getEventTypeTitle(Resource collection, boolean capitalize) {
        Property eventTypeTitleProp = collection.getProperty(this.eventTypeTitlePropDef);
        if (eventTypeTitleProp != null) {
            String eventTypeTitle = eventTypeTitleProp.getStringValue();
            eventTypeTitle = capitalize ? eventTypeTitle.substring(0, 1).toUpperCase()
                    + eventTypeTitle.substring(1).toLowerCase() : eventTypeTitle.toLowerCase();
            return eventTypeTitle;
        }
        return null;
    }

    private String getRequestedDateAsLocalizedString(Resource collection, SpecificDateSearchType searchType, Date date) {
        Calendar requestedCal = Calendar.getInstance();
        requestedCal.setTime(date);
        if (searchType != SpecificDateSearchType.Year) {
            Locale locale = this.localeResolver.resolveResourceLocale(collection.getURI());
            String format = "full-month-year-short";
            if (searchType == SpecificDateSearchType.Month) {
                format = "full-month-year";
            }
            return this.dateValueFormatter.valueToString(new Value(date, false), format, locale);
        }
        return String.valueOf(requestedCal.get(Calendar.YEAR));
    }

    public Calendar getCurrentMonth() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    public String getCalendarWidgetEventDates(Listing events, Calendar cal) {

        Set<String> eventDatesList = new HashSet<String>();
        SimpleDateFormat eventDateFormat = new SimpleDateFormat("yyyy-M-d");

        for (PropertySet propSet : events.getFiles()) {

            Property startDateProp = propSet.getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE, "start-date");
            Date eventStart = startDateProp != null ? startDateProp.getDateValue() : cal.getTime();
            Property endDateProp = propSet.getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE, "end-date");
            Date eventEnd = endDateProp != null ? endDateProp.getDateValue() : eventStart;

            Calendar eventStartCal = this.getDayOfMonth(eventStart);
            if (eventStartCal.get(Calendar.MONTH) < cal.get(Calendar.MONTH)) {
                eventStartCal.setTime(cal.getTime());
            }
            Calendar eventEndCal = this.getDayOfMonth(eventEnd);
            if (eventEndCal.get(Calendar.MONTH) > cal.get(Calendar.MONTH)) {
                eventEndCal.set(Calendar.DAY_OF_MONTH, eventEndCal.getActualMaximum(Calendar.DAY_OF_MONTH));
            }

            while (eventStartCal.before(eventEndCal) || eventStartCal.equals(eventEndCal)) {
                eventDatesList.add(eventDateFormat.format(eventStartCal.getTime()));
                eventStartCal.add(Calendar.DAY_OF_MONTH, 1);
            }

        }

        String eventDates = this.getEventDatesAsArrayString(eventDatesList);
        return eventDates;
    }

    private Calendar getDayOfMonth(Date eventStart) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(eventStart);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    private String getEventDatesAsArrayString(Set<String> eventDates) {
        StringBuilder sb = new StringBuilder("[");
        Iterator<String> it = eventDates.iterator();
        boolean first = true;
        while (it.hasNext()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("'" + it.next() + "'");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void setCalendarTitles(HttpServletRequest request, Resource resource, Map model) {
        model.put("dayHasPlannedEventsTitle",
                this.getEventTypeTitle(request, resource, "eventListing.calendar.dayHasPlannedEvents", false));
        model.put("dayHasNoPlannedEventsTitle",
                this.getEventTypeTitle(request, resource, "eventListing.calendar.dayHasNoPlannedEvents", false));
    }

    @Required
    public void setDateValueFormatter(DateValueFormatter dateValueFormatter) {
        this.dateValueFormatter = dateValueFormatter;
    }

    @Required
    public void setLocaleResolver(ResourceAwareLocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @Required
    public void setEventTypeTitlePropDef(PropertyTypeDefinition eventTypeTitlePropDef) {
        this.eventTypeTitlePropDef = eventTypeTitlePropDef;
    }

}
