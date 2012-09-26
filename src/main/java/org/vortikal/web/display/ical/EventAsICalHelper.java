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
package org.vortikal.web.display.ical;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Property;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.Resource;
import org.vortikal.repository.resourcetype.HtmlValueFormatter;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.util.net.NetUtils;

public final class EventAsICalHelper {

    private PropertyTypeDefinition startDatePropDef;
    private PropertyTypeDefinition endDatePropDef;
    private PropertyTypeDefinition locationPropDef;
    private PropertyTypeDefinition introductionPropDef;
    private PropertyTypeDefinition titlePropDef;

    public String getAsICal(List<PropertySet> events) {
        
        System.out.println("Generating iCal...");

        if (events == null || events.size() < 1) {
            return null;
        }

        StringBuilder ical = new StringBuilder();
        ical.append("BEGIN:VCALENDAR\n");
        ical.append("VERSION:2.0\n");
        ical.append("METHOD:PUBLISH\n");
        ical.append("PRODID:-//UiO//Vortikal//NONSGML v1.0//NO\n");

        for (PropertySet event : events) {
            String iCalEntry = this.createICalEntryFromEvent(event);
            ical.append(iCalEntry);
        }

        ical.append("END:VCALENDAR");

        return ical.toString();
    }

    // XXX Better naming?
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
        ServletOutputStream out = response.getOutputStream();
        out.print(iCal);
        out.close();
    }

    private String createICalEntryFromEvent(PropertySet event) {

        // Spec: http://www.ietf.org/rfc/rfc2445.txt
        // PRODID (4.7.3) & UID (4.8.4.7) added as recommended by spec. DTEND is
        // not required.
        // If DTEND not present, DTSTART will count for both start & end, as
        // stated in spec (4.6.1).

        // We don't create anything unless we have the startdate
        Property startDate = this.getProperty(event, this.startDatePropDef);
        if (startDate == null) {
            return null;
        }

        StringBuilder iCalEntry = new StringBuilder();
        iCalEntry.append("BEGIN:VEVENT\n");
        iCalEntry.append("DTSTAMP:" + this.getDtstamp() + "\n");
        iCalEntry.append("UID:" + this.getUiD(Calendar.getInstance().getTime()) + "\n");
        iCalEntry.append("DTSTART:" + this.getICalDate(startDate.getDateValue()) + "Z\n");

        Property endDate = this.getProperty(event, this.endDatePropDef);
        if (endDate != null) {
            iCalEntry.append("DTEND:" + this.getICalDate(endDate.getDateValue()) + "Z\n");
        }

        Property location = this.getProperty(event, this.locationPropDef);
        if (location != null) {
            iCalEntry.append("LOCATION:" + location.getStringValue() + "\n");
        }

        Property description = this.getProperty(event, this.introductionPropDef);
        if (description != null && StringUtils.isNotBlank(description.getStringValue())) {
            iCalEntry.append("DESCRIPTION:" + this.getDescription(description) + "\n");
        }

        String summary = event.getProperty(this.titlePropDef).getStringValue();
        iCalEntry.append("SUMMARY:" + summary + "\n");
        iCalEntry.append("END:VEVENT\n");
        return iCalEntry.toString();
    }

    private Property getProperty(PropertySet event, PropertyTypeDefinition propDef) {
        Property prop = event.getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE, propDef.getName());
        if (prop == null) {
            prop = event.getProperty(propDef);
        }
        return prop;
    }

    private String getDtstamp() {
        String dateFormat = "yyyyMMdd'T'HHmmss'Z'";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        return sdf.format(Calendar.getInstance().getTime());
    }

    private String getUiD(Date currenttime) {
        return getICalDate(currenttime) + "-" + Calendar.getInstance().getTimeInMillis() + "@"
                + NetUtils.guessHostName();
    }

    private String getICalDate(Date date) {
        DateTimeZone zone = DateTimeZone.getDefault();
        Date UTCDate = new Date(zone.convertLocalToUTC(date.getTime(), true));
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HHmmss");
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
    public void setStartDatePropDef(PropertyTypeDefinition startDatePropDef) {
        this.startDatePropDef = startDatePropDef;
    }

    @Required
    public void setEndDatePropDef(PropertyTypeDefinition endDatePropDef) {
        this.endDatePropDef = endDatePropDef;
    }

    @Required
    public void setLocationPropDef(PropertyTypeDefinition locationPropDef) {
        this.locationPropDef = locationPropDef;
    }

    @Required
    public void setIntroductionPropDef(PropertyTypeDefinition introductionPropDef) {
        this.introductionPropDef = introductionPropDef;
    }

    @Required
    public void setTitlePropDef(PropertyTypeDefinition titlePropDef) {
        this.titlePropDef = titlePropDef;
    }

}
