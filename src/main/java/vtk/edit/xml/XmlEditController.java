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
package vtk.edit.xml;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jdom.JDOMException;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.AuthorizationException;
import vtk.repository.Lock;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.RepositoryException;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.AuthenticationException;
import vtk.security.Principal;
import vtk.security.SecurityContext;
import vtk.util.repository.ContentTypeHelper;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.ServiceUnlinkableException;
import vtk.xml.StylesheetCompilationException;
import vtk.xml.TransformerManager;



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
public class XmlEditController implements Controller {

    private static Logger logger = LoggerFactory.getLogger(XmlEditController.class);

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
    
    
    private Map<String, ActionHandler> actionMapping = new HashMap<String, ActionHandler>();

    public XmlEditController() {
        this.actionMapping.put(EDIT_ACTION, new EditController());
        this.actionMapping.put(EDIT_DONE_ACTION, new EditDoneController());
        this.actionMapping.put(NEW_AT_ACTION, new NewElementAtController());
        this.actionMapping.put(MOVE_ACTION, new MoveController());
        this.actionMapping.put(MOVE_DONE_ACTION, new MoveItController());
        this.actionMapping.put(DELETE_ELEMENT_ACTION, new DeleteController());
        this.actionMapping.put(NEW_ACTION, new NewElementController());
        this.actionMapping.put(NEW_SUB_ELEMENT_AT_ACTION, new NewSubElementAtController());
        this.actionMapping.put(DELETE_SUB_ELEMENT_AT_ACTION, new DeleteSubElementAtController());
    }
    
    public ModelAndView handleRequest(HttpServletRequest request, 
            HttpServletResponse response) throws Exception, TransformerException {
        try {
            String action = request.getParameter(ACTION_PARAMETER_NAME);

            Map<String, Object> sessionMap = getSessionMap(request);

            /* "Validate" and create web editable resource session */
            if (sessionMap == null) {
                sessionMap = initEditSession(request);
            }

            EditDocument document = 
                (EditDocument) sessionMap.get(EditDocument.class.getName());
            SchemaDocumentDefinition documentDefinition = (SchemaDocumentDefinition) 
                sessionMap.get(SchemaDocumentDefinition.class.getName());

            if (FINISH_ACTION.equals(action)) {
                finish(request, document);
                return new ModelAndView(this.finishViewName);
            }

            ActionHandler handler = this.actionMapping.get(action);
            Map<String, Object> model = new HashMap<String, Object>();

            if (handler != null)
                model = handler.handle(request, document, documentDefinition);

            if (model == null)
                model = handleModeError(document, request);

            referenceData(model, document);

            return new ModelAndView(this.viewName, model);
        } catch (RepositoryException e) {
            logger.error("Rethrowing repo exception", e);
            throw e;
        }
    }
    
    private void finish(HttpServletRequest request, EditDocument document)
    throws Exception {
        document.finish();
        Path uri = RequestContext.getRequestContext().getResourceURI();
        String sessionID = XmlEditController.class.getName() + ":" + uri; 
        request.getSession(true).removeAttribute(sessionID);
    }
    
    private Map<String, Object> getSessionMap(HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Path uri = requestContext.getResourceURI();

        String sessionID = XmlEditController.class.getName() + ":" + uri; 
        @SuppressWarnings("unchecked")
        Map<String, Object> sessionMap = (Map<String, Object>) request.getSession(true).getAttribute(sessionID);

        if (sessionMap == null) {
            return null;
        }

        /* Check that session map isn't stale (the lock has been released).
           A user can access the same (locked) resource from different clients */
        String token = SecurityContext.getSecurityContext().getToken();
        Principal principal = SecurityContext.getSecurityContext().getPrincipal();
        Resource resource = this.repository.retrieve(token, uri, false);
        
        Lock lock = resource.getLock();

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

        if (sessionMap != null) {
            EditDocument document = 
                (EditDocument) sessionMap.get(EditDocument.class.getName());

            if (document.getResource().getLastModified().before(resource.getLastModified())) {
                return null;
            }
        }
        return sessionMap;
    }
    
    @SuppressWarnings("unchecked")
    private void referenceData(Map model, EditDocument document) 
        throws Exception {
        Resource resource = document.getResource();
        Principal principal = SecurityContext.getSecurityContext().getPrincipal();

        String token = SecurityContext.getSecurityContext().getToken();

        model.put("resource", document.getResource());
        model.put("jdomDocument", document);

        Util.setXsltParameter(model, "DAY", date("dd"));
        Util.setXsltParameter(model, "MONTH", date("MM"));
        Util.setXsltParameter(model, "YEAR", date("yyyy"));
        Util.setXsltParameter(model, "HOUR", date("HH"));
        Util.setXsltParameter(model, "TIMESTAMP", date("yyMMddHHmmss"));
        Util.setXsltParameter(model, "CMSURL", resource.getURI());
        if (principal != null)
            Util.setXsltParameter(model, "USERNAME", principal.getName());

        // The Browse service is optional, must javadoc this
        if (this.browseService != null) {
            try {
                Resource parentResource = this.repository.retrieve(token, resource.getURI().getParent(), false);
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

        Path uri = resource.getURI();
        Service service = RequestContext.getRequestContext().getService();
        Map<String,String> actionParam = new HashMap<String,String>();

        actionParam.put(ACTION_PARAMETER_NAME, EDIT_ACTION);
        Util.setXsltParameter(model, "editElementServiceURL", 
                service.constructLink(uri, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, EDIT_DONE_ACTION);
        Util.setXsltParameter(model, "editElementDoneServiceURL", 
                service.constructLink(uri, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, MOVE_ACTION);
        Util.setXsltParameter(model, "moveElementServiceURL", 
                service.constructLink(uri, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, MOVE_DONE_ACTION);
        Util.setXsltParameter(model, "moveElementDoneServiceURL", 
                service.constructLink(uri, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, DELETE_ELEMENT_ACTION);
        Util.setXsltParameter(model, "deleteElementServiceURL", 
                service.constructLink(uri, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, NEW_AT_ACTION);
        Util.setXsltParameter(model, "newElementAtServiceURL", 
                service.constructLink(uri, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, NEW_ACTION);
        Util.setXsltParameter(model, "newElementServiceURL", 
                service.constructLink(uri, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, NEW_SUB_ELEMENT_AT_ACTION);
        Util.setXsltParameter(model, "newSubElementAtServiceURL",
                service.constructLink(uri, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, DELETE_SUB_ELEMENT_AT_ACTION);
        Util.setXsltParameter(model, "deleteSubElementAtServiceURL",
                service.constructLink(uri, actionParam));
        actionParam.put(ACTION_PARAMETER_NAME, FINISH_ACTION);
        Util.setXsltParameter(model, "finishEditingServiceURL",
                service.constructLink(uri, actionParam));

    }

    private String date(String format) {
        Date today = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat(format);
        return formatter.format(today);
    }

    private Map<String, Object> handleModeError(EditDocument document, HttpServletRequest request) {
        
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
        
        Map<String, Object> model = new HashMap<String, Object>();
        Util.setXsltParameter(model, "ERRORMESSAGE", "UNNSUPPORTED_ACTION_IN_MODE");
        return model;
    }
    


    private Map<String, Object> initEditSession(HttpServletRequest request) 
    throws Exception, TransformerException {
        RequestContext requestContext = RequestContext.getRequestContext();
        SecurityContext securityContext = SecurityContext.getSecurityContext();
        
        Path uri = requestContext.getResourceURI();
        String token = securityContext.getToken();
        
        String sessionID = XmlEditController.class.getName() + ":" + uri; 
        
        Resource resource = this.repository.retrieve(token, uri, false);
        
        Map<String, Object> sessionMap = new HashMap<String, Object>();

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
            this.transformerManager.getTransformer(document);
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
    
    @Required
    public void setTransformerManager(TransformerManager transformerManager) {
        this.transformerManager = transformerManager;
    }

    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public void setLockTimeoutSeconds(int lockTimeoutSeconds) {
        this.lockTimeoutSeconds = lockTimeoutSeconds;
    }

    @Required
    public void setViewName(final String viewName){
        this.viewName = viewName;
    }

    @Required
    public void setSchemaPropDef(PropertyTypeDefinition schemaPropDef) {
        this.schemaPropDef = schemaPropDef;
    }

    @Required
    public void setFinishViewName(String finishViewName) {
        this.finishViewName = finishViewName;
    }

}
