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
import vtk.web.search.SearchComponentQueryBuilder;

public class EventsDateAndTimeQueryBuilder implements SearchComponentQueryBuilder {

    private boolean inverted;
    private ResourceTypeTree resourceTypeTree;
    private String startPropDefPointer;
    private String endPropDefPointer;

    @Override
    public Query build(Resource base, HttpServletRequest request) {

        PropertyTypeDefinition startPropDef = this.resourceTypeTree
                .getPropertyDefinitionByName(this.startPropDefPointer);
        PropertyTypeDefinition endPropDef = this.resourceTypeTree
                .getPropertyDefinitionByName(this.endPropDefPointer);

        if (startPropDef == null || endPropDef == null) {
            throw new IllegalArgumentException("Both start and end date properties must be available");
        }

        long now = Calendar.getInstance().getTimeInMillis();
        long today = this.getToday();
        long oneHourEarlier = this.getOneHourEarlier();

        // Base query -> requires existence of start date
        AndQuery baseQuery = new AndQuery();
        baseQuery.add(new PropertyExistsQuery(startPropDef, false));

        // If inverted, search for previous events, otherwise check for upcoming
        if (this.isInverted()) {

            // End time is passed, but not if end time is a given date (no time
            // supplied)
            AndQuery ended = new AndQuery();
            ended.add(new PropertyTermQuery(endPropDef, String.valueOf(now), TermOperator.LT));
            ended.add(new PropertyTermQuery(endPropDef, String.valueOf(today), TermOperator.NE));

            // No end time given, and start time is past one hour ago
            AndQuery passed = new AndQuery();
            passed.add(new PropertyTermQuery(startPropDef, String.valueOf(oneHourEarlier), TermOperator.LT));
            passed.add(new PropertyExistsQuery(endPropDef, true));

            OrQuery previousConditions = new OrQuery();
            previousConditions.add(ended);
            previousConditions.add(passed);

            baseQuery.add(previousConditions);

        } else {
            // Start time is not yet passed
            Query notYetStarted = new PropertyTermQuery(startPropDef, String.valueOf(now), TermOperator.GT);

            // Start time is now/passed, but there is no end time -> regarded as
            // upcoming for one hour from start time
            AndQuery noEndDate = new AndQuery();
            noEndDate.add(new PropertyTermQuery(startPropDef, String.valueOf(oneHourEarlier), TermOperator.GE));
            noEndDate.add(new PropertyExistsQuery(endPropDef, true));

            // Start time is passed, but end time is not yet passed
            AndQuery notYetEnded = new AndQuery();
            notYetEnded.add(new PropertyTermQuery(startPropDef, String.valueOf(now), TermOperator.LT));
            OrQuery endTimeOr = new OrQuery();
            endTimeOr.add(new PropertyTermQuery(endPropDef, String.valueOf(now), TermOperator.GE));
            endTimeOr.add(new PropertyTermQuery(endPropDef, String.valueOf(today), TermOperator.EQ));
            notYetEnded.add(endTimeOr);

            OrQuery upcomingConditions = new OrQuery();
            upcomingConditions.add(notYetStarted);
            upcomingConditions.add(noEndDate);
            upcomingConditions.add(notYetEnded);

            baseQuery.add(upcomingConditions);
        }

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

    public boolean isInverted() {
        return inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
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
