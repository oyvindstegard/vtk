/* Copyright (c) 2008, University of Oslo, Norway
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
package vtk.web.display.feed;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.abdera.Abdera;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.abdera.model.Link;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openxri.IRIUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.View;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.HtmlValueFormatter;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.resourcetype.ValueFormatter;
import vtk.text.html.HtmlElement;
import vtk.text.html.HtmlFragment;
import vtk.text.html.HtmlUtil;
import vtk.util.text.TextUtils;
import vtk.web.RequestContext;
import vtk.web.TitleResolver;
import vtk.web.display.collection.BaseCollectionListingController;
import vtk.web.display.listing.ListingPager;
import vtk.web.search.Listing;
import vtk.web.search.ListingEntry;
import vtk.web.search.MultiHostUtil;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 *
 * Creates an Atom feed using the Apache Abdera library≈
 *
 * Subclasses provide results for and add entries to feed, as well
 * as override title and certain other properties (date,
 * author ++).
 *
 */
public class ListingFeedView implements View {

    private static final Log logger = LogFactory.getLog(ListingFeedView.class);

    public static final String TAG_PREFIX = "tag:";
    private static final String THUMBNAIL = "thumbnail";

    private Map<String,String> feedMetadata = null;
    protected Service viewService;
    protected Abdera abdera;
    protected ResourceTypeTree resourceTypeTree;
    protected PropertyTypeDefinition publishDatePropDef;
    protected HtmlUtil htmlUtil;
    protected TitleResolver titleResolver;
    protected boolean useProtocolRelativeImages = true;

    protected PropertyTypeDefinition titlePropDef;
    protected PropertyTypeDefinition lastModifiedPropDef;
    protected PropertyTypeDefinition creationTimePropDef;
    protected PropertyTypeDefinition numberOfCommentsPropDef;

    private String authorPropDefPointer;
    private String introductionPropDefPointer;
    private String picturePropDefPointer;
    private String mediaPropDefPointer;
    private List<String> introductionAsXHTMLSummaryResourceTypes;

    @Override
    public String getContentType() {
        return "application/atom+xml;charset=utf-8";
    }

    @Override
    public void render(Map<String, ?> model, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        Resource feedScope = getFeedScope(request);

        @SuppressWarnings("unchecked")
        List<Listing> listings = (List<Listing>)
                model.get(BaseCollectionListingController.MODEL_KEY_SEARCH_COMPONENTS);

        if (listings == null) throw new IllegalStateException(
                "Expected object in model with key "
                        + BaseCollectionListingController.MODEL_KEY_SEARCH_COMPONENTS);

        ListingPager.Pagination pagination = (ListingPager.Pagination)
                model.get(BaseCollectionListingController.MODEL_KEY_PAGINATION);

        Feed feed = createFeed(request, feedScope, model);
        addFeedLinks(request, feedScope, feed);

        if (pagination != null) addPagination(feed, pagination);

        addEntries(request, model, listings, feedScope, feed);
        printFeed(feed, response);
    }


    protected void addEntries(HttpServletRequest request, Map<String, ?> model,
            List<Listing> listings, Resource feedScope, Feed feed) {
        for (Listing listing: listings) {
            for (ListingEntry entry: listing.getEntries()) {
                addPropertySetAsFeedEntry(request, model, feed, entry.getPropertySet());
            }
        }
    }


    protected void addPagination(Feed feed, ListingPager.Pagination pagination) {

        feed.addLink(pagination.first().toString(), "first");
        feed.addLink(pagination.last().toString(), "last");

        if (pagination.previous().isPresent())
            feed.addLink(pagination.previous().get().toString(), "previous");

        if (pagination.next().isPresent())
            feed.addLink(pagination.next().get().toString(), "next");
    }

    protected Feed createFeed(HttpServletRequest request, Resource feedScope,
            Map<String, ?> model) throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext();

        Feed feed = abdera.newFeed();

        String feedTitle = getFeedTitle(request, model, feedScope);
        feed.setTitle(TextUtils.removeUnprintables(feedTitle));

        if (feedMetadata != null) {
            for (String key: feedMetadata.keySet()) {
                feed.addSimpleExtension("vrtx", key, "v", feedMetadata.get(key));
            }
        }

        Property publishedDateProp = getPublishDate(feedScope);
        publishedDateProp = publishedDateProp == null
                ? feedScope.getProperty(creationTimePropDef) : publishedDateProp;
        feed.setId(getId(feedScope.getURI(), publishedDateProp, getFeedPrefix()));

        feed.addAuthor(requestContext.getRepository().getId());
        feed.setUpdated(getLastModified(feedScope));

        // Whether or not to display collection introduction in feed
        boolean showIntroduction = showFeedIntroduction(feedScope);
        if (showIntroduction) {
            String subTitle = getIntroduction(feedScope);
            if (subTitle != null) {
                feed.setSubtitleAsXhtml(TextUtils.removeUnprintables(subTitle));
            }
            else {
                subTitle = getDescription(feedScope);
                if (subTitle != null) {
                    feed.setSubtitle(TextUtils.removeUnprintables(subTitle));
                }
            }

            Property picture = getProperty(feedScope, picturePropDefPointer);
            if (picture != null) {
                String val = picture.getFormattedValue(THUMBNAIL, Locale.getDefault());
                feed.setLogo(val);
            }
        }
        return feed;
    }

    protected void addFeedLinks(HttpServletRequest request, Resource feedScope, Feed feed) {
        RequestContext requestContext = RequestContext.getRequestContext();
        URL feedAlternateURL = viewService.constructURL(feedScope);
        feed.addLink(feedAlternateURL.toString(), "alternate");
        feed.addLink(requestContext.getRequestURL().toString(), "self");
    }

    /**
     * Add the appropriate resource properties to the Entry
     *
     * @param request the current servlet request
     * @param model the MVC model
     * @param feed the resulting feed
     * @param resource the current property set
     */
    protected void addPropertySetAsFeedEntry(HttpServletRequest request, Map<String, ?> model,
            Feed feed, PropertySet resource) {
        try {

            Entry entry = Abdera.getInstance().newEntry();

            Property publishedDateProp = getPublishDate(resource);
            publishedDateProp = publishedDateProp == null
                    ? resource.getProperty(creationTimePropDef) : publishedDateProp;
            String id = getId(resource.getURI(), publishedDateProp, null);
            entry.setId(id);
            entry.addCategory(resource.getResourceType());

            Property title = resource.getProperty(titlePropDef);
            if (title != null) {
                entry.setTitle(TextUtils.removeUnprintables(title.getFormattedValue()));
            }

            Property numberOfComments = resource.getProperty(numberOfCommentsPropDef);
            if (numberOfComments != null) {
                entry.addSimpleExtension("vrtx", "numberofcomments", "v", numberOfComments.getFormattedValue());
            }

            // Set the summary
            setFeedEntrySummary(request, entry, resource);

            Property publishDate = getPublishDate(resource);
            if (publishDate != null) {
                entry.setPublished(publishDate.getDateValue());
            }

            Date updated = getLastModified(resource);
            if (updated != null) {
                entry.setUpdated(updated);
            }

            Property author = getProperty(resource, authorPropDefPointer);
            if (author != null) {
                ValueFormatter vf = author.getDefinition().getValueFormatter();
                if (author.getDefinition().isMultiple()) {
                    for (Value v : author.getValues()) {
                        entry.addAuthor(vf.valueToString(v, "name", null));
                    }
                }
                else {
                    entry.addAuthor(author.getFormattedValue("name", null));
                }
            }

            Link link = abdera.getFactory().newLink();
            String urlString;
            if (!MultiHostUtil.isMultiHostPropertySet(resource)) {
                urlString = viewService.constructLink(resource.getURI());
            }
            else {
                urlString = MultiHostUtil.getMultiHostUrlProp(resource).getStringValue();
            }
            link.setHref(urlString);
            link.setRel("alternate");
            entry.addLink(link);

            if (resource.getResourceType().equals("structured-event")) {
                entry.addSimpleExtension("vrtx", "ical-url", "v", urlString + "?vrtx=ical");
            }

            Property mediaRef = getProperty(resource, mediaPropDefPointer);
            if (mediaRef != null) {
                try {
                    Link mediaLink = abdera.getFactory().newLink();
                    Path propRef = getPropRef(resource, mediaRef.getStringValue());
                    if (propRef != null) {
                        RequestContext requestContext = RequestContext.getRequestContext();
                        mediaLink.setHref(viewService.constructLink(propRef));
                        mediaLink.setRel("enclosure");
                        Repository repository = requestContext.getRepository();
                        String token = requestContext.getSecurityToken();
                        Resource mediaResource = repository.retrieve(token, propRef, true);
                        mediaLink.setMimeType(mediaResource.getContentType());
                        entry.addLink(mediaLink);
                    }
                }
                catch (Throwable t) {
                    // Don't break the entire entry if media link breaks
                    logger.warn("An error occured while setting media link for feed entry, " + resource.getURI() + ": "
                            + t.getMessage());
                }
            }
            addExtensions(request, model, feed, entry, resource);
            feed.addEntry(entry);

        }
        catch (Throwable t) {
            // Don't break the entire feed if the entry breaks
            logger.warn("An error occured while creating feed entry for " + resource.getURI(), t);
        }
    }

    protected boolean isExtendedFormat(HttpServletRequest request) {
        return "extended".equals(request.getParameter("format"));
    }

    protected void addExtensions(HttpServletRequest request, Map<String, ?> model,
            Feed feed, Entry entry, PropertySet resource) {
        Property numberOfComments = resource.getProperty(numberOfCommentsPropDef);
        if (numberOfComments != null) {
            entry.addSimpleExtension("vrtx", "numberofcomments", "v", numberOfComments.getFormattedValue());
        }

        String imageRef = imageRef(resource);
        if (imageRef != null) {
            entry.addSimpleExtension("vrtx", "image", "v", imageRef);
        }

        String imageThumbnailRef = imageThumbnailRef(resource);
        if (imageThumbnailRef != null) {
            entry.addSimpleExtension("vrtx", "image-thumbnail", "v", imageThumbnailRef);
        }

        String imageCaption = null;
        for (Property property : resource) {
            if ("caption".equals(property.getDefinition().getName())) {
                imageCaption = property.getStringValue();
                break;
            }
        }
        if (imageCaption != null) {
            entry.addSimpleExtension("vrtx", "image-caption", "v", htmlUtil.flatten(imageCaption));
        }
    }

    protected void printFeed(Feed feed, HttpServletResponse response) throws IOException {
        response.setContentType(getContentType());
        PrintWriter writer = response.getWriter();
        feed.writeTo("prettyxml", writer);
        writer.close();
    }

    private HtmlFragment prepareSummary(HttpServletRequest request, PropertySet propSet) {
        StringBuilder sb = new StringBuilder();

        URL baseURL = viewService.constructURL(propSet.getURI());

        if (!isExtendedFormat(request)) {
            // Maintain same behavior for IMAGE_REF in SearchResultMapper as before VTK-4174
            if (MultiHostUtil.isMultiHostPropertySet(propSet)) {
                propSet = MultiHostUtil.resolveImageRefProperties(propSet);
            }

            // Include picture in summary only if "regular" format:
            Property picture = getProperty(propSet, picturePropDefPointer);
            if (picture != null) {
                String imageRef = picture.getStringValue();
                if (!imageRef.startsWith("/") && !imageRef.startsWith("https://")
                        && !imageRef.startsWith("https://")) {
                    try {
                        imageRef = propSet.getURI().getParent().expand(imageRef).toString();
                        picture.setValue(new Value(imageRef, PropertyType.Type.STRING));
                    }
                    catch (Throwable t) { }
                }

                String imgPath = picture.getFormattedValue(THUMBNAIL, Locale.getDefault());
                String imgAlt = getImageAlt(imgPath);
                sb.append("<img src=\"").append(HtmlUtil.encodeBasicEntities(imgPath)).append("\" alt=\"");
                sb.append(HtmlUtil.encodeBasicEntities(imgAlt)).append("\"/>");
            }
        }

        String intro = getIntroduction(propSet);
        if (intro != null) {
            sb.append(intro);
        }

        if (sb.length() > 0) {
            HtmlFragment summary = htmlUtil.linkResolveFilter(sb.toString(), baseURL, RequestContext
                    .getRequestContext().getRequestURL(), useProtocolRelativeImages);
            return summary;
        }
        return null;
    }

    private String imageRef(PropertySet propSet) {
        String ret = null;

        URL baseURL = viewService.constructURL(propSet.getURI());

        Property picture = getProperty(propSet, picturePropDefPointer);
        if (picture != null) {
            String imageRef;
            if (!MultiHostUtil.isMultiHostPropertySet(propSet)) {
                imageRef = picture.getFormattedValue();
            }
            else {
                imageRef = MultiHostUtil.resolveImageRefStringValue(picture,
                        MultiHostUtil.getMultiHostUrlProp(propSet), false); // false = Do not add thumbnail
            }

            /**
             * XXX: Wraps image URLs in a HTML fragment and uses {@link HtmlUtil#linkResolveFilter} to generate
             * protocol-relative URLs, should have been a utility function operating directly on the values.
             */
            String imgHtml = "<img src=\"" + HtmlUtil.encodeBasicEntities(imageRef) + "\" />";
            HtmlFragment imgElem = htmlUtil.linkResolveFilter(imgHtml, baseURL, RequestContext
                    .getRequestContext().getRequestURL(), useProtocolRelativeImages);
            try {
                ret = ((HtmlElement) imgElem.getContent().get(0)).getAttribute("src").getValue();
            }
            catch (Exception e) {
                logger.warn("Failed to generate image thumbnail URL for resource " + propSet.getURI(), e);
            }
        }

        return ret;
    }

    private String imageThumbnailRef(PropertySet propSet) {
        String ret = null;

        URL baseURL = viewService.constructURL(propSet.getURI());

        Property picture = getProperty(propSet, picturePropDefPointer);
        if (picture != null) {
            String imageRef;
            if (!MultiHostUtil.isMultiHostPropertySet(propSet)) {
                imageRef = picture.getFormattedValue(THUMBNAIL, Locale.getDefault());
            }
            else {
                imageRef = MultiHostUtil.resolveImageRefStringValue(picture,
                        MultiHostUtil.getMultiHostUrlProp(propSet), true); // true = add thumbnail if possible
            }

            if (imageRef.contains("vrtx=thumbnail")) {
                /**
                 * XXX: Wraps image URLs in a HTML fragment and uses {@link HtmlUtil#linkResolveFilter} to generate
                 * protocol-relative URLs, should have been a utility function operating directly on the values.
                 */
                String imgHtml = "<img src=\"" + HtmlUtil.encodeBasicEntities(imageRef) + "\" />";
                HtmlFragment imgElem = htmlUtil.linkResolveFilter(imgHtml, baseURL, RequestContext
                        .getRequestContext().getRequestURL(), useProtocolRelativeImages);
                try {
                    ret = ((HtmlElement) imgElem.getContent().get(0)).getAttribute("src").getValue();
                }
                catch (Exception e) {
                    logger.warn("Failed to generate image thumbnail URL for resource " + propSet.getURI(), e);
                }
            }
        }

        return ret;
    }


    protected String getIntroduction(PropertySet resource) {
        Property introductionProp = getProperty(resource, introductionPropDefPointer);
        return introductionProp != null ? introductionProp.getFormattedValue() : null;
    }

    protected String getDescription(PropertySet resource) {
        Namespace NS_CONTENT = Namespace.getNamespace("http://www.uio.no/content");
        Property prop = resource.getProperty(NS_CONTENT, PropertyType.DESCRIPTION_PROP_NAME);
        return prop != null ? prop.getFormattedValue(HtmlValueFormatter.FLATTENED_FORMAT, null) : null;
    }

    protected Path getPropRef(PropertySet resource, String val) {
        if (val.startsWith("/")) {
            return Path.fromString(val);
        }
        if (val.startsWith("http://") || val.startsWith("https://")) {
            // Only relative references are supported:
            return null;
        }
        Property collectionProp = resource.getProperty(Namespace.DEFAULT_NAMESPACE,
                PropertyType.COLLECTION_PROP_NAME);
        if (collectionProp != null && collectionProp.getBooleanValue() == true) {
            return resource.getURI().extend(val);
        }
        return resource.getURI().getParent().extend(val);
    }

    protected String getId(Path resourceUri, Property publishedDateProp, String prefix)
            throws URIException, UnsupportedEncodingException {
        String host = viewService.constructURL(resourceUri).getHost();
        StringBuilder sb = new StringBuilder(TAG_PREFIX);
        sb.append(host + ",");
        sb.append(publishedDateProp.getFormattedValue("iso-8601-short", null) + ":");
        if (prefix != null) {
            sb.append(prefix);
        }
        String uriString = resourceUri.toString();
        // Remove any invalid character before decoding
        uriString = removeInvalid(uriString);
        uriString = URIUtil.decode(uriString);
        // Remove any unknown character after decoding
        uriString = removeInvalid(uriString);
        uriString = URIUtil.encode(uriString, null);
        String iriString = IRIUtils.URItoIRI(uriString);
        sb.append(iriString);
        return sb.toString();
    }

    protected String getFeedPrefix() {
        return null;
    }

    protected Property getProperty(PropertySet resource, String propDefPointer) {
        PropertyTypeDefinition propDef = resourceTypeTree
                .getPropertyDefinitionByPointer(propDefPointer);

        if (propDef != null) {
            Property prop = resource.getProperty(propDef);
            if (prop == null && propDefPointer.contains(":")) {
                String defaultPropDefPointer = propDefPointer
                        .substring(propDefPointer.indexOf(":") + 1,
                                propDefPointer.length());
                propDef = resourceTypeTree.getPropertyDefinitionByPointer(defaultPropDefPointer);
                if (propDef != null) {
                    prop = resource.getProperty(propDef);
                }
            }
            return prop;
        }
        return null;
    }

    protected String removeInvalid(String s) {
        return s.replaceAll("[#%?\\[\\] ]", "");
    }

    private String getImageAlt(String imgPath) {
        try {
            return imgPath.substring(imgPath.lastIndexOf("/") + 1, imgPath.lastIndexOf("."));
        }
        catch (Throwable t) {
            // Don't do anything special, imgAlt isn't all that important
            return "feed_image";
        }
    }

    protected Property getPublishDate(PropertySet propertySet) {
        return propertySet.getProperty(publishDatePropDef);
    }

    /**
     * Gets the feed introduction
     *
     * @param feedScope the feed collection
     */
    protected boolean showFeedIntroduction(Resource feedScope) {
        return true;
    }

    /**
     * Gets the feed title
     *
     * @param request the current servlet request
     * @param model the MVC model
     * @param feedScope the feed collection
     * @return
     */
    protected String getFeedTitle(HttpServletRequest request, Map<String,?> model,
            Resource feedScope) {
        if (titleResolver != null) {
            String feedTitle = titleResolver.resolve(feedScope);
            if (feedTitle != null) return feedTitle;
        }
        return feedScope.getTitle();
    }

    /**
     * Gets the feed scope (collection)
     *
     * @param request the current servlet request
     * @return
     */
    protected Resource getFeedScope(HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        return requestContext.getRepository().retrieve(token, uri, true);
    }

    protected Date getLastModified(PropertySet propertySet) {
        return propertySet.getProperty(lastModifiedPropDef).getDateValue();
    }

    protected void setFeedEntrySummary(HttpServletRequest request,
            Entry entry, PropertySet result) throws Exception {
        String type = result.getResourceType();
        if (type != null && introductionAsXHTMLSummaryResourceTypes.contains(type)) {
            HtmlFragment summary = prepareSummary(request, result);
            if (summary != null) {
                try {
                    entry.setSummaryAsXhtml(summary.getStringRepresentation());
                }
                catch (Exception e) {
                    // Don't remove entry because of illegal characters in
                    // string. In the future, consider blacklist of illegal
                    // characters (VTK-3009).
                    logger.error("Could not set summary as XHTML: " + e.getMessage());
                }
            }
        }
        else {
            // ...add description as plain text else
            String description = getDescription(result);
            if (description != null) {
                entry.setSummary(description);
            }
        }

    }

    public void setFeedMetadata(Map<String,String> feedMetadata) {
        if (feedMetadata != null) {
            this.feedMetadata = new HashMap<>();
            for (String key: feedMetadata.keySet())
                this.feedMetadata.put(key, feedMetadata.get(key));
        }
    }

    @Required
    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }

    @Required
    public void setAbdera(Abdera abdera) {
        this.abdera = abdera;
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    @Required
    public void setTitlePropDef(PropertyTypeDefinition titlePropDef) {
        this.titlePropDef = titlePropDef;
    }

    @Required
    public void setLastModifiedPropDef(PropertyTypeDefinition lastModifiedPropDef) {
        this.lastModifiedPropDef = lastModifiedPropDef;
    }

    @Required
    public void setAuthorPropDefPointer(String authorPropDefPointer) {
        this.authorPropDefPointer = authorPropDefPointer;
    }

    @Required
    public void setIntroductionPropDefPointer(String introductionPropDefPointer) {
        this.introductionPropDefPointer = introductionPropDefPointer;
    }

    @Required
    public void setPicturePropDefPointer(String picturePropDefPointer) {
        this.picturePropDefPointer = picturePropDefPointer;
    }

    @Required
    public void setMediaPropDefPointer(String mediaPropDefPointer) {
        this.mediaPropDefPointer = mediaPropDefPointer;
    }

    @Required
    public void setCreationTimePropDef(PropertyTypeDefinition creationTimePropDef) {
        this.creationTimePropDef = creationTimePropDef;
    }

    @Required
    public void setPublishDatePropDef(PropertyTypeDefinition publishDatePropDef) {
        this.publishDatePropDef = publishDatePropDef;
    }

    @Required
    public void setNumberOfCommentsPropDef(PropertyTypeDefinition numberOfCommentsPropDef) {
        this.numberOfCommentsPropDef = numberOfCommentsPropDef;
    }

    @Required
    public void setIntroductionAsXHTMLSummaryResourceTypes(
            List<String> introductionAsXHTMLSummaryResourceTypes) {
        this.introductionAsXHTMLSummaryResourceTypes = introductionAsXHTMLSummaryResourceTypes;
    }

    @Required
    public void setHtmlUtil(HtmlUtil htmlUtil) {
        this.htmlUtil = htmlUtil;
    }

    public void setTitleResolver(TitleResolver titleResolver) {
        this.titleResolver = titleResolver;
    }

    public void setUseProtocolRelativeImages(boolean useProtocolRelativeImages) {
        this.useProtocolRelativeImages = useProtocolRelativeImages;
    }

}
