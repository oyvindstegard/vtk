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
package org.vortikal.web.controller.feed;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.abdera.model.Feed;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.Path;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.Resource;
import org.vortikal.web.RequestContext;
import org.vortikal.web.controller.article.ArticleListingSearcher;
import org.vortikal.web.search.Listing;

public class ArticleListingAsFeedController extends AtomFeedController {
	
    private ArticleListingSearcher searcher;

	@Override
	protected Feed createFeed(HttpServletRequest request, HttpServletResponse response, String token) throws Exception {
    	
		Path uri = RequestContext.getRequestContext().getResourceURI();
        Resource collection = this.repository.retrieve(token, uri, true);
        
        Feed feed = populateFeed(collection, collection.getTitle());
        
        List<Listing> results = new ArrayList<Listing>();
        Listing featuredArticles = this.searcher.getFeaturedArticles(request, collection, 1, 25, 0);
        if (featuredArticles.size() > 0) {
        	results.add(featuredArticles);
        }
        
        Listing articles = this.searcher.getArticles(request, collection, 1, 25, 0);
        if (articles.size() > 0) {
        	if (featuredArticles.size() > 0) {
        		this.searcher.removeFeaturedArticlesFromDefault(featuredArticles.getFiles(), articles.getFiles());
        	}
        	results.add(articles);
        }

        for (Listing searchResult : results) {
        	for (PropertySet result : searchResult.getFiles()) {
                populateEntry(token, result, feed.addEntry());
            }
        }

    	return feed;
	}

	@Required
	public void setSearcher(ArticleListingSearcher searcher) {
		this.searcher = searcher;
	}

}
