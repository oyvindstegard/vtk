/* Copyright (c) 2010-2017, University of Oslo, Norway
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
package vtk.web.display.collection.event;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
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
import org.apache.commons.lang.time.FastDateFormat;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.support.RequestContext;

import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Resource;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.DateValueFormatter;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.web.display.listing.ListingPager;
import vtk.web.servlet.ResourceAwareLocaleResolver;

public final class EventListingHelper {

    public enum SpecificDateSearchType {
        Day, Month, Year;
    }

    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern MONTH_PATTERN = Pattern.compile("\\d{4}-\\d{2}");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\d{4}");
    private static final Map<Pattern, DateTimeFormatter> DATE_FORMATS;
    private static final Map<Pattern, SpecificDateSearchType> SEARCH_TYPES;
    static {
        // These formatters must provide defaults for day and month when they are not part of pattern
        DATE_FORMATS = new HashMap<>(4, 1f);
        DATE_FORMATS.put(DATE_PATTERN, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        DATE_FORMATS.put(MONTH_PATTERN, new DateTimeFormatterBuilder()
                                            .appendPattern("yyyy-MM")
                                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                                            .toFormatter());
        DATE_FORMATS.put(YEAR_PATTERN, new DateTimeFormatterBuilder()
                                            .appendPattern("yyyy")
                                            .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
                                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                                            .toFormatter());
        SEARCH_TYPES = new HashMap<>(4, 1f);
        SEARCH_TYPES.put(DATE_PATTERN, SpecificDateSearchType.Day);
        SEARCH_TYPES.put(MONTH_PATTERN, SpecificDateSearchType.Month);
        SEARCH_TYPES.put(YEAR_PATTERN, SpecificDateSearchType.Year);
    }

    private DateValueFormatter dateValueFormatter;
    private ResourceAwareLocaleResolver localeResolver;
    private PropertyTypeDefinition eventTypeTitlePropDef;
    private String startPropDefPointer;
    private String endPropDefPointer;
    private ResourceTypeTree resourceTypeTree;

    public static final String REQUEST_PARAMETER_DATE = "date";
    public static final String REQUEST_PARAMETER_VIEW = "view";
    public static final String VIEW_TYPE_ALL_UPCOMING = "allupcoming";
    public static final String VIEW_TYPE_ALL_PREVIOUS = "allprevious";
    public static final String DISPLAY_LISTING_ICAL_LINK = "displayEventListingICalLink";

    public SpecificDateSearchType getSpecificDateSearchType(HttpServletRequest request) {
        String specificDate = request.getParameter(EventListingHelper.REQUEST_PARAMETER_DATE);
        if (specificDate != null && !"".equals(specificDate.trim())) {
            for (Pattern regex : SEARCH_TYPES.keySet()) {
                if (regex.matcher(specificDate).matches()) {
                    return SEARCH_TYPES.get(regex);
                }
            }
        }
        return null;
    }

    public Date getSpecificSearchDate(HttpServletRequest request) {
        String specificDate = request.getParameter(EventListingHelper.REQUEST_PARAMETER_DATE);
        if (specificDate != null && !(specificDate = specificDate.trim()).isEmpty()) {
            DateTimeFormatter dateFormatter = null;
            for (Pattern regex : SEARCH_TYPES.keySet()) {
                if (regex.matcher(specificDate).matches()) {
                    dateFormatter = DATE_FORMATS.get(regex);
                }
            }
            if (dateFormatter != null) {
                try {
                    LocalDate localDate = LocalDate.parse(specificDate, dateFormatter);
                    return Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
                } catch (IllegalArgumentException e) {
                    // Ignore, return null
                }
            }
        }
        return null;
    }

    public String getEventTypeTitle(HttpServletRequest request, Resource collection, String key, boolean capitalize) {
        return getEventTypeTitle(request, collection, null, null, key, capitalize, true);
    }

    public String getEventTypeTitle(HttpServletRequest request, Resource collection, SpecificDateSearchType searchType,
            Date date, String key, boolean capitalize, boolean includePage) {
        List<Object> params = new ArrayList<>();
        String eventTypeTitle = getEventTypeTitle(collection, capitalize);
        if (eventTypeTitle != null) {
            key = key + ".overrideDefault";
            params.add(eventTypeTitle);
        }
        if (searchType != null && date != null) {
            String titleDate = getRequestedDateAsLocalizedString(collection, searchType, date);
            params.add(titleDate);
        }
        String title = getLocalizedTitle(request, key, params.toArray());
        int page = ListingPager.getPage(request, ListingPager.UPCOMING_PAGE_PARAM);
        if (includePage && page > 1) {
            String pageText = getLocalizedTitle(request, "viewCollectionListing.page", null);
            title = title + " - " + pageText + " " + page;
        }
        return title;
    }

    public String getLocalizedTitle(HttpServletRequest request, String key, Object[] params) {
        RequestContext springRequestContext = new RequestContext(request);
        if (params != null) {
            return springRequestContext.getMessage(key, params, key);
        }
        return springRequestContext.getMessage(key, key);
    }

    public String getEventTypeTitle(Resource collection, boolean capitalize) {
        Property eventTypeTitleProp = collection.getProperty(eventTypeTitlePropDef);
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
            Locale locale = localeResolver.resolveResourceLocale(collection.getURI());
            String format = "full-month-year-short";
            if (searchType == SpecificDateSearchType.Month) {
                format = "full-month-year";
            }
            return dateValueFormatter.valueToString(new Value(date, false), format, locale);
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

    public String getCalendarWidgetMonthEventDates(List<PropertySet> events, Calendar requestedMonthCal) {

        Set<String> eventDatesList = new HashSet<>();
        FastDateFormat eventDateFormat = FastDateFormat.getInstance("yyyy-M-d");

        Calendar endOfRequestedMonthCal = Calendar.getInstance();
        endOfRequestedMonthCal.setTime(requestedMonthCal.getTime());
        endOfRequestedMonthCal.add(Calendar.MONTH, 1);

        for (PropertySet event : events) {

            Property eventStartDateProp = getStartDateProperty(event);
            Date eventStart = null;
            if (eventStartDateProp != null) {
                eventStart = eventStartDateProp.getDateValue();
            }

            Property eventEndDateProp = getEndDateProperty(event);
            Date eventEnd = null;
            if (eventEndDateProp != null) {
                eventEnd = eventEndDateProp.getDateValue();
            }

            if (eventStart == null && eventEnd == null) {
                // Should not even be in event list, repository search would
                // under normal circumstances not return it
                continue;
            }

            Calendar eventStartCal = eventStart != null ? getDayOfMonth(eventStart) : getDayOfMonth(eventEnd);
            if (eventStartCal.after(endOfRequestedMonthCal)) {
                continue;
            }
            if (eventStartCal.get(Calendar.MONTH) < requestedMonthCal.get(Calendar.MONTH)) {
                eventStartCal.setTime(requestedMonthCal.getTime());
            }

            Calendar eventEndCal = eventEnd != null ? getDayOfMonth(eventEnd) : getDayOfMonth(eventStart);
            if (eventEndCal.get(Calendar.MONTH) > requestedMonthCal.get(Calendar.MONTH)) {
                eventEndCal.set(Calendar.DAY_OF_MONTH, eventEndCal.getActualMaximum(Calendar.DAY_OF_MONTH));
            }

            while (eventStartCal.before(eventEndCal) || eventStartCal.equals(eventEndCal)) {
                eventDatesList.add(eventDateFormat.format(eventStartCal.getTime()));
                eventStartCal.add(Calendar.DAY_OF_MONTH, 1);
            }

        }

        String eventDates = getEventDatesAsArrayString(eventDatesList);
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
            sb.append("'").append(it.next()).append("'");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void setCalendarTitles(HttpServletRequest request, Resource resource, Map model) {
        model.put("dayHasPlannedEventsTitle",
                getEventTypeTitle(request, resource, "eventListing.calendar.dayHasPlannedEvents", false));
        model.put("dayHasNoPlannedEventsTitle",
                getEventTypeTitle(request, resource, "eventListing.calendar.dayHasNoPlannedEvents", false));
    }

    public Property getStartDateProperty(PropertySet ps) {
        PropertyTypeDefinition propDef = resourceTypeTree.getPropertyDefinitionByPointer(startPropDefPointer);
        if (propDef == null) {
            throw new IllegalStateException("Could not find property definition for '" + startPropDefPointer + "'");
        }
        return ps.getProperty(propDef);
    }

    public Property getEndDateProperty(PropertySet ps) {
        PropertyTypeDefinition propDef = resourceTypeTree.getPropertyDefinitionByPointer(endPropDefPointer);
        if (propDef == null) {
            throw new IllegalStateException("Could not find property definition for '" + endPropDefPointer + "'");
        }
        return ps.getProperty(propDef);
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

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    @Required
    public void setStartPropDefPointer(String startPropDefPointer) {
        this.startPropDefPointer = startPropDefPointer;
    }

    @Required
    public void setEndPropDefPointer(String endPropDefPointer) {
        this.endPropDefPointer = endPropDefPointer;
    }

}
