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
package org.vortikal.web.controller;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.vortikal.repository.Path;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.security.SecurityContext;
import org.vortikal.web.RequestContext;
import org.vortikal.web.search.Listing;
import org.vortikal.web.search.SearchComponent;
import org.vortikal.web.service.Service;
import org.vortikal.web.service.URL;
import org.vortikal.web.tags.RepositoryTagElementsDataProvider;
import org.vortikal.web.tags.TagElement;

/**
 * 
 */
public class TagsController implements Controller {

    private boolean defaultRecursive = true;
    private Repository repository;
    private int defaultPageLimit = 20;
    private String viewName;
    private SearchComponent searchComponent;
    private Map<String, Service> alternativeRepresentations;

    private RepositoryTagElementsDataProvider tagElementsProvider;
	private String defaultURLPattern;

    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        SecurityContext securityContext = SecurityContext.getSecurityContext();
        String token = securityContext.getToken();

        Resource scope = getScope(token, request);
        Map<String, Object> model = new HashMap<String, Object>();
        String tag = request.getParameter("tag");

        
        /* List all known tags for the current collection */
        if (tag == null || tag.trim().equals("")) {
        	 Path scopeUri = RequestContext.getRequestContext().getCurrentCollection();
        	 
        	 List<TagElement> tagElements = 
        		 tagElementsProvider.getTagElements(scopeUri, token, 1,
        				 1, Integer.MAX_VALUE, 1, defaultURLPattern); 
        	 model.put("isRoot", scopeUri.isRoot()); 
        	 model.put("tagElements", tagElements);
        	 model.put("uriName", scopeUri.getName());
                 
                 
            return new ModelAndView(this.viewName, model);
        }
        model.put("scope", scope);
        model.put("tag", tag);
        
        
        // Setting the default page limit
        int pageLimit = this.defaultPageLimit;

        PageInfo pageInfo = new PageInfo(request, pageLimit);
        int page = pageInfo.getPage();
        int limit = pageInfo.getLimit();
        int offset = pageInfo.getOffset();

        Listing listing = this.searchComponent.execute(request, scope, page, limit, 0, defaultRecursive);
        model.put("listing", listing);

        // Check previous result (by redoing the previous search),
        // to see if we need to adjust the offset.
        // XXX: is there a better way?
        if (listing.getFiles().size() == 0 && offset > 0) {
            Listing prevListing = this.searchComponent.execute(request, scope, page - 1, limit, 0, defaultRecursive);
            if (prevListing.getFiles().size() > 0 && !prevListing.hasMoreResults()) {
                offset -= prevListing.getFiles().size();
            }
        }

        // We have more results to display for this listing
        // Only include enough results to fill the page:
        if (!listing.hasMoreResults() && listing.getFiles().size() > 0) {
            limit -= listing.getFiles().size();
        }

        model.put("page", page);

        URL nextURL = null;
        URL prevURL = null;
        if (listing.hasContent()) {
            if (listing.hasMoreResults()) {
                nextURL = URL.create(request);
                nextURL.setParameter("page", String.valueOf(page + 1));
            }
            if (page > 1) {
                prevURL = URL.create(request);
                if (page == 1) {
                    prevURL.removeParameter("page");
                } else {
                    prevURL.setParameter("page", String.valueOf(page - 1));
                }
            }
        }

        model.put("nextURL", nextURL);
        model.put("prevURL", prevURL);

        Set<Object> alt = new HashSet<Object>();
        for (String contentType : this.alternativeRepresentations.keySet()) {
            try {
                Map<String, Object> m = new HashMap<String, Object>();
                Service service = this.alternativeRepresentations.get(contentType);
                Map<String, String> parameters = new HashMap<String, String>();
                parameters.put("tag", tag);
                URL url = service.constructURL(scope.getURI(), parameters);
                String title = service.getName();
                org.springframework.web.servlet.support.RequestContext rc = new org.springframework.web.servlet.support.RequestContext(
                        request);
                title = rc.getMessage(service.getName(), new Object[] { scope.getTitle() }, service.getName());

                m.put("title", title);
                m.put("url", url);
                m.put("contentType", contentType);

                alt.add(m);
            } catch (Throwable t) {
            }
        }
        model.put("alternativeRepresentations", alt);
        return new ModelAndView(this.viewName, model);
    }


    protected Resource getScope(String token, HttpServletRequest request) throws Exception {
        String scopeFromRequest = request.getParameter("scope");
        if (scopeFromRequest == null || scopeFromRequest.equals("")) {
            return this.repository.retrieve(token, Path.ROOT, true);
        }
        if (".".equals(scopeFromRequest)) {
            Path currentCollection = RequestContext.getRequestContext().getCurrentCollection();
            return this.repository.retrieve(token, currentCollection, true);
        }
        if (scopeFromRequest.startsWith("/")) {
            Resource scopedResource = this.repository.retrieve(token, Path.fromString(scopeFromRequest), true);
            if (!scopedResource.isCollection()) {
                throw new IllegalArgumentException("scope must be a collection");
            }
            return scopedResource;
        } 
        
        throw new IllegalArgumentException("Scope must be empty, '.' or be a valid collection");


    }


    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }


    public void setDefaultPageLimit(int defaultPageLimit) {
        if (defaultPageLimit <= 0)
            throw new IllegalArgumentException("Argument must be a positive integer");
        this.defaultPageLimit = defaultPageLimit;
    }


    @Required
    public void setSearchComponent(SearchComponent searchComponent) {
        this.searchComponent = searchComponent;
    }


    @Required
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }


    public void setAlternativeRepresentations(Map<String, Service> alternativeRepresentations) {
        this.alternativeRepresentations = alternativeRepresentations;
    }
    
    public void setTagElementsProvider(
			RepositoryTagElementsDataProvider tagElementsProvider) {
		this.tagElementsProvider = tagElementsProvider;
	}

	public void setDefaultURLPattern(String defaultURLPattern) {
		this.defaultURLPattern = defaultURLPattern;
	}

}
