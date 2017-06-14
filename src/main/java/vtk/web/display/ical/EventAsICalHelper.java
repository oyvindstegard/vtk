/* Copyright (c) 2012, University of Oslo, Norway
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
package vtk.web.display.ical;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.MultiHostSearcher;
import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Resource;
import vtk.repository.resourcetype.HtmlValueFormatter;
import vtk.web.RequestContext;
import vtk.web.service.URL;

public final class EventAsICalHelper {

    private String startDate;
    private String endDate;
    private String location;
    private String introduction;
    private String title;

    public String getAsICal(List<PropertySet> events) {

        if (events == null || events.size() < 1) {
            return null;
        }

        StringBuilder ical = new StringBuilder();
        ical.append("BEGIN:VCALENDAR\n");
        ical.append("VERSION:2.0\n");
        ical.append("METHOD:PUBLISH\n");
        ical.append("PRODID:-//UiO//VTK//NONSGML v1.0//NO\n");

        String repositoryId = RequestContext.getRequestContext().getRepository().getId();
        for (PropertySet event : events) {
            String iCalEntry = createICalEntryFromEvent(event, repositoryId);
            ical.append(iCalEntry);
        }

        ical.append("END:VCALENDAR");

        return ical.toString();
    }

    public String getICalFileName(Resource event) {
        String name = event.getName();
        if (name.contains(".")) {
            name = name.substring(0, name.indexOf("."));
        }
        return name;
    }
    
    public void printResponse(HttpServletResponse response, String iCal, String iCalfileName) throws IOException {
        response.setContentType("text/calendar;charset=utf-8");
        response.setHeader("Content-Disposition", "filename=" + iCalfileName + ".ics");
        try (PrintWriter responsePrinter = response.getWriter()) {
            responsePrinter.print(iCal);
        }
    }

    private String createICalEntryFromEvent(PropertySet event, String repositoryId) {

        // Spec: http://www.ietf.org/rfc/rfc2445.txt
        // PRODID (4.7.3) & UID (4.8.4.7) added as recommended by spec. DTEND is
        // not required.
        // If DTEND not present, DTSTART will count for both start & end, as
        // stated in spec (4.6.1).

        // We don't create anything unless we have the startdate
        Property startDateProp = getProperty(event, startDate);
        if (startDateProp == null) {
            return null;
        }

        StringBuilder iCalEntry = new StringBuilder();
        iCalEntry.append("BEGIN:VEVENT\n");
        iCalEntry.append("DTSTAMP:").append(getDtstamp()).append("\n");
        iCalEntry.append("UID:").append(getUiD(event, repositoryId)).append("\n");
        iCalEntry.append("DTSTART:").append(getICalDate(startDateProp.getDateValue())).append("Z\n");

        Property endDateProp = getProperty(event, endDate);
        if (endDateProp != null && endDateProp.getDateValue().after(startDateProp.getDateValue())) {
            iCalEntry.append("DTEND:").append(getICalDate(endDateProp.getDateValue())).append("Z\n");
        }

        Property locationProp = getProperty(event, location);
        if (locationProp != null) {
            iCalEntry.append("LOCATION:").append(locationProp.getStringValue()).append("\n");
        }

        Property descriptionProp = getProperty(event, introduction);
        if (descriptionProp != null && StringUtils.isNotBlank(descriptionProp.getStringValue())) {
            iCalEntry.append("DESCRIPTION:").append(getDescription(descriptionProp)).append("\n");
        }

        String summary = event.getPropertyByPrefix(null, title).getStringValue();
        iCalEntry.append("SUMMARY:").append(summary).append("\n");
        iCalEntry.append("END:VEVENT\n");
        return iCalEntry.toString();
    }

    private Property getProperty(PropertySet event, String name) {
        Property prop = event.getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE, name);
        if (prop == null) {
            prop = event.getPropertyByPrefix(null, name);
        }

        return prop;
    }

    private String getDtstamp() {
        return FastDateFormat.getInstance("yyyyMMdd'T'HHmmss'Z'").format(Calendar.getInstance().getTime());
    }

    private String getUiD(PropertySet event, String repositoryId) {

        String at = repositoryId;
        Property urlProp = event.getProperty(Namespace.DEFAULT_NAMESPACE, MultiHostSearcher.URL_PROP_NAME);
        if (urlProp != null) {
            URL url = URL.parse(urlProp.getStringValue());
            at = url.getHost();
        }

        return getICalDate(Calendar.getInstance().getTime()) + "-" +
                String.valueOf(event.getURI().hashCode()).replace("-", "0") +
                "@" + at;
    }

    private String getICalDate(Date date) {
        DateTimeZone zone = DateTimeZone.getDefault();
        Date UTCDate = new Date(zone.convertLocalToUTC(date.getTime(), true));
        FastDateFormat dateFormat = FastDateFormat.getInstance("yyyyMMdd");
        FastDateFormat timeFormat = FastDateFormat.getInstance("HHmmss");
        return dateFormat.format(UTCDate) + "T" + timeFormat.format(UTCDate);
    }

    private String getDescription(Property description) {
        String flattenedDescription = description.getFormattedValue(HtmlValueFormatter.FLATTENED_FORMAT, null);
        // Remove linebreaks and the like...
        flattenedDescription = flattenedDescription.replaceAll("(\r\n|\r|\n|\n\r|\t)", " ");
        // Remove multiple whitespaces between words
        flattenedDescription = flattenedDescription.replaceAll("\\b\\s{2,}\\b", " ");
        return flattenedDescription;
    }

    @Required
    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    @Required
    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    @Required
    public void setLocation(String location) {
        this.location = location;
    }

    @Required
    public void setIntroduction(String introduction) {
        this.introduction = introduction;
    }

    @Required
    public void setTitle(String title) {
        this.title = title;
    }

}
