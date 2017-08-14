/* Copyright (c) 2011, University of Oslo, Norway
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
import java.util.Date;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Resource;
import vtk.repository.search.query.Query;
import vtk.web.display.collection.event.EventListingHelper;
import vtk.web.display.collection.event.EventListingHelper.SpecificDateSearchType;
import vtk.web.search.ParameterizedQueryStringProcessor;

public class DateAndSearchTypeQueryStringProcessor extends ParameterizedQueryStringProcessor {

    private EventListingHelper helper;

    @Override
    public Optional<Query> build(Resource base, HttpServletRequest request) {

        String query = this.queryString;
        Calendar cal = Calendar.getInstance();
        Date trimDate = cal.getTime();
        SpecificDateSearchType searchType = this.helper.getSpecificDateSearchType(request);
        if (searchType != null) {
            Date date = this.helper.getSpecificSearchDate(request);
            if (date != null) {
                cal.setTime(date);
                trimDate = date;
            }
            switch (searchType) {
            case Day:
                cal.add(Calendar.DAY_OF_MONTH, 1);
                break;
            case Month:
                cal.add(Calendar.MONTH, 1);
                break;
            case Year:
                cal.add(Calendar.YEAR, 1);
            default:
                break;
            }
        }
        query = query.replace("[1]", String.valueOf(trimDate.getTime())).replace("[2]",
                String.valueOf(cal.getTime().getTime()));

        return Optional.of(queryParser.parse(query));
    }

    @Required
    public void setHelper(EventListingHelper helper) {
        this.helper = helper;
    }

}
