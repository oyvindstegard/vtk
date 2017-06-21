/* Copyright (c) 2009, University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.List;

import com.sun.syndication.feed.synd.SyndEntry;

import vtk.repository.AuthorizationException;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceNotFoundException;
import vtk.security.AuthenticationException;
import vtk.text.html.HtmlContent;
import vtk.text.html.HtmlElement;
import vtk.text.html.HtmlFragment;
import vtk.text.html.HtmlPage;
import vtk.text.html.HtmlPageFilter;
import vtk.text.html.HtmlPageParser;
import vtk.text.html.HtmlUtil;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.service.URL;

public abstract class AbstractFeedComponent extends ViewRenderingDecoratorComponent {

    protected HtmlPageFilter safeHtmlFilter;

    protected static final String PARAMETER_MAX_MESSAGES = "max-messages";
    protected static final String PARAMETER_MAX_MESSAGES_DESC = "The max number of messages to display, defaults to 10";

    protected static final String PARAMETER_OFFSET = "offset";
    protected static final String PARAMETER_OFFSET_DESC = "The position in the message list to display messages from. Default is 0";
    
    protected static final String PARAMETER_FEED_TITLE = "feed-title";
    protected static final String PARAMETER_FEED_TITLE_DESC = "Deprecated (use 'diplay-feed-title' instead). Kept to avoid breaking existing component references. (Set to 'false' if you don't want to show feed title)";

    protected static final String PARAMETER_FEED_DESCRIPTION = "feed-description";
    protected static final String PARAMETER_FEED_DESCRIPTION_DESC = "Must be set to 'true' to show feed description";

    protected static final String PARAMETER_ITEM_DESCRIPTION = "item-description";
    protected static final String PARAMETER_ITEM_DESCRIPTION_DESC = "Must be set to 'true' to show item descriptions";

    protected static final String PARAMETER_ITEM_PICTURE = "item-picture";
    protected static final String PARAMETER_ITEM_PICTURE_DESC = "Must be set to 'true' to show item picture";

    protected static final String PARAMETER_IF_EMPTY_MESSAGE = "if-empty-message";
    protected static final String PARAMETER_IF_EMPTY_MESSAGE_DESC = "Message to be displayed if feed is empty";

    protected static final String PARAMETER_FEED_ELEMENT_ORDER = "element-order";
    protected static final String PARAMETER_FEED_ELEMENT_ORDER_DESC = "The order in which the elementes are listed";
    
    protected static final String PARAMETER_SORT = "sort";
    protected static final String PARAMETER_SORT_DESC = "Default sorted by published date. Set to 'item-title' to sort by this instead. "
            + "You can control the direction of the sorting by using the keywords 'asc' or 'desc'. "
            + "Usage examples: sort=[asc], sort=[item-title desc], sort=[published-date asc], etc. "
            + "The default is descending direction (newest first) for published date and ascending when sorting by 'item-title'.";

    protected static final String PARAMETER_PUBLISHED_DATE = "published-date";
    protected static final String PARAMETER_PUBLISHED_DATE_DESC = "How to display published date, defaults to date and time. Set to 'date' to only display the date, or 'none' to not show the date";

    protected static final String PARAMETER_INCLUDE_IF_EMPTY = "include-if-empty";
    protected static final String PARAMETER_INCLUDE_IF_EMPTY_DESC = "Set to 'false' if you don't want to display empty feeds. Default is 'true'.";

    protected static final String PARAMETER_DISPLAY_CATEGORIES = "display-categories";
    protected static final String PARAMETER_DISPLAY_CATEGORIES_DESC = "Set to 'true' if feed elements should display contents of category field.";

    protected static final String PARAMETER_ALLOW_MARKUP = "allow-markup";
    protected static final String PARAMETER_ALLOW_MARKUP_DESC = "Set to 'true' to include span elements and class attributes";

    
    private HtmlPageParser parser = new HtmlPageParser();
    private List<String> defaultElementOrder;

    /**
     * Retrieves the resource corresponding to a local feed for authorization
     * purposes
     */
    protected Resource retrieveLocalResource(URL feedURL) throws ResourceNotFoundException,
    AuthorizationException, AuthenticationException, Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.isViewUnauthenticated() ? null : requestContext.getSecurityToken(); // VTK-2460
        return repository.retrieve(token, feedURL.getPath(), true);
    }

    boolean parameterHasValue(String param, String includeParamValue, DecoratorRequest request) {
        String itemDescriptionString = request.getStringParameter(param);
        if (itemDescriptionString != null && includeParamValue.equalsIgnoreCase(itemDescriptionString)) {
            return true;
        }
        return false;
    }

    protected HtmlFragment filterEntry(SyndEntry entry, HtmlPageFilter filter) throws Exception {
        String htmlFragment = null;
        if (entry.getDescription() == null) {
            return null;
        }
        if (entry.getDescription() != null) {
            htmlFragment = entry.getDescription().getValue();
        }
        HtmlFragment fragment = this.parser.parseFragment(htmlFragment);
        fragment.filter(filter);
        return fragment;
    }

    protected HtmlElement removeImage(HtmlFragment fragment) {
        RemoveImageFilter filter = new RemoveImageFilter();
        fragment.filter(filter);
        return filter.getImageElement();
    }

    private static class RemoveImageFilter implements HtmlPageFilter {

        private HtmlElement image = null;

        @Override
        public boolean match(HtmlPage page) {
            return true;
        }

        @Override
        public NodeResult filter(HtmlContent node) {
            if (node instanceof HtmlElement) {

                HtmlElement elem = (HtmlElement) node;
                if (elem.getName().equals("img")) {
                    if (image == null)
                        image = elem;

                    return NodeResult.exclude;

                }

            }
            return NodeResult.keep;
        }

        public HtmlElement getImageElement() {
            return image;
        }
    }

    protected HtmlFragment getDescription(SyndEntry entry, URL baseURL, URL requestURL, boolean filter) throws Exception {
        if (entry.getDescription() == null) {
            return null;
        }

        String value = null;
        HtmlFragment html;
        
        if (filter) {
            html = filterEntry(entry, safeHtmlFilter);
            value = html.getStringRepresentation();
        } else {
            value = entry.getDescription().getValue();
        }
        
        if (value == null) {
            return null;
        }
        
        return HtmlUtil.linkResolveFilter(value, baseURL, requestURL, false);
    }

    protected List<String> getElementOrder(String param, DecoratorRequest request) {
        List<String> resultOrder = new ArrayList<>();

        String[] order = null;
        try {
            order = request.getStringParameter(param).split(",");
        } catch (Exception e) {
        }

        if (order == null) {
            return getDefaultElementOrder();
        }

        // check and add
        for (int i = 0; i < order.length; i++) {
            if (order[i] != null && !getDefaultElementOrder().contains(order[i].trim())) {
                throw new DecoratorComponentException("Illigal element '" + order[i] + "' in '" + param + "'");
            }
            if (order[i] != null) {
                resultOrder.add(order[i].trim());
            }
        }
        return resultOrder;
    }

    public void setDefaultElementOrder(List<String> defaultElementOrder) {
        this.defaultElementOrder = defaultElementOrder;
    }

    public List<String> getDefaultElementOrder() {
        return defaultElementOrder;
    }

    public void setSafeHtmlFilter(HtmlPageFilter safeHtmlFilter) {
        this.safeHtmlFilter = safeHtmlFilter;
    }

}
