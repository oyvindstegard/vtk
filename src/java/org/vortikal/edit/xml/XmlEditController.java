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
package org.vortikal.edit.xml;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.JDOMException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.vortikal.repository.AuthorizationException;
import org.vortikal.repository.Lock;
import org.vortikal.repository.Property;
import org.vortikal.repository.Repository;
import org.vortikal.repository.RepositoryException;
import org.vortikal.repository.Resource;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.security.AuthenticationException;
import org.vortikal.security.Principal;
import org.vortikal.security.SecurityContext;
import org.vortikal.util.repository.ContentTypeHelper;
import org.vortikal.web.RequestContext;
import org.vortikal.web.service.Service;
import org.vortikal.web.service.ServiceUnlinkableException;
import org.vortikal.xml.StylesheetCompilationException;
import org.vortikal.xml.TransformerManager;



/**
 * The XML edit controller.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>lockTimeoutSeconds</code> - an integer specifying the
 *   number of seconds to lock the resource when editing it. The
 *   default is <code>1800</code> (30 minutes).
 *   <li><code>viewName</code> - the view name to return.
 *   <li>+
 *   <li>+++++
 * </ul>
 */
public class XmlEditController implements Controller, InitializingBean {

    private static Log logger = LogFactory.getLog(XmlEditController.class);

    private Repository repository;
    private TransformerManager transformerManager;
    private PropertyTypeDefinition schemaPropDef;
    private Service browseService;
    private int lockTimeoutSeconds = 30 * 60;
    private String viewName;
    private String finishViewName;

    private static String ACTION_PARAMETER_NAME = "action";

    private static String FINISH_ACTION = "finish";

    private static String EDIT_ACTION = "edit";
    private static String EDIT_DONE_ACTION = "editDone";
    private static String DELETE_ELEMENT_ACTION = "deleteElement";
    private static String MOVE_ACTION = "moveElement";
    private static String MOVE_DONE_ACTION = "moveElementDone";
    private static String NEW_ACTION = "newElement";
    private static String NEW_AT_ACTION = "newElementAt";
    private static String DELETE_SUB_ELEMENT_AT_ACTION = "deleteSubElementAt";
    private static String NEW_SUB_ELEMENT_AT_ACTION = "newSubElementAt";
    
    private ActionHandler editActionHandler = new EditController();
    private ActionHandler editElementDoneActionHandler = new EditDoneController();
    private ActionHandler newElementAtActionHandler = new NewElementAtController();
    private ActionHandler moveElementActionHandler = new MoveController();
    private ActionHandler moveElementDoneActionHandler = new MoveItController();
    private ActionHandler deleteElementActionHandler = new DeleteController();
    private ActionHandler newElementActionHandler = new NewElementController();
    private ActionHandler newSubElementAtActionHandler = new NewSubElementAtController();
    private ActionHandler deleteSubElementAtActionHandler = new DeleteSubElementAtController();
    
    private Map actionMapping = new HashMap();

    public void afterPropertiesSet() throws Exception {
        this.actionMapping.put(EDIT_ACTION, editActionHandler);
        this.actionMapping.put(EDIT_DONE_ACTION, editElementDoneActionHandler);
        this.actionMapping.put(NEW_AT_ACTION, newElementAtActionHandler);
        this.actionMapping.put(MOVE_ACTION, moveElementActionHandler);
        this.actionMapping.put(MOVE_DONE_ACTION, moveElementDoneActionHandler);
        this.actionMapping.put(DELETE_ELEMENT_ACTION, deleteElementActionHandler);
        this.actionMapping.put(NEW_ACTION, newElementActionHandler);
        this.actionMapping.put(NEW_SUB_ELEMENT_AT_ACTION, newSubElementAtActionHandler);
        this.actionMapping.put(DELETE_SUB_ELEMENT_AT_ACTION, deleteSubElementAtActionHandler);

    }
    
    public ModelAndView handleRequest(HttpServletRequest request, 
            HttpServletResponse response) throws IOException, TransformerException {
try {        
        String action = request.getParameter(ACTION_PARAMETER_NAME);

        Map sessionMap = getSessionMap(request);

        
        /* "Validate" and create web-editable resource session */
        if (sessionMap == null) {
            sessionMap = initEditSession(request);
        } 
        
        EditDocument document = (EditDocument) 
            sessionMap.get(EditDocument.class.getName());
        SchemaDocumentDefinition documentDefinition = (SchemaDocumentDefinition)
            sessionMap.get(SchemaDocumentDefinition.class.getName());
        
        if (FINISH_ACTION.equals(action)) {
            finish(request, document);
            return new ModelAndView(this.finishViewName);
        }

        ActionHandler handler = (ActionHandler) this.actionMapping.get(action);
        Map model = new HashMap();
        
        if (handler != null) 
            model = handler.handle(request, document, documentDefinition);

        if (model == null)
            model = handleModeError(document, request);
        
        referenceData(model, document);

        return new ModelAndView(this.viewName, model);
}catch (RepositoryException e) {
    logger.error("Rethrowing repo exception", e);
    throw e;
}   
    }
    
    private void finish(HttpServletRequest request, EditDocument document)
    throws IOException {
        document.finish();
        String uri = RequestContext.getRequestContext().getResourceURI();
        String sessionID = XmlEditController.class.getName() + ":" + uri; 
        request.getSession(true).removeAttribute(sessionID);
    }
    
    private Map getSessionMap(HttpServletRequest request) throws IOException {
        RequestContext requestContext = RequestContext.getRequestContext();
        String uri = requestContext.getResourceURI();

        String sessionID = XmlEditController.class.getName() + ":" + uri; 
        Map sessionMap = (Map) request.getSession(true).getAttribute(sessionID);

        /* Check that sessionmap isn't stale (the lock has been released) */
        /* a user can access the same (locked) resource from different clients */
        if (sessionMap != null) {
            String token = SecurityContext.getSecurityContext().getToken();
            Principal principal = SecurityContext.getSecurityContext().getPrincipal();
            Lock lock = this.repository.retrieve(token, uri, false).getLock();

            if (lock == null) {
                if (logger.isDebugEnabled())
                    logger.debug("Stored xml edit session data is out of date.");

                request.getSession(true).removeAttribute(sessionID);
                sessionMap = null;
            } else if (!lock.getPrincipal().equals(principal)) {
                // Should do something else
                if (logger.isDebugEnabled())
                    logger.debug("Resource locked by another user.");
                request.getSession(true).removeAttribute(sessionID);
                sessionMap = null;
            }

        }
        return sessionMap;
    }
    
    private void referenceData(Map model, EditDocument document) 
        throws IOException {
        Resource resource = document.getResource();
        Principal principal = SecurityContext.getSecurityContext().getPrincipal();

        String token = SecurityContext.getSecurityContext().getToken();

        model.put("resource", document.getResource());
        model.put("jdomDocument", document);

        Util.setXsltParameter(model, "pageTitle", "Du redigerer: " + resource.getName());
        Util.setXsltParameter(model, "DAY", date("dd"));
        Util.setXsltParameter(model, "MONTH", date("MM"));
        Util.setXsltParameter(model, "YEAR", date("yyyy"));
        Util.setXsltParameter(model, "TIMESTAMP", date("yyMMddHHmmss"));
        Util.setXsltParameter(model, "CMSURL", resource.getURI());
        Util.setXsltParameter(model,"applicationMode", "edit");
        if (principal != null)
            Util.setXsltParameter(model, "USERNAME", principal.getName());

        // The Browse service is optional, must javadoc this
        if (this.browseService != null) {
            try {
                Resource parentResource = this.repository.retrieve(token, resource
                        .getParent(), false);
                Util.setXsltParameter(model, "BROWSEURL", this.browseService
                        .constructLink(parentResource, principal));
            } catch (AuthorizationException e) {
                // No browse available for this resource
            } catch (AuthenticationException e) {
                // No browse available for this resource
            } catch (ServiceUnlinkableException e) {
                // No browse available for this resource
            }
        }

        Service service = RequestContext.getRequestContext().getService();
        Map actionParam = new HashMap();

        actionParam.put(ACTION_PARAMETER_NAME, EDIT_ACTION);
        Util.setXsltParameter(model, "editElementServiceURL", 
                service.constructLink(resource, principal, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, EDIT_DONE_ACTION);
        Util.setXsltParameter(model, "editElementDoneServiceURL", 
                service.constructLink(resource, principal, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, MOVE_ACTION);
        Util.setXsltParameter(model, "moveElementServiceURL", 
                service.constructLink(resource, principal, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, MOVE_DONE_ACTION);
        Util.setXsltParameter(model, "moveElementDoneServiceURL", 
                service.constructLink(resource, principal, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, DELETE_ELEMENT_ACTION);
        Util.setXsltParameter(model, "deleteElementServiceURL", 
                service.constructLink(resource, principal, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, NEW_AT_ACTION);
        Util.setXsltParameter(model, "newElementAtServiceURL", 
                service.constructLink(resource, principal, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, NEW_ACTION);
        Util.setXsltParameter(model, "newElementServiceURL", 
                service.constructLink(resource, principal, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, NEW_SUB_ELEMENT_AT_ACTION);
        Util.setXsltParameter(model, "newSubElementAtServiceURL",
                service.constructLink(resource, principal, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, DELETE_SUB_ELEMENT_AT_ACTION);
        Util.setXsltParameter(model, "deleteSubElementAtServiceURL",
                service.constructLink(resource, principal, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, FINISH_ACTION);
        Util.setXsltParameter(model, "finishEditingServiceURL",
                service.constructLink(resource, principal, actionParam));

    }

    private String date(String format) {
        Date today = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat(format);
        return formatter.format(today);
    }

    private Map handleModeError(EditDocument document, HttpServletRequest request) {
        
        RequestContext requestContext = RequestContext.getRequestContext();
        SecurityContext securityContext = SecurityContext.getSecurityContext();

        StringBuffer sb = new StringBuffer();
        

        sb.append("Mismatch in edit state (don't use 'back' button). ");
        sb.append("Request context: [").append(requestContext).append("], ");
        sb.append("security context: [").append(securityContext).append("], ");
        sb.append("request parameters: ").append(request.getParameterMap()).append(", ");
        sb.append("user agent: [").append(request.getHeader("User-Agent")).append("], ");
        sb.append("remote host: [").append(request.getRemoteHost()).append("]");
        sb.append("Current document state:\n").append(document.toStringDetail());

        logger.warn(sb.toString());
        
        Map model = new HashMap();
        Util.setXsltParameter(model, "ERRORMESSAGE", "UNNSUPPORTED_ACTION_IN_MODE");
        return model;
    }
    


    private Map initEditSession(HttpServletRequest request) 
    throws IOException, TransformerException {
        RequestContext requestContext = RequestContext.getRequestContext();
        SecurityContext securityContext = SecurityContext.getSecurityContext();
        
        String uri = requestContext.getResourceURI();
        String token = securityContext.getToken();
        
        String sessionID = XmlEditController.class.getName() + ":" + uri; 
        
        Resource resource = this.repository.retrieve(token, uri, false);
        
        Map sessionMap = new HashMap();

        EditDocument document = null;
        SchemaDocumentDefinition documentDefinition = null;
        
        
        /* The resource has to be an XML document */
        if (! ContentTypeHelper.isXMLContentType(resource.getContentType())) {
            throw new XMLEditException("Resource is not an xml document");
        }

        /* get required schemaURL */
        Property schemaProp = resource.getProperty(this.schemaPropDef); 
        if (schemaProp == null)
            throw new XMLEditException(
                    "XML document is uneditable, schema reference is missing");

        String schemaURL = schemaProp.getStringValue();
        if (schemaURL == null) 
            throw new XMLEditException("Invalid schema URI '" + schemaURL + "'");
        
        /* Try to build document */
        try {
            document = EditDocument.createEditDocument(this.repository, this.lockTimeoutSeconds);
        } catch (JDOMException e) {
            // FIXME: error handling?
            throw new XMLEditException("Document build failure", e);
        } catch (IOException e) {
            throw new XMLEditException("Document build failure", e);
        } 
        
        
        String docType = document.getRootElement().getName();
        

        /* try to instantiate schema-parser */
        try {
            documentDefinition = 
                new SchemaDocumentDefinition(docType, new URL(schemaURL));
        } catch (JDOMException e) {
            throw new XMLEditException("Schema build failure for schema '" + schemaURL + "'", e);
        } catch (MalformedURLException e) {
            throw new XMLEditException("Invalid schema uri '" + schemaURL + "'", e);
        } catch (IOException e) {
            throw new XMLEditException("Schema build failure for schema '" + schemaURL + "'", e);
        }

        /* Locate the edit XSL for this document type */
        String relativePath = documentDefinition.getXSLPath();
        if (relativePath == null || relativePath.trim().equals("")) {
            throw new XMLEditException("Edit XSL path not defined in schema '" + schemaURL + "'");
        }
        
        
        try {
            this.transformerManager.getTransformer(resource, document);
        } catch (IOException e) {
            // FIXME: error handling
            throw new XMLEditException("Unable to compile edit stylesheets for document '" + uri + "'", e);
        } catch (TransformerConfigurationException e) {
            // FIXME: error handling
            throw new XMLEditException("Unable to compile edit stylesheets for document '" + uri + "'", e);
        } catch (StylesheetCompilationException e) {
            // FIXME: error handling
            throw new XMLEditException("Unable to compile edit stylesheets for document '" + uri + "'", e);
        }

        sessionMap.put(EditDocument.class.getName(), document);
        sessionMap.put(SchemaDocumentDefinition.class.getName(), documentDefinition);
        request.getSession(true).setAttribute(sessionID, sessionMap);
        return sessionMap;
    } 

    public void setBrowseService(Service browseService) {
        this.browseService = browseService;
    }
    
    public void setTransformerManager(TransformerManager transformerManager) {
        this.transformerManager = transformerManager;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public void setLockTimeoutSeconds(int lockTimeoutSeconds) {
        this.lockTimeoutSeconds = lockTimeoutSeconds;
    }

    public void setViewName(final String viewName){
        this.viewName = viewName;
    }

    public void setSchemaPropDef(PropertyTypeDefinition schemaPropDef) {
        this.schemaPropDef = schemaPropDef;
    }

    public void setFinishViewName(String finishViewName) {
        this.finishViewName = finishViewName;
    }

}
