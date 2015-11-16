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
package vtk.web.display.collection.article;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Property;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.RequestContext;
import vtk.web.display.collection.BaseCollectionListingController;
import vtk.web.display.listing.ListingPager;
import vtk.web.display.listing.ListingPagingLink;
import vtk.web.search.Listing;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class ArticleListingController extends BaseCollectionListingController {

    private ArticleListingSearcher searcher;
    private final String defaultListingView = "regular";
    private PropertyTypeDefinition listingViewPropDef;

    @Override
    public void runSearch(HttpServletRequest request, Resource collection, Map<String, Object> model, int pageLimit)
            throws Exception {

        int featuredArticlesPage = ListingPager.getPage(request, ListingPager.UPCOMING_PAGE_PARAM);
        int defaultArticlesPage = ListingPager.getPage(request, ListingPager.PREVIOUS_PAGE_PARAM);
        int totalHits = 0;
        int featuredArticlesTotalHits = 0;
        int userDisplayPage = featuredArticlesPage;

        boolean atLeastOneFeaturedArticle = false;
        Listing featuredArticles = null;
        if (collection.getProperty(searcher.getFeaturedArticlesPropDef()) != null) {
            featuredArticles = this.searcher.getFeaturedArticles(request, collection, 1, 1, 0);
            atLeastOneFeaturedArticle = featuredArticles != null && featuredArticles.size() > 0;
        }

        List<Listing> results = new ArrayList<Listing>();
        if (request.getParameter(ListingPager.PREVIOUS_PAGE_PARAM) == null) {
            // Search featured articles
            featuredArticles = this.searcher.getFeaturedArticles(request, collection, featuredArticlesPage, pageLimit,
                    0);
            if (featuredArticles != null) {
                int hits = featuredArticles.getTotalHits();
                totalHits += hits;
                featuredArticlesTotalHits = hits;
                if (featuredArticles.size() > 0) {
                    results.add(featuredArticles);
                }
            }
        } else {
            featuredArticles = this.searcher.getFeaturedArticles(request, collection, featuredArticlesPage, 0, 0);
            if (featuredArticles != null) {
                int hits = featuredArticles.getTotalHits();
                totalHits += hits;
                featuredArticlesTotalHits = hits;
                featuredArticles = null;
            }
        }

        if (featuredArticles == null || featuredArticles.size() == 0) {
            // Searching only in default articles
            int upcomingOffset = getIntParameter(request, ListingPager.PREV_BASE_OFFSET_PARAM, 0);
            if (upcomingOffset > pageLimit)
                upcomingOffset = 0;
            Listing defaultArticles = this.searcher.getArticles(request, collection, defaultArticlesPage, pageLimit,
                    upcomingOffset);
            totalHits += defaultArticles.getTotalHits();
            if (defaultArticles.size() > 0) {
                results.add(defaultArticles);
            }
            if (atLeastOneFeaturedArticle) {
                userDisplayPage += defaultArticlesPage;
            } else {
                userDisplayPage = defaultArticlesPage;
            }
        } else if (featuredArticles.size() < pageLimit) {
            // Fill up the rest of the page with default articles
            int upcomingOffset = pageLimit - featuredArticles.size();
            Listing defaultArticles = this.searcher.getArticles(request, collection, 1, upcomingOffset, 0);
            totalHits += defaultArticles.getTotalHits();
            if (defaultArticles.size() > 0) {
                results.add(defaultArticles);
            }
        } else {
            Listing defaultArticles = this.searcher.getArticles(request, collection, defaultArticlesPage, 0, 0);
            totalHits += defaultArticles.getTotalHits();
            defaultArticles = null;
        }
        Service service = RequestContext.getRequestContext().getService();
        URL baseURL = service.constructURL(RequestContext.getRequestContext().getResourceURI());
        
        Optional<ListingPager.Pagination> pagination = 
                ListingPager.pagination(totalHits, pageLimit, 
                        featuredArticlesTotalHits, baseURL, true, userDisplayPage);
        if (pagination.isPresent()) {
            List<ListingPagingLink> urls = pagination.get().pageThroughLinks();
            model.put(MODEL_KEY_PAGE_THROUGH_URLS, urls);
            model.put(MODEL_KEY_PAGINATION, pagination.get());
        }
        model.put(MODEL_KEY_SEARCH_COMPONENTS, results);
        model.put(MODEL_KEY_PAGE, userDisplayPage);

        model.put(MODEL_KEY_HIDE_NUMBER_OF_COMMENTS, getHideNumberOfComments(collection));
        model.put("listingView", getListingView(collection));
    }
    
    private String getListingView(Resource collection) {
        String listingView = this.defaultListingView;
        Property listingViewProp = collection.getProperty(this.listingViewPropDef);
        if (listingViewProp != null) {
            listingView = listingViewProp.getFormattedValue();
        }
        return listingView;
    }

    @Required
    public void setSearcher(ArticleListingSearcher searcher) {
        this.searcher = searcher;
    }

    @Required
    public void setListingViewPropDef(PropertyTypeDefinition listingViewPropDef) {
        this.listingViewPropDef = listingViewPropDef;
    }

}
