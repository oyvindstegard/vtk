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
package vtk.web.display.collection;

import javax.servlet.http.HttpServletRequest;

import org.apache.abdera.model.Feed;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Resource;
import vtk.web.RequestContext;
import vtk.web.display.feed.AtomFeedGenerator;
import vtk.web.search.Listing;
import vtk.web.search.ListingEntry;
import vtk.web.search.SearchComponent;

public class CollectionListingAtomFeedGenerator extends AtomFeedGenerator {

    private SearchComponent searchComponent;

    @Override
    protected void addFeedEntries(HttpServletRequest request, Feed feed,
            Resource feedScope) throws Exception {
		feed.addSimpleExtension("vrtx", "feed-type", "v", "collection-list");

        Listing entryElements = searchComponent.execute(
                RequestContext.getRequestContext().getServletRequest(),
                feedScope, 1, entryCountLimit, 0);

        for (ListingEntry entry : entryElements.getEntries()) {
            addPropertySetAsFeedEntry(request, feed, entry.getPropertySet());
        }

    }

    @Required
    public void setSearchComponent(SearchComponent searchComponent) {
        this.searchComponent = searchComponent;
    }

}
