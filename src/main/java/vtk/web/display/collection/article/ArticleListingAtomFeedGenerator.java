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

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.abdera.model.Feed;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Property;
import vtk.repository.PropertySet;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.display.feed.AtomFeedGenerator;
import vtk.web.search.Listing;

public class ArticleListingAtomFeedGenerator extends AtomFeedGenerator {

    private ArticleListingSearcher searcher;
    private PropertyTypeDefinition overridePublishDatePropDef;

    @Override
    protected void addFeedEntries(HttpServletRequest request, Feed feed, Resource feedScope) throws Exception {

        Listing featuredArticles = searcher.getFeaturedArticles(request, feedScope, 1, entryCountLimit, 0);
        
        if (featuredArticles != null && featuredArticles.size() > 0) {
            Map<String, Object> extensions = new HashMap<>();
            extensions.put("featured-article", true);
            for (PropertySet propSet: featuredArticles.getPropertySets()) {
                addPropertySetAsFeedEntry(request, feed, propSet, extensions);
            }
        }        

        Listing articles = searcher.getArticles(request, feedScope, 1, entryCountLimit, 0);
        if (articles.size() > 0) {

            for (PropertySet propSet: articles.getPropertySets()) {
                addPropertySetAsFeedEntry(request, feed, propSet);
            }
        }
    }

    @Override
    protected Property getPublishDate(PropertySet resource) {
        Property overridePublishDateProp = resource.getProperty(overridePublishDatePropDef);
        if (overridePublishDateProp != null) {
            return overridePublishDateProp;
        }
        return getDefaultPublishDate(resource);
    }

    @Required
    public void setSearcher(ArticleListingSearcher searcher) {
        this.searcher = searcher;
    }

    @Required
    public void setOverridePublishDatePropDef(PropertyTypeDefinition overridePublishDatePropDef) {
        this.overridePublishDatePropDef = overridePublishDatePropDef;
    }

}
