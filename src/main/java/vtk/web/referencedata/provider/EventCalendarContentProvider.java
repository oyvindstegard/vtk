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
package vtk.web.referencedata.provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.web.RequestContext;
import vtk.web.display.collection.event.EventListingHelper;
import vtk.web.display.collection.event.EventListingHelper.SpecificDateSearchType;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.search.Listing;
import vtk.web.search.SearchComponent;

/**
 * Content provider for event listing calendar. Supplies a set of dates
 * representing days that contain events.
 */
public class EventCalendarContentProvider implements ReferenceDataProvider {

    private EventListingHelper helper;
    private SearchComponent currentMonthSearchComponent;

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {

        SpecificDateSearchType searchType = helper.getSpecificDateSearchType(request);
        if (searchType != null) {
            // valid date on request
            String requestedDate = request.getParameter(EventListingHelper.REQUEST_PARAMETER_DATE);
            model.put("requestedDate", requestedDate);
        }
        RequestContext requestContext = RequestContext.getRequestContext(request);
        String token = requestContext.getSecurityToken();
        Path resourceURI = requestContext.getResourceURI();
        Repository repository = requestContext.getRepository();
        try {
            Resource resource = repository.retrieve(token, resourceURI, true);
            Calendar cal = helper.getCurrentMonth();

            String dateString = request.getParameter(EventListingHelper.REQUEST_PARAMETER_DATE);
            if (dateString != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM");
                try {
                    Date requestedMonth = sdf.parse(dateString);
                    cal.setTime(requestedMonth);
                } catch (ParseException e) {
                    // Ignore, show current month
                }
            }
            Listing plannedEvents = currentMonthSearchComponent.execute(request, resource, 1, 500, 0);
            String eventDates = helper.getCalendarWidgetMonthEventDates(plannedEvents.getPropertySets(), cal);
            model.put("allowedDates", eventDates);

            helper.setCalendarTitles(request, resource, model);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Required
    public void setHelper(EventListingHelper helper) {
        this.helper = helper;
    }

    @Required
    public void setCurrentMonthSearchComponent(SearchComponent currentMonthSearchComponent) {
        this.currentMonthSearchComponent = currentMonthSearchComponent;
    }

}
