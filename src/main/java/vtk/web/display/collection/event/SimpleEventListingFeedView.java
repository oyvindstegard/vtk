/* Copyright (c) 2015, University of Oslo, Norway
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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.apache.abdera.model.Entry;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.display.feed.ListingFeedView;

/**
 * A simpler feed view for event listings. Sorts entries on
 * {@code publish-date} (not {@code start-date/end-date}), 
 * and includes listing-specific data (such as {@code event-start}) as 
 * extension elements.
 */
public class SimpleEventListingFeedView extends ListingFeedView {

    private EventListingHelper helper;
    private EventListingSearcher searcher;
    private PropertyTypeDefinition displayTypePropDef;


    @Override
    protected void addExtensions(Entry entry, PropertySet resource) {
        super.addExtensions(entry, resource);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_INSTANT;
        Property startDateProp = helper.getStartDateProperty(resource);
        if (startDateProp != null) {
            Date date = startDateProp.getDateValue();
            ZonedDateTime zdt = ZonedDateTime.ofInstant(date.toInstant(), ZoneId.of("UTC"));
            entry.addSimpleExtension("vrtx", "event-start", "v", dateFormatter.format(zdt));
        }
        Property endDateProp = helper.getEndDateProperty(resource);
        if (endDateProp != null) {
            Date date = endDateProp.getDateValue();
            ZonedDateTime zdt = ZonedDateTime.ofInstant(date.toInstant(), ZoneId.of("UTC"));
            entry.addSimpleExtension("vrtx", "event-end", "v", dateFormatter.format(zdt));
        }
        
        String location = null;
        String mapURL = null;
        for (Property property: resource) {
            if ("location".equals(property.getDefinition().getName())) {
                location = property.getStringValue();
            }
            else if ("mapurl".equals(property.getDefinition().getName())) {
                mapURL = property.getStringValue();
            }
        }
        if (location != null) {
            entry.addSimpleExtension("vrtx", "event-location", "v", location);
        }
        if (mapURL != null) {
            entry.addSimpleExtension("vrtx", "event-map-url", "v", mapURL);
        }
    }
    
    @Override
    protected boolean showFeedIntroduction(Resource feedScope) {
        Property displayTypeProp = feedScope.getProperty(displayTypePropDef);
        if (displayTypeProp != null && "calendar".equals(displayTypeProp.getStringValue())) {
            return false;
        }
        return true;
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

}
