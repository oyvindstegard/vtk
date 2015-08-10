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
package vtk.web.display.collection.event;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.apache.abdera.model.Feed;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.RequestContext;
import vtk.web.display.collection.event.EventListingHelper.SpecificDateSearchType;
import vtk.web.display.feed.AtomFeedGenerator;
import vtk.web.search.Listing;
import vtk.web.search.ListingEntry;
import vtk.web.service.Service;

public class EventListingAtomFeedGenerator extends AtomFeedGenerator {

    private EventListingHelper helper;
    private EventListingSearcher searcher;
    private PropertyTypeDefinition displayTypePropDef;
    private String overridePublishDatePropDefPointer;

    @Override
    protected void addFeedEntries(HttpServletRequest request, 
            Feed feed, Resource feedScope) throws Exception {

        Listing entryElements = null;
        Property displayTypeProp = feedScope.getProperty(displayTypePropDef);
        if (displayTypeProp != null && "calendar".equals(displayTypeProp.getStringValue())) {
            SpecificDateSearchType searchType = helper.getSpecificDateSearchType(request);
            if (searchType != null) {
                entryElements = searcher.searchSpecificDate(request, feedScope, entryCountLimit, 1);
            }
        }
        if (entryElements == null) {
            entryElements = searcher.searchUpcoming(request, feedScope, 1, entryCountLimit, 0);
        }

        for (ListingEntry entry : entryElements.getEntries()) {
            PropertySet feedEntry = entry.getPropertySet();
            addPropertySetAsFeedEntry(request, feed, feedEntry);
        }
    }

    @Override
    protected String getFeedTitle(HttpServletRequest request, Resource feedScope) {
        RequestContext requestContext = RequestContext.getRequestContext();
        Service service = requestContext.getService();
        String feedTitle = service.getLocalizedName(feedScope, requestContext.getServletRequest());
        feedTitle = feedTitle == null ? super.getFeedTitle(request, feedScope) : feedTitle;

        Property displayTypeProp = feedScope.getProperty(displayTypePropDef);
        if (displayTypeProp != null && "calendar".equals(displayTypeProp.getStringValue())) {
            SpecificDateSearchType searchType = helper.getSpecificDateSearchType(request);
            if (searchType != null) {
                Date date = helper.getSpecificSearchDate(request);
                String messageKey = searchType == SpecificDateSearchType.Day ? "eventListing.specificDayEvent"
                        : "eventListing.specificDateEvent";
                feedTitle = helper.getEventTypeTitle(request, feedScope, searchType, date, messageKey, true, false);
            } else {
                String viewType = request.getParameter(EventListingHelper.REQUEST_PARAMETER_VIEW);
                if (EventListingHelper.VIEW_TYPE_ALL_UPCOMING.equals(viewType)
                        || EventListingHelper.VIEW_TYPE_ALL_PREVIOUS.equals(viewType)) {
                    feedTitle = helper.getEventTypeTitle(request, feedScope, "eventListing.allupcoming", false);
                }
            }
        }

        return feedTitle;
    }

    @Override
    protected boolean showFeedIntroduction(Resource feedScope) {
        Property displayTypeProp = feedScope.getProperty(displayTypePropDef);
        if (displayTypeProp != null && "calendar".equals(displayTypeProp.getStringValue())) {
            return false;
        }
        return true;
    }

    @Override
    protected Property getPublishDate(PropertySet resource) {
        Property sortProp = helper.getStartDateProperty(resource);
        if (sortProp == null) {
            sortProp = helper.getEndDateProperty(resource);
        }
        return sortProp != null ? sortProp : getDefaultPublishDate(resource);
    }

    @Override
    protected Date getLastModified(PropertySet resource) {
        PropertyTypeDefinition overridePublishDatePropDef = resourceTypeTree
                .getPropertyDefinitionByPointer(overridePublishDatePropDefPointer);
        Property overridePublishDateProp = resource.getProperty(overridePublishDatePropDef);
        if (overridePublishDateProp != null) {
            return overridePublishDateProp.getDateValue();
        }
        return resource.getProperty(lastModifiedPropDef).getDateValue();
    }

    @Required
    public void setHelper(EventListingHelper helper) {
        this.helper = helper;
    }

    @Required
    public void setSearcher(EventListingSearcher searcher) {
        this.searcher = searcher;
    }

    @Required
    public void setDisplayTypePropDef(PropertyTypeDefinition displayTypePropDef) {
        this.displayTypePropDef = displayTypePropDef;
    }

    @Required
    public void setOverridePublishDatePropDefPointer(String overridePublishDatePropDefPointer) {
        this.overridePublishDatePropDefPointer = overridePublishDatePropDefPointer;
    }

}
