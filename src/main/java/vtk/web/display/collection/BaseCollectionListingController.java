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

import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;

import freemarker.template.TemplateModelException;
import vtk.edit.editor.ResourceWrapperManager;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.decorating.components.menu.SubFolderMenuProvider;
import vtk.web.service.Service;
import vtk.web.service.URL;
import vtk.web.view.freemarker.MessageLocalizer;

public class BaseCollectionListingController implements ListingController {
    public final static String MODEL_KEY_SEARCH_COMPONENTS = "searchComponents";
    public final static String MODEL_KEY_PAGE = "page";
    public final static String MODEL_KEY_PAGINATION = "pagination";
    public final static String MODEL_KEY_PAGE_THROUGH_URLS = "pageThroughUrls";
    public final static String MODEL_KEY_HIDE_ALTERNATIVE_REP = "hideAlternativeRepresentation";
    public final static String MODEL_KEY_OVERRIDDEN_TITLE = "overriddenTitle";
    public final static String MODEL_KEY_HIDE_NUMBER_OF_COMMENTS = "hideNumberOfComments";
    public final static String MODEL_KEY_FEATURED_ARTICLES = "featuredArticles";

    protected static Log logger = LogFactory.getLog(BaseCollectionListingController.class);

    protected ResourceWrapperManager resourceManager;
    protected int defaultPageLimit = 20;
    protected int collectionDisplayLimit = 1000;
    protected PropertyTypeDefinition pageLimitPropDef;
    protected PropertyTypeDefinition hideNumberOfComments;
    protected String viewName;
    protected Map<String, Service> alternativeRepresentations;
    private boolean includeRequestParametersInAlternativeRepresentation;
    private SubFolderMenuProvider subFolderMenuProvider;

    @Override
    public void runSearch(
            HttpServletRequest request,
            Resource collection,
            Map<String, Object> model,
            int pageLimit
    ) throws Exception {}

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        Principal principal = requestContext.getPrincipal();
        Repository repository = requestContext.getRepository();

        Resource collection = repository.retrieve(token, uri, true);

        Map<String, Object> model = new HashMap<String, Object>();

        Map<String, Object> subfolders = getSubFolderMenu(collection, request);
        if (subfolders != null) {
            int size = (Integer) subfolders.get("size");
            if (size > 0) {
                model.put("subFolderMenu", subfolders);
            }
        }
        model.put("collection", resourceManager.createResourceWrapper(collection));

        int pageLimit = getPageLimit(collection);
        /* Run the actual search (done in subclasses) */
        runSearch(request, collection, model, pageLimit);
 
        if (alternativeRepresentations != null) {
            Set<Object> alt = new HashSet<>();
            for (String contentType : alternativeRepresentations.keySet()) {
                Map<String, Object> m = new HashMap<>();
                Service service = alternativeRepresentations.get(contentType);
                try {
                    URL url = service.constructURL(collection, principal);
                    if (includeRequestParametersInAlternativeRepresentation) {
                        Enumeration<String> requestParameters = request.getParameterNames();
                        while (requestParameters.hasMoreElements()) {
                            String requestParameter = requestParameters.nextElement();
                            String parameterValue = request.getParameter(requestParameter);

                            String urlParameterValue = url.getParameter(requestParameter);
                            if (urlParameterValue == null) {
                                url.addParameter(requestParameter, parameterValue);
                            }
                            else if ("".equals(urlParameterValue.trim())) {
                                url.setParameter(requestParameter, parameterValue);
                            }
                        }
                    }

                    String title = service.getName();

                    org.springframework.web.servlet.support.RequestContext rc = 
                        new org.springframework.web.servlet.support.RequestContext(
                            request);
                    title = rc.getMessage(service.getName(),
                        new Object[] { collection.getTitle() }, service.getName());

                    m.put("title", title);
                    m.put("url", url);
                    m.put("contentType", contentType);

                    alt.add(m);
                }
                catch (Throwable t) {
                    logger.debug("Failed to Link to alternative representation '"
                            + contentType + "' for resource " + collection, t);
                }
            }
            if (pageLimit > 0) {
                model.put("alternativeRepresentations", alt);
            }
        }
        model.put("requestURL", requestContext.getRequestURL());
        return new ModelAndView(viewName, model);
    }

    protected Map<String, Object> getSubFolderMenu(Resource collection, HttpServletRequest request) {

        Map<String, Object> result = subFolderMenuProvider.getSubfolderMenuWithGeneratedResultSets(collection, request);
        if (result == null) {
            return null;
        }
        HttpServletRequest servletRequest = request;
        org.springframework.web.servlet.support.RequestContext springRequestContext = new org.springframework.web.servlet.support.RequestContext(
                servletRequest);

        try {
            String standardCollectionName = new MessageLocalizer("viewCollectionListing.subareas", "Subareas", null,
                    springRequestContext).get(null).toString();
            result.put("title", standardCollectionName);
        } catch (TemplateModelException e) {
            e.printStackTrace();
        }
        return result;
    }

    protected URL createURL(HttpServletRequest request, String... removeableParams) {
        URL url = URL.create(request);
        for (String removableParam : removeableParams) {
            url.removeParameter(removableParam);
        }
        return url;
    }

    protected int getPageLimit(Resource collection) {
        int pageLimit = defaultPageLimit;
        Property pageLimitProp = collection.getProperty(pageLimitPropDef);
        if (pageLimitProp != null) {
            pageLimit = pageLimitProp.getIntValue();
        }
        return pageLimit;
    }

    protected boolean getHideNumberOfComments(Resource collection) {
        Property p = collection.getProperty(hideNumberOfComments);
        if (p == null) {
            return false;
        }
        return p.getBooleanValue();
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

    @Required
    public void setResourceManager(ResourceWrapperManager resourceManager) {
        this.resourceManager = resourceManager;
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

    public void setIncludeRequestParametersInAlternativeRepresentation(
            boolean includeRequestParametersInAlternativeRepresentation) {
        this.includeRequestParametersInAlternativeRepresentation = includeRequestParametersInAlternativeRepresentation;
    }

    public void setHideNumberOfComments(PropertyTypeDefinition hideNumberOfComments) {
        this.hideNumberOfComments = hideNumberOfComments;
    }

    public void setCollectionDisplayLimit(int collectionDisplayLimit) {
        this.collectionDisplayLimit = collectionDisplayLimit;
    }

    public void setSubFolderMenuProvider(SubFolderMenuProvider subFolderMenuProvider) {
        this.subFolderMenuProvider = subFolderMenuProvider;
    }

}
