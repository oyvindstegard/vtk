/* Copyright (c) 2004, 2008 University of Oslo, Norway
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
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.time.FastDateFormat;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.springframework.beans.factory.InitializingBean;

import vtk.repository.AuthorizationException;
import vtk.repository.Lock;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceLockedException;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.security.AuthenticationException;
import vtk.security.Principal;
import vtk.util.io.BoundedInputStream;
import vtk.util.io.SizeLimitException;
import vtk.util.repository.LocaleHelper;
import vtk.util.text.TextUtils;
import vtk.util.web.HttpUtil;
import vtk.web.InvalidRequestException;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * Handler for PROPFIND requests.
 * 
 * Analyzes the propfind body and puts the list of requested resources
 * in the model.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>maxRequestSize</code> - the maximum number of bytes the
 *   request size may consist of before it is considered invalid. The
 *   default value is <code>40000</code>.
 * </ul>
 *
 * <p>View names returned:
 * <ul>
 *   <li>PROPFIND - in successful cases
 *   <li>HTTP_STATUS_VIEW - in error cases
 * </ul>
 * 
 * <p>Model data provided (successful cases):
 * <ul>
 *   <li>resources - a list of the resources being requested
 *   <li>requestedProperties - the list of the requested resource
 *       properties (these are <code>org.jdom.Element</code> objects,
 *       representing the <code>dav:prop</code> elements from the
 *       request body).
 *   <li>appendValuesToRequestedProperties - a boolean indicating
 *       whether the values of the requested properties (not only the
 *       names and their existence) should be appended
 * </ul>
 *
 * <p>Model data provided (error cases):
 * <ul>
 *   <li>httpStatusCode - the HTTP status code indicating the type of
 *       error
 *   <li>errorObject - the cause of the error
 * </ul>
 * 
 */
public class PropfindController extends AbstractWebdavController 
                                implements InitializingBean {
    private Service webdavService = null;
    private String collectionContentType = null;
    private long maxRequestSize = 40000;
    private Map<org.jdom.Namespace,Set<String>> childAuthorizeWhitelistProperties;

    /**
     * Sets the maximum number of bytes allowed in request body. This
     * is to reduce the risk of DoS attacks by clients sending huge
     * request bodies.
     *
     * @param newSize a <code>Long</code> value
     */
    public void setMaxRequestSize(long newSize) {
        this.maxRequestSize = newSize;
    }
    
    public void setCollectionContentType(String value) {
        if (value != null) {
            if (value.trim().equals("")) {
                value = null;
            }
        }
        this.collectionContentType = value;
    }
    
    /**
     * Sets the WebDAV service. This service is needed for URL
     * construction (WebDAV PROPFIND browsing, downloading, etc.).
     *
     * @param webdavService a <code>Service</code> value
     */
    public void setWebdavService(Service webdavService) {
        this.webdavService = webdavService;
    }

    @Override
    public void afterPropertiesSet() {
        if (this.childAuthorizeWhitelistProperties == null) {
            this.childAuthorizeWhitelistProperties = new HashMap<>();
        }
        
        // Add standard set of DAV: props to whitelist
        Set<String> davProps = this.childAuthorizeWhitelistProperties.get(WebdavConstants.DAV_NAMESPACE);
        if (davProps == null) {
            davProps = new HashSet<>();
            this.childAuthorizeWhitelistProperties.put(WebdavConstants.DAV_NAMESPACE, davProps);
        }
        davProps.addAll(DAV_PROPERTIES);
    }


    @Override
    public void handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws IOException {
         
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        try {
            Resource resource = repository.retrieve(token, uri, false);

            /* Parse the request body XML: */
            Document requestBody = parseRequestBody(request);

            validateRequestBody(requestBody);

            String depth = request.getHeader("Depth");

            if (depth == null) {
                /* No Depth header from client means treat as
                 * 'infinity', but we only support '1', so set that.
                 */
                depth = "1";
            }

            PropfindRequestModel model = buildPropfindModel(request,
                resource, requestBody, depth, token);

            writeResponse(request, response, model);
        }
        catch (InvalidRequestException e) {
            responseBuilder(HttpServletResponse.SC_BAD_REQUEST)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message(e.getMessage())
                .writeTo(response);
        }
        catch (ResourceNotFoundException e) {
            responseBuilder(HttpServletResponse.SC_NOT_FOUND)
                // GULP WebDAV library does not handle request body in 404-responses: 
                //.header("Content-Type", "text/plain;charset=utf-8")
                //.message(e.getMessage())
                .writeTo(response);

        }
        catch (ResourceLockedException e) {
            responseBuilder(HttpUtil.SC_LOCKED)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message(e.getMessage())
            .writeTo(response);
        }
    }

    /**
     * Retrieves the requested resources and puts them in the model.
     * 
     * @param resource the resource 
     * @param requestBody a PROPFIND JDOM tree
     * @param depth defines the recursive behavior of the method
     * @param token the client session
     * @return the MVC model.
     * @exception InvalidRequestException if invalid request body is
     * supplied
     * @exception ResourceNotFoundException if the resource is not found
     * @exception AuthenticationException if the resource in question
     * requires authorization and the session ID is not authenticated
     * @exception AuthorizationException if the client does not have
     * sufficient rights to access the resource
     * @exception IOException if an I/O error occurs
     */
    private PropfindRequestModel buildPropfindModel(HttpServletRequest request,
        Resource resource, Document requestBody, String depth, String token) throws IOException {
        Repository repository = RequestContext.getRequestContext(request).getRepository();
        

        List<Resource> resourceList = new ArrayList<>();
        resourceList.add(resource);
        if (resource.isCollection()) {
            resourceList.addAll(getResourceDescendants(
                resource.getURI(), depth, repository, token));
        }

        Element root = requestBody.getRootElement();
        Element propType = (Element) root.getChildren().get(0);
        String propTypeName = propType != null ? 
                propType.getName().toLowerCase() : null;

        if (! ("allprop".equals(propTypeName)
               || "propname".equals(propTypeName)
               || "prop".equals(propTypeName))) {
            throw new InvalidRequestException(
                "Expected one of `allprop', `propname' or `prop' elements");
        }
        
        boolean wildcardPropRequest = 
            ("allprop".equals(propTypeName) || "propname".equals(propTypeName)); 
        //model.put(WebdavConstants.WEBDAVMODEL_WILDCARD_PROP_REQUEST, wildcardPropRequest);
        
        List<Element> requestedProps = getRequestedProperties(requestBody, resource, depth);
        
        // VTK-3235
        // Maybe authorize all resources for read before allowing request to proceed
        maybeAuthorize(request, resourceList, requestedProps, depth, wildcardPropRequest);

        //model.put(WebdavConstants.WEBDAVMODEL_REQUESTED_PROPERTIES, requestedProps);

        /* if property name is 'allprop' or 'prop', we expect values
         * of the elements to be filled into the response elements */
        boolean appendPropertyValues = ("allprop".equals(propTypeName)
                                        || "prop".equals(propTypeName));

        //model.put(WebdavConstants.WEBDAVMODEL_REQUESTED_PROPERTIES_APPEND_VALUES,
                  //appendPropertyValues);
        
        return new PropfindRequestModel(resourceList, requestedProps, 
                appendPropertyValues, wildcardPropRequest);
    }
    
    /**
     * Maybe do READ_PROCESSED authorization on all resources, depending on
     * parameters. (VTK-3235)
     */
    private void maybeAuthorize(HttpServletRequest request, List<Resource> resources, 
            List<Element> requestedProps, String depth, boolean wildcard) 
        throws AuthorizationException {
        
        if ("0".equals(depth) || wildcard) {
            // No need to do extra authorize when depth is 0 or wildcard.
            return;
        }
        
        boolean authorize = false;
        for (Element e: requestedProps) {
            Set<String> whitelistProps = this.childAuthorizeWhitelistProperties.get(e.getNamespace());
            if (whitelistProps == null) {
                authorize = true;
                break;
            }
            if (!whitelistProps.contains(e.getName())) {
                authorize = true;
                break;
            }
        }
        if (authorize) {
            RequestContext requestContext = RequestContext.getRequestContext(request);
            Repository repo = requestContext.getRepository();
            Principal principal = requestContext.getPrincipal();
            for (Resource r: resources) {
                if (!repo.authorize(principal, r.getAcl(), Privilege.READ_PROCESSED)) {
                    throw new AuthorizationException();
                }
            }
        }
    }

    /**
     * Returns the WebDAV source element for a resource.
     *
     * @param resource the <code>Resource</code> in question
     * @return a WebDAV "source" element
     */
    protected Element buildSourceElement(Resource resource) {
        return new Element("notimplemented");
    }


    /**
     * Finds the properties requested by the client as specified in
     * the PROPFIND body.
     *
     * @param requestBody the WebDAV request body 
     * @return a <code>List</code> of DAV property elements
     * represented as <code>org.jdom.Element</code> objects.
     */
    protected List<Element> getRequestedProperties(Document requestBody, Resource res,
                                                   String depth) {
        List<Element> propList = new ArrayList<>();

        /* Check for 'allprop' or 'propname': */
        if (requestBody.getRootElement().getChild(
                "allprop", WebdavConstants.DAV_NAMESPACE) != null ||
            requestBody.getRootElement().getChild(
                "propname", WebdavConstants.DAV_NAMESPACE) != null) {

            /* DAV properties: */
            for (String name : DAV_PROPERTIES) {
                Element e = new Element(name, WebdavConstants.DAV_NAMESPACE);
                propList.add(e);
            }
            
            // VTK-3235
            // For wildcard we only include all props when depth is 0.
            // (Otherwise only standard WebDAV-props are provided.)
            if ("0".equals(depth)) {
                List<Element> defaultNsPropList = new ArrayList<>();
                List<Element> otherProps = new ArrayList<>();
                /* Resource type (treat it as a normal property): */
                defaultNsPropList.add(new Element("resourceType", WebdavConstants.DEFAULT_NAMESPACE.getURI()));
                
                /* Other properties: */
                for (Property prop : res) {
                    Namespace namespace = prop.getDefinition().getNamespace();
                    String name = prop.getDefinition().getName();

                    if (Namespace.DEFAULT_NAMESPACE.equals(namespace)
                            && MAPPED_DAV_PROPERTIES.containsValue(name)) {
                        continue;
                    }
                    Element e;

                    if (Namespace.DEFAULT_NAMESPACE.equals(namespace)) {
                        e = new Element(name, WebdavConstants.DEFAULT_NAMESPACE.getURI());
                        if (isSupportedProperty(name, e.getNamespace())) {
                            defaultNsPropList.add(e);
                        }
                    } else {
                        e = new Element(name, namespace.getUri());
                        if (isSupportedProperty(name, e.getNamespace())) {
                            otherProps.add(e);
                        }
                    }
                }
                propList.addAll(defaultNsPropList);
                propList.addAll(otherProps);
            }
        } else {
            Element propertyElement = requestBody.getRootElement().getChild(
                    "prop", WebdavConstants.DAV_NAMESPACE);

            for (@SuppressWarnings("rawtypes") Iterator propIter = propertyElement.getChildren().iterator();
                    propIter.hasNext();) {

                Element requestedProperty = (Element) propIter.next();
                if (isSupportedProperty(requestedProperty.getName(), requestedProperty.getNamespace())) {
                    propList.add(requestedProperty);
                }
            }
        }
        return propList;
    }
   

    /**
     * Gets a list of a resource's children/descendants, depending on
     * the value of the <code>depth</code> parameter.
     *
     * @param uri the URI of the resource of which to find children
     * @param depth determines whether to list only the immediate
     * children of the resource or all descendants (legal values are
     * <code>"1"</code> or <code>"infinity"</code>.
     * @param repository the <code>Repository</code> to query
     * @param token the client session
     * @return a <code>List</code> of <code>Resource</code> objects
     */
    protected List<Resource> getResourceDescendants(Path uri, String depth,
                                          Repository repository, String token) 
                                                  throws IOException {

        ArrayList<Resource> descendants = new ArrayList<>();
        
        if (!(depth.equals("1") || depth.equals("infinity"))) {
            return descendants;
        }

        /* List immediate children: */
        Resource[] resourceArray = repository.listChildren(token, uri, false);
        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Number of children: " + resourceArray.length);
        }
        
        for (int i = 0;  i < resourceArray.length; i++) {
            descendants.add(resourceArray[i]);
        }

        return descendants;
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
        builder.setValidation(false);
        builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        /* if empty request body, request is implicitly "allprop": */
        if (request.getHeader("Content-Length") != null
            && request.getHeader("Content-Length").equals("0")) {
            Element propFind = new Element("propfind", WebdavConstants.DAV_NAMESPACE);
            propFind.addContent(new Element("allprop", WebdavConstants.DAV_NAMESPACE));
            return new Document(propFind);
        }

        try {
            Document requestBody = builder.build(
                new BoundedInputStream(
                    request.getInputStream(), this.maxRequestSize));
            return requestBody;

        } catch (JDOMException e) {
            throw new InvalidRequestException(e.getMessage(), e);

        } catch (SizeLimitException e) {
            throw new InvalidRequestException(
                "PROPFIND request too large for size limit: " + this.maxRequestSize);
        }
    }
   

    /**
     * Verifies that a JDOM tree constitutes a valid PROPFIND request
     * body.
     *
     * @param requestBody a <code>org.jdom.Document</code> tree
     * representing the WebDAV request body
     * @exception InvalidRequestException if the request body is not
     * valid
     */
    protected void validateRequestBody(Document requestBody)
        throws InvalidRequestException {
        Element root = requestBody.getRootElement();
        if (!root.getName().equals("propfind")) {
            // FIXME: actually validate the request body
            throw new InvalidRequestException(
                "Invalid request element '" + root.getName()
                + "' (expected 'propfind')");
        }      
    }
    
    /**
     * Set of whitelisted properties which, when requested by a client, will not
     * trigger authorization on descendant resources returned in response to a
     * <code>PROPFIND</code> request with <code>Depth</code> greater than 0 (VTK-3235).
     * 
     * The each string in list should be on the form "namespace:prop".
     */
    public void setChildAuthorizeWhitelistProperties(List<String> props) {
        this.childAuthorizeWhitelistProperties = new HashMap<>();
        
        for (String namespaceProp: props) {
            String[] kv = TextUtils.parseKeyValue(namespaceProp, ':', TextUtils.TRIM);
            if (kv[0].isEmpty() || kv[1].isEmpty()) {
                throw new IllegalArgumentException("Namespace and/or property name cannot be empty");
            }
            org.jdom.Namespace ns = org.jdom.Namespace.getNamespace(kv[0]);
            Set<String> propset = this.childAuthorizeWhitelistProperties.get(ns);
            if (propset == null) {
                propset = new HashSet<>();
                this.childAuthorizeWhitelistProperties.put(ns, propset);
            }
            propset.add(kv[1]);
        }
    }
    
    private static class PropfindRequestModel {
        public final List<Resource> resources;
        public final List<Element> properties;
        public final boolean appendValues;
        public final boolean wildcardPropRequest;
        private PropfindRequestModel(List<Resource> resources, List<Element> properties, 
                boolean appendValues, boolean wildcardPropRequest) {
            this.resources = resources;
            this.properties = properties;
            this.appendValues = appendValues;
            this.wildcardPropRequest = wildcardPropRequest;
        }
    }
    

    /**
     *  Builds a DAV 'multistatus' XML element.
     */
    private void writeResponse(HttpServletRequest request, 
            HttpServletResponse response, PropfindRequestModel model) throws IOException {
        
        Element e = buildMultistatusElement(request, model);

        Document doc = new Document(e);

        Format format = Format.getPrettyFormat();
        format.setEncoding("utf-8");
        //format.setLineSeparator("\r\n");
        //format.setIndent("");

        XMLOutputter xmlOutputter = new XMLOutputter(format);
        String xml = xmlOutputter.outputString(doc);
        byte[] buffer = null;
        try {
            buffer = xml.getBytes("utf-8");
        } catch (UnsupportedEncodingException ex) {
            logger.warn("Warning: UTF-8 encoding not supported", ex);
            throw new RuntimeException("UTF-8 encoding not supported");
        }
        response.setHeader("Content-Type", "text/xml;charset=utf-8");
        response.setIntHeader("Content-Length", buffer.length);
        response.setStatus(HttpUtil.SC_MULTI_STATUS,
                WebdavUtil.getStatusMessage(
                        HttpUtil.SC_MULTI_STATUS));
        OutputStream out = null;
        try {
            out = response.getOutputStream();
            out.write(buffer, 0, buffer.length);
            out.flush();
            out.close();

        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }

    private Element buildMultistatusElement(HttpServletRequest request, 
            PropfindRequestModel model) throws IOException {

        Element multiStatus = new Element("multistatus", WebdavConstants.DAV_NAMESPACE);

        for (Resource currentResource: model.resources) {
                Element responseElement = buildResponseElement(request, 
                    currentResource, model.properties, 
                    model.appendValues, model.wildcardPropRequest);
            
                multiStatus.addContent(responseElement);
        }
        
        return multiStatus;
    }
   



    /**
     * Builds a DAV 'response' XML element from data in a
     * <code>Resource</code>.
     *
     * @param resource the <code>Resource</code> that is being queried
     * @param requestedProps a list of DAV property names (must be
     * given as a list of <code>org.jdom.Element</code> objects) for
     * which to find values
     * @param appendPropertyValues determines whether property values should
     * be appended to the JDOM elements or not (this is not always
     * requested, i.e. when a client requests only "propnames").
     * @return an <code>org.jdom.Element</code> representing the DAV
     * response element
     * @throws IOException 
     */
    private Element buildResponseElement(HttpServletRequest request, 
            Resource resource, List<Element> requestedProps,
            boolean appendPropertyValues, boolean isWildcardPropRequest) throws IOException {

        RequestContext requestContext = RequestContext.getRequestContext(request);
        Principal p = requestContext.getPrincipal();

        Element responseElement = new Element("response", WebdavConstants.DAV_NAMESPACE);
        URL href = webdavService.urlConstructor(requestContext.getRequestURL())
                .withResource(resource)
                .withPrincipal(p)
                .constructURL();

        responseElement.addContent(
                new Element("href", WebdavConstants.DAV_NAMESPACE).addContent(href.toString()));
        Element foundProperties = new Element("prop", WebdavConstants.DAV_NAMESPACE);
        Element unknownProperties = new Element("prop", WebdavConstants.DAV_NAMESPACE);

        for (Element prop: requestedProps) {
            Element foundProp = buildPropertyElement(request, resource, prop, appendPropertyValues);

            if (foundProp != null) {

                foundProperties.addContent(foundProp);

            } else {

                unknownProperties.addContent(
                    new Element(prop.getName(), prop.getNamespace().getPrefix(),
                                prop.getNamespace().getURI()));
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Found properties: " + foundProperties.getChildren()
                         + ", unknown properties: " + unknownProperties.getChildren());
        }


        if (foundProperties.getChildren().size() > 0) {

            Element propStatElement = new Element("propstat", WebdavConstants.DAV_NAMESPACE);
            propStatElement.addContent(foundProperties);
            Element status = new Element("status", WebdavConstants.DAV_NAMESPACE);
            status.addContent(WebdavUtil.getStatusMessage(HttpServletResponse.SC_OK));
            propStatElement.addContent(status);
            responseElement.addContent(propStatElement);
        }
        

        if (unknownProperties.getChildren().size() > 0 && !isWildcardPropRequest) {

            Element status = new Element("status", WebdavConstants.DAV_NAMESPACE);
            Element propStatUnknown = new Element("propstat", WebdavConstants.DAV_NAMESPACE);
            propStatUnknown.addContent(unknownProperties);
            propStatUnknown.addContent(status);
            status.addContent(WebdavUtil.getStatusMessage(HttpServletResponse.SC_NOT_FOUND));
            responseElement.addContent(propStatUnknown);
        }
        
        return responseElement;
    }

   

    /**
     * Translates DAV property names to appropriate method calls on a
     * <code>Resource</code> object and returns JDOM elements
     * containing the name and possibly also the value.
     *
     * @param resource the <code>Resource</code> object to query
     * information about
     * @param propElement DAV property name
     * @param appendValue determines whether the value should be added
     * to the JDOM element
     * @return a JDOM element containing the property and possibly its
     * value, or <code>null</code> if not found.
     * @throws IOException 
     */
    protected Element buildPropertyElement(HttpServletRequest request, 
            Resource resource, Element propElement, 
            boolean appendValue) throws IOException {
        
        String propertyName = propElement.getName();
        org.jdom.Namespace namespace = propElement.getNamespace();

        Element element = new Element(propertyName, propElement.getNamespace());
        if (!appendValue) {
            return element;
        }
      
        if (namespace.equals(WebdavConstants.DAV_NAMESPACE)) {

            if (propertyName.equals("creationdate")) {
                element.addContent(formatCreationTime(
                                       resource.getCreationTime()));
            
            } else if (propertyName.equals("displayname")) {
                String name = resource.getName();
                element.addContent(name);

            } else if (propertyName.equals("getcontentlanguage")) {
                Locale locale = LocaleHelper.getLocale(resource.getContentLanguage());
                if (locale == null) return null;
                element.addContent(locale.getLanguage());

            } else if (propertyName.equals("getcontentlength")) {
                if (resource.isCollection()) {
                    element.addContent("0");
                } else {
                    element.addContent(String.valueOf(
                                           resource.getContentLength()));
                }

            } else if (propertyName.equals("getcontenttype")) {
                String type = resource.getContentType();
                
                if (type == null || type.equals("")) {
                    if (!resource.isCollection()) {
                        return null;
                    }
                    if (this.collectionContentType != null) {
                        element.addContent(this.collectionContentType);
                    }
                } else {
                    element.addContent(type);
                }
               
            } else if (propertyName.equals("getetag")) {
                if (resource.getSerial() == null) {
                    return null;
                }
                element.addContent(resource.getSerial());

            } else if (propertyName.equals("getlastmodified")) {
                element.addContent(HttpUtil.getHttpDateString(resource.getLastModified()));

            } else if (propertyName.equals("lockdiscovery")) {
                element = buildLockDiscoveryElement(resource);

            } else if (propertyName.equals("resourcetype")) {
                if (resource.isCollection()) {
                    element.addContent(new Element("collection",
                                                   WebdavConstants.DAV_NAMESPACE));
                }
            } else if (propertyName.equals("source")) {
                //element.addContent(buildSourceElement(resource));
                return null;            

            } else if (propertyName.equals("supportedlock")) {
                element = buildSupportedLockElement(resource);

            } else if (propertyName.equals("supported-privilege-set")) {
                element = buildSupportedPrivilegeSetElement(resource);

            } else if (propertyName.equals("current-user-privilege-set")) {
                element = buildCurrentUserPrivilegeSetElement(resource);
            }


        } else {

            vtk.repository.Namespace ns;
            if (WebdavConstants.DEFAULT_NAMESPACE.equals(namespace)) {
                ns = vtk.repository.Namespace.DEFAULT_NAMESPACE;
            } else {
                ns = vtk.repository.Namespace.getNamespace(namespace.getURI());
            }

            if ("resourceType".equals(propertyName)) {
                Element e = new Element("resourceType", WebdavConstants.DEFAULT_NAMESPACE);
                e.setText(resource.getResourceType());
                return e;
            }

            Property property = resource.getProperty(ns, propertyName);
            if (property == null) {
                return null;
            }
            
            PropertyTypeDefinition def = property.getDefinition();
            if (def != null && def.isMultiple()) {
                element = buildMultiValueCustomPropertyElement(property);
            } else {
                element = buildCustomPropertyElement(property);
            }
        }

        return element;
    }
   


    /**
     * Builds a WebDAV "lockdiscovery" JDOM element for a resource.
     *
     * @param resource the resource to find lock information about
     * @return a JDOM lockdiscovery element
     */
    private Element buildLockDiscoveryElement(Resource resource) throws IOException {
        Element lockDiscovery = new Element("lockdiscovery",
                WebdavConstants.DAV_NAMESPACE);

        Lock lock = resource.getLock();
        
        if (lock == null) {
            // No lock on resource, return empty 'lockdiscovery' element.
            return lockDiscovery;
        }
        
        Element activeLock = new Element("activelock",
                WebdavConstants.DAV_NAMESPACE);

        String type = "exclusive";
        String scope = "write";

        activeLock.addContent(new Element("locktype",
                WebdavConstants.DAV_NAMESPACE).addContent(new Element(type,
                WebdavConstants.DAV_NAMESPACE)));

        activeLock.addContent(new Element("lockscope",
                WebdavConstants.DAV_NAMESPACE).addContent(new Element(scope,
                WebdavConstants.DAV_NAMESPACE)));

        activeLock.addContent(new Element("depth",
                WebdavConstants.DAV_NAMESPACE).addContent(lock.getDepth().toString()));

        activeLock.addContent(LockController.buildLockOwnerElement(lock.getOwnerInfo()));

        long timeout = lock.getTimeout().getTime() - System.currentTimeMillis();

        if (timeout < 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("Lock's timeout was: " + lock.getTimeout()
                        + ", (has already timed out) ");
            }
            return null;
        }

        String timeoutStr = "Second-" + (timeout / 1000);

        activeLock.addContent(new Element("timeout",
                WebdavConstants.DAV_NAMESPACE).addContent(timeoutStr));

        activeLock.addContent(new Element("locktoken",
                        WebdavConstants.DAV_NAMESPACE).addContent(new Element(
                        "href", WebdavConstants.DAV_NAMESPACE).addContent(lock.getLockToken())));

        lockDiscovery.addContent(activeLock);

        return lockDiscovery;
    }
   




    /**
     * Builds a WebDAV "supportedlock" JDOM element for a resource.
     * 
     * @param resource
     *            the resource in question
     * @return a JDOM supportedlock element
     */
    private Element buildSupportedLockElement(Resource resource) {
        Element supportedLock = new Element("supportedlock", WebdavConstants.DAV_NAMESPACE);
        Element lockEntry = new Element("lockentry", WebdavConstants.DAV_NAMESPACE);
        lockEntry.addContent(
                new Element("lockscope", WebdavConstants.DAV_NAMESPACE).addContent(
                        new Element("exclusive", WebdavConstants.DAV_NAMESPACE)));
        lockEntry.addContent(
                new Element("locktype", WebdavConstants.DAV_NAMESPACE).addContent(
                        new Element("write", WebdavConstants.DAV_NAMESPACE)));

        supportedLock.addContent(lockEntry);

        return supportedLock;
    }


    /**
     * Builds a JDOM element for a custom (non "DAV:" namespace)
     * resource property.
     */
    private Element buildCustomPropertyElement(Property property) {

        /* FIXME: This is not a particurlarly nice/efficient way of
         * building the element. Assume first that the property is an
         * XML fragment, and try to build a document from it. If that
         * fails, we assume it is a 'name = value' style property, and
         * build a simple JDOM element from it. */
        PropertyTypeDefinition propDef = property.getDefinition();
        String name = propDef.getName();
        org.jdom.Namespace namespace = org.jdom.Namespace.getNamespace(propDef.getNamespace().getUri());

        if (vtk.repository.Namespace.DEFAULT_NAMESPACE.equals(propDef.getNamespace())) {
            namespace = WebdavConstants.DEFAULT_NAMESPACE;
        }

        String value = property.getValue().getNativeStringRepresentation();

        /* If the value does not contain both "<" and ">" we know for
         * sure that it is not an XML fragment: */

        if (value.indexOf("<") >= 0 && value.indexOf(">") >= 0) {
            
            try {
        
                String xml = "<" + name + " xmlns=\"" +
                    namespace.getURI() + "\">" + value + "</" +
                    name + ">";

                Document doc = (new SAXBuilder()).build(
                    new StringReader(xml));

                if (!doc.getRootElement().getNamespace().equals(namespace)) {
                    doc.getRootElement().setNamespace(namespace);
                }
                Element rootElement = doc.getRootElement();
                rootElement.detach();
                return rootElement;
            
            } catch (IOException e) {

                /* Ignore this, build a simple JDOM element instead. */

            } catch (JDOMException e) {

                /* Ignore this, build a simple JDOM element instead. */
            }
        }
        
        Element propElement = new Element(name, namespace);
        
        // Format dates according to HTTP spec, 
        // use value's native string representation for other types.
        if (property.getValue().getType() == PropertyType.Type.TIMESTAMP
                || property.getValue().getType() == PropertyType.Type.DATE) {
            propElement.setText(WebdavUtil.formatPropertyDateValue(property.getDateValue()));
        } else {
            propElement.setText(property.getValue().getNativeStringRepresentation());    
        }
        
        return propElement;

    }
   
    /**
     * Make a simple XML-list structure out of a multi-valued property.
     */
    private Element buildMultiValueCustomPropertyElement(Property property) {
        Value[] values = property.getValues();

        PropertyTypeDefinition propDef = property.getDefinition();
        org.jdom.Namespace namespace = org.jdom.Namespace.getNamespace(propDef.getNamespace().getUri());

        if (vtk.repository.Namespace.DEFAULT_NAMESPACE.equals(propDef.getNamespace())) {
            namespace = WebdavConstants.DEFAULT_NAMESPACE;
        }
        Element propElement = new Element(propDef.getName(), namespace);
        
        Element valuesElement = new Element("values", WebdavConstants.MULTI_VALUE_NAMESPACE);
        
        for (int i=0; i<values.length; i++) {
            Element valueElement = 
                new Element("value", WebdavConstants.MULTI_VALUE_NAMESPACE);

            // Format dates according to HTTP spec, 
            // use value's native string representation for other types.
            if (values[i].getType() == PropertyType.Type.TIMESTAMP ||
                    values[i].getType() == PropertyType.Type.DATE) {
                valueElement.setText(WebdavUtil.formatPropertyDateValue(values[i].getDateValue()));
            } else {
                valueElement.setText(values[i].getNativeStringRepresentation());
            }
            
            valuesElement.addContent(valueElement);
        }
        
        propElement.addContent(valuesElement);
        
        return propElement;
    }


    private String formatCreationTime(Date date) {
        /* example: 1970-01-01T12:00:00Z */
        FastDateFormat formatter = FastDateFormat.getInstance(
                "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.TimeZone.getTimeZone("UTC"));

        return formatter.format(date);
    }

    /* EXPERIMENTAL WebDAV ACL STUFF: */

    /**
     * Build the supported privilege set element.
     * @param resource the requested resource
     */
    protected Element buildSupportedPrivilegeSetElement(Resource resource) {

        Element supportedPrivilege =
            new Element("supported-privilege", WebdavConstants.DAV_NAMESPACE);

        supportedPrivilege.addContent(
            new Element("privilege", WebdavConstants.DAV_NAMESPACE).addContent(
                new Element("all", WebdavConstants.DAV_NAMESPACE)));
        supportedPrivilege.addContent(
            new Element("description", WebdavConstants.DAV_NAMESPACE).addContent(
                "Any operation"));


        Element sub = new Element("supported-privilege", WebdavConstants.DAV_NAMESPACE);
        sub.addContent(
            new Element("privilege", WebdavConstants.DAV_NAMESPACE).addContent(
                new Element("read", WebdavConstants.DAV_NAMESPACE)));
        sub.addContent(new Element("description", WebdavConstants.DAV_NAMESPACE).addContent(
                           "Read any object"));
        
        supportedPrivilege.addContent(sub);


        sub = new Element("supported-privilege", WebdavConstants.DAV_NAMESPACE);
        sub.addContent(
            new Element("privilege", WebdavConstants.DAV_NAMESPACE).addContent(
                new Element("write", WebdavConstants.DAV_NAMESPACE)));
        sub.addContent(
            new Element("description", WebdavConstants.DAV_NAMESPACE).addContent(
                           "Write any object"));
        
        supportedPrivilege.addContent(sub);
        
        Element supportedPrivilegeSet =
            new Element("supported-privilege-set", WebdavConstants.DAV_NAMESPACE);
        supportedPrivilegeSet.addContent(supportedPrivilege);

        return supportedPrivilegeSet;
    }
    


    /**
     * Builds the current user privilege set element.
     * @param resource the requested resource
     */
    protected Element buildCurrentUserPrivilegeSetElement(Resource resource) {
        
        Element e = new Element("current-user-privilege-set", WebdavConstants.DAV_NAMESPACE);
        e.addContent(new Element("privilege", WebdavConstants.DAV_NAMESPACE).addContent(
                         new Element("read", WebdavConstants.DAV_NAMESPACE)));
        e.addContent(new Element("privilege", WebdavConstants.DAV_NAMESPACE).addContent(
                         new Element("write", WebdavConstants.DAV_NAMESPACE)));
        e.addContent(new Element("privilege", WebdavConstants.DAV_NAMESPACE).addContent(
                         new Element("all", WebdavConstants.DAV_NAMESPACE)));
        return e;
    }
}
