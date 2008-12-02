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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.vortikal.edit.editor.ResourceWrapperManager;
import org.vortikal.repository.Path;
import org.vortikal.repository.Property;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.security.Principal;
import org.vortikal.security.SecurityContext;
import org.vortikal.util.repository.ResourcePropertyComparator;
import org.vortikal.web.RequestContext;
import org.vortikal.web.service.Service;
import org.vortikal.web.service.URL;

public abstract class AbstractCollectionListingController implements Controller {
	
    protected Repository repository;
    protected ResourceWrapperManager resourceManager;    
    protected PropertyTypeDefinition hiddenPropDef;
    protected int defaultPageLimit = 20;
    protected PropertyTypeDefinition pageLimitPropDef;
    protected PropertyTypeDefinition hideNumberOfComments;
    protected List<PropertyTypeDefinition> sortPropDefs;
    protected String viewName;
    protected Map<String, Service> alternativeRepresentations;
    
    protected static final String UPCOMING_PAGE_PARAM = "page";
    protected static final String PREVIOUS_PAGE_PARAM = "p-page";
    protected static final String PREV_BASE_OFFSET_PARAM = "p-offset";
    
	public ModelAndView handleRequest(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		
        Path uri = RequestContext.getRequestContext().getResourceURI();
        SecurityContext securityContext = SecurityContext.getSecurityContext(); 
        String token = securityContext.getToken();
        Principal principal = securityContext.getPrincipal();
        Resource collection = this.repository.retrieve(token, uri, true);

        Resource[] children = this.repository.listChildren(token, uri, true);
        List<Resource> subCollections = new ArrayList<Resource>();
        for (Resource r : children) {
            if (r.isCollection() && r.getProperty(this.hiddenPropDef) == null) {
                subCollections.add(r);
            }
        }

        Locale locale = new org.springframework.web.servlet.support.RequestContext(request).getLocale();
        Collections.sort(subCollections, new ResourcePropertyComparator(this.sortPropDefs, false, locale));
        
        Map<String, Object> model = new HashMap<String, Object>();
        model.put("collection", this.resourceManager.createResourceWrapper(collection));
        model.put("subCollections", subCollections);
        
        /* Run the actual search (done in subclasses) */
        runSearch(request, collection, model);
        
        Set<Object> alt = new HashSet<Object>();
        for (String contentType: this.alternativeRepresentations.keySet()) {
            try {
                Map<String, Object> m = new HashMap<String, Object>();
                Service service = this.alternativeRepresentations.get(contentType);
                URL url = service.constructURL(collection, principal);
                String title = service.getName();
                org.springframework.web.servlet.support.RequestContext rc = 
                new org.springframework.web.servlet.support.RequestContext(request);
                title = rc.getMessage(service.getName(), new Object[]{collection.getTitle()}, service.getName());
                
                m.put("title", title);
                m.put("url", url);
                m.put("contentType", contentType);
  
                
                alt.add(m);
            } catch (Throwable t) { }
        }
        model.put("alternativeRepresentations", alt);
        
		return new ModelAndView(this.viewName, model);
	}
    
	
	protected abstract void runSearch(HttpServletRequest request, Resource collection,
			Map<String, Object> model) throws Exception;
	
	
	protected int getPageLimit(Resource collection) {
        int pageLimit = this.defaultPageLimit;
        Property pageLimitProp = collection.getProperty(this.pageLimitPropDef);
        if (pageLimitProp != null) {
            pageLimit = pageLimitProp.getIntValue();
        }
        return pageLimit;
	}
	
	protected boolean getHideNumberOfComments(Resource collection) {
		Property p = collection.getProperty(this.hideNumberOfComments);
		if(p == null){
			return false;
		}
		return p.getBooleanValue();
	}
	
    protected int getPage(HttpServletRequest request, String parameter) {
        int page = 0;
        String pageParam = request.getParameter(parameter);
        if (StringUtils.isNotBlank(pageParam)) {
            try {
                page = Integer.parseInt(pageParam);
                if (page < 1) {
                    page = 1;
                }
            } catch (Throwable t) { }
        }

        if (page == 0) {
            page = 1;
        }
        return page;
    }
    
    protected int getIntParameter(HttpServletRequest request, String name, int defaultValue) {
        String param = request.getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(param);
        } catch (Throwable t) { 
            return defaultValue;
        }
    }

    protected void cleanURL(URL url) {
        if (url != null) {
            url.setCollection(true);
            String param = url.getParameter(UPCOMING_PAGE_PARAM);
            if ("1".equals(param)) {
                url.removeParameter(UPCOMING_PAGE_PARAM);
            }
            param = url.getParameter(PREV_BASE_OFFSET_PARAM);
            if ("0".equals(param)) {
                url.removeParameter(PREV_BASE_OFFSET_PARAM);
            }
        }
    }
	
    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    @Required
    public void setResourceManager(ResourceWrapperManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @Required
    public void setHiddenPropDef(PropertyTypeDefinition hiddenPropDef) { 
        this.hiddenPropDef = hiddenPropDef;
    }
    
    @Required 
    public void setSortPropDefs(List<PropertyTypeDefinition> sortPropDefs) {
        this.sortPropDefs = sortPropDefs;
    }

    @Required
    public void setPageLimitPropDef(PropertyTypeDefinition pageLimitPropDef) {
        this.pageLimitPropDef = pageLimitPropDef;
    }

    public void setDefaultPageLimit(int defaultPageLimit) {
        if (defaultPageLimit <= 0)
            throw new IllegalArgumentException("Argument must be a positive integer");
        this.defaultPageLimit = defaultPageLimit;
    }

    @Required
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }
    
    public void setAlternativeRepresentations(Map<String, Service> alternativeRepresentations) {
        this.alternativeRepresentations = alternativeRepresentations;
    }

	public void setHideNumberOfComments(PropertyTypeDefinition hideNumberOfComments) {
		this.hideNumberOfComments = hideNumberOfComments;
	}
}
