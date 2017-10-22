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
package vtk.webdav;

import java.io.IOException;
import java.text.ParseException;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.http.HttpStatus;

import vtk.repository.AuthorizationException;
import vtk.repository.IllegalOperationException;
import vtk.repository.InheritablePropertiesStoreContext;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.ReadOnlyException;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceLockedException;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.TypeInfo;
import vtk.repository.resourcetype.ConstraintViolationException;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.resourcetype.ValueFormatException;
import vtk.security.AuthenticationException;
import vtk.security.Principal;
import vtk.text.html.HtmlUtil;
import vtk.web.InvalidRequestException;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * Handler for PROPPATCH requests.
 *
 */
public class ProppatchController extends AbstractWebdavController  {

    private Service webdavService;
    
    @SuppressWarnings("deprecation")
    @Override
    public void handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws IOException {
         
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Principal principal = requestContext.getPrincipal();
        Path uri = requestContext.getResourceURI();

        try {
            Resource resource = repository.retrieve(token, uri, false);
            TypeInfo typeInfo = repository.getTypeInfo(resource);
            /* Parse the request body XML: */
            Document requestBody = parseRequestBody(request);

            /* Make sure the request is valid: */
            validateRequestBody(requestBody);

            InheritablePropertiesStoreContext sc = new InheritablePropertiesStoreContext();
            Document doc = doPropertyUpdate(request, resource, requestBody, principal, typeInfo, sc);

            /* Store the altered resource: */
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("storing modified Resource");
            }
            
            if (sc.getAffectedProperties().isEmpty()) {
                resource = repository.store(token, null, resource);
            }
            else {
                // One or more inheritable props are to be stored/removed
                resource = repository.store(token, null, resource, sc);
            }

            responseBuilder(HttpStatus.MULTI_STATUS.value())
                    .handler(xmlResponseHandler(doc))
                    .writeTo(response);
        }
        catch (InvalidRequestException e) {
            this.logger.info("Invalid request on URI '" + uri + "'", e);
            writeDavErrorResponse(response, new Integer(HttpServletResponse.SC_BAD_REQUEST), e);            
        }
        catch (ResourceNotFoundException e) {
            responseBuilder(HttpServletResponse.SC_NOT_FOUND)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message(e.getMessage())
                .writeTo(response);
        }
        catch (ConstraintViolationException e) {
            writeDavErrorResponse(response, new Integer(HttpServletResponse.SC_FORBIDDEN), e);
        }
        catch (IllegalOperationException e) {
            writeDavErrorResponse(response, new Integer(HttpServletResponse.SC_FORBIDDEN), e);
        }
        catch (ReadOnlyException e) {
            writeDavErrorResponse(response, new Integer(HttpServletResponse.SC_FORBIDDEN), e);
        }
        catch (ResourceLockedException e) {
            responseBuilder(HttpStatus.LOCKED.value())
                .header("Content-Type", "text/plain;charset=utf-8")
                .message(e.getMessage())
                .writeTo(response);
        }
    }
    
    private void writeDavErrorResponse(HttpServletResponse response, Integer status, Exception e) throws IOException { 
        Element error = new Element("error", WebdavConstants.DAV_NAMESPACE);
        Element errormsg = new Element("errormsg", WebdavConstants.DEFAULT_NAMESPACE);
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        errormsg.setText(HtmlUtil.encodeBasicEntities(message));
        error.addContent(errormsg);

        Document doc = new Document(error);

        responseBuilder(status)
                .handler(xmlResponseHandler(doc))
                .writeTo(response);
    }
    
    /**
     * Builds a JDOM tree from the XML request body.
     *
     * @param request the <code>HttpServletRequest</code> 
     * @return a <code>org.jdom.Document</code> tree
     * @exception InvalidRequestException if request does not contain
     * a valid XML request body
     * @exception IOException if an I/O error occurs
     */
    protected Document parseRequestBody(HttpServletRequest request)
        throws InvalidRequestException, IOException {
        SAXBuilder builder = new SAXBuilder();

        try {

            Document requestBody = builder.build(
                request.getInputStream());
            return requestBody;

        } catch (JDOMException e) {
            this.logger.info("Invalid XML in request body", e);
            throw new InvalidRequestException("Invalid XML in request body");
        }
    }

    /**
     * Verifies that a JDOM tree constitutes a valid PROPPATCH request
     * body.
     *
     * @param requestBody a <code>org.jdom.Document</code> tree
     * representing the WebDAV request body
     * @exception InvalidRequestException if the request body is not
     * valid
     */ 
    @SuppressWarnings("unchecked") 
    protected void validateRequestBody(Document requestBody)
        throws InvalidRequestException {

        Element root = requestBody.getRootElement();

        if (!"propertyupdate".equals( root.getName())) {

            throw new InvalidRequestException(
                "Invalid request element '" + root.getName()
                + "' (expected 'propertyupdate')");
        }      

        for (Iterator<Element> actionIterator = root.getChildren().iterator();
             actionIterator.hasNext();) {

            Element actionElement = actionIterator.next();
            String action = actionElement.getName();

            if (!("set".equals(action) || "remove".equals(action))) {
                throw new InvalidRequestException(
                    "invalid element '" + action + "' (expected "
                    + "'set' or 'remove')");
            }

            /* FIXME: check name and namespace of the children of the
             * 'set' or 'remove' element */
        }
    }
    
    /**
     * Performs setting or removing of properties on a resource 
     *
     * @param resource a <code>Resource</code> value
     * @param requestBody a <code>Document</code> value
     * @exception InvalidRequestException if an error occurs
     */
    @SuppressWarnings("rawtypes")
    protected Document doPropertyUpdate(HttpServletRequest request, Resource resource, 
            Document requestBody, Principal principal, TypeInfo typeInfo, 
            InheritablePropertiesStoreContext sc)
        throws ResourceNotFoundException, AuthorizationException,
        AuthenticationException, IllegalOperationException,
        InvalidRequestException {
        
        Element root = requestBody.getRootElement();

        Element multistatus = new Element("multistatus", WebdavConstants.DAV_NAMESPACE);
        Element response = new Element("response", WebdavConstants.DAV_NAMESPACE);
        Element href = new Element("href", WebdavConstants.DAV_NAMESPACE);
        RequestContext requestContext = RequestContext.getRequestContext(request);
        URL url = webdavService.urlConstructor(requestContext.getRequestURL())
                .withResource(resource)
                .withPrincipal(principal)
                .constructURL();
        href.setText(url.toString());
        
        Element propstat = new Element("propstat", WebdavConstants.DAV_NAMESPACE);
        multistatus.addContent(response);
        response.addContent(href);
        response.addContent(propstat);
        
        for (Iterator actionIterator = root.getChildren().iterator();
             actionIterator.hasNext();) {

            Element actionElement = (Element) actionIterator.next();

            String action = actionElement.getName();

            if (action.equals("set")) {
                setProperties(request, propstat, resource,
                              actionElement.getChild(
                                  "prop", WebdavConstants.DAV_NAMESPACE).getChildren(), typeInfo, sc);
                
            }
            else if (action.equals("remove")) {
                removeProperties(request, propstat, resource,
                                 actionElement.getChild(
                                     "prop", WebdavConstants.DAV_NAMESPACE).getChildren(), typeInfo, sc);

            }
            else {
                throw new InvalidRequestException(
                    "invalid element '" + action + "' (expected "
                    + "'set' or 'remove')");
            }
        }
        return new Document(multistatus);
    }

    /**
     * Sets a list of properties on a resource .
     *
     * @param resource the <code>Resource</code> to modify
     * @param propElements a list of <code>org.jdom.Element</code>
     * objects representing DAV 'prop' elements (see RFC 2518,
     * sec. 12.11)
     */
    @SuppressWarnings("rawtypes")
    protected void setProperties(HttpServletRequest request,
            Element propstat, Resource resource, List propElements,
            TypeInfo typeInfo, InheritablePropertiesStoreContext sc)
        throws ResourceNotFoundException, AuthorizationException,
        AuthenticationException, IllegalOperationException {

        Element resultPropElement = new Element("prop", WebdavConstants.DAV_NAMESPACE);
        for (Iterator elementIterator = propElements.iterator();
             elementIterator.hasNext();) {
            Element propElement = (Element) elementIterator.next();
            setProperty(request, resultPropElement, resource, propElement, typeInfo, sc);
        }
        propstat.addContent(resultPropElement);

        Element statusElement = new Element("status", WebdavConstants.DAV_NAMESPACE);
        statusElement.setText("HTTP/" + WebdavConstants.HTTP_VERSION_USED + " 200 OK");
        propstat.addContent(statusElement);
    }
    
    /**
     * Sets a single property on a resource .
     *
     * @param resource the <code>Resource</code> to modify.
     * @param propertyElement an <code>org.jdom.Element</code> object
     * representing a DAV property element. This may be a standard DAV
     * property, or a custom one, although at present only standard
     * DAV properties are supported.
     */
    protected void setProperty(HttpServletRequest request, 
            Element resultElement, Resource resource, Element propertyElement,
            TypeInfo typeInfo, InheritablePropertiesStoreContext sc)
        throws ResourceNotFoundException, AuthorizationException,
        AuthenticationException, IllegalOperationException {

        String propertyName = propertyElement.getName();
        String nameSpace = propertyElement.getNamespace().getURI();

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Setting property with namespace: " + nameSpace);
        }

        if (nameSpace.toUpperCase().equals(WebdavConstants.DAV_NAMESPACE.getURI())) {

            if (propertyName.equals("displayname")) {
                throw new AuthorizationException("Setting property 'displayname' not permitted");
                
            }
            else if (propertyName.equals("getcontentlanguage")) {
                if (this.logger.isDebugEnabled()) {
                    this.logger.debug("setting property 'getcontentlanguage' to '"
                                 + propertyElement.getText() + "'");
                }
                PropertyTypeDefinition propDef = typeInfo.getPropertyTypeDefinition(Namespace.DEFAULT_NAMESPACE, PropertyType.CONTENTLOCALE_PROP_NAME);
                Property prop = propDef.createProperty();
                prop.setStringValue(propertyElement.getText());
                resource.addProperty(prop);
                // Add to inheritable property store context to get proper persistence in backend
                sc.addAffectedProperty(propDef);
                
            }
            else if (propertyName.equals("getcontenttype")) {
                if (this.logger.isDebugEnabled()) {
                    this.logger.debug("setting property 'getcontenttype' to '"
                                 + propertyElement.getText() + "'");
                }
                Property prop = typeInfo.createProperty(Namespace.DEFAULT_NAMESPACE, 
                        PropertyType.CONTENTTYPE_PROP_NAME);
                prop.setStringValue(propertyElement.getText());
                resource.addProperty(prop);
            }
            else {
                if (this.logger.isDebugEnabled()) {
                    this.logger.debug("Unsupported property: " + propertyName);
                }
                //throw new AuthorizationException();
            }
        }
        else {
            Namespace ns;
            if (nameSpace.toUpperCase().equals(
                    WebdavConstants.DEFAULT_NAMESPACE.getURI().toUpperCase())) {
                ns = Namespace.DEFAULT_NAMESPACE;
            }
            else {
                ns = Namespace.getNamespace(nameSpace);
            }
            Property property = resource.getProperty(ns, propertyName);
            if (property == null) {
                /* Create a new property: */
                property = typeInfo.createProperty(ns, propertyName);
                resource.addProperty(property);
            }
            
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Setting property " + property + 
                             " on resource " + resource.getURI());
            }
            
            PropertyTypeDefinition def = property.getDefinition();
            
            if (def != null) {
                // Set value of controlled property
                try {
                    if (def.isMultiple()) {
                        property.setValues(elementToValues(propertyElement, def));
                    }
                    else {
                        property.setValue(elementToValue(propertyElement, def));
                    }
                }
                catch (ValueFormatException e) {
                    this.logger.warn("Could not convert given value(s) for property " 
                            + property + " to the correct type: " + e.getMessage());
                    throw new IllegalOperationException("Could not convert given value(s) for property " 
                            + property + " to the correct type: " + e.getMessage());
                }
            }
            else {
                // Set string value of un-controlled property
                property.setStringValue(elementToString(propertyElement));
            }
        }
        Element resultPropertyElement = new Element(propertyName, propertyElement.getNamespace());
        resultElement.addContent(resultPropertyElement);
    }
    
    @SuppressWarnings("rawtypes")
    protected void removeProperties(HttpServletRequest request, Element propstat, Resource resource, 
            List propElements, TypeInfo typeInfo, InheritablePropertiesStoreContext sc) {
        Element resultPropElement = new Element("prop", WebdavConstants.DAV_NAMESPACE);
        for (Iterator elementIterator = propElements.iterator();
             elementIterator.hasNext();) {
            Element theProperty = (Element) elementIterator.next();
            removeProperty(request, resultPropElement, resource, theProperty, typeInfo, sc);
        }
        propstat.addContent(resultPropElement);

        Element statusElement = new Element("status", WebdavConstants.DAV_NAMESPACE);
        statusElement.setText("HTTP/" + WebdavConstants.HTTP_VERSION_USED + " 200 OK");
        propstat.addContent(statusElement);
    }
    

    protected void removeProperty(HttpServletRequest request, 
            Element resultElement, Resource resource, Element propElement,
            TypeInfo typeInfo, InheritablePropertiesStoreContext sc) {
        if (propElement.getNamespace().equals(WebdavConstants.DAV_NAMESPACE)) {
            return; 
        }
        String elementNamespaceURI = propElement.getNamespace().getURI();
        String propertyName = propElement.getName();
        Namespace propertyNamespace;
        if (elementNamespaceURI.toUpperCase().equals(
                WebdavConstants.DEFAULT_NAMESPACE.getURI().toUpperCase())) {
            propertyNamespace = Namespace.DEFAULT_NAMESPACE;
        } else {
            propertyNamespace = Namespace.getNamespace(elementNamespaceURI);
        }
        resource.removeProperty(propertyNamespace, propertyName);
        Element resultPropertyElement = new Element(propertyName, propElement.getNamespace());
        resultElement.addContent(resultPropertyElement);
    }
    
    /**
     * Builds a string representation of a property element.
     *
     * @param element a child element (property) of the "dav:prop" element.
     * @return a String representation of the property. If the element
     * has no child elements, the string returned is the value of the
     * element's text, otherwise the XML structure is preserved.
     */
    protected String elementToString(Element element) {
        try {
            if (element.getChildren().size() == 0) {
                /* Assume a "name = value" style property */
                return element.getText();
            }
            
            Format format = Format.getRawFormat();
            format.setOmitDeclaration(true);
            XMLOutputter xmlOutputter = new XMLOutputter(format);
            
            return xmlOutputter.outputString(element.getChildren());
        } catch (Exception e) {
            this.logger.warn("Error reading property value", e);
            return null;
        }
    }
    
    protected Value elementToValue(Element element, PropertyTypeDefinition def) throws ValueFormatException {
        String stringValue = element.getText();
        
        if (def.getType() == PropertyType.Type.TIMESTAMP || def.getType() == PropertyType.Type.DATE) {
            // Try to be liberal in accepting date formats:
            try {
                return new Value(WebdavUtil.parsePropertyDateValue(stringValue), def.getType() == PropertyType.Type.DATE);
            } catch (ParseException e) {
                try {
                    return def.getValueFormatter().stringToValue(stringValue, null, null);
                } catch (Exception vfe) {
                    throw new ValueFormatException(e);
                }
            }
        } 
        return def.getValueFormatter().stringToValue(stringValue, null, null);
    }
    
    @SuppressWarnings("unchecked") 
    protected Value[] elementToValues(Element element, PropertyTypeDefinition def) throws ValueFormatException {
        
        String[] stringValues;
        Element valuesElement;
        if ((valuesElement = element.getChild("values", 
                WebdavConstants.MULTI_VALUE_NAMESPACE))!= null) {
                
            List<Element> children = valuesElement.getChildren(
                "value", WebdavConstants.MULTI_VALUE_NAMESPACE);
            
            stringValues = new String[children.size()];
            int u=0;
            for (Element e: children) {
                stringValues[u++] = e.getText();
            }
        } else if (element.getChildren().size() == 0) {
            // Assume values separated by comma (CSV)
            stringValues = element.getText().split(",");
        } else {
            throw new ValueFormatException("Invalid multi-value syntax.");
        }

        if (stringValues.length == 0) {
            throw new ValueFormatException("Empty value lists are not supported.");
        }
    
        Value[] values;
        if (def.getType() == PropertyType.Type.TIMESTAMP || def.getType() == PropertyType.Type.DATE) {
            values = new Value[stringValues.length];
            try {
                for (int i=0; i<values.length; i++) {
                    values[i] = new Value(WebdavUtil.parsePropertyDateValue(stringValues[i]), def.getType() == PropertyType.Type.DATE);
                }
            } catch (ParseException e) {
                throw new ValueFormatException(e.getMessage());
            }
        } else {
            values = new Value[stringValues.length];
            for (int i=0; i<values.length; i++) {
            	values[i] = def.getValueFormatter().stringToValue(stringValues[i], null, null);	
            }
        }
        
        return values;
    }

    @Required
    public void setWebdavService(Service webdavService) {
        this.webdavService = webdavService;
    }
}
