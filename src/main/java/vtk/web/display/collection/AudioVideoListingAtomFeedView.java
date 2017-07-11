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

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.abdera.Abdera;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.abdera.model.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.resourcetype.ValueFormatter;
import vtk.text.html.HtmlUtil;
import vtk.web.RequestContext;
import vtk.web.display.feed.ListingFeedView;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class AudioVideoListingAtomFeedView extends ListingFeedView {

    private final Logger logger = LoggerFactory.getLogger(AudioVideoListingAtomFeedView.class);

    private final String VIDEOREF_TYPE = "videoref";

    private PropertyTypeDefinition videoHtmlDescriptionPropDef;
    private PropertyTypeDefinition audioHtmlDescriptionPropDef;
    private PropertyTypeDefinition posterImagePropDef;
    private Service thumbnailService;

    @Override
    protected void addPropertySetAsFeedEntry(HttpServletRequest request, Map<String, ?> model,
            Feed feed, PropertySet result) {

        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();

        try {
            Entry entry = Abdera.getInstance().newEntry();

            Property publishedDateProp = getPublishDate(result);
            publishedDateProp = publishedDateProp == null ? result.getProperty(creationTimePropDef) : publishedDateProp;
            String id = getId(request, result.getURI(), publishedDateProp, null);
            entry.setId(id);
            entry.addCategory(result.getResourceType());

            Property title = result.getProperty(titlePropDef);
            if (title != null) {
                entry.setTitle(title.getFormattedValue());
            }

            Link mediaLink = abdera.getFactory().newLink();
            URL href = viewService.urlConstructor(URL.create(request)).withURI(result.getURI()).constructURL();
            mediaLink.setHref(href.toString());
            mediaLink.setRel("enclosure");
            Resource mediaResource = repository.retrieve(token, result.getURI(), true);
            mediaLink.setMimeType(mediaResource.getContentType());
            entry.addLink(mediaLink);

            // Item poster image link to summary
            StringBuilder summary = new StringBuilder();
            Property posterImageProp = result.getProperty(posterImagePropDef);
            if (posterImageProp != null) {
                URL base = URL.create(request).relativeURL(posterImageProp.getStringValue());
                URL posterImageURL = viewService.urlConstructor(base)
                        .withURI(base.getPath()).constructURL();
                summary.append("<img src=\"").append(
                        HtmlUtil.encodeBasicEntities(posterImageURL.toString())).append("\"/>");
            }
            else if (result.getResourceType().equals(VIDEOREF_TYPE)) {
                // We can assume that a videoref will always have a thumbnail
                URL thumbnailURL = thumbnailService.urlConstructor(URL.create(request))
                        .withURI(result.getURI()).constructURL();
                summary.append("<img src=\"").append(
                        HtmlUtil.encodeBasicEntities(thumbnailURL.toString())).append("\"/>");
            }

            // Item description to summary
            Property introductionProp = result.getProperty(videoHtmlDescriptionPropDef);
            if (introductionProp == null) {
                introductionProp = result.getProperty(audioHtmlDescriptionPropDef);
            }
            if (introductionProp != null) {
                summary.append(introductionProp.getStringValue());
            }

            if (summary.length() > 0) {
                entry.setSummaryAsXhtml(summary.toString());
            }

            Property publishDate = getPublishDate(result);
            if (publishDate != null) {
                entry.setPublished(publishDate.getDateValue());
            }

            Property updated = result.getProperty(lastModifiedPropDef);
            if (updated != null) {
                entry.setUpdated(updated.getDateValue());
            }

            Property author = getProperty(result, "resource:author");
            if (author != null) {
                ValueFormatter vf = author.getDefinition().getValueFormatter();
                if (author.getDefinition().isMultiple()) {
                    for (Value v : author.getValues()) {
                        entry.addAuthor(vf.valueToString(v, "name", null));
                    }
                } else {
                    entry.addAuthor(author.getFormattedValue("name", null));
                }
            }

            Link link = abdera.getFactory().newLink();
            
            URL alternateURL = viewService.urlConstructor(URL.create(request))
                    .withURI(result.getURI()).constructURL();
            
            link.setHref(alternateURL.toString());
            link.setRel("alternate");
            entry.addLink(link);

            feed.addEntry(entry);

        } catch (Throwable t) {
            // Don't break the entire feed if the entry breaks
            logger.warn("An error occured while creating feed entry for " + result.getURI(), t);
        }

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
    public void setPosterImagePropDef(PropertyTypeDefinition posterImagePropDef) {
        this.posterImagePropDef = posterImagePropDef;
    }

    @Required
    public void setThumbnailService(Service thumbnailService) {
        this.thumbnailService = thumbnailService;
    }

}
