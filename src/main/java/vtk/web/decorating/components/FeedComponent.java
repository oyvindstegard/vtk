/* Copyright (c) 2007, University of Oslo, Norway
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
package vtk.web.decorating.components;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;

import vtk.repository.AuthorizationException;
import vtk.repository.ResourceNotFoundException;
import vtk.security.AuthenticationException;
import vtk.text.html.HtmlElement;
import vtk.text.html.HtmlFragment;
import vtk.util.cache.ContentCache;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.service.URL;

/**
 * XXX: this class currently depends on the thread safety of the SyndFeed
 * implementation: if it turns out that it is not thread safe, its data has to
 * be extracted to a custom bean after fetching a feed.
 */
public class FeedComponent extends AbstractFeedComponent {

    private static final String PARAMETER_FEED_DESCRIPTION = "feed-description";
    private static final String PARAMETER_FEED_DESCRIPTION_DESC = "Must be set to 'true' to show feed description";

    private static final String PARAMETER_URL = "url";
    private static final String PARAMETER_URL_DESC = "The feed url. For host "
            + "local feeds, you get authenticated retrieval of the resource if you skip the protocol/host part";

    private static final String PARAMETER_FEED_TITLE = "feed-title";
    private static final String PARAMETER_FEED_TITLE_DESC = "Deprecated (use 'diplay-feed-title' instead). Kept to avoid breaking existing component references. (Set to 'false' if you don't want to show feed title)";

    private static final String PARAMETER_DISPLAY_FEED_TITLE = "display-feed-title";
    private static final String PARAMETER_DISPLAY_FEED_TITLE_DESC = "Set to 'false' if you don't want to show feed title";

    private static final String PARAMETER_OVERRIDE_FEED_TITLE = "override-feed-title";
    private static final String PARAMETER_OVERRIDE_FEED_TITLE_DESC = "Optional string overriding the feed title";

    private static final String PARAMETER_ALL_MESSAGES_LINK = "all-messages-link";
    private static final String PARAMETER_ALL_MESSAGES_LINK_DESC = "Defaults to 'true' displaying 'All messages' link at the bottom. Set to 'false' to remove this link.";

    private static final String PARAMETER_SORT = "sort";
    private static final String PARAMETER_SORT_DESC = "Default sorted by published date. Set to 'item-title' to sort by this instead. "
            + "You can control the direction of the sorting by using the keywords 'asc' or 'desc'. "
            + "Usage examples: sort=[asc], sort=[item-title desc], sort=[published-date asc], etc. "
            + "The default is descending direction (newest first) for published date and ascending when sorting by 'item-title'.";

    private static final String PARAMETER_PUBLISHED_DATE = "published-date";
    private static final String PARAMETER_PUBLISHED_DATE_DESC = "How to display published date, defaults to date and time. Set to 'date' to only display the date, or 'none' to not show the date";

    private static final String PARAMETER_MAX_MESSAGES = "max-messages";
    private static final String PARAMETER_MAX_MESSAGES_DESC = "The max number of messages to display, defaults to 10";

    private static final String PARAMETER_ITEM_DESCRIPTION = "item-description";
    private static final String PARAMETER_ITEM_DESCRIPTION_DESC = "Must be set to 'true' to show item descriptions";

    private static final String PARAMETER_INCLUDE_IF_EMPTY = "include-if-empty";
    private static final String PARAMETER_INCLUDE_IF_EMPTY_DESC = "Set to 'false' if you don't want to display empty feeds. Default is 'true'.";

    private static final String PARAMETER_DISPLAY_CATEGORIES = "display-categories";
    private static final String PARAMETER_DISPLAY_CATEGORIES_DESC = "Set to 'true' if feed elements should display contents of category field.";

    private ContentCache<String, SyndFeed> cache;
    private LocalFeedFetcher localFeedFetcher;

    @Override
    protected void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {
        super.processModel(model, request, response);

        Map<String, Object> conf = new HashMap<String, Object>();

        String url = request.getStringParameter(PARAMETER_URL);
        if (url == null) {
            throw new DecoratorComponentException("Component parameter 'url' is required");
        }

        if (!(parameterHasValue(PARAMETER_DISPLAY_FEED_TITLE, "false", request) || parameterHasValue(
                PARAMETER_FEED_TITLE, "false", request))) {
            conf.put("feedTitle", true);
        }

        if (parameterHasValue(PARAMETER_FEED_DESCRIPTION, "true", request)) {
            conf.put("feedDescription", true);
        }

        if (parameterHasValue(PARAMETER_ITEM_DESCRIPTION, "true", request)) {
            conf.put("itemDescription", true);
        }

        if (parameterHasValue(PARAMETER_ITEM_PICTURE, "true", request)) {
            conf.put("itemPicture", true);
        }

        if (parameterHasValue(PARAMETER_DISPLAY_CATEGORIES, "true", request)) {
            conf.put("displayCategories", true);
        }

        if (!parameterHasValue(PARAMETER_ALL_MESSAGES_LINK, "false", request)) {
            conf.put("linkToAllMessages", true);
        }

        conf.put("includeIfEmpty", !parameterHasValue(PARAMETER_INCLUDE_IF_EMPTY, "false", request));

        String overrideFeedTitle = request.getStringParameter(PARAMETER_OVERRIDE_FEED_TITLE);
        if (overrideFeedTitle != null && overrideFeedTitle.length() > 0) {
            model.put("overrideFeedTitle", overrideFeedTitle);
        }
        String displayIfEmptyMessage = request.getStringParameter(PARAMETER_IF_EMPTY_MESSAGE);
        if (displayIfEmptyMessage != null && displayIfEmptyMessage.length() > 0) {
            model.put("displayIfEmptyMessage", displayIfEmptyMessage);
        }

        conf.put("maxMsgs", 10);
        String maxMsgsString = request.getStringParameter(PARAMETER_MAX_MESSAGES);
        if (maxMsgsString != null) {
            try {
                int tmpInt = Integer.parseInt(maxMsgsString);
                if (tmpInt > 0) {
                    conf.put("maxMsgs", tmpInt);
                }
            } catch (Exception e) {
            }
        }

        String publishedDateString = request.getStringParameter(PARAMETER_PUBLISHED_DATE);
        if ("none".equals(publishedDateString)) {
            conf.put("publishedDate", null);
        } else if ("date".equals(publishedDateString)) {
            conf.put("publishedDate", "short");
        } else {
            conf.put("publishedDate", "long");
        }

        // Typical sort strings we handle:
        // asc
        // item-title
        // item-title desc
        // desc item-title
        // etc..
        String sortString = request.getStringParameter(PARAMETER_SORT);
        boolean directionSpecified = false; // Indicates explicitly set sort
        // direction
        if (sortString != null) {
            StringTokenizer tokenizer = new StringTokenizer(sortString);
            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken();
                if ("item-title".equals(token)) {
                    conf.put("sortByTitle", true);
                    if (!directionSpecified) {
                        // Set to default for title, if not already specified.
                        conf.put("sortAscending", true);
                    }
                } else if ("asc".equalsIgnoreCase(token)) {
                    conf.remove("sortDescending");
                    conf.put("sortAscending", true);
                    directionSpecified = true;
                } else if ("desc".equalsIgnoreCase(token)) {
                    conf.remove("sortAscending");
                    conf.put("sortDescending", true);
                    directionSpecified = true;
                }
            }
        }

        SyndFeed feed = null;
        URL requestURL = RequestContext.getRequestContext().getRequestURL();
        URL baseURL = null;
        URL feedURL = null;
        boolean auth = true;
        try {
            feedURL = requestURL.relativeURL(url);
            if (feedURL.getHost().equals(requestURL.getHost())) {
                baseURL = new URL(requestURL);
                retrieveLocalResource(feedURL);
                feed = this.localFeedFetcher.getFeed(feedURL, request);
            } else {
                baseURL = new URL(feedURL);
                feed = this.cache.get(url);
            }
        } catch (AuthenticationException e) {
            auth = false;
        } catch (AuthorizationException e) {
            auth = false;
        } catch (ResourceNotFoundException e) {
            throw new DecoratorComponentException(url + " does not exist");
        }

        conf.put("auth", auth);
        if (!auth) {
            model.put("conf", conf);
            return;
        }

        baseURL.clearParameters();
        String feedLink = feed.getLink();
        if (feedLink != null) {
            URL viewURL = baseURL.relativeURL(feedLink);
            model.put("viewURL", viewURL);
        }

        List<String> elementOrder = getElementOrder(PARAMETER_FEED_ELEMENT_ORDER, request);
        model.put("elementOrder", elementOrder);

        Map<String, String> descriptionNoImage = new HashMap<String, String>();
        Map<String, String> imgMap = new HashMap<String, String>();

        @SuppressWarnings("unchecked")
        List<SyndEntry> entries = feed.getEntries();
        boolean filter = !parameterHasValue(PARAMETER_ALLOW_MARKUP, "true", request);
        for (SyndEntry entry : entries) {

            HtmlFragment description = getDescription(entry, baseURL, requestURL, filter);

            if (description == null) {
                descriptionNoImage.put(entry.toString(), null);
                continue;
            }

            HtmlElement image = removeImage(description);
            if (image != null) {
                imgMap.put(entry.toString(), image.getEnclosedContent());
            }

            descriptionNoImage.put(entry.toString(), description.getStringRepresentation());

        }
        model.put("descriptionNoImage", descriptionNoImage);
        model.put("imageMap", imgMap);

        model.put("feed", feed);
        model.put("conf", conf);
    }

    @Override
    protected String getDescriptionInternal() {
        return "Inserts a feed (RSS, Atom) component on the page";
    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(PARAMETER_ITEM_PICTURE, PARAMETER_ITEM_PICTURE_DESC);
        map.put(PARAMETER_IF_EMPTY_MESSAGE, PARAMETER_IF_EMPTY_MESSAGE_DESC);
        map.put(PARAMETER_FEED_ELEMENT_ORDER, PARAMETER_FEED_ELEMENT_ORDER_DESC);
        map.put(PARAMETER_URL, PARAMETER_URL_DESC);
        map.put(PARAMETER_MAX_MESSAGES, PARAMETER_MAX_MESSAGES_DESC);
        map.put(PARAMETER_FEED_TITLE, PARAMETER_FEED_TITLE_DESC);
        map.put(PARAMETER_DISPLAY_FEED_TITLE, PARAMETER_DISPLAY_FEED_TITLE_DESC);
        map.put(PARAMETER_OVERRIDE_FEED_TITLE, PARAMETER_OVERRIDE_FEED_TITLE_DESC);
        map.put(PARAMETER_FEED_DESCRIPTION, PARAMETER_FEED_DESCRIPTION_DESC);
        map.put(PARAMETER_ITEM_DESCRIPTION, PARAMETER_ITEM_DESCRIPTION_DESC);
        map.put(PARAMETER_ALL_MESSAGES_LINK, PARAMETER_ALL_MESSAGES_LINK_DESC);
        map.put(PARAMETER_PUBLISHED_DATE, PARAMETER_PUBLISHED_DATE_DESC);
        map.put(PARAMETER_SORT, PARAMETER_SORT_DESC);
        map.put(PARAMETER_INCLUDE_IF_EMPTY, PARAMETER_INCLUDE_IF_EMPTY_DESC);
        map.put(PARAMETER_DISPLAY_CATEGORIES, PARAMETER_DISPLAY_CATEGORIES_DESC);
        map.put(PARAMETER_ALLOW_MARKUP, PARAMETER_ALLOW_MARKUP_DESC);
        return map;
    }

    public void setContentCache(ContentCache<String, SyndFeed> cache) {
        this.cache = cache;
    }

    public void setLocalFeedFetcher(LocalFeedFetcher localFeedFetcher) {
        this.localFeedFetcher = localFeedFetcher;
    }

}
