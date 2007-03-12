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
package org.vortikal.web.referencedata.provider;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.output.DOMOutputter;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.search.XmlSearcher;
import org.vortikal.security.Principal;
import org.vortikal.security.SecurityContext;
import org.vortikal.web.RequestContext;
import org.vortikal.web.referencedata.ReferenceDataProvider;
import org.vortikal.web.service.Service;
import org.w3c.dom.NodeList;


/**
 * XSL reference data provider
 * 
 * This class provides backend-data to the model suitable for use in
 * XSLT transformations.
 *
 * <p>Configurable properties:
 * <ul>
 *   <li>repository - the content repository
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
 *   <li><code>xmlSearcher</code> - the {@link XmlSearcher} to
 *   provide to the XSLT transformation - provided under the key
 *   <code>{http://www.uio.no/vortex/xsl-parameters}XMLSearcher</code>.
 * </ul>
 *
 * <p>Optionally model data:
 * <ul>
 *   <li>resource - a <code>org.vortikal.repository.Resource</code> object</li>
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
public class XSLReferenceDataProvider
  implements InitializingBean, ReferenceDataProvider {

    private static String XML_SEARCHER_KEY = "{http://www.uio.no/vortex/xsl-parameters}XmlSearcher";
    
    private XmlSearcher xmlSearcher;

    
    private static final String REQUEST_PARAMETERS = "requestParameters";
    private static final String ADMIN_URL = "ADMIN-URL";
    private static final String CURRENT_URL = "CURRENT-URL";
    private static final String PARENT_COLLECTION = "PARENT-COLLECTION";
    private static final String CONTENT_LANGUAGE = "contentLanguage";
    private static final String PATH_ELEMENTS = "pathElements";
    private static final String CURRENT_USER = "currentUser";
    
    protected Log logger = LogFactory.getLog(this.getClass());
    
    private String modelName = null;
    private Repository repository; 
    private Service service; 
    private Service adminService;
    private boolean supplyRequestParameters = true;
    
    private boolean matchAdminServiceAssertions = false;
    
    private BreadCrumbProvider breadCrumbProvider; 
        
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }


    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    
    public void setService(Service service) {
        this.service = service;
    }
    
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

    
    public void afterPropertiesSet() throws Exception {
        if (this.xmlSearcher == null) {
            throw new BeanInitializationException("Property 'xmlSearcher' not set.");
        }
        if (this.modelName == null) {
            throw new BeanInitializationException(
                "Bean property 'modelName' must be set");
        }
        if (this.adminService == null) {
            throw new BeanInitializationException(
                "Bean property 'adminService' must be set");
        }
        if (this.repository == null) {
            throw new BeanInitializationException(
                "Bean property 'repository' must be set");
        }
        if (this.service == null) {
            throw new BeanInitializationException(
                "Bean property 'service' must be set");
        }
        if (this.breadCrumbProvider == null) {
            throw new BeanInitializationException(
                "Bean property 'breadCrumbProvider' must be set");
        }
    }


    public void referenceData(Map model, HttpServletRequest request) {
        
        SecurityContext securityContext = SecurityContext.getSecurityContext();
        Principal principal = securityContext.getPrincipal();
        String token = securityContext.getToken();

        String uri = RequestContext.getRequestContext().getResourceURI();

        Resource resource = (Resource) model.get("resource");

        try {

            if (resource == null) {
                resource = this.repository.retrieve(token, uri, true);
            }

            Map subModel = (Map) model.get(this.modelName);
            if (subModel == null) {
                subModel = new HashMap();
                model.put(this.modelName, subModel);
            }

            /* setting variables */

            /* this property is only set if the page requires authentication */
            String currentUser = (principal != null) ? principal.getName()
                    : null;
            subModel.put(CURRENT_USER, currentUser);

            NodeList path = buildPaths(request);
            subModel.put(PATH_ELEMENTS, path);

            subModel.put(CONTENT_LANGUAGE, resource.getContentLanguage());
            subModel.put(PARENT_COLLECTION, resource.getParent());
            subModel.put(CURRENT_URL, request.getRequestURL());
            subModel.put(ADMIN_URL, this.adminService.constructLink(resource,
                    principal, this.matchAdminServiceAssertions));

            if (this.supplyRequestParameters) {
                subModel.put(REQUEST_PARAMETERS, getRequestParams(request));
            }
            subModel.put(XML_SEARCHER_KEY, this.xmlSearcher);

            
        } catch (Throwable t) {
            this.logger.warn("Unable to provide complete XSLT reference data", t);
        }

    }

    private NodeList getRequestParams(HttpServletRequest request) throws JDOMException {
        /* creating a nodeList with all request parameters */
        Document doc = new Document(new Element("root"));
        Element root = doc.getRootElement();
        List children = root.getChildren();

        for (Enumeration e = request.getParameterNames(); e.hasMoreElements();) {
            String parameterName = (String) e.nextElement();
        
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

        Map model = new HashMap();
        this.breadCrumbProvider.referenceData(model, request);
        
        BreadcrumbElement[] crumbs = 
            (BreadcrumbElement[])model.get("breadcrumb");

        for (int i = crumbs.length - 1; i >= 0; i--) {
            BreadcrumbElement element = crumbs[i];
          Element pathElement = new Element("pathElement");
          String title = element.getTitle();
          if (title == null) title = "";
          pathElement.setAttribute("title", title);
          String url = element.getURL();
          if (url == null) url = "";
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

    public void setBreadCrumbProvider(BreadCrumbProvider breadCrumbProvider) {
        this.breadCrumbProvider = breadCrumbProvider;
    }


    public void setXmlSearcher(XmlSearcher xmlSearcher) {
        this.xmlSearcher = xmlSearcher;
    }

}
