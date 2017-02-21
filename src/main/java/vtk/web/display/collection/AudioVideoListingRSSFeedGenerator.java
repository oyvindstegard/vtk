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
package vtk.web.display.collection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.time.FastDateFormat;

import org.springframework.beans.factory.annotation.Required;
import vtk.repository.MultiHostSearcher;
import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.RequestContext;
import vtk.web.display.feed.RSSFeedGenerator;
import vtk.web.search.Listing;
import vtk.web.search.ListingEntry;
import vtk.web.search.SearchComponent;
import vtk.web.service.URL;

public class AudioVideoListingRSSFeedGenerator extends RSSFeedGenerator {

    private SearchComponent searchComponent;
    private PropertyTypeDefinition videoHtmlDescriptionPropDef;
    private PropertyTypeDefinition audioHtmlDescriptionPropDef;
    private PropertyTypeDefinition contentLengthPropDef;
    private PropertyTypeDefinition contentTypePropDef;

    @Override
    protected List<Map<String, Object>> getFeedEntries(Resource feedScope) throws Exception {

        Listing entryElements = searchComponent.execute(RequestContext.getRequestContext().getServletRequest(),
                feedScope, 1, 500, 0);

        List<Map<String, Object>> feedEntries = new ArrayList<Map<String, Object>>();

        for (ListingEntry entry : entryElements.getEntries()) {

            PropertySet ps = entry.getPropertySet();
            Map<String, Object> feedEntry = new HashMap<String, Object>();

            // Item title
            String title = ps.getProperty(titlePropDef).getStringValue();
            feedEntry.put("title", title);

            // Item description
            Property introductionProp = ps.getProperty(videoHtmlDescriptionPropDef);
            if (introductionProp == null) {
                introductionProp = ps.getProperty(audioHtmlDescriptionPropDef);
            }
            if (introductionProp != null) {
                feedEntry.put("description", introductionProp.getFormattedValue());
            }

            // Item link
            String urlString = null;
            Property urlProp = ps.getProperty(Namespace.DEFAULT_NAMESPACE, MultiHostSearcher.URL_PROP_NAME);
            if (urlProp != null) {
                urlString = URL.parse(urlProp.getStringValue()).toString();
            } else {
                urlString = viewService.constructLink(ps.getURI());
            }
            feedEntry.put("link", urlString);

            feedEntry.put("enclosure", urlString);
            long contentLength = ps.getProperty(contentLengthPropDef).getLongValue();
            feedEntry.put("length", String.valueOf(contentLength));
            String contentType = ps.getProperty(contentTypePropDef).getStringValue();
            feedEntry.put("type", contentType);

            feedEntry.put("category", ps.getResourceType());

            // Item guid
            feedEntry.put("guid", urlString);

            // Item publish date
            FastDateFormat rfc822_dateformat = FastDateFormat.getInstance("EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss' 'Z");
            String rfc822FormattedPubDate = rfc822_dateformat.format(ps.getProperty(publishDatePropDef).getDateValue());
            feedEntry.put("pubDate", rfc822FormattedPubDate);

            feedEntries.add(feedEntry);

        }

        return feedEntries;

    }

    @Required
    public void setSearchComponent(SearchComponent searchComponent) {
        this.searchComponent = searchComponent;
    }

    @Required
    public void setVideoHtmlDescriptionPropDef(PropertyTypeDefinition videoHtmlDescriptionPropDef) {
        this.videoHtmlDescriptionPropDef = videoHtmlDescriptionPropDef;
    }

    @Required
    public void setAudioHtmlDescriptionPropDef(PropertyTypeDefinition audioHtmlDescriptionPropDef) {
        this.audioHtmlDescriptionPropDef = audioHtmlDescriptionPropDef;
    }

    @Required
    public void setContentLengthPropDef(PropertyTypeDefinition contentLengthPropDef) {
        this.contentLengthPropDef = contentLengthPropDef;
    }

    @Required
    public void setContentTypePropDef(PropertyTypeDefinition contentTypePropDef) {
        this.contentTypePropDef = contentTypePropDef;
    }

}
