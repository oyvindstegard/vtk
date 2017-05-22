/* Copyright (c) 2006, 2008, University of Oslo, Norway
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

package vtk.web.view;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.View;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndEntryImpl;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.feed.synd.SyndFeedImpl;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedOutput;

import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Service;

/**
 * This view renders a feed based on a object in the model of type {@link java.util.Map} with key
 * "feedModel".
 * 
 * <p>
 * <code>feedModel</code> can contain the following data:
 * </p>
 * <ul>
 * <li><code>title</code> - title for the feed to be used in
 * {@link com.sun.syndication.feed.synd.SyndFeed#setTitle}
 * <li><code>description</code> - description for the rssfeed to be used in
 * {@link com.sun.syndication.feed.synd.SyndFeed#setDescription}
 * <li><code>url</code> - url for the feed to be used in
 * {@link com.sun.syndication.feed.synd.SyndFeed#setLink}
 * <li><code>resources</code> - An array of {@link vtk.repository.Resource} to be
 * included in the rssfeed
 * </ul>
 * 
 * <p>
 * Configurable properties:
 * </p>
 * <ul>
 * <li><code>browsingService</code> - The service used for constructing the link to the resource in the list "resources"</li>
 * <li><code>defaultFeedType</code> - The feedType which will be used for the feed if the
 * parameter "format" is not available in the request or this parameter contains an unssupported
 * feedType. Legal values for format in rome 0.8:
 * <ul>
 * <li>rss_0.9</li>
 * <li>rss_0.91</li>
 * <li>rss_0.92</li>
 * <li>rss_0.93</li>
 * <li>rss_0.94</li>
 * <li>rss_1.0</li>
 * <li>rss_2.0</li>
 * <li>atom_0.3</li>
 * <li>atom_1.0</li>
 * </ul>
 * </li>
 * <li><code>charset</code> - The character set used for the feed. Default is utf-8</li>
 * <li><code>useTimestampInIdentifier</code> - Use resource lastModified as anchor in the url for
 * each entry to make Thunderbird believe the entry is modified</li>
 * </ul>
 * 
 */
public class RomeFeedView implements View {

    private static final int RSS_ENTRY_TITLE_MAX_LENGTH = 99;
    
    private ReferenceDataProvider[] referenceDataProviders;

    private static final String FORMAT_PARAMETER_NAME = "format";
    private Service browsingService;

    private String defaultFeedType;
    
    private boolean useTimestampInIdentifier = false;
    
    private String charset = "utf-8";
    
    private static Logger logger = LoggerFactory.getLogger(RomeFeedView.class);

    /**
     * 
     * @see org.springframework.web.servlet.View#render(java.util.Map,
     *      javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @SuppressWarnings("rawtypes")
    public void render(Map model, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        
        if (referenceDataProviders != null) {
            for (ReferenceDataProvider provider: referenceDataProviders) {
                provider.referenceData(model, request);
            }
        }

        Map feedModel = (Map) model.get("feedModel");
        
        if (feedModel == null) {
            return;
        }
        // Generate and write RSS file:
        SyndFeed feed = new SyndFeedImpl();

        String title = (String) feedModel.get("title");
        feed.setTitle(title);

        String description = (String) feedModel.get("description");
        feed.setDescription(description);

        String url = (String) feedModel.get("url");
        feed.setLink(url);
        feed.setUri(url);

        Resource[] resources = (Resource[]) feedModel.get("resources");
        // Created and add list of entries
        // (Each entry is set with a title, link, published date and a description)
        // ( -> Description can be plain text or HTML)
        List<SyndEntry> feedEntries = new ArrayList<>();
        Principal principal = RequestContext.getRequestContext().getPrincipal();

        for (int i = 0; i < resources.length; i++) {
            SyndEntry entry = getSyndEntryForResource(resources[i], principal);
            feedEntries.add(entry);
        }
       
        feed.setFeedType(getFormatFromRequest(request, feed));
        feed.setEncoding(this.charset);

        // Maybe a better implemantation is to set publishedDate for the feed to the lastest of the
        // updated
        // dates for the entries.
        feed.setPublishedDate(Calendar.getInstance().getTime());

        feed.setEntries(feedEntries);

        
        response.setContentType("text/xml; charset=" + feed.getEncoding());
        PrintWriter writer = response.getWriter();

        try {
            SyndFeedOutput output = new SyndFeedOutput();
            output.output(feed, writer);
            writer.close();
        } catch (IOException e) {
            logger.error("IO exception for RSS build", e);
        } catch (FeedException e) {
            logger.error("Error when constructing RSS feed", e);
        } catch (Exception e) {
            logger.error("RSS error: ", e);
        }
    } // end of render()

    private String getFormatFromRequest(HttpServletRequest request, SyndFeed feed) {
        String feedType = request.getParameter(FORMAT_PARAMETER_NAME);
        if (feedType == null || !isSuuportedFeedType(feedType, feed)) {
            feedType = this.defaultFeedType;
        }
        return feedType;
    }
    
    private boolean isSuuportedFeedType(String feedType, SyndFeed feed) {
        return feed.getSupportedFeedTypes().contains(feedType);
    }

    /**
     * Helper method to build a RSS SyndEntry from provided arguments
     * 
     * @param String
     *            title, String url, String publishDate, String doctype, String tilhorighet
     * @return SyndEntry RSS feed with values corresponding to document values
     */
    private SyndEntry getSyndEntryForResource(Resource resource, Principal principal) {
        // RSS entry objects
        SyndEntry entry = new SyndEntryImpl();
        String title = resource.getTitle();
        if (!title.equals("")) {
            // Throws FeedException if title exceeds 100 characters        
            if (title.length() > RSS_ENTRY_TITLE_MAX_LENGTH) {
                String truncationString = "...";
                logger.debug("Title of a feed entry cannot exceed " + RSS_ENTRY_TITLE_MAX_LENGTH + " characters. Title is \'" + title + "\'");
                title = title.substring(0, RSS_ENTRY_TITLE_MAX_LENGTH
                        - truncationString.length())
                        + truncationString;
            }
  
            entry.setTitle(formatTitle(title));
        } else {
            entry.setTitle("Title is missing");
        }
        
        entry.setAuthor(resource.getOwner().getName());
     
        String link = this.browsingService.constructLink(resource, principal);
        entry.setLink(link);

        if (this.useTimestampInIdentifier) {
            long lastModified = resource.getLastModified().getTime();
            entry.setUri(link + "#" + lastModified); 
        } else {
            entry.setUri(link); 
        }
        
        entry.setPublishedDate(resource.getCreationTime());
        //entry.setPublishedDate(resource.getLastModified());
        entry.setUpdatedDate(resource.getLastModified());
        return entry;
    }

    /**
     * Private helper method for setEntry() to format a title which contains multiple, separated
     * elements
     * 
     * @param noticeTitle
     * @return title with any occurences of separators "|" replaced by ": "
     */
    private String formatTitle(String noticeTitle) {
        return noticeTitle.replaceAll("\\|", ": ");
    }

    public void setBrowsingService(Service browsingService) {
        this.browsingService = browsingService;
    }

    /**
     * 
     * @param defaultFeedType
     *            
     */
    public void setDefaultFeedType(String defaultFeedType) {
        this.defaultFeedType = defaultFeedType;
    }

    /**
     * 
     * @param charset
     */
    public void setCharset(String charset) {
        this.charset = charset;
    }

    public void setUseTimestampInIdentifier(boolean useTimestampInIdentifier) {
        this.useTimestampInIdentifier = useTimestampInIdentifier;
    }
    
    public ReferenceDataProvider[] getReferenceDataProviders() {
        return this.referenceDataProviders;
    }

    public void setReferenceDataProviders(ReferenceDataProvider[] referenceDataProviders) {
        this.referenceDataProviders = referenceDataProviders;
    }

    public String getContentType() {
        return null;
    }

}
