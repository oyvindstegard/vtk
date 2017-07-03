/* Copyright (c) 2004, University of Oslo, Norway
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
package vtk.web.referencedata.provider;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.output.DOMOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.w3c.dom.NodeList;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * XSL reference data provider
 * 
 * This class provides backend-data to the model suitable for use in
 * XSLT transformations.
 *
 * <p>Configurable properties:
 * <ul>
 *   <li>service - the {@link Service} for constructing path URLs
 *   <li><code>requireDocumentInModel</code> - whether to throw an
 *   exception when (normally required) <code>jdomDocument</code>
 *   model data is absent in the model. Default is <code>true</code>.
 *   <li>modelName - name to use for the provided (sub)model
 *   <li><code>adminService</code> - The default admin mode service -
 *   required
 *   <li><code>supplyRequestParameters</code> - default
 *     <code>true</code> - supply request parameters as a node list to
 *     xsl processing
 *   <li><code>matchAdminServiceAssertions</code> - default 
 *     <code>false</code> - determines whether all assertions must
 *     match in order for the admin link to be constructed
 * </ul>
 *
 * <p>Optionally model data:
 * <ul>
 *   <li>resource - a <code>vtk.repository.Resource</code> object</li>
 * </ul>
 * 
 * <p>Model data provided:
 * <ul>
 *   <li>a submodel having the bean property <code>modelName</code> of
 *   this class as its key. This submodel (map) in turn contains the following data:
 *   <ul>
 *     <li><code>currentUser</code>: the currently logged in user
 *     <li><code>pathElements</code>: the breadcrumb path (see below)
 *     <li><code>contentLanguage</code>
 *     <li><code>PARENT-COLLECTION</code>
 *     <li><code>CURRENT-URL</code>
 *     <li><code>ADMIN-URL</code> 
 *     <li>If the configuration property
 *       <code>supplyRequestParameters</code> is <code>true</code> (default),
 *       <code>requestParameters</code> is supplied, which is a node list with elements like:<br> 
 *       <code>&lt;parameter name="requestParameterName"&gt;requestParameterValue&lt;/parameter&gt;</code>
 *   </ul>
 * </ul>
 *
 * <p>The breadcrumb path: this is a {@link NodeList} of elements
 *  containing attributes <code>title</code> and <code>URL</code>.
 *  It is a straight forward XML mapping of the <code>breadCrumbProvider</code> 
 *  result.
 */
public class XSLReferenceDataProvider implements ReferenceDataProvider {

    private static final String REQUEST_PARAMETERS = "requestParameters";
    private static final String ADMIN_URL = "ADMIN-URL";
    private static final String CURRENT_URL = "CURRENT-URL";
    private static final String PARENT_COLLECTION = "PARENT-COLLECTION";
    private static final String CONTENT_LANGUAGE = "contentLanguage";
    private static final String PATH_ELEMENTS = "pathElements";
    private static final String CURRENT_USER = "currentUser";

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    private String modelName = null;
    private Service adminService;
    private boolean supplyRequestParameters = true;

    private boolean matchAdminServiceAssertions = false;

    private BreadCrumbProvider breadCrumbProvider; 

    @Required
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    @Required
    public void setAdminService(Service adminService) {
        this.adminService = adminService;
    }

    public void setSupplyRequestParameters(boolean supplyRequestParameters) {
        this.supplyRequestParameters = supplyRequestParameters;
    }

    public void setMatchAdminServiceAssertions(
            boolean matchAdminServiceAssertions) {
        this.matchAdminServiceAssertions = matchAdminServiceAssertions;
    }

    @Required
    public void setBreadCrumbProvider(BreadCrumbProvider breadCrumbProvider) {
        this.breadCrumbProvider = breadCrumbProvider;
    }

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {
        RequestContext requestContext = RequestContext.getRequestContext();
        Principal principal = requestContext.getPrincipal();
        String token = requestContext.getSecurityToken();

        Path uri = RequestContext.getRequestContext().getResourceURI();
        Resource resource = (Resource) model.get("resource");

        try {
            if (resource == null) {
                Repository repository = requestContext.getRepository();
                resource = repository.retrieve(token, uri, true);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> subModel = (Map<String, Object>) model.get(this.modelName);
            if (subModel == null) {
                subModel = new HashMap<>();
                model.put(this.modelName, subModel);
            }

            /* this property is only set if the page requires authentication */
            String currentUser = (principal != null) ? principal.getName()
                    : null;
            subModel.put(CURRENT_USER, currentUser);

            NodeList path = buildPaths(request);
            subModel.put(PATH_ELEMENTS, path);

            subModel.put(CONTENT_LANGUAGE, resource.getContentLanguage());
            subModel.put(PARENT_COLLECTION, resource.getURI().getParent());
            subModel.put(CURRENT_URL, request.getRequestURL());
            URL adminURL = adminService.urlConstructor(URL.create(request))
                    .withResource(resource)
                    .withPrincipal(principal)
                    .matchAssertions(matchAdminServiceAssertions)
                    .constructURL();
            
            subModel.put(ADMIN_URL, adminURL.toString());

            if (this.supplyRequestParameters) {
                subModel.put(REQUEST_PARAMETERS, getRequestParams(request));
            }

        } catch (Throwable t) {
            this.logger.warn("Unable to provide complete XSLT reference data", t);
        }
    }

    @SuppressWarnings("unchecked")
    private NodeList getRequestParams(HttpServletRequest request) throws JDOMException {
        /* creating a nodeList with all request parameters */
        Document doc = new Document(new Element("root"));
        Element root = doc.getRootElement();
        List<Element> children = root.getChildren();

        for (Enumeration<String> e = request.getParameterNames(); e.hasMoreElements();) {
            String parameterName = e.nextElement();

            Element element = new Element("parameter");
            element.setAttribute("name", parameterName);
            element.setText(request.getParameter(parameterName));
            children.add(element);
        }

        DOMOutputter oupt = new DOMOutputter();
        org.w3c.dom.Document domDoc = null;

        domDoc = oupt.output(doc);
        return domDoc.getDocumentElement().getChildNodes();
    }

    private NodeList buildPaths(HttpServletRequest request) {
        Document doc = new Document(new Element(PATH_ELEMENTS));
        Element pathElements = doc.getRootElement();

        Map<String, Object> model = new HashMap<>();
        this.breadCrumbProvider.referenceData(model, request);

        BreadcrumbElement[] crumbs = 
            (BreadcrumbElement[])model.get("breadcrumb");

        for (int i = crumbs.length - 1; i >= 0; i--) {
            BreadcrumbElement element = crumbs[i];
            Element pathElement = new Element("pathElement");
            String title = element.getTitle();
            if (title == null) title = "";
            pathElement.setAttribute("title", title);
            String url = "";
            if(element.getURL() != null)
                url = element.getURL().toString();
            pathElement.setAttribute("URL", url);
            pathElements.addContent(0, pathElement);
            if (logger.isDebugEnabled()) {
                logger.debug("Built path element: title = "
                        + element.getTitle() + ", URL = " + element.getURL());
            }
        }
        // Convert the JDOM element to a org.w3c.dom Element:
        DOMOutputter oupt = new DOMOutputter();
        NodeList nodeList = null;
        org.w3c.dom.Document domDoc = null;

        try {
            domDoc = oupt.output(doc);
            nodeList =  domDoc.getDocumentElement().getChildNodes();
        } catch (JDOMException e) {
            logger.warn("Failed to build path document", e);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("pathElements: " + pathElements);
            logger.debug("nodeList: " + nodeList.getLength());
        }
        return nodeList;
    }
}
