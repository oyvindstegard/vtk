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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.security.SecurityContext;
import vtk.web.RequestContext;
import vtk.web.display.collection.event.EventListingHelper;
import vtk.web.display.collection.event.EventListingHelper.SpecificDateSearchType;
import vtk.web.display.collection.event.EventListingSearcher;
import vtk.web.search.Listing;

public class EventListingAsICalController implements Controller {

    private EventListingSearcher searcher;
    private EventListingHelper helper;
    private EventAsICalHelper iCalHelper;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        String token = SecurityContext.getSecurityContext().getToken();
        Resource currentResource = repository.retrieve(token, requestContext.getCurrentCollection(), true);

        Listing events = null;
        SpecificDateSearchType specificDateSearchType = this.helper.getSpecificDateSearchType(request);
        if (specificDateSearchType == null) {
            events = this.searcher.searchUpcoming(request, currentResource, 1, 100, 0);
        } else {
            events = this.searcher.searchSpecificDate(request, currentResource, 100, 1);
        }

        String iCal = this.iCalHelper.getAsICal(events.getPropertySets(), repository.getId());
        if (iCal == null) {
            return null;
        }

        String iCalfileName = this.iCalHelper.getICalFileName(currentResource);
        this.iCalHelper.printResponse(response, iCal, iCalfileName);

        return null;
    }

    @Required
    public void setSearcher(EventListingSearcher searcher) {
        this.searcher = searcher;
    }

    @Required
    public void setHelper(EventListingHelper helper) {
        this.helper = helper;
    }

    @Required
    public void setiCalHelper(EventAsICalHelper iCalHelper) {
        this.iCalHelper = iCalHelper;
    }

}
