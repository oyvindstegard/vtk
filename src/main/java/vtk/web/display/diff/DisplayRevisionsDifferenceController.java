/* Copyright (c) 2014, University of Oslo, Norway
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
package vtk.web.display.diff;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.mvc.ParameterizableViewController;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.Revision;
import vtk.security.Principal.Type;
import vtk.security.PrincipalFactory;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;
import vtk.web.servlet.BufferedResponse;
import vtk.web.servlet.ConfigurableRequestWrapper;
import vtk.web.servlet.VTKServlet;

/*
 * Visually display the differences between two revisions of a document,
 * by comparing and marking up the underlying HTML.
 * 
 * Parameter: The request argument 'revision' should contain a comma separated pair of revision names.
 * Differences will then be calculated from the first one to the second one.
 * Revisions names may be anything (not including a comma), and is not constrained to integers.
 * If just a single revision name is present, it must be an integer value (n). Differences 
 * will then be calculated for revisions 'n-1,n'.
 * 
 * NB! The current implementation will only return the inner HTMl of the body tag. Outer HTML is discarded.
 * NB! No decorating is performed on the result. 
 */
public class DisplayRevisionsDifferenceController extends ParameterizableViewController implements Controller, InitializingBean {

    private static Logger logger = LoggerFactory.getLogger(DisplayRevisionsDifferenceController.class);

    private PrincipalFactory principalFactory;
    private Service viewService;
    
    private final static String ORIGINAL_PARAMETER = "original";
   
    public void afterPropertiesSet() throws Exception {
    }

    @Override
    protected ModelAndView handleRequestInternal(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        
        String revisionParam = request.getParameter("revision");
        if (revisionParam == null) {
            return badRequest("'revision' argument must be supplied", response);
        }
        String revisionNameA = null;
        String revisionNameB = null;
        String[] revisions = revisionParam.split(",");
        if (revisions.length == 2) {
            revisionNameA = revisions[0];
            revisionNameB = revisions[1];
        } else {
            try {
                revisionNameB = revisionParam;
                int revisionNumber = Integer.parseInt(revisionParam);
                revisionNameA = "" + (revisionNumber - 1); 
            } catch (NumberFormatException e) {
                return badRequest("'revision' must be either an integer or a comma separated pair", response);
            }
        }
        
        boolean showOriginal = request.getParameter(ORIGINAL_PARAMETER) != null;
        
        String content = diffRevisions(revisionNameA, revisionNameB, request);

        Map<String, Object> model = new HashMap<String, Object>();
        model.put("revisionA", revisionNameA);
        model.put("revisionB", revisionNameB);
        model.put("content", content);
        model.put(ORIGINAL_PARAMETER, showOriginal);
        
        // TODO: Diff service
        RequestContext requestContext = RequestContext.getRequestContext();
        URL originalUrl = viewService.constructURL(requestContext.getResourceURI());

        originalUrl.addParameter("revision", revisionNameB);
        model.put("originalUrl", originalUrl);
        
        putRevisionInfo(model, revisionNameA, revisionNameB, request, showOriginal);
        
        return new ModelAndView(getViewName(), model);
    }
    
    /*
     * For aborting the request with a HTTP Bad Request response.
     */
    private ModelAndView badRequest(String errorMessage, HttpServletResponse response) throws IOException {
        logger.error(errorMessage);
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, errorMessage);
        return null;
    }
    
    /*
     * Retrieve specified revisions and return a comparison result.
     * 
     * NB! No input validation is performed.
     * I.e. it is assumed that the revisions names are valid an existing,
     * and that a null result for a revision is not returned.
     */
    private String diffRevisions(String revisionNameA, String revisionNameB, HttpServletRequest request) throws Exception {
        String contentA = getContentForRevision(revisionNameA, request);
        String contentB = getContentForRevision(revisionNameB, request);
        return getHtmlDifferences(contentA, contentB);
    }
    
    private String getHtmlDifferences(String contentA, String contentB) throws Exception {
        DifferenceEngine differ = new DifferenceEngine();
        return differ.diff(contentA, contentB);
    }

    /*
     * Use internal request to look up a plain version of the given revision of the resource.
     */
    private String getContentForRevision(String revisionName, HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Resource resource = repository.retrieve(token, requestContext.getResourceURI(), false);
        URL forwardURL = viewService.constructURL(resource, requestContext.getPrincipal());
        forwardURL.clearParameters();
        if (!"HEAD".equals(revisionName)) {
            forwardURL.addParameter("revision", revisionName);
        }

        forwardURL.addParameter("x-prevent-decorating", "true")
                  .addParameter("vrtxPreviewUnpublished", "true");
        
        if (logger.isDebugEnabled()) {
            logger.debug("Dispatch forward request to: " + forwardURL);
        }

        ConfigurableRequestWrapper requestWrapper = new ConfigurableRequestWrapper(request, forwardURL);
        String servletName = (String) request.getAttribute(VTKServlet.SERVLET_NAME_REQUEST_ATTRIBUTE);
        RequestDispatcher rd = getServletContext().getNamedDispatcher(servletName);

        if (rd == null) {
            throw new RuntimeException("No request dispatcher for name '" + servletName + "' available");
        }

        BufferedResponse bufferedResponse = new BufferedResponse();
        rd.include(requestWrapper, bufferedResponse);
        int status = bufferedResponse.getStatus();
        if (status < 200 || status > 299) {
            throw new Exception("Internal request " + forwardURL + " failed with HTTP status " + status);
        }
        return bufferedResponse.getContentString();
    }

    /*
     * Add meta data for current revision, and list all available revisions for the resource
     */
    private void putRevisionInfo(Map<String, Object> model, String revisionNameA, String revisionNameB, HttpServletRequest request, boolean showOriginal) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        Resource r = repository.retrieve(token, uri, true);
        Property title = r.getProperty(Namespace.DEFAULT_NAMESPACE, "title");
        if (title != null && title.getValue() != null && title.getValue().getStringValue() != null) {
            model.put("title", title.getValue().getStringValue()); 
        }
        
        List<Revision> revisions = repository.getRevisions(token, uri);

        List<Object> allRevisions = new ArrayList<Object>();
        String revisionBNext = null;
        boolean haveRecentlySeenRevisionA = false;
        for (Revision revision: revisions) {
            Map<String, Object> rev = new HashMap<String, Object>();
            rev.put("id", revision.getID());
            rev.put("name", revision.getName());
            rev.put("timestamp", revision.getTimestamp());
            rev.put("principal", this.principalFactory.getPrincipal(revision.getUid(), Type.USER)); 
            rev.put("acl", revision.getAcl());
            rev.put("checksum", revision.getChecksum());
            allRevisions.add(rev);
            if (haveRecentlySeenRevisionA) {
                model.put("revisionAPrevious", revision.getName());
                haveRecentlySeenRevisionA = false;
            }
            if (revisionNameA.equalsIgnoreCase(revision.getName())) {
                if (showOriginal) {
                    rev.put("name", revision.getName() + "&amp;" + ORIGINAL_PARAMETER);
                }
                model.put("revisionADetails", rev);
                haveRecentlySeenRevisionA = true;
            }
            if (revisionNameB.equalsIgnoreCase(revision.getName())) {
                model.put("revisionBDetails", rev);
                if (revisionBNext != null) {
                    model.put("revisionBNext", revisionBNext + (showOriginal ? "&amp;" + ORIGINAL_PARAMETER : ""));
                }
            }
            revisionBNext = revision.getName();
        }
        model.put("allRevisions", allRevisions);
    }
   
    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }
    
    @Required
    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }
}
