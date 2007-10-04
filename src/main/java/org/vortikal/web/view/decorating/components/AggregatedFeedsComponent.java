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
package org.vortikal.web.view.decorating.components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;

import org.vortikal.util.cache.ContentCache;
import org.vortikal.web.view.decorating.DecoratorRequest;
import org.vortikal.web.view.decorating.DecoratorResponse;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.feed.synd.SyndFeedImpl;

public class AggregatedFeedsComponent extends ViewRenderingDecoratorComponent {

    private ContentCache<String, SyndFeed> cache;
    private LocalFeedFetcher localFeedFetcher;


    public void setContentCache(ContentCache<String, SyndFeed> cache) {
        this.cache = cache;
    }
    
    
    @Override
    protected void processModel(Map<Object, Object> model,
            DecoratorRequest request, DecoratorResponse response)
            throws Exception {
        super.processModel(model, request, response);

        Map<String, Object> conf = new HashMap<String, Object>();
        

        String urls = request.getStringParameter(Parameter.URLS.getId());
        if (urls == null) {
            throw new DecoratorComponentException(
                "Component parameter 'urls' is required");
        }

        String displayChannelString = request.getStringParameter(Parameter.DISPLAY_CHANNEL.getId()); 
        if (displayChannelString == null || !"false".equals(displayChannelString)) {
            conf.put("displayChannel", true);
        }
        
        String itemDescriptionString = request.getStringParameter(Parameter.ITEM_DESCRIPTION.getId()); 
        if (itemDescriptionString != null && "true".equals(itemDescriptionString)) {
            conf.put("itemDescription", true);
        }

        conf.put("maxMsgs", 10);
        String maxMsgsString = request.getStringParameter(Parameter.MAX_MESSAGES.getId());
        if (maxMsgsString != null) {
            try {
                int tmpInt = Integer.parseInt(maxMsgsString);
                if (tmpInt > 0) {
                    conf.put("maxMsgs", tmpInt);
                }
            } catch (Exception e) { }
        }

        String publishedDateString = request.getStringParameter(Parameter.PUBLISHED_DATE.getId());
        if ("none".equals(publishedDateString)) {
            conf.put("publishedDate", null);
        } else if ("date".equals(publishedDateString)) {
            conf.put("publishedDate", "short");
        } else {
            conf.put("publishedDate", "long");
        }
        


        String sortString = request.getStringParameter(Parameter.SORT.getId());
        if ("item-title".equals(sortString)) {
            conf.put("sortByTitle", true);
        }
        
        boolean includeIfEmpty = true;
        String includeIfEmptyParam = request.getStringParameter(Parameter.INCLUDE_IF_EMPTY.getId());
        if ("false".equalsIgnoreCase(includeIfEmptyParam)) {
            includeIfEmpty = false;
        }
        conf.put("includeIfEmpty", includeIfEmpty);

        SyndFeed feed = new SyndFeedImpl();
        feed.setTitle("Aggregated Feed");
        feed.setDescription("Vortex Aggregated Feed");
        feed.setAuthor("Vortex");
        feed.setLink("http://www.uio.no");

        List<SyndEntry> entries = new ArrayList<SyndEntry>();
        feed.setEntries(entries);
        
        Map<SyndEntry, SyndFeed> feedMapping = new HashMap<SyndEntry, SyndFeed>();
        Object displayChannel = conf.get("displayChannel");
        if (displayChannel != null && (Boolean)displayChannel) {
            model.put("feedMapping", new FeedMapping(feedMapping));
        }
        
        String[] urlArray = urls.split(",");

        for (String url : urlArray) {
            url = url.trim();

            SyndFeed tmpFeed = null;
            if (!url.startsWith("/")) {
                tmpFeed = this.cache.get(url);
            } else {
                tmpFeed = this.localFeedFetcher.getFeed(url, request);
            }
            entries.addAll(tmpFeed.getEntries());

            if (displayChannel != null && (Boolean)displayChannel) {
                for (SyndEntry entry : (List<SyndEntry>)tmpFeed.getEntries()) {
                    feedMapping.put(entry, tmpFeed);
                }
            }
        }

        Collections.sort(entries, new Comparator<SyndEntry>() {
            public int compare(SyndEntry arg0, SyndEntry arg1) {
                return arg1.getPublishedDate().compareTo(arg0.getPublishedDate());
            }});

        model.put("feed", feed);
        model.put("conf", conf);
    }

    protected String getDescriptionInternal() {
        return "Inserts a feed (RSS, Atom) component on the page";
    }


    private enum Parameter {
        URLS ("urls", "Comma separated list of feed urls."),
        DISPLAY_CHANNEL ("display-channel", "Defaults to 'true', displaying the items source feed"), 
        SORT ("sort", "Default sorted by published date. Set to 'item-title' to sort by this instead."),
        PUBLISHED_DATE ("published-date", "How to display published date, defaults to date and time. Set to 'date' to only display the date, or 'none' to not show the date"),
        MAX_MESSAGES ("max-messages", "The max number of messages to display, defaults to 10"),
        ITEM_DESCRIPTION ("item-description", "Must be set to 'true' to show item descriptions"),
        INCLUDE_IF_EMPTY ("include-if-empty", "Set to 'false' if you don't want to display empty feeds. Default is 'true'.");

        
        private final String id;
        private final String desc;

        private Parameter(String id, String desc) {
            this.id = id;
            this.desc = desc;
        }

        public String getId() {
            return id;
        }

        public String getDesc() {
            return desc;
        }
    }
    

    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(Parameter.URLS.getId(), Parameter.URLS.getDesc());
        map.put(Parameter.MAX_MESSAGES.getId(), Parameter.MAX_MESSAGES.getDesc());
        map.put(Parameter.DISPLAY_CHANNEL.getId(), Parameter.DISPLAY_CHANNEL.getDesc());
        map.put(Parameter.ITEM_DESCRIPTION.getId(), Parameter.ITEM_DESCRIPTION.getDesc());
        map.put(Parameter.PUBLISHED_DATE.getId(), Parameter.PUBLISHED_DATE.getDesc());
        map.put(Parameter.SORT.getId(), Parameter.SORT.getDesc());
        map.put(Parameter.INCLUDE_IF_EMPTY.getId(), Parameter.INCLUDE_IF_EMPTY.getDesc());
        return map;
    }


    public void setServletContext(ServletContext servletContext) {
        this.localFeedFetcher = new LocalFeedFetcher(servletContext);
    }
    
    
    public class FeedMapping {
        Map<SyndEntry, SyndFeed> feedMapping = new HashMap<SyndEntry, SyndFeed>();

        public FeedMapping(Map<SyndEntry, SyndFeed> feedMapping) {
            super();
            this.feedMapping = feedMapping;
        }
      
        public String getTitle(SyndEntry entry) {
            return this.feedMapping.get(entry).getTitle();
        }

        public String getUrl(SyndEntry entry) {
            return this.feedMapping.get(entry).getLink();
        }
    } 
}
