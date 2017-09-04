/* Copyright (c) 2007, University of Oslo, Norway
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
package vtk.web.decorating.components;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.AuthorizationException;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceNotFoundException;
import vtk.security.AuthenticationException;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.search.Listing;
import vtk.web.search.ListingEntry;
import vtk.web.search.SearchComponent;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class EventComponent extends ViewRenderingDecoratorComponent {
    private static Logger logger = LoggerFactory.getLogger(EventComponent.class);

    private static final String PARAMETER_URI = "uri";
    private static final String PARAMETER_URI_DESC = "Uri to the event folder. This is a required parameter.";

    private static final String PARAMETER_INCLUDE_IF_EMPTY = "include-if-empty";
    private static final String PARAMETER_INCLUDE_IF_EMPTY_DESC = "Set to 'false' if you don't want to display empty events. Default is 'true'.";

    private static final String PARAMETER_EVENT_DESCRIPTION = "event-description";
    private static final String PARAMETER_EVENT_DESCRIPTION_DESC = "Must be set to 'true' to show event description";

    private static final String PARAMETER_ALL_EVENTS_LINK = "all-events-link";
    private static final String PARAMETER_ALL_EVENTS_LINK_DESC = "Set to 'true' to display 'All events' link at the bottom of the list. Default is 'false'.";

    private static final String PARAMETER_MAX_EVENTS = "max-events";
    private static final String PARAMETER_MAX_EVENTS_DESC = "The max number of events to display, defaults to 5";

    private static final String PARAMETER_DATE_ICON = "date-icon";
    private static final String PARAMETER_DATE_ICON_DESC = "Set to 'false' if you don't want to show event date icon. Default is 'true'.";

    private static final String PARAMETER_LIST_ONLY_ONCE = "list-only-once";
    private static final String PARAMETER_LIST_ONLY_ONCE_DESC = "Set to 'true' if you only want to list each event once. Default is 'false'. Enabling this overrides the date icon parameter and sets it to false.";

    private static final String PARAMETER_SHOW_LOCATION = "show-location";
    private static final String PARAMETER_SHOW_LOCATION_DESC = "Set to 'false' if you don't want to show event location. Default is 'true'.";

    private static final String PARAMETER_SHOW_PICTURE = "show-picture";
    private static final String PARAMETER_SHOW_PICTURE_DESC = "Set to 'true' if you want to show picture for an event. Default is 'false'.";

    private static final String PARAMETER_SHOW_END_TIME = "show-end-time";
    private static final String PARAMETER_SHOW_END_TIME_DESC = "Set to 'false' if you want to hide end time for an event This option is only available when each event is listed only once. Default is 'true'.";

    private static final String PARAMETER_ADD_TO_CALENDAR = "add-to-calendar";
    private static final String PARAMETER_ADD_TO_CALENDAR_DESC = "Set to 'true' if you want add to calendar function. Default is 'false'.";

    private static final String PARAMETER_EVENTS_TITLE = "events-title";
    private static final String PARAMETER_EVENTS_TITLE_DESC = "Set to true if you want to display title of the events folder. Default is 'false'";

    private static final String PARAMETER_EVENTS_EMPTY_MSG = "if-empty-message";
    private static final String PARAMETER_EVENTS_EMPTY_MSG_DESC = "Sets message to be shown if there are no events.";

    private static final String PARAMETER_ALL_EVENTS_TEXT = "override-all-events-link-text";
    private static final String PARAMETER_ALL_EVENTS_TEXT_DESC = "Sets text to be shown instead of go to all events link.";

    private static final String PARAMETER_SHOW_ONLY_ONGOING = "show-only-ongoing";
    private static final String PARAMETER_SHOW_ONLY_ONGOING_DESC = "Set to true if you only want to display ongoing events. Default is 'false'";

    private static final String PARAMETER_SHOW_DATE = "show-date";
    private static final String PARAMETER_SHOW_DATE_DESC = "Set to false if you want to hide date on events. Default is 'true'";

    private static final String PARAMETER_EVENT_ELEMENT_ORDER = "element-order";
    private static final String PARAMETER_EVENT_ELEMENT_ORDER_DESC = "The order that the elementes are listed";

    private SearchComponent search;
    private Service viewService;
    private List<String> defaultElementOrder;

    @Override
    protected void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {
        super.processModel(model, request, response);

        Map<String, Object> conf = new HashMap<>();

        String uri = request.getStringParameter(PARAMETER_URI);
        if (uri == null) {
            throw new DecoratorComponentException("Component parameter 'uri' is required");
        }
        RequestContext requestContext = RequestContext.getRequestContext(request.getServletRequest());
        Path resourcePath = this.getResourcePath(requestContext, uri);
        if (resourcePath == null) {
            throw new DecoratorComponentException("Provided uri is not a valid folder reference: " + uri);
        }

        conf.put("includeIfEmpty", !parameterHasValue(PARAMETER_INCLUDE_IF_EMPTY, "false", request));
        conf.put("eventDescription", parameterHasValue(PARAMETER_EVENT_DESCRIPTION, "true", request));
        conf.put("allEventsLink", parameterHasValue(PARAMETER_ALL_EVENTS_LINK, "true", request));

        int maxEvents = 5;
        try {
            if ((maxEvents = Integer.parseInt(request.getStringParameter(PARAMETER_MAX_EVENTS))) <= 0) {
                maxEvents = 5;
            }
        } catch (Exception e) {
        }
        conf.put("maxEvents", maxEvents);

        boolean dateIcon;
        boolean listOnlyOnce = parameterHasValue(PARAMETER_LIST_ONLY_ONCE, "true", request);
        if (listOnlyOnce) {
            dateIcon = false;
        } else {
            dateIcon = !parameterHasValue(PARAMETER_DATE_ICON, "false", request);
        }

        conf.put("dateIcon", dateIcon);
        conf.put("listOnlyOnce", listOnlyOnce);
        conf.put("showLocation", !parameterHasValue(PARAMETER_SHOW_LOCATION, "false", request));
        conf.put("showPicture", parameterHasValue(PARAMETER_SHOW_PICTURE, "true", request));
        conf.put("showEndTime", !parameterHasValue(PARAMETER_SHOW_END_TIME, "false", request) && listOnlyOnce);
        conf.put("addToCalendar", parameterHasValue(PARAMETER_ADD_TO_CALENDAR, "true", request));

        boolean showOnlyOngoing = parameterHasValue(PARAMETER_SHOW_ONLY_ONGOING, "true", request);
        conf.put("showDate", !parameterHasValue(PARAMETER_SHOW_DATE, "false", request));

        String emptyMsg = request.getStringParameter(PARAMETER_EVENTS_EMPTY_MSG);
        if (emptyMsg != null) {
            conf.put("emptyMsg", emptyMsg);
        }

        String allEventsText = request.getStringParameter(PARAMETER_ALL_EVENTS_TEXT);
        if (allEventsText != null) {
            conf.put("allEventsText", allEventsText);
        }

        boolean eventsTitle = parameterHasValue(PARAMETER_EVENTS_TITLE, "true", request);
        conf.put("eventsTitle", eventsTitle);

        model.put("elementOrder", getElementOrder(PARAMETER_EVENT_ELEMENT_ORDER, request));

        RequestContext rc = RequestContext.getRequestContext(request.getServletRequest());
        Repository repo = rc.getRepository();
        String token = rc.getSecurityToken();
        Resource resource = null;
        try {
            resource = repo.retrieve(token, resourcePath, false);
            URL url = viewService.urlConstructor(rc.getRequestURL())
                    .withURI(resource.getURI())
                    .constructURL();
            conf.put("uri", url);
        } catch (AuthenticationException | AuthorizationException e) {
        } catch (ResourceNotFoundException e) {
            throw new DecoratorComponentException(uri + " does not exist");
        } catch (IOException e) {
            throw new DecoratorComponentException(e.getMessage());
        }

        conf.put("auth", resource != null);
        if (resource == null || !resource.isPublished()) {
            model.put("conf", conf);
            return;
        }

        if (!resource.isCollection()) {
            throw new DecoratorComponentException(uri + " is not a folder");
        }

        if (eventsTitle) {
            model.put("eventsTitle", resource.getTitle());
        }

        Listing events = search.execute(request.getServletRequest(), resource, 1, maxEvents, 0);

        if (showOnlyOngoing) {
            Calendar startDate, now = Calendar.getInstance();
            List<ListingEntry> tmpEvents = new ArrayList<>(events.getEntries());

            for (ListingEntry entry : events.getEntries()) {

                PropertySet ps = entry.getPropertySet();
                
                Property sprop = ps.getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE, "start-date");
                if (sprop == null) {
                    sprop = ps.getProperty(Namespace.DEFAULT_NAMESPACE, "start-date");
                }

                if (sprop != null) {
                    startDate = Calendar.getInstance();
                    startDate.setTimeInMillis(sprop.getDateValue().getTime());

                    if (!startDate.before(now)) {
                        tmpEvents.remove(entry);
                    }
                }
            }

            events.setEntries(tmpEvents);
            conf.put("showOnlyOngoing", showOnlyOngoing);
        }

        /*
         * If events will just be listed once we can use the search result,
         * otherwise we have to process the search result to list out each day
         * for itself.
         */
        if (listOnlyOnce) {
            conf.put("type", "list-once");
            if (events.size() > 0) {
                model.put("events", events);
            }
        } else {
            model.put("today", new Date());
            Calendar tomorrow = Calendar.getInstance();
            tomorrow.add(Calendar.DATE, 1);
            model.put("tomorrow", tomorrow.getTime());

            List<PropertySetTmp> psList = new ArrayList<>();
            for (ListingEntry entry : events.getEntries()) {

                PropertySet ps = entry.getPropertySet();
                Property sprop = ps.getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE, "start-date");
                if (sprop == null) {
                    sprop = ps.getProperty(Namespace.DEFAULT_NAMESPACE, "start-date");
                }

                Property eprop = ps.getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE, "end-date");
                if (eprop == null) {
                    eprop = ps.getProperty(Namespace.DEFAULT_NAMESPACE, "end-date");
                }

                if (eprop != null && sprop != null) {
                    psList.add(new PropertySetTmp(ps, sprop.getDateValue(), eprop.getDateValue()));
                } else if (sprop != null) {
                    psList.add(new PropertySetTmp(ps, sprop.getDateValue()));
                }
            }

            /* Set midnight to 00:00. */
            Calendar midnight = Calendar.getInstance(), smidnight;
            midnight.set(Calendar.HOUR_OF_DAY, 00);
            midnight.set(Calendar.MINUTE, 00);
            midnight.set(Calendar.SECOND, 00);
            midnight.set(Calendar.MILLISECOND, 0);

            PropertySetTmp pst;
            List<PropertySetDate> groupedByDayEvents = new ArrayList<>();
            while (groupedByDayEvents.size() < maxEvents && 0 < psList.size()) {
                for (int i = 0; i < psList.size(); i++) {
                    pst = psList.get(i);

                    /* Used to set sprop and showTime in PropertySetData. */
                    smidnight = Calendar.getInstance();
                    smidnight.setTimeInMillis(pst.getStartDate().getTimeInMillis());
                    smidnight.set(Calendar.HOUR_OF_DAY, 00);
                    smidnight.set(Calendar.MINUTE, 00);
                    smidnight.set(Calendar.SECOND, 00);
                    smidnight.set(Calendar.MILLISECOND, 0);

                    /*
                     * Only lists up from today and after. Because an event can
                     * start earlier than today and end later.
                     */
                    if (pst.getStartDate().getTimeInMillis() >= midnight.getTimeInMillis()) {
                        groupedByDayEvents.add(new PropertySetDate(pst.getPropertySet(), pst.getStartDate(), smidnight
                                .getTimeInMillis() != pst.getStartDate().getTimeInMillis()));
                    }

                    /* If we got enough events listed. */
                    if (groupedByDayEvents.size() == maxEvents) {
                        break;
                    }

                    /* Set midnight to the next day, relative to current event. */
                    smidnight.add(Calendar.DATE, 1);

                    /*
                     * If it does not last longer than this day, remove it and
                     * decrement i. Else set the start-date value to next day.
                     */
                    if (pst.getEndDate() == null || smidnight.getTimeInMillis() > pst.getEndDate().getTimeInMillis()) {
                        psList.remove(i--);
                    } else {
                        pst.setStartDate(smidnight);
                    }

                    /* If we cannot check the next PropertySet. */
                    if ((i + 1) >= psList.size()) {
                        break;
                    }

                    /* If the next event starts after the current day. */
                    if (psList.get(i + 1).getStartDate().getTimeInMillis() >= smidnight.getTimeInMillis()) {
                        break;
                    }

                }
            }

            model.put("groupedByDayEvents", groupedByDayEvents);
            conf.put("type", "groupedByDayEvents");
        }
        model.put("conf", conf);
        logger.debug("Populated model: {}", model);
    }

    boolean parameterHasValue(String param, String includeParamValue, DecoratorRequest request) {
        String itemDescriptionString = request.getStringParameter(param);
        if (itemDescriptionString != null && includeParamValue.equalsIgnoreCase(itemDescriptionString)) {
            return true;
        }
        return false;
    }

    private List<String> getElementOrder(String param, DecoratorRequest request) {
        List<String> resultOrder = new ArrayList<>();

        String[] order = null;
        try {
            order = request.getStringParameter(param).split(",");
        } catch (Exception e) {
        }

        if (order == null) {
            return defaultElementOrder;
        }

        // check and add
        for (int i = 0; i < order.length; i++) {
            if (order[i] != null && !defaultElementOrder.contains(order[i].trim())) {
                throw new DecoratorComponentException("Illigal element '" + order[i] + "' in '" + param + "'");
            }
            if (order[i] != null) {
                resultOrder.add(order[i].trim());
            }
        }
        return resultOrder;
    }

    /* Class to keep date and PropertySet so start date can be incremented */
    public static class PropertySetTmp {

        private PropertySet propertySet;
        private Calendar startDate;
        private Calendar endDate;

        public PropertySetTmp(PropertySet propertySet, Date sdate) {
            this.propertySet = propertySet;
            this.startDate = Calendar.getInstance();
            this.startDate.setTimeInMillis(sdate.getTime());
            this.endDate = null;
        }

        public PropertySetTmp(PropertySet ps, Date sdate, Date edate) {
            this.propertySet = ps;
            this.startDate = Calendar.getInstance();
            this.endDate = Calendar.getInstance();
            this.startDate.setTimeInMillis(sdate.getTime());
            this.endDate.setTimeInMillis(edate.getTime());
        }

        public PropertySet getPropertySet() {
            return propertySet;
        }

        public Calendar getStartDate() {
            return startDate;
        }

        public Calendar getEndDate() {
            return endDate;
        }

        public void setStartDate(Calendar startDate) {
            this.startDate = startDate;
        }
    }

    /*
     * Class to keep date and showTime for each day an event occupies. Only used
     * if not listOnlyOnce is set.
     */
    public static class PropertySetDate {

        private PropertySet propertySet;
        private Date date;
        private boolean showTime;

        public PropertySetDate(PropertySet propertySet, Calendar date, boolean showTime) {
            this.propertySet = propertySet;
            this.date = date.getTime();
            this.showTime = showTime;
        }

        public PropertySet getPropertySet() {
            return propertySet;
        }

        public Date getDate() {
            return date;
        }

        public boolean getShowTime() {
            return showTime;
        }
        
        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + propertySet + ","
                    + date + "," + showTime + ")";
        }
    }

    private Path getResourcePath(RequestContext rc, String uri) {
        // Be lenient on trailing slash
        uri = uri.endsWith("/") && !uri.equals("/") ? uri.substring(0, uri.lastIndexOf("/")) : uri;

        try {
            if (!uri.startsWith("/")) {
                return rc.getCurrentCollection().extend(uri);
            }
            return Path.fromString(uri);
        } catch (IllegalArgumentException iae) {
            return null;
        }
    }

    @Required
    public void setSearch(SearchComponent search) {
        this.search = search;
    }

    @Required
    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }

    @Required
    public void setDefaultElementOrder(List<String> defaultElementOrder) {
        this.defaultElementOrder = defaultElementOrder;
    }

    @Override
    protected String getDescriptionInternal() {
        return "Inserts a event list component on the page";
    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(PARAMETER_ADD_TO_CALENDAR, PARAMETER_ADD_TO_CALENDAR_DESC);
        map.put(PARAMETER_ALL_EVENTS_LINK, PARAMETER_ALL_EVENTS_LINK_DESC);
        map.put(PARAMETER_ALL_EVENTS_TEXT, PARAMETER_ALL_EVENTS_TEXT_DESC);
        map.put(PARAMETER_DATE_ICON, PARAMETER_DATE_ICON_DESC);
        map.put(PARAMETER_EVENT_DESCRIPTION, PARAMETER_EVENT_DESCRIPTION_DESC);
        map.put(PARAMETER_EVENTS_TITLE, PARAMETER_EVENTS_TITLE_DESC);
        map.put(PARAMETER_EVENTS_EMPTY_MSG, PARAMETER_EVENTS_EMPTY_MSG_DESC);
        map.put(PARAMETER_INCLUDE_IF_EMPTY, PARAMETER_INCLUDE_IF_EMPTY_DESC);
        map.put(PARAMETER_LIST_ONLY_ONCE, PARAMETER_LIST_ONLY_ONCE_DESC);
        map.put(PARAMETER_MAX_EVENTS, PARAMETER_MAX_EVENTS_DESC);
        map.put(PARAMETER_SHOW_END_TIME, PARAMETER_SHOW_END_TIME_DESC);
        map.put(PARAMETER_SHOW_LOCATION, PARAMETER_SHOW_LOCATION_DESC);
        map.put(PARAMETER_SHOW_PICTURE, PARAMETER_SHOW_PICTURE_DESC);
        map.put(PARAMETER_URI, PARAMETER_URI_DESC);
        map.put(PARAMETER_SHOW_ONLY_ONGOING, PARAMETER_SHOW_ONLY_ONGOING_DESC);
        map.put(PARAMETER_SHOW_DATE, PARAMETER_SHOW_DATE_DESC);
        map.put(PARAMETER_EVENT_ELEMENT_ORDER, PARAMETER_EVENT_ELEMENT_ORDER_DESC);
        return map;
    }

}
