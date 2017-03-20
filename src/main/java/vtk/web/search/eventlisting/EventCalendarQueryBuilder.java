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
package vtk.web.search.eventlisting;

import java.util.Calendar;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;
import vtk.repository.Resource;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.PropertyExistsQuery;
import vtk.repository.search.query.PropertyTermQuery;
import vtk.repository.search.query.Query;
import vtk.repository.search.query.TermOperator;
import vtk.web.display.collection.event.EventListingHelper;
import vtk.web.search.SearchComponentQueryBuilder;

public class EventCalendarQueryBuilder implements SearchComponentQueryBuilder {

    private EventListingHelper helper;
    private ResourceTypeTree resourceTypeTree;
    private String startPropDefPointer;
    private String endPropDefPointer;

    @Override
    public Query build(Resource base, HttpServletRequest request) {
        final PropertyTypeDefinition startPropDef = resourceTypeTree.getPropertyDefinitionByName(startPropDefPointer);
        final PropertyTypeDefinition endPropDef = resourceTypeTree.getPropertyDefinitionByName(endPropDefPointer);
        if (startPropDef == null) {
            throw new IllegalStateException("Could not find property definition for '" + startPropDefPointer + "'");
        }
        if (endPropDef == null) {
            throw new IllegalStateException("Could not find property definition for '" + endPropDefPointer + "'");
        }

        Calendar startCal = this.helper.getCurrentMonth();
        long start = startCal.getTimeInMillis();

        // Base query -> requires existence of start date
        AndQuery baseQuery = new AndQuery();
        baseQuery.add(new PropertyExistsQuery(startPropDef, false));

        // Start time is not yet passed
        Query notYetStarted = new PropertyTermQuery(startPropDef, String.valueOf(start), TermOperator.GT);

        // Start time is passed, but end time is not yet passed
        AndQuery notYetEnded = new AndQuery();
        notYetEnded.add(new PropertyTermQuery(startPropDef, String.valueOf(start), TermOperator.LT));
        OrQuery endTimeOr = new OrQuery();
        endTimeOr.add(new PropertyTermQuery(endPropDef, String.valueOf(start), TermOperator.GE));
        endTimeOr.add(new PropertyTermQuery(endPropDef, String.valueOf(this.getToday()), TermOperator.EQ));
        notYetEnded.add(endTimeOr);

        // Start time is now/passed, but there is no end time -> regarded as
        // upcoming for one hour from start time. Only when no specific date is
        // supplied.
        AndQuery noEndDate = new AndQuery();
        long oneHourEarlier = this.getOneHourEarlier();
        noEndDate.add(new PropertyTermQuery(startPropDef, String.valueOf(oneHourEarlier), TermOperator.GE));
        noEndDate.add(new PropertyExistsQuery(endPropDef, true));

        OrQuery calendarQueryConditions = new OrQuery();
        calendarQueryConditions.add(notYetStarted);
        calendarQueryConditions.add(notYetEnded);
        calendarQueryConditions.add(noEndDate);

        baseQuery.add(calendarQueryConditions);

        return baseQuery;

    }

    private long getToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        return calendar.getTimeInMillis();
    }

    private long getOneHourEarlier() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR_OF_DAY, -1);
        return calendar.getTimeInMillis();
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

    @Required
    public void setHelper(EventListingHelper helper) {
        this.helper = helper;
    }
}
