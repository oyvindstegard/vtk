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
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
import org.springframework.web.servlet.ModelAndView;

import vtk.repository.MultiHostSearcher;
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
import vtk.text.html.HtmlFragment;
import vtk.text.html.HtmlUtil;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * 
 * Creates an Atom feed using the Apache Abdera library, adhering to:
 * http://tools.ietf.org/html/rfc4287
 * 
 * Subclasses provide results for and add entries to feed, as well as override
 * title and certain other properties (date, author ++).
 * 
 */
public abstract class AtomFeedGenerator implements FeedGenerator {

    private final Log logger = LogFactory.getLog(AtomFeedGenerator.class);
    public static final String TAG_PREFIX = "tag:";

    protected Service viewService;
    protected Abdera abdera;
    protected ResourceTypeTree resourceTypeTree;
    protected PropertyTypeDefinition publishDatePropDef;
    protected HtmlUtil htmlUtil;
    protected int entryCountLimit = 200;
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

    // Must be overriden by subclasses to provide content for feed entries and
    // add these to feed
    protected abstract void addFeedEntries(Feed feed, Resource feedScope) throws Exception;

    @Override
    public ModelAndView generateFeed(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Resource feedScope = getFeedScope();
        Feed feed = createFeed(feedScope);
        addFeedEntries(feed, feedScope);
        printFeed(feed, response);
        return null;
    }

    protected Feed createFeed(Resource feedScope) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Feed feed = abdera.newFeed();

        String feedTitle = getFeedTitle(feedScope, requestContext);
        feed.setTitle(feedTitle);

        Property publishedDateProp = getPublishDate(feedScope);
        publishedDateProp = publishedDateProp == null ? feedScope.getProperty(creationTimePropDef) : publishedDateProp;
        feed.setId(getId(feedScope.getURI(), publishedDateProp, getFeedPrefix()));

        addFeedLinks(feedScope, feed);
        
        feed.addAuthor(requestContext.getRepository().getId());
        feed.setUpdated(getLastModified(feedScope));

        // Whether or not to display collection introduction in feed
        boolean showIntroduction = showFeedIntroduction(feedScope);
        if (showIntroduction) {
            String subTitle = getIntroduction(feedScope);
            if (subTitle != null) {
                feed.setSubtitleAsXhtml(subTitle);
            } else {
                subTitle = getDescription(feedScope);
                if (subTitle != null) {
                    feed.setSubtitle(subTitle);
                }
            }

            Property picture = getProperty(feedScope, picturePropDefPointer);
            if (picture != null) {
                String val = picture.getFormattedValue(PropertyType.THUMBNAIL_PROP_NAME, Locale.getDefault());
                feed.setLogo(val);
            }
        }
        return feed;
    }

    protected void addFeedLinks(Resource feedScope, Feed feed) {
        RequestContext requestContext = RequestContext.getRequestContext();
        URL feedAlternateURL = viewService.constructURL(feedScope);
        feed.addLink(feedAlternateURL.toString(), "alternate");
        feed.addLink(requestContext.getRequestURL().toString(), "self");
    }

    protected void addPropertySetAsFeedEntry(Feed feed, PropertySet result) {
        try {
	        	
            Entry entry = Abdera.getInstance().newEntry();

            Property publishedDateProp = getPublishDate(result);
            publishedDateProp = publishedDateProp == null ? result.getProperty(creationTimePropDef) : publishedDateProp;
            String id = getId(result.getURI(), publishedDateProp, null);
            entry.setId(id);
            entry.addCategory(result.getResourceType());

            Property title = result.getProperty(titlePropDef);
            if (title != null) {
                entry.setTitle(title.getFormattedValue());
            }

			Property numberOfComments = result.getProperty(numberOfCommentsPropDef);
			if (numberOfComments != null) {
				entry.addCategory("number-of-comments:" + numberOfComments.getFormattedValue());
			}

            // Set the summary
            setFeedEntrySummary(entry, result);

            Property publishDate = getPublishDate(result);
            if (publishDate != null) {
                entry.setPublished(publishDate.getDateValue());
            }

            Date updated = getLastModified(result);
            if (updated != null) {
                entry.setUpdated(updated);
            }

            Property author = getProperty(result, authorPropDefPointer);
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
            String urlString = viewService.constructLink(result.getURI());
            Property urlProp = result.getProperty(Namespace.DEFAULT_NAMESPACE, MultiHostSearcher.URL_PROP_NAME);
            if (urlProp != null) {
                urlString = URL.parse(urlProp.getStringValue()).toString();
            }
            link.setHref(urlString);
            link.setRel("alternate");
            entry.addLink(link);

            Property mediaRef = getProperty(result, mediaPropDefPointer);
            if (mediaRef != null) {
                try {
                    Link mediaLink = abdera.getFactory().newLink();
                    Path propRef = getPropRef(result, mediaRef.getStringValue());
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
                } catch (Throwable t) {
                    // Don't break the entire entry if media link breaks
                    logger.warn("An error occured while setting media link for feed entry, " + result.getURI() + ": "
                            + t.getMessage());
                }
            }

            feed.addEntry(entry);

        } catch (Throwable t) {
            // Don't break the entire feed if the entry breaks
            logger.warn("An error occured while creating feed entry for " + result.getURI(), t);
        }
    }

    protected void printFeed(Feed feed, HttpServletResponse response) throws IOException {
        response.setContentType("application/atom+xml;charset=utf-8");
        feed.writeTo("prettyxml", response.getWriter());
    }

    private HtmlFragment prepareSummary(PropertySet propSet) {
        StringBuilder sb = new StringBuilder();

        URL baseURL = viewService.constructURL(propSet.getURI());

        Property picture = getProperty(propSet, picturePropDefPointer);
        if (picture != null) {
            String imageRef = picture.getStringValue();
            if (!imageRef.startsWith("/") && !imageRef.startsWith("https://") && !imageRef.startsWith("https://")) {
                try {
                    imageRef = propSet.getURI().getParent().expand(imageRef).toString();
                    picture.setValue(new Value(imageRef, PropertyType.Type.STRING));
                } catch (Throwable t) {
                }
            }

            String imgPath = picture.getFormattedValue(PropertyType.THUMBNAIL_PROP_NAME, Locale.getDefault());
            String imgAlt = getImageAlt(imgPath);
            sb.append("<img src=\"" + HtmlUtil.encodeBasicEntities(imgPath) + "\" alt=\""
                    + HtmlUtil.encodeBasicEntities(imgAlt) + "\"/>");
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

    protected Property getDefaultPublishDate(PropertySet result) {
        if (publishDatePropDef != null) {
            return result.getProperty(publishDatePropDef);
        }
        return null;
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
        Property collectionProp = resource.getProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.COLLECTION_PROP_NAME);
        if (collectionProp != null && collectionProp.getBooleanValue() == true) {
            return resource.getURI().extend(val);
        }
        return resource.getURI().getParent().extend(val);
    }

    protected String getId(Path resourceUri, Property publishedDateProp, String prefix) throws URIException,
            UnsupportedEncodingException {
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
        PropertyTypeDefinition propDef = resourceTypeTree.getPropertyDefinitionByPointer(propDefPointer);
        if (propDef != null) {
            Property prop = resource.getProperty(propDef);
            if (prop == null && propDefPointer.contains(":")) {
                String defaultPropDefPointer = propDefPointer.substring(propDefPointer.indexOf(":") + 1,
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
        } catch (Throwable t) {
            // Don't do anything special, imgAlt isn't all that important
            return "feed_image";
        }
    }

    // To be overridden where necessary
    protected Property getPublishDate(PropertySet propertySet) {
        return getDefaultPublishDate(propertySet);
    }

    // To be overridden where necessary
    protected boolean showFeedIntroduction(Resource feedScope) {
        return true;
    }

    // To be overridden where necessary
    protected String getFeedTitle(Resource feedScope, RequestContext requestContext) {
        String feedTitle = feedScope.getTitle();
        if (Path.ROOT.equals(feedScope.getURI())) {
            feedTitle = requestContext.getRepository().getId();
        }
        return feedTitle;
    }

    // To be overridden where necessary
    protected Resource getFeedScope() throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        return requestContext.getRepository().retrieve(token, uri, true);
    }

    // To be overridden where necessary
    protected Date getLastModified(PropertySet propertySet) {
        return propertySet.getProperty(lastModifiedPropDef).getDateValue();
    }

    // To be overridden where necessary
    protected void setFeedEntrySummary(Entry entry, PropertySet result) throws Exception {
        String type = result.getResourceType();
        if (type != null && introductionAsXHTMLSummaryResourceTypes.contains(type)) {
            HtmlFragment summary = prepareSummary(result);
            if (summary != null) {
                try {
                    entry.setSummaryAsXhtml(summary.getStringRepresentation());
                } catch (Exception e) {
                    // Don't remove entry because of illegal characters in
                    // string. In the future, consider blacklist of illegal
                    // characters (VTK-3009).
                    logger.error("Could not set summery as XHTML: " + e.getMessage());
                }
            }
        } else {
            // ...add description as plain text else
            String description = getDescription(result);
            if (description != null) {
                entry.setSummary(description);
            }
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
    public void setIntroductionAsXHTMLSummaryResourceTypes(List<String> introductionAsXHTMLSummaryResourceTypes) {
        this.introductionAsXHTMLSummaryResourceTypes = introductionAsXHTMLSummaryResourceTypes;
    }

    @Required
    public void setHtmlUtil(HtmlUtil htmlUtil) {
        this.htmlUtil = htmlUtil;
    }

    public void setUseProtocolRelativeImages(boolean useProtocolRelativeImages) {
        this.useProtocolRelativeImages = useProtocolRelativeImages;
    }

}
